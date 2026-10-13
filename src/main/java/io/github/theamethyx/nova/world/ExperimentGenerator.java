package io.github.theamethyx.nova.world;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Locale;

import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import io.github.theamethyx.nova.core.research.engine.Dataset;
import io.github.theamethyx.nova.core.research.geode.GeodeClusterer;
import io.github.theamethyx.nova.core.research.geode.GeodeObservation;

/**
 * Generates a labelled dataset with Minecraft's own code, one small step per server tick so the
 * server never stalls. Arms of the experiment:
 * <ul>
 *   <li><b>sweep</b>: each geode is observed after 0, 10, 20 ... 2000 random ticks (repeated
 *       observations of the same region at different observation times);</li>
 *   <li><b>harvested</b>: long-exposed geodes after 70% of their clusters were removed, as a player
 *       mining them would (expected to look younger than they are);</li>
 *   <li><b>harvested-regrowth</b>: the same geodes after 30 more minutes of growth;</li>
 *   <li><b>partial-scan</b>: sweep geodes observed with half of their chunk columns unreadable.</li>
 * </ul>
 * Ground truth (exposure, arm) is recorded in the dataset's separate truth map. Server thread only.
 */
public final class ExperimentGenerator {
	public record Config(List<Long> sweepSeeds, List<Integer> exposures, List<Long> harvestSeeds, int harvestExposure, double harvestFraction,
			int regrowthTicks, List<Integer> partialAt) {
		public static Config standard() {
			List<Long> sweep = new ArrayList<>();
			for (long s = 2001; s <= 2040; s++) sweep.add(s);
			List<Long> harvest = new ArrayList<>();
			for (long s = 3001; s <= 3012; s++) harvest.add(s);
			return new Config(sweep, List.of(0, 10, 20, 35, 52, 75, 100, 130, 180, 300, 520, 800, 1300, 2000), harvest, 1300, 0.7, 26,
					List.of(0, 180));
		}

		/** A small configuration for quick in-game runs. */
		public static Config quick() {
			List<Long> sweep = new ArrayList<>();
			for (long s = 2001; s <= 2015; s++) sweep.add(s);
			return new Config(sweep, List.of(0, 20, 52, 100, 130, 300, 1300), List.of(3001L, 3002L, 3003L), 1300, 0.7, 26, List.of(180));
		}

		public int totalSteps() {
			// Per sweep geode: build + one step per exposure; per harvest geode: build, grow, harvest, regrow.
			return sweepSeeds.size() * (1 + exposures.size()) + harvestSeeds.size() * 4;
		}
	}

	private final Config config;
	private final BlockPos center;
	private final Dataset dataset = new Dataset();
	/** One unit of work; {@code seed} lets a refused geode's remaining steps be skipped. */
	private record Step(long seed, Runnable action) {
	}

	private final Deque<Step> steps = new ArrayDeque<>();
	private final GeodeClusterer clusterer = new GeodeClusterer();
	private final int totalSteps;
	private ServerLevel level;
	private RandomSource random;
	private int exposure;
	private int done;
	private int skippedGeodes;
	private String status = "Waiting";

	public ExperimentGenerator(Config config, BlockPos center) {
		this.config = config;
		// Chunk-aligned on X so the partial-scan arm can make exactly the eastern half unreadable.
		this.center = new BlockPos(Math.floorDiv(center.getX(), 16) * 16, center.getY(), center.getZ());
		this.totalSteps = config.totalSteps();
		for (long seed : config.sweepSeeds()) {
			steps.add(new Step(seed, () -> buildGeode(seed)));
			int previous = 0;
			for (int level : config.exposures()) {
				int delta = level - previous;
				previous = level;
				steps.add(new Step(seed, () -> growAndObserve(seed, delta)));
			}
		}

		for (long seed : config.harvestSeeds()) {
			steps.add(new Step(seed, () -> buildGeode(seed)));
			steps.add(new Step(seed, () -> growAndObserveHarvestBaseline(seed)));
			steps.add(new Step(seed, () -> harvestAndObserve(seed)));
			steps.add(new Step(seed, () -> regrowAndObserve(seed)));
		}

		dataset.metadata().put("Minecraft", SharedConstants.getCurrentVersion().name());
		dataset.metadata().put("Method", "vanilla minecraft:amethyst_geode feature in a stone cube, growth by calling BlockState.randomTick on "
				+ "every budding block with a seeded RandomSource; observations by the analyzer's grouping code on server block data");
		dataset.metadata().put("Sweep geodes", config.sweepSeeds().size() + " (feature seeds " + config.sweepSeeds().getFirst() + "-" + config.sweepSeeds().getLast() + ")");
		dataset.metadata().put("Exposure levels (random ticks)", config.exposures().toString());
		dataset.metadata().put("Harvest arm", config.harvestSeeds().size() + " geodes: " + config.harvestExposure() + " random ticks, then "
				+ Math.round(config.harvestFraction() * 100) + "% of clusters removed, then " + config.regrowthTicks() + " more random ticks");
	}

	/** Runs one step. Returns false when everything is done. */
	public boolean step(ServerLevel serverLevel) {
		if (this.level == null) {
			// Geode shapes also depend on world-seeded noise and position: record both for traceability.
			dataset.metadata().put("World seed", String.valueOf(serverLevel.getSeed()));
			dataset.metadata().put("Geode center", center.toShortString());
		}

		this.level = serverLevel;
		Step next = steps.poll();
		if (next == null) {
			status = "Done";
			return false;
		}

		next.action().run();
		done++;
		return !steps.isEmpty();
	}

	public boolean finished() {
		return steps.isEmpty();
	}

	public float progress() {
		return totalSteps == 0 ? 1 : (float) done / totalSteps;
	}

	public String status() {
		return status;
	}

	public Dataset dataset() {
		return dataset;
	}

	public int skippedGeodes() {
		return skippedGeodes;
	}

	private BoundingBox region() {
		return GeodeFixtureBuilder.clearedBox(center);
	}

	private void buildGeode(long seed) {
		status = String.format(Locale.ROOT, "Building geode %d", seed);
		GeodeFixtureBuilder.Result result = GeodeFixtureBuilder.build(level, center, seed, 0);
		random = RandomSource.create(seed ^ 0x5DEECE66DL);
		exposure = 0;
		if (!result.placed() || result.census().buddingBlocks() == 0) {
			// The feature can refuse a seed; drop this geode's remaining steps (reported as skipped).
			skippedGeodes++;
			while (!steps.isEmpty() && steps.peek().seed() == seed) {
				steps.poll();
				done++;
			}
		}
	}

	private void growAndObserve(long seed, int delta) {
		grow(delta);
		observe(seed, "sweep", "");
		if (config.partialAt().contains(exposure)) {
			observePartial(seed);
		}
	}

	private void growAndObserveHarvestBaseline(long seed) {
		grow(config.harvestExposure());
		observe(seed, "harvest-baseline", "");
	}

	private void harvestAndObserve(long seed) {
		BoundingBox region = region();
		List<BlockPos> clusters = new ArrayList<>();
		for (BlockPos pos : BlockPos.betweenClosed(region.minX(), region.minY(), region.minZ(), region.maxX(), region.maxY(), region.maxZ())) {
			if (level.getBlockState(pos).is(Blocks.AMETHYST_CLUSTER)) clusters.add(pos.immutable());
		}

		clusters.sort(Comparator.comparingLong(BlockPos::asLong));
		RandomSource harvest = RandomSource.create(seed * 31 + 7);
		for (BlockPos pos : clusters) {
			if (harvest.nextDouble() < config.harvestFraction()) {
				level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
			}
		}

		observe(seed, "harvested", "");
	}

	private void regrowAndObserve(long seed) {
		grow(config.regrowthTicks());
		observe(seed, "harvested-regrowth", "");
	}

	private void grow(int ticks) {
		if (ticks > 0) {
			GeodeFixtureBuilder.applyRandomTicks(level, region(), ticks, random);
			exposure += ticks;
		}

		status = String.format(Locale.ROOT, "Exposure %d random ticks", exposure);
	}

	private void observe(long seed, String condition, String suffix) {
		GeodeObservation geode = largest(clusterer.cluster(ServerScan.scan(level, region(), (cx, cz) -> true), GeodeClusterer.Params.DEFAULT,
				System.currentTimeMillis(), "server scan"));
		if (geode != null) {
			String id = "seed" + seed + "-e" + exposure + "-" + condition + suffix;
			dataset.add(new Dataset.Sample(id, "seed" + seed, geode), new Dataset.Truth(exposure, condition));
		}
	}

	/** Same geode, but only chunk columns west of its center (x below the center) are readable. */
	private void observePartial(long seed) {
		int splitChunk = center.getX() >> 4;
		GeodeObservation geode = largest(clusterer.cluster(ServerScan.scan(level, region(), (cx, cz) -> cx < splitChunk),
				GeodeClusterer.Params.DEFAULT, System.currentTimeMillis(), "server scan (half unreadable)"));
		if (geode != null) {
			String id = "seed" + seed + "-e" + exposure + "-partial-scan";
			dataset.add(new Dataset.Sample(id, "seed" + seed, geode), new Dataset.Truth(exposure, "partial-scan"));
		}
	}

	private static GeodeObservation largest(List<GeodeObservation> geodes) {
		return geodes.stream().max(Comparator.comparingInt(g -> g.buddingBlocks() + g.amethystBlocks())).orElse(null);
	}
}
