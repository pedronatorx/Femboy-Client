package io.github.theamethyx.nova.core.research.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;

import io.github.theamethyx.nova.core.research.StageCounts;
import io.github.theamethyx.nova.core.research.geode.GeodeObservation;

class ActivityResearchEngineTest {
	private static GeodeObservation observation(StageCounts counts, boolean complete) {
		return new GeodeObservation("g", "minecraft:overworld", 0, 0, 0, 1, 1, 1, counts, 10, 50,
				new GeodeObservation.FaceSummary(0, counts.total(), 10, 0), 0, complete, "", 0, null, "test", List.of());
	}

	/** One hypothesis: positive when exposure >= 100, rule on the cluster share. */
	private static ActivityResearchEngine.Config h1Only() {
		return new ActivityResearchEngine.Config(false, List.of(new Hypothesis("T", "test", "test", "clusters / all",
				Features::clusterFraction, 100, 0, Double.NaN)));
	}

	@Test
	void metricsFromAKnownConfusionMatrix() {
		Metrics m = new Metrics(8, 2, 6, 4);
		assertEquals(0.8, m.precision().value(), 1e-12);
		assertEquals(8.0 / 12, m.recall().value(), 1e-12);
		assertEquals(0.25, m.falsePositiveRate().value(), 1e-12);
		assertEquals(4.0 / 12, m.falseNegativeRate().value(), 1e-12);
		assertEquals((8.0 / 12 + 0.75) / 2, m.balancedAccuracy(), 1e-12);
	}

	@Test
	void separableDataIsSupportedAndThresholdLiesBetweenClasses() {
		Dataset data = new Dataset();
		for (int geode = 0; geode < 50; geode++) {
			// Short exposure: <= 30% clusters. Long exposure: >= 70% clusters.
			data.add(new Dataset.Sample("s" + geode + "a", "geode" + geode, observation(new StageCounts(7, 0, 0, 3), true)), new Dataset.Truth(10, "sweep"));
			data.add(new Dataset.Sample("s" + geode + "b", "geode" + geode, observation(new StageCounts(3, 0, 0, 7), true)), new Dataset.Truth(500, "sweep"));
		}

		ActivityResearchEngine.Report report = new ActivityResearchEngine().evaluate(data, h1Only());
		ActivityResearchEngine.HypothesisResult result = report.results().getFirst();

		assertEquals(ActivityResearchEngine.Verdict.SUPPORTED, result.verdict(), result.reason());
		assertTrue(result.threshold() > 0.3 && result.threshold() <= 0.7, "threshold " + result.threshold());
		assertEquals(0, result.test().falsePositives());
		assertEquals(0, result.test().falseNegatives());
		assertEquals(34, report.trainGeodes());
		assertEquals(16, report.testGeodes(), "every third geode is held out");
		assertEquals(50, report.monotoneGeodes());
	}

	@Test
	void noiseIsNotCalledSupported() {
		Dataset data = new Dataset();
		Random random = new Random(7);
		for (int geode = 0; geode < 60; geode++) {
			int clusters = random.nextInt(11);
			// The label is unrelated to the observation.
			data.add(new Dataset.Sample("s" + geode, "geode" + geode, observation(new StageCounts(10 - clusters, 0, 0, clusters), true)),
					new Dataset.Truth(random.nextBoolean() ? 500 : 10, "sweep"));
		}

		ActivityResearchEngine.HypothesisResult result = new ActivityResearchEngine().evaluate(data, h1Only()).results().getFirst();
		assertNotEquals(ActivityResearchEngine.Verdict.SUPPORTED, result.verdict(), result.reason());
	}

	@Test
	void incompleteObservationsAreExcludedByDefault() {
		Dataset data = new Dataset();
		data.add(new Dataset.Sample("a", "g1", observation(new StageCounts(1, 1, 1, 1), false)), new Dataset.Truth(0, "partial-scan"));
		data.add(new Dataset.Sample("b", "g2", observation(new StageCounts(1, 1, 1, 1), true)), new Dataset.Truth(0, "sweep"));

		ActivityResearchEngine.Report report = new ActivityResearchEngine().evaluate(data, h1Only());
		assertEquals(1, report.excludedIncomplete());
		assertEquals(1, report.usedSamples());
	}

	@Test
	void smallTestSetsAreInconclusiveNotSupported() {
		Dataset data = new Dataset();
		for (int geode = 0; geode < 6; geode++) {
			data.add(new Dataset.Sample("s" + geode + "a", "geode" + geode, observation(new StageCounts(9, 0, 0, 1), true)), new Dataset.Truth(10, "sweep"));
			data.add(new Dataset.Sample("s" + geode + "b", "geode" + geode, observation(new StageCounts(1, 0, 0, 9), true)), new Dataset.Truth(500, "sweep"));
		}

		ActivityResearchEngine.HypothesisResult result = new ActivityResearchEngine().evaluate(data, h1Only()).results().getFirst();
		assertEquals(ActivityResearchEngine.Verdict.INCONCLUSIVE, result.verdict(), "perfect but tiny test sets prove nothing");
	}

	@Test
	void calibrationOfPerfectlyCalibratedPredictions() {
		double[] predicted = {0.5, 0.5, 0.5, 0.5, 0.9, 0.9, 0.9, 0.9, 0.9, 0.9, 0.9, 0.9, 0.9, 0.9};
		boolean[] actual = {true, false, true, false, true, true, true, true, true, true, true, true, true, false};
		Calibration calibration = Calibration.of(predicted, actual);
		assertEquals(0.0, calibration.expectedCalibrationError(), 1e-9, "bins match their observed rates exactly");
		assertTrue(calibration.brierScore() > 0);
	}

	@Test
	void logisticModelIncreasesWithTheFeature() {
		Features[] x = new Features[40];
		boolean[] y = new boolean[40];
		for (int i = 0; i < 40; i++) {
			double fraction = i / 39.0;
			x[i] = new Features(fraction, fraction, 10, 5, 0.5, true);
			y[i] = fraction > 0.5;
		}

		LogisticModel model = new LogisticModel(Features::clusterFraction);
		model.fit(x, y);
		assertTrue(model.slope() > 0);
		assertTrue(model.probability(x[39]) > 0.9 && model.probability(x[0]) < 0.1);
	}

	@Test
	void reportWriterLabelsEstimatesAndLimitations() {
		Dataset data = new Dataset();
		for (int geode = 0; geode < 50; geode++) {
			data.add(new Dataset.Sample("s" + geode + "a", "geode" + geode, observation(new StageCounts(7, 0, 0, 3), true)), new Dataset.Truth(10, "sweep"));
			data.add(new Dataset.Sample("s" + geode + "b", "geode" + geode, observation(new StageCounts(3, 0, 0, 7), true)), new Dataset.Truth(500, "sweep"));
		}

		String md = EvaluationReportWriter.markdown(new ActivityResearchEngine().evaluate(data, h1Only()), "Test");
		assertTrue(md.contains("does not identify players"));
		assertTrue(md.contains("## Limitations and confounders"));
		assertTrue(md.contains("Pre-registered support criteria"));
		assertTrue(EvaluationReportWriter.summary(new ActivityResearchEngine().evaluate(data, h1Only()), "id", "t", 0).toString().contains("SUPPORTED"));
	}
}
