package io.github.theamethyx.femboyclient.core.research.engine;

import java.util.Locale;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/** Renders an evaluation as a Markdown report and a compact JSON summary. */
public final class EvaluationReportWriter {
	private EvaluationReportWriter() {
	}

	public static String markdown(ActivityResearchEngine.Report report, String title) {
		StringBuilder md = new StringBuilder();
		md.append("# ").append(title).append("\n\n");
		md.append("This report evaluates what amethyst growth-stage observations can and cannot tell about how long a geode's chunk ")
				.append("was randomly ticked, in a controlled test world with known ground truth. It does not identify players or locate bases, ")
				.append("and a correlation found here does not establish causation outside the tested setup.\n\n");

		md.append("## Setup\n\n");
		report.metadata().forEach((key, value) -> md.append("- ").append(key).append(": ").append(value).append('\n'));
		md.append("- Samples: ").append(report.totalSamples()).append(" observations; ").append(report.usedSamples()).append(" used, ")
				.append(report.excludedIncomplete()).append(" incomplete observations excluded\n");
		md.append("- Split by geode: ").append(report.trainGeodes()).append(" training geodes, ").append(report.testGeodes())
				.append(" held-out test geodes (every third geode by id). Repeated observations of one geode never span both sets.\n");
		md.append("- Exposure unit: random ticks received by each budding block. One hour of a loaded, randomly ticking chunk at the ")
				.append(String.format(Locale.ROOT, "default random tick speed (3) is %.1f random ticks.\n", Hypothesis.TICKS_PER_HOUR));
		md.append("- Classifiers receive only observed features (stage counts, budding blocks, face occupancy). Ground truth is stored separately ")
				.append("and is used only to score predictions.\n");
		md.append(String.format(Locale.ROOT, "- Pre-registered support criteria (held-out set): balanced accuracy >= %.2f, false-positive and false-negative rates <= %.2f, at least %d test samples and %d per class. Below %.2f balanced accuracy a hypothesis is unsupported.\n",
				Hypothesis.SUPPORT_MIN_BALANCED_ACCURACY, Hypothesis.SUPPORT_MAX_ERROR_RATE, Hypothesis.SUPPORT_MIN_TEST_SAMPLES,
				Hypothesis.SUPPORT_MIN_PER_CLASS, Hypothesis.UNSUPPORTED_BELOW_BALANCED_ACCURACY));

		md.append("\n## Results by hypothesis\n\n");
		md.append("| Hypothesis | Verdict | Test samples (pos/neg) | Threshold | Precision | Recall | False-positive rate | False-negative rate |\n");
		md.append("|---|---|---|---|---|---|---|---|\n");
		for (ActivityResearchEngine.HypothesisResult r : report.results()) {
			Metrics t = r.test();
			md.append("| ").append(r.hypothesis().id()).append(" ").append(r.hypothesis().title()).append(" | **").append(r.verdict()).append("** | ")
					.append(r.testSamples()).append(" (").append(r.testPositives()).append("/").append(r.testNegatives()).append(") | ")
					.append(Double.isNaN(r.threshold()) ? "n/a" : String.format(Locale.ROOT, "%.3f", r.threshold())).append(" | ")
					.append(pct(t.precision().value())).append(" | ").append(pct(t.recall().value())).append(" | ")
					.append(pct(t.falsePositiveRate().value())).append(" | ").append(pct(t.falseNegativeRate().value())).append(" |\n");
		}

		for (ActivityResearchEngine.HypothesisResult r : report.results()) {
			Hypothesis h = r.hypothesis();
			md.append("\n### ").append(h.id()).append(": ").append(h.title()).append("\n\n");
			md.append("Statement: ").append(h.statement()).append("\n\n");
			md.append(String.format(Locale.ROOT, "- Label: positive when exposure >= %.1f random ticks (%.1f h)", h.positiveAtLeast(), h.positiveAtLeast() / Hypothesis.TICKS_PER_HOUR));
			if (h.minimumExposure() > 0) {
				md.append(String.format(Locale.ROOT, "; only samples with exposure >= %.1f random ticks (%.1f h)", h.minimumExposure(), h.minimumExposure() / Hypothesis.TICKS_PER_HOUR));
			}

			md.append("\n- Rule: ").append(h.featureName()).append(" >= threshold, ")
					.append(Double.isNaN(h.fixedThreshold()) ? "threshold fitted on training geodes (Youden's J)" : "threshold fixed in advance").append('\n');
			md.append("- Verdict: **").append(r.verdict()).append("**. ").append(r.reason()).append('\n');
			md.append("- Training: ").append(r.trainSamples()).append(" samples; held-out test: ").append(r.testSamples()).append(" samples\n");
			md.append("- Test precision: ").append(Metrics.percent(r.test().precision())).append('\n');
			md.append("- Test recall: ").append(Metrics.percent(r.test().recall())).append('\n');
			md.append("- Test false-positive rate: ").append(Metrics.percent(r.test().falsePositiveRate())).append('\n');
			md.append("- Test false-negative rate: ").append(Metrics.percent(r.test().falseNegativeRate())).append('\n');
			md.append(String.format(Locale.ROOT, "- Balanced accuracy: test %.3f, training %.3f (a large gap would indicate overfitting)\n",
					r.test().balancedAccuracy(), r.train().balancedAccuracy()));

			Calibration c = r.calibration();
			md.append(String.format(Locale.ROOT, "- Calibration of a logistic model p = 1/(1+exp(-(%.2f + %.2f x))) fitted on training geodes: Brier score %.3f, expected calibration error %.3f on test geodes\n",
					r.logisticIntercept(), r.logisticSlope(), c.brierScore(), c.expectedCalibrationError()));
			md.append("\n| Predicted probability | Test samples | Mean predicted | Observed positive rate |\n|---|---:|---:|---:|\n");
			for (Calibration.Bin bin : c.bins()) {
				md.append(String.format(Locale.ROOT, "| %.1f-%.1f | %d | %s | %s |\n", bin.low(), bin.high(), bin.count(),
						bin.count() == 0 ? "-" : pct(bin.meanPredicted()), bin.count() == 0 ? "-" : pct(bin.observedRate())));
			}

			md.append("\nPerformance by experimental condition (held-out geodes):\n\n| Condition | Samples | Positives | Accuracy | False-positive rate | False-negative rate |\n|---|---:|---:|---:|---:|---:|\n");
			for (ActivityResearchEngine.ConditionResult cr : r.byCondition()) {
				Metrics m = cr.metrics();
				md.append("| ").append(cr.condition()).append(" | ").append(m.total()).append(" | ").append(m.positives()).append(" | ")
						.append(pct(m.accuracy().value())).append(" | ").append(pct(m.falsePositiveRate().value())).append(" | ")
						.append(pct(m.falseNegativeRate().value())).append(" |\n");
			}
		}

		md.append("\n## Cluster share versus exposure (sweep arm, complete observations)\n\n");
		md.append("| Random ticks | Hours at default speed | Samples | Mean cluster share | SD | Min | Max |\n|---:|---:|---:|---:|---:|---:|---:|\n");
		for (ActivityResearchEngine.CurvePoint p : report.curve()) {
			md.append(String.format(Locale.ROOT, "| %.0f | %.1f | %d | %s | %.3f | %s | %s |\n", p.exposure(), p.exposure() / Hypothesis.TICKS_PER_HOUR,
					p.samples(), pct(p.mean()), p.sd(), pct(p.min()), pct(p.max())));
		}

		md.append("\nRepeated observations: in ").append(report.monotoneGeodes()).append(" of ").append(report.checkedGeodes())
				.append(" sweep geodes the cluster count never decreased between consecutive observations.\n");

		md.append("\n## Findings derived from the data\n\n");
		findings(report).forEach(finding -> md.append("- ").append(finding).append('\n'));

		md.append("\n## Limitations and confounders\n\n");
		md.append("- Precision depends on the share of long-exposure geodes in the sample, which here is set by the experiment design. ")
				.append("Applied to a population with a different mix, the same rule would have a different precision.\n");
		md.append("- Exposure was produced by calling the game's real random-tick code on budding blocks. In a live world, random ticks reach every ")
				.append("entity-ticking chunk (including force-loaded ones), while a chunk's inhabited time only counts nearby non-spectator players; ")
				.append("the two are therefore not interchangeable.\n");
		md.append("- The random tick speed game rule scales growth directly; a world with a different setting shifts every threshold.\n");
		md.append("- Generation already places buds of all four stages (about a quarter clusters on average), so fresh geodes are not cluster-free.\n");
		md.append("- Growth saturates: once every free face holds a cluster, more time leaves no trace.\n");
		md.append("- Harvesting clusters resets the visible state; harvested geodes look younger than they are.\n");
		md.append("- Small geodes have few growth objects, so their shares are noisy; incomplete observations undercount.\n");
		md.append("- Geode fixtures share one generation method (vanilla feature, flat test world seed); natural terrain, caves and water can change free faces.\n");
		md.append("- Geode shapes depend on the world seed and position as well as the feature seed, so a rerun in another world produces a ")
				.append("different (equally valid) sample; the world seed and position are listed under Setup.\n");
		md.append("- Results hold only for the tested environment and the stated exposure range.\n");
		return md.toString();
	}

	/** Observations computed from the results, phrased neutrally; nothing here is hard-coded. */
	static java.util.List<String> findings(ActivityResearchEngine.Report report) {
		java.util.List<String> findings = new java.util.ArrayList<>();
		java.util.List<ActivityResearchEngine.CurvePoint> curve = report.curve();
		for (int i = 1; i < curve.size(); i++) {
			ActivityResearchEngine.CurvePoint before = curve.get(i - 1);
			ActivityResearchEngine.CurvePoint after = curve.get(i);
			if (after.mean() < before.mean() - 0.02) {
				findings.add(String.format(Locale.ROOT, "The mean cluster share is not monotone in exposure: it fell from %.1f%% at %.0f to %.1f%% at %.0f random ticks. "
						+ "New small buds on free faces add to the denominator before any of them matures, so a higher share does not simply mean longer exposure.",
						before.mean() * 100, before.exposure(), after.mean() * 100, after.exposure()));
				break;
			}
		}

		for (ActivityResearchEngine.CurvePoint point : curve) {
			if (point.mean() >= 0.99) {
				findings.add(String.format(Locale.ROOT, "The share saturates: from %.0f random ticks (about %.1f h at default speed) the mean is %.1f%%, so longer exposure leaves no further trace in this statistic.",
						point.exposure(), point.exposure() / Hypothesis.TICKS_PER_HOUR, point.mean() * 100));
				break;
			}
		}

		if (!curve.isEmpty() && curve.getFirst().exposure() == 0) {
			ActivityResearchEngine.CurvePoint fresh = curve.getFirst();
			findings.add(String.format(Locale.ROOT, "Freshly generated geodes already have a mean cluster share of %.1f%% (range %.1f-%.1f%%) from world generation alone.",
					fresh.mean() * 100, fresh.min() * 100, fresh.max() * 100));
		}

		for (ActivityResearchEngine.HypothesisResult r : report.results()) {
			for (ActivityResearchEngine.ConditionResult c : r.byCondition()) {
				Metrics m = c.metrics();
				if (m.positives() > 0 && m.falseNegativeRate().value() >= 0.5) {
					findings.add(String.format(Locale.ROOT, "%s: in the '%s' condition %d of %d long-exposure geodes were missed (held-out set; small sample).",
							r.hypothesis().id(), c.condition(), m.falseNegatives(), m.positives()));
				}
			}
		}

		findings.add(report.monotoneGeodes() + " of " + report.checkedGeodes() + " repeatedly observed geodes never lost a cluster between observations "
				+ "(expected: growth never reverses without block removal).");
		return findings;
	}

	public static JsonObject summary(ActivityResearchEngine.Report report, String id, String title, long createdAt) {
		JsonObject json = new JsonObject();
		json.addProperty("id", id);
		json.addProperty("title", title);
		json.addProperty("createdAt", createdAt);
		json.addProperty("samples", report.totalSamples());
		json.addProperty("usedSamples", report.usedSamples());
		json.addProperty("testGeodes", report.testGeodes());
		JsonArray hypotheses = new JsonArray();
		for (ActivityResearchEngine.HypothesisResult r : report.results()) {
			JsonObject h = new JsonObject();
			h.addProperty("id", r.hypothesis().id());
			h.addProperty("title", r.hypothesis().title());
			h.addProperty("verdict", r.verdict().name());
			h.addProperty("reason", r.reason());
			h.addProperty("testSamples", r.testSamples());
			number(h, "precision", r.test().precision().value());
			number(h, "recall", r.test().recall().value());
			number(h, "falsePositiveRate", r.test().falsePositiveRate().value());
			number(h, "falseNegativeRate", r.test().falseNegativeRate().value());
			number(h, "brier", r.calibration().brierScore());
			number(h, "ece", r.calibration().expectedCalibrationError());
			hypotheses.add(h);
		}

		json.add("hypotheses", hypotheses);
		return json;
	}

	/** JSON has no NaN; undefined rates are written as null. */
	private static void number(JsonObject json, String key, double value) {
		if (Double.isNaN(value)) {
			json.add(key, com.google.gson.JsonNull.INSTANCE);
		} else {
			json.addProperty(key, value);
		}
	}

	private static String pct(double value) {
		return Double.isNaN(value) ? "n/a" : String.format(Locale.ROOT, "%.1f%%", value * 100);
	}
}
