package io.github.theamethyx.femboyclient.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import net.fabricmc.fabric.api.gametest.v1.GameTest;

import io.github.theamethyx.femboyclient.core.research.StageCounts;
import io.github.theamethyx.femboyclient.world.GeodeCensus;
import io.github.theamethyx.femboyclient.world.GeodeFixtureBuilder;

/**
 * Headless server GameTests for the geode research fixtures. They run Minecraft's real geode
 * feature and random-tick code, so they verify game behavior the research depends on.
 */
public class ServerGameTest {
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
		StringBuilder log = new StringBuilder("Femboy Client growth curve (seed 1001):");
		for (int ticks : new int[] {0, 10, 20, 40, 60, 90, 120, 180, 300}) {
			GeodeCensus census = GeodeFixtureBuilder.build(level, center, 1001, ticks).census();
			log.append("\n  ticks=").append(ticks).append(" counts=").append(census.counts()).append(" budding=").append(census.buddingBlocks())
					.append(" free=").append(census.freeFaces()).append(" own=").append(census.ownGrowthFaces()).append(" blocked=").append(census.blockedFaces());
		}

		org.slf4j.LoggerFactory.getLogger("FemboyClient").info(log.toString());
		helper.succeed();
	}

	/**
	 * Runs the full research experiment (about 650 steps, one per tick) and writes the evaluation to
	 * {@code <run dir>/femboyclient-research}. Verdicts are research results, so they are reported, not asserted.
	 */
	@GameTest(maxTicks = 1200)
	public void researchExperiment(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		io.github.theamethyx.femboyclient.world.ExperimentGenerator generator = new io.github.theamethyx.femboyclient.world.ExperimentGenerator(
				io.github.theamethyx.femboyclient.world.ExperimentGenerator.Config.standard(), helper.absolutePos(new BlockPos(4, 100, 4)));
		boolean[] reported = {false};
		helper.onEachTick(() -> {
			if (reported[0]) {
				return;
			}

			long deadline = System.nanoTime() + 40_000_000L;
			while (!generator.finished() && System.nanoTime() < deadline) {
				generator.step(level);
			}

			if (!generator.finished()) {
				return;
			}

			reported[0] = true;
			var dataset = generator.dataset();
			helper.assertTrue(dataset.size() >= 500, "experiment produced " + dataset.size() + " observations");
			var engine = new io.github.theamethyx.femboyclient.core.research.engine.ActivityResearchEngine();
			var main = engine.evaluate(dataset, io.github.theamethyx.femboyclient.core.research.engine.ActivityResearchEngine.Config.defaults());
			var withIncomplete = engine.evaluate(dataset, new io.github.theamethyx.femboyclient.core.research.engine.ActivityResearchEngine.Config(true,
					io.github.theamethyx.femboyclient.core.research.engine.Hypothesis.defaults()));
			try {
				java.nio.file.Path dir = net.fabricmc.loader.api.FabricLoader.getInstance().getGameDir().resolve("femboyclient-research");
				java.nio.file.Files.createDirectories(dir);
				java.nio.file.Files.writeString(dir.resolve("evaluation.md"), io.github.theamethyx.femboyclient.core.research.engine.EvaluationReportWriter
						.markdown(main, "Amethyst growth as an exposure indicator: controlled evaluation"));
				java.nio.file.Files.writeString(dir.resolve("evaluation-including-incomplete.md"), io.github.theamethyx.femboyclient.core.research.engine.EvaluationReportWriter
						.markdown(withIncomplete, "Same evaluation including incomplete observations"));
				java.nio.file.Files.writeString(dir.resolve("evaluation-summary.json"), io.github.theamethyx.femboyclient.core.research.engine.EvaluationReportWriter
						.summary(main, "headless", "Controlled evaluation (headless GameTest)", System.currentTimeMillis()).toString());
				StringBuilder csv = new StringBuilder("sample,geode,condition,exposure_random_ticks,small,medium,large,cluster,budding,complete\n");
				var truth = dataset.truthView();
				for (var sample : dataset.samples()) {
					var c = sample.observation().counts();
					var t = truth.get(sample.sampleId());
					csv.append(sample.sampleId()).append(',').append(sample.groupId()).append(',').append(t.condition()).append(',')
							.append((long) t.exposureRandomTicks()).append(',').append(c.small()).append(',').append(c.medium()).append(',')
							.append(c.large()).append(',').append(c.cluster()).append(',').append(sample.observation().buddingBlocks()).append(',')
							.append(sample.observation().complete()).append('\n');
				}

				java.nio.file.Files.writeString(dir.resolve("dataset.csv"), csv.toString());
				org.slf4j.LoggerFactory.getLogger("FemboyClient").info("Research experiment: {} observations, {} geodes skipped; reports in {}",
						dataset.size(), generator.skippedGeodes(), dir.toAbsolutePath());
				main.results().forEach(r -> org.slf4j.LoggerFactory.getLogger("FemboyClient").info("  {} {}: {} - {}", r.hypothesis().id(), r.hypothesis().title(), r.verdict(), r.reason()));
			} catch (java.io.IOException e) {
				throw helper.assertionException("could not write research report: " + e.getMessage());
			}

			helper.succeed();
		});
	}
}
