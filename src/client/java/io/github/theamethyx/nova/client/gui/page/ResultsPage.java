package io.github.theamethyx.nova.client.gui.page;

import java.nio.file.Files;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import net.minecraft.util.Util;

import io.github.theamethyx.nova.client.gui.GuiState;
import io.github.theamethyx.nova.client.gui.Icon;
import io.github.theamethyx.nova.client.gui.Theme;
import io.github.theamethyx.nova.client.gui.UiContext;
import io.github.theamethyx.nova.client.gui.widget.Button;
import io.github.theamethyx.nova.client.gui.widget.Container;
import io.github.theamethyx.nova.client.gui.widget.DetailCard;
import io.github.theamethyx.nova.client.gui.widget.ScrollList;
import io.github.theamethyx.nova.client.gui.widget.SelectableRow;
import io.github.theamethyx.nova.client.gui.widget.Widget;
import io.github.theamethyx.nova.client.research.ExperimentRunner;
import io.github.theamethyx.nova.core.research.GroundTruthManager;
import io.github.theamethyx.nova.core.research.GrowthStage;
import io.github.theamethyx.nova.core.research.IndicatorRegistry;
import io.github.theamethyx.nova.core.research.StageCounts;
import io.github.theamethyx.nova.core.research.TestResultStore;
import io.github.theamethyx.nova.core.research.geode.GeodeObservation;

/** Saved observations, research evaluations and the indicator registry. */
public class ResultsPage extends Page {
	private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());
	private static final String[] TABS = {"Observations", "Evaluations", "Indicators"};

	private final List<Button> tabs = new ArrayList<>();
	private final ScrollList list;
	private final ScrollList details;
	private String signature = "";
	private String pendingDelete;
	private long pendingDeleteAt;
	private String autoSelectedRun;

	public ResultsPage(UiContext ui) {
		super(ui);
		for (int i = 0; i < TABS.length; i++) {
			int index = i;
			Button tab = add(new Button(TABS[i], Button.Style.GHOST, () -> {
				GuiState.resultsTab = index;
				rebuild();
			}));
			tabs.add(tab);
		}

		tabs.get(0).tooltip("Geode observations you saved from the World Analysis page.");
		tabs.get(1).tooltip("Controlled experiments that test what geode growth can and cannot tell about exposure time.");
		tabs.get(2).tooltip("Block-state indicators, their hypotheses, alternative explanations and test status.");
		list = add(new ScrollList(ui, 3, 0));
		details = add(new ScrollList(ui, Theme.GAP, 0));
	}

	@Override
	public String title() {
		return "Test Results";
	}

	@Override
	public String navLabel() {
		return "Results";
	}

	@Override
	public String subtitle() {
		return "Saved observations, research evaluations and indicator status, with the files they were written to.";
	}

	@Override
	public Icon icon() {
		return Icon.RESULTS;
	}

	@Override
	public void onShow() {
		rebuild();
	}

	@Override
	protected void onLayout() {
		int tabWidth = Math.min(90, (width - 2 * Theme.GAP) / 3);
		for (int i = 0; i < tabs.size(); i++) {
			tabs.get(i).setBounds(x + i * (tabWidth + Theme.GAP), y, tabWidth, 15);
		}

		int top = y + 15 + Theme.GAP;
		int listWidth = Math.max(130, Math.min(180, width * 2 / 5));
		list.setBounds(x, top, listWidth, height - (top - y));
		details.setBounds(x + listWidth + Theme.PAD, top, width - listWidth - Theme.PAD, height - (top - y));
		rebuild();
	}

	@Override
	public void render(UiContext ui) {
		Button active = tabs.get(GuiState.resultsTab);
		ui.rect(active.x() + 4, active.y() + active.height(), active.width() - 8, 2, Theme.ACCENT);
		super.render(ui);
	}

	@Override
	public void tick() {
		ExperimentRunner runner = nova.experiments();
		String current = GuiState.resultsTab + "|" + nova.results().all().size() + "|" + GuiState.selectedResult + "|" + GuiState.selectedEvaluation
				+ "|" + GuiState.selectedIndicator + "|" + runner.state() + "|" + runner.lastEvaluationId();
		if (!current.equals(signature)) {
			rebuild();
		}
	}

	private void rebuild() {
		ExperimentRunner runner = nova.experiments();
		signature = GuiState.resultsTab + "|" + nova.results().all().size() + "|" + GuiState.selectedResult + "|" + GuiState.selectedEvaluation
				+ "|" + GuiState.selectedIndicator + "|" + runner.state() + "|" + runner.lastEvaluationId();
		List<Widget> left = new ArrayList<>();
		List<Widget> right = new ArrayList<>();
		switch (GuiState.resultsTab) {
			case 1 -> buildEvaluations(left, right);
			case 2 -> buildIndicators(left, right);
			default -> buildObservations(left, right);
		}

		list.setChildren(left);
		details.setChildren(right);
	}

	// ----- observations ----------------------------------------------------------------------

	private void buildObservations(List<Widget> left, List<Widget> right) {
		List<TestResultStore.Record> records = new ArrayList<>(nova.results().all());
		Collections.reverse(records);
		if (GuiState.selectedResult == null && !records.isEmpty()) {
			GuiState.selectedResult = records.getFirst().id();
		}

		for (TestResultStore.Record record : records) {
			GeodeObservation o = record.observation();
			left.add(new SelectableRow(TIME.format(Instant.ofEpochMilli(record.savedAt())) + "  geode",
					o.counts().total() + " growth objects" + (o.scenarioId() != null ? " · fixture" : ""),
					o.complete() ? null : "incomplete", Theme.WARNING,
					() -> record.id().equals(GuiState.selectedResult), () -> GuiState.selectedResult = record.id()));
		}

		if (left.isEmpty()) {
			left.add(new ModulesPage.Message("No saved observations yet. Save one on the World Analysis page."));
		}

		TestResultStore.Record selected = GuiState.selectedResult == null ? null : nova.results().get(GuiState.selectedResult);
		if (selected == null) {
			right.add(new ModulesPage.Message("Select an observation on the left."));
			return;
		}

		right.add(new DetailCard("Saved geode observation", () -> observationRows(selected)));
		right.add(new Actions(
				new Button("Open report file", Button.Style.SECONDARY, () -> Util.getPlatform().openPath(nova.results().reportPath(selected.id())))
						.icon(Icon.FOLDER).enabledWhen(() -> Files.exists(nova.results().reportPath(selected.id()))),
				new Button(() -> confirming(selected.id()) ? "Confirm delete" : "Delete", Button.Style.DANGER, () -> {
					if (!confirming(selected.id())) {
						pendingDelete = selected.id();
						pendingDeleteAt = System.currentTimeMillis();
						return;
					}

					nova.results().delete(selected.id());
					GuiState.selectedResult = null;
				}).icon(Icon.TRASH).tooltip("Delete this record and its report file. Click twice to confirm.")));
	}

	private boolean confirming(String id) {
		return id.equals(pendingDelete) && System.currentTimeMillis() - pendingDeleteAt < 4000;
	}

	private List<DetailCard.Row> observationRows(TestResultStore.Record record) {
		GeodeObservation o = record.observation();
		StageCounts c = o.counts();
		List<DetailCard.Row> rows = new ArrayList<>();
		rows.add(new DetailCard.Row("Record", record.id()));
		rows.add(new DetailCard.Row("Geode", o.id()));
		rows.add(new DetailCard.Row("Completeness", o.complete() ? "Complete" : "Incomplete", o.complete() ? Theme.SUCCESS : Theme.WARNING));
		for (GrowthStage stage : GrowthStage.values()) {
			rows.add(new DetailCard.Row(stage.label(), c.get(stage) + (c.total() == 0 ? "" : String.format(Locale.ROOT, "  (%.1f%%)", c.get(stage) * 100.0 / c.total()))));
		}

		rows.add(new DetailCard.Row("Total growth objects", String.valueOf(c.total())));
		rows.add(new DetailCard.Row("Clusters / all growth objects", c.clustersOfAll().describe()));
		rows.add(new DetailCard.Row("Clusters / (large buds + clusters)", c.clustersOfLargeAndClusters().describe()));
		rows.add(new DetailCard.Row("Scenario run", o.scenarioId() == null ? "none" : o.scenarioId()));
		GroundTruthManager.Record truth = nova.groundTruth().get(o.scenarioId());
		if (truth != null && truth.conditions().has("serverCensus")) {
			// Verification only: the research engine never gives ground truth to a classifier.
			JsonObject census = truth.conditions().getAsJsonObject("serverCensus");
			boolean match = census.get("small").getAsInt() == c.small() && census.get("medium").getAsInt() == c.medium()
					&& census.get("large").getAsInt() == c.large() && census.get("cluster").getAsInt() == c.cluster();
			rows.add(new DetailCard.Row("Fixture ground truth", match ? "counts match" : "counts differ", match ? Theme.SUCCESS : Theme.ERROR));
		}

		record.context().forEach((key, value) -> rows.add(new DetailCard.Row(key, value)));
		return rows;
	}

	// ----- evaluations -----------------------------------------------------------------------

	private void buildEvaluations(List<Widget> left, List<Widget> right) {
		List<TestResultStore.Evaluation> evaluations = nova.results().evaluations();
		ExperimentRunner runner = nova.experiments();
		if (runner.lastEvaluationId() != null && !runner.lastEvaluationId().equals(autoSelectedRun)) {
			// Show a freshly finished run once; afterwards the user's selection wins.
			autoSelectedRun = runner.lastEvaluationId();
			GuiState.selectedEvaluation = autoSelectedRun;
		}

		if (GuiState.selectedEvaluation == null && !evaluations.isEmpty()) {
			GuiState.selectedEvaluation = evaluations.getFirst().id();
		}

		left.add(new RunnerCard());
		for (TestResultStore.Evaluation evaluation : evaluations) {
			JsonObject summary = evaluation.summary();
			String h1 = verdictOf(summary, "H1");
			left.add(new SelectableRow(TIME.format(Instant.ofEpochMilli(summary.get("createdAt").getAsLong())),
					summary.get("samples").getAsInt() + " observations", "H1 " + h1.toLowerCase(Locale.ROOT), verdictColor(h1),
					() -> evaluation.id().equals(GuiState.selectedEvaluation), () -> GuiState.selectedEvaluation = evaluation.id()));
		}

		TestResultStore.Evaluation selected = evaluations.stream().filter(e -> e.id().equals(GuiState.selectedEvaluation)).findFirst().orElse(null);
		if (selected == null) {
			right.add(new ModulesPage.Message("No evaluations yet. Run the quick experiment (singleplayer) or the full headless one "
					+ "(./gradlew runGameTest, see README)."));
			return;
		}

		JsonObject summary = selected.summary();
		right.add(new DetailCard(summary.get("title").getAsString(), () -> List.of(
				new DetailCard.Row("Observations", summary.get("samples").getAsString() + " (" + summary.get("usedSamples").getAsString() + " complete, used)"),
				new DetailCard.Row("Held-out test geodes", summary.get("testGeodes").getAsString()),
				new DetailCard.Row("", "Verdicts use criteria fixed before the experiment. 'Supported' means only: within this test world and "
						+ "exposure range. Nothing here identifies players or locations."))));
		for (JsonElement element : summary.getAsJsonArray("hypotheses")) {
			JsonObject h = element.getAsJsonObject();
			String verdict = h.get("verdict").getAsString();
			right.add(new DetailCard(h.get("id").getAsString() + ": " + h.get("title").getAsString(), () -> List.of(
					new DetailCard.Row("Verdict", verdict, verdictColor(verdict)),
					new DetailCard.Row("Held-out samples", h.get("testSamples").getAsString()),
					new DetailCard.Row("Precision", pct(h, "precision")),
					new DetailCard.Row("Recall", pct(h, "recall")),
					new DetailCard.Row("False-positive rate", pct(h, "falsePositiveRate")),
					new DetailCard.Row("False-negative rate", pct(h, "falseNegativeRate")),
					new DetailCard.Row("Brier score / calibration error", num(h, "brier") + " / " + num(h, "ece")),
					new DetailCard.Row("", h.get("reason").getAsString()))));
		}

		right.add(new Actions(new Button("Open full report", Button.Style.SECONDARY, () -> Util.getPlatform().openPath(selected.markdown()))
				.icon(Icon.FOLDER).enabledWhen(() -> Files.exists(selected.markdown()))
				.tooltip("The Markdown report has the exposure curve, calibration bins, per-condition results and limitations.")));
	}

	private static String verdictOf(JsonObject summary, String id) {
		for (JsonElement element : summary.getAsJsonArray("hypotheses")) {
			if (element.getAsJsonObject().get("id").getAsString().equals(id)) {
				return element.getAsJsonObject().get("verdict").getAsString();
			}
		}

		return "n/a";
	}

	private static int verdictColor(String verdict) {
		return switch (verdict) {
			case "SUPPORTED" -> Theme.SUCCESS;
			case "UNSUPPORTED" -> Theme.ERROR;
			default -> Theme.WARNING;
		};
	}

	private static String pct(JsonObject json, String key) {
		JsonElement value = json.get(key);
		return value == null || value.isJsonNull() ? "n/a" : String.format(Locale.ROOT, "%.1f%%", value.getAsDouble() * 100);
	}

	private static String num(JsonObject json, String key) {
		JsonElement value = json.get(key);
		return value == null || value.isJsonNull() ? "n/a" : String.format(Locale.ROOT, "%.3f", value.getAsDouble());
	}

	/** Starts, cancels and shows the progress of the in-game quick experiment. */
	private final class RunnerCard extends Container {
		private final Button run;
		private final Button cancel;

		RunnerCard() {
			ExperimentRunner runner = nova.experiments();
			run = add(new Button("Run quick experiment", Button.Style.PRIMARY, () -> {
				String problem = runner.start();
				if (problem != null) {
					nova.diagnostics().warn("Research", problem);
				}
			}).icon(Icon.PLAY));
			run.enabledWhen(() -> runner.state() != ExperimentRunner.State.RUNNING && runner.state() != ExperimentRunner.State.EVALUATING
					&& nova.scenarios().unavailableReason() == null);
			run.tooltip("Singleplayer only. Builds 15 geodes one after another in a stone cube 48 blocks above you, applies known growth, "
					+ "evaluates the hypotheses on held-out geodes and saves a report. The cube is removed afterwards. Takes about a minute.");
			cancel = add(new Button("", Button.Style.SECONDARY, runner::cancel).icon(Icon.STOP));
			cancel.enabledWhen(() -> runner.state() == ExperimentRunner.State.RUNNING);
			cancel.tooltip("Stop the experiment and clear its test area.");
		}

		@Override
		public int preferredHeight(UiContext ui, int width) {
			return 44;
		}

		@Override
		protected void onLayout() {
			run.setBounds(x + 4, y + 4, width - 8 - 18 - Theme.GAP, 15);
			cancel.setBounds(x + width - 4 - 18, y + 4, 18, 15);
		}

		@Override
		public void render(UiContext ui) {
			ExperimentRunner runner = nova.experiments();
			ui.roundRect(x, y, width, height, Theme.SURFACE);
			super.render(ui);
			ui.rect(x + 4, y + 23, width - 8, 3, 0xFF2C2C36);
			ui.rect(x + 4, y + 23, Math.round((width - 8) * runner.progress()), 3, Theme.ACCENT);
			String message = nova.scenarios().unavailableReason() != null ? nova.scenarios().unavailableReason() : runner.message();
			boolean cut = ui.textClipped(message, x + 4, y + 31, width - 8, Theme.TEXT_SECONDARY);
			if (cut && ui.hovered(x, y + 29, width, 12)) {
				ui.tooltip(this, message);
			}
		}
	}

	// ----- indicators ------------------------------------------------------------------------

	private void buildIndicators(List<Widget> left, List<Widget> right) {
		List<TestResultStore.Evaluation> evaluations = nova.results().evaluations();
		List<IndicatorRegistry.Indicator> indicators = IndicatorRegistry.build(evaluations.isEmpty() ? null : evaluations.getFirst().summary());
		if (GuiState.selectedIndicator == null) {
			GuiState.selectedIndicator = indicators.getFirst().id();
		}

		for (IndicatorRegistry.Indicator indicator : indicators) {
			left.add(new SelectableRow(indicator.name(), indicator.status().label(), null, 0,
					() -> indicator.id().equals(GuiState.selectedIndicator), () -> GuiState.selectedIndicator = indicator.id()));
		}

		IndicatorRegistry.Indicator selected = indicators.stream().filter(i -> i.id().equals(GuiState.selectedIndicator)).findFirst().orElse(indicators.getFirst());
		int statusColor = switch (selected.status()) {
			case SUPPORTED_IN_TEST_ENVIRONMENT -> Theme.SUCCESS;
			case UNSUPPORTED -> Theme.ERROR;
			case INCONCLUSIVE -> Theme.WARNING;
			case UNTESTED -> Theme.TEXT_MUTED;
		};
		right.add(new DetailCard(selected.name(), () -> List.of(
				new DetailCard.Row("Status", selected.status().label(), statusColor),
				new DetailCard.Row("Observed property", selected.observedProperty()),
				new DetailCard.Row("Hypothesized relationship", selected.hypothesis()),
				new DetailCard.Row("Evidence", selected.evidence()),
				new DetailCard.Row("Test method", selected.methodology()),
				new DetailCard.Row("Measured results", selected.measuredResults()))));
		List<DetailCard.Row> alternatives = new ArrayList<>();
		selected.alternativeExplanations().forEach(text -> alternatives.add(new DetailCard.Row("", "- " + text)));
		right.add(new DetailCard("Known alternative explanations", () -> alternatives));
	}

	/** One or two buttons side by side. */
	private static final class Actions extends Container {
		Actions(Widget... widgets) {
			for (Widget widget : widgets) add(widget);
		}

		@Override
		public int preferredHeight(UiContext ui, int width) {
			return 16;
		}

		@Override
		protected void onLayout() {
			int n = children.size();
			int w = (width - (n - 1) * Theme.GAP) / n;
			for (int i = 0; i < n; i++) {
				children.get(i).setBounds(x + i * (w + Theme.GAP), y, i == n - 1 ? width - i * (w + Theme.GAP) : w, 16);
			}
		}
	}
}
