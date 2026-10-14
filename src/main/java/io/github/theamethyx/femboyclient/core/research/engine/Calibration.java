package io.github.theamethyx.femboyclient.core.research.engine;

import java.util.ArrayList;
import java.util.List;

/** Calibration of predicted probabilities against outcomes: Brier score and reliability bins. */
public record Calibration(double brierScore, double expectedCalibrationError, List<Bin> bins) {
	/** One reliability bin: predictions in [low, high), their mean, and the observed positive rate. */
	public record Bin(double low, double high, int count, double meanPredicted, double observedRate) {
	}

	public static final int BINS = 5;

	public static Calibration of(double[] predicted, boolean[] actual) {
		int n = predicted.length;
		double brier = 0;
		int[] counts = new int[BINS];
		double[] sumPredicted = new double[BINS];
		int[] positives = new int[BINS];

		for (int i = 0; i < n; i++) {
			double y = actual[i] ? 1 : 0;
			brier += (predicted[i] - y) * (predicted[i] - y);
			int bin = Math.min(BINS - 1, (int) (predicted[i] * BINS));
			counts[bin]++;
			sumPredicted[bin] += predicted[i];
			if (actual[i]) positives[bin]++;
		}

		List<Bin> bins = new ArrayList<>();
		double ece = 0;
		for (int b = 0; b < BINS; b++) {
			double mean = counts[b] == 0 ? Double.NaN : sumPredicted[b] / counts[b];
			double rate = counts[b] == 0 ? Double.NaN : (double) positives[b] / counts[b];
			bins.add(new Bin((double) b / BINS, (double) (b + 1) / BINS, counts[b], mean, rate));
			if (counts[b] > 0) {
				ece += (double) counts[b] / n * Math.abs(mean - rate);
			}
		}

		return new Calibration(n == 0 ? Double.NaN : brier / n, n == 0 ? Double.NaN : ece, bins);
	}
}
