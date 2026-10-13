package io.github.theamethyx.nova.world;

import java.util.function.BiPredicate;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import io.github.theamethyx.nova.core.research.GrowthStage;
import io.github.theamethyx.nova.core.research.geode.ScanData;

/**
 * Builds {@link ScanData} for a region directly from server block data, applying the same rules as
 * the client's WorldScanner (the client game test checks that both produce identical counts).
 * A column filter simulates unreadable chunks for incomplete-observation experiments.
 */
public final class ServerScan {
	private ServerScan() {
	}

	/**
	 * @param readable which chunk columns (chunkX, chunkZ) count as readable; blocks in other
	 *                 columns are not recorded and faces reaching into them are unreadable
	 */
	public static ScanData scan(ServerLevel level, BoundingBox region, BiPredicate<Integer, Integer> readable) {
		ScanData data = new ScanData(level.dimension().identifier().toString(), level.getMinY(), level.getMaxY());
		for (int cx = region.minX() >> 4; cx <= region.maxX() >> 4; cx++) {
			for (int cz = region.minZ() >> 4; cz <= region.maxZ() >> 4; cz++) {
				data.requestColumn(cx, cz);
				if (readable.test(cx, cz)) {
					data.markAvailable(cx, cz);
				}
			}
		}

		BlockPos.MutableBlockPos neighbor = new BlockPos.MutableBlockPos();
		for (BlockPos pos : BlockPos.betweenClosed(region.minX(), region.minY(), region.minZ(), region.maxX(), region.maxY(), region.maxZ())) {
			if (!readable.test(pos.getX() >> 4, pos.getZ() >> 4)) {
				continue;
			}

			BlockState state = level.getBlockState(pos);
			if (!AmethystBlocks.isGeodeBlock(state)) {
				continue;
			}

			GrowthStage stage = AmethystBlocks.stageOf(state);
			if (stage != null) {
				data.addCell(new ScanData.Cell(pos.getX(), pos.getY(), pos.getZ(), ScanData.Kind.BUD, stage, AmethystBlocks.facing(state)));
			} else if (state.is(Blocks.BUDDING_AMETHYST)) {
				data.addCell(new ScanData.Cell(pos.getX(), pos.getY(), pos.getZ(), ScanData.Kind.BUDDING, null, -1));
				ScanData.FaceState[] faces = new ScanData.FaceState[6];
				for (Direction direction : Direction.values()) {
					neighbor.setWithOffset(pos, direction);
					faces[direction.ordinal()] = readable.test(neighbor.getX() >> 4, neighbor.getZ() >> 4)
							? AmethystBlocks.faceState(level.getBlockState(neighbor), direction)
							: ScanData.FaceState.UNAVAILABLE;
				}

				data.setBuddingFaces(pos.getX(), pos.getY(), pos.getZ(), faces);
			} else {
				data.addCell(new ScanData.Cell(pos.getX(), pos.getY(), pos.getZ(), ScanData.Kind.AMETHYST_BLOCK, null, -1));
			}
		}

		return data;
	}
}
