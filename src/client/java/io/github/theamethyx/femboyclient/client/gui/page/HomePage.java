package io.github.theamethyx.femboyclient.client.gui.page;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

import io.github.theamethyx.femboyclient.client.ClientKeys;
import io.github.theamethyx.femboyclient.client.gui.Icon;
import io.github.theamethyx.femboyclient.client.gui.Theme;
import io.github.theamethyx.femboyclient.client.gui.UiContext;
import io.github.theamethyx.femboyclient.client.gui.widget.Dropdown;
import io.github.theamethyx.femboyclient.client.gui.widget.InfoCard;
import io.github.theamethyx.femboyclient.client.gui.widget.ModuleRow;
import io.github.theamethyx.femboyclient.client.gui.widget.ScrollList;
import io.github.theamethyx.femboyclient.client.gui.widget.TextField;
import io.github.theamethyx.femboyclient.client.gui.widget.Widget;
import io.github.theamethyx.femboyclient.core.diag.PerformanceMonitor;
import io.github.theamethyx.femboyclient.core.module.Module;
import io.github.theamethyx.femboyclient.core.module.ModuleCategory;

/**
 * Start page: every module in one searchable list (filterable by category; click a module to open it on
 * its category page) next to a short overview of the session.
 */
public class HomePage extends Page {
	private final Consumer<Module> openModule;
	private final TextField search;
	private final Dropdown<ModulesPage.CategoryChoice> filter;
	private final ScrollList list;
	private final ScrollList overview;
	private ModuleCategory category;
	private String query = "";

	public HomePage(UiContext ui, Consumer<Module> openModule) {
		super(ui);
		this.openModule = openModule;
		search = add(new TextField("Search all modules", 40).icon(Icon.SEARCH).onChange(text -> {
			query = text;
			rebuildList();
		}));
		search.tooltip("Type to find a module by name, description or setting name.");

		List<ModulesPage.CategoryChoice> choices = new ArrayList<>();
		choices.add(new ModulesPage.CategoryChoice(null));
		for (ModuleCategory c : ModuleCategory.values()) {
			choices.add(new ModulesPage.CategoryChoice(c));
		}

		filter = add(new Dropdown<>(() -> choices, () -> new ModulesPage.CategoryChoice(category), choice -> {
			category = choice.category();
			rebuildList();
		}, ModulesPage.CategoryChoice::label));
		filter.tooltip("Show only one category.");
		list = add(new ScrollList(ui, 3, 0));
		overview = add(new ScrollList(ui, Theme.GAP, 0));
	}

	@Override
	public String title() {
		return "Home";
	}

	@Override
	public String subtitle() {
		return "All modules at a glance. Click a module to open its settings.";
	}

	@Override
	public Icon icon() {
		return Icon.HOME;
	}

	@Override
	public void onShow() {
		search.setText(query);
		rebuildList();
		rebuildOverview();
	}

	@Override
	protected void onLayout() {
		int listWidth = Math.max(140, Math.min(200, (width - Theme.PAD) / 2));
		int filterWidth = Math.min(80, listWidth / 2 - Theme.GAP);
		search.setBounds(x, y, listWidth - filterWidth - Theme.GAP, 15);
		filter.setBounds(x + listWidth - filterWidth, y, filterWidth, 15);
		list.setBounds(x, y + 15 + Theme.GAP, listWidth, height - 15 - Theme.GAP);
		overview.setBounds(x + listWidth + Theme.PAD, y, width - listWidth - Theme.PAD, height);
		rebuildList();
		rebuildOverview();
	}

	private void rebuildList() {
		List<Widget> rows = new ArrayList<>();
		for (Module module : femboy.modules().search(query, category)) {
			rows.add(new ModuleRow(module, femboy.modules(), () -> null, openModule));
		}

		if (rows.isEmpty()) {
			rows.add(new ModulesPage.Message("No modules match \"" + query + "\"."));
		}

		list.setChildren(rows);
	}

	private void rebuildOverview() {
		List<Widget> cards = new ArrayList<>();
		cards.add(new InfoCard("Session", Icon.INFO, this::sessionLines));
		cards.add(new InfoCard("Modules", Icon.VISUAL, this::moduleLines));
		cards.add(new InfoCard("Performance", Icon.PERFORMANCE, this::performanceLines));
		overview.setChildren(cards);
	}

	private List<InfoCard.Line> sessionLines() {
		boolean authorized = femboy.modules().environment().isAuthorizedEnvironment();
		List<InfoCard.Line> lines = new ArrayList<>();
		lines.add(new InfoCard.Line("Where", femboy.modules().environment().describe(), authorized ? Theme.SUCCESS : Theme.TEXT_SECONDARY,
				authorized ? "All modules may run here." : "Entity ESP, Storage ESP, the Geode Analyzer and Free Camera pause here. "
						+ "They run in singleplayer and on servers you authorize under Settings."));
		lines.add(new InfoCard.Line("Profile", femboy.config().activeProfile(), Theme.ACCENT_STRONG));
		lines.add(new InfoCard.Line("Menu key", ClientKeys.openMenu.getTranslatedKeyMessage().getString(), Theme.TEXT,
				"Change it in Minecraft's Options > Controls > Key Binds under 'Femboy Client'."));
		return lines;
	}

	private List<InfoCard.Line> moduleLines() {
		int enabled = 0;
		int running = 0;
		int failed = 0;
		for (Module module : femboy.modules().all()) {
			if (module.isEnabled()) enabled++;
			if (module.isRunning()) running++;
			if (module.lastError() != null) failed++;
		}

		List<InfoCard.Line> lines = new ArrayList<>();
		lines.add(new InfoCard.Line("Enabled", enabled + " of " + femboy.modules().all().size()));
		lines.add(new InfoCard.Line("Running now", String.valueOf(running), running < enabled ? Theme.WARNING : Theme.TEXT,
				running < enabled ? "Some enabled modules are paused because they are not allowed here or no world is loaded." : null));
		lines.add(new InfoCard.Line("Errors", failed == 0 ? "None" : failed + " module(s) stopped", failed == 0 ? Theme.SUCCESS : Theme.ERROR,
				failed == 0 ? null : "See Settings > Diagnostics for details."));
		return lines;
	}

	private List<InfoCard.Line> performanceLines() {
		PerformanceMonitor.FrameStats stats = femboy.performance().frameStats();
		List<InfoCard.Line> lines = new ArrayList<>();
		lines.add(new InfoCard.Line("Frame rate", stats.samples() == 0 ? "-" : String.format(Locale.ROOT, "%.0f FPS", 1000 / Math.max(0.001, stats.averageFrameMs()))));
		lines.add(new InfoCard.Line("Frame time (avg / worst)", stats.samples() == 0 ? "-"
				: String.format(Locale.ROOT, "%.1f / %.1f ms", stats.averageFrameMs(), stats.worstFrameMs())));
		return lines;
	}
}
