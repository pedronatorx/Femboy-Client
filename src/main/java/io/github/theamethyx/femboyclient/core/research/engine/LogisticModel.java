package io.github.theamethyx.femboyclient.core.research.engine;

import java.util.function.ToDoubleFunction;

/**
 * One-feature logistic regression {@code p = 1 / (1 + exp(-(a + b x)))}, fitted by Newton's method
 * with a small L2 penalty (keeps the fit finite when classes separate perfectly). Used to check
 * whether a probability derived from the observed feature would be calibrated, i.e. whether "70%"
 * actually means 70% on held-out data.
 */
public final class LogisticModel {
	private static final double PENALTY = 1e-3;
	private final ToDoubleFunction<Features> feature;
	private double intercept;
	private double slope;

	public LogisticModel(ToDoubleFunction<Features> feature) {
		this.feature = feature;
	}

	public void fit(Features[] training, boolean[] labels) {
		double a = 0, b = 0;
		for (int iteration = 0; iteration < 100; iteration++) {
			double ga = 0, gb = 0, haa = PENALTY, hab = 0, hbb = PENALTY;
			for (int i = 0; i < training.length; i++) {
				double x = value(training[i]);
				double p = sigmoid(a + b * x);
				double y = labels[i] ? 1 : 0;
				ga += p - y;
				gb += (p - y) * x;
				double w = p * (1 - p);
				haa += w;
				hab += w * x;
				hbb += w * x * x;
			}

			ga += PENALTY * a;
			gb += PENALTY * b;
			double det = haa * hbb - hab * hab;
			if (Math.abs(det) < 1e-12) {
				break;
			}

			double da = (hbb * ga - hab * gb) / det;
			double db = (haa * gb - hab * ga) / det;
			a -= da;
			b -= db;
			if (Math.abs(da) < 1e-9 && Math.abs(db) < 1e-9) {
				break;
			}
		}

		intercept = a;
		slope = b;
	}

	private double value(Features f) {
		double v = feature.applyAsDouble(f);
		return Double.isNaN(v) ? 0 : v;
	}

	public double probability(Features f) {
		return sigmoid(intercept + slope * value(f));
	}

	public double intercept() {
		return intercept;
	}

	public double slope() {
		return slope;
	}

	private static double sigmoid(double z) {
		return 1 / (1 + Math.exp(-z));
	}
}
