package io.github.theamethyx.femboyclient.client.gui.page;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import net.minecraft.client.Minecraft;

import io.github.theamethyx.femboyclient.client.GlobalOptions;
import io.github.theamethyx.femboyclient.client.Performance;
import io.github.theamethyx.femboyclient.client.gui.ClientScreen;
import io.github.theamethyx.femboyclient.client.gui.HudEditorScreen;
import io.github.theamethyx.femboyclient.client.gui.Icon;
import io.github.theamethyx.femboyclient.client.gui.ModuleStatus;
import io.github.theamethyx.femboyclient.client.gui.Theme;
import io.github.theamethyx.femboyclient.client.gui.UiContext;
import io.github.theamethyx.femboyclient.client.gui.widget.Button;
import io.github.theamethyx.femboyclient.client.gui.widget.Container;
import io.github.theamethyx.femboyclient.client.gui.widget.Dropdown;
import io.github.theamethyx.femboyclient.client.gui.widget.ModuleRow;
import io.github.theamethyx.femboyclient.client.gui.widget.ScrollList;
import io.github.theamethyx.femboyclient.client.gui.widget.ToggleSwitch;
import io.github.theamethyx.femboyclient.client.gui.widget.Widget;
import io.github.theamethyx.femboyclient.core.module.Module;
import io.github.theamethyx.femboyclient.core.setting.BooleanSetting;

/**
 * The start page: a short overview, not a list of everything.
 * <ul>
 * <li>a search box (the same search as everywhere: modules, settings, profiles, HUD elements, help and actions);</li>
 * <li>a getting-started tip until it is dismissed;</li>
 * <li>three small cards: the active profile, the performance mode and whether every module is fine;</li>
 * <li>favorite and recently used modules, with their switches;</li>
 * <li>shortcuts to profiles, the HUD editor, appearance and help.</li>
 * </ul>
 * "Customize Home" shows or hides each part (Settings › General › Home screen holds the same choices).
 */
public class HomePage extends Page {
	private final ClientScreen screen;
	private final ScrollList content;
	private final Button customize;

	public HomePage(UiContext ui, ClientScreen screen) {
		super(ui);
		this.screen = screen;
		content = add(new ScrollList(ui, Theme.SPACE_S, 0));
		customize = add(new Button("Customize Home", Button.Style.GHOST, () -> ui.openOverlay(new CustomizeMenu())));
		customize.icon(Icon.SLIDERS);
		customize.tooltip("Choose which parts Home shows.");
	}

	@Override
	public String title() {
		return "Home";
	}

	@Override
	public String subtitle() {
		return "Your favorites, recent modules and shortcuts. Modules are grouped in the sections on the left.";
	}

	@Override
	public Icon icon() {
		return Icon.HOME;
	}

	@Override
	public void onShow() {
		rebuild();
	}

	@Override
	protected void onLayout() {
		int customizeWidth = customize.naturalWidth(ui) + 6;
		customize.setBounds(x + width - customizeWidth, y + height - 14, customizeWidth, 14);
		content.setBounds(x, y, width, height - 18);
		rebuild();
	}

	private GlobalOptions options() {
		return femboy.options();
	}

	/** Builds the visible parts; called when the page is shown and when a part is switched on or off. */
	public void rebuild() {
		List<Widget> rows = new ArrayList<>();
		rows.add(new SearchBox());
		if (options().homeTips.get()) {
			rows.add(new Tip());
		}

		if (options().homeStatus.get()) {
			rows.add(new StatusCards());
		}

		if (options().homeFavorites.get()) {
			List<Module> favorites = femboy.quickAccess().favorites();
			rows.add(new Heading("Favorites", favorites.isEmpty() ? null : favorites.size() + ""));
			if (favorites.isEmpty()) {
				rows.add(new Hint(Icon.STAR, "No favorites yet. Open a module and click the star next to its name to keep it here."));
			} else {
				rows.add(new ModuleGrid(favorites));
			}
		}

		if (options().homeRecent.get()) {
			List<Module> favorites = femboy.quickAccess().favorites();
			List<Module> recent = femboy.quickAccess().recent().stream().filter(module -> !favorites.contains(module)).limit(6).toList();
			rows.add(new Heading("Recently used", null));
			if (recent.isEmpty()) {
				rows.add(new Hint(Icon.CLOCK, "Modules you open or switch on appear here."));
			} else {
				rows.add(new ModuleGrid(recent));
			}
		}

		if (options().homeShortcuts.get()) {
			rows.add(new Heading("Shortcuts", null));
			rows.add(new Shortcuts());
		}

		if (rows.size() == 1) {
			rows.add(new Hint(Icon.SLIDERS, "Every part of Home is hidden. Use \"Customize Home\" below to show some again."));
		}

		content.setChildren(rows);
	}

	@Override
	public void render(UiContext ui) {
		ui.pushClip(content.x(), content.y(), content.width(), content.height());
		content.render(ui);
		ui.popClip();
		customize.render(ui);
	}

	/** Module rows shown on Home, in display order (used by automated UI tests). */
	public List<ModuleRow> rows() {
		return content.descendants(ModuleRow.class);
	}

	// ----- parts -----------------------------------------------------------------------------

	/** The large search box at the top; it opens the search, with whatever is typed. */
	private final class SearchBox extends Widget {
		@Override
		public int preferredHeight(UiContext ui, int width) {
			return 22;
		}

		@Override
		public void render(UiContext ui) {
			boolean hot = hovered(ui);
			ui.roundRect(x, y, width, height, hot ? Theme.SURFACE_HOVER : Theme.SURFACE, Theme.RADIUS_PANEL);
			ui.outline(x, y, width, height, hot ? Theme.BORDER_STRONG : Theme.BORDER, Theme.RADIUS_PANEL);
			ui.icon(Icon.SEARCH, x + 8, y + 6, 10, Theme.TEXT_MUTED);
			ui.textClipped("Search modules, settings, profiles and help", x + 24, y + 7, width - 80, Theme.TEXT_MUTED);
			ui.textRight("Ctrl+K", x + width - 8, y + 7, Theme.TEXT_MUTED);
			if (hot) {
				ui.pointer();
			}
		}

		@Override
		public boolean mouseClicked(UiContext ui, double mouseX, double mouseY, int button) {
			if (!contains(mouseX, mouseY)) {
				return false;
			}

			screen.openPalette();
			return true;
		}

		@Override
		public boolean charTyped(UiContext ui, net.minecraft.client.input.CharacterEvent event) {
			return false;
		}
	}

	@Override
	public boolean charTyped(UiContext ui, net.minecraft.client.input.CharacterEvent event) {
		// Typing on Home starts a search right away.
		if (Character.isLetterOrDigit(event.codepoint())) {
			screen.openSearch(Character.toString(event.codepoint()));
			return true;
		}

		return false;
	}

	/** The getting-started tip, with a button to dismiss it for good. */
	private final class Tip extends Container {
		private static final String TEXT = "New here? Modules are grouped in the sections on the left. Click a module to see what it does and "
				+ "change its settings; its switch turns it on. Search everything with Ctrl+K. With help mode on (the i next to a module's "
				+ "name) every setting explains itself. Settings › Help has a guide and a glossary.";
		private final Button gotIt;
		private final Button guide;

		Tip() {
			gotIt = add(new Button("Got it", Button.Style.SECONDARY, () -> {
				options().homeTips.set(false);
				rebuild();
			}));
			gotIt.tooltip("Hide this tip. Settings › General › Home screen brings it back.");
			guide = add(new Button("Open the guide", Button.Style.GHOST, () -> screen.openSettings("Help")));
			guide.icon(Icon.INFO);
		}

		@Override
		public int preferredHeight(UiContext ui, int width) {
			return 12 + ui.wrappedHeight(TEXT, width - 34) + 22;
		}

		@Override
		protected void onLayout() {
			gotIt.setBounds(x + 26, y + height - 20, gotIt.naturalWidth(ui) + 8, 14);
			guide.setBounds(gotIt.x() + gotIt.width() + 4, y + height - 20, guide.naturalWidth(ui) + 8, 14);
		}

		@Override
		public void render(UiContext ui) {
			ui.roundRect(x, y, width, height, Theme.ACCENT_TINT, Theme.RADIUS_PANEL);
			ui.icon(Icon.SPARKLE, x + 9, y + 8, 10, Theme.ACCENT_STRONG);
			ui.wrapped(TEXT, x + 26, y + 8, width - 34, Theme.TEXT);
			super.render(ui);
		}
	}

	/** Profile, performance mode and status, side by side. */
	private final class StatusCards extends Container {
		private final Dropdown<GlobalOptions.PerformanceMode> mode;

		StatusCards() {
			mode = add(new Dropdown<>(() -> Arrays.asList(GlobalOptions.PerformanceMode.values()), options().performanceMode::get,
					options().performanceMode::set, GlobalOptions.PerformanceMode::label));
			mode.tooltip(options().performanceMode.description());
		}

		@Override
		public int preferredHeight(UiContext ui, int width) {
			return 38;
		}

		private int cardWidth() {
			return (width - 2 * Theme.SPACE_S) / 3;
		}

		@Override
		protected void onLayout() {
			int w = cardWidth();
			mode.setBounds(x + w + Theme.SPACE_S + 6, y + 19, w - 12, 14);
		}

		@Override
		public void render(UiContext ui) {
			int w = cardWidth();
			// Profile.
			boolean profileHot = ui.hovered(x, y, w, height);
			card(ui, x, w, profileHot);
			ui.textScaled("Profile", x + 7, y + 6, ui.small(), Theme.TEXT_MUTED);
			ui.icon(Icon.LAYERS, x + 7, y + 20, 9, Theme.ACCENT);
			ui.textClipped(femboy.config().activeProfile(), x + 20, y + 21, w - 36, Theme.TEXT);
			ui.icon(Icon.CHEVRON_DOWN, x + w - 13, y + 21, 7, Theme.TEXT_MUTED);
			if (profileHot) {
				ui.pointer();
				ui.tooltip("home-profile", "The active profile keeps which modules are on and their settings. Click to switch.");
			}

			// Performance mode.
			int px = x + w + Theme.SPACE_S;
			card(ui, px, w, false);
			ui.textScaled("Performance mode", px + 7, y + 6, ui.small(), Theme.TEXT_MUTED);

			// Status.
			int sx = x + 2 * (w + Theme.SPACE_S);
			int attention = 0;
			int on = 0;
			for (Module module : femboy.modules().all()) {
				on += module.isEnabled() ? 1 : 0;
				attention += ModuleStatus.of(module, femboy.modules()).needsAttention() ? 1 : 0;
			}

			boolean statusHot = ui.hovered(sx, y, w, height);
			card(ui, sx, w, statusHot);
			ui.textScaled("Client", sx + 7, y + 6, ui.small(), Theme.TEXT_MUTED);
			ui.icon(attention > 0 ? Icon.WARNING : Icon.CHECK, sx + 7, y + 20, 9, attention > 0 ? Theme.WARNING : Theme.SUCCESS);
			String text = attention > 0 ? attention + " need a look" : on + (on == 1 ? " module on" : " modules on");
			ui.textClipped(text, sx + 20, y + 21, w - 26, attention > 0 ? Theme.WARNING : Theme.TEXT);
			if (statusHot) {
				ui.pointer();
				ui.tooltip("home-status", (attention > 0 ? "Some modules have a problem. Click to see which." : "Every module is fine.") + "\n"
						+ Performance.summary());
			}

			mode.render(ui);
		}

		private void card(UiContext ui, int cx, int w, boolean hot) {
			ui.roundRect(cx, y, w, height, hot ? Theme.SURFACE_HOVER : Theme.SURFACE, Theme.RADIUS_PANEL);
		}

		@Override
		public boolean mouseClicked(UiContext ui, double mouseX, double mouseY, int button) {
			if (super.mouseClicked(ui, mouseX, mouseY, button)) {
				return true;
			}

			int w = cardWidth();
			if (UiContext.inside(mouseX, mouseY, x, y, w, height)) {
				screen.openProfileMenu();
				return true;
			}

			if (UiContext.inside(mouseX, mouseY, x + 2 * (w + Theme.SPACE_S), y, w, height)) {
				screen.openSettings("Developer");
				return true;
			}

			return false;
		}
	}

	/** Module rows in one or two columns, depending on the width. */
	private final class ModuleGrid extends Container {
		private final List<ModuleRow> rows = new ArrayList<>();

		ModuleGrid(List<Module> modules) {
			for (Module module : modules) {
				rows.add(add(new ModuleRow(module, femboy.modules(), () -> null, screen::openModule)));
			}
		}

		private int columns() {
			return width >= 360 ? 2 : 1;
		}

		@Override
		public int preferredHeight(UiContext ui, int width) {
			int columns = width >= 360 ? 2 : 1;
			int lines = (rows.size() + columns - 1) / columns;
			return lines * (ModuleRow.HEIGHT + 2);
		}

		@Override
		protected void onLayout() {
			int columns = columns();
			int columnWidth = (width - (columns - 1) * Theme.SPACE_S) / columns;
			for (int i = 0; i < rows.size(); i++) {
				int column = i % columns;
				int line = i / columns;
				rows.get(i).setBounds(x + column * (columnWidth + Theme.SPACE_S), y + line * (ModuleRow.HEIGHT + 2), columnWidth, ModuleRow.HEIGHT);
			}
		}

		@Override
		public void render(UiContext ui) {
			for (ModuleRow row : rows) {
				ui.roundRect(row.x(), row.y(), row.width(), row.height(), Theme.withAlpha(Theme.SURFACE, 0.6f), Theme.RADIUS_CONTROL);
			}

			super.render(ui);
		}
	}

	/** Buttons to the places people look for most. */
	private final class Shortcuts extends Container {
		private final List<Button> buttons = new ArrayList<>();

		Shortcuts() {
			buttons.add(shortcut("Profiles", Icon.LAYERS, "Create, switch, compare and back up profiles.",
					() -> screen.showPage(screen.pageIndex(page -> page instanceof ProfilesPage))));
			buttons.add(shortcut("Edit HUD", Icon.HUD, "Move, resize and hide HUD elements on a live preview.",
					() -> Minecraft.getInstance().setScreen(new HudEditorScreen(screen, femboy.hud()))));
			buttons.add(shortcut("Appearance", Icon.PALETTE, "Theme, colors, corners and animations.", () -> screen.openSettings("Appearance")));
			buttons.add(shortcut("Help", Icon.INFO, "A short guide and a glossary of terms.", () -> screen.openSettings("Help")));
			buttons.add(shortcut("Settings", Icon.SETTINGS, "Client options, backups and files.", () -> screen.openSettings("General")));
		}

		private Button shortcut(String label, Icon icon, String tooltip, Runnable action) {
			Button button = add(new Button(label, Button.Style.SECONDARY, action));
			button.icon(icon);
			button.tooltip(tooltip);
			return button;
		}

		@Override
		public int preferredHeight(UiContext ui, int width) {
			int lines = 1;
			int lineWidth = 0;
			for (Button button : buttons) {
				int w = button.naturalWidth(ui) + 10;
				if (lineWidth + w > width && lineWidth > 0) {
					lines++;
					lineWidth = 0;
				}

				lineWidth += w + Theme.SPACE_S;
			}

			return lines * 20;
		}

		@Override
		protected void onLayout() {
			int bx = x;
			int by = y;
			for (Button button : buttons) {
				int w = button.naturalWidth(ui) + 10;
				if (bx + w > x + width && bx > x) {
					bx = x;
					by += 20;
				}

				button.setBounds(bx, by, w, 16);
				bx += w + Theme.SPACE_S;
			}
		}
	}

	/** A section title with an optional count. */
	private static final class Heading extends Widget {
		private final String title;
		private final String count;

		Heading(String title, String count) {
			this.title = title;
			this.count = count;
		}

		@Override
		public int preferredHeight(UiContext ui, int width) {
			return 16;
		}

		@Override
		public void render(UiContext ui) {
			ui.text(title, x + 2, y + 6, Theme.TEXT);
			if (count != null) {
				ui.text(count, x + 8 + ui.textWidth(title), y + 6, Theme.TEXT_MUTED);
			}
		}
	}

	/** An icon and a helpful sentence where a part has nothing to show yet. */
	private static final class Hint extends Widget {
		private final Icon icon;
		private final String text;

		Hint(Icon icon, String text) {
			this.icon = icon;
			this.text = text;
		}

		@Override
		public int preferredHeight(UiContext ui, int width) {
			return ui.wrappedHeight(text, width - 30) + 10;
		}

		@Override
		public void render(UiContext ui) {
			ui.roundRect(x, y, width, height, Theme.withAlpha(Theme.SURFACE, 0.5f), Theme.RADIUS_CONTROL);
			ui.icon(icon, x + 8, y + 5, 9, Theme.TEXT_MUTED);
			ui.wrapped(text, x + 22, y + 5, width - 30, Theme.TEXT_SECONDARY);
		}
	}

	/** Which parts Home shows: one switch per part. */
	private final class CustomizeMenu extends Widget {
		private final List<BooleanSetting> parts = List.of(options().homeTips, options().homeStatus, options().homeFavorites, options().homeRecent,
				options().homeShortcuts);

		CustomizeMenu() {
			int w = 180;
			int h = parts.size() * 16 + 22;
			setBounds(customize.x() + customize.width() - w, customize.y() - h - 4, w, h);
		}

		@Override
		public void render(UiContext ui) {
			ui.shadow(x, y, width, height, Theme.RADIUS_PANEL, 1);
			ui.roundRect(x, y, width, height, Theme.SURFACE, Theme.RADIUS_PANEL);
			ui.outline(x, y, width, height, Theme.BORDER_STRONG, Theme.RADIUS_PANEL);
			ui.text("Show on Home", x + 8, y + 7, Theme.TEXT_SECONDARY);
			for (int i = 0; i < parts.size(); i++) {
				BooleanSetting part = parts.get(i);
				int rowY = y + 20 + i * 16;
				boolean hot = ui.hovered(x + 3, rowY, width - 6, 15);
				if (hot) {
					ui.roundRect(x + 3, rowY, width - 6, 15, Theme.SURFACE_HOVER, Theme.RADIUS_CONTROL);
					ui.pointer();
					ui.tooltip(part, part.description());
				}

				ui.text(part.name(), x + 8, rowY + 4, Theme.TEXT);
				int sx = x + width - ToggleSwitch.WIDTH - 8;
				int knob = ToggleSwitch.HEIGHT - 4;
				ui.roundRect(sx, rowY + 3, ToggleSwitch.WIDTH, ToggleSwitch.HEIGHT, part.get() ? Theme.ACCENT : Theme.SURFACE_ACTIVE, ToggleSwitch.HEIGHT / 2);
				ui.roundRect(part.get() ? sx + ToggleSwitch.WIDTH - knob - 2 : sx + 2, rowY + 5, knob, knob, 0xFFFFFFFF, knob / 2);
			}
		}

		@Override
		public boolean mouseClicked(UiContext ui, double mouseX, double mouseY, int button) {
			if (!contains(mouseX, mouseY)) {
				ui.closeOverlay();
				return true;
			}

			int index = (int) ((mouseY - y - 20) / 16);
			if (mouseY >= y + 20 && index >= 0 && index < parts.size()) {
				BooleanSetting part = parts.get(index);
				part.set(!part.get());
				rebuild();
			}

			return true;
		}
	}
}
