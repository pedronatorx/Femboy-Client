package io.github.theamethyx.nova.client.gui.page;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import net.minecraft.client.Minecraft;

import io.github.theamethyx.nova.client.gui.Icon;
import io.github.theamethyx.nova.client.gui.Theme;
import io.github.theamethyx.nova.client.gui.UiContext;
import io.github.theamethyx.nova.client.gui.widget.Button;
import io.github.theamethyx.nova.client.gui.widget.Container;
import io.github.theamethyx.nova.client.gui.widget.Dropdown;
import io.github.theamethyx.nova.client.gui.widget.InfoCard;
import io.github.theamethyx.nova.client.gui.widget.ScrollList;
import io.github.theamethyx.nova.client.gui.widget.Widget;
import io.github.theamethyx.nova.core.diag.DiagnosticLogger;
import io.github.theamethyx.nova.core.diag.PerformanceMonitor;

/** Frame timing, per-module cost and the diagnostic log. */
public class DiagnosticsPage extends Page {
	private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());

	enum LogFilter {
		ALL("All messages"), WARNINGS("Warnings and errors"), ERRORS("Errors only");

		final String label;

		LogFilter(String label) {
			this.label = label;
		}

		boolean accepts(DiagnosticLogger.Level level) {
			return switch (this) {
				case ALL -> true;
				case WARNINGS -> level != DiagnosticLogger.Level.INFO;
				case ERRORS -> level == DiagnosticLogger.Level.ERROR;
			};
		}
	}

	private static LogFilter filter = LogFilter.ALL;

	private final ScrollList content;
	private int shownLogSize = -1;
	private LogFilter shownFilter;

	public DiagnosticsPage(UiContext ui) {
		super(ui);
		content = add(new ScrollList(ui, Theme.GAP, 0));
	}

	@Override
	public String title() {
		return "Performance & Diagnostics";
	}

	@Override
	public String navLabel() {
		return "Diagnostics";
	}

	@Override
	public String subtitle() {
		return "Frame timing, how long each module takes, and recent messages and errors.";
	}

	@Override
	public Icon icon() {
		return Icon.PERFORMANCE;
	}

	@Override
	protected void onLayout() {
		content.setBounds(x, y, width, height);
		rebuild();
	}

	@Override
	public void tick() {
		int size = nova.diagnostics().snapshot().size();
		if (size != shownLogSize || filter != shownFilter) {
			rebuild();
		}
	}

	private void rebuild() {
		List<DiagnosticLogger.Entry> entries = nova.diagnostics().snapshot();
		shownLogSize = entries.size();
		shownFilter = filter;

		List<Widget> rows = new ArrayList<>();
		rows.add(new InfoCard("Frame timing", Icon.PERFORMANCE, this::frameLines));
		rows.add(new FrameGraph());
		rows.add(new TimingTable());
		rows.add(new LogHeader());

		int shown = 0;
		for (int i = entries.size() - 1; i >= 0 && shown < 150; i--) {
			DiagnosticLogger.Entry entry = entries.get(i);
			if (filter.accepts(entry.level())) {
				rows.add(new LogRow(entry));
				shown++;
			}
		}

		if (shown == 0) {
			rows.add(new ModulesPage.Message("No messages for this filter."));
		}

		content.setChildren(rows);
	}

	private List<InfoCard.Line> frameLines() {
		PerformanceMonitor.FrameStats stats = nova.performance().frameStats();
		if (stats.samples() == 0) {
			return List.of(new InfoCard.Line("FPS", String.valueOf(Minecraft.getInstance().getFps())),
					new InfoCard.Line("Frame samples", "None yet (load a world)", Theme.TEXT_MUTED));
		}

		return List.of(
				new InfoCard.Line("FPS (Minecraft counter)", String.valueOf(Minecraft.getInstance().getFps())),
				new InfoCard.Line("Average frame time", String.format("%.2f ms", stats.averageFrameMs())),
				new InfoCard.Line("Slowest frame", String.format("%.2f ms", stats.worstFrameMs()),
						stats.worstFrameMs() > 50 ? Theme.WARNING : Theme.TEXT, "Longest single frame in the sampled window."),
				new InfoCard.Line("1% low FPS", String.format("%.0f", stats.onePercentLowFps()), Theme.TEXT,
						"Frame rate computed from the slowest 1% of sampled frames; shows stutter better than the average."),
				new InfoCard.Line("Samples", stats.samples() + " frames", Theme.TEXT_SECONDARY));
	}

	/** Bar graph of recent frame times; the line marks 16.7 ms (60 FPS). */
	private final class FrameGraph extends Widget {
		@Override
		public int preferredHeight(UiContext ui, int width) {
			return 54;
		}

		@Override
		public void render(UiContext ui) {
			ui.roundRect(x, y, width, height, Theme.SURFACE);
			ui.text("Frame times (newest right)", x + 6, y + 5, Theme.TEXT_SECONDARY);
			long[] frames = nova.performance().frameHistory();
			int graphX = x + 6, graphY = y + 16, graphW = width - 12, graphH = height - 21;
			ui.rect(graphX, graphY, graphW, graphH, 0x30000000);
			double scaleMs = 50;
			int sixty = graphY + graphH - (int) Math.round(graphH * 16.7 / scaleMs);
			ui.rect(graphX, sixty, graphW, 1, Theme.withAlpha(Theme.SUCCESS, 0.5f));

			int bars = Math.min(frames.length, graphW);
			for (int i = 0; i < bars; i++) {
				double ms = frames[frames.length - bars + i] / 1e6;
				int barHeight = (int) Math.min(graphH, Math.round(graphH * ms / scaleMs));
				int color = ms > 33.4 ? Theme.ERROR : ms > 16.7 ? Theme.WARNING : Theme.ACCENT;
				ui.rect(graphX + graphW - bars + i, graphY + graphH - barHeight, 1, barHeight, color);
			}

			if (ui.hovered(x, y, width, height)) {
				ui.tooltip(this, "Each bar is one rendered frame; full height is 50 ms. Purple is under 16.7 ms (60 FPS), "
						+ "amber under 33.3 ms, red slower. Only frames with a world loaded are sampled.");
			}
		}
	}

	/** The most expensive module lifecycle calls. */
	private final class TimingTable extends Widget {
		private static final int MAX_ROWS = 8;

		private List<PerformanceMonitor.Timing> top() {
			List<PerformanceMonitor.Timing> all = nova.performance().timings();
			return all.subList(0, Math.min(MAX_ROWS, all.size()));
		}

		@Override
		public int preferredHeight(UiContext ui, int width) {
			return 34 + Math.max(1, top().size()) * 11;
		}

		@Override
		public void render(UiContext ui) {
			ui.roundRect(x, y, width, height, Theme.SURFACE);
			ui.icon(Icon.RESULTS, x + 6, y + 6, 9, Theme.ACCENT);
			ui.text("Module cost (per call)", x + 19, y + 7, Theme.TEXT);
			int colCalls = x + width - 6;
			int colMax = colCalls - 44;
			int colAvg = colMax - 44;
			ui.textRight("avg µs", colAvg, y + 21, Theme.TEXT_MUTED);
			ui.textRight("max µs", colMax, y + 21, Theme.TEXT_MUTED);
			ui.textRight("calls", colCalls, y + 21, Theme.TEXT_MUTED);
			ui.text("module / phase", x + 6, y + 21, Theme.TEXT_MUTED);

			List<PerformanceMonitor.Timing> rows = top();
			if (rows.isEmpty()) {
				ui.text("No module calls measured yet.", x + 6, y + 33, Theme.TEXT_MUTED);
			}

			for (int i = 0; i < rows.size(); i++) {
				PerformanceMonitor.Timing timing = rows.get(i);
				int rowY = y + 33 + i * 11;
				ui.textClipped(timing.key(), x + 6, rowY, colAvg - 40 - x - 6, Theme.TEXT_SECONDARY);
				ui.textRight(String.format("%.1f", timing.averageMicros()), colAvg, rowY, timing.averageMicros() > 1000 ? Theme.WARNING : Theme.TEXT);
				ui.textRight(String.format("%.0f", timing.maxMicros()), colMax, rowY, Theme.TEXT);
				ui.textRight(compact(timing.calls()), colCalls, rowY, Theme.TEXT_SECONDARY);
			}

			if (ui.hovered(x, y, width, height)) {
				ui.tooltip(this, "Time spent inside each module's code, measured around every call. 'avg' is a moving average "
						+ "weighted towards recent calls. 1000 µs = 1 ms; a 60 FPS frame lasts about 16,700 µs.");
			}
		}

		private String compact(long calls) {
			return calls >= 1_000_000 ? calls / 1_000_000 + "M" : calls >= 10_000 ? calls / 1000 + "k" : String.valueOf(calls);
		}
	}

	/** Log title with filter and clear controls. */
	private final class LogHeader extends Container {
		private final Dropdown<LogFilter> filterDropdown;
		private final Button clear;

		LogHeader() {
			filterDropdown = add(new Dropdown<>(() -> Arrays.asList(LogFilter.values()), () -> filter, value -> filter = value, value -> value.label));
			filterDropdown.tooltip("Choose which messages to list.");
			clear = add(new Button("Clear", Button.Style.SECONDARY, () -> {
				nova.diagnostics().clear();
				nova.performance().reset();
			}).icon(Icon.TRASH));
			clear.tooltip("Remove all messages and reset timing statistics. Files and the game log are not affected.");
		}

		@Override
		public int preferredHeight(UiContext ui, int width) {
			return 20;
		}

		@Override
		protected void onLayout() {
			clear.setBounds(x + width - 52, y + 3, 52, 14);
			filterDropdown.setBounds(x + width - 52 - Theme.GAP - 110, y + 3, 110, 14);
		}

		@Override
		public void render(UiContext ui) {
			ui.text("Messages", x + 2, y + 6, Theme.TEXT);
			super.render(ui);
		}
	}

	private static final class LogRow extends Widget {
		private final DiagnosticLogger.Entry entry;

		LogRow(DiagnosticLogger.Entry entry) {
			this.entry = entry;
		}

		private String body() {
			return entry.message() + (entry.detail() == null ? "" : " (" + entry.detail() + ")");
		}

		@Override
		public int preferredHeight(UiContext ui, int width) {
			return 13 + ui.wrappedHeight(body(), width - 14);
		}

		@Override
		public void render(UiContext ui) {
			int color = switch (entry.level()) {
				case ERROR -> Theme.ERROR;
				case WARN -> Theme.WARNING;
				case INFO -> Theme.INFO;
			};

			ui.roundRect(x, y, width, height, Theme.SURFACE);
			ui.rect(x, y + 2, 2, height - 4, color);
			ui.text(TIME.format(entry.time()), x + 7, y + 3, Theme.TEXT_MUTED);
			ui.textClipped(entry.source(), x + 52, y + 3, width - 58, color);
			ui.wrapped(body(), x + 7, y + 13, width - 14, Theme.TEXT_SECONDARY);
		}
	}
}
