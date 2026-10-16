package io.github.theamethyx.femboyclient.client.schematic;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A loaded schematic: the non-air blocks of a build, relative to its lowest corner. Reads vanilla structure files
 * ({@code .nbt}, from structure blocks), Sponge schematics ({@code .schem}, versions 2 and 3, used by WorldEdit) and
 * Litematica files ({@code .litematic}, all regions merged).
 */
public final class Schematic {
	/** Refuses files with more non-air blocks than this, to protect memory. */
	public static final int MAX_BLOCKS = 2_000_000;

	private final String name;
	private final int sizeX;
	private final int sizeY;
	private final int sizeZ;
	private final long[] positions;
	private final BlockState[] states;
	private final List<String> warnings;

	private Schematic(String name, int sizeX, int sizeY, int sizeZ, List<BlockPos> positions, List<BlockState> states, List<String> warnings) {
		this.name = name;
		this.sizeX = sizeX;
		this.sizeY = sizeY;
		this.sizeZ = sizeZ;
		this.positions = positions.stream().mapToLong(BlockPos::asLong).toArray();
		this.states = states.toArray(BlockState[]::new);
		this.warnings = List.copyOf(warnings);
	}

	public String name() {
		return name;
	}

	public int sizeX() {
		return sizeX;
	}

	public int sizeY() {
		return sizeY;
	}

	public int sizeZ() {
		return sizeZ;
	}

	/** Number of non-air blocks. */
	public int blockCount() {
		return states.length;
	}

	public BlockPos position(int index) {
		return BlockPos.of(positions[index]);
	}

	public BlockState state(int index) {
		return states[index];
	}

	/** Problems found while reading (unknown blocks and the like); empty when the file read cleanly. */
	public List<String> warnings() {
		return warnings;
	}

	// ----- loading ---------------------------------------------------------------------------

	public static Schematic load(Path path) throws IOException {
		String file = path.getFileName().toString();
		String lower = file.toLowerCase(Locale.ROOT);
		CompoundTag root;
		try (var in = Files.newInputStream(path)) {
			root = NbtIo.readCompressed(in, NbtAccounter.create(512L * 1024 * 1024));
		}

		Builder builder = new Builder(file);
		if (lower.endsWith(".litematic")) {
			readLitematic(root, builder);
		} else if (lower.endsWith(".schem") || lower.endsWith(".schematic")) {
			readSponge(root, builder);
		} else if (lower.endsWith(".nbt")) {
			readStructure(root, builder);
		} else {
			throw new IOException("Unknown file type (use .nbt, .schem or .litematic)");
		}

		return builder.build();
	}

	/** Vanilla structure files: size, palette (or palettes), blocks with pos and state index. */
	private static void readStructure(CompoundTag root, Builder builder) throws IOException {
		ListTag size = root.getListOrEmpty("size");
		if (size.size() != 3) {
			throw new IOException("Not a structure file (no size)");
		}

		ListTag paletteTag = root.getList("palette").orElseGet(() -> root.getListOrEmpty("palettes").getListOrEmpty(0));
		List<BlockState> palette = new ArrayList<>();
		for (int i = 0; i < paletteTag.size(); i++) {
			palette.add(NbtUtils.readBlockState(BuiltInRegistries.BLOCK, paletteTag.getCompoundOrEmpty(i)));
		}

		ListTag blocks = root.getListOrEmpty("blocks");
		for (int i = 0; i < blocks.size(); i++) {
			CompoundTag block = blocks.getCompoundOrEmpty(i);
			ListTag pos = block.getListOrEmpty("pos");
			int state = block.getIntOr("state", -1);
			if (pos.size() == 3 && state >= 0 && state < palette.size()) {
				builder.add(pos.getIntOr(0, 0), pos.getIntOr(1, 0), pos.getIntOr(2, 0), palette.get(state));
			}
		}
	}

	/** Sponge schematics: version 2 has the data at the top level, version 3 inside "Schematic" → "Blocks". */
	private static void readSponge(CompoundTag root, Builder builder) throws IOException {
		CompoundTag schematic = root.getCompound("Schematic").orElse(root);
		int width = schematic.getShort("Width").orElse((short) 0) & 0xFFFF;
		int height = schematic.getShort("Height").orElse((short) 0) & 0xFFFF;
		int length = schematic.getShort("Length").orElse((short) 0) & 0xFFFF;
		CompoundTag blocks = schematic.getCompound("Blocks").orElse(schematic);
		CompoundTag paletteTag = blocks.getCompound("Palette").orElse(null);
		byte[] data = blocks.getByteArray(blocks.contains("Data") ? "Data" : "BlockData").orElse(null);
		if (width == 0 || paletteTag == null || data == null) {
			throw new IOException("Not a Sponge schematic, or an old (pre-1.13) one");
		}

		Map<Integer, BlockState> palette = new HashMap<>();
		for (String key : paletteTag.keySet()) {
			palette.put(paletteTag.getIntOr(key, -1), builder.parse(key));
		}

		int index = 0;
		int offset = 0;
		while (offset < data.length && index < width * height * length) {
			// Varint-encoded palette indices.
			int value = 0;
			int shift = 0;
			byte b;
			do {
				b = data[offset++];
				value |= (b & 0x7F) << shift;
				shift += 7;
			} while ((b & 0x80) != 0 && offset < data.length);

			int y = index / (width * length);
			int z = index % (width * length) / width;
			int x = index % width;
			BlockState state = palette.get(value);
			if (state != null) {
				builder.add(x, y, z, state);
			}

			index++;
		}
	}

	/** Litematica: each region has a position, a (possibly negative) size, a palette and bit-packed states. */
	private static void readLitematic(CompoundTag root, Builder builder) throws IOException {
		CompoundTag regions = root.getCompound("Regions").orElse(null);
		if (regions == null || regions.isEmpty()) {
			throw new IOException("Not a Litematica file (no regions)");
		}

		for (String regionName : regions.keySet()) {
			CompoundTag region = regions.getCompoundOrEmpty(regionName);
			CompoundTag position = region.getCompoundOrEmpty("Position");
			CompoundTag size = region.getCompoundOrEmpty("Size");
			int sx = size.getIntOr("x", 0);
			int sy = size.getIntOr("y", 0);
			int sz = size.getIntOr("z", 0);
			int ax = Math.abs(sx);
			int ay = Math.abs(sy);
			int az = Math.abs(sz);
			// A negative size extends from the position toward lower coordinates.
			int minX = position.getIntOr("x", 0) + (sx < 0 ? sx + 1 : 0);
			int minY = position.getIntOr("y", 0) + (sy < 0 ? sy + 1 : 0);
			int minZ = position.getIntOr("z", 0) + (sz < 0 ? sz + 1 : 0);

			ListTag paletteTag = region.getListOrEmpty("BlockStatePalette");
			List<BlockState> palette = new ArrayList<>();
			for (int i = 0; i < paletteTag.size(); i++) {
				palette.add(NbtUtils.readBlockState(BuiltInRegistries.BLOCK, paletteTag.getCompoundOrEmpty(i)));
			}

			long[] packed = region.getLongArray("BlockStates").orElse(new long[0]);
			int bits = Math.max(2, 32 - Integer.numberOfLeadingZeros(Math.max(1, palette.size() - 1)));
			long mask = (1L << bits) - 1;
			long volume = (long) ax * ay * az;
			if (packed.length * 64L < volume * bits) {
				throw new IOException("Region '" + regionName + "' has too little block data");
			}

			for (long i = 0; i < volume; i++) {
				long start = i * bits;
				int startIndex = (int) (start >> 6);
				int endIndex = (int) (((i + 1) * bits - 1) >> 6);
				int startBit = (int) (start & 63);
				long value = startIndex == endIndex ? packed[startIndex] >>> startBit & mask
						: (packed[startIndex] >>> startBit | packed[endIndex] << (64 - startBit)) & mask;
				if (value < palette.size()) {
					int x = (int) (i % ax);
					int z = (int) (i / ax % az);
					int y = (int) (i / ((long) ax * az));
					builder.add(minX + x, minY + y, minZ + z, palette.get((int) value));
				}
			}
		}
	}

	/** Collects blocks and shifts them so the lowest corner is at 0,0,0. */
	private static final class Builder {
		private final String name;
		private final List<BlockPos> positions = new ArrayList<>();
		private final List<BlockState> states = new ArrayList<>();
		private final List<String> warnings = new ArrayList<>();
		private int unknown;

		Builder(String name) {
			this.name = name;
		}

		void add(int x, int y, int z, BlockState state) throws IOException {
			if (state.isAir() || state.is(Blocks.STRUCTURE_VOID)) {
				return;
			}

			if (positions.size() >= MAX_BLOCKS) {
				throw new IOException("More than " + MAX_BLOCKS + " blocks");
			}

			positions.add(new BlockPos(x, y, z));
			states.add(state);
		}

		BlockState parse(String text) {
			try {
				return BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK, text, false).blockState();
			} catch (CommandSyntaxException e) {
				unknown++;
				return Blocks.AIR.defaultBlockState();
			}
		}

		Schematic build() throws IOException {
			if (positions.isEmpty()) {
				throw new IOException("The file contains no blocks");
			}

			int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
			int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
			for (BlockPos pos : positions) {
				minX = Math.min(minX, pos.getX());
				minY = Math.min(minY, pos.getY());
				minZ = Math.min(minZ, pos.getZ());
				maxX = Math.max(maxX, pos.getX());
				maxY = Math.max(maxY, pos.getY());
				maxZ = Math.max(maxZ, pos.getZ());
			}

			List<BlockPos> shifted = new ArrayList<>(positions.size());
			for (BlockPos pos : positions) {
				shifted.add(pos.offset(-minX, -minY, -minZ));
			}

			if (unknown > 0) {
				warnings.add(unknown + " palette entries were unknown blocks (from another version or a mod) and are left out.");
			}

			return new Schematic(name, maxX - minX + 1, maxY - minY + 1, maxZ - minZ + 1, shifted, states, warnings);
		}
	}
}
