package io.github.theamethyx.femboyclient.client.gui.widget;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import io.github.theamethyx.femboyclient.client.FemboyClient;
import io.github.theamethyx.femboyclient.client.analysis.GeodeScanner;
import io.github.theamethyx.femboyclient.client.gui.Icon;
import io.github.theamethyx.femboyclient.client.gui.Theme;
import io.github.theamethyx.femboyclient.client.gui.UiContext;
import io.github.theamethyx.femboyclient.client.modules.GeodeAnalyzerModule;
import io.github.theamethyx.femboyclient.core.research.GrowthStage;
import io.github.theamethyx.femboyclient.core.research.Proportion;
import io.github.theamethyx.femboyclient.core.research.StageCounts;
import io.github.theamethyx.femboyclient.core.research.geode.EvidenceQuality;
import io.github.theamethyx.femboyclient.core.research.geode.GeodeObservation;

/**
 * The Geode Analyzer's live panel inside its module settings: status and controls, one compact card per
 * geode (nearest first) and, for the selected geode, the full counts, observation details and estimates.
 * The card list follows the analyzer's results without the page having to rebuild.
 */
public class GeodePanel extends Container {
	private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());
	private static final int MAX_CARDS = 12;

	private final GeodeAnalyzerModule analyzer;
	private final StatusCard status;
	private Object shownResults;
	private String shownSelection;
	private boolean shownPercentages;
	private int shownCount = -1;

	public GeodePanel(GeodeAnalyzerModule analyzer) {
		this.analyzer = analyzer;
		this.status = new StatusCard();
	}

	private void rebuildIfNeeded(UiContext ui) {
		List<GeodeObservation> geodes = analyzer.geodes();
		GeodeObservation selected = analyzer.selected();
		String selection = selected == null ? null : selected.id();
		Object results = analyzer.scanner().results();
		if (results == shownResults && java.util.Objects.equals(selection, shownSelection)
				&& shownPercentages == analyzer.showPercentages() && shownCount == geodes.size()) {
			return;
		}

		shownResults = results;
		shownSelection = selection;
		shownPercentages = analyzer.showPercentages();
		shownCount = geodes.size();

		children.clear();
		children.add(status);
		if (geodes.isEmpty()) {
			children.add(new Note(emptyText()));
		}

		for (int i = 0; i < Math.min(MAX_CARDS, geodes.size()); i++) {
			GeodeObservation geode = geodes.get(i);
			children.add(new GeodeCard(geode));
			if (geode.id().equals(selection)) {
				children.add(new StageTable(geode));
				children.add(new ObservationDetails(geode));
				children.add(new Estimates(geode));
			}
		}

		if (geodes.size() > MAX_CARDS) {
			children.add(new Note((geodes.size() - MAX_CARDS) + " more geode(s) farther away are not listed."));
		}

		children.add(new ReferenceCard());
	}

	private String emptyText() {
		GeodeScanner scanner = analyzer.scanner();
		if (!analyzer.isRunning()) {
			return "Turn the analyzer on to start.";
		}

		if (scanner.resultsAt() == 0) {
			return "Reading loaded chunks...";
		}

		return "No geodes in the loaded chunks within " + analyzer.radiusChunks() + " chunks.";
	}

	@Override
	public int preferredHeight(UiContext ui, int width) {
		rebuildIfNeeded(ui);
		int total = 0;
		for (Widget child : children) {
			total += child.preferredHeight(ui, width) + Theme.GAP;
		}

		return Math.max(0, total - Theme.GAP);
	}

	@Override
	protected void onLayout() {
		int cy = y;
		for (Widget child : children) {
			int h = child.preferredHeight(null, width);
			child.setBounds(x, cy, width, h);
			cy += h + Theme.GAP;
		}
	}

	@Override
	public void render(UiContext ui) {
		rebuildIfNeeded(ui);
		int cy = y;
		for (Widget child : children) {
			int h = child.preferredHeight(ui, width);
			child.setBounds(x, cy, width, h);
			child.render(ui);
			cy += h + Theme.GAP;
		}
	}

	// ----- shared drawing --------------------------------------------------------------------

	/** 100% stacked bar of the four stages with a 1px gap between segments. */
	static void stackedBar(UiContext ui, StageCounts counts, int x, int y, int width, int height, boolean tooltips) {
		int total = counts.total();
		if (total == 0) {
			ui.rect(x, y, width, height, 0xFF2C2C36);
			return;
		}

		int cursor = x;
		int cumulative = 0;
		GrowthStage[] stages = GrowthStage.values();
		for (int i = 0; i < stages.length; i++) {
			int count = counts.get(stages[i]);
			cumulative += count;
			if (count == 0) {
				continue;
			}

			int end = x + Math.round(width * cumulative / (float) total);
			int segment = Math.max(1, end - cursor - (cumulative == total ? 0 : 1));
			ui.rect(cursor, y, segment, height, GeodeAnalyzerModule.stageColor(stages[i]));
			if (tooltips && ui.hovered(cursor, y, segment, height)) {
				ui.tooltip(stages[i], stages[i].label() + ": " + count + " of " + total + " growth objects (" + percent(count, total) + ")");
			}

			cursor = end;
		}
	}

	static String percent(int part, int total) {
		return total == 0 ? "n/a" : String.format(Locale.ROOT, "%.1f%%", part * 100.0 / total);
	}

	private static int badge(UiContext ui, int bx, int by, String text, int color) {
		int w = ui.textWidth(text) + 8;
		ui.roundRect(bx, by, w, 11, Theme.withAlpha(color, 0.15f));
		ui.text(text, bx + 4, by + 2, color);
		return w;
	}

	private static int qualityColor(EvidenceQuality quality) {
		return switch (quality) {
			case ADEQUATE -> Theme.SUCCESS;
			case LIMITED -> Theme.WARNING;
			case INSUFFICIENT -> Theme.ERROR;
		};
	}

	private static String ago(long millis) {
		long seconds = Math.max(0, (System.currentTimeMillis() - millis) / 1000);
		return seconds < 60 ? seconds + " s ago" : seconds / 60 + " min ago";
	}

	// ----- status ----------------------------------------------------------------------------

	/** What the analyzer is doing, chunk coverage, and Pause / Read again. */
	private final class StatusCard extends Container {
		private final Button pause;
		private final Button rescan;

		StatusCard() {
			pause = add(new Button(() -> analyzer.paused() ? "Resume" : "Pause", Button.Style.SECONDARY,
					() -> analyzer.setPaused(!analyzer.paused())));
			pause.icon(Icon.PAUSE).enabledWhen(analyzer::isRunning);
			pause.tooltip("Pause stops all reading and analysis; the last results stay visible. Turning the module off also clears them.");
			rescan = add(new Button("Read again", Button.Style.SECONDARY, analyzer::rescanNow).icon(Icon.RESET));
			rescan.enabledWhen(() -> analyzer.isRunning() && !analyzer.paused());
			rescan.tooltip("Read every loaded chunk in range again now instead of waiting for the scan interval.");
		}

		private String headline() {
			if (!analyzer.isEnabled()) return "Off";
			if (!analyzer.isRunning()) return FemboyClient.get().modules().availability(analyzer).reason();
			if (analyzer.paused()) return "Paused";
			GeodeScanner scanner = analyzer.scanner();
			if (scanner.failure() != null) return scanner.failure();
			return scanner.upToDate() ? "Up to date" : scanner.analyzing() ? "Analyzing..." : "Reading chunks...";
		}

		private int headlineColor() {
			if (!analyzer.isRunning() || analyzer.scanner().failure() != null) return Theme.WARNING;
			if (analyzer.paused()) return Theme.TEXT_SECONDARY;
			return analyzer.scanner().upToDate() ? Theme.SUCCESS : Theme.ACCENT_STRONG;
		}

		private List<String> lines() {
			GeodeScanner scanner = analyzer.scanner();
			List<String> lines = new ArrayList<>();
			lines.add("Read " + scanner.readColumns() + " of " + scanner.loadedColumns() + " loaded chunks within " + analyzer.radiusChunks() + " chunks");
			if (scanner.unloadedColumns() > 0) {
				lines.add(scanner.unloadedColumns() + " chunk(s) in range are not loaded on this client (unavailable)");
			}

			lines.add(scanner.resultsAt() == 0 ? "No analysis yet" : "Last analysis " + ago(scanner.resultsAt()));
			if (analyzer.debug()) {
				lines.add("Cache: " + scanner.cachedColumns() + " columns, " + scanner.cachedCells() + " amethyst blocks; analysis took "
						+ scanner.lastAnalysisMillis() + " ms");
			}

			return lines;
		}

		@Override
		public int preferredHeight(UiContext ui, int width) {
			return 22 + lines().size() * 10 + 4 + 16 + 6;
		}

		@Override
		protected void onLayout() {
			int half = (width - 12 - Theme.GAP) / 2;
			pause.setBounds(x + 6, y + height - 22, half, 16);
			rescan.setBounds(x + 6 + half + Theme.GAP, y + height - 22, width - 12 - half - Theme.GAP, 16);
		}

		@Override
		public void render(UiContext ui) {
			ui.roundRect(x, y, width, height, Theme.SURFACE);
			ui.text("Status", x + 6, y + 6, Theme.TEXT);
			ui.textRight(ui.ellipsize(headline(), width - 50), x + width - 6, y + 6, headlineColor());
			int ly = y + 20;
			for (String line : lines()) {
				ui.textClipped(line, x + 6, ly, width - 12, Theme.TEXT_SECONDARY);
				ly += 10;
			}

			onLayout();
			super.render(ui);
		}
	}

	// ----- per-geode cards -------------------------------------------------------------------

	/** Compact summary of one geode; click to show or hide its details. */
	private final class GeodeCard extends Widget {
		private final GeodeObservation geode;

		GeodeCard(GeodeObservation geode) {
			this.geode = geode;
		}

		@Override
		public int preferredHeight(UiContext ui, int width) {
			return 52;
		}

		@Override
		public void render(UiContext ui) {
			boolean selected = geode.id().equals(shownSelection);
			boolean hot = ui.hovered(x, y, width, height);
			ui.roundRect(x, y, width, height, selected ? Theme.SURFACE_ACTIVE : hot ? Theme.SURFACE_HOVER : Theme.SURFACE);
			if (selected) {
				ui.rect(x, y + 4, 2, height - 8, Theme.ACCENT);
			}

			// Row 1: location and completeness.
			String completeness = geode.complete() ? "Complete" : "Incomplete";
			int completenessColor = geode.complete() ? Theme.SUCCESS : Theme.WARNING;
			int badgeWidth = ui.textWidth(completeness) + 8;
			String title = "Geode " + GeodeAnalyzerModule.distanceText(geode) + "  ·  " + geode.minX() + ", " + geode.minY() + ", " + geode.minZ();
			ui.textClipped(title, x + 6, y + 5, width - badgeWidth - 18, Theme.TEXT);
			badge(ui, x + width - 6 - badgeWidth, y + 3, completeness, completenessColor);

			// Row 2: distribution.
			StageCounts counts = geode.counts();
			stackedBar(ui, counts, x + 6, y + 17, width - 12, 5, true);

			// Row 3: per-stage counts, colored swatch = legend.
			int column = (width - 12) / 4;
			GrowthStage[] stages = GrowthStage.values();
			for (int i = 0; i < stages.length; i++) {
				int cx = x + 6 + i * column;
				int count = counts.get(stages[i]);
				ui.rect(cx, y + 27, 6, 6, GeodeAnalyzerModule.stageColor(stages[i]));
				String value = analyzer.showPercentages() ? count + " (" + percentShort(count, counts.total()) + ")" : String.valueOf(count);
				ui.textClipped(value, cx + 9, y + 26, column - 11, Theme.TEXT_SECONDARY);
				if (ui.hovered(cx, y + 25, column, 10)) {
					ui.tooltip(stages[i], stages[i].label() + ": " + count + " of " + counts.total() + " growth objects");
				}
			}

			// Row 4: sample size, time, evidence quality.
			EvidenceQuality quality = EvidenceQuality.rate(geode, analyzer.minimumSample());
			String evidence = "Evidence: " + quality.label();
			int evidenceWidth = ui.textWidth(evidence) + 8;
			long readAt = analyzer.scanner().dataReadAt(geode);
			String meta = "n = " + counts.total() + "  ·  observed " + TIME.format(Instant.ofEpochMilli(readAt > 0 ? readAt : geode.timestampMillis()));
			ui.textClipped(meta, x + 6, y + 40, width - evidenceWidth - 18, Theme.TEXT_MUTED);
			badge(ui, x + width - 6 - evidenceWidth, y + 38, evidence, qualityColor(quality));

			if (hot && ui.mouseY() >= y + 36) {
				ui.tooltip(quality, "Evidence quality rates the data (completeness and sample size), not a probability. " + quality.explanation()
						+ "\n\nn = number of buds and clusters, the denominator of every percentage.");
			} else if (hot && ui.mouseY() < y + 15) {
				ui.tooltip(geode, (geode.complete() ? "Complete observation. " : "Incomplete: counts are a lower bound. ") + geode.completenessNote()
						+ "\n\nClick to " + (selected ? "hide" : "show") + " details.");
			}

			if (hot) {
				ui.pointer();
			}
		}

		private static String percentShort(int part, int total) {
			return total == 0 ? "n/a" : String.format(Locale.ROOT, "%.0f%%", part * 100.0 / total);
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

	/** Exact counts with explicit denominators (doubles as the chart's table view). */
	private final class StageTable extends Widget {
		private final GeodeObservation geode;

		StageTable(GeodeObservation geode) {
			this.geode = geode;
		}

		@Override
		public int preferredHeight(UiContext ui, int width) {
			return 20 + 4 * 11 + 16;
		}

		@Override
		public void render(UiContext ui) {
			StageCounts counts = geode.counts();
			ui.roundRect(x, y, width, height, Theme.SURFACE);
			ui.text("Growth stages", x + 6, y + 6, Theme.TEXT);
			ui.textRight("observed block states", x + width - 6, y + 6, Theme.TEXT_MUTED);
			int rowY = y + 20;
			for (GrowthStage stage : GrowthStage.values()) {
				int count = counts.get(stage);
				ui.rect(x + 6, rowY + 1, 7, 7, GeodeAnalyzerModule.stageColor(stage));
				ui.text(stage.label(), x + 17, rowY, Theme.TEXT_SECONDARY);
				String value = analyzer.showPercentages() ? count + "  (" + percent(count, counts.total()) + ")" : String.valueOf(count);
				ui.textRight(value, x + width - 6, rowY, Theme.TEXT);
				rowY += 11;
			}

			ui.rect(x + 6, rowY + 1, width - 12, 1, Theme.BORDER);
			ui.text("Total growth objects (denominator)", x + 6, rowY + 4, Theme.TEXT);
			ui.textRight(String.valueOf(counts.total()), x + width - 6, rowY + 4, Theme.TEXT);
		}
	}

	/** Other directly observed quantities. */
	private final class ObservationDetails extends Widget {
		private final GeodeObservation geode;

		ObservationDetails(GeodeObservation geode) {
			this.geode = geode;
		}

		private List<String[]> lines() {
			GeodeObservation.FaceSummary faces = geode.faces();
			long readAt = analyzer.scanner().dataReadAt(geode);
			List<String[]> lines = new ArrayList<>();
			lines.add(new String[] {"Completeness", geode.complete() ? "Complete" : "Incomplete"});
			lines.add(new String[] {"Unavailable positions nearby", String.valueOf(geode.unavailablePositions())});
			lines.add(new String[] {"Budding amethyst blocks", String.valueOf(geode.buddingBlocks())});
			lines.add(new String[] {"Budding faces free / grown / blocked", faces.free() + " / " + faces.ownGrowth() + " / " + faces.blocked()});
			lines.add(new String[] {"Unreadable faces", String.valueOf(faces.unavailable())});
			lines.add(new String[] {"Size", (geode.maxX() - geode.minX() + 1) + " x " + (geode.maxY() - geode.minY() + 1) + " x " + (geode.maxZ() - geode.minZ() + 1)});
			lines.add(new String[] {"Oldest data read", readAt > 0 ? TIME.format(Instant.ofEpochMilli(readAt)) + " (" + ago(readAt) + ")" : "unknown"});
			return lines;
		}

		@Override
		public int preferredHeight(UiContext ui, int width) {
			int note = ui == null ? 20 : ui.wrappedHeight(geode.completenessNote(), width - 12);
			return 20 + lines().size() * 11 + note + 6;
		}

		@Override
		public void render(UiContext ui) {
			ui.roundRect(x, y, width, height, Theme.SURFACE);
			ui.text("Observation", x + 6, y + 6, Theme.TEXT);
			int rowY = y + 19;
			for (String[] line : lines()) {
				int valueWidth = Math.min(ui.textWidth(line[1]), width / 2);
				ui.textClipped(line[0], x + 6, rowY, width - 18 - valueWidth, Theme.TEXT_SECONDARY);
				ui.textClipped(line[1], x + width - 6 - valueWidth, rowY, valueWidth, Theme.TEXT);
				rowY += 11;
			}

			ui.wrapped(geode.completenessNote(), x + 6, rowY + 2, width - 12, geode.complete() ? Theme.TEXT_MUTED : Theme.WARNING);
		}
	}

	/** Statistics derived from the counts, labelled as estimates with their denominators. */
	private final class Estimates extends Widget {
		private static final String NOTE = "95% Wilson intervals describe sampling uncertainty of the observed fractions only. They are not "
				+ "probabilities of player activity or of anything else.";
		private final GeodeObservation geode;

		Estimates(GeodeObservation geode) {
			this.geode = geode;
		}

		@Override
		public int preferredHeight(UiContext ui, int width) {
			int note = ui == null ? 30 : ui.wrappedHeight(NOTE, width - 12);
			return 20 + 2 * 22 + note + 6;
		}

		@Override
		public void render(UiContext ui) {
			ui.roundRect(x, y, width, height, Theme.SURFACE);
			ui.text("Estimates", x + 6, y + 6, Theme.TEXT);
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

	/** What controlled tests showed, quoted from the research report; never applied to this geode as a verdict. */
	private static final class ReferenceCard extends Widget {
		private static final String TEXT = "Reference from controlled tests (vanilla geodes in a flat test world, growth simulated with the "
				+ "game's own random-tick code; see docs/research/evaluation.md):\n"
				+ "- The cluster share separated less than 2 hours from 2 hours or more of simulated ticking with 2-7% false positives "
				+ "and 3-5% false negatives on held-out geodes (three runs).\n"
				+ "- It cannot tell 5-24 hours from longer: the share is near 100% after about 6 hours.\n"
				+ "- World generation already places clusters, so a single cluster means nothing on its own.\n"
				+ "- Harvesting resets it: harvested and regrown geodes looked young.\n"
				+ "These results describe simulated ticking in test worlds. They are not evidence of player activity or of a base.";

		@Override
		public int preferredHeight(UiContext ui, int width) {
			return (ui == null ? 90 : ui.wrappedHeight(TEXT, width - 12)) + 20;
		}

		@Override
		public void render(UiContext ui) {
			ui.roundRect(x, y, width, height, Theme.withAlpha(Theme.SURFACE, 0.6f));
			ui.icon(Icon.INFO, x + 6, y + 5, 8, Theme.TEXT_MUTED);
			ui.text("Interpreting the numbers", x + 18, y + 6, Theme.TEXT_SECONDARY);
			ui.wrapped(TEXT, x + 6, y + 18, width - 12, Theme.TEXT_MUTED);
		}
	}

	private static final class Note extends Widget {
		private final String text;

		Note(String text) {
			this.text = text;
		}

		@Override
		public int preferredHeight(UiContext ui, int width) {
			return (ui == null ? 10 : ui.wrappedHeight(text, width - 12)) + 10;
		}

		@Override
		public void render(UiContext ui) {
			ui.wrapped(text, x + 6, y + 5, width - 12, Theme.TEXT_MUTED);
		}
	}
}
