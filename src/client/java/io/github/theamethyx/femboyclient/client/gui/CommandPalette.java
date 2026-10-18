package io.github.theamethyx.femboyclient.client.gui;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import org.lwjgl.glfw.GLFW;

import net.minecraft.client.Minecraft;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;

import io.github.theamethyx.femboyclient.client.FemboyClient;
import io.github.theamethyx.femboyclient.client.GlobalOptions;
import io.github.theamethyx.femboyclient.client.diag.DiagnosticReport;
import io.github.theamethyx.femboyclient.client.gui.page.Page;
import io.github.theamethyx.femboyclient.client.gui.page.SettingsPage;
import io.github.theamethyx.femboyclient.client.gui.widget.TextField;
import io.github.theamethyx.femboyclient.client.gui.widget.Widget;
import io.github.theamethyx.femboyclient.client.hud.Notifications;
import io.github.theamethyx.femboyclient.client.smart.SessionTimeline;
import io.github.theamethyx.femboyclient.core.module.Module;
import io.github.theamethyx.femboyclient.core.setting.Setting;

/**
 * The search, as a command palette (Ctrl+K or the Search field in the menu, or its own key anywhere): type to find
 * modules, settings, pages, profiles, HUD elements, help and actions, grouped by kind. Enter opens the selected result
 * (a module's settings, a page, a help topic) or runs an action; Tab (or a right-click) turns a module on or off; arrows
 * move. With nothing typed it lists a pending rule suggestion, favorites, recently used modules and common actions.
 */
public final class CommandPalette extends Widget {
	private static final int ROW = 15;
	private static final int MAX_SHOWN = 11;

	/** One result: {@code run} on Enter or click, {@code alternate} (may be null) on Tab or right-click. */
	public record Entry(Icon icon, String title, String detail, String group, Runnable run, Runnable alternate, int score) {
	}

	private final ClientScreen screen;
	private final FemboyClient femboy = FemboyClient.get();
	private final TextField field;
	private List<Entry> entries = List.of();
	private int selected;
	private int scroll;

	public CommandPalette(ClientScreen screen, UiContext ui) {
		this.screen = screen;
		this.field = new TextField("Search modules, settings, profiles, HUD, help and actions", 60).icon(Icon.SEARCH);
		field.onChange(text -> refresh());
		field.onSubmit(text -> runSelected(false));
		refresh();
		ui.focus(field);
	}

	public TextField field() {
		return field;
	}

	public List<Entry> entries() {
		return entries;
	}

	public void setQuery(String query) {
		field.setText(query);
		refresh();
	}

	private void refresh() {
		entries = build(field.text().strip().toLowerCase(Locale.ROOT));
		selected = 0;
		scroll = 0;
	}

	// ----- entries ---------------------------------------------------------------------------

	private List<Entry> build(String query) {
		List<Entry> list = new ArrayList<>();
		QuickAccess quick = femboy.quickAccess();
		var suggestion = femboy.rules().suggestion();
		if (suggestion != null) {
			add(list, query, new Entry(Icon.SPARKLE, "Apply suggestion: " + suggestion.text(), "from your rules", "Suggestion", () -> {
				String done = femboy.rules().applySuggestion();
				Notifications.post(Notifications.Category.RULE, "Suggestion applied", done, Theme.SUCCESS);
			}, femboy.rules()::dismissSuggestion, 1000), "");
		}

		if (query.isEmpty()) {
			for (Module module : quick.favorites()) list.add(moduleEntry(module, "Favorites", 0));
			for (Module module : quick.recent()) {
				if (!quick.isFavorite(module)) list.add(moduleEntry(module, "Recently used", 0));
			}
		} else {
			for (Module module : femboy.modules().all()) {
				int score = score(query, module.name(), module.description() + " " + String.join(" ", module.aliases()) + " " + module.category().label());
				if (score > 0) list.add(moduleEntry(module, "Modules", score + (quick.isFavorite(module) ? 5 : 0)));
			}

			for (Module module : femboy.modules().all()) {
				for (Setting<?> setting : module.settings().all()) {
					if (!setting.isListed()) continue;
					int score = score(query, setting.name(), setting.description());
					if (score >= 40) {
						list.add(new Entry(Icon.SLIDERS, setting.name(), module.name() + (setting.section() == null ? "" : " › " + setting.section()) + ": "
								+ setting.displayValue(), "Settings", () -> openSetting(module, setting), null, score - 20));
					}
				}
			}

			for (int i = 0; i < screen.pageCount(); i++) {
				Page page = screen.page(i);
				int index = i;
				int score = score(query, page.navLabel(), page.subtitle());
				if (score > 0) list.add(new Entry(page.icon(), "Go to " + page.navLabel(), page.subtitle(), "Pages", () -> screen.showPage(index), null, score));
			}

			for (String tab : List.of("General", "Appearance", "Performance", "Rules", "Timeline", "Help", "Developer")) {
				int score = score(query, tab, "settings");
				if (score > 0) list.add(new Entry(Icon.SETTINGS, "Settings: " + tab, "open this settings tab", "Pages", () -> openSettingsTab(tab), null, score - 5));
			}

			for (String profile : femboy.config().profiles()) {
				int score = score(query, profile, "profile switch");
				if (score > 0 && !profile.equals(femboy.config().activeProfile())) {
					list.add(new Entry(Icon.LAYERS, "Switch to profile " + profile, "the current profile is saved first", "Profiles", () -> switchProfile(profile), null, score));
				}
			}

			for (Module module : femboy.modules().all()) {
				if (module instanceof io.github.theamethyx.femboyclient.client.module.ClientHooks.HudBlock
						|| module instanceof io.github.theamethyx.femboyclient.client.module.ClientHooks.HudWidget) {
					int score = score(query, module.name(), "hud element position layout move");
					if (score >= 60 || score > 0 && (query.contains("hud") || query.contains("layout"))) {
						list.add(new Entry(Icon.HUD, module.name() + " on the HUD", module.isEnabled() ? "move, resize or hide it in the HUD editor"
								: "off: turn it on to show it", "HUD elements", () -> openHudEditor(module), null, score - 25));
					}
				}
			}

			for (HelpTopics.Topic topic : HelpTopics.all()) {
				int score = score(query, topic.title(), topic.body());
				if (score > 0) {
					String body = topic.body();
					list.add(new Entry(Icon.INFO, topic.title(), body.length() > 90 ? body.substring(0, 87) + "..." : body, "Help",
							() -> openHelp(topic), null, score - 15));
				}
			}

			for (ThemePresets.Preset preset : ThemePresets.BUILT_IN) {
				int score = score(query, preset.name(), "theme " + preset.description());
				if (score > 0) {
					list.add(new Entry(Icon.PALETTE, "Theme: " + preset.name(), preset.description(), "Appearance",
							() -> new ThemePresets(femboy.options(), femboy.config()).apply(preset.values()), null, score - 10));
				}
			}
		}

		for (Entry action : actions()) {
			add(list, query, action, action.detail());
		}

		list.sort(Comparator.comparingInt(Entry::score).reversed());
		if (query.isEmpty()) {
			// Keep the natural grouping: suggestion, favorites, recent, then actions.
			list.sort(Comparator.comparingInt(entry -> groupOrder(entry.group())));
		}

		return list;
	}

	private static int groupOrder(String group) {
		return switch (group) {
			case "Suggestion" -> 0;
			case "Favorites" -> 1;
			case "Recently used" -> 2;
			default -> 3;
		};
	}

	private void add(List<Entry> list, String query, Entry entry, String extra) {
		if (query.isEmpty()) {
			list.add(entry);
			return;
		}

		int score = score(query, entry.title(), extra + " " + entry.group());
		if (score > 0) {
			list.add(new Entry(entry.icon(), entry.title(), entry.detail(), entry.group(), entry.run(), entry.alternate(), entry.score() + score));
		}
	}

	private List<Entry> actions() {
		List<Entry> actions = new ArrayList<>();
		String previous = femboy.config().previousProfile();
		if (previous != null) {
			actions.add(new Entry(Icon.UNDO, "Back to profile " + previous, "the profile used before this one", "Actions", () -> {
				String problem = femboy.config().revertProfile();
				Notifications.post(Notifications.Category.PROFILE, "Profile", problem == null ? "back to " + previous : problem, problem == null ? Theme.SUCCESS : Theme.ERROR);
			}, null, 0));
		}

		actions.add(new Entry(Icon.HUD, "Open the HUD editor", "move, scale and hide HUD elements", "Actions",
				() -> Minecraft.getInstance().setScreen(new HudEditorScreen(screen, femboy.hud())), null, 0));
		actions.add(new Entry(Icon.LAYERS, "Manage profiles", "create, compare, rename, presets", "Actions",
				() -> screen.showPage(screen.pageIndex(page -> page instanceof io.github.theamethyx.femboyclient.client.gui.page.ProfilesPage)), null, 0));
		actions.add(new Entry(Icon.SAVE, "Save settings now", "normally saved automatically", "Actions", () -> {
			boolean ok = femboy.config().saveNow();
			Notifications.post(Notifications.Category.CONFIG, "Settings", ok ? "saved" : "saving failed (see Developer)", ok ? Theme.SUCCESS : Theme.ERROR);
		}, null, 0));
		actions.add(new Entry(Icon.CLOCK, "Create a restore point", "a copy of all settings and profiles", "Actions", () -> {
			String problem = femboy.config().createRestorePoint("From the command palette");
			Notifications.post(Notifications.Category.CONFIG, "Restore point", problem == null ? "created" : problem, problem == null ? Theme.SUCCESS : Theme.ERROR);
		}, null, 0));
		actions.add(new Entry(Icon.BOLT, "Performance mode: " + next(femboy.options().performanceMode.get()).label(), "currently "
				+ femboy.options().performanceMode.get().label(), "Actions", () -> femboy.options().performanceMode.set(next(femboy.options().performanceMode.get())), null, 0));
		actions.add(new Entry(Icon.STAR, "Add a timeline marker", "type the text after 'marker', e.g. 'marker base entrance'", "Actions", () -> {
			String text = field.text().strip();
			String note = text.toLowerCase(Locale.ROOT).startsWith("marker") ? text.substring(6).strip() : "";
			femboy.timeline().record(SessionTimeline.Kind.MARKER, note.isEmpty() ? "Marker" : note, "");
			Notifications.post(Notifications.Category.GENERAL, "Timeline", "marker added", Theme.SUCCESS);
		}, null, 0));
		actions.add(new Entry(Icon.RESULTS, "Export a diagnostic report", "to the reports folder", "Actions",
				() -> DiagnosticReport.export(result -> Notifications.post(Notifications.Category.CONFIG, "Report", result, Theme.ACCENT)), null, 0));
		actions.add(new Entry(Icon.STOP, "Turn all modules off", "emergency off", "Actions", () -> femboy.disableEverything(), null, -5));
		return actions;
	}

	private static GlobalOptions.PerformanceMode next(GlobalOptions.PerformanceMode mode) {
		GlobalOptions.PerformanceMode[] all = GlobalOptions.PerformanceMode.values();
		return all[(mode.ordinal() + 1) % all.length];
	}

	private Entry moduleEntry(Module module, String group, int score) {
		ModuleStatus status = ModuleStatus.of(module, femboy.modules());
		return new Entry(Icon.byName(module.category().icon()), module.name(), status.label() + "  ·  " + module.category().label() + "  ·  Tab: turn "
				+ (module.isEnabled() ? "off" : "on"), group, () -> screen.openModule(module), () -> toggle(module), score);
	}

	private void openHudEditor(Module module) {
		Minecraft.getInstance().setScreen(new HudEditorScreen(screen, femboy.hud()).select(module.id()));
	}

	private void openHelp(HelpTopics.Topic topic) {
		var settings = screen.openSettings("Help");
		if (settings.activeTab() instanceof io.github.theamethyx.femboyclient.client.gui.page.HelpPage help) {
			help.setQuery(topic.title());
		}
	}

	/** 0 = no match; higher = better. Name prefix beats word prefix beats substring beats description. */
	static int score(String query, String name, String extra) {
		if (query.isEmpty()) {
			return 1;
		}

		String lower = name.toLowerCase(Locale.ROOT);
		if (lower.equals(query)) return 120;
		if (lower.startsWith(query)) return 100;
		if ((" " + lower).contains(" " + query)) return 80;
		if (lower.contains(query)) return 60;
		String[] words = query.split("\\s+");
		String haystack = lower + " " + extra.toLowerCase(Locale.ROOT);
		for (String word : words) {
			if (!haystack.contains(word)) return 0;
		}

		return 30;
	}

	private void toggle(Module module) {
		femboy.modules().toggle(module);
		femboy.quickAccess().touch(module);
		Module.Availability availability = femboy.modules().availability(module);
		String state = module.isEnabled() ? availability.available() ? "on" : "on, but " + availability.reason() : "off";
		Notifications.post(module.isEnabled() && !availability.available() ? Notifications.Category.ENVIRONMENT : Notifications.Category.MODULE,
				module.name(), state, module.isEnabled() ? Theme.SUCCESS : Theme.TEXT_MUTED);
	}

	private void switchProfile(String profile) {
		String problem = femboy.config().switchProfile(profile);
		Notifications.post(Notifications.Category.PROFILE, "Profile", problem == null ? "switched to " + profile : problem, problem == null ? Theme.SUCCESS : Theme.ERROR);
	}

	private void openSetting(Module module, Setting<?> setting) {
		io.github.theamethyx.femboyclient.client.gui.widget.SettingRow.revealLater(module.id(), setting);
		screen.openModule(module);
	}

	private void openSettingsTab(String tab) {
		screen.openSettings(tab);
	}

	private void runSelected(boolean alternate) {
		if (entries.isEmpty()) {
			return;
		}

		Entry entry = entries.get(Math.clamp(selected, 0, entries.size() - 1));
		Runnable action = alternate && entry.alternate() != null ? entry.alternate() : entry.run();
		screen.uiContext().closeOverlay();
		screen.uiContext().focus(null);
		action.run();
	}

	// ----- layout and drawing ----------------------------------------------------------------

	private void layout(UiContext ui) {
		int w = Math.min(360, ui.screenWidth() - 24);
		int shown = Math.min(MAX_SHOWN, Math.max(1, entries.size()));
		int h = 30 + shown * ROW + groupsShown() * 10 + 14;
		setBounds((ui.screenWidth() - w) / 2, Math.max(12, ui.screenHeight() / 6), w, h);
		field.setBounds(x + 8, y + 8, width - 16, 16);
	}

	private int groupsShown() {
		int groups = 0;
		String last = null;
		for (int i = scroll; i < Math.min(entries.size(), scroll + MAX_SHOWN); i++) {
			if (!entries.get(i).group().equals(last)) {
				groups++;
				last = entries.get(i).group();
			}
		}

		return groups;
	}

	@Override
	public void render(UiContext ui) {
		layout(ui);
		ui.rect(0, 0, ui.screenWidth(), ui.screenHeight(), 0x50000000);
		ui.shadow(x, y, width, height, 7, 1.4f);
		ui.roundRect(x, y, width, height, Theme.PANEL, 7);
		ui.outline(x, y, width, height, Theme.BORDER_STRONG, 7);
		field.render(ui);

		int rowY = y + 30;
		String last = null;
		for (int i = scroll; i < Math.min(entries.size(), scroll + MAX_SHOWN); i++) {
			Entry entry = entries.get(i);
			if (!entry.group().equals(last)) {
				last = entry.group();
				ui.textScaled(last, x + 10, rowY + 1, 0.75f, Theme.TEXT_MUTED);
				rowY += 10;
			}

			boolean isSelected = i == selected;
			boolean hot = ui.hovered(x + 4, rowY, width - 8, ROW - 1);
			if (isSelected || hot) {
				ui.roundRect(x + 4, rowY, width - 8, ROW - 1, isSelected ? Theme.ACCENT_TINT : Theme.SURFACE_HOVER, 4);
			}

			if (hot) {
				ui.pointer();
			}

			ui.icon(entry.icon(), x + 10, rowY + 3, 8, isSelected ? Theme.ACCENT_STRONG : Theme.ACCENT);
			int titleW = Math.min(ui.textWidth(entry.title()), (width - 30) * 3 / 5);
			ui.textClipped(entry.title(), x + 23, rowY + 3, titleW, isSelected ? Theme.TEXT : Theme.TEXT_SECONDARY);
			ui.textScaled(ui.ellipsize(entry.detail(), Math.round((width - 36 - titleW) / 0.75f)), x + 29 + titleW, rowY + 4.5f, 0.75f, Theme.TEXT_MUTED);
			rowY += ROW;
		}

		if (entries.isEmpty()) {
			ui.text("Nothing found. Try fewer or other words.", x + 12, rowY + 3, Theme.TEXT_MUTED);
		}

		ui.textScaled("↑↓ choose   Enter open   Tab on/off   Esc close" + (entries.size() > MAX_SHOWN ? "   " + entries.size() + " results" : ""),
				x + 10, y + height - 11, 0.75f, Theme.TEXT_MUTED);
	}

	/** Index of the entry at a point, or -1. */
	private int entryAt(double mouseX, double mouseY) {
		int rowY = y + 30;
		String last = null;
		for (int i = scroll; i < Math.min(entries.size(), scroll + MAX_SHOWN); i++) {
			if (!entries.get(i).group().equals(last)) {
				last = entries.get(i).group();
				rowY += 10;
			}

			if (mouseY >= rowY && mouseY < rowY + ROW - 1 && mouseX >= x + 4 && mouseX < x + width - 4) {
				return i;
			}

			rowY += ROW;
		}

		return -1;
	}

	@Override
	public boolean mouseClicked(UiContext ui, double mouseX, double mouseY, int button) {
		if (!contains(mouseX, mouseY)) {
			ui.closeOverlay();
			ui.focus(null);
			return true;
		}

		if (field.contains(mouseX, mouseY)) {
			field.mouseClicked(ui, mouseX, mouseY, button);
			ui.focus(field);
			return true;
		}

		int index = entryAt(mouseX, mouseY);
		if (index >= 0) {
			selected = index;
			runSelected(button == GLFW.GLFW_MOUSE_BUTTON_RIGHT);
		}

		return true;
	}

	@Override
	public boolean mouseScrolled(UiContext ui, double mouseX, double mouseY, double amount) {
		scroll = Math.clamp(scroll - (int) Math.signum(amount), 0, Math.max(0, entries.size() - MAX_SHOWN));
		return true;
	}

	/** Keys while the palette is open; everything not handled here goes to the text field. */
	@Override
	public boolean keyPressed(UiContext ui, KeyEvent event) {
		switch (event.key()) {
			case GLFW.GLFW_KEY_ESCAPE -> {
				ui.closeOverlay();
				ui.focus(null);
			}
			case GLFW.GLFW_KEY_DOWN -> move(1);
			case GLFW.GLFW_KEY_UP -> move(-1);
			case GLFW.GLFW_KEY_PAGE_DOWN -> move(MAX_SHOWN);
			case GLFW.GLFW_KEY_PAGE_UP -> move(-MAX_SHOWN);
			case GLFW.GLFW_KEY_TAB -> runSelected(true);
			case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> runSelected(event.hasShiftDown());
			default -> {
				ui.focus(field);
				return field.keyPressed(ui, event);
			}
		}

		return true;
	}

	@Override
	public boolean charTyped(UiContext ui, CharacterEvent event) {
		ui.focus(field);
		return field.charTyped(ui, event);
	}

	private void move(int delta) {
		if (entries.isEmpty()) {
			return;
		}

		selected = Math.clamp(selected + delta, 0, entries.size() - 1);
		if (selected < scroll) scroll = selected;
		if (selected >= scroll + MAX_SHOWN) scroll = selected - MAX_SHOWN + 1;
	}
}
