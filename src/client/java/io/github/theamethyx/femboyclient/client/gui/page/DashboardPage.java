package io.github.theamethyx.femboyclient.client.gui.page;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;
import java.util.function.Supplier;

import net.minecraft.client.Minecraft;

import io.github.theamethyx.femboyclient.client.gui.Icon;
import io.github.theamethyx.femboyclient.client.gui.Theme;
import io.github.theamethyx.femboyclient.client.gui.UiContext;
import io.github.theamethyx.femboyclient.client.gui.widget.Button;
import io.github.theamethyx.femboyclient.client.gui.widget.Container;
import io.github.theamethyx.femboyclient.client.gui.widget.InfoCard;
import io.github.theamethyx.femboyclient.client.gui.widget.ScrollList;
import io.github.theamethyx.femboyclient.client.gui.widget.Widget;
import io.github.theamethyx.femboyclient.core.diag.PerformanceMonitor;
import io.github.theamethyx.femboyclient.core.module.Module;

/** Overview: where the client is, what is running, health, and shortcuts to the other pages. */
public class DashboardPage extends Page {
	private final ScrollList content;
	private final Supplier<List<String>> pageNames;
	private final IntConsumer openPage;

	public DashboardPage(UiContext ui, Supplier<List<String>> pageNames, IntConsumer openPage) {
		super(ui);
		this.pageNames = pageNames;
		this.openPage = openPage;
		content = add(new ScrollList(ui, Theme.GAP, 0));
	}

	@Override
	public String title() {
		return "Dashboard";
	}

	@Override
	public String subtitle() {
		return "Status of the client, the current world and running modules.";
	}

	@Override
	public Icon icon() {
		return Icon.DASHBOARD;
	}

	@Override
	protected void onLayout() {
		content.setBounds(x, y, width, height);
		List<Widget> cards = new ArrayList<>();
		cards.add(new InfoCard("Session", Icon.INFO, this::sessionLines));
		cards.add(new InfoCard("Modules", Icon.VISUAL, this::moduleLines));
		cards.add(new InfoCard("Health", Icon.PERFORMANCE, this::healthLines));
		cards.add(new InfoCard("Testing", Icon.SCENARIOS, this::testingLines));
		cards.add(new Shortcuts());
		content.setChildren(cards);
	}

	private List<InfoCard.Line> sessionLines() {
		boolean authorized = femboy.modules().environment().isAuthorizedEnvironment();
		return List.of(
				new InfoCard.Line("Environment", femboy.modules().environment().describe(), authorized ? Theme.SUCCESS : Theme.WARNING,
						"Restricted modules (world analysis, fixture highlighting, free camera) run only in singleplayer "
								+ "or on servers added under Configuration > Authorized test servers."),
				new InfoCard.Line("Profile", femboy.config().activeProfile(), Theme.ACCENT_STRONG),
				new InfoCard.Line("Menu key", openKeyName(), Theme.TEXT,
						"Change it in Minecraft's Options > Controls > Key Binds under 'Femboy Client'."));
	}

	private static String openKeyName() {
		return io.github.theamethyx.femboyclient.client.ClientKeys.openMenu.getTranslatedKeyMessage().getString();
	}

	private List<InfoCard.Line> moduleLines() {
		int total = femboy.modules().all().size();
		int enabled = 0;
		int running = 0;
		int errors = 0;
		for (Module module : femboy.modules().all()) {
			if (module.isEnabled()) enabled++;
			if (module.isRunning()) running++;
			if (module.lastError() != null) errors++;
		}

		return List.of(
				new InfoCard.Line("Registered", String.valueOf(total)),
				new InfoCard.Line("Enabled", String.valueOf(enabled)),
				new InfoCard.Line("Running now", String.valueOf(running), running > 0 ? Theme.SUCCESS : Theme.TEXT_SECONDARY,
						"Enabled modules pause automatically when no world is loaded or the environment is not authorized."),
				new InfoCard.Line("With errors", String.valueOf(errors), errors > 0 ? Theme.ERROR : Theme.TEXT_SECONDARY,
						"A module that throws an error is turned off and reported under Diagnostics; other modules keep running."));
	}

	private List<InfoCard.Line> healthLines() {
		PerformanceMonitor.FrameStats stats = femboy.performance().frameStats();
		int fps = Minecraft.getInstance().getFps();
		int warnings = femboy.diagnostics().warningCount();
		int errors = femboy.diagnostics().errorCount();
		return List.of(
				new InfoCard.Line("FPS", String.valueOf(fps)),
				new InfoCard.Line("Average frame", stats.samples() == 0 ? "No samples yet" : String.format("%.1f ms", stats.averageFrameMs()), Theme.TEXT,
						"Measured over the last " + PerformanceMonitor.FRAME_HISTORY + " rendered world frames."),
				new InfoCard.Line("Warnings / errors", warnings + " / " + errors, errors > 0 ? Theme.ERROR : warnings > 0 ? Theme.WARNING : Theme.SUCCESS),
				new InfoCard.Line("Unsaved changes", femboy.config().isDirty() ? "Saving shortly" : "All saved",
						femboy.config().isDirty() ? Theme.WARNING : Theme.TEXT_SECONDARY));
	}

	private List<InfoCard.Line> testingLines() {
		long built = femboy.scenarios().scenarios().stream()
				.filter(s -> femboy.scenarios().run(s.id()).state() == io.github.theamethyx.femboyclient.client.scenario.TestScenarioManager.State.BUILT).count();
		var evaluations = femboy.results().evaluations();
		String latest = "None yet";
		int latestColor = Theme.TEXT_SECONDARY;
		if (!evaluations.isEmpty()) {
			var summary = evaluations.getFirst().summary();
			StringBuilder verdicts = new StringBuilder();
			for (var h : summary.getAsJsonArray("hypotheses")) {
				var o = h.getAsJsonObject();
				if (!verdicts.isEmpty()) verdicts.append(", ");
				verdicts.append(o.get("id").getAsString()).append(' ').append(o.get("verdict").getAsString().charAt(0))
						.append(o.get("verdict").getAsString().substring(1).toLowerCase(java.util.Locale.ROOT));
			}

			latest = verdicts.toString();
			latestColor = Theme.TEXT;
		}

		return List.of(
				new InfoCard.Line("Scenarios built in this world", built + " of " + femboy.scenarios().scenarios().size(), Theme.TEXT,
						"Test scenarios currently standing in this world (Test Scenarios page)."),
				new InfoCard.Line("Saved observations", String.valueOf(femboy.results().all().size())),
				new InfoCard.Line("Evaluations", String.valueOf(evaluations.size())),
				new InfoCard.Line("Latest verdicts", latest, latestColor,
						"Hypothesis verdicts of the most recent evaluation, judged on held-out geodes with criteria fixed in advance. "
								+ "'Supported' applies only to the tested environment."));
	}

	/** Buttons that jump to the other pages. */
	private final class Shortcuts extends Container {
		private final List<Button> buttons = new ArrayList<>();

		Shortcuts() {
			List<String> names = pageNames.get();
			for (int i = 1; i < names.size(); i++) {
				int index = i;
				buttons.add(add(new Button(names.get(i), Button.Style.SECONDARY, () -> openPage.accept(index))));
			}
		}

		private int columns() {
			return width >= 260 ? 3 : 2;
		}

		@Override
		public int preferredHeight(UiContext ui, int width) {
			int columns = width >= 260 ? 3 : 2;
			int rows = (buttons.size() + columns - 1) / columns;
			return 22 + rows * 18 + 2;
		}

		@Override
		protected void onLayout() {
			int columns = columns();
			int cellWidth = (width - 12 - (columns - 1) * Theme.GAP) / columns;
			for (int i = 0; i < buttons.size(); i++) {
				int col = i % columns;
				int row = i / columns;
				buttons.get(i).setBounds(x + 6 + col * (cellWidth + Theme.GAP), y + 22 + row * 18, cellWidth, 15);
			}
		}

		@Override
		public void render(UiContext ui) {
			ui.roundRect(x, y, width, height, Theme.SURFACE);
			ui.icon(Icon.STAR, x + 6, y + 6, 9, Theme.ACCENT);
			ui.text("Go to", x + 19, y + 7, Theme.TEXT);
			ui.rect(x + 6, y + 19, width - 12, 1, Theme.BORDER);
			super.render(ui);
		}
	}
}
