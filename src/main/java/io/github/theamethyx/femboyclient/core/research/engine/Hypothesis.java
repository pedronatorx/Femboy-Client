package io.github.theamethyx.femboyclient.core.research.engine;

import java.util.List;
import java.util.function.ToDoubleFunction;

/**
 * A testable statement, fixed before the data is seen.
 *
 * @param positiveAtLeast samples with at least this exposure (random ticks per budding block) are
 *                        labelled positive, the rest negative
 * @param minimumExposure samples below this exposure are left out (used to test discrimination
 *                        within a range, e.g. beyond saturation)
 * @param fixedThreshold  if not NaN, the rule's threshold is fixed in advance instead of fitted
 */
public record Hypothesis(String id, String title, String statement, String featureName, ToDoubleFunction<Features> feature,
		double positiveAtLeast, double minimumExposure, double fixedThreshold) {

	/** Random ticks that correspond to one hour of a loaded chunk at the default random tick speed (3). */
	public static final double TICKS_PER_HOUR = 72_000.0 * 3 / 4096;

	/** Pre-registered criteria: a hypothesis counts as supported only if all hold on the held-out test set. */
	public static final double SUPPORT_MIN_BALANCED_ACCURACY = 0.80;
	public static final double SUPPORT_MAX_ERROR_RATE = 0.20;
	public static final int SUPPORT_MIN_TEST_SAMPLES = 30;
	public static final int SUPPORT_MIN_PER_CLASS = 10;
	public static final double UNSUPPORTED_BELOW_BALANCED_ACCURACY = 0.65;

	public static double hoursToTicks(double hours) {
		return hours * TICKS_PER_HOUR;
	}

	/** The hypotheses evaluated by default. Written before any experiment was run. */
	public static List<Hypothesis> defaults() {
		return List.of(
				new Hypothesis("H1", "Cluster share separates short from long exposure",
						"Geodes whose budding blocks were randomly ticked for at least 2 hours (at default speed) show a higher share "
								+ "of clusters among all growth objects than geodes with less exposure, enough to classify them on unseen geodes.",
						"clusters / all growth objects", Features::clusterFraction, hoursToTicks(2), 0, Double.NaN),
				new Hypothesis("H1b", "Cluster share among large buds and clusters separates short from long exposure",
						"Same as H1 but using clusters / (large buds + clusters), which ignores small and medium buds.",
						"clusters / (large + clusters)", Features::clusterAmongLarge, hoursToTicks(2), 0, Double.NaN),
				new Hypothesis("H2", "Cluster share distinguishes durations beyond saturation",
						"Among geodes exposed for at least 5 hours, those exposed for at least 24 hours have a higher cluster share.",
						"clusters / all growth objects", Features::clusterFraction, hoursToTicks(24), hoursToTicks(5), Double.NaN),
				new Hypothesis("H3", "Any cluster indicates exposure (naive rule)",
						"A geode containing at least one fully grown cluster has been exposed for at least 30 minutes. Threshold fixed "
								+ "in advance at 'one or more clusters'; not fitted.",
						"cluster count", f -> f.growthObjects() == 0 ? 0 : Math.round(f.clusterFraction() * f.growthObjects()),
						hoursToTicks(0.5), 0, 1));
	}
}
