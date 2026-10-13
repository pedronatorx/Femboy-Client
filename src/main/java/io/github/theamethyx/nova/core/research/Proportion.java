package io.github.theamethyx.nova.core.research;

/**
 * An observed proportion {@code successes / trials} with an explicit denominator and a Wilson score
 * confidence interval. The interval describes sampling uncertainty of the observed fraction; it is
 * <em>not</em> a probability that any hypothesis is true.
 */
public record Proportion(int successes, int trials, String numerator, String denominator) {
	/** z for a two-sided 95% interval. */
	public static final double Z95 = 1.959963984540054;

	public Proportion {
		if (successes < 0 || trials < 0 || successes > trials) {
			throw new IllegalArgumentException("Invalid proportion " + successes + "/" + trials);
		}
	}

	public boolean defined() {
		return trials > 0;
	}

	/** Observed fraction, or {@code NaN} when there are no trials. */
	public double value() {
		return trials == 0 ? Double.NaN : (double) successes / trials;
	}

	/** 95% Wilson score interval {@code [low, high]}; {@code [NaN, NaN]} without trials. */
	public double[] wilson95() {
		return wilson(successes, trials, Z95);
	}

	public static double[] wilson(int successes, int trials, double z) {
		if (trials == 0) {
			return new double[] {Double.NaN, Double.NaN};
		}

		double n = trials;
		double p = successes / n;
		double z2 = z * z;
		double center = (p + z2 / (2 * n)) / (1 + z2 / n);
		double half = z * Math.sqrt(p * (1 - p) / n + z2 / (4 * n * n)) / (1 + z2 / n);
		return new double[] {Math.max(0, center - half), Math.min(1, center + half)};
	}

	/** e.g. "25.0% (8 / 32 growth objects)". */
	public String describe() {
		if (!defined()) {
			return "undefined (no " + denominator + ")";
		}

		return String.format(java.util.Locale.ROOT, "%.1f%% (%d / %d %s)", value() * 100, successes, trials, denominator);
	}
}
