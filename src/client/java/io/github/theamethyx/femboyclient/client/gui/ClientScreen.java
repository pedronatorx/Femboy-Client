package io.github.theamethyx.femboyclient.client.gui;

import java.util.ArrayList;
import java.util.List;

import org.lwjgl.glfw.GLFW;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import io.github.theamethyx.femboyclient.client.ClientKeys;
import io.github.theamethyx.femboyclient.client.FemboyClient;
import io.github.theamethyx.femboyclient.client.GlobalOptions;
import io.github.theamethyx.femboyclient.client.Performance;
import io.github.theamethyx.femboyclient.client.gui.page.HomePage;
import io.github.theamethyx.femboyclient.client.gui.page.ModulesPage;
import io.github.theamethyx.femboyclient.client.gui.page.Page;
import io.github.theamethyx.femboyclient.client.gui.page.ProfilesPage;
import io.github.theamethyx.femboyclient.client.gui.page.SettingsPage;
import io.github.theamethyx.femboyclient.client.gui.widget.TextField;
import io.github.theamethyx.femboyclient.client.gui.widget.Widget;
import io.github.theamethyx.femboyclient.client.hud.Notifications;
import io.github.theamethyx.femboyclient.core.module.Module;
import io.github.theamethyx.femboyclient.core.module.ModuleCategory;

/**
 * The Femboy Client menu: a sidebar with the module sections (Home, Combat, Movement, Visuals, Inventory, World,
 * Automation) and, set apart, Profiles and Settings; a header with the page title, one search for everything (it opens the
 * command palette), the profile selector and the notification bell; and the active page.
 *
 * <p>The menu is laid out in its own "virtual" pixel space so it can use a different (always whole-number, hence crisp)
 * scale than Minecraft's GUI scale. {@code f} converts virtual pixels to Minecraft GUI pixels.
 *
 * <p>Motion: the menu fades and slides in, page changes cross-fade, highlights glide. Animations follow the Animations
 * option and the performance mode; "Reduce motion" keeps only fades.
 *
 * <p>Keyboard: Ctrl+K or Ctrl+F search, Ctrl+Tab and Ctrl+Shift+Tab change section, Ctrl+1 to Ctrl+9 jump to a section,
 * Escape closes an open list first, then the menu.
 */
public class ClientScreen extends Screen {
	/** Smallest virtual size the layout is designed for; "Auto" scale keeps at least this much room. */
	private static final int MIN_WIDTH = 500;
	private static final int MIN_HEIGHT = 290;
	private static final int RADIUS = 8;
	private static final int SEARCH_WIDTH = 170;
	/** Pages after this index are the management pages (Profiles, Settings), drawn below a divider. */
	private static final int MODULE_PAGES = 7;

	private final FemboyClient femboy = FemboyClient.get();
	private final List<Page> pages = new ArrayList<>();
	private final Anim open = new Anim(0, 14);
	private final Anim pageFade = new Anim(1, 16);
	private final Anim navHighlight = new Anim(0, 20);
	private final Anim bellPulse = new Anim(0, 6);
	private final long openedAt = System.currentTimeMillis();
	private UiContext ui;
	private TextField search;
	private HomePage home;
	private long lastFrame;
	private float f = 1;
	private int vw;
	private int vh;
	private int appliedScale = -2;
	private int panelX, panelY, panelW, panelH, sidebar;
	private int current;

	private boolean paletteOnOpen;

	public ClientScreen() {
		super(Component.literal("Femboy Client"));
	}

	/** The menu with the command palette already open (the palette's own key). */
	public static ClientScreen withPalette() {
		ClientScreen screen = new ClientScreen();
		screen.paletteOnOpen = true;
		return screen;
	}

	/** Opens the command palette (the search) over the current page. */
	public CommandPalette openPalette() {
		CommandPalette palette = new CommandPalette(this, ui);
		ui.openOverlay(palette);
		return palette;
	}

	/** Opens the search with some text already typed. */
	public CommandPalette openSearch(String text) {
		CommandPalette palette = openPalette();
		palette.setQuery(text);
		return palette;
	}

	@Override
	protected void init() {
		if (ui == null) {
			ui = new UiContext(font);
			home = new HomePage(ui, this);
			pages.add(home);
			addCategoryPage("Combat", "Information for fights: your target, weapon timing, clicks and explosions.", Icon.COMBAT, ModuleCategory.COMBAT);
			addCategoryPage("Movement", "Free camera, sprinting, falling, elytra and other ways to move.", Icon.MOVEMENT, ModuleCategory.MOVEMENT);
			addCategoryPage("Visuals", "Highlights (ESP), HUD panels and tools that change what you see.", Icon.VISUAL, ModuleCategory.VISUALS,
					ModuleCategory.HUD);
			addCategoryPage("Inventory", "Item counts, refills, armor, sorting and hotbar layouts.", Icon.STORAGE, ModuleCategory.INVENTORY);
			addCategoryPage("World", "World tools, finding players and places, building helpers, developer tools.", Icon.GLOBE, ModuleCategory.WORLD,
					ModuleCategory.TRACKERS, ModuleCategory.DEVELOPER);
			addCategoryPage("Automation", "Modules that act for you. Only where you are allowed to use them.", Icon.BOLT, ModuleCategory.AUTOMATION);
			pages.add(new ProfilesPage(ui));
			pages.add(new SettingsPage(ui));
			current = Math.max(0, Math.min(pages.size() - 1, GuiState.page));
			navHighlight.snap(current);
			search = new TextField("Search", 40).icon(Icon.SEARCH);
			search.tooltip("Search modules, settings, profiles, HUD elements, help and actions (Ctrl+K).");
			pages.get(current).onShow();
		}

		ui.closeOverlay();
		ui.focus(null);
		applyScale();
		if (paletteOnOpen) {
			paletteOnOpen = false;
			openPalette();
		}
	}

	/** A category page; only added when at least one of its categories has modules. */
	private void addCategoryPage(String title, String subtitle, Icon icon, ModuleCategory... categories) {
		for (ModuleCategory category : categories) {
			if (!femboy.modules().byCategory(category).isEmpty()) {
				pages.add(new ModulesPage(ui, title, title, subtitle, icon, categories));
				return;
			}
		}
	}

	/** Chooses the menu's pixel scale and lays everything out in virtual pixels. */
	private void applyScale() {
		int mcScale = Math.max(1, minecraft.getWindow().getGuiScale());
		GlobalOptions.MenuScale option = femboy.options().menuScale.get();
		int physicalW = minecraft.getWindow().getWidth();
		int physicalH = minecraft.getWindow().getHeight();
		int scale = switch (option) {
			case MINECRAFT -> mcScale;
			case AUTO -> {
				int chosen = 1;
				for (int s = mcScale; s >= 1; s--) {
					if (physicalW / s >= MIN_WIDTH && physicalH / s >= MIN_HEIGHT) {
						chosen = s;
						break;
					}
				}

				yield chosen;
			}
			default -> option.pixels;
		};

		appliedScale = option.ordinal() * 100 + scale;
		f = (float) scale / mcScale;
		ui.setPixelScale(scale);
		vw = Math.round(width / f);
		vh = Math.round(height / f);
		ui.setScreenSize(vw, vh);

		panelW = Math.min(vw - 16, 900);
		panelH = Math.min(vh - 16, 520);
		panelX = (vw - panelW) / 2;
		panelY = (vh - panelH) / 2;
		sidebar = panelW >= 640 ? 118 : panelW >= 480 ? 104 : 92;
		layoutPage();
	}

	private int contentX() {
		return panelX + sidebar + Theme.SPACE_L;
	}

	private void layoutPage() {
		int contentX = contentX();
		int contentY = panelY + Theme.HEADER + 10;
		pages.get(current).setBounds(contentX, contentY, panelX + panelW - Theme.SPACE_L - contentX, panelY + panelH - Theme.SPACE_L - contentY);
		int searchW = Math.min(SEARCH_WIDTH, Math.max(90, (panelX + panelW - contentX) / 3));
		search.setBounds(headerRight() - searchW, panelY + 10, searchW, 15);
	}

	/** Whether the header shows the search field (Home has its own, larger one). */
	private boolean headerSearch() {
		return pages.get(current) != home;
	}

	/** Right edge of the search field: left of the profile chip, the bell and the close button. */
	private int headerRight() {
		return profileChipX() - 6;
	}

	public void showPage(int index) {
		if (index == current || index < 0 || index >= pages.size()) {
			return;
		}

		ui.closeOverlay();
		ui.focus(null);
		current = index;
		GuiState.page = index;
		pageFade.snap(0);
		pages.get(current).onShow();
		layoutPage();
	}

	/** Shows a module on its section page, selected. */
	public void openModule(Module module) {
		femboy.quickAccess().touch(module);
		GuiState.selectedModule = module.id();
		for (int i = 0; i < pages.size(); i++) {
			if (pages.get(i) instanceof ModulesPage modulesPage && modulesPage.categories().contains(module.category())) {
				if (i == current) {
					modulesPage.onShow();
				} else {
					showPage(i);
				}

				return;
			}
		}
	}

	/** Shows the Settings page on a tab (General, Appearance, Performance, Rules, Timeline, Help, Developer). */
	public SettingsPage openSettings(String tab) {
		showPage(pageIndex(page -> page instanceof SettingsPage));
		SettingsPage settings = (SettingsPage) pages.get(current);
		settings.showTab(tab);
		return settings;
	}

	/** Index of the first page of a type, or -1 (used by automated UI tests). */
	public int pageIndex(java.util.function.Predicate<Page> test) {
		for (int i = 0; i < pages.size(); i++) {
			if (test.test(pages.get(i))) {
				return i;
			}
		}

		return -1;
	}

	public UiContext uiContext() {
		return ui;
	}

	public Page currentPage() {
		return pages.get(current);
	}

	public int pageCount() {
		return pages.size();
	}

	public Page page(int index) {
		return pages.get(index);
	}

	/** Navigation labels in sidebar order (used by automated UI tests). */
	public List<String> navigation() {
		return pages.stream().map(Page::navLabel).toList();
	}

	/** {x, y, width, height} of the profile selector in the header (used by automated UI tests). */
	public int[] profileChipBounds() {
		return new int[] {profileChipX(), panelY + 10, profileChipWidth(), 15};
	}

	/** {x, y, width, height} of the notification bell (used by automated UI tests). */
	public int[] bellBounds() {
		return new int[] {bellX() - 2, panelY + 10, 15, 15};
	}

	/** {x, y, width, height} of the header search field, which opens the search (used by automated UI tests). */
	public int[] paletteButtonBounds() {
		return new int[] {search.x(), search.y(), search.width(), search.height()};
	}

	/** The header search field (used by automated UI tests). */
	public TextField searchField() {
		return search;
	}

	/**
	 * Checks the current layout for overlaps and things that do not fit: sidebar entries running into the status line,
	 * the header's search, profile chip, bell and close button overlapping, and the page leaving the panel. Empty when
	 * everything fits (used by automated UI tests at several window sizes and scales).
	 */
	public List<String> layoutProblems() {
		List<String> problems = new ArrayList<>();
		int lastBottom = navY(pages.size() - 1) + navItem() - 2;
		if (lastBottom > panelY + panelH - 28) {
			problems.add("sidebar entries reach the status line (" + lastBottom + " > " + (panelY + panelH - 28) + ")");
		}

		if (search.x() + search.width() > profileChipX()) {
			problems.add("search overlaps the profile chip");
		}

		if (profileChipX() + profileChipWidth() > bellX() - 2) {
			problems.add("profile chip overlaps the bell");
		}

		if (search.x() < contentX() + 40) {
			problems.add("search leaves no room for the title");
		}

		Page page = pages.get(current);
		if (page.x() < panelX + sidebar || page.x() + page.width() > panelX + panelW || page.y() + page.height() > panelY + panelH) {
			problems.add("page " + page.navLabel() + " leaves the panel");
		}

		if (page.width() < 200 || page.height() < 120) {
			problems.add("page " + page.navLabel() + " is too small (" + page.width() + "x" + page.height() + ")");
		}

		return problems;
	}

	/** Menu pixels per screen pixel and the panel size, e.g. "2x, 900x520" (for test reports). */
	public String layoutSummary() {
		return ui == null ? "?" : Math.round(f * minecraft.getWindow().getGuiScale()) + "x, panel " + panelW + "x" + panelH + " in " + vw + "x" + vh;
	}

	/** Converts a Minecraft GUI coordinate to this menu's virtual coordinate. */
	public double toVirtual(double guiCoordinate) {
		return guiCoordinate / f;
	}

	/** Converts a virtual coordinate to a Minecraft GUI coordinate (used by tests to aim clicks). */
	public double toGui(double virtualCoordinate) {
		return virtualCoordinate * f;
	}

	@Override
	public void tick() {
		int wanted = femboy.options().menuScale.get().ordinal() * 100;
		if (appliedScale / 100 * 100 != wanted) {
			applyScale();
		}

		pages.get(current).tick();
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	// ----- drawing ---------------------------------------------------------------------------

	@Override
	public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		float t = open.get();
		graphics.fillGradient(0, 0, width, height, Theme.withAlpha(Theme.BACKDROP, t), Theme.withAlpha(0xC0050409, t));
	}

	/** Soft accent glows and a few sparkles behind the panel (Appearance › Background art; off by default). */
	private void renderBackdropArt(float t) {
		if (!Theme.BACKDROP_ART) {
			return;
		}

		ui.glow(panelX + panelW / 5, panelY + panelH / 6, Math.max(120, panelW / 3), Theme.withAlpha(Theme.ACCENT, 0.18f * t));
		ui.glow(panelX + panelW * 4 / 5, panelY + panelH * 5 / 6, Math.max(120, panelW / 3), Theme.withAlpha(Theme.ACCENT_2, 0.14f * t));
		int[][] sparkles = {{7, 12, 6}, {91, 9, 5}, {12, 88, 5}, {86, 84, 7}, {50, 4, 4}, {96, 47, 4}, {3, 52, 4}};
		for (int[] s : sparkles) {
			int sx = vw * s[0] / 100;
			int sy = vh * s[1] / 100;
			if (sx > panelX - 8 && sx < panelX + panelW + 8 && sy > panelY - 8 && sy < panelY + panelH + 8) {
				continue; // never on the panel
			}

			ui.icon(Icon.SPARKLE, sx, sy, s[2], Theme.withAlpha(Theme.ACCENT_2, 0.35f * t));
		}
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		long now = System.nanoTime();
		float frameSeconds = lastFrame == 0 ? 0 : (now - lastFrame) / 1e9f;
		lastFrame = now;
		boolean animate = Performance.animations(femboy.options().animations.get());
		double rawX = minecraft.mouseHandler.xpos() * width / Math.max(1, minecraft.getWindow().getScreenWidth());
		double rawY = minecraft.mouseHandler.ypos() * height / Math.max(1, minecraft.getWindow().getScreenHeight());
		int mx = (int) Math.floor(toVirtual(rawX));
		int my = (int) Math.floor(toVirtual(rawY));

		graphics.pose().pushMatrix();
		graphics.pose().scale(f, f);
		ui.beginFrame(graphics, mx, my, frameSeconds, animate);
		ui.setReduceMotion(femboy.options().reduceMotion.get());

		float t = open.update(1, frameSeconds, animate);
		float fade = pageFade.update(1, frameSeconds, animate);
		renderBackdropArt(t);

		graphics.pose().pushMatrix();
		if (ui.motion()) {
			graphics.pose().translate(0, Math.round((1 - t) * 8));
		}

		renderPanel();
		renderSidebar();
		renderHeader();
		Page page = pages.get(current);
		page.render(ui);
		graphics.pose().popMatrix();

		if (fade < 1) {
			// Fade the newly shown page in by covering it with a fading panel-colored layer.
			graphics.nextStratum();
			ui.rect(page.x(), page.y(), page.width(), page.height(), Theme.withAlpha(Theme.PANEL, 1 - fade));
		}

		renderToasts();
		Widget overlay = ui.overlay();
		if (overlay != null) {
			graphics.nextStratum();
			ui.setRenderingOverlay(true);
			overlay.render(ui);
			ui.setRenderingOverlay(false);
		}

		ui.endFrame(femboy.options().tooltips.get(), vw, vh);
		graphics.pose().popMatrix();
	}

	private void renderPanel() {
		ui.shadow(panelX, panelY, panelW, panelH, RADIUS, 1.4f);
		ui.roundRect(panelX, panelY, panelW, panelH, Theme.PANEL, RADIUS);
		ui.roundRect(panelX, panelY, sidebar + 1, panelH, Theme.SIDEBAR, RADIUS);
		ui.rect(panelX + sidebar - RADIUS + 1, panelY, RADIUS, panelH, Theme.SIDEBAR);
		ui.rect(panelX + sidebar + 1, panelY, 1, panelH, Theme.withAlpha(Theme.BORDER, 0.7f));
	}

	/** Y of a sidebar entry: the management pages sit below a small gap. */
	private int navY(int index) {
		return navTop() + index * navItem() + (index >= MODULE_PAGES ? 9 : 0);
	}

	private void renderSidebar() {
		int x = panelX + 1;
		// Brand: the logo badge and name.
		ui.logo(x + 9, panelY + 9, 18);
		ui.textClipped("Femboy Client", x + 32, panelY + 14, sidebar - 36, Theme.TEXT);

		int itemW = sidebar - 12;
		int itemH = navItem() - 2;
		float highlight = navHighlight.update(current, ui.frameSeconds(), ui.motion());
		// The highlight glides between entries (jumping across the divider).
		int from = (int) Math.floor(highlight);
		float frac = highlight - from;
		int highlightY = Math.round(navY(from) + (navY(Math.min(pages.size() - 1, from + 1)) - navY(from)) * frac);
		ui.roundRect(x + 6, highlightY, itemW, itemH, Theme.SURFACE_ACTIVE, Theme.RADIUS_CONTROL + 1);
		ui.roundRect(x + 6, highlightY + 4, 2, itemH - 8, Theme.ACCENT, 1);

		if (pages.size() > MODULE_PAGES) {
			ui.rect(x + 12, navY(MODULE_PAGES) - 6, itemW - 12, 1, Theme.withAlpha(Theme.BORDER, 0.8f));
		}

		for (int i = 0; i < pages.size(); i++) {
			Page page = pages.get(i);
			int itemY = navY(i);
			boolean hot = ui.hovered(x + 6, itemY, itemW, itemH);
			if (hot && i != current) {
				ui.roundRect(x + 6, itemY, itemW, itemH, Theme.withAlpha(Theme.SURFACE_HOVER, 0.8f), Theme.RADIUS_CONTROL + 1);
				ui.pointer();
			}

			boolean selected = i == current;
			int color = selected ? Theme.TEXT : hot ? Theme.TEXT : Theme.TEXT_SECONDARY;
			int textY = itemY + (itemH - 8) / 2;
			int iconColor = selected ? Theme.ACCENT_STRONG : Theme.ACCENT_ICONS ? Theme.lerp(color, Theme.ACCENT, 0.3f) : color;
			ui.icon(page.icon(), x + 14, textY, 9, iconColor);
			ui.textClipped(page.navLabel(), x + 28, textY + 1, sidebar - 36, color);
			if (hot) {
				ui.tooltip(page, page.subtitle() + (i < 9 ? "\nCtrl+" + (i + 1) : ""));
			}
		}

		renderStatus(x);
	}

	/** One quiet line at the bottom of the sidebar: whether everything is fine, and the performance mode. */
	private void renderStatus(int x) {
		int attention = 0;
		int enabled = 0;
		for (Module module : femboy.modules().all()) {
			if (module.isEnabled()) {
				enabled++;
			}

			if (ModuleStatus.of(module, femboy.modules()).needsAttention()) {
				attention++;
			}
		}

		int y = panelY + panelH - 26;
		boolean hot = ui.hovered(x + 6, y - 2, sidebar - 12, 22);
		if (hot) {
			ui.roundRect(x + 6, y - 2, sidebar - 12, 22, Theme.withAlpha(Theme.SURFACE_HOVER, 0.8f), Theme.RADIUS_CONTROL);
			ui.pointer();
		}

		Icon icon = attention > 0 ? Icon.WARNING : Icon.CHECK;
		int color = attention > 0 ? Theme.WARNING : Theme.SUCCESS;
		String text = attention > 0 ? attention + (attention == 1 ? " module needs a look" : " modules need a look") : "All good";
		ui.icon(icon, x + 12, y + 1, 8, color);
		ui.textClipped(text, x + 23, y + 1, sidebar - 30, attention > 0 ? Theme.WARNING : Theme.TEXT_SECONDARY);
		String detail = enabled + " on  ·  " + Performance.mode().label();
		ui.textScaled(ui.ellipsize(detail, Math.round((sidebar - 30) / ui.small())), x + 23, y + 11, ui.small(), Theme.TEXT_MUTED);
		if (hot) {
			ui.tooltip("status", (attention > 0 ? text + ". Click to see which (Settings › Developer)." : "No module has a problem. Click for details.")
					+ "\nProfile: " + femboy.config().activeProfile() + "\nWhere: " + femboy.modules().environment().describe() + "\nModules on: " + enabled
					+ "\n" + Performance.summary());
		}
	}

	private boolean statusHit(double mx, double my) {
		return UiContext.inside(mx, my, panelX + 7, panelY + panelH - 28, sidebar - 12, 22);
	}

	/** Height of one sidebar entry: roomier when the window is tall, never less than 15. */
	private int navItem() {
		return Math.max(15, Math.min(21, (panelH - 44 - 44 - 9) / Math.max(1, pages.size())));
	}

	private int navTop() {
		return panelY + 38;
	}

	private int closeX() {
		return panelX + panelW - Theme.SPACE_L - 10;
	}

	private int bellX() {
		return closeX() - 24;
	}

	private int profileChipWidth() {
		return Math.min(118, ui.textWidth(femboy.config().activeProfile()) + 33);
	}

	private int profileChipX() {
		return bellX() - 8 - profileChipWidth();
	}

	private int unreadNotifications() {
		int unread = 0;
		for (Notifications.Toast toast : Notifications.history()) {
			if (toast.shownAt() > GuiState.notificationsSeenAt) {
				unread++;
			}
		}

		return unread;
	}

	private void renderHeader() {
		Page page = pages.get(current);
		int x = contentX();
		int titleRight = (headerSearch() ? search.x() : profileChipX()) - 10;
		ui.textScaled(ui.ellipsize(page.title(), Math.round((titleRight - x) / Theme.TEXT_TITLE)), x, panelY + 9, Theme.TEXT_TITLE, Theme.TEXT);
		boolean cut = ui.textClipped(page.subtitle(), x, panelY + 22, titleRight - x, Theme.TEXT_MUTED);
		if (cut && ui.hovered(x, panelY + 20, titleRight - x, 10)) {
			ui.tooltip(page.subtitle(), page.subtitle());
		}

		if (headerSearch()) {
			renderSearchButton();
		}

		// Profile selector: the active profile; a click opens the list.
		String profile = femboy.config().activeProfile();
		int chipX = profileChipX();
		int chipW = profileChipWidth();
		boolean chipHot = ui.hovered(chipX, panelY + 10, chipW, 15);
		ui.roundRect(chipX, panelY + 10, chipW, 15, chipHot ? Theme.SURFACE_HOVER : Theme.SURFACE, Theme.RADIUS_CONTROL + 1);
		ui.icon(Icon.LAYERS, chipX + 5, panelY + 13, 9, Theme.ACCENT);
		ui.textClipped(profile, chipX + 17, panelY + 14, chipW - 28, Theme.TEXT);
		ui.icon(Icon.CHEVRON_DOWN, chipX + chipW - 10, panelY + 14, 7, Theme.TEXT_MUTED);
		if (chipHot) {
			ui.pointer();
			ui.tooltip("profile-chip", "Active profile: " + profile + ". Click to switch or manage profiles.");
		}

		// Notification bell with the number of new messages.
		int unread = unreadNotifications();
		boolean bellHot = ui.hovered(bellX() - 2, panelY + 10, 15, 15);
		float pulse = bellPulse.update(unread > 0 ? 1 : 0, ui.frameSeconds(), ui.animate());
		ui.icon(Icon.BELL, bellX(), panelY + 12, 11, bellHot ? Theme.TEXT : Theme.lerp(Theme.TEXT_MUTED, Theme.ACCENT_STRONG, pulse));
		if (unread > 0) {
			String count = unread > 9 ? "9+" : String.valueOf(unread);
			int w = Math.max(8, ui.textWidth(count) + 4);
			ui.roundRect(bellX() + 7, panelY + 7, w, 8, Theme.ACCENT, 4);
			ui.textScaled(count, bellX() + 7 + (w - ui.textWidth(count) * 0.75f) / 2, panelY + 8.5f, 0.75f, 0xFFFFFFFF);
		}

		if (bellHot) {
			ui.pointer();
			ui.tooltip("bell", unread == 0 ? "Notifications" : unread + " new notification(s)");
		}

		boolean closeHot = ui.hovered(closeX() - 1, panelY + 11, 12, 12);
		ui.icon(Icon.CLOSE, closeX(), panelY + 13, 8, closeHot ? Theme.TEXT : Theme.TEXT_MUTED);
		if (closeHot) {
			ui.pointer();
			ui.tooltip("close", "Close (Esc or " + ClientKeys.openMenu.getTranslatedKeyMessage().getString() + ")");
		}

		ui.rect(contentX(), panelY + Theme.HEADER + 4, panelX + panelW - Theme.SPACE_L - contentX(), 1, Theme.withAlpha(Theme.BORDER, 0.6f));
	}

	/** The header search: looks like a field, opens the search (command palette) when clicked or typed into. */
	private void renderSearchButton() {
		int sx = search.x();
		int sy = search.y();
		int sw = search.width();
		boolean hot = ui.hovered(sx, sy, sw, 15);
		ui.roundRect(sx, sy, sw, 15, hot ? Theme.SURFACE_HOVER : Theme.SURFACE, Theme.RADIUS_CONTROL + 1);
		ui.outline(sx, sy, sw, 15, hot ? Theme.BORDER_STRONG : Theme.BORDER, Theme.RADIUS_CONTROL + 1);
		ui.icon(Icon.SEARCH, sx + 5, sy + 3, 9, Theme.TEXT_MUTED);
		String shortcut = "Ctrl+K";
		int shortcutWidth = Math.round(ui.textWidth(shortcut) * ui.small());
		ui.textClipped("Search", sx + 17, sy + 4, sw - 26 - shortcutWidth, Theme.TEXT_MUTED);
		ui.textScaled(shortcut, sx + sw - shortcutWidth - 5, sy + 4 + (8 - 8 * ui.small()) / 2, ui.small(), Theme.TEXT_MUTED);
		if (hot) {
			ui.pointer();
			ui.tooltip("search", "Search modules, settings, profiles, HUD elements, help and actions.");
		}
	}

	/** Messages that arrived while the menu is open, briefly, in the bottom-right corner of the panel. */
	private void renderToasts() {
		long now = System.currentTimeMillis();
		List<Notifications.Toast> recent = new ArrayList<>();
		for (Notifications.Toast toast : Notifications.history()) {
			if (toast.shownAt() >= openedAt && now - toast.shownAt() < 4000) {
				recent.add(toast);
			}
		}

		int y = panelY + panelH - 10;
		for (int i = recent.size() - 1; i >= 0 && i >= recent.size() - 3; i--) {
			Notifications.Toast toast = recent.get(i);
			long age = now - toast.shownAt();
			float alpha = Math.min(1, Math.min(age / 150f, (4000 - age) / 400f));
			String text = toast.text().isEmpty() ? toast.title() : toast.title() + ": " + toast.text();
			int w = Math.min(panelW / 2, ui.textWidth(text) + 18);
			int x = panelX + panelW - Theme.SPACE_L - w;
			y -= 18;
			int slide = ui.motion() ? Math.round((1 - Math.min(1, age / 200f)) * 12) : 0;
			ui.graphics().nextStratum();
			ui.shadow(x + slide, y, w, 15, 4, 0.8f * alpha);
			ui.roundRect(x + slide, y, w, 15, Theme.withAlpha(Theme.SURFACE_ACTIVE, 0.97f * alpha), 5);
			ui.roundRect(x + slide + 3, y + 4, 3, 7, Theme.withAlpha(toast.color(), alpha), 1);
			ui.textClipped(text, x + slide + 10, y + 4, w - 13, Theme.withAlpha(Theme.TEXT, alpha));
		}
	}

	// ----- overlays: profile list and notifications ------------------------------------------

	/** The profile list under the header chip. */
	private final class ProfileMenu extends Widget {
		private final List<String> names = femboy.config().profiles();

		ProfileMenu() {
			int w = Math.max(profileChipWidth(), 150);
			setBounds(profileChipX() + profileChipWidth() - w, panelY + 28, w, names.size() * 14 + 22);
		}

		@Override
		public void render(UiContext ui) {
			ui.shadow(x, y, width, height, 5, 1);
			ui.roundRect(x, y, width, height, Theme.SURFACE, 5);
			ui.outline(x, y, width, height, Theme.BORDER_STRONG, 5);
			for (int i = 0; i < names.size(); i++) {
				String name = names.get(i);
				int rowY = y + 3 + i * 14;
				boolean active = name.equals(femboy.config().activeProfile());
				boolean hot = ui.hovered(x + 3, rowY, width - 6, 13);
				if (hot) {
					ui.roundRect(x + 3, rowY, width - 6, 13, Theme.SURFACE_HOVER, 4);
					ui.pointer();
				}

				if (active) {
					ui.icon(Icon.CHECK, x + 7, rowY + 3, 7, Theme.ACCENT);
				}

				ui.textClipped(name, x + 18, rowY + 3, width - 24, active ? Theme.ACCENT_STRONG : Theme.TEXT);
			}

			int manageY = y + height - 17;
			ui.rect(x + 6, manageY - 2, width - 12, 1, Theme.BORDER);
			boolean hot = ui.hovered(x + 3, manageY, width - 6, 13);
			if (hot) {
				ui.roundRect(x + 3, manageY, width - 6, 13, Theme.SURFACE_HOVER, 4);
				ui.pointer();
			}

			ui.icon(Icon.SLIDERS, x + 7, manageY + 3, 7, Theme.TEXT_SECONDARY);
			ui.text("Manage profiles...", x + 18, manageY + 3, Theme.TEXT_SECONDARY);
		}

		@Override
		public boolean mouseClicked(UiContext ui, double mouseX, double mouseY, int button) {
			ui.closeOverlay();
			if (!contains(mouseX, mouseY)) {
				return true;
			}

			int index = (int) ((mouseY - y - 3) / 14);
			if (mouseY >= y + height - 17) {
				showPage(pageIndex(page -> page instanceof ProfilesPage));
			} else if (index >= 0 && index < names.size()) {
				String name = names.get(index);
				String problem = femboy.config().switchProfile(name);
				Notifications.post(Notifications.Category.PROFILE, "Profile", problem == null ? "switched to " + name : problem, problem == null ? Theme.SUCCESS : Theme.ERROR);
			}

			return true;
		}
	}

	/** Opens the profile list (what the header chip and Home's profile card do). */
	public void openProfileMenu() {
		ui.openOverlay(new ProfileMenu());
	}

	/** The latest client messages, newest first. */
	private final class NotificationList extends Widget {
		private final List<Notifications.Toast> shown;

		NotificationList() {
			List<Notifications.Toast> all = new ArrayList<>(Notifications.history());
			java.util.Collections.reverse(all);
			shown = all.subList(0, Math.min(8, all.size()));
			int w = 220;
			setBounds(closeX() + 12 - w, panelY + 28, w, Math.max(1, shown.size()) * 22 + 10);
			GuiState.notificationsSeenAt = System.currentTimeMillis();
		}

		@Override
		public void render(UiContext ui) {
			ui.shadow(x, y, width, height, 5, 1);
			ui.roundRect(x, y, width, height, Theme.SURFACE, 5);
			ui.outline(x, y, width, height, Theme.BORDER_STRONG, 5);
			if (shown.isEmpty()) {
				ui.textCentered("No notifications yet.", x + width / 2, y + 10, Theme.TEXT_MUTED);
				return;
			}

			for (int i = 0; i < shown.size(); i++) {
				Notifications.Toast toast = shown.get(i);
				int rowY = y + 5 + i * 22;
				ui.roundRect(x + 5, rowY + 3, 3, 14, toast.color(), 1);
				ui.textClipped(toast.title(), x + 12, rowY + 2, width - 60, Theme.TEXT);
				ui.textRight(ago(toast.shownAt()), x + width - 6, rowY + 2, Theme.TEXT_MUTED);
				ui.textScaled(ui.ellipsize(toast.text(), Math.round((width - 18) / 0.8f)), x + 12, rowY + 12, 0.8f, Theme.TEXT_SECONDARY);
			}
		}

		private static String ago(long millis) {
			long seconds = Math.max(0, (System.currentTimeMillis() - millis) / 1000);
			return seconds < 60 ? seconds + "s" : seconds < 3600 ? seconds / 60 + "m" : seconds / 3600 + "h";
		}

		@Override
		public boolean mouseClicked(UiContext ui, double mouseX, double mouseY, int button) {
			ui.closeOverlay();
			return true;
		}
	}

	// ----- input (all coordinates converted to virtual pixels) --------------------------------

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		double mx = toVirtual(event.x());
		double my = toVirtual(event.y());
		int button = event.button();

		Widget overlay = ui.overlay();
		if (overlay != null) {
			ui.setRenderingOverlay(true);
			overlay.mouseClicked(ui, mx, my, button);
			ui.setRenderingOverlay(false);
			return true;
		}

		Widget previousFocus = ui.focused();
		if (UiContext.inside(mx, my, closeX() - 2, panelY + 10, 14, 14)) {
			onClose();
			return true;
		}

		if (UiContext.inside(mx, my, profileChipX(), panelY + 10, profileChipWidth(), 15)) {
			openProfileMenu();
			return true;
		}

		if (UiContext.inside(mx, my, bellX() - 2, panelY + 10, 15, 15)) {
			ui.openOverlay(new NotificationList());
			return true;
		}

		if (headerSearch() && search.contains(mx, my)) {
			openPalette();
			return true;
		}

		if (statusHit(mx, my)) {
			openSettings("Developer");
			return true;
		}

		for (int i = 0; i < pages.size(); i++) {
			if (UiContext.inside(mx, my, panelX + 7, navY(i), sidebar - 12, navItem() - 2)) {
				showPage(i);
				return true;
			}
		}

		boolean handled = pages.get(current).mouseClicked(ui, mx, my, button);
		if (ui.focused() == previousFocus && previousFocus != null && !previousFocus.contains(mx, my)) {
			// Clicking anywhere else ends text entry or key capture.
			ui.focus(null);
		}

		return handled || UiContext.inside(mx, my, panelX, panelY, panelW, panelH);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		Widget captured = ui.captured();
		if (captured != null) {
			captured.mouseReleased(ui, toVirtual(event.x()), toVirtual(event.y()), event.button());
			ui.releaseCapture();
			return true;
		}

		return false;
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
		Widget captured = ui.captured();
		if (captured != null) {
			captured.mouseDragged(ui, toVirtual(event.x()), toVirtual(event.y()), event.button());
			return true;
		}

		return false;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
		Widget overlay = ui.overlay();
		double vx = toVirtual(mouseX);
		double vy = toVirtual(mouseY);
		if (overlay instanceof CommandPalette palette) {
			return palette.mouseScrolled(ui, vx, vy, vertical);
		}

		if (overlay != null) {
			// A long open list scrolls; scrolling elsewhere closes it.
			if (overlay.contains(vx, vy) && overlay.mouseScrolled(ui, vx, vy, vertical)) {
				return true;
			}

			ui.closeOverlay();
			return true;
		}

		return pages.get(current).mouseScrolled(ui, vx, vy, vertical);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (ui.overlay() instanceof CommandPalette palette) {
			palette.keyPressed(ui, event);
			return true;
		}

		Widget overlay = ui.overlay();
		if (overlay != null) {
			// An open list (profiles, notifications, a dropdown) gets keys first; Escape closes it whatever has the keyboard.
			if (overlay.keyPressed(ui, event)) {
				return true;
			}

			if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
				ui.closeOverlay();
				return true;
			}
		}

		Widget focused = ui.focused();
		if (control(event) && (event.key() == GLFW.GLFW_KEY_K || event.key() == GLFW.GLFW_KEY_P || event.key() == GLFW.GLFW_KEY_F)
				&& (focused == null || focused == search)) {
			openPalette();
			return true;
		}

		if (focused != null) {
			// A focused field or key capture owns the keyboard, including Escape.
			focused.keyPressed(ui, event);
			return true;
		}

		if (ClientKeys.openMenu.matches(event)) {
			onClose();
			return true;
		}

		if (control(event) && event.key() == GLFW.GLFW_KEY_TAB) {
			showPage((current + (event.hasShiftDown() ? pages.size() - 1 : 1)) % pages.size());
			return true;
		}

		if (control(event) && event.key() >= GLFW.GLFW_KEY_1 && event.key() <= GLFW.GLFW_KEY_9) {
			showPage(event.key() - GLFW.GLFW_KEY_1);
			return true;
		}

		if (pages.get(current).keyPressed(ui, event)) {
			return true;
		}

		return super.keyPressed(event);
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		if (ui.overlay() instanceof CommandPalette palette) {
			return palette.charTyped(ui, event);
		}

		Widget overlay = ui.overlay();
		if (overlay != null && overlay.charTyped(ui, event)) {
			return true;
		}

		Widget focused = ui.focused();
		if (focused != null) {
			return focused.charTyped(ui, event);
		}

		return pages.get(current).charTyped(ui, event);
	}

	@Override
	public void removed() {
		femboy.config().tick();
	}

	/** Ctrl (Cmd on macOS) is held: from the event, or from the window's key state when the event lacks it. */
	private boolean control(KeyEvent event) {
		var window = minecraft.getWindow();
		return event.hasControlDownWithQuirk() || com.mojang.blaze3d.platform.InputConstants.isKeyDown(window, GLFW.GLFW_KEY_LEFT_CONTROL)
				|| com.mojang.blaze3d.platform.InputConstants.isKeyDown(window, GLFW.GLFW_KEY_RIGHT_CONTROL);
	}
}
