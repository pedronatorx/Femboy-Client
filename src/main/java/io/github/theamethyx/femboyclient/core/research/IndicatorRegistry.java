package io.github.theamethyx.femboyclient.core.research;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * Environmental block-state indicators and what is actually known about each. Statuses are only
 * changed by evaluation results; nothing here asserts that an indicator reveals player activity.
 */
public final class IndicatorRegistry {
	public enum Status {
		UNTESTED("Untested"), UNSUPPORTED("Unsupported"), INCONCLUSIVE("Inconclusive"), SUPPORTED_IN_TEST_ENVIRONMENT("Supported in the tested environment only");

		private final String label;

		Status(String label) {
			this.label = label;
		}

		public String label() {
			return label;
		}
	}

	public record Indicator(String id, String name, String observedProperty, String hypothesis, String evidence,
			List<String> alternativeExplanations, String methodology, String measuredResults, Status status) {
	}

	private IndicatorRegistry() {
	}

	/**
	 * @param latestEvaluation summary JSON of the most recent amethyst evaluation, or null if none
	 */
	public static List<Indicator> build(JsonObject latestEvaluation) {
		List<Indicator> indicators = new ArrayList<>();
		indicators.add(amethyst(latestEvaluation));
		indicators.add(new Indicator("kelp", "Kelp growth", "The 'age' block state of the top kelp block and the height of kelp columns.",
				"Kelp columns grow on random ticks, so taller columns might indicate longer random ticking.",
				"None collected yet.",
				List.of("Kelp is generated with random heights.", "Players plant and cut kelp; bone meal adds height instantly.",
						"Columns stop at age 25 or at the water surface, so growth saturates."),
				"Planned: plant kelp fixtures of known height and age, apply known random ticks with the game's own random-tick code, "
						+ "and evaluate height or age against exposure on held-out fixtures, as for amethyst.",
				"Not measured.", Status.UNTESTED));
		indicators.add(new Indicator("vines", "Vine spread", "The number and extent of vine blocks below and beside a starting vine.",
				"Vines spread on random ticks, so larger vine coverage might indicate longer random ticking.",
				"None collected yet.",
				List.of("Generated vines (jungles, swamps) start at varied sizes.", "Players place, trim and shear vines.",
						"Spread depends on available surfaces and a local density limit, not only on time."),
				"Planned: vine fixtures with known starting blocks and known random ticks; measure covered blocks.", "Not measured.", Status.UNTESTED));
		indicators.add(new Indicator("bamboo", "Bamboo growth", "Bamboo column heights and the 'stage' and 'age' block states.",
				"Bamboo grows on random ticks until a maximum height, so taller stalks might indicate longer random ticking.",
				"None collected yet.",
				List.of("Generated bamboo has random heights.", "Bone meal and player harvesting change heights instantly.",
						"Growth stops at a per-stalk maximum (saturation)."),
				"Planned: bamboo fixtures of known height with known random ticks.", "Not measured.", Status.UNTESTED));
		indicators.add(new Indicator("bees", "Bee nests and beehives", "The 'honey_level' block state (0-5) of nests and hives.",
				"Bees deposit honey over time, so fuller hives might indicate longer simulation time nearby.",
				"None collected yet.",
				List.of("Honey depends on flowers near the hive and on bees surviving, not on time alone.",
						"Players harvest honey, resetting the level.", "Bees only work during the day and in good weather."),
				"Planned: hive fixtures with a fixed number of bees and flowers; record honey level against simulated time.",
				"Not measured.", Status.UNTESTED));
		return indicators;
	}

	private static Indicator amethyst(JsonObject evaluation) {
		List<String> alternatives = List.of(
				"World generation already places clusters (about a quarter of growth objects in fresh geodes).",
				"Growth saturates within a few hours of random ticking, after which no duration can be told apart.",
				"Harvesting and regrowth make long-exposed geodes look young.",
				"Random ticks also reach force-loaded chunks without players, and the random tick speed rule scales growth.",
				"Small or partly visible geodes give noisy or undercounted shares.");
		String methodology = "Vanilla geodes generated in a test world; known numbers of random ticks applied with the game's own code; "
				+ "observed with the analyzer; rules fitted on training geodes and scored on held-out geodes against pre-registered criteria.";
		if (evaluation == null) {
			return new Indicator("amethyst", "Amethyst growth stages", "Counts of small, medium and large buds and clusters in a geode.",
					"A higher share of clusters indicates that the geode's chunk was randomly ticked for longer.", "No evaluation has been run yet.",
					alternatives, methodology, "Not measured. Run an evaluation from the Test Results page.", Status.UNTESTED);
		}

		StringBuilder results = new StringBuilder();
		Status status = Status.UNTESTED;
		for (JsonElement element : evaluation.getAsJsonArray("hypotheses")) {
			JsonObject h = element.getAsJsonObject();
			String verdict = h.get("verdict").getAsString();
			results.append(h.get("id").getAsString()).append(" (").append(h.get("title").getAsString()).append("): ").append(verdict)
					.append(". ").append(h.get("reason").getAsString()).append('\n');
			if (h.get("id").getAsString().equals("H1")) {
				status = switch (verdict) {
					case "SUPPORTED" -> Status.SUPPORTED_IN_TEST_ENVIRONMENT;
					case "UNSUPPORTED" -> Status.UNSUPPORTED;
					default -> Status.INCONCLUSIVE;
				};
			}
		}

		String evidence = String.format(Locale.ROOT, "%d observations of %s held-out geodes in evaluation '%s'.",
				evaluation.get("samples").getAsInt(), evaluation.get("testGeodes").getAsString(), evaluation.get("title").getAsString());
		return new Indicator("amethyst", "Amethyst growth stages", "Counts of small, medium and large buds and clusters in a geode.",
				"A higher share of clusters indicates that the geode's chunk was randomly ticked for longer (tested as H1; H2 and H3 test limits).",
				evidence, alternatives, methodology, results.toString().trim(), status);
	}
}
