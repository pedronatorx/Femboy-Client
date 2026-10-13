package io.github.theamethyx.nova.world;

import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import io.github.theamethyx.nova.core.research.GrowthStage;

/** Maps Minecraft block states to the analyzer's categories. Uses block identity only. */
public final class AmethystBlocks {
	private AmethystBlocks() {
	}

	/** Growth stage of a bud or cluster block, or {@code null} for anything else. */
	public static GrowthStage stageOf(BlockState state) {
		if (state.is(Blocks.SMALL_AMETHYST_BUD)) return GrowthStage.SMALL_BUD;
		if (state.is(Blocks.MEDIUM_AMETHYST_BUD)) return GrowthStage.MEDIUM_BUD;
		if (state.is(Blocks.LARGE_AMETHYST_BUD)) return GrowthStage.LARGE_BUD;
		if (state.is(Blocks.AMETHYST_CLUSTER)) return GrowthStage.CLUSTER;
		return null;
	}

	/** Blocks that make up a geode's inner layer or grow from it. */
	public static boolean isGeodeBlock(BlockState state) {
		return state.is(Blocks.AMETHYST_BLOCK) || state.is(Blocks.BUDDING_AMETHYST) || stageOf(state) != null;
	}

	/** Direction index (Direction ordinal) a growth object points to. */
	public static int facing(BlockState state) {
		return state.getValue(AmethystClusterBlock.FACING).ordinal();
	}
}
