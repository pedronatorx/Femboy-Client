package io.github.theamethyx.femboyclient.client.gui.page;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.IntPredicate;
import java.util.function.Predicate;

import net.minecraft.client.Minecraft;

import io.github.theamethyx.femboyclient.client.gui.Icon;
import io.github.theamethyx.femboyclient.client.gui.Theme;
import io.github.theamethyx.femboyclient.client.gui.UiContext;
import io.github.theamethyx.femboyclient.client.gui.widget.Button;
import io.github.theamethyx.femboyclient.client.gui.widget.Container;
import io.github.theamethyx.femboyclient.client.gui.widget.Dropdown;
import io.github.theamethyx.femboyclient.client.gui.widget.ScrollList;
import io.github.theamethyx.femboyclient.client.gui.widget.SettingRow;
import io.github.theamethyx.femboyclient.client.gui.widget.TextField;
import io.github.theamethyx.femboyclient.client.gui.widget.Widget;
import io.github.theamethyx.femboyclient.client.profile.Presets;
import io.github.theamethyx.femboyclient.core.config.ConfigurationManager;
import io.github.theamethyx.femboyclient.core.config.ProfileRule;

/**
 * Profiles: switch, create, duplicate, import, export, delete and reset them, and the rules that switch profiles
 * automatically from the held item.
 */
public class ProfilesPage extends Page {
	private static final long CONFIRM_WINDOW_MS = 4000;
	private static final IntPredicate NAME_CHARS = cp -> Character.isLetterOrDigit(cp) || cp == '-' || cp == '_';

	private final ScrollList content;
	private String feedback;
	private int feedbackColor;
	private String pendingConfirm;
	private long pendingConfirmAt;

	public ProfilesPage(UiContext ui) {
		super(ui);
		content = add(new ScrollList(ui, Theme.GAP, 0));
	}

	@Override
	public String title() {
		return "Profiles";
	}

	@Override
	public String subtitle() {
		return "Each profile remembers which modules are on, their keys and all settings.";
	}

	@Override
	public Icon icon() {
		return Icon.FOLDER;
	}

	@Override
	public void onShow() {
		rebuild();
	}

	@Override
	protected void onLayout() {
		content.setBounds(x, y, width, height);
		rebuild();
	}

	private void report(String message, boolean ok) {
		feedback = message;
		feedbackColor = ok ? Theme.SUCCESS : Theme.ERROR;
	}

	/** Two clicks within a few seconds confirm a destructive action. */
	private boolean confirm(String action) {
		if (action.equals(pendingConfirm) && System.currentTimeMillis() - pendingConfirmAt < CONFIRM_WINDOW_MS) {
			pendingConfirm = null;
			return true;
		}

		pendingConfirm = action;
		pendingConfirmAt = System.currentTimeMillis();
		return false;
	}

	private boolean confirming(String action) {
		return action.equals(pendingConfirm) && System.currentTimeMillis() - pendingConfirmAt < CONFIRM_WINDOW_MS;
	}

	private void rebuild() {
		ConfigurationManager config = femboy.config();
		List<Widget> rows = new ArrayList<>();
		rows.add(new Feedback());

		rows.add(new ConfigPage.SectionTitle("Your profiles", "Use switches profiles; the current one is saved first. "
				+ "Modules that are off in the profile you switch to are turned off."));
		for (String profile : config.profiles()) {
			rows.add(new ProfileRow(profile));
		}

		rows.add(new NameAndButton("New profile name", "Create", Icon.PLUS,
				"Create a profile as a copy of the current one, without switching to it.", name -> {
			String problem = config.createProfile(name);
			report(problem == null ? "Created profile '" + name + "'." : problem, problem == null);
			return problem == null;
		}));
		rows.add(new NameAndButton("Name for the imported profile", "Import", Icon.FOLDER,
				"Create a profile from text copied with Export. Every value is checked; invalid ones are replaced by defaults.", name -> {
			String clipboard = Minecraft.getInstance().keyboardHandler.getClipboard();
			ConfigurationManager.ImportResult result = config.importProfile(name, clipboard);
			if (result.error() != null) {
				report("Import failed: " + result.error(), false);
				return false;
			}

			report("Imported '" + name + "'." + (result.warnings().isEmpty() ? ""
					: " Replaced by defaults: " + String.join(" ", result.warnings())), true);
			return true;
		}));
		rows.add(new ResetRow());

		rows.add(new ConfigPage.SectionTitle("Ready-made presets", "Starting points for common PvP styles. Installing one creates a "
				+ "normal profile you can change, copy and export; Reset puts the preset's settings back. Presets only turn on "
				+ "information and visual modules."));
		for (Presets.Preset preset : Presets.all()) {
			rows.add(new PresetRow(preset));
		}

		rows.add(new ConfigPage.SectionTitle("Automatic switching", "Rules are checked from the top; the first one that matches "
				+ "chooses the profile. When none matches, the current profile stays."));
		rows.addAll(SettingRow.rowsFor(List.of(femboy.options().autoProfile, femboy.options().profileMessages)));
		String problem = femboy.profileSwitcher().lastProblem();
		if (problem != null) {
			rows.add(new ModulesPage.Message("Last automatic switch failed: " + problem));
		}

		List<ProfileRule> rules = config.profileRules();
		for (int i = 0; i < rules.size(); i++) {
			rows.add(new RuleRow(i));
		}

		if (rules.isEmpty()) {
			rows.add(new ModulesPage.Message("No rules yet. Add one, then choose what you hold and which profile to use."));
		}

		rows.add(new AddRuleRow());
		content.setChildren(rows);
	}

	private void setRules(List<ProfileRule> rules) {
		femboy.config().setProfileRules(rules);
		femboy.profileSwitcher().reset();
		rebuild();
	}

	private final class Feedback extends Widget {
		@Override
		public int preferredHeight(UiContext ui, int width) {
			return feedback == null ? 0 : ui.wrappedHeight(feedback, width - 16) + 8;
		}

		@Override
		public void render(UiContext ui) {
			if (feedback == null) {
				return;
			}

			ui.roundRect(x, y, width, height, Theme.withAlpha(feedbackColor, 0.12f));
			ui.rect(x, y + 2, 2, height - 4, feedbackColor);
			ui.wrapped(feedback, x + 8, y + 4, width - 16, feedbackColor);
		}
	}

	private final class ProfileRow extends Container {
		private final String name;
		private final boolean active;
		private final Button use;
		private final Button duplicate;
		private final Button export;
		private final Button delete;

		ProfileRow(String name) {
			this.name = name;
			this.active = name.equals(femboy.config().activeProfile());
			use = add(new Button("Use", Button.Style.SECONDARY, () -> {
				String problem = femboy.config().switchProfile(name);
				report(problem == null ? "Switched to profile '" + name + "'." : problem, problem == null);
				rebuild();
			}));
			use.tooltip("Save the current profile, then load '" + name + "'.");
			duplicate = add(new Button("Copy", Button.Style.GHOST, this::duplicate));
			duplicate.tooltip("Create a copy of this profile named '" + copyName() + "'.");
			export = add(new Button("Export", Button.Style.GHOST, this::export));
			export.tooltip("Copy this profile to the clipboard to share it, and save it in the exports folder.");
			delete = add(new Button(() -> confirming("delete:" + name) ? "Confirm" : "", Button.Style.DANGER, () -> {
				if (!confirm("delete:" + name)) {
					return;
				}

				String problem = femboy.config().deleteProfile(name);
				report(problem == null ? "Deleted profile '" + name + "'." : problem, problem == null);
				rebuild();
			}).icon(Icon.TRASH));
			delete.tooltip("Delete this profile. Click twice to confirm. A backup copy stays in the profiles folder.");
		}

		private String copyName() {
			List<String> existing = femboy.config().profiles().stream().map(String::toLowerCase).toList();
			String base = name.length() > 26 ? name.substring(0, 26) : name;
			String candidate = base + "-copy";
			for (int i = 2; existing.contains(candidate.toLowerCase()); i++) {
				candidate = base + "-copy" + i;
			}

			return candidate;
		}

		private void duplicate() {
			String target = copyName();
			String problem = femboy.config().duplicateProfile(name, target);
			report(problem == null ? "Copied '" + name + "' as '" + target + "'." : problem, problem == null);
			rebuild();
		}

		private void export() {
			String text = femboy.config().exportProfile(name);
			if (text == null) {
				report("Profile '" + name + "' could not be read.", false);
				return;
			}

			Minecraft.getInstance().keyboardHandler.setClipboard(text);
			Path file = femboy.config().exportProfileToFile(name);
			report("Copied '" + name + "' to the clipboard" + (file == null ? " (saving the file failed; see Diagnostics)."
					: " and saved it as exports/" + file.getFileName() + "."), true);
			rebuild();
		}

		@Override
		public int preferredHeight(UiContext ui, int width) {
			return 20;
		}

		@Override
		protected void onLayout() {
			int right = x + width - 3;
			if (!active) {
				int deleteWidth = confirming("delete:" + name) ? 50 : 18;
				delete.setBounds(right - deleteWidth, y + 3, deleteWidth, 14);
				right -= deleteWidth + Theme.GAP;
			}

			int exportWidth = Math.max(44, export.naturalWidth(ui) + 4);
			export.setBounds(right - exportWidth, y + 3, exportWidth, 14);
			right -= exportWidth + Theme.GAP;
			duplicate.setBounds(right - 34, y + 3, 34, 14);
			right -= 34 + Theme.GAP;
			use.setBounds(right - 30, y + 3, 30, 14);
		}

		@Override
		public void render(UiContext ui) {
			onLayout();
			ui.roundRect(x, y, width, height, active ? Theme.SURFACE_ACTIVE : Theme.SURFACE);
			if (active) {
				ui.rect(x, y + 3, 2, height - 6, Theme.ACCENT);
				ui.icon(Icon.CHECK, x + 7, y + 6, 8, Theme.ACCENT);
			} else {
				ui.icon(Icon.FOLDER, x + 7, y + 6, 8, Theme.TEXT_MUTED);
			}

			ui.textClipped(name, x + 20, y + 6, Math.max(20, use.x() - x - 70), active ? Theme.TEXT : Theme.TEXT_SECONDARY);
			if (active) {
				ui.textRight("Active", use.x() - 6, y + 6, Theme.ACCENT_STRONG);
				duplicate.render(ui);
				export.render(ui);
			} else {
				super.render(ui);
			}
		}

		@Override
		public boolean mouseClicked(UiContext ui, double mouseX, double mouseY, int button) {
			if (active) {
				return duplicate.mouseClicked(ui, mouseX, mouseY, button) || export.mouseClicked(ui, mouseX, mouseY, button);
			}

			return super.mouseClicked(ui, mouseX, mouseY, button);
		}
	}

	private final class NameAndButton extends Container {
		private final TextField field;
		private final Button button;

		NameAndButton(String placeholder, String label, Icon icon, String tooltip, Predicate<String> action) {
			field = add(new TextField(placeholder, 32).allow(NAME_CHARS));
			Runnable submit = () -> {
				if (action.test(field.text().trim())) {
					field.setText("");
				}

				rebuild();
			};
			field.onSubmit(text -> submit.run());
			button = add(new Button(label, Button.Style.PRIMARY, submit).icon(icon));
			button.enabledWhen(() -> !field.text().isBlank());
			button.tooltip(tooltip);
		}

		@Override
		public int preferredHeight(UiContext ui, int width) {
			return 15;
		}

		@Override
		protected void onLayout() {
			int buttonWidth = 58;
			field.setBounds(x, y, width - buttonWidth - Theme.GAP, 15);
			button.setBounds(x + width - buttonWidth, y, buttonWidth, 15);
		}
	}

	private final class ResetRow extends Container {
		private final Button reset;

		ResetRow() {
			reset = add(new Button(() -> confirming("reset") ? "Click again to reset '" + femboy.config().activeProfile() + "'"
					: "Reset the current profile", Button.Style.DANGER, () -> {
				if (confirm("reset")) {
					femboy.config().resetActiveProfile();
					report("Reset '" + femboy.config().activeProfile() + "': every module is off with default settings.", true);
					rebuild();
				}
			}).icon(Icon.RESET));
			reset.tooltip("Turn every module off and restore all settings and keys of the current profile to their defaults. "
					+ "Other profiles are not changed. Click twice to confirm.");
		}

		@Override
		public int preferredHeight(UiContext ui, int width) {
			return 16;
		}

		@Override
		protected void onLayout() {
			reset.setBounds(x, y, width, 16);
		}
	}

	private final class PresetRow extends Container {
		private final Presets.Preset preset;
		private final Button install;
		private final Button use;
		private final Button rule;

		PresetRow(Presets.Preset preset) {
			this.preset = preset;
			install = add(new Button(() -> !exists() ? "Install" : confirming("preset:" + preset.profile()) ? "Confirm" : "Reset",
					Button.Style.SECONDARY, this::install));
			install.tooltip(() -> exists() ? "Put this preset's original settings back into '" + preset.profile() + "'. Click twice to confirm."
					: "Create the profile '" + preset.profile() + "' from this preset (without switching to it).");
			use = add(new Button("Use", Button.Style.PRIMARY, () -> {
				String problem = femboy.config().switchProfile(preset.profile());
				report(problem == null ? "Switched to '" + preset.profile() + "'." : problem, problem == null);
				rebuild();
			}));
			use.enabledWhen(() -> exists() && !preset.profile().equals(femboy.config().activeProfile()));
			use.tooltip("Switch to this profile.");
			rule = add(new Button(() -> hasRule() ? "Rule added" : "Add rule", Button.Style.GHOST, () -> {
				List<ProfileRule> rules = new ArrayList<>(femboy.config().profileRules());
				rules.add(new ProfileRule(preset.trigger(), ProfileRule.Dimension.ANY, preset.profile()));
				setRules(rules);
				report("Added a rule: " + preset.trigger().label() + " switches to '" + preset.profile()
						+ "'. Turn on 'Switch profiles automatically' below to use it.", true);
			}));
			rule.enabledWhen(() -> exists() && !hasRule());
			rule.tooltip("Add an automatic switching rule: " + preset.trigger().label() + " uses this profile.");
		}

		private boolean exists() {
			return femboy.config().profileExists(preset.profile());
		}

		private boolean hasRule() {
			return femboy.config().profileRules().stream().anyMatch(r -> r.profile().equals(preset.profile()) && r.item() == preset.trigger());
		}

		private void install() {
			String problem;
			if (!exists()) {
				problem = femboy.config().createProfileFrom(preset.profile(), preset.modules());
				report(problem == null ? "Installed '" + preset.title() + "' as profile '" + preset.profile() + "'." : problem, problem == null);
			} else if (confirm("preset:" + preset.profile())) {
				problem = femboy.config().overwriteProfile(preset.profile(), preset.modules());
				report(problem == null ? "Reset '" + preset.profile() + "' to the " + preset.title() + " preset." : problem, problem == null);
			} else {
				return;
			}

			rebuild();
		}

		@Override
		public int preferredHeight(UiContext ui, int width) {
			return 24 + ui.wrappedHeight(preset.description(), width - 12);
		}

		@Override
		protected void onLayout() {
			int right = x + width - 4;
			rule.setBounds(right - 58, y + 4, 58, 14);
			use.setBounds(right - 58 - Theme.GAP - 30, y + 4, 30, 14);
			install.setBounds(right - 58 - 2 * Theme.GAP - 30 - 46, y + 4, 46, 14);
		}

		@Override
		public void render(UiContext ui) {
			onLayout();
			boolean active = preset.profile().equals(femboy.config().activeProfile());
			ui.roundRect(x, y, width, height, active ? Theme.SURFACE_ACTIVE : Theme.SURFACE);
			ui.icon(Icon.STAR, x + 6, y + 7, 8, exists() ? Theme.ACCENT : Theme.TEXT_MUTED);
			ui.textClipped(preset.title(), x + 18, y + 7, install.x() - x - 24, Theme.TEXT);
			ui.wrapped(preset.description(), x + 6, y + 20, width - 12, Theme.TEXT_MUTED);
			super.render(ui);
		}
	}

	private final class RuleRow extends Container {
		private final Dropdown<ProfileRule.Trigger> item;
		private final Dropdown<ProfileRule.Dimension> dimension;
		private final Dropdown<String> profile;
		private final Button up;
		private final Button remove;

		RuleRow(int index) {
			item = add(new Dropdown<>(() -> Arrays.asList(ProfileRule.Trigger.values()), () -> rule(index).item(),
					value -> replace(index, new ProfileRule(value, rule(index).dimension(), rule(index).profile())), ProfileRule.Trigger::label));
			item.tooltip("What you hold in your main hand, or gliding with an elytra.");
			dimension = add(new Dropdown<>(() -> Arrays.asList(ProfileRule.Dimension.values()), () -> rule(index).dimension(),
					value -> replace(index, new ProfileRule(rule(index).item(), value, rule(index).profile())), ProfileRule.Dimension::label));
			dimension.tooltip("Only match in this dimension.");
			profile = add(new Dropdown<>(() -> femboy.config().profiles(), () -> rule(index).profile(),
					value -> replace(index, new ProfileRule(rule(index).item(), rule(index).dimension(), value)), name -> name));
			profile.tooltip(() -> femboy.config().profileExists(rule(index).profile()) ? "Profile to switch to."
					: "This profile does not exist any more; the rule is skipped until you pick another.");
			up = add(new Button("Up", Button.Style.GHOST, () -> {
				List<ProfileRule> rules = new ArrayList<>(femboy.config().profileRules());
				rules.add(index - 1, rules.remove(index));
				setRules(rules);
			}));
			up.enabledWhen(() -> index > 0);
			up.tooltip("Check this rule before the one above it.");
			remove = add(new Button("", Button.Style.DANGER, () -> {
				List<ProfileRule> rules = new ArrayList<>(femboy.config().profileRules());
				rules.remove(index);
				setRules(rules);
			}).icon(Icon.TRASH));
			remove.tooltip("Remove this rule.");
		}

		private ProfileRule rule(int index) {
			List<ProfileRule> rules = femboy.config().profileRules();
			return index < rules.size() ? rules.get(index) : rules.getLast();
		}

		private void replace(int index, ProfileRule rule) {
			List<ProfileRule> rules = new ArrayList<>(femboy.config().profileRules());
			rules.set(index, rule);
			setRules(rules);
		}

		@Override
		public int preferredHeight(UiContext ui, int width) {
			return 20;
		}

		@Override
		protected void onLayout() {
			int buttons = 26 + Theme.GAP + 18;
			int free = width - 8 - buttons - 3 * Theme.GAP;
			int itemWidth = free * 2 / 5;
			int dimensionWidth = free / 4;
			int profileWidth = free - itemWidth - dimensionWidth;
			int cx = x + 4;
			item.setBounds(cx, y + 3, itemWidth, 14);
			dimension.setBounds(cx += itemWidth + Theme.GAP, y + 3, dimensionWidth, 14);
			profile.setBounds(cx += dimensionWidth + Theme.GAP, y + 3, profileWidth, 14);
			up.setBounds(cx += profileWidth + Theme.GAP, y + 3, 26, 14);
			remove.setBounds(cx + 26 + Theme.GAP, y + 3, 18, 14);
		}

		@Override
		public void render(UiContext ui) {
			ui.roundRect(x, y, width, height, Theme.SURFACE);
			super.render(ui);
		}
	}

	private final class AddRuleRow extends Container {
		private final Button add;

		AddRuleRow() {
			add = add(new Button("Add rule", Button.Style.SECONDARY, () -> {
				List<ProfileRule> rules = new ArrayList<>(femboy.config().profileRules());
				rules.add(new ProfileRule(ProfileRule.Trigger.SWORD, ProfileRule.Dimension.ANY, femboy.config().activeProfile()));
				setRules(rules);
			}).icon(Icon.PLUS));
			add.tooltip("Add a rule at the bottom of the list.");
		}

		@Override
		public int preferredHeight(UiContext ui, int width) {
			return 16;
		}

		@Override
		protected void onLayout() {
			add.setBounds(x, y, width, 16);
		}
	}
}
