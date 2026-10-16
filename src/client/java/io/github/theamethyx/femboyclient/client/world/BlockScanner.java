package io.github.theamethyx.femboyclient.client.world;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;

/**
 * Finds blocks matching a filter in chunks the client has loaded, a few chunks per tick within a time budget, nearest
 * chunks first. Each chunk is re-read after {@code rescanMillis}. Never loads chunks or asks the server for anything.
 */
public final class BlockScanner {
	public record Hit(BlockPos pos, BlockState state) {
	}

	private final Map<Long, List<Hit>> byChunk = new HashMap<>();
	private final Map<Long, Long> scannedAt = new HashMap<>();
	private Predicate<BlockState> filter = state -> false;
	private ClientLevel level;
	private int total;
	private int inRange;

	public void setFilter(Predicate<BlockState> filter) {
		this.filter = filter;
		clear();
	}

	public void clear() {
		byChunk.clear();
		scannedAt.clear();
		total = 0;
	}

	/** Scans stale or new chunks within {@code radiusChunks} of {@code center} until {@code budgetNanos} is used. */
	public void tick(ClientLevel current, BlockPos center, int radiusChunks, long rescanMillis, long budgetNanos) {
		if (current != level) {
			level = current;
			clear();
		}

		if (level == null) {
			return;
		}

		int cx = center.getX() >> 4;
		int cz = center.getZ() >> 4;
		byChunk.keySet().removeIf(key -> Math.abs(ChunkPos.getX(key) - cx) > radiusChunks || Math.abs(ChunkPos.getZ(key) - cz) > radiusChunks
				|| !LoadedChunks.isLoaded(level, ChunkPos.getX(key), ChunkPos.getZ(key)));
		scannedAt.keySet().retainAll(byChunk.keySet());

		long start = System.nanoTime();
		long now = System.currentTimeMillis();
		inRange = 0;
		for (int ring = 0; ring <= radiusChunks; ring++) {
			for (int dx = -ring; dx <= ring; dx++) {
				for (int dz = -ring; dz <= ring; dz++) {
					if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) {
						continue;
					}

					LevelChunk chunk = level.getChunkSource().getChunk(cx + dx, cz + dz, ChunkStatus.FULL, false);
					if (chunk == null) {
						continue;
					}

					inRange++;
					long key = ChunkPos.asLong(cx + dx, cz + dz);
					Long when = scannedAt.get(key);
					if (when != null && now - when < rescanMillis) {
						continue;
					}

					if (System.nanoTime() - start > budgetNanos) {
						continue;
					}

					byChunk.put(key, scan(chunk));
					scannedAt.put(key, now);
				}
			}
		}

		total = byChunk.values().stream().mapToInt(List::size).sum();
	}

	private List<Hit> scan(LevelChunk chunk) {
		List<Hit> hits = new ArrayList<>();
		LevelChunkSection[] sections = chunk.getSections();
		int minSectionY = level.getMinSectionY();
		int baseX = chunk.getPos().getMinBlockX();
		int baseZ = chunk.getPos().getMinBlockZ();
		for (int i = 0; i < sections.length; i++) {
			LevelChunkSection section = sections[i];
			if (section == null || section.hasOnlyAir() || !section.maybeHas(filter)) {
				continue;
			}

			int baseY = (minSectionY + i) << 4;
			for (int y = 0; y < 16; y++) {
				for (int z = 0; z < 16; z++) {
					for (int x = 0; x < 16; x++) {
						BlockState state = section.getBlockState(x, y, z);
						if (filter.test(state)) {
							hits.add(new Hit(new BlockPos(baseX + x, baseY + y, baseZ + z), state));
						}
					}
				}
			}
		}

		return hits;
	}

	public List<Hit> hits() {
		List<Hit> all = new ArrayList<>(total);
		byChunk.values().forEach(all::addAll);
		return all;
	}

	public int count() {
		return total;
	}

	/** Chunks read so far out of loaded chunks in range. */
	public int scannedChunks() {
		return byChunk.size();
	}

	public int chunksInRange() {
		return inRange;
	}
}
