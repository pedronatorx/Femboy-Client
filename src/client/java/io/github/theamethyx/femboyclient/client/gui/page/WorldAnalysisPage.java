package io.github.theamethyx.femboyclient.client.gui.page;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;

import io.github.theamethyx.femboyclient.client.analysis.WorldScanner;
import io.github.theamethyx.femboyclient.client.gui.GuiState;
import io.github.theamethyx.femboyclient.client.gui.Icon;
import io.github.theamethyx.femboyclient.client.gui.Theme;
import io.github.theamethyx.femboyclient.client.gui.UiContext;
import io.github.theamethyx.femboyclient.client.gui.widget.Button;
import io.github.theamethyx.femboyclient.client.gui.widget.Container;
import io.github.theamethyx.femboyclient.client.gui.widget.ScrollList;
import io.github.theamethyx.femboyclient.client.gui.widget.SettingRow;
import io.github.theamethyx.femboyclient.client.gui.widget.ToggleSwitch;
import io.github.theamethyx.femboyclient.client.gui.widget.Widget;
import io.github.theamethyx.femboyclient.client.modules.AmethystGeodeAnalyzerModule;
import io.github.theamethyx.femboyclient.client.world.WorldIdentity;
import io.github.theamethyx.femboyclient.core.research.GrowthStage;
import io.github.theamethyx.femboyclient.core.research.Proportion;
import io.github.theamethyx.femboyclient.core.research.StageCounts;
import io.github.theamethyx.femboyclient.core.research.TestResultStore;
import io.github.theamethyx.femboyclient.core.research.geode.EvidenceQuality;
import io.github.theamethyx.femboyclient.core.research.geode.GeodeObservation;

/** Scan controls, the list of detected geodes and the analysis of the selected one. */
public class WorldAnalysisPage extends Page {
	private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());

	private final AmethystGeodeAnalyzerModule analyzer;
	private final Consumer<String> openResult;
	private final Controls controls;
	private final ScrollList list;
	private final ScrollList details;
	private boolean showSettings;
	private String shownSelection = "";
	private int shownResultCount = -1;
	private String savedRecordId;

	public WorldAnalysisPage(UiContext ui, Consumer<String> openResult) {
		super(ui);
		this.analyzer = femboy.modules().get(AmethystGeodeAnalyzerModule.class);
		this.openResult = openResult;
		controls = add(new Controls());
		list = add(new ScrollList(ui, 3, 0));
		details = add(new ScrollList(ui, Theme.GAP, 0));
	}

	@Override
	public String title() {
		return "World Analysis";
	}

	@Override
	public String navLabel() {
		return "Analysis";
	}

	@Override
	public String subtitle() {
		return "Amethyst Geode Analyzer: exact growth-stage counts in loaded chunks of a test world.";
	}

	@Override
	public Icon icon() {
		return Icon.ANALYSIS;
	}

	@Override
	protected void onLayout() {
		int controlsHeight = 44;
		controls.setBounds(x, y, width, controlsHeight);
		int listWidth = Math.max(130, Math.min(180, width * 2 / 5));
		list.setBounds(x, y + controlsHeight + Theme.GAP, listWidth, height - controlsHeight - Theme.GAP);
		details.setBounds(x + listWidth + Theme.PAD, y + controlsHeight + Theme.GAP, width - listWidth - Theme.PAD, height - controlsHeight - Theme.GAP);
		rebuildList();
		rebuildDetails();
	}

	@Override
	public void tick() {
		GeodeObservation selected = analyzer.selected();
		String selection = selected == null ? "" : selected.id() + selected.timestampMillis();
		int count = analyzer.visibleResults().size() * 31 + analyzer.results().size();
		if (count != shownResultCount) {
			rebuildList();
		}

		if (!selection.equals(shownSelection)) {
			savedRecordId = null;
			rebuildDetails();
		}
	}

	private void rebuildList() {
		shownResultCount = analyzer.visibleResults().size() * 31 + analyzer.results().size();
		List<Widget> rows = new ArrayList<>();
		for (GeodeObservation geode : analyzer.visibleResults()) {
			rows.add(new GeodeRow(geode));
		}

		if (rows.isEmpty()) {
			String text = analyzer.results().isEmpty() ? "No geodes yet. Press Scan to read the loaded chunks around you."
					: "No geodes match the test-fixture filter (see Settings).";
			rows.add(new ModulesPage.Message(text));
		}

		list.setChildren(rows);
	}

	private void rebuildDetails() {
		GeodeObservation geode = analyzer.selected();
		shownSelection = geode == null ? "" : geode.id() + geode.timestampMillis();
		List<Widget> rows = new ArrayList<>();
		if (showSettings) {
			rows.add(new Heading("Analyzer settings", "These are saved in your profile. Hover a setting for an explanation."));
			rows.addAll(SettingRow.rowsFor(analyzer.settings().all()));
		} else if (geode == null) {
			rows.add(new ModulesPage.Message("Select a geode on the left to see its analysis."));
		} else {
			rows.add(new Summary(geode));
			rows.add(new StageChart(geode));
			rows.add(new Observation(geode));
			rows.add(new Estimates(geode));
			rows.add(new SaveRow(geode));
		}

		details.setChildren(rows);
	}

	// ----- top controls ----------------------------------------------------------------------

	private final class Controls extends Container {
		private final ToggleSwitch enabled;
		private final Button scan;
		private final Button cancel;
		private final Button settings;

		Controls() {
			enabled = add(new ToggleSwitch(analyzer::isEnabled, value -> femboy.modules().setEnabled(analyzer, value)));
			enabled.tooltip("Turn the analyzer on or off. It runs only in singleplayer or on authorized test servers.");
			scan = add(new Button(() -> analyzer.results().isEmpty() ? "Scan" : "Rescan", Button.Style.PRIMARY, () -> {
				analyzer.startScan();
				savedRecordId = null;
			}).icon(Icon.PLAY));
			scan.enabledWhen(() -> analyzer.isRunning() && !analyzer.scanner().busy());
			scan.tooltip("Read the loaded chunks within the analysis radius and group amethyst blocks into geodes.");
			cancel = add(new Button("Cancel", Button.Style.SECONDARY, analyzer::cancelScan).icon(Icon.STOP));
			cancel.enabledWhen(() -> analyzer.scanner().busy());
			cancel.tooltip("Stop the running scan. Results from the previous scan are kept.");
			settings = add(new Button(() -> showSettings ? "Results" : "Settings", Button.Style.SECONDARY, () -> {
				showSettings = !showSettings;
				rebuildDetails();
			}).icon(Icon.SETTINGS));
			settings.tooltip("Switch the right panel between the selected geode and the analyzer's settings.");
		}

		@Override
		protected void onLayout() {
			enabled.setBounds(x + 6, y + 6, ToggleSwitch.WIDTH, ToggleSwitch.HEIGHT);
			int right = x + width - 6;
			settings.setBounds(right - 62, y + 4, 62, 14);
			cancel.setBounds(right - 62 - Theme.GAP - 54, y + 4, 54, 14);
			scan.setBounds(right - 62 - Theme.GAP - 54 - Theme.GAP - 58, y + 4, 58, 14);
		}

		@Override
		public void render(UiContext ui) {
			ui.roundRect(x, y, width, height, Theme.SURFACE);
			String state = !analyzer.isEnabled() ? "Off" : analyzer.isRunning() ? "On" : "Paused";
			int stateColor = !analyzer.isEnabled() ? Theme.TEXT_MUTED : analyzer.isRunning() ? Theme.SUCCESS : Theme.WARNING;
			ui.text("Analyzer", x + 31, y + 7, Theme.TEXT);
			ui.text(state, x + 31 + ui.textWidth("Analyzer") + 5, y + 7, stateColor);

			WorldScanner scanner = analyzer.scanner();
			String message = !analyzer.isEnabled() ? "Turn the analyzer on to scan."
					: !analyzer.isRunning() ? femboy.modules().availability(analyzer).reason() : scanner.message();
			int barY = y + 22;
			int barWidth = width - 12;
			ui.rect(x + 6, barY, barWidth, 3, 0xFF2C2C36);
			if (scanner.busy() || scanner.state() == WorldScanner.State.DONE) {
				float progress = scanner.state() == WorldScanner.State.DONE ? 1 : scanner.progress();
				ui.rect(x + 6, barY, Math.round(barWidth * progress), 3, Theme.ACCENT);
			}

			boolean cut = ui.textClipped(message, x + 6, y + 30, width - 12, Theme.TEXT_SECONDARY);
			String radius = "Analysis radius " + analyzer.radiusChunks() + " chunks; your client has chunks loaded up to "
					+ analyzer.clientChunkRadius() + " chunks away. A larger radius never loads more chunks.";
			if (ui.hovered(x, y + 20, width, 22)) {
				ui.tooltip(this, (cut ? message + "\n\n" : "") + radius);
			}

			super.render(ui);
		}
	}

	// ----- geode list ------------------------------------------------------------------------

	private final class GeodeRow extends Widget {
		private final GeodeObservation geode;

		GeodeRow(GeodeObservation geode) {
			this.geode = geode;
		}

		@Override
		public int preferredHeight(UiContext ui, int width) {
			return 34;
		}

		@Override
		public void render(UiContext ui) {
			GeodeObservation selected = analyzer.selected();
			boolean isSelected = selected != null && selected.id().equals(geode.id());
			boolean hot = ui.hovered(x, y, width, height);
			ui.roundRect(x, y, width, height, isSelected ? Theme.SURFACE_ACTIVE : hot ? Theme.SURFACE_HOVER : Theme.SURFACE);
			if (isSelected) {
				ui.rect(x, y + 3, 2, height - 6, Theme.ACCENT);
			}

			String title = geode.minX() + ", " + geode.minY() + ", " + geode.minZ();
			ui.textClipped(title, x + 6, y + 4, width - 30, Theme.TEXT);
			ui.icon(geode.complete() ? Icon.CHECK : Icon.WARNING, x + width - 14, y + 4, 8, geode.complete() ? Theme.SUCCESS : Theme.WARNING);
			String sub = geode.counts().total() + " growth objects" + (geode.scenarioId() != null ? " · fixture" : "");
			ui.textClipped(sub, x + 6, y + 14, width - 12, Theme.TEXT_MUTED);
			stackedBar(ui, geode.counts(), x + 6, y + 26, width - 12, 4, false);
			if (hot) {
				ui.pointer();
				ui.tooltip(this, (geode.complete() ? "Complete observation" : "Incomplete observation") + "\n" + geode.completenessNote());
			}
		}

		@Override
		public boolean mouseClicked(UiContext ui, double mouseX, double mouseY, int button) {
			if (button == 0 && contains(mouseX, mouseY)) {
				analyzer.select(geode.id());
				return true;
			}

			return false;
		}
	}

	/** 100% stacked bar of the four stages, with a 1px gap between segments. */
	private static void stackedBar(UiContext ui, StageCounts counts, int x, int y, int width, int height, boolean tooltips) {
		int total = counts.total();
		if (total == 0) {
			ui.rect(x, y, width, height, 0xFF2C2C36);
			return;
		}

		int cursor = x;
		GrowthStage[] stages = GrowthStage.values();
		for (int i = 0; i < stages.length; i++) {
			int count = counts.get(stages[i]);
			if (count == 0) continue;
			int end = i == stages.length - 1 ? x + width : x + Math.round(width * cumulative(counts, i) / (float) total);
			int segment = Math.max(1, end - cursor - 1);
			ui.rect(cursor, y, segment, height, AmethystGeodeAnalyzerModule.stageColor(stages[i]));
			if (tooltips && ui.hovered(cursor, y, segment, height)) {
				ui.tooltip(stages[i], stages[i].label() + ": " + count + " of " + total + " growth objects (" + percent(count, total) + ")");
			}

			cursor = end;
		}
	}

	private static int cumulative(StageCounts counts, int index) {
		int sum = 0;
		for (int i = 0; i <= index; i++) sum += counts.get(GrowthStage.values()[i]);
		return sum;
	}

	private static String percent(int part, int total) {
		return total == 0 ? "n/a" : String.format(Locale.ROOT, "%.1f%%", part * 100.0 / total);
	}

	// ----- detail cards ----------------------------------------------------------------------

	private static final class Heading extends Widget {
		private final String title;
		private final String text;

		Heading(String title, String text) {
			this.title = title;
			this.text = text;
		}

		@Override
		public int preferredHeight(UiContext ui, int width) {
			return 15 + ui.wrappedHeight(text, width - 4);
		}

		@Override
		public void render(UiContext ui) {
			ui.text(title, x + 2, y + 3, Theme.ACCENT_STRONG);
			ui.wrapped(text, x + 2, y + 14, width - 4, Theme.TEXT_MUTED);
		}
	}

	/** Location, completeness and evidence quality badges. */
	private final class Summary extends Widget {
		private final GeodeObservation geode;

		Summary(GeodeObservation geode) {
			this.geode = geode;
		}

		@Override
		public int preferredHeight(UiContext ui, int width) {
			return 44 + ui.wrappedHeight(geode.completenessNote(), width - 12);
		}

		@Override
		public void render(UiContext ui) {
			ui.roundRect(x, y, width, height, Theme.SURFACE);
			ui.icon(Icon.ANALYSIS, x + 6, y + 6, 9, Theme.ACCENT);
			ui.textClipped("Geode at " + geode.minX() + ", " + geode.minY() + ", " + geode.minZ(), x + 19, y + 7, width - 25, Theme.TEXT);
			ui.textClipped(geode.dimension() + " · size " + (geode.maxX() - geode.minX() + 1) + "x" + (geode.maxY() - geode.minY() + 1) + "x"
					+ (geode.maxZ() - geode.minZ() + 1), x + 6, y + 19, width - 12, Theme.TEXT_MUTED);

			int badgeX = badge(ui, x + 6, y + 30, geode.complete() ? "Complete" : "Incomplete", geode.complete() ? Theme.SUCCESS : Theme.WARNING);
			EvidenceQuality quality = EvidenceQuality.rate(geode, analyzer.minimumSample());
			int qualityColor = switch (quality) {
				case ADEQUATE -> Theme.SUCCESS;
				case LIMITED -> Theme.WARNING;
				case INSUFFICIENT -> Theme.ERROR;
			};
			int qx = badgeX + 4;
			int qEnd = badge(ui, qx, y + 30, "Evidence: " + quality.label(), qualityColor);
			if (ui.hovered(qx, y + 29, qEnd - qx, 11)) {
				ui.tooltip(quality, "Evidence quality rates the data (completeness and sample size), not a probability. "
						+ quality.explanation() + " Minimum sample: " + analyzer.minimumSample() + " growth objects.");
			}

			ui.wrapped(geode.completenessNote(), x + 6, y + 43, width - 12, geode.complete() ? Theme.TEXT_SECONDARY : Theme.WARNING);
		}

		private int badge(UiContext ui, int bx, int by, String text, int color) {
			int w = ui.textWidth(text) + 8;
			ui.roundRect(bx, by, w, 11, Theme.withAlpha(color, 0.15f));
			ui.text(text, bx + 4, by + 2, color);
			return bx + w;
		}
	}

	/** Stacked bar plus a table of counts (the table doubles as the legend and the table view). */
	private final class StageChart extends Widget {
		private final GeodeObservation geode;

		StageChart(GeodeObservation geode) {
			this.geode = geode;
		}

		@Override
		public int preferredHeight(UiContext ui, int width) {
			return 36 + 4 * 11 + 14;
		}

		@Override
		public void render(UiContext ui) {
			StageCounts counts = geode.counts();
			ui.roundRect(x, y, width, height, Theme.SURFACE);
			ui.text("Growth-stage distribution", x + 6, y + 6, Theme.TEXT);
			ui.textRight("direct observation", x + width - 6, y + 6, Theme.TEXT_MUTED);
			stackedBar(ui, counts, x + 6, y + 19, width - 12, 8, true);

			int rowY = y + 34;
			for (GrowthStage stage : GrowthStage.values()) {
				int count = counts.get(stage);
				ui.rect(x + 6, rowY + 1, 7, 7, AmethystGeodeAnalyzerModule.stageColor(stage));
				ui.text(stage.label(), x + 17, rowY, Theme.TEXT_SECONDARY);
				String value = analyzer.showPercentages() ? count + "  (" + percent(count, counts.total()) + ")" : String.valueOf(count);
				ui.textRight(value, x + width - 6, rowY, Theme.TEXT);
				rowY += 11;
			}

			ui.rect(x + 6, rowY + 1, width - 12, 1, Theme.BORDER);
			ui.text("Total growth objects", x + 6, rowY + 4, Theme.TEXT);
			ui.textRight(String.valueOf(counts.total()), x + width - 6, rowY + 4, Theme.TEXT);
		}
	}

	/** Other directly observed quantities and metadata. */
	private final class Observation extends Widget {
		private final GeodeObservation geode;

		Observation(GeodeObservation geode) {
			this.geode = geode;
		}

		private List<String[]> lines() {
			GeodeObservation.FaceSummary faces = geode.faces();
			List<String[]> lines = new ArrayList<>();
			lines.add(new String[] {"Budding amethyst blocks", String.valueOf(geode.buddingBlocks())});
			lines.add(new String[] {"Amethyst blocks", String.valueOf(geode.amethystBlocks())});
			lines.add(new String[] {"Budding faces free / grown / blocked", faces.free() + " / " + faces.ownGrowth() + " / " + faces.blocked()});
			lines.add(new String[] {"Unreadable faces", String.valueOf(faces.unavailable())});
			lines.add(new String[] {"Unavailable positions nearby", String.valueOf(geode.unavailablePositions())});
			lines.add(new String[] {"Observed at", TIME.format(Instant.ofEpochMilli(geode.timestampMillis()))});
			lines.add(new String[] {"Test scenario run", geode.scenarioId() == null ? "none" : geode.scenarioId()});
			lines.add(new String[] {"Source", geode.source()});
			return lines;
		}

		@Override
		public int preferredHeight(UiContext ui, int width) {
			return 20 + lines().size() * 11;
		}

		@Override
		public void render(UiContext ui) {
			ui.roundRect(x, y, width, height, Theme.SURFACE);
			ui.text("Observation details", x + 6, y + 6, Theme.TEXT);
			int rowY = y + 19;
			for (String[] line : lines()) {
				int valueWidth = Math.min(ui.textWidth(line[1]), width / 2);
				ui.textClipped(line[0], x + 6, rowY, width - 18 - valueWidth, Theme.TEXT_SECONDARY);
				boolean cut = ui.textClipped(line[1], x + width - 6 - valueWidth, rowY, valueWidth, Theme.TEXT);
				if (cut && ui.hovered(x, rowY - 1, width, 11)) {
					ui.tooltip(line, line[1]);
				}

				rowY += 11;
			}
		}
	}

	/** Statistics derived from the counts, clearly labelled as estimates. */
	private final class Estimates extends Widget {
		private final GeodeObservation geode;

		Estimates(GeodeObservation geode) {
			this.geode = geode;
		}

		private static final String NOTE = "Estimates with 95% Wilson intervals. The intervals describe sampling uncertainty of the "
				+ "observed fractions only; they are not probabilities of player activity or of anything else.";

		@Override
		public int preferredHeight(UiContext ui, int width) {
			return 20 + 2 * 22 + ui.wrappedHeight(NOTE, width - 12) + 6;
		}

		@Override
		public void render(UiContext ui) {
			ui.roundRect(x, y, width, height, Theme.SURFACE);
			ui.text("Statistical estimates", x + 6, y + 6, Theme.TEXT);
			int rowY = y + 19;
			rowY = proportion(ui, "Clusters among all growth objects", geode.counts().clustersOfAll(), rowY);
			rowY = proportion(ui, "Clusters among large buds + clusters", geode.counts().clustersOfLargeAndClusters(), rowY);
			ui.wrapped(NOTE, x + 6, rowY + 2, width - 12, Theme.TEXT_MUTED);
		}

		private int proportion(UiContext ui, String label, Proportion p, int rowY) {
			ui.textClipped(label, x + 6, rowY, width - 12, Theme.TEXT_SECONDARY);
			String value;
			if (!p.defined()) {
				value = "undefined (no " + p.denominator() + ")";
			} else {
				double[] ci = p.wilson95();
				value = String.format(Locale.ROOT, "%.1f%%  (%d/%d, 95%%: %.0f-%.0f%%)", p.value() * 100, p.successes(), p.trials(), ci[0] * 100, ci[1] * 100);
			}

			ui.textClipped(value, x + 12, rowY + 10, width - 18, Theme.TEXT);
			if (ui.hovered(x, rowY - 1, width, 21)) {
				ui.tooltip(p, "Numerator: " + p.numerator() + ". Denominator: " + p.denominator() + ". " + p.describe());
			}

			return rowY + 22;
		}
	}

	private final class SaveRow extends Container {
		private final GeodeObservation geode;
		private final Button save;
		private final Button open;

		SaveRow(GeodeObservation geode) {
			this.geode = geode;
			save = add(new Button("Save observation", Button.Style.PRIMARY, this::saveNow).icon(Icon.CHECK));
			save.enabledWhen(() -> savedRecordId == null);
			save.tooltip("Store this observation and write a report (Test Results page and research/reports folder).");
			open = add(new Button("Open report", Button.Style.SECONDARY, () -> openResult.accept(savedRecordId)).icon(Icon.RESULTS));
			open.enabledWhen(() -> savedRecordId != null);
			open.tooltip("Show the saved report on the Test Results page.");
		}

		private void saveNow() {
			Map<String, String> context = new LinkedHashMap<>();
			context.put("World", String.valueOf(WorldIdentity.current()));
			context.put("Environment", femboy.modules().environment().describe());
			context.put("Minecraft", SharedConstants.getCurrentVersion().name());
			context.put("Analysis radius", analyzer.radiusChunks() + " chunks (client render distance " + Minecraft.getInstance().options.getEffectiveRenderDistance() + ")");
			context.put("Minimum sample size", String.valueOf(analyzer.minimumSample()));
			TestResultStore.Record record = femboy.results().save(geode, context, analyzer.minimumSample());
			savedRecordId = record.id();
			GuiState.selectedResult = record.id();
		}

		@Override
		public int preferredHeight(UiContext ui, int width) {
			return savedRecordId == null ? 16 : 28;
		}

		@Override
		protected void onLayout() {
			int half = (width - Theme.GAP) / 2;
			save.setBounds(x, y, half, 16);
			open.setBounds(x + half + Theme.GAP, y, width - half - Theme.GAP, 16);
		}

		@Override
		public void render(UiContext ui) {
			super.render(ui);
			if (savedRecordId != null) {
				ui.textClipped("Saved as " + savedRecordId, x + 2, y + 19, width - 4, Theme.SUCCESS);
			}
		}
	}
}
