package io.github.theamethyx.femboyclient.core.research.engine;

import java.util.Locale;

import io.github.theamethyx.femboyclient.core.research.Proportion;

/** Confusion-matrix statistics of a binary classifier. Rates are NaN when undefined. */
public record Metrics(int truePositives, int falsePositives, int trueNegatives, int falseNegatives) {
	public static Metrics of(boolean[] actual, boolean[] predicted) {
		int tp = 0, fp = 0, tn = 0, fn = 0;
		for (int i = 0; i < actual.length; i++) {
			if (actual[i] && predicted[i]) tp++;
			else if (!actual[i] && predicted[i]) fp++;
			else if (!actual[i]) tn++;
			else fn++;
		}

		return new Metrics(tp, fp, tn, fn);
	}

	public int total() {
		return truePositives + falsePositives + trueNegatives + falseNegatives;
	}

	public int positives() {
		return truePositives + falseNegatives;
	}

	public int negatives() {
		return trueNegatives + falsePositives;
	}

	/** Of the samples predicted positive, the share that are positive. */
	public Proportion precision() {
		return new Proportion(truePositives, truePositives + falsePositives, "true positives", "predicted positives");
	}

	/** Of the positive samples, the share predicted positive (sensitivity). */
	public Proportion recall() {
		return new Proportion(truePositives, positives(), "true positives", "actual positives");
	}

	/** Of the negative samples, the share wrongly predicted positive. */
	public Proportion falsePositiveRate() {
		return new Proportion(falsePositives, negatives(), "false positives", "actual negatives");
	}

	/** Of the positive samples, the share wrongly predicted negative. */
	public Proportion falseNegativeRate() {
		return new Proportion(falseNegatives, positives(), "false negatives", "actual positives");
	}

	public Proportion accuracy() {
		return new Proportion(truePositives + trueNegatives, total(), "correct predictions", "samples");
	}

	/** Mean of recall and specificity; robust to class imbalance. */
	public double balancedAccuracy() {
		double recall = recall().value();
		double specificity = 1 - falsePositiveRate().value();
		return (recall + specificity) / 2;
	}

	public static String percent(Proportion p) {
		if (!p.defined()) {
			return "n/a";
		}

		double[] ci = p.wilson95();
		return String.format(Locale.ROOT, "%.1f%% (%d/%d; 95%% CI %.1f-%.1f%%)", p.value() * 100, p.successes(), p.trials(), ci[0] * 100, ci[1] * 100);
	}
}
