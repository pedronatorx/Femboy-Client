package io.github.theamethyx.nova.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import net.fabricmc.fabric.api.gametest.v1.GameTest;

import io.github.theamethyx.nova.core.research.StageCounts;
import io.github.theamethyx.nova.world.GeodeCensus;
import io.github.theamethyx.nova.world.GeodeFixtureBuilder;

/**
 * Headless server GameTests for the geode research fixtures. They run Minecraft's real geode
 * feature and random-tick code, so they verify game behavior the research depends on.
 */
public class NovaServerGameTest {
	/** Same seed and position: identical geode. More applied ticks: no stage ever goes backwards. */
	@GameTest(maxTicks = 100)
	public void geodeFixturesAreReproducibleAndGrowMonotonically(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos center = helper.absolutePos(new BlockPos(4, 40, 4));

		GeodeCensus first = GeodeFixtureBuilder.build(level, center, 1001, 0).census();
		GeodeCensus again = GeodeFixtureBuilder.build(level, center, 1001, 0).census();
		helper.assertTrue(first.geodeBounds() != null, "the geode feature placed a geode");
		helper.assertValueEqual(again.counts(), first.counts(), "fresh geode counts with the same seed");
		helper.assertValueEqual(again.buddingBlocks(), first.buddingBlocks(), "budding blocks with the same seed");
		helper.assertTrue(first.buddingBlocks() > 0, "a vanilla geode has budding amethyst");

		GeodeCensus grown1500 = GeodeFixtureBuilder.build(level, center, 1001, 1500).census();
		GeodeCensus grown6000 = GeodeFixtureBuilder.build(level, center, 1001, 6000).census();
		StageCounts c0 = first.counts();
		StageCounts c1 = grown1500.counts();
		StageCounts c2 = grown6000.counts();
		helper.assertTrue(c1.total() >= c0.total() && c2.total() >= c1.total(), "growth objects never disappear: " + c0 + " " + c1 + " " + c2);
		helper.assertTrue(c1.cluster() >= c0.cluster() && c2.cluster() >= c1.cluster(), "clusters never disappear: " + c0 + " " + c1 + " " + c2);
		helper.assertTrue(c2.cluster() > c0.cluster(), "6000 random ticks produce new clusters");
		helper.assertTrue(grown6000.freeFaces() <= first.freeFaces(), "free budding faces only get used up");
		helper.assertValueEqual(grown6000.buddingBlocks(), first.buddingBlocks(), "random ticks never change budding blocks");
		helper.succeed();
	}

	/** One budding block in open air: each of its six free faces grows to a cluster, never more. */
	@GameTest
	public void buddingAmethystGrowsOnlyIntoFreeFacesUntilClusters(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos budding = helper.absolutePos(new BlockPos(3, 3, 3));
		BoundingBox area = new BoundingBox(budding.getX() - 2, budding.getY() - 2, budding.getZ() - 2, budding.getX() + 2, budding.getY() + 2, budding.getZ() + 2);
		for (BlockPos pos : BlockPos.betweenClosed(area.minX(), area.minY(), area.minZ(), area.maxX(), area.maxY(), area.maxZ())) {
			level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
		}

		level.setBlockAndUpdate(budding, Blocks.BUDDING_AMETHYST.defaultBlockState());
		// Block one face so it must stay empty.
		level.setBlockAndUpdate(budding.relative(Direction.EAST), Blocks.STONE.defaultBlockState());

		GeodeFixtureBuilder.applyRandomTicks(level, area, 5000, 42);
		GeodeCensus census = GeodeCensus.of(level, area);

		helper.assertValueEqual(census.counts(), new StageCounts(0, 0, 0, 5), "five free faces, five clusters after 5000 ticks");
		for (Direction direction : Direction.values()) {
			var state = level.getBlockState(budding.relative(direction));
			if (direction == Direction.EAST) {
				helper.assertTrue(state.is(Blocks.STONE), "the blocked face is untouched");
			} else {
				helper.assertTrue(state.is(Blocks.AMETHYST_CLUSTER) && state.getValue(AmethystClusterBlock.FACING) == direction,
						"cluster on " + direction + " faces away from the budding block");
			}
		}

		helper.succeed();
	}

	/** The census counts exactly what was placed. */
	@GameTest
	public void censusCountsPlacedGrowthObjectsExactly(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos base = helper.absolutePos(new BlockPos(1, 2, 1));
		BoundingBox area = new BoundingBox(base.getX() - 1, base.getY() - 1, base.getZ() - 1, base.getX() + 6, base.getY() + 2, base.getZ() + 2);
		for (BlockPos pos : BlockPos.betweenClosed(area.minX(), area.minY(), area.minZ(), area.maxX(), area.maxY(), area.maxZ())) {
			level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
		}

		// A row of budding blocks with one growth object on top of each.
		var stages = new net.minecraft.world.level.block.Block[] {Blocks.SMALL_AMETHYST_BUD, Blocks.SMALL_AMETHYST_BUD, Blocks.MEDIUM_AMETHYST_BUD,
				Blocks.AMETHYST_CLUSTER, Blocks.AMETHYST_CLUSTER, Blocks.AMETHYST_CLUSTER};
		for (int i = 0; i < stages.length; i++) {
			BlockPos support = base.offset(i, 0, 0);
			level.setBlockAndUpdate(support, Blocks.BUDDING_AMETHYST.defaultBlockState());
			level.setBlockAndUpdate(support.above(), stages[i].defaultBlockState().setValue(AmethystClusterBlock.FACING, Direction.UP));
		}

		GeodeCensus census = GeodeCensus.of(level, area);
		helper.assertValueEqual(census.counts(), new StageCounts(2, 1, 0, 3), "stage counts");
		helper.assertValueEqual(census.buddingBlocks(), 6, "budding blocks");
		helper.assertValueEqual(census.ownGrowthFaces(), 6, "each budding block carries one growth object");
		helper.succeed();
	}

	/**
	 * Diagnostic, not an assertion about expected values: logs the census of the same geode after
	 * several exposures, so the growth curve can be inspected in the test log.
	 */
	@GameTest(maxTicks = 200)
	public void logGrowthCurve(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos center = helper.absolutePos(new BlockPos(4, 40, 60));
		StringBuilder log = new StringBuilder("NOVA growth curve (seed 1001):");
		for (int ticks : new int[] {0, 10, 20, 40, 60, 90, 120, 180, 300}) {
			GeodeCensus census = GeodeFixtureBuilder.build(level, center, 1001, ticks).census();
			log.append("\n  ticks=").append(ticks).append(" counts=").append(census.counts()).append(" budding=").append(census.buddingBlocks())
					.append(" free=").append(census.freeFaces()).append(" own=").append(census.ownGrowthFaces()).append(" blocked=").append(census.blockedFaces());
		}

		org.slf4j.LoggerFactory.getLogger("NOVA").info(log.toString());
		helper.succeed();
	}
}
