package io.github.theamethyx.nova.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BuddingAmethystBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import io.github.theamethyx.nova.core.research.GrowthStage;
import io.github.theamethyx.nova.core.research.StageCounts;

/**
 * Exhaustive count of a region's amethyst blocks, taken directly on the server when a fixture is
 * built. Used as ground truth that the client-side analyzer's counts are checked against.
 */
public record GeodeCensus(StageCounts counts, int buddingBlocks, int amethystBlocks, int freeFaces, int ownGrowthFaces, int blockedFaces,
		BoundingBox geodeBounds) {

	public static GeodeCensus of(BlockGetter level, BoundingBox region) {
		StageCounts counts = StageCounts.EMPTY;
		int budding = 0, amethyst = 0, free = 0, own = 0, blocked = 0;
		int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
		int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;

		for (BlockPos pos : BlockPos.betweenClosed(region.minX(), region.minY(), region.minZ(), region.maxX(), region.maxY(), region.maxZ())) {
			BlockState state = level.getBlockState(pos);
			if (!AmethystBlocks.isGeodeBlock(state)) {
				continue;
			}

			minX = Math.min(minX, pos.getX());
			minY = Math.min(minY, pos.getY());
			minZ = Math.min(minZ, pos.getZ());
			maxX = Math.max(maxX, pos.getX());
			maxY = Math.max(maxY, pos.getY());
			maxZ = Math.max(maxZ, pos.getZ());

			GrowthStage stage = AmethystBlocks.stageOf(state);
			if (stage != null) {
				counts = counts.plus(stage);
			} else if (state.is(Blocks.BUDDING_AMETHYST)) {
				budding++;
				for (Direction direction : Direction.values()) {
					BlockState neighbor = level.getBlockState(pos.relative(direction));
					if (BuddingAmethystBlock.canClusterGrowAtState(neighbor)) {
						free++;
					} else if (AmethystBlocks.stageOf(neighbor) != null && neighbor.getValue(AmethystClusterBlock.FACING) == direction) {
						own++;
					} else {
						blocked++;
					}
				}
			} else {
				amethyst++;
			}
		}

		BoundingBox bounds = minX == Integer.MAX_VALUE ? null : new BoundingBox(minX, minY, minZ, maxX, maxY, maxZ);
		return new GeodeCensus(counts, budding, amethyst, free, own, blocked, bounds);
	}
}
