package io.github.theamethyx.femboyclient.client.gui.page;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import net.minecraft.client.Minecraft;

import io.github.theamethyx.femboyclient.client.GlobalOptions;
import io.github.theamethyx.femboyclient.client.Performance;
import io.github.theamethyx.femboyclient.client.diag.DiagnosticReport;
import io.github.theamethyx.femboyclient.client.diag.ModuleProfiler;
import io.github.theamethyx.femboyclient.client.gui.Icon;
import io.github.theamethyx.femboyclient.client.gui.Theme;
import io.github.theamethyx.femboyclient.client.gui.UiContext;
import io.github.theamethyx.femboyclient.client.gui.widget.Button;
import io.github.theamethyx.femboyclient.client.gui.widget.Container;
import io.github.theamethyx.femboyclient.client.gui.widget.Dropdown;
import io.github.theamethyx.femboyclient.client.gui.widget.FrameGraph;
import io.github.theamethyx.femboyclient.client.gui.widget.InfoCard;
import io.github.theamethyx.femboyclient.client.gui.widget.ScrollList;
import io.github.theamethyx.femboyclient.client.gui.widget.Widget;
import io.github.theamethyx.femboyclient.core.diag.PerformanceMonitor;
import io.github.theamethyx.femboyclient.core.module.Module;
import io.github.theamethyx.femboyclient.core.module.ModuleManager;

/**
 * The Performance Center: what the client itself costs. Frame rate and frame times, memory, the client's share of each
 * frame, the cost of every module, what the busy modules are working on, warnings and recommendations based on the
 * measurements, an on/off comparison for a single module, and a report to export. Numbers come from the timers that wrap
 * every call into module code; nothing extra is measured while this page is closed, except allocations if turned on here.
 */
public class PerformanceCenterPage extends Page {
	/** A module whose average tick or drawing exceeds this is flagged. */
	static final double HEAVY_MICROS = 1000;

	private final ScrollList content;
	private List<ModuleCost> costs = List.of();
	private int refresh;
	private Module profiled;
	private int profileSeconds = 5;
	private String feedback;

	public PerformanceCenterPage(UiContext ui) {
		super(ui);
		content = add(new ScrollList(ui, Theme.GAP, 0));
	}

	@Override
	public String title() {
		return "Settings";
	}

	@Override
	public String navLabel() {
		return "Performance";
	}

	@Override
	public String subtitle() {
		return "Performance Center: frame times, memory, module cost, warnings and on/off comparisons.";
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
		// The numbers are read once a second, not every frame.
		if (++refresh >= 20) {
			refresh = 0;
			costs = computeCosts();
		}
	}

	@Override
	public void onShow() {
		costs = computeCosts();
	}

	/** Cost of one module: average microseconds per call in each phase. */
	record ModuleCost(Module module, double tick, double hud, double world) {
		double total() {
			return tick + hud + world;
		}
	}

	private List<ModuleCost> computeCosts() {
		PerformanceMonitor performance = femboy.performance();
		List<ModuleCost> list = new ArrayList<>();
		for (Module module : femboy.modules().all()) {
			if (!module.isRunning()) {
				continue;
			}

			double tick = average(performance, module.id() + "/" + ModuleManager.Phase.TICK.name().toLowerCase(Locale.ROOT));
			double hud = average(performance, module.id() + "/" + ModuleManager.Phase.RENDER_HUD.name().toLowerCase(Locale.ROOT));
			double world = average(performance, module.id() + "/" + ModuleManager.Phase.RENDER_WORLD.name().toLowerCase(Locale.ROOT));
			list.add(new ModuleCost(module, tick, hud, world));
		}

		list.sort(Comparator.comparingDouble(ModuleCost::total).reversed());
		return list;
	}

	private static double average(PerformanceMonitor performance, String key) {
		PerformanceMonitor.Timing timing = performance.timing(key);
		return timing == null ? 0 : timing.averageMicros();
	}

	private void rebuild() {
		List<Widget> rows = new ArrayList<>();
		rows.add(new InfoCard("Right now", Icon.PERFORMANCE, this::summaryLines));
		rows.add(new FrameGraph(femboy.performance()));
		rows.add(new InfoCard("Warnings and recommendations", Icon.WARNING, this::adviceLines));
		rows.add(new CostTable());
		rows.add(new InfoCard("Busy modules: current work", Icon.TARGET, this::workloadLines));
		rows.add(new InfoCard("Update rates in " + Performance.mode().label() + " mode", Icon.BOLT, this::rateLines));
		rows.add(new ConfigPage.SectionTitle("Compare a module on and off", "Measures the frame time and the client's own cost for a few "
				+ "seconds in the module's current state, switches it, measures again and switches it back. Stand still and keep the view "
				+ "steady. Only visual and assist modules can be compared."));
		rows.add(new ProfilerRow());
		rows.add(new InfoCard("Comparisons this session", Icon.RESULTS, this::profilerLines));
		rows.add(new ConfigPage.SectionTitle("Tools", "Allocation measurement adds a counter read to every measured call, so it is off "
				+ "unless you need it. The report contains no server address, chat or player names."));
		rows.add(new ToolsRow());
		content.setChildren(rows);
	}

	private List<InfoCard.Line> summaryLines() {
		PerformanceMonitor.FrameStats stats = femboy.performance().frameStats();
		List<InfoCard.Line> lines = new ArrayList<>();
		lines.add(new InfoCard.Line("FPS", String.valueOf(Minecraft.getInstance().getFps())));
		if (stats.samples() > 0) {
			lines.add(new InfoCard.Line("Frame time", String.format(Locale.ROOT, "%.2f ms average, %.2f ms slowest", stats.averageFrameMs(), stats.worstFrameMs())));
			lines.add(new InfoCard.Line("1% low", String.format(Locale.ROOT, "%.0f FPS", stats.onePercentLowFps()), Theme.TEXT,
					"Frame rate of the slowest 1% of the last " + stats.samples() + " frames; shows stutter."));
			double client = clientMicrosPerFrame();
			double share = client / 10 / Math.max(0.01, stats.averageFrameMs());
			lines.add(new InfoCard.Line("Femboy Client per frame", String.format(Locale.ROOT, "%.0f µs (%.1f%% of the frame)", client, share),
					share > 20 ? Theme.WARNING : Theme.TEXT, "HUD and world drawing of all modules, plus their tick work spread over the frames."));
		} else {
			lines.add(new InfoCard.Line("Frame time", "load a world to measure", Theme.TEXT_MUTED));
		}

		Runtime runtime = Runtime.getRuntime();
		long used = runtime.totalMemory() - runtime.freeMemory();
		double fraction = (double) used / runtime.maxMemory();
		lines.add(new InfoCard.Line("Memory", String.format(Locale.ROOT, "%d / %d MB (%.0f%%)", used >> 20, runtime.maxMemory() >> 20, fraction * 100),
				fraction > 0.85 ? Theme.WARNING : Theme.TEXT, "Java heap of the whole game; it rises and falls as the garbage collector runs."));
		lines.add(new InfoCard.Line("Mode", Performance.mode().label(), Theme.ACCENT_STRONG));
		lines.add(new InfoCard.Line("Running modules", String.valueOf(costs.size())));
		return lines;
	}

	/** Client drawing per frame plus tick work spread over frames (20 ticks per second at the current FPS). */
	private double clientMicrosPerFrame() {
		PerformanceMonitor performance = femboy.performance();
		double perFrame = average(performance, "client/hud") + average(performance, "client/world");
		double fps = Math.max(1, Minecraft.getInstance().getFps());
		return perFrame + average(performance, "client/tick") * Math.min(1, 20 / fps);
	}

	private List<InfoCard.Line> adviceLines() {
		List<InfoCard.Line> lines = new ArrayList<>();
		PerformanceMonitor.FrameStats stats = femboy.performance().frameStats();
		for (ModuleCost cost : costs) {
			if (cost.tick() > HEAVY_MICROS || cost.hud() > HEAVY_MICROS || cost.world() > HEAVY_MICROS) {
				lines.add(new InfoCard.Line(cost.module().name(), String.format(Locale.ROOT, "%.1f ms per call", cost.total() / 1000), Theme.WARNING,
						"Lower its range, target limit or update rate in its Performance section, or turn it off when not needed."));
			}
		}

		if (stats.samples() == 0) {
			lines.add(new InfoCard.Line("No frames measured yet", "load a world", Theme.TEXT_MUTED));
			return lines;
		}

		double share = clientMicrosPerFrame() / 10 / Math.max(0.01, stats.averageFrameMs());
		boolean slow = stats.averageFrameMs() > 16.7;
		if (slow && share > 15 && Performance.mode() != GlobalOptions.PerformanceMode.PERFORMANCE) {
			lines.add(new InfoCard.Line("Try Performance mode", String.format(Locale.ROOT, "client uses %.0f%% of a slow frame", share), Theme.WARNING,
					"Performance mode updates less often, draws fewer targets and turns menu animations off. Settings → General → Performance mode."));
		} else if (slow) {
			lines.add(new InfoCard.Line("The game itself is the bottleneck", String.format(Locale.ROOT, "client: %.1f%% of the frame", share), Theme.TEXT,
					"Femboy Client takes a small part of each frame; Minecraft's video settings (render distance, graphics) matter more."));
		}

		if (stats.onePercentLowFps() > 0 && stats.onePercentLowFps() < 0.5 * (1000 / Math.max(0.01, stats.averageFrameMs()))) {
			lines.add(new InfoCard.Line("Stutter", String.format(Locale.ROOT, "1%% low %.0f FPS", stats.onePercentLowFps()), Theme.WARNING,
					"Some frames take more than twice the average. Chunk loading and garbage collection cause this too, not only modules."));
		}

		Runtime runtime = Runtime.getRuntime();
		if ((double) (runtime.totalMemory() - runtime.freeMemory()) / runtime.maxMemory() > 0.85) {
			lines.add(new InfoCard.Line("Memory is nearly full", "consider more RAM for the game", Theme.WARNING,
					"Above 85% of the heap the garbage collector runs often, which causes stutter."));
		}

		if (lines.isEmpty()) {
			lines.add(new InfoCard.Line("No problems found", "", Theme.SUCCESS));
		}

		return lines;
	}

	private List<InfoCard.Line> workloadLines() {
		List<InfoCard.Line> lines = new ArrayList<>();
		for (ModuleCost cost : costs) {
			String workload = femboy.modules().health(cost.module()).workload();
			if (workload != null) {
				lines.add(new InfoCard.Line(cost.module().name(), workload));
			}
		}

		if (femboy.config().isDirty()) {
			lines.add(new InfoCard.Line("Configuration", "changes waiting to be saved (within 2 s)", Theme.TEXT_SECONDARY));
		}

		if (femboy.profiler().running()) {
			lines.add(new InfoCard.Line("Comparison", femboy.profiler().status(), Theme.ACCENT_STRONG));
		}

		if (lines.isEmpty()) {
			lines.add(new InfoCard.Line("Nothing busy", "turn on ESP or world modules to see their work here", Theme.TEXT_MUTED));
		}

		return lines;
	}

	private List<InfoCard.Line> rateLines() {
		List<InfoCard.Line> lines = new ArrayList<>();
		for (String part : Performance.summary().split(";\\s*|\\.\\s+")) {
			if (!part.isBlank()) {
				lines.add(new InfoCard.Line("", part.strip()));
			}
		}

		return lines;
	}

	private List<InfoCard.Line> profilerLines() {
		List<InfoCard.Line> lines = new ArrayList<>();
		for (ModuleProfiler.Result result : femboy.profiler().results()) {
			int color = result.frameDeltaMs() > 1 ? Theme.WARNING : Theme.TEXT;
			lines.add(new InfoCard.Line(result.module(), String.format(Locale.ROOT, "frame %+.2f ms (%.2f on, %.2f off), client %+.0f µs", result.frameDeltaMs(),
					result.frameMsOn(), result.frameMsOff(), result.clientDeltaUs()), color,
					String.format(Locale.ROOT, "%d s per half; %d frames on, %d off. Differences under about 0.2 ms are within normal variation.",
							result.seconds(), result.framesOn(), result.framesOff())));
		}

		if (lines.isEmpty()) {
			lines.add(new InfoCard.Line("None yet", "choose a module above and press Compare", Theme.TEXT_MUTED));
		}

		return lines;
	}

	/** Modules that can be compared: visual and assist ones. */
	private List<Module> profilable() {
		List<Module> list = new ArrayList<>();
		for (Module module : femboy.modules().all()) {
			if (module.tier() == Module.Tier.VISUAL || module.tier() == Module.Tier.ASSIST) {
				list.add(module);
			}
		}

		return list;
	}

	/** Running modules by cost: average microseconds per tick, HUD frame and world frame. */
	private final class CostTable extends Widget {
		private static final int MAX_ROWS = 12;

		@Override
		public int preferredHeight(UiContext ui, int width) {
			return 34 + Math.max(1, Math.min(MAX_ROWS, costs.size())) * 11;
		}

		@Override
		public void render(UiContext ui) {
			ui.roundRect(x, y, width, height, Theme.SURFACE);
			ui.icon(Icon.RESULTS, x + 6, y + 6, 9, Theme.ACCENT);
			ui.text("Module cost (µs per call)", x + 19, y + 7, Theme.TEXT);
			int colWorld = x + width - 6;
			int colHud = colWorld - 46;
			int colTick = colHud - 46;
			ui.textRight("tick", colTick, y + 21, Theme.TEXT_MUTED);
			ui.textRight("HUD", colHud, y + 21, Theme.TEXT_MUTED);
			ui.textRight("world", colWorld, y + 21, Theme.TEXT_MUTED);
			if (costs.isEmpty()) {
				ui.text("No module is running.", x + 6, y + 33, Theme.TEXT_MUTED);
			}

			int barMax = Math.max(10, colTick - 50 - (x + 100));
			double worst = costs.isEmpty() ? 1 : Math.max(1, costs.getFirst().total());
			for (int i = 0; i < Math.min(MAX_ROWS, costs.size()); i++) {
				ModuleCost cost = costs.get(i);
				int rowY = y + 33 + i * 11;
				ui.textClipped(cost.module().name(), x + 6, rowY, 90, Theme.TEXT_SECONDARY);
				int bar = (int) Math.round(barMax * cost.total() / worst);
				ui.roundRect(x + 100, rowY + 2, Math.max(1, bar), 5, cost.total() > HEAVY_MICROS ? Theme.WARNING : Theme.ACCENT_DIM, 2);
				cell(ui, cost.tick(), colTick, rowY);
				cell(ui, cost.hud(), colHud, rowY);
				cell(ui, cost.world(), colWorld, rowY);
			}

			if (ui.hovered(x, y, width, height)) {
				ui.tooltip(this, "Average time inside each running module's code per call, weighted towards recent calls. Ticks run 20 times "
						+ "a second; HUD and world drawing once per frame. 1000 µs = 1 ms; a 60 FPS frame lasts about 16,700 µs.");
			}
		}

		private void cell(UiContext ui, double micros, int right, int rowY) {
			ui.textRight(micros == 0 ? "-" : String.format(Locale.ROOT, micros >= 100 ? "%.0f" : "%.1f", micros), right, rowY,
					micros > HEAVY_MICROS ? Theme.WARNING : Theme.TEXT);
		}
	}

	private final class ProfilerRow extends Container {
		private final Dropdown<Module> module;
		private final Dropdown<Integer> seconds;
		private final Button start;

		ProfilerRow() {
			module = add(new Dropdown<>(PerformanceCenterPage.this::profilable, () -> profiled, value -> profiled = value,
					value -> value == null ? "Choose a module" : value.name()));
			seconds = add(new Dropdown<>(() -> List.of(3, 5, 10, 20), () -> profileSeconds, value -> profileSeconds = value, value -> value + " s each"));
			start = add(new Button(() -> femboy.profiler().running() ? "Stop" : "Compare", Button.Style.PRIMARY, () -> {
				if (femboy.profiler().running()) {
					femboy.profiler().cancel("Stopped by you.");
				} else if (profiled != null) {
					feedback = femboy.profiler().start(profiled, profileSeconds);
				}
			}).icon(Icon.PLAY));
			start.enabledWhen(() -> profiled != null || femboy.profiler().running());
		}

		@Override
		public int preferredHeight(UiContext ui, int width) {
			return 30;
		}

		@Override
		protected void onLayout() {
			int buttonW = 70;
			int secondsW = 70;
			module.setBounds(x, y, width - buttonW - secondsW - 2 * Theme.GAP, 14);
			seconds.setBounds(x + width - buttonW - secondsW - Theme.GAP, y, secondsW, 14);
			start.setBounds(x + width - buttonW, y, buttonW, 14);
		}

		@Override
		public void render(UiContext ui) {
			super.render(ui);
			ModuleProfiler profiler = femboy.profiler();
			if (profiler.running()) {
				ui.roundRect(x, y + 19, width, 4, Theme.SURFACE_ACTIVE, 2);
				ui.roundRect(x, y + 19, Math.max(4, Math.round(width * profiler.progress())), 4, Theme.ACCENT, 2);
				ui.textScaled(profiler.status(), x, y + 24.5f, 0.75f, Theme.TEXT_SECONDARY);
			} else {
				String text = feedback != null ? feedback : profiler.status();
				if (text != null) {
					ui.textScaled(ui.ellipsize(text, Math.round(width / 0.75f)), x, y + 19, 0.75f, Theme.WARNING);
				}
			}
		}
	}

	private final class ToolsRow extends Container {
		private final Button allocations;
		private final Button report;
		private String reportStatus;

		ToolsRow() {
			allocations = add(new Button(() -> femboy.performance().measuresAllocations() ? "Allocations: measured" : "Measure allocations",
					Button.Style.SECONDARY, () -> femboy.performance().setMeasureAllocations(!femboy.performance().measuresAllocations())).icon(Icon.SLIDERS));
			allocations.tooltip("Also count memory allocated by each measured call (shown in the exported report and benchmark).");
			report = add(new Button("Export report", Button.Style.SECONDARY, () -> {
				reportStatus = "Writing...";
				DiagnosticReport.export(result -> reportStatus = result);
			}).icon(Icon.SAVE));
			report.tooltip("Write a diagnostic report (versions, frame statistics, module costs and health, recent log) to the reports folder.");
		}

		@Override
		public int preferredHeight(UiContext ui, int width) {
			return 28;
		}

		@Override
		protected void onLayout() {
			int half = (width - Theme.GAP) / 2;
			allocations.setBounds(x, y, half, 16);
			report.setBounds(x + half + Theme.GAP, y, width - half - Theme.GAP, 16);
		}

		@Override
		public void render(UiContext ui) {
			super.render(ui);
			if (reportStatus != null) {
				ui.textClipped(reportStatus, x, y + 19, width, Theme.TEXT_SECONDARY);
			}
		}
	}

	/** Totals used by tests: costs grouped by module id. */
	Map<String, Double> costsById() {
		Map<String, Double> map = new LinkedHashMap<>();
		costs.forEach(cost -> map.put(cost.module().id(), cost.total()));
		return map;
	}
}
