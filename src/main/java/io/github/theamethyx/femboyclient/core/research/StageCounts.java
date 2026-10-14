package io.github.theamethyx.femboyclient.core.research;

/** Number of observed growth objects in each stage. */
public record StageCounts(int small, int medium, int large, int cluster) {
	public static final StageCounts EMPTY = new StageCounts(0, 0, 0, 0);

	public StageCounts {
		if (small < 0 || medium < 0 || large < 0 || cluster < 0) {
			throw new IllegalArgumentException("Counts cannot be negative");
		}
	}

	public int get(GrowthStage stage) {
		return switch (stage) {
			case SMALL_BUD -> small;
			case MEDIUM_BUD -> medium;
			case LARGE_BUD -> large;
			case CLUSTER -> cluster;
		};
	}

	public StageCounts plus(GrowthStage stage) {
		return switch (stage) {
			case SMALL_BUD -> new StageCounts(small + 1, medium, large, cluster);
			case MEDIUM_BUD -> new StageCounts(small, medium + 1, large, cluster);
			case LARGE_BUD -> new StageCounts(small, medium, large + 1, cluster);
			case CLUSTER -> new StageCounts(small, medium, large, cluster + 1);
		};
	}

	public StageCounts plus(StageCounts other) {
		return new StageCounts(small + other.small, medium + other.medium, large + other.large, cluster + other.cluster);
	}

	/** All growth objects: small + medium + large buds + clusters. */
	public int total() {
		return small + medium + large + cluster;
	}

	/** Share of one stage among all growth objects. */
	public Proportion share(GrowthStage stage) {
		return new Proportion(get(stage), total(), stage.label().toLowerCase(java.util.Locale.ROOT) + "s", "growth objects");
	}

	/** Fully grown clusters among <em>all</em> growth objects (small + medium + large + cluster). */
	public Proportion clustersOfAll() {
		return new Proportion(cluster, total(), "clusters", "growth objects");
	}

	/**
	 * Fully grown clusters among large buds and clusters <em>only</em>. A different statistic from
	 * {@link #clustersOfAll()}: it ignores small and medium buds entirely.
	 */
	public Proportion clustersOfLargeAndClusters() {
		return new Proportion(cluster, large + cluster, "clusters", "large buds + clusters");
	}
}
