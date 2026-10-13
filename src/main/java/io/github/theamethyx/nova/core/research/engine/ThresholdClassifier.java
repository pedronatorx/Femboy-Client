package io.github.theamethyx.nova.core.research.engine;

import java.util.Arrays;
import java.util.function.ToDoubleFunction;

/**
 * Transparent baseline: predicts "long exposure" when one observed feature is at least a threshold.
 * The threshold is chosen on training data only, by maximizing Youden's J (recall + specificity - 1);
 * ties go to the smallest threshold. It is then frozen and applied to the test data.
 */
public final class ThresholdClassifier {
	private final String featureName;
	private final ToDoubleFunction<Features> feature;
	private double threshold = Double.NaN;

	public ThresholdClassifier(String featureName, ToDoubleFunction<Features> feature) {
		this.featureName = featureName;
		this.feature = feature;
	}

	/** Creates a classifier with a fixed threshold (no fitting), e.g. "any clusters at all". */
	public static ThresholdClassifier fixed(String featureName, ToDoubleFunction<Features> feature, double threshold) {
		ThresholdClassifier classifier = new ThresholdClassifier(featureName, feature);
		classifier.threshold = threshold;
		return classifier;
	}

	public void fit(Features[] training, boolean[] labels) {
		double[] values = Arrays.stream(training).mapToDouble(feature).filter(v -> !Double.isNaN(v)).distinct().sorted().toArray();
		if (values.length == 0) {
			throw new IllegalArgumentException("No usable training values for " + featureName);
		}

		double best = Double.NEGATIVE_INFINITY;
		double bestThreshold = values[0];
		for (int i = 0; i < values.length; i++) {
			// Candidate thresholds: each observed value and the midpoint below it.
			double[] candidates = i == 0 ? new double[] {values[0]} : new double[] {(values[i - 1] + values[i]) / 2, values[i]};
			for (double candidate : candidates) {
				Metrics metrics = Metrics.of(labels, predictAll(training, candidate));
				double j = metrics.recall().value() - metrics.falsePositiveRate().value();
				if (!Double.isNaN(j) && j > best + 1e-12) {
					best = j;
					bestThreshold = candidate;
				}
			}
		}

		threshold = bestThreshold;
	}

	private boolean[] predictAll(Features[] samples, double t) {
		boolean[] predicted = new boolean[samples.length];
		for (int i = 0; i < samples.length; i++) {
			double v = feature.applyAsDouble(samples[i]);
			predicted[i] = !Double.isNaN(v) && v >= t;
		}

		return predicted;
	}

	public boolean[] predict(Features[] samples) {
		if (Double.isNaN(threshold)) {
			throw new IllegalStateException("Classifier is not fitted");
		}

		return predictAll(samples, threshold);
	}

	public double threshold() {
		return threshold;
	}

	public String featureName() {
		return featureName;
	}
}
