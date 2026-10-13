package io.github.theamethyx.nova.client.gui.page;

import java.nio.file.Files;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import net.minecraft.util.Util;

import io.github.theamethyx.nova.client.gui.GuiState;
import io.github.theamethyx.nova.client.gui.Icon;
import io.github.theamethyx.nova.client.gui.Theme;
import io.github.theamethyx.nova.client.gui.UiContext;
import io.github.theamethyx.nova.client.gui.widget.Button;
import io.github.theamethyx.nova.client.gui.widget.Container;
import io.github.theamethyx.nova.client.gui.widget.ScrollList;
import io.github.theamethyx.nova.client.gui.widget.Widget;
import io.github.theamethyx.nova.core.research.GroundTruthManager;
import io.github.theamethyx.nova.core.research.GrowthStage;
import io.github.theamethyx.nova.core.research.StageCounts;
import io.github.theamethyx.nova.core.research.TestResultStore;
import io.github.theamethyx.nova.core.research.geode.GeodeObservation;

/** Saved observations and their reports. */
public class ResultsPage extends Page {
	private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

	private final ScrollList list;
	private final ScrollList details;
	private int shownCount = -1;
	private String shownSelection;
	private String pendingDelete;
	private long pendingDeleteAt;

	public ResultsPage(UiContext ui) {
		super(ui);
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
		return "Saved observations and research reports, with the files they were written to.";
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
		int listWidth = Math.max(130, Math.min(180, width * 2 / 5));
		list.setBounds(x, y, listWidth, height);
		details.setBounds(x + listWidth + Theme.PAD, y, width - listWidth - Theme.PAD, height);
		rebuild();
	}

	@Override
	public void tick() {
		if (nova.results().all().size() != shownCount || !java.util.Objects.equals(GuiState.selectedResult, shownSelection)) {
			rebuild();
		}
	}

	private void rebuild() {
		List<TestResultStore.Record> records = new ArrayList<>(nova.results().all());
		java.util.Collections.reverse(records);
		shownCount = records.size();
		if (GuiState.selectedResult == null && !records.isEmpty()) {
			GuiState.selectedResult = records.getFirst().id();
		}

		shownSelection = GuiState.selectedResult;
		List<Widget> rows = new ArrayList<>();
		for (TestResultStore.Record record : records) {
			rows.add(new RecordRow(record));
		}

		if (rows.isEmpty()) {
			rows.add(new ModulesPage.Message("No saved results yet. Save an observation on the World Analysis page."));
		}

		list.setChildren(rows);

		List<Widget> detailRows = new ArrayList<>();
		TestResultStore.Record selected = GuiState.selectedResult == null ? null : nova.results().get(GuiState.selectedResult);
		if (selected == null) {
			detailRows.add(new ModulesPage.Message("Select a result on the left."));
		} else {
			detailRows.add(new RecordDetails(selected));
			detailRows.add(new RecordActions(selected));
		}

		details.setChildren(detailRows);
	}

	private final class RecordRow extends Widget {
		private final TestResultStore.Record record;

		RecordRow(TestResultStore.Record record) {
			this.record = record;
		}

		@Override
		public int preferredHeight(UiContext ui, int width) {
			return 26;
		}

		@Override
		public void render(UiContext ui) {
			boolean selected = record.id().equals(GuiState.selectedResult);
			boolean hot = ui.hovered(x, y, width, height);
			ui.roundRect(x, y, width, height, selected ? Theme.SURFACE_ACTIVE : hot ? Theme.SURFACE_HOVER : Theme.SURFACE);
			if (selected) {
				ui.rect(x, y + 3, 2, height - 6, Theme.ACCENT);
			}

			GeodeObservation o = record.observation();
			ui.textClipped(TIME.format(Instant.ofEpochMilli(record.savedAt())) + "  geode", x + 6, y + 4, width - 12, Theme.TEXT);
			ui.textClipped(o.counts().total() + " growth objects" + (o.scenarioId() != null ? " · fixture" : "") + (o.complete() ? "" : " · incomplete"),
					x + 6, y + 14, width - 12, Theme.TEXT_MUTED);
			if (hot) {
				ui.pointer();
				ui.tooltip(this, record.id());
			}
		}

		@Override
		public boolean mouseClicked(UiContext ui, double mouseX, double mouseY, int button) {
			if (button == 0 && contains(mouseX, mouseY)) {
				GuiState.selectedResult = record.id();
				return true;
			}

			return false;
		}
	}

	private final class RecordDetails extends Widget {
		private final TestResultStore.Record record;
		private final List<String[]> lines = new ArrayList<>();

		RecordDetails(TestResultStore.Record record) {
			this.record = record;
			GeodeObservation o = record.observation();
			StageCounts c = o.counts();
			lines.add(new String[] {"Record", record.id()});
			lines.add(new String[] {"Geode", o.id()});
			lines.add(new String[] {"Completeness", o.complete() ? "Complete" : "Incomplete"});
			for (GrowthStage stage : GrowthStage.values()) {
				lines.add(new String[] {stage.label(), c.get(stage) + (c.total() == 0 ? "" : String.format(Locale.ROOT, "  (%.1f%%)", c.get(stage) * 100.0 / c.total()))});
			}

			lines.add(new String[] {"Total growth objects", String.valueOf(c.total())});
			lines.add(new String[] {"Clusters / all growth objects", c.clustersOfAll().describe()});
			lines.add(new String[] {"Clusters / (large + clusters)", c.clustersOfLargeAndClusters().describe()});
			lines.add(new String[] {"Scenario run", o.scenarioId() == null ? "none" : o.scenarioId()});
			GroundTruthManager.Record truth = nova.groundTruth().get(o.scenarioId());
			if (truth != null && truth.conditions().has("serverCensus")) {
				// Shown for verification only; the research classifier never reads ground truth.
				var census = truth.conditions().getAsJsonObject("serverCensus");
				int total = census.get("small").getAsInt() + census.get("medium").getAsInt() + census.get("large").getAsInt() + census.get("cluster").getAsInt();
				boolean match = census.get("small").getAsInt() == c.small() && census.get("medium").getAsInt() == c.medium()
						&& census.get("large").getAsInt() == c.large() && census.get("cluster").getAsInt() == c.cluster();
				lines.add(new String[] {"Fixture ground-truth total", total + (match ? " (counts match)" : " (counts differ)")});
			}

			record.context().forEach((key, value) -> lines.add(new String[] {key, value}));
		}

		/** Rows whose label and value do not fit side by side use two lines. */
		private boolean twoLines(UiContext ui, String[] line, int width) {
			return ui.textWidth(line[0]) + ui.textWidth(line[1]) + 18 > width;
		}

		@Override
		public int preferredHeight(UiContext ui, int width) {
			int h = 20;
			for (String[] line : lines) {
				h += twoLines(ui, line, width) ? 21 : 11;
			}

			return h;
		}

		@Override
		public void render(UiContext ui) {
			ui.roundRect(x, y, width, height, Theme.SURFACE);
			ui.text("Saved geode observation", x + 6, y + 6, Theme.TEXT);
			int rowY = y + 19;
			for (String[] line : lines) {
				int color = line[1].contains("differ") ? Theme.ERROR : Theme.TEXT;
				if (twoLines(ui, line, width)) {
					ui.textClipped(line[0], x + 6, rowY, width - 12, Theme.TEXT_SECONDARY);
					boolean cut = ui.textClipped(line[1], x + 12, rowY + 10, width - 18, color);
					if (cut && ui.hovered(x, rowY - 1, width, 21)) {
						ui.tooltip(line, line[1]);
					}

					rowY += 21;
				} else {
					ui.text(line[0], x + 6, rowY, Theme.TEXT_SECONDARY);
					ui.textRight(line[1], x + width - 6, rowY, color);
					rowY += 11;
				}
			}
		}
	}

	private final class RecordActions extends Container {
		private final Button open;
		private final Button delete;

		RecordActions(TestResultStore.Record record) {
			open = add(new Button("Open report file", Button.Style.SECONDARY, () -> Util.getPlatform().openPath(nova.results().reportPath(record.id())))
					.icon(Icon.FOLDER));
			open.enabledWhen(() -> Files.exists(nova.results().reportPath(record.id())));
			open.tooltip(() -> "Open " + nova.results().reportPath(record.id()).toAbsolutePath() + " in your default Markdown viewer.");
			delete = add(new Button(() -> confirming(record) ? "Confirm delete" : "Delete", Button.Style.DANGER, () -> {
				if (!confirming(record)) {
					pendingDelete = record.id();
					pendingDeleteAt = System.currentTimeMillis();
					return;
				}

				nova.results().delete(record.id());
				GuiState.selectedResult = null;
				pendingDelete = null;
			}).icon(Icon.TRASH));
			delete.tooltip("Delete this record and its report file. Click twice to confirm.");
		}

		private boolean confirming(TestResultStore.Record record) {
			return record.id().equals(pendingDelete) && System.currentTimeMillis() - pendingDeleteAt < 4000;
		}

		@Override
		public int preferredHeight(UiContext ui, int width) {
			return 16;
		}

		@Override
		protected void onLayout() {
			int half = (width - Theme.GAP) / 2;
			open.setBounds(x, y, half, 16);
			delete.setBounds(x + half + Theme.GAP, y, width - half - Theme.GAP, 16);
		}
	}
}
