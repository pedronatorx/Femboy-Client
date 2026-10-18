package io.github.theamethyx.femboyclient.client.gui.page;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import net.minecraft.client.Minecraft;

import io.github.theamethyx.femboyclient.client.gui.Icon;
import io.github.theamethyx.femboyclient.client.gui.Theme;
import io.github.theamethyx.femboyclient.client.gui.UiContext;
import io.github.theamethyx.femboyclient.client.gui.widget.Button;
import io.github.theamethyx.femboyclient.client.gui.widget.ConfirmButton;
import io.github.theamethyx.femboyclient.client.gui.widget.Container;
import io.github.theamethyx.femboyclient.client.gui.widget.Dropdown;
import io.github.theamethyx.femboyclient.client.gui.widget.ScrollList;
import io.github.theamethyx.femboyclient.client.gui.widget.SettingRow;
import io.github.theamethyx.femboyclient.client.gui.widget.TextField;
import io.github.theamethyx.femboyclient.client.gui.widget.ToggleSwitch;
import io.github.theamethyx.femboyclient.client.gui.widget.Widget;
import io.github.theamethyx.femboyclient.client.smart.RuleRunner;
import io.github.theamethyx.femboyclient.core.rules.Rule;
import io.github.theamethyx.femboyclient.core.rules.RuleEngine;

/**
 * The rule builder: each rule is a card reading "When ... / If ... / Then ...". Rules are checked from the top; arrows move
 * them. Problems (missing values, unknown profiles, contradicting rules) are listed above the rules; the history below
 * shows what ran. Rules recommend unless you choose an action that changes something.
 */
public class RulesPage extends Page {
	private static final int ROW = 18;
	private static final List<Integer> COOLDOWNS = List.of(0, 5, 10, 30, 60, 300, 600, 1800);

	private final ScrollList content;
	private List<Rule> rules = new ArrayList<>();
	private String feedback;
	private int feedbackColor;

	public RulesPage(UiContext ui) {
		super(ui);
		content = add(new ScrollList(ui, Theme.GAP, 0));
	}

	@Override
	public String title() {
		return "Settings";
	}

	@Override
	public String navLabel() {
		return "Rules";
	}

	@Override
	public String subtitle() {
		return "When something happens, if conditions hold, then suggest or change something. Nothing that acts in the game.";
	}

	@Override
	public Icon icon() {
		return Icon.FLOW;
	}

	private RuleRunner runner() {
		return femboy.rules();
	}

	@Override
	public void onShow() {
		rules = new ArrayList<>(runner().engine().rules());
	}

	@Override
	protected void onLayout() {
		content.setBounds(x, y, width, height);
		rules = new ArrayList<>(runner().engine().rules());
		rebuild();
	}

	private void report(String message, boolean ok) {
		feedback = message;
		feedbackColor = ok ? Theme.SUCCESS : Theme.ERROR;
	}

	/** Saves the working copy (rules apply at once). */
	private void save() {
		runner().setRules(rules);
	}

	private void change(int index, Rule rule, boolean structural) {
		rules.set(index, rule);
		save();
		if (structural) {
			rebuild();
		}
	}

	private void rebuild() {
		List<Widget> rows = new ArrayList<>();
		rows.add(new Feedback());
		rows.addAll(SettingRow.rowsFor("options", List.of(femboy.options().rulesEnabled)));
		List<String> problems = runner().engine().validate(runner());
		for (String problem : problems) {
			rows.add(new ModulesPage.Message("⚠ " + problem));
		}

		for (String warning : runner().loadWarnings()) {
			rows.add(new ModulesPage.Message("Skipped when loading: " + warning));
		}

		for (int i = 0; i < rules.size(); i++) {
			rows.add(new RuleCard(i));
		}

		if (rules.isEmpty()) {
			rows.add(new ModulesPage.Message("No rules. Add one below, or put back the suggested starting rules."));
		}

		rows.add(new Actions());
		rows.add(new ConfigPage.SectionTitle("History", "What ran recently (newest first). Dry-run rules show what they would have done."));
		List<RuleEngine.Execution> history = runner().engine().history();
		for (int i = 0; i < Math.min(15, history.size()); i++) {
			RuleEngine.Execution run = history.get(i);
			String what = run.done().isEmpty() ? "" : String.join("; ", run.done());
			rows.add(new ModulesPage.Message(io.github.theamethyx.femboyclient.client.smart.SessionTimeline.time(run.timeMillis()) + "  " + run.rule()
					+ (run.dryRun() ? " (dry run)" : "") + ": " + (what.isEmpty() ? "" : what) + (run.problem() == null ? "" : " " + run.problem())));
		}

		if (history.isEmpty()) {
			rows.add(new ModulesPage.Message("Nothing has run yet this session."));
		}

		content.setChildren(rows);
	}

	private final class Feedback extends Widget {
		@Override
		public int preferredHeight(UiContext ui, int width) {
			return feedback == null ? 0 : ui.wrappedHeight(feedback, width - 16) + 8;
		}

		@Override
		public void render(UiContext ui) {
			if (feedback != null) {
				ui.roundRect(x, y, width, height, Theme.withAlpha(feedbackColor, 0.12f));
				ui.rect(x, y + 2, 2, height - 4, feedbackColor);
				ui.wrapped(feedback, x + 8, y + 4, width - 16, feedbackColor);
			}
		}
	}

	/** One rule. Layout: header, When, If (conditions), Then (actions), footer. */
	private final class RuleCard extends Container {
		private final int index;
		private final List<Widget[]> lines = new ArrayList<>();
		private final List<String> labels = new ArrayList<>();
		private String checkResult;

		RuleCard(int index) {
			this.index = index;
			Rule rule = rules.get(index);

			ToggleSwitch on = add(new ToggleSwitch(() -> rules.get(index).enabled(), value -> change(index, rules.get(index).withEnabled(value), false)));
			on.tooltip("Run this rule.");
			TextField name = add(new TextField("Rule name", 40).onChange(text -> change(index, rules.get(index).withName(text), false)));
			name.setText(rule.name());
			Button up = add(new Button("", Button.Style.GHOST, () -> move(index, -1)).icon(Icon.ARROW));
			up.enabledWhen(() -> index > 0).tooltip("Move up: checked earlier (higher priority).");
			Button down = add(new Button("", Button.Style.GHOST, () -> move(index, 1)).icon(Icon.CHEVRON_DOWN));
			down.enabledWhen(() -> index < rules.size() - 1).tooltip("Move down: checked later (lower priority).");
			Button delete = add(new ConfirmButton("", "Delete?", Button.Style.DANGER, () -> {
				rules.remove(index);
				save();
				rebuild();
			}).icon(Icon.TRASH));
			delete.tooltip("Delete this rule (two clicks).");
			line("", on, name, up, down, delete);

			Dropdown<Rule.Trigger> trigger = add(new Dropdown<>(() -> Arrays.asList(Rule.Trigger.values()), () -> rules.get(index).trigger(),
					value -> change(index, rules.get(index).withTrigger(value), false), Rule.Trigger::label));
			line("When", trigger);

			if (rule.conditions().size() > 1) {
				Dropdown<Boolean> match = add(new Dropdown<>(() -> List.of(true, false), () -> rules.get(index).matchAll(),
						value -> change(index, rules.get(index).withMatchAll(value), false), value -> value ? "all of these hold" : "any of these holds"));
				line("If", match);
			}

			for (int c = 0; c < rule.conditions().size(); c++) {
				int ci = c;
				Rule.Condition condition = rule.conditions().get(c);
				Dropdown<Rule.ConditionType> type = add(new Dropdown<>(() -> Arrays.asList(Rule.ConditionType.values()), () -> condition.type(),
						value -> setCondition(ci, new Rule.Condition(value, ""), true), Rule.ConditionType::label));
				Widget value = valueWidget(runner().valueChoices(condition.type()), condition.value(), text -> setCondition(ci, new Rule.Condition(condition.type(), text), false),
						text -> setCondition(ci, new Rule.Condition(condition.type(), text), true), condition.type() == Rule.ConditionType.FPS_BELOW ? "e.g. 30" : "value");
				Button remove = add(new Button("", Button.Style.GHOST, () -> {
					List<Rule.Condition> list = new ArrayList<>(rules.get(index).conditions());
					list.remove(ci);
					change(index, rules.get(index).withConditions(list), true);
				}).icon(Icon.CLOSE));
				line(rule.conditions().size() > 1 || c > 0 ? "" : "If", type, value, remove);
			}

			Button addCondition = add(new Button("Add condition", Button.Style.GHOST, () -> {
				List<Rule.Condition> list = new ArrayList<>(rules.get(index).conditions());
				list.add(new Rule.Condition(Rule.ConditionType.PROFILE_IS, femboy.config().activeProfile()));
				change(index, rules.get(index).withConditions(list), true);
			}).icon(Icon.PLUS));
			addCondition.enabledWhen(() -> rules.get(index).conditions().size() < Rule.MAX_CONDITIONS);
			line(rule.conditions().isEmpty() ? "If" : "", addCondition);

			for (int a = 0; a < rule.actions().size(); a++) {
				int ai = a;
				Rule.Action action = rule.actions().get(a);
				Dropdown<Rule.ActionType> type = add(new Dropdown<>(() -> Arrays.asList(Rule.ActionType.values()), () -> action.type(),
						value -> setAction(ai, new Rule.Action(value, ""), true), RulesPage::actionLabel));
				Widget value = action.type().needsValue()
						? valueWidget(runner().valueChoices(action.type()), action.value(), text -> setAction(ai, new Rule.Action(action.type(), text), false),
								text -> setAction(ai, new Rule.Action(action.type(), text), true), "message")
						: null;
				Button remove = add(new Button("", Button.Style.GHOST, () -> {
					List<Rule.Action> list = new ArrayList<>(rules.get(index).actions());
					list.remove(ai);
					change(index, rules.get(index).withActions(list), true);
				}).icon(Icon.CLOSE));
				if (value == null) {
					line(a == 0 ? "Then" : "", type, remove);
				} else {
					line(a == 0 ? "Then" : "", type, value, remove);
				}
			}

			Button addAction = add(new Button("Add action", Button.Style.GHOST, () -> {
				List<Rule.Action> list = new ArrayList<>(rules.get(index).actions());
				list.add(new Rule.Action(Rule.ActionType.NOTIFY, ""));
				change(index, rules.get(index).withActions(list), true);
			}).icon(Icon.PLUS));
			addAction.enabledWhen(() -> rules.get(index).actions().size() < Rule.MAX_ACTIONS);
			line(rule.actions().isEmpty() ? "Then" : "", addAction);

			Dropdown<Integer> cooldown = add(new Dropdown<>(() -> COOLDOWNS, () -> rules.get(index).cooldownSeconds(),
					value -> change(index, rules.get(index).withCooldown(value), false), value -> value == 0 ? "no cooldown" : "at most every " + seconds(value)));
			cooldown.tooltip("The rule runs at most this often. Rules that change something always wait at least " + RuleEngine.MIN_CHANGE_COOLDOWN + " s.");
			Dropdown<Boolean> dry = add(new Dropdown<>(() -> List.of(false, true), () -> rules.get(index).dryRun(),
					value -> change(index, rules.get(index).withDryRun(value), false), value -> value ? "dry run: only record" : "do it"));
			dry.tooltip("Dry run: the rule only records in the history what it would have done.");
			Button check = add(new Button("Check now", Button.Style.SECONDARY, () -> {
				Rule current = rules.get(index);
				checkResult = RuleEngine.matches(current, runner()) ? "The conditions hold right now." : "The conditions do not hold right now.";
			}).icon(Icon.CHECK));
			check.tooltip("Evaluate the conditions against the current state, without running anything.");
			line("", cooldown, dry, check);
		}

		private void line(String label, Widget... widgets) {
			labels.add(label);
			lines.add(widgets);
		}

		private void setCondition(int conditionIndex, Rule.Condition condition, boolean structural) {
			List<Rule.Condition> list = new ArrayList<>(rules.get(index).conditions());
			list.set(conditionIndex, condition);
			change(index, rules.get(index).withConditions(list), structural);
		}

		private void setAction(int actionIndex, Rule.Action action, boolean structural) {
			List<Rule.Action> list = new ArrayList<>(rules.get(index).actions());
			list.set(actionIndex, action);
			change(index, rules.get(index).withActions(list), structural);
		}

		/** A dropdown when the value has fixed choices (names, ids, modes), otherwise a text field. */
		private Widget valueWidget(List<String> choices, String current, java.util.function.Consumer<String> typed, java.util.function.Consumer<String> chosen,
				String placeholder) {
			if (!choices.isEmpty()) {
				return add(new Dropdown<>(() -> choices, () -> current.isEmpty() ? null : current, chosen, value -> value));
			}

			TextField field = add(new TextField(placeholder, 80).onChange(typed));
			field.setText(current);
			return field;
		}

		@Override
		public int preferredHeight(UiContext ui, int width) {
			return lines.size() * ROW + 8 + (checkResult == null ? 0 : 10);
		}

		@Override
		protected void onLayout() {
			int labelW = 34;
			for (int i = 0; i < lines.size(); i++) {
				Widget[] widgets = lines.get(i);
				int rowY = y + 4 + i * ROW;
				int left = x + 6 + labelW;
				int right = x + width - 6;
				if (i == 0) {
					// Header: switch, name, then the small buttons on the right.
					widgets[0].setBounds(x + 8, rowY + 3, ToggleSwitch.WIDTH, ToggleSwitch.HEIGHT);
					int bx = right - 16;
					for (int b = widgets.length - 1; b >= 2; b--) {
						widgets[b].setBounds(bx, rowY, 16, 16);
						bx -= 18;
					}

					widgets[1].setBounds(x + 8 + ToggleSwitch.WIDTH + 6, rowY + 1, bx - (x + 8 + ToggleSwitch.WIDTH + 6) - 2, 14);
					continue;
				}

				boolean hasRemove = widgets.length > 1 && widgets[widgets.length - 1] instanceof Button button && button.label().isEmpty();
				int available = right - left - (hasRemove ? 18 : 0);
				int count = hasRemove ? widgets.length - 1 : widgets.length;
				int gap = Theme.GAP;
				int each = (available - gap * (count - 1)) / Math.max(1, count);
				for (int w = 0; w < count; w++) {
					widgets[w].setBounds(left + w * (each + gap), rowY + 1, each, 14);
				}

				if (hasRemove) {
					widgets[widgets.length - 1].setBounds(right - 16, rowY, 16, 16);
				}
			}
		}

		@Override
		public void render(UiContext ui) {
			Rule rule = rules.get(index);
			ui.roundRect(x, y, width, height, Theme.SURFACE, 5);
			ui.rect(x, y + 4, 2, height - 8, rule.enabled() ? (rule.dryRun() ? Theme.INFO : Theme.ACCENT) : Theme.TEXT_MUTED);
			for (int i = 1; i < labels.size(); i++) {
				if (!labels.get(i).isEmpty()) {
					ui.text(labels.get(i), x + 8, y + 4 + i * ROW + 4, Theme.ACCENT_STRONG);
				}
			}

			if (checkResult != null) {
				ui.textScaled(checkResult, x + 40, y + height - 11, 0.75f, Theme.TEXT_SECONDARY);
			}

			super.render(ui);
		}
	}

	private static String actionLabel(Rule.ActionType type) {
		return type.label() + (type.changesSomething() ? " (changes settings)" : "");
	}

	private static String seconds(int value) {
		return value >= 60 ? value / 60 + " min" : value + " s";
	}

	private void move(int index, int delta) {
		int target = index + delta;
		if (target < 0 || target >= rules.size()) {
			return;
		}

		Rule rule = rules.remove(index);
		rules.add(target, rule);
		save();
		rebuild();
	}

	/** Add, starting rules, export and import. */
	private final class Actions extends Container {
		Actions() {
			add(new Button("Add rule", Button.Style.PRIMARY, () -> {
				if (rules.size() >= RuleEngine.MAX_RULES) {
					report("At most " + RuleEngine.MAX_RULES + " rules.", false);
					return;
				}

				rules.add(new Rule("New rule", true, Rule.Trigger.PROFILE_CHANGED, true, List.of(), List.of(new Rule.Action(Rule.ActionType.NOTIFY, "")), 30, false));
				save();
				rebuild();
			}).icon(Icon.PLUS)).tooltip("Add a rule at the bottom.");
			add(new Button("Starting rules", Button.Style.SECONDARY, () -> {
				for (Rule rule : RuleEngine.defaults()) {
					if (rules.stream().noneMatch(existing -> existing.name().equals(rule.name()))) {
						rules.add(rule);
					}
				}

				save();
				report("Added the suggested starting rules that were missing.", true);
				rebuild();
			}).icon(Icon.SPARKLE)).tooltip("Add the suggestion rules a new installation starts with (if missing).");
			add(new Button("", Button.Style.SECONDARY, () -> {
				Minecraft.getInstance().keyboardHandler.setClipboard(runner().engine().export());
				report("Rules copied to the clipboard as text.", true);
				rebuild();
			}).icon(Icon.COPY)).tooltip("Copy all rules as text.");
			add(new Button("", Button.Style.SECONDARY, () -> {
				List<String> warnings = new ArrayList<>();
				List<Rule> imported = RuleEngine.parseExport(Minecraft.getInstance().keyboardHandler.getClipboard(), warnings);
				if (imported == null) {
					report("Import failed: " + String.join(" ", warnings), false);
				} else {
					int room = RuleEngine.MAX_RULES - rules.size();
					rules.addAll(imported.subList(0, Math.min(room, imported.size())).stream().map(rule -> rule.withEnabled(false)).toList());
					save();
					report("Imported " + Math.min(room, imported.size()) + " rule(s), switched off so you can check them first."
							+ (warnings.isEmpty() ? "" : " Skipped: " + String.join(" ", warnings)), true);
				}

				rebuild();
			}).icon(Icon.FOLDER)).tooltip("Import rules copied as text (from the clipboard). They are added switched off.");
		}

		@Override
		public int preferredHeight(UiContext ui, int width) {
			return 16;
		}

		@Override
		protected void onLayout() {
			int small = 18;
			int rest = width - 2 * small - 3 * Theme.GAP;
			children.get(0).setBounds(x, y, rest / 2, 16);
			children.get(1).setBounds(x + rest / 2 + Theme.GAP, y, rest - rest / 2, 16);
			children.get(2).setBounds(x + width - 2 * small - Theme.GAP, y, small, 16);
			children.get(3).setBounds(x + width - small, y, small, 16);
		}
	}
}
