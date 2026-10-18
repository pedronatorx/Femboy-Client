package io.github.theamethyx.femboyclient.client.gui.page;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

import net.fabricmc.loader.api.FabricLoader;

import io.github.theamethyx.femboyclient.client.gui.Icon;
import io.github.theamethyx.femboyclient.client.gui.Theme;
import io.github.theamethyx.femboyclient.client.gui.UiContext;
import io.github.theamethyx.femboyclient.client.gui.widget.Button;
import io.github.theamethyx.femboyclient.client.gui.widget.Container;
import io.github.theamethyx.femboyclient.client.gui.widget.Dropdown;
import io.github.theamethyx.femboyclient.client.gui.widget.InfoCard;
import io.github.theamethyx.femboyclient.client.gui.widget.ScrollList;
import io.github.theamethyx.femboyclient.client.gui.widget.Widget;
import io.github.theamethyx.femboyclient.client.hud.Notifications;
import io.github.theamethyx.femboyclient.core.diag.DiagnosticLogger;
import io.github.theamethyx.femboyclient.core.diag.PerformanceMonitor;
import io.github.theamethyx.femboyclient.core.module.Module;
import io.github.theamethyx.femboyclient.core.module.ModuleHealth;

/**
 * The Developer tab: the module health monitor, module dependencies and conflicts, a configuration validator, crash
 * reports, event counters, raw call timings and the diagnostic log. Technical on purpose; the rest of the menu stays free
 * of it.
 */
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

	enum HealthFilter {
		ENABLED("Enabled modules"), PROBLEMS("Problems only"), ALL("All modules");

		final String label;

		HealthFilter(String label) {
			this.label = label;
		}
	}

	private static LogFilter filter = LogFilter.ALL;
	private static HealthFilter healthFilter = HealthFilter.ENABLED;

	private final ScrollList content;
	private int shownLogSize = -1;
	private LogFilter shownFilter;
	private HealthFilter shownHealthFilter;
	private int healthSignature;
	private Map<String, List<String>> validation;
	private String crashText;
	private String crashName;

	public DiagnosticsPage(UiContext ui) {
		super(ui);
		content = add(new ScrollList(ui, Theme.GAP, 0));
	}

	@Override
	public String title() {
		return "Developer";
	}

	@Override
	public String navLabel() {
		return "Developer";
	}

	@Override
	public String subtitle() {
		return "Module health, dependencies, config validation, crash reports, timings and the log.";
	}

	@Override
	public Icon icon() {
		return Icon.CODE;
	}

	@Override
	protected void onLayout() {
		content.setBounds(x, y, width, height);
		rebuild();
	}

	@Override
	public void tick() {
		int size = femboy.diagnostics().snapshot().size();
		int signature = healthSignature();
		if (size != shownLogSize || filter != shownFilter || healthFilter != shownHealthFilter || signature != healthSignature) {
			rebuild();
		}
	}

	/** Changes when the set of listed modules may change (a module turned on or off, or failed). */
	private int healthSignature() {
		int hash = 1;
		for (Module module : femboy.modules().all()) {
			hash = hash * 31 + (module.isEnabled() ? 1 : 0) + (module.isRunning() ? 2 : 0) + module.errorCount() * 4;
		}

		return hash;
	}

	private List<Module> listedModules() {
		List<Module> list = new ArrayList<>();
		for (Module module : femboy.modules().all()) {
			boolean show = switch (healthFilter) {
				case ALL -> true;
				case ENABLED -> module.isEnabled() || module.lastError() != null;
				case PROBLEMS -> {
					ModuleHealth.State state = femboy.modules().health(module).state();
					yield state == ModuleHealth.State.ERROR || state == ModuleHealth.State.SUSPENDED || module.errorCount() > 0
							|| (module.isEnabled() && !module.isRunning());
				}
			};
			if (show) {
				list.add(module);
			}
		}

		return list;
	}

	private void rebuild() {
		List<DiagnosticLogger.Entry> entries = femboy.diagnostics().snapshot();
		shownLogSize = entries.size();
		shownFilter = filter;
		shownHealthFilter = healthFilter;
		healthSignature = healthSignature();

		List<Widget> rows = new ArrayList<>();
		rows.add(new HealthHeader());
		List<Module> listed = listedModules();
		for (Module module : listed) {
			rows.add(new HealthRow(module));
		}

		if (listed.isEmpty()) {
			rows.add(new ModulesPage.Message(healthFilter == HealthFilter.PROBLEMS ? "No module has problems." : "No module is enabled."));
		}

		rows.add(new InfoCard("Dependencies and conflicts", Icon.FLOW, this::dependencyLines));
		rows.add(new ConfigPage.SectionTitle("Configuration validator", "Checks every profile for values this version would replace by "
				+ "defaults, without changing anything."));
		rows.add(new ValidateRow());
		if (validation != null) {
			for (Map.Entry<String, List<String>> entry : validation.entrySet()) {
				rows.add(new ModulesPage.Message(entry.getKey() + ": " + (entry.getValue().isEmpty() ? "no problems." : String.join(" ", entry.getValue()))));
			}
		}

		rows.add(new ConfigPage.SectionTitle("Crash reports", "Minecraft's newest crash reports (crash-reports folder)."));
		rows.add(new CrashRow());
		if (crashText != null) {
			rows.add(new ModulesPage.Message(crashName + ":\n" + crashText));
		}

		rows.add(new InfoCard("Event counters", Icon.BOLT, this::eventLines));
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

	private List<InfoCard.Line> dependencyLines() {
		List<InfoCard.Line> lines = new ArrayList<>();
		for (Module module : femboy.modules().all()) {
			for (Module.Requirement requirement : module.requirements()) {
				Module other = femboy.modules().get(requirement.moduleId());
				String name = other == null ? requirement.moduleId() : other.name();
				boolean on = other != null && other.isEnabled();
				lines.add(new InfoCard.Line(module.name() + " uses " + name, on ? "on" : "off", on ? Theme.SUCCESS : Theme.TEXT_MUTED, requirement.purpose()));
			}
		}

		for (Module module : femboy.modules().all()) {
			for (String id : module.conflicts()) {
				Module other = femboy.modules().get(id);
				if (other != null) {
					lines.add(new InfoCard.Line(module.name() + " ⨯ " + other.name(), "cannot run together", Theme.WARNING,
							"Turning one on turns the other off: both control the same thing."));
				}
			}
		}

		return lines;
	}

	private List<InfoCard.Line> eventLines() {
		List<InfoCard.Line> lines = new ArrayList<>();
		lines.add(new InfoCard.Line("Entity index rebuilds", String.valueOf(io.github.theamethyx.femboyclient.client.world.EntityIndex.builds()), Theme.TEXT,
				"The shared sorted entity list is built at most once per tick, only when a module asks for it."));
		lines.add(new InfoCard.Line("HUD text rebuilds", String.valueOf(femboy.hud().rebuilds()), Theme.TEXT,
				"HUD text blocks are rebuilt once per tick (or less in Performance mode), not every frame."));
		var blockEsp = femboy.modules().get(io.github.theamethyx.femboyclient.client.modules.BlockEspModule.class);
		if (blockEsp != null && blockEsp.isRunning()) {
			lines.add(new InfoCard.Line("Block ESP chunk reads", String.valueOf(blockEsp.scanner().chunkReads())));
			lines.add(new InfoCard.Line("Block ESP block-change updates", String.valueOf(blockEsp.scanner().incrementalUpdates())));
		}

		lines.add(new InfoCard.Line("Toasts held back by the rate limit", String.valueOf(Notifications.dropped()), Theme.TEXT,
				"They are still in the notification list."));
		lines.add(new InfoCard.Line("Measured call types", String.valueOf(femboy.performance().timings().size())));
		return lines;
	}

	private final class HealthHeader extends Container {
		private final Dropdown<HealthFilter> dropdown = add(new Dropdown<>(() -> Arrays.asList(HealthFilter.values()), () -> healthFilter,
				value -> healthFilter = value, value -> value.label));

		@Override
		public int preferredHeight(UiContext ui, int width) {
			return 20;
		}

		@Override
		protected void onLayout() {
			dropdown.setBounds(x + width - 120, y + 3, 120, 14);
		}

		@Override
		public void render(UiContext ui) {
			ui.icon(Icon.HEART, x + 2, y + 5, 9, Theme.ACCENT);
			ui.text("Module health", x + 15, y + 6, Theme.TEXT);
			if (ui.hovered(x, y, width - 125, height)) {
				ui.tooltip(this, "Active: running. Waiting: needs a world. Unsupported here: not allowed in this environment. Reduced: paused by the "
						+ "performance mode. Suspended: stopped for a few seconds after an error, then restarts. Error: turned off after repeated "
						+ "errors. Off: turned off.");
			}

			super.render(ui);
		}
	}

	private static int stateColor(ModuleHealth.State state) {
		return switch (state) {
			case ACTIVE -> Theme.SUCCESS;
			case REDUCED, WAITING -> Theme.INFO;
			case SUSPENDED, UNSUPPORTED -> Theme.WARNING;
			case ERROR -> Theme.ERROR;
			case OFF -> Theme.TEXT_MUTED;
		};
	}

	/** One module: state, reason, last update, errors, cost and work. Values are read every frame (they are cheap). */
	private final class HealthRow extends Widget {
		private final Module module;

		HealthRow(Module module) {
			this.module = module;
		}

		@Override
		public int preferredHeight(UiContext ui, int width) {
			return 24;
		}

		@Override
		public void render(UiContext ui) {
			ModuleHealth health = femboy.modules().health(module);
			int color = stateColor(health.state());
			ui.roundRect(x, y, width, height, Theme.SURFACE);
			ui.rect(x, y + 3, 2, height - 6, color);
			ui.textClipped(module.name(), x + 7, y + 3, 100, Theme.TEXT);
			String state = health.state().label();
			int chipW = ui.textWidth(state) + 8;
			ui.roundRect(x + width - chipW - 4, y + 3, chipW, 10, Theme.withAlpha(color, 0.18f), 4);
			ui.text(state, x + width - chipW, y + 4, color);

			PerformanceMonitor.Timing tick = femboy.performance().timing(module.id() + "/tick");
			List<String> facts = new ArrayList<>();
			if (health.lastSuccessMillis() >= 0 && module.isRunning()) {
				facts.add("updated " + ago(health.lastSuccessMillis()));
			}

			if (tick != null && module.isRunning()) {
				facts.add(String.format(Locale.ROOT, "%.0f µs/tick", tick.averageMicros()));
			}

			if (health.totalErrors() > 0) {
				facts.add(health.totalErrors() + " error(s)" + (health.recentFailures() > 0 ? ", " + health.recentFailures() + " recent" : ""));
			}

			if (health.workload() != null) {
				facts.add(health.workload());
			}

			String detail = health.reason().isEmpty() ? String.join("  ·  ", facts)
					: health.reason() + (facts.isEmpty() ? "" : "  ·  " + String.join("  ·  ", facts));
			if (detail.isEmpty()) {
				detail = health.state().meaning();
			}

			ui.textScaled(ui.ellipsize(detail, Math.round((width - 14) / 0.75f)), x + 7, y + 14, 0.75f, Theme.TEXT_SECONDARY);
			if (ui.hovered(x, y, width, height)) {
				ui.tooltip(this, module.name() + ": " + health.state().meaning() + (detail.isEmpty() ? "" : "\n" + detail)
						+ (health.lastError() == null ? "" : "\nLast error: " + health.lastError()));
			}
		}

		private static String ago(long millis) {
			return millis < 1000 ? "just now" : millis < 60_000 ? millis / 1000 + " s ago" : millis / 60_000 + " min ago";
		}
	}

	private final class ValidateRow extends Container {
		private final Button button = add(new Button("Check all profiles", Button.Style.SECONDARY, () -> {
			validation = femboy.config().validateProfiles();
			rebuild();
		}).icon(Icon.CHECK));

		@Override
		public int preferredHeight(UiContext ui, int width) {
			return 16;
		}

		@Override
		protected void onLayout() {
			button.setBounds(x, y, width, 16);
		}
	}

	private static Path crashDir() {
		return FabricLoader.getInstance().getGameDir().resolve("crash-reports");
	}

	private static List<Path> crashReports() {
		if (!Files.isDirectory(crashDir())) {
			return List.of();
		}

		try (Stream<Path> files = Files.list(crashDir())) {
			return files.filter(path -> path.getFileName().toString().endsWith(".txt"))
					.sorted((a, b) -> b.getFileName().toString().compareTo(a.getFileName().toString())).limit(5).toList();
		} catch (IOException e) {
			return List.of();
		}
	}

	private final class CrashRow extends Container {
		private final List<Path> reports = crashReports();

		CrashRow() {
			for (Path report : reports) {
				add(new Button(report.getFileName().toString().replace("crash-", "").replace(".txt", ""), Button.Style.SECONDARY, () -> show(report))
						.icon(Icon.WARNING));
			}
		}

		private void show(Path report) {
			try (Stream<String> lines = Files.lines(report)) {
				crashName = report.getFileName().toString();
				crashText = String.join("\n", lines.limit(30).toList());
			} catch (IOException | java.io.UncheckedIOException e) {
				crashName = report.getFileName().toString();
				crashText = "Could not read it: " + e.getMessage();
			}

			rebuild();
		}

		@Override
		public int preferredHeight(UiContext ui, int width) {
			return reports.isEmpty() ? 12 : reports.size() * 18;
		}

		@Override
		protected void onLayout() {
			for (int i = 0; i < children.size(); i++) {
				children.get(i).setBounds(x, y + i * 18, width, 16);
			}
		}

		@Override
		public void render(UiContext ui) {
			if (reports.isEmpty()) {
				ui.text("No crash reports. Good.", x + 2, y + 2, Theme.TEXT_MUTED);
			}

			super.render(ui);
		}
	}

	/** The most expensive measured calls. */
	private final class TimingTable extends Widget {
		private static final int MAX_ROWS = 8;

		private List<PerformanceMonitor.Timing> top() {
			List<PerformanceMonitor.Timing> all = femboy.performance().timings();
			return all.subList(0, Math.min(MAX_ROWS, all.size()));
		}

		@Override
		public int preferredHeight(UiContext ui, int width) {
			return 34 + MAX_ROWS * 11;
		}

		@Override
		public void render(UiContext ui) {
			ui.roundRect(x, y, width, height, Theme.SURFACE);
			ui.icon(Icon.RESULTS, x + 6, y + 6, 9, Theme.ACCENT);
			ui.text("Raw call timings (per call)", x + 19, y + 7, Theme.TEXT);
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
				ui.textRight(String.format(Locale.ROOT, "%.1f", timing.averageMicros()), colAvg, rowY, timing.averageMicros() > 1000 ? Theme.WARNING : Theme.TEXT);
				ui.textRight(String.format(Locale.ROOT, "%.0f", timing.maxMicros()), colMax, rowY, Theme.TEXT);
				ui.textRight(compact(timing.calls()), colCalls, rowY, Theme.TEXT_SECONDARY);
			}

			if (ui.hovered(x, y, width, height)) {
				ui.tooltip(this, "Time spent inside each module's code, measured around every call, and the client's dispatch (client/...). 'avg' "
						+ "is a moving average weighted towards recent calls. 1000 µs = 1 ms.");
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
				femboy.diagnostics().clear();
				femboy.performance().reset();
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
