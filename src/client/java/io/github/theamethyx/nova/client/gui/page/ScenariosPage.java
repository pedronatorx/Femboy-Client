package io.github.theamethyx.nova.client.gui.page;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import io.github.theamethyx.nova.client.gui.Icon;
import io.github.theamethyx.nova.client.gui.Theme;
import io.github.theamethyx.nova.client.gui.UiContext;
import io.github.theamethyx.nova.client.gui.widget.Button;
import io.github.theamethyx.nova.client.gui.widget.Container;
import io.github.theamethyx.nova.client.gui.widget.ScrollList;
import io.github.theamethyx.nova.client.gui.widget.Widget;
import io.github.theamethyx.nova.client.scenario.TestScenario;
import io.github.theamethyx.nova.client.scenario.TestScenarioManager;

/** Lists the test scenarios and builds or removes them in the singleplayer world. */
public class ScenariosPage extends Page {
	private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());

	private final ScrollList content;
	private String feedback;
	private int feedbackColor = Theme.TEXT_SECONDARY;

	public ScenariosPage(UiContext ui) {
		super(ui);
		content = add(new ScrollList(ui, Theme.GAP, 0));
	}

	@Override
	public String title() {
		return "Test Scenarios";
	}

	@Override
	public String navLabel() {
		return "Scenarios";
	}

	@Override
	public String subtitle() {
		return "Reproducible fixtures built into a singleplayer test world, with their known conditions recorded.";
	}

	@Override
	public Icon icon() {
		return Icon.SCENARIOS;
	}

	@Override
	protected void onLayout() {
		content.setBounds(x, y, width, height);
		rebuild();
	}

	@Override
	public void onShow() {
		rebuild();
	}

	private void rebuild() {
		List<Widget> rows = new ArrayList<>();
		rows.add(new StatusBanner());
		for (TestScenario scenario : nova.scenarios().scenarios()) {
			rows.add(new ScenarioCard(scenario));
		}

		content.setChildren(rows);
	}

	private final class StatusBanner extends Widget {
		private String text() {
			String reason = nova.scenarios().unavailableReason();
			if (feedback != null) {
				return feedback;
			}

			return reason != null ? reason : "Scenarios are built a few blocks in front of you. Removing one clears its area to air.";
		}

		private int color() {
			if (feedback != null) {
				return feedbackColor;
			}

			return nova.scenarios().unavailableReason() != null ? Theme.WARNING : Theme.TEXT_SECONDARY;
		}

		@Override
		public int preferredHeight(UiContext ui, int width) {
			return ui.wrappedHeight(text(), width - 16) + 8;
		}

		@Override
		public void render(UiContext ui) {
			int color = color();
			ui.roundRect(x, y, width, height, Theme.withAlpha(color, 0.10f));
			ui.rect(x, y + 2, 2, height - 4, color);
			ui.wrapped(text(), x + 8, y + 4, width - 16, color);
		}
	}

	private final class ScenarioCard extends Container {
		private final TestScenario scenario;
		private final Button build;
		private final Button remove;

		ScenarioCard(TestScenario scenario) {
			this.scenario = scenario;
			build = add(new Button(() -> run().state() == TestScenarioManager.State.BUILT ? "Rebuild here" : "Build here",
					Button.Style.PRIMARY, this::buildNow).icon(Icon.PLAY));
			build.enabledWhen(() -> nova.scenarios().unavailableReason() == null && run().state() != TestScenarioManager.State.BUILDING);
			build.tooltip("Build this scenario in front of you. A previous build in this world is removed first.");
			remove = add(new Button("Remove", Button.Style.DANGER, this::removeNow).icon(Icon.TRASH));
			remove.enabledWhen(() -> nova.scenarios().unavailableReason() == null && run().state() == TestScenarioManager.State.BUILT);
			remove.tooltip("Delete what this scenario built (its area is cleared to air) and forget its fixtures.");
		}

		private TestScenarioManager.Run run() {
			return nova.scenarios().run(scenario.id());
		}

		private void buildNow() {
			feedback = "Building '" + scenario.name() + "'...";
			feedbackColor = Theme.INFO;
			nova.scenarios().build(scenario.id()).whenComplete((run, error) -> {
				feedback = error == null ? "Built '" + scenario.name() + "'." : "Could not build: " + rootMessage(error);
				feedbackColor = error == null ? Theme.SUCCESS : Theme.ERROR;
			});
		}

		private void removeNow() {
			nova.scenarios().remove(scenario.id()).whenComplete((ignored, error) -> {
				feedback = error == null ? "Removed '" + scenario.name() + "'." : "Could not remove: " + rootMessage(error);
				feedbackColor = error == null ? Theme.SUCCESS : Theme.ERROR;
			});
		}

		private String status() {
			TestScenarioManager.Run run = run();
			return switch (run.state()) {
				case NOT_BUILT -> "Not built in this world";
				case BUILDING -> "Building...";
				case BUILT -> "Built " + TIME.format(run.time()) + " at " + run.origin().toShortString() + " (" + run.dimension() + ")";
				case FAILED -> "Failed: " + run.message();
			};
		}

		private int statusColor() {
			return switch (run().state()) {
				case BUILT -> Theme.SUCCESS;
				case FAILED -> Theme.ERROR;
				case BUILDING -> Theme.INFO;
				case NOT_BUILT -> Theme.TEXT_MUTED;
			};
		}

		@Override
		public int preferredHeight(UiContext ui, int width) {
			return 22 + ui.wrappedHeight(scenario.description(), width - 12) + 34;
		}

		@Override
		protected void onLayout() {
			int buttonY = y + height - 20;
			remove.setBounds(x + width - 6 - 64, buttonY, 64, 15);
			build.setBounds(x + width - 6 - 64 - Theme.GAP - 84, buttonY, 84, 15);
		}

		@Override
		public void render(UiContext ui) {
			ui.roundRect(x, y, width, height, Theme.SURFACE);
			ui.icon(Icon.SCENARIOS, x + 6, y + 6, 9, Theme.ACCENT);
			ui.text(scenario.name(), x + 19, y + 7, Theme.TEXT);
			int used = ui.wrapped(scenario.description(), x + 6, y + 20, width - 12, Theme.TEXT_SECONDARY);
			int statusY = y + 22 + used;
			ui.rect(x + 6, statusY + 3, 4, 4, statusColor());
			boolean cut = ui.textClipped(status(), x + 14, statusY + 1, width - 20, statusColor());
			if (cut && ui.hovered(x, statusY, width, 10)) {
				ui.tooltip(this, status());
			}

			super.render(ui);
		}
	}

	private static String rootMessage(Throwable error) {
		Throwable root = error;
		while (root.getCause() != null) {
			root = root.getCause();
		}

		return root.getMessage() == null ? root.getClass().getSimpleName() : root.getMessage();
	}
}
