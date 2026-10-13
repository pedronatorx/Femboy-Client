package io.github.theamethyx.nova.core.research.engine;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

import io.github.theamethyx.nova.core.research.geode.GeodeObservation;

/**
 * Evaluates what can actually be inferred from geode growth-stage observations about how long a
 * geode's chunk was randomly ticked.
 *
 * <p>Protocol: samples are split by geode (every third geode, sorted by id, goes to the test set);
 * each hypothesis's rule is fitted on training geodes only and scored once on test geodes; verdicts
 * use criteria fixed in {@link Hypothesis} before any data was generated. Classifiers only ever
 * receive {@link Features}.
 */
public final class ActivityResearchEngine {
	public enum Verdict { SUPPORTED, UNSUPPORTED, INCONCLUSIVE }

	/** Settings of one evaluation. Incomplete observations are excluded unless requested. */
	public record Config(boolean includeIncomplete, List<Hypothesis> hypotheses) {
		public static Config defaults() {
			return new Config(false, Hypothesis.defaults());
		}
	}

	public record ConditionResult(String condition, Metrics metrics) {
	}

	public record HypothesisResult(Hypothesis hypothesis, int trainSamples, int testSamples, int testPositives, int testNegatives,
			double threshold, Metrics train, Metrics test, double logisticIntercept, double logisticSlope, Calibration calibration,
			List<ConditionResult> byCondition, Verdict verdict, String reason) {
	}

	/** Mean and spread of the cluster share at one exposure level (all complete samples). */
	public record CurvePoint(double exposure, int samples, double mean, double sd, double min, double max) {
	}

	public record Report(int totalSamples, int usedSamples, int excludedIncomplete, int trainGeodes, int testGeodes,
			List<HypothesisResult> results, List<CurvePoint> curve, int monotoneGeodes, int checkedGeodes, Map<String, String> metadata) {
	}

	public Report evaluate(Dataset dataset, Config config) {
		List<Dataset.Sample> used = new ArrayList<>();
		int excluded = 0;
		for (Dataset.Sample sample : dataset.samples()) {
			if (!sample.observation().complete() && !config.includeIncomplete()) {
				excluded++;
			} else {
				used.add(sample);
			}
		}

		TreeSet<String> groups = new TreeSet<>();
		used.forEach(sample -> groups.add(sample.groupId()));
		List<String> ordered = new ArrayList<>(groups);
		java.util.Set<String> testGroups = new java.util.HashSet<>();
		for (int i = 0; i < ordered.size(); i++) {
			if (i % 3 == 2) testGroups.add(ordered.get(i));
		}

		List<HypothesisResult> results = new ArrayList<>();
		for (Hypothesis hypothesis : config.hypotheses()) {
			results.add(evaluate(dataset, used, testGroups, hypothesis));
		}

		return new Report(dataset.size(), used.size(), excluded, groups.size() - testGroups.size(), testGroups.size(), results,
				curve(dataset, used), monotoneGroups(dataset, used), countSweepGroups(dataset, used), Map.copyOf(dataset.metadata()));
	}

	private HypothesisResult evaluate(Dataset dataset, List<Dataset.Sample> used, java.util.Set<String> testGroups, Hypothesis h) {
		List<Features> trainFeatures = new ArrayList<>();
		List<Boolean> trainLabels = new ArrayList<>();
		List<Features> testFeatures = new ArrayList<>();
		List<Boolean> testLabels = new ArrayList<>();
		List<String> testConditions = new ArrayList<>();

		for (Dataset.Sample sample : used) {
			Dataset.Truth truth = dataset.truth(sample.sampleId());
			if (truth.exposureRandomTicks() < h.minimumExposure()) {
				continue;
			}

			Features features = Features.of(sample.observation());
			if (Double.isNaN(h.feature().applyAsDouble(features))) {
				continue; // e.g. no growth objects: the feature is undefined, not zero
			}

			boolean label = truth.exposureRandomTicks() >= h.positiveAtLeast();
			if (testGroups.contains(sample.groupId())) {
				testFeatures.add(features);
				testLabels.add(label);
				testConditions.add(truth.condition());
			} else {
				trainFeatures.add(features);
				trainLabels.add(label);
			}
		}

		Features[] train = trainFeatures.toArray(Features[]::new);
		Features[] test = testFeatures.toArray(Features[]::new);
		boolean[] yTrain = toArray(trainLabels);
		boolean[] yTest = toArray(testLabels);

		ThresholdClassifier classifier;
		if (!Double.isNaN(h.fixedThreshold())) {
			classifier = ThresholdClassifier.fixed(h.featureName(), h.feature(), h.fixedThreshold());
		} else {
			classifier = new ThresholdClassifier(h.featureName(), h.feature());
			if (train.length > 0) {
				classifier.fit(train, yTrain);
			}
		}

		if (train.length == 0 || test.length == 0 || Double.isNaN(classifier.threshold())) {
			return new HypothesisResult(h, train.length, test.length, count(yTest, true), count(yTest, false), Double.NaN,
					Metrics.of(new boolean[0], new boolean[0]), Metrics.of(new boolean[0], new boolean[0]), Double.NaN, Double.NaN,
					Calibration.of(new double[0], new boolean[0]), List.of(), Verdict.INCONCLUSIVE, "Not enough samples to evaluate.");
		}

		Metrics trainMetrics = Metrics.of(yTrain, classifier.predict(train));
		boolean[] predicted = classifier.predict(test);
		Metrics testMetrics = Metrics.of(yTest, predicted);

		LogisticModel logistic = new LogisticModel(h.feature());
		logistic.fit(train, yTrain);
		double[] probabilities = new double[test.length];
		for (int i = 0; i < test.length; i++) probabilities[i] = logistic.probability(test[i]);
		Calibration calibration = Calibration.of(probabilities, yTest);

		Map<String, List<Integer>> byCondition = new LinkedHashMap<>();
		for (int i = 0; i < test.length; i++) byCondition.computeIfAbsent(testConditions.get(i), c -> new ArrayList<>()).add(i);
		List<ConditionResult> conditions = new ArrayList<>();
		byCondition.forEach((condition, indices) -> {
			boolean[] actual = new boolean[indices.size()];
			boolean[] pred = new boolean[indices.size()];
			for (int k = 0; k < indices.size(); k++) {
				actual[k] = yTest[indices.get(k)];
				pred[k] = predicted[indices.get(k)];
			}

			conditions.add(new ConditionResult(condition, Metrics.of(actual, pred)));
		});

		String[] verdict = verdict(testMetrics);
		return new HypothesisResult(h, train.length, test.length, testMetrics.positives(), testMetrics.negatives(), classifier.threshold(),
				trainMetrics, testMetrics, logistic.intercept(), logistic.slope(), calibration, conditions, Verdict.valueOf(verdict[0]), verdict[1]);
	}

	/** Applies the pre-registered criteria from {@link Hypothesis}. */
	static String[] verdict(Metrics test) {
		if (test.total() < Hypothesis.SUPPORT_MIN_TEST_SAMPLES || test.positives() < Hypothesis.SUPPORT_MIN_PER_CLASS
				|| test.negatives() < Hypothesis.SUPPORT_MIN_PER_CLASS) {
			return new String[] {"INCONCLUSIVE", "The test set is too small (needs " + Hypothesis.SUPPORT_MIN_TEST_SAMPLES + " samples and "
					+ Hypothesis.SUPPORT_MIN_PER_CLASS + " per class)."};
		}

		double balanced = test.balancedAccuracy();
		double fpr = test.falsePositiveRate().value();
		double fnr = test.falseNegativeRate().value();
		String numbers = String.format(java.util.Locale.ROOT, "balanced accuracy %.2f, false-positive rate %.2f, false-negative rate %.2f", balanced, fpr, fnr);
		if (balanced >= Hypothesis.SUPPORT_MIN_BALANCED_ACCURACY && fpr <= Hypothesis.SUPPORT_MAX_ERROR_RATE && fnr <= Hypothesis.SUPPORT_MAX_ERROR_RATE) {
			return new String[] {"SUPPORTED", "Meets all pre-registered criteria on held-out geodes (" + numbers + ")."};
		}

		if (balanced < Hypothesis.UNSUPPORTED_BELOW_BALANCED_ACCURACY) {
			return new String[] {"UNSUPPORTED", "Performance on held-out geodes is close to chance (" + numbers + ")."};
		}

		return new String[] {"INCONCLUSIVE", "Some criteria are not met (" + numbers + ")."};
	}

	private static List<CurvePoint> curve(Dataset dataset, List<Dataset.Sample> used) {
		TreeMap<Double, List<Double>> byExposure = new TreeMap<>();
		for (Dataset.Sample sample : used) {
			Dataset.Truth truth = dataset.truth(sample.sampleId());
			double value = Features.of(sample.observation()).clusterFraction();
			if (!"sweep".equals(truth.condition()) || Double.isNaN(value)) continue;
			byExposure.computeIfAbsent(truth.exposureRandomTicks(), e -> new ArrayList<>()).add(value);
		}

		List<CurvePoint> points = new ArrayList<>();
		byExposure.forEach((exposure, values) -> {
			double mean = values.stream().mapToDouble(Double::doubleValue).average().orElse(Double.NaN);
			double sd = Math.sqrt(values.stream().mapToDouble(v -> (v - mean) * (v - mean)).sum() / Math.max(1, values.size() - 1));
			double min = values.stream().mapToDouble(Double::doubleValue).min().orElse(Double.NaN);
			double max = values.stream().mapToDouble(Double::doubleValue).max().orElse(Double.NaN);
			points.add(new CurvePoint(exposure, values.size(), mean, sd, min, max));
		});
		return points;
	}

	/** Geodes in the "sweep" arm whose cluster count never decreases as exposure increases. */
	private static int monotoneGroups(Dataset dataset, List<Dataset.Sample> used) {
		int monotone = 0;
		for (List<Dataset.Sample> series : sweepSeries(dataset, used).values()) {
			boolean ok = true;
			for (int i = 1; i < series.size(); i++) {
				if (series.get(i).observation().counts().cluster() < series.get(i - 1).observation().counts().cluster()) ok = false;
			}

			if (ok) monotone++;
		}

		return monotone;
	}

	private static int countSweepGroups(Dataset dataset, List<Dataset.Sample> used) {
		return sweepSeries(dataset, used).size();
	}

	private static Map<String, List<Dataset.Sample>> sweepSeries(Dataset dataset, List<Dataset.Sample> used) {
		Map<String, List<Dataset.Sample>> series = new TreeMap<>();
		for (Dataset.Sample sample : used) {
			if ("sweep".equals(dataset.truth(sample.sampleId()).condition())) {
				series.computeIfAbsent(sample.groupId(), g -> new ArrayList<>()).add(sample);
			}
		}

		series.values().forEach(list -> list.sort(java.util.Comparator.comparingDouble(s -> dataset.truth(s.sampleId()).exposureRandomTicks())));
		return series;
	}

	private static boolean[] toArray(List<Boolean> values) {
		boolean[] array = new boolean[values.size()];
		for (int i = 0; i < array.length; i++) array[i] = values.get(i);
		return array;
	}

	private static int count(boolean[] values, boolean target) {
		int n = 0;
		for (boolean v : values) if (v == target) n++;
		return n;
	}

	/** Convenience for callers that build a dataset from observations of one geode over time. */
	public static Dataset.Sample sample(String id, String group, GeodeObservation observation) {
		return new Dataset.Sample(id, group, observation);
	}
}
