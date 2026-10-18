package io.github.theamethyx.femboyclient.client.gui.page;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.IntPredicate;

import net.minecraft.client.Minecraft;

import io.github.theamethyx.femboyclient.client.gui.GuiState;
import io.github.theamethyx.femboyclient.client.gui.Icon;
import io.github.theamethyx.femboyclient.client.gui.Theme;
import io.github.theamethyx.femboyclient.client.gui.UiContext;
import io.github.theamethyx.femboyclient.client.gui.widget.Button;
import io.github.theamethyx.femboyclient.client.gui.widget.Container;
import io.github.theamethyx.femboyclient.client.gui.widget.Dialog;
import io.github.theamethyx.femboyclient.client.gui.widget.Dropdown;
import io.github.theamethyx.femboyclient.client.gui.widget.PopupMenu;
import io.github.theamethyx.femboyclient.client.gui.widget.ScrollList;
import io.github.theamethyx.femboyclient.client.gui.widget.SettingRow;
import io.github.theamethyx.femboyclient.client.gui.widget.Widget;
import io.github.theamethyx.femboyclient.client.profile.Presets;
import io.github.theamethyx.femboyclient.core.config.ConfigurationManager;
import io.github.theamethyx.femboyclient.core.config.ProfileRule;
import io.github.theamethyx.femboyclient.core.module.Module;

/**
 * Profiles, kept simple:
 * <ul>
 * <li>a row of actions: New profile, Import, Back to the previous profile;</li>
 * <li>one card per profile: its name, whether it is active (a check mark and the word "Active"), a short description
 * (your note, or which modules it turns on), and Preview / Use; everything else (duplicate, rename, export, note, reset,
 * delete) is in its "⋯" menu;</li>
 * <li>the preview of a switch: exactly which modules, keys and settings would change;</li>
 * <li>folded away until opened: what a profile stores, recently used profiles, ready-made presets and automatic
 * switching.</li>
 * </ul>
 * Deleting and resetting ask first and say what will be lost. Backups (restore points) are in Settings › General.
 */
public class ProfilesPage extends Page {
	private static final IntPredicate NAME_CHARS = cp -> Character.isLetterOrDigit(cp) || cp == '-' || cp == '_';

	private final ScrollList content;
	private final Button create;
	private final Button importButton;
	private final Button back;
	private String feedback;
	private int feedbackColor;
	/** Profile compared with the active one (the preview), or null. */
	private String compareWith;

	public ProfilesPage(UiContext ui) {
		super(ui);
		content = add(new ScrollList(ui, Theme.SPACE_S, 0));
		create = add(new Button("New profile", Button.Style.PRIMARY, this::promptCreate));
		create.icon(Icon.PLUS);
		create.tooltip("Create a profile as a copy of the current one, without switching to it.");
		importButton = add(new Button("Import", Button.Style.SECONDARY, this::promptImport));
		importButton.icon(Icon.FOLDER);
		importButton.tooltip("Create a profile from text someone shared (copied with Export). Every value is checked first.");
		back = add(new Button(() -> "Back to " + (femboy.config().previousProfile() == null ? "previous" : femboy.config().previousProfile()),
				Button.Style.GHOST, this::revert));
		back.icon(Icon.UNDO);
		back.enabledWhen(() -> femboy.config().previousProfile() != null);
		back.tooltip("Switch back to the profile you used before this one (the current one is saved first).");
	}

	@Override
	public String title() {
		return "Profiles";
	}

	@Override
	public String subtitle() {
		return "A profile keeps which modules are on, their settings and keys, the HUD layout and the look.";
	}

	@Override
	public Icon icon() {
		return Icon.LAYERS;
	}

	@Override
	public void onShow() {
		rebuild();
	}

	@Override
	protected void onLayout() {
		int bx = x;
		for (Button button : List.of(create, importButton, back)) {
			int w = button.naturalWidth(ui) + 10;
			button.setBounds(bx, y, w, 16);
			bx += w + Theme.SPACE_S;
		}

		content.setBounds(x, y + 16 + Theme.SPACE_M, width, height - 16 - Theme.SPACE_M);
		rebuild();
	}

	@Override
	public void render(UiContext ui) {
		create.render(ui);
		importButton.render(ui);
		back.render(ui);
		ui.pushClip(content.x(), content.y(), content.width(), content.height());
		content.render(ui);
		ui.popClip();
	}

	private void report(String message, boolean ok) {
		feedback = message;
		feedbackColor = ok ? Theme.SUCCESS : Theme.ERROR;
	}

	// ----- actions ---------------------------------------------------------------------------

	private void promptCreate() {
		Dialog.prompt("New profile", "A copy of the current profile ('" + femboy.config().activeProfile() + "'). You stay on the current one; "
				+ "switch with Use. Names use letters, digits, - and _.", "Profile name", "", 32, "Create", name -> {
			String problem = femboy.config().createProfile(name);
			if (problem == null) {
				report("Created profile '" + name + "'.", true);
				rebuild();
			}

			return problem;
		}).allow(NAME_CHARS).open(ui);
	}

	private void promptImport() {
		Dialog.prompt("Import a profile", "Copy a shared profile (the text from Export) first, then choose a name for it here. Values this "
				+ "version does not accept are replaced by defaults and listed.", "Name for the imported profile", "", 32, "Import from clipboard", name -> {
			String clipboard = Minecraft.getInstance().keyboardHandler.getClipboard();
			ConfigurationManager.ImportResult result = femboy.config().importProfile(name, clipboard);
			if (result.error() != null) {
				return result.error();
			}

			report("Imported '" + name + "'." + (result.warnings().isEmpty() ? "" : " Replaced by defaults: " + String.join(" ", result.warnings())), true);
			rebuild();
			return null;
		}).allow(NAME_CHARS).open(ui);
	}

	private void revert() {
		String previous = femboy.config().previousProfile();
		String problem = femboy.config().revertProfile();
		report(problem == null ? "Back to profile '" + previous + "'." : problem, problem == null);
		rebuild();
	}

	private void use(String name) {
		String problem = femboy.config().switchProfile(name);
		compareWith = null;
		report(problem == null ? "Switched to profile '" + name + "'." : problem, problem == null);
		rebuild();
	}

	/** The "⋯" menu of a profile (public for automated UI tests). */
	public PopupMenu menuFor(String name) {
		ConfigurationManager config = femboy.config();
		boolean active = name.equals(config.activeProfile());
		List<PopupMenu.Item> items = new ArrayList<>();
		items.add(PopupMenu.Item.of(Icon.COPY, "Duplicate", "Create a copy named '" + copyName(name) + "'.", () -> duplicate(name)));
		items.add(PopupMenu.Item.of(Icon.CODE, "Rename...", "Give this profile another name; rules and history follow.", () -> promptRename(name)));
		items.add(PopupMenu.Item.of(Icon.SAVE, "Export", "Copy it to the clipboard to share, and save it in the exports folder.", () -> export(name)));
		items.add(PopupMenu.Item.of(Icon.INFO, "Edit description...", "A few words about what this profile is for.", () -> promptNote(name)));
		if (active) {
			items.add(new PopupMenu.Item(Icon.RESET, "Reset to defaults...", "Turn every module off and restore every setting and key.", true, true,
					() -> confirmReset(name)));
		} else {
			items.add(new PopupMenu.Item(Icon.TRASH, "Delete...", "Remove this profile (a backup copy is kept).", true, true, () -> confirmDelete(name)));
		}

		return new PopupMenu(items);
	}

	private String copyName(String name) {
		List<String> existing = femboy.config().profiles().stream().map(String::toLowerCase).toList();
		String base = name.length() > 26 ? name.substring(0, 26) : name;
		String candidate = base + "-copy";
		for (int i = 2; existing.contains(candidate.toLowerCase()); i++) {
			candidate = base + "-copy" + i;
		}

		return candidate;
	}

	private void duplicate(String name) {
		String target = copyName(name);
		String problem = femboy.config().duplicateProfile(name, target);
		report(problem == null ? "Copied '" + name + "' as '" + target + "'." : problem, problem == null);
		rebuild();
	}

	private void promptRename(String name) {
		Dialog.prompt("Rename '" + name + "'", "Automatic switching rules and the history follow the new name.", "New name", name, 32, "Rename",
				newName -> {
					String problem = femboy.config().renameProfile(name, newName);
					if (problem == null) {
						report("Renamed '" + name + "' to '" + newName + "'.", true);
						rebuild();
					}

					return problem;
				}).allow(NAME_CHARS).open(ui);
	}

	private void export(String name) {
		String text = femboy.config().exportProfile(name);
		if (text == null) {
			report("Profile '" + name + "' could not be read.", false);
			rebuild();
			return;
		}

		Minecraft.getInstance().keyboardHandler.setClipboard(text);
		Path file = femboy.config().exportProfileToFile(name);
		report("Copied '" + name + "' to the clipboard" + (file == null ? " (saving the file failed; see Settings › Developer)."
				: " and saved it as exports/" + file.getFileName() + "."), true);
		rebuild();
	}

	private void promptNote(String name) {
		Dialog.prompt("Description of '" + name + "'", "A few words shown under the profile's name, such as \"Bedwars on my server\". Leave it "
				+ "empty to show which modules it turns on instead.", "What is this profile for?", femboy.config().profileNote(name), 60, "Save", note -> {
			femboy.config().setProfileNote(name, note);
			rebuild();
			return null;
		}).open(ui);
	}

	private void confirmReset(String name) {
		Dialog.confirm("Reset '" + name + "'?", "Every module of this profile is turned off and every setting and key goes back to its default. "
				+ "Other profiles do not change. A restore point is made first (Settings › General), so this can be undone.", "Reset profile", true, () -> {
			String problem = femboy.config().createRestorePoint("Before resetting profile '" + name + "'");
			femboy.config().resetActiveProfile();
			report("Reset '" + name + "': every module is off with default settings." + (problem == null ? " A restore point was made first."
					: " (No restore point: " + problem + ")"), true);
			rebuild();
			return null;
		}).open(ui);
	}

	private void confirmDelete(String name) {
		List<String> enabled = femboy.config().enabledModules(name);
		String what = enabled == null || enabled.isEmpty() ? "The profile and all its settings disappear from the list."
				: "The profile disappears from the list, with all its settings and the " + enabled.size() + (enabled.size() == 1 ? " module" : " modules")
						+ " it turns on.";
		Dialog.confirm("Delete '" + name + "'?", what + " A backup copy of the file stays in the profiles folder.", "Delete profile", true, () -> {
			String problem = femboy.config().deleteProfile(name);
			if (problem == null) {
				femboy.config().setProfileNote(name, null);
				report("Deleted profile '" + name + "'.", true);
				rebuild();
			}

			return problem;
		}).open(ui);
	}

	// ----- content ---------------------------------------------------------------------------

	private boolean folded(String key) {
		return !Boolean.TRUE.equals(GuiState.openSections.get("profiles/" + key));
	}

	private void rebuild() {
		ConfigurationManager config = femboy.config();
		List<Widget> rows = new ArrayList<>();
		rows.add(new Feedback());

		for (String profile : config.profiles()) {
			rows.add(new ProfileCard(profile));
		}

		if (compareWith != null && config.profileExists(compareWith)) {
			addComparison(rows, config);
		}

		rows.add(new Fold("about", "What does a profile store?", null));
		if (!folded("about")) {
			rows.add(new ModulesPage.Message("Which modules are on, every module setting and key, the HUD layout, the appearance (theme, colors, "
					+ "animations) and the performance mode. Not stored per profile: the menu scale, tooltips and help mode, rules and the timeline. "
					+ "Switching saves the current profile first; modules that are off in the profile you switch to are turned off. Backups of "
					+ "everything (restore points) are in Settings › General."));
		}

		List<ConfigurationManager.HistoryEntry> history = config.history();
		if (!history.isEmpty()) {
			rows.add(new Fold("history", "Recently used", String.valueOf(Math.min(5, history.size()))));
			if (!folded("history")) {
				for (int i = 0; i < Math.min(5, history.size()); i++) {
					ConfigurationManager.HistoryEntry entry = history.get(i);
					rows.add(new ModulesPage.Message(entry.profile() + "  ·  left " + io.github.theamethyx.femboyclient.client.smart.SessionTimeline.time(entry.timeMillis())));
				}
			}
		}

		rows.add(new Fold("presets", "Ready-made presets", String.valueOf(Presets.all().size())));
		if (!folded("presets")) {
			rows.add(new ModulesPage.Message("Starting points for common play styles. Install creates a normal profile you can change; Reset puts "
					+ "the preset's settings back. Presets only turn on information and visual modules."));
			for (Presets.Preset preset : Presets.all()) {
				rows.add(new PresetRow(preset));
			}
		}

		rows.add(new Fold("rules", "Automatic switching", config.profileRules().isEmpty() ? null : String.valueOf(config.profileRules().size())));
		if (!folded("rules")) {
			rows.add(new ModulesPage.Message("Switch profiles by what you hold or where you are. Rules are checked from the top; the first match "
					+ "wins. When none matches, the current profile stays."));
			rows.addAll(SettingRow.rowsFor(List.of(femboy.options().autoProfile, femboy.options().profileDelay, femboy.options().profileMessages)));
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
		}

		content.setChildren(rows);
	}

	/** A heading that opens and closes the rows under it. */
	private final class Fold extends Widget {
		private final String key;
		private final String title;
		private final String count;

		Fold(String key, String title, String count) {
			this.key = key;
			this.title = title;
			this.count = count;
		}

		@Override
		public int preferredHeight(UiContext ui, int width) {
			return 20;
		}

		@Override
		public void render(UiContext ui) {
			boolean hot = hovered(ui);
			boolean open = !folded(key);
			ui.icon(open ? Icon.CHEVRON_DOWN : Icon.CHEVRON_RIGHT, x + 2, y + 7, 8, hot ? Theme.TEXT : Theme.TEXT_MUTED);
			ui.text(title, x + 13, y + 8, hot ? Theme.ACCENT_STRONG : Theme.TEXT);
			if (count != null) {
				ui.text(count, x + 19 + ui.textWidth(title), y + 8, Theme.TEXT_MUTED);
			}

			ui.rect(x + 2, y + height - 2, width - 4, 1, Theme.BORDER);
			if (hot) {
				ui.pointer();
			}
		}

		@Override
		public boolean mouseClicked(UiContext ui, double mouseX, double mouseY, int button) {
			if (button != 0 || !contains(mouseX, mouseY)) {
				return false;
			}

			GuiState.openSections.put("profiles/" + key, folded(key));
			rebuild();
			return true;
		}
	}

	/** One profile: name, active state, description, and its actions. */
	public final class ProfileCard extends Container {
		private final String name;
		private final boolean active;
		private final String description;
		private final Button use;
		private final Button preview;
		private final Button more;

		ProfileCard(String name) {
			this.name = name;
			this.active = name.equals(femboy.config().activeProfile());
			this.description = describe(name);
			use = add(new Button("Use", Button.Style.PRIMARY, () -> use(name)));
			use.tooltip("Save the current profile, then switch to '" + name + "'.");
			preview = add(new Button(() -> name.equals(compareWith) ? "Hide preview" : "Preview", Button.Style.GHOST, () -> {
				compareWith = name.equals(compareWith) ? null : name;
				rebuild();
			}));
			preview.tooltip("See exactly which modules, keys and settings would change if you switched to it.");
			more = add(new Button("", Button.Style.GHOST, () -> menuFor(name).openAt(ui, this.more())).icon(Icon.MORE));
			more.tooltip("More: duplicate, rename, export, description, " + (active ? "reset" : "delete") + ".");
		}

		private Button more() {
			return more;
		}

		public String name() {
			return name;
		}

		@Override
		public int preferredHeight(UiContext ui, int width) {
			return 32;
		}

		@Override
		protected void onLayout() {
			int right = x + width - 6;
			more.setBounds(right - 16, y + 8, 16, 16);
			right -= 16 + Theme.SPACE_S;
			if (!active) {
				int useWidth = use.naturalWidth(ui) + 12;
				use.setBounds(right - useWidth, y + 8, useWidth, 16);
				right -= useWidth + Theme.SPACE_S;
				int previewWidth = Math.max(50, preview.naturalWidth(ui) + 8);
				preview.setBounds(right - previewWidth, y + 8, previewWidth, 16);
			}
		}

		@Override
		public void render(UiContext ui) {
			onLayout();
			ui.roundRect(x, y, width, height, active ? Theme.SURFACE_ACTIVE : Theme.SURFACE, Theme.RADIUS_PANEL);
			int textRight = (active ? more.x() : preview.x()) - 8;
			int left = x + 10;
			if (active) {
				ui.roundRect(x, y + 6, 2, height - 12, Theme.ACCENT, 1);
				ui.icon(Icon.CHECK, left, y + 6, 9, Theme.ACCENT_STRONG);
				left += 13;
			}

			ui.textClipped(name, left, y + 6, textRight - left - (active ? 46 : 0), Theme.TEXT);
			if (active) {
				int nameWidth = Math.min(ui.textWidth(name), textRight - left - 46);
				ui.roundRect(left + nameWidth + 6, y + 5, 38, 10, Theme.withAlpha(Theme.ACCENT, 0.2f), Theme.RADIUS_CONTROL);
				ui.text("Active", left + nameWidth + 9, y + 6, Theme.ACCENT_STRONG);
			}

			ui.textScaled(ui.ellipsize(description, Math.round((textRight - x - 10) / ui.small())), x + 10, y + 19, ui.small(), Theme.TEXT_MUTED);
			if (ui.hovered(x, y, textRight - x, height)) {
				ui.tooltip(this, name + (active ? " (active)" : "") + "\n" + description);
			}

			more.render(ui);
			if (!active) {
				use.render(ui);
				preview.render(ui);
			}
		}

		@Override
		public boolean mouseClicked(UiContext ui, double mouseX, double mouseY, int button) {
			if (more.mouseClicked(ui, mouseX, mouseY, button)) {
				return true;
			}

			return !active && (use.mouseClicked(ui, mouseX, mouseY, button) || preview.mouseClicked(ui, mouseX, mouseY, button));
		}
	}

	/** The user's note, or which modules the profile turns on. */
	private String describe(String name) {
		String note = femboy.config().profileNote(name);
		if (!note.isEmpty()) {
			return note;
		}

		List<String> enabled = femboy.config().enabledModules(name);
		if (enabled == null) {
			return "Could not be read.";
		}

		if (enabled.isEmpty()) {
			return "No modules on.";
		}

		List<String> names = new ArrayList<>();
		for (String id : enabled) {
			Module module = femboy.modules().get(id);
			if (module != null) {
				names.add(module.name());
			}
		}

		return enabled.size() + (enabled.size() == 1 ? " module on: " : " modules on: ") + String.join(", ", names);
	}

	/** The preview: what switching to {@link #compareWith} would change, with buttons to apply or close. */
	private void addComparison(List<Widget> rows, ConfigurationManager config) {
		String active = config.activeProfile();
		List<io.github.theamethyx.femboyclient.core.config.ConfigDiff.Difference> diff = config.diffProfiles(active, compareWith);
		rows.add(new ConfigPage.SectionTitle("Preview: " + active + " → " + compareWith, diff == null ? "The profile could not be read."
				: diff.isEmpty() ? "No differences: switching changes nothing." : diff.size() + " difference(s). Left: now; right: after switching."));
		if (diff != null) {
			for (int i = 0; i < Math.min(40, diff.size()); i++) {
				rows.add(new DiffRow(diff.get(i)));
			}

			if (diff.size() > 40) {
				rows.add(new ModulesPage.Message((diff.size() - 40) + " more difference(s) not listed."));
			}
		}

		rows.add(new PreviewActions(compareWith));
	}

	static String describeKey(String code) {
		try {
			int key = Integer.parseInt(code);
			return key == io.github.theamethyx.femboyclient.core.module.Module.NO_KEY ? "none"
					: com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM.getOrCreate(key).getDisplayName().getString();
		} catch (NumberFormatException e) {
			return code;
		}
	}

	private static final class DiffRow extends Widget {
		private final io.github.theamethyx.femboyclient.core.config.ConfigDiff.Difference difference;

		DiffRow(io.github.theamethyx.femboyclient.core.config.ConfigDiff.Difference difference) {
			this.difference = difference;
		}

		@Override
		public int preferredHeight(UiContext ui, int width) {
			return 13;
		}

		@Override
		public void render(UiContext ui) {
			boolean key = difference.kind() == io.github.theamethyx.femboyclient.core.config.ConfigDiff.Kind.KEY;
			String left = key ? describeKey(difference.left()) : difference.left();
			String right = key ? describeKey(difference.right()) : difference.right();
			String where = difference.moduleName().isEmpty() ? "Client option" : difference.moduleName();
			ui.roundRect(x, y, width, height, Theme.SURFACE);
			int split = width / 2;
			ui.textClipped(where + ": " + difference.what(), x + 6, y + 3, split - 10, Theme.TEXT_SECONDARY);
			String change = left + "  →  " + right;
			ui.textClipped(change, x + split, y + 3, width - split - 6, Theme.TEXT);
			if (ui.hovered(x, y, width, height)) {
				ui.tooltip(this, where + ": " + difference.what() + "\nNow: " + left + "\nAfter switching: " + right);
			}
		}
	}

	private final class PreviewActions extends Container {
		PreviewActions(String target) {
			add(new Button("Switch to " + target, Button.Style.PRIMARY, () -> use(target)).icon(Icon.CHECK));
			add(new Button("Close preview", Button.Style.SECONDARY, () -> {
				compareWith = null;
				rebuild();
			}).icon(Icon.CLOSE));
		}

		@Override
		public int preferredHeight(UiContext ui, int width) {
			return 16;
		}

		@Override
		protected void onLayout() {
			int half = (width - Theme.GAP) / 2;
			children.get(0).setBounds(x, y, half, 16);
			children.get(1).setBounds(x + half + Theme.GAP, y, width - half - Theme.GAP, 16);
		}
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

	private final class PresetRow extends Container {
		private final Presets.Preset preset;
		private final Button install;
		private final Button use;
		private final Button rule;

		PresetRow(Presets.Preset preset) {
			this.preset = preset;
			install = add(new Button(() -> !exists() ? "Install" : "Reset", Button.Style.SECONDARY, this::install));
			install.tooltip(() -> exists() ? "Put this preset's original settings back into '" + preset.profile() + "' (asks first)."
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
			rule.enabledWhen(() -> exists() && preset.trigger() != null && !hasRule());
			rule.tooltip(preset.trigger() == null ? "No held item suits this profile; switch to it by hand or add your own rule below."
					: "Add an automatic switching rule: " + preset.trigger().label() + " uses this profile.");
		}

		private boolean exists() {
			return femboy.config().profileExists(preset.profile());
		}

		private boolean hasRule() {
			return preset.trigger() != null && femboy.config().profileRules().stream().anyMatch(r -> r.profile().equals(preset.profile()) && r.item() == preset.trigger());
		}

		private void install() {
			String problem;
			if (!exists()) {
				problem = femboy.config().createProfileFrom(preset.profile(), preset.modules());
				report(problem == null ? "Installed '" + preset.title() + "' as profile '" + preset.profile() + "'." : problem, problem == null);
			} else {
				Dialog.confirm("Reset '" + preset.profile() + "'?", "Its modules, settings and keys are replaced by the " + preset.title()
						+ " preset's. Your changes to this profile are lost; a restore point is made first, so this can be undone.", "Reset to preset",
						true, () -> {
					String result = femboy.config().overwriteProfile(preset.profile(), preset.modules());
					report(result == null ? "Reset '" + preset.profile() + "' to the " + preset.title() + " preset." : result, result == null);
					rebuild();
					return result;
				}).open(ui);
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
			ui.roundRect(x, y, width, height, active ? Theme.SURFACE_ACTIVE : Theme.withAlpha(Theme.SURFACE, 0.6f), Theme.RADIUS_CONTROL);
			ui.icon(exists() ? Icon.CHECK : Icon.STAR, x + 6, y + 7, 8, exists() ? Theme.ACCENT : Theme.TEXT_MUTED);
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
