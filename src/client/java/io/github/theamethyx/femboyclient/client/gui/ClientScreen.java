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
 * The Femboy Client menu: a sidebar (Home, Combat, Automation, Movement, Visuals, Inventory, World, Profiles,
 * Settings), a header with the page title, a search field for all modules, the profile selector and the notification
 * bell, and the active page.
 *
 * <p>The menu is laid out in its own "virtual" pixel space so it can use a different (always whole-number, hence crisp)
 * scale than Minecraft's GUI scale. {@code f} converts virtual pixels to Minecraft GUI pixels.
 *
 * <p>Motion: the menu fades and slides in, page changes cross-fade, highlights glide. Animations follow the Animations
 * option and the performance mode; "Reduce motion" keeps only fades.
 */
public class ClientScreen extends Screen {
	/** Smallest virtual size the layout is designed for; "Auto" scale keeps at least this much room. */
	private static final int MIN_WIDTH = 500;
	private static final int MIN_HEIGHT = 290;
	private static final int RADIUS = 8;
	private static final int SEARCH_WIDTH = 150;

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

	public ClientScreen() {
		super(Component.literal("Femboy Client"));
	}

	@Override
	protected void init() {
		if (ui == null) {
			ui = new UiContext(font);
			home = new HomePage(ui, this::openModule);
			pages.add(home);
			addCategoryPage("Combat", "Combat information, target and weapon timing, explosions and attribute swapping.", Icon.COMBAT, ModuleCategory.COMBAT);
			addCategoryPage("Automation", "Combat automation for singleplayer and servers in your own network.", Icon.BOLT, ModuleCategory.AUTOMATION);
			addCategoryPage("Movement", "Free camera, sprinting, falls, elytra and movement helpers.", Icon.MOVEMENT, ModuleCategory.MOVEMENT);
			addCategoryPage("Visuals", "ESP with target lines, highlights, HUD panels and screen tools.", Icon.VISUAL, ModuleCategory.VISUALS,
					ModuleCategory.HUD);
			addCategoryPage("Inventory", "Item counters, refills, armor, sorting and hotbar layouts.", Icon.STORAGE, ModuleCategory.INVENTORY);
			addCategoryPage("World", "World tools, players, alerts, waypoints, trackers and the Java runner.", Icon.GLOBE, ModuleCategory.WORLD,
					ModuleCategory.TRACKERS, ModuleCategory.DEVELOPER);
			pages.add(new ProfilesPage(ui));
			pages.add(new SettingsPage(ui));
			current = Math.max(0, Math.min(pages.size() - 1, GuiState.page));
			navHighlight.snap(current);
			search = new TextField("Search modules", 40).icon(Icon.SEARCH).onChange(this::searchChanged);
			search.tooltip("Find a module by name, description or setting name; results appear on Home. Ctrl+F jumps here.");
			search.setText(GuiState.search);
			pages.get(current).onShow();
		}

		ui.closeOverlay();
		ui.focus(null);
		applyScale();
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

	private void searchChanged(String text) {
		GuiState.search = text;
		if (pages.get(current) != home && !text.isBlank()) {
			showPage(0);
		}

		home.setQuery(text);
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
		vw = Math.round(width / f);
		vh = Math.round(height / f);
		ui.setScreenSize(vw, vh);

		panelW = Math.min(vw - 16, 900);
		panelH = Math.min(vh - 16, 520);
		panelX = (vw - panelW) / 2;
		panelY = (vh - panelH) / 2;
		sidebar = panelW >= 640 ? 124 : panelW >= 480 ? 108 : 94;
		layoutPage();
	}

	private int contentX() {
		return panelX + sidebar + Theme.PAD + 4;
	}

	private void layoutPage() {
		int contentX = contentX();
		int contentY = panelY + Theme.HEADER + 8;
		pages.get(current).setBounds(contentX, contentY, panelX + panelW - Theme.PAD - 2 - contentX, panelY + panelH - Theme.PAD - 2 - contentY);
		int searchW = Math.min(SEARCH_WIDTH, Math.max(90, (panelX + panelW - contentX) / 3));
		search.setBounds(headerRight() - searchW, panelY + 9, searchW, 15);
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
		if (ui.focused() != search) {
			ui.focus(null);
		}

		current = index;
		GuiState.page = index;
		pageFade.snap(0);
		pages.get(current).onShow();
		layoutPage();
	}

	/** Shows a module on its category page, selected. */
	public void openModule(Module module) {
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

	/** Navigation labels in sidebar order (used by automated UI tests). */
	public List<String> navigation() {
		return pages.stream().map(Page::navLabel).toList();
	}

	/** {x, y, width, height} of the profile selector in the header (used by automated UI tests). */
	public int[] profileChipBounds() {
		return new int[] {profileChipX(), panelY + 9, profileChipWidth(), 15};
	}

	/** {x, y, width, height} of the notification bell (used by automated UI tests). */
	public int[] bellBounds() {
		return new int[] {bellX() - 2, panelY + 9, 15, 15};
	}

	/** The header search field (used by automated UI tests). */
	public TextField searchField() {
		return search;
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

	/** Soft accent glows and a few sparkles behind the panel: the menu's backdrop art, drawn from two small textures. */
	private void renderBackdropArt(float t) {
		ui.glow(panelX + panelW / 5, panelY + panelH / 6, Math.max(120, panelW / 3), Theme.withAlpha(Theme.ACCENT, 0.22f * t));
		ui.glow(panelX + panelW * 4 / 5, panelY + panelH * 5 / 6, Math.max(120, panelW / 3), Theme.withAlpha(Theme.ACCENT_2, 0.18f * t));
		// Fixed sparkle positions (relative to the screen), so they do not jump between frames.
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
		ui.shadow(panelX, panelY, panelW, panelH, RADIUS, 1.6f);
		ui.roundRect(panelX, panelY, panelW, panelH, Theme.PANEL, RADIUS);
		// A faint lighter band at the top of the content area gives the panel some depth.
		ui.graphics().fillGradient(panelX + sidebar + 1, panelY + RADIUS, panelX + panelW - RADIUS, panelY + 46,
				Theme.withAlpha(Theme.ACCENT, 0.06f), 0x00000000);
		ui.roundRect(panelX, panelY, sidebar + 1, panelH, Theme.SIDEBAR, RADIUS);
		ui.rect(panelX + sidebar - RADIUS + 1, panelY, RADIUS, panelH, Theme.SIDEBAR);
		ui.rect(panelX + sidebar + 1, panelY + 8, 1, panelH - 16, Theme.BORDER);
		ui.rect(panelX + sidebar + 1 + Theme.PAD, panelY + Theme.HEADER + 3, panelW - sidebar - 2 * Theme.PAD - 1, 1, Theme.withAlpha(Theme.BORDER, 0.8f));
	}

	private void renderSidebar() {
		int x = panelX + 1;
		// Brand: the logo badge, name and tagline.
		ui.logo(x + 8, panelY + 8, 20);
		ui.textClipped("Femboy Client", x + 33, panelY + 9, sidebar - 37, Theme.TEXT);
		ui.textScaled(ui.ellipsize("Play Your Way", Math.round((sidebar - 37) / 0.75f)), x + 33, panelY + 19, 0.75f, Theme.ACCENT_STRONG);

		int top = navTop();
		float highlight = navHighlight.update(current, ui.frameSeconds(), ui.motion());
		int highlightY = top + Math.round(highlight * navItem());
		ui.gradientRect(x + 5, highlightY, sidebar - 10, navItem() - 3, Theme.withAlpha(Theme.ACCENT, 0.95f), Theme.withAlpha(Theme.ACCENT_2, 0.8f), 5);

		for (int i = 0; i < pages.size(); i++) {
			Page page = pages.get(i);
			int itemY = top + i * navItem();
			boolean hot = ui.hovered(x + 5, itemY, sidebar - 10, navItem() - 3);
			if (hot && i != current) {
				ui.roundRect(x + 5, itemY, sidebar - 10, navItem() - 3, Theme.withAlpha(Theme.SURFACE_HOVER, 0.85f), 5);
				ui.pointer();
			}

			int color = i == current ? 0xFFFFFFFF : hot ? Theme.TEXT : Theme.TEXT_SECONDARY;
			int textY = itemY + (navItem() - 3 - 8) / 2;
			ui.icon(page.icon(), x + 11, textY, 9, i == current ? 0xFFFFFFFF : Theme.lerp(color, Theme.ACCENT, 0.45f));
			ui.textClipped(page.navLabel(), x + 25, textY + 1, sidebar - 32, color);
			if (hot) {
				ui.tooltip(page, page.title() + ": " + page.subtitle());
			}
		}

		// Status card: client state, enabled modules, performance mode, profile and environment.
		int cardY = panelY + panelH - 50;
		ui.roundRect(x + 5, cardY, sidebar - 10, 44, Theme.SURFACE, 6);
		long enabled = femboy.modules().all().stream().filter(Module::isEnabled).count();
		boolean errors = femboy.modules().all().stream().anyMatch(m -> m.lastError() != null);
		ui.roundRect(x + 10, cardY + 7, 5, 5, errors ? Theme.ERROR : Theme.SUCCESS, 2);
		ui.textClipped(errors ? "Module error" : "Client ready", x + 19, cardY + 6, sidebar - 28, errors ? Theme.ERROR : Theme.SUCCESS);
		ui.textScaled(ui.ellipsize(enabled == 0 ? "No modules enabled" : enabled + " modules enabled", Math.round((sidebar - 24) / 0.75f)), x + 10, cardY + 17,
				0.75f, Theme.TEXT_MUTED);
		String mode = Performance.mode().label() + " mode";
		ui.textScaled(ui.ellipsize(mode, Math.round((sidebar - 24) / 0.75f)), x + 10, cardY + 26, 0.75f, Theme.ACCENT_STRONG);
		String where = femboy.modules().environment().describe();
		ui.textScaled(ui.ellipsize(where, Math.round((sidebar - 24) / 0.75f)), x + 10, cardY + 35, 0.75f, Theme.TEXT_SECONDARY);
		if (ui.hovered(x + 5, cardY, sidebar - 10, 44)) {
			ui.tooltip("footer", "Profile: " + femboy.config().activeProfile() + "\nEnvironment: " + where + "\nEnabled modules: " + enabled
					+ "\n" + Performance.summary());
		}
	}

	/** Height of one sidebar entry: roomier when the window is tall, never less than 15. */
	private int navItem() {
		return Math.max(15, Math.min(22, (panelH - 38 - 58) / Math.max(1, pages.size())));
	}

	private int navTop() {
		return panelY + 38;
	}

	private int closeX() {
		return panelX + panelW - Theme.PAD - 12;
	}

	private int bellX() {
		return closeX() - 24;
	}

	private int profileChipWidth() {
		return Math.min(118, ui.textWidth(femboy.config().activeProfile()) + 33);
	}

	private int profileChipX() {
		return bellX() - 6 - profileChipWidth();
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
		ui.textScaled(page.title(), x, panelY + 8, 1.2f, Theme.TEXT);
		int subtitleWidth = search.x() - x - 8;
		boolean cut = ui.textClipped(page.subtitle(), x, panelY + 21, subtitleWidth, Theme.TEXT_MUTED);
		if (cut && ui.hovered(x, panelY + 19, subtitleWidth, 10)) {
			ui.tooltip(page.subtitle(), page.subtitle());
		}

		search.render(ui);

		// Profile selector: the active profile; a click opens the list.
		String profile = femboy.config().activeProfile();
		int chipX = profileChipX();
		int chipW = profileChipWidth();
		boolean chipHot = ui.hovered(chipX, panelY + 9, chipW, 15);
		ui.roundRect(chipX, panelY + 9, chipW, 15, chipHot ? Theme.SURFACE_HOVER : Theme.SURFACE, 7);
		ui.icon(Icon.LAYERS, chipX + 5, panelY + 12, 9, Theme.ACCENT);
		ui.textClipped(profile, chipX + 17, panelY + 13, chipW - 28, Theme.TEXT);
		ui.icon(Icon.CHEVRON_DOWN, chipX + chipW - 10, panelY + 13, 7, Theme.TEXT_MUTED);
		if (chipHot) {
			ui.pointer();
			ui.tooltip("profile-chip", "Active profile: " + profile + ". Click to switch or manage profiles.");
		}

		// Notification bell with the number of new messages.
		int unread = unreadNotifications();
		boolean bellHot = ui.hovered(bellX() - 2, panelY + 9, 15, 15);
		float pulse = bellPulse.update(unread > 0 ? 1 : 0, ui.frameSeconds(), ui.animate());
		ui.icon(Icon.BELL, bellX(), panelY + 11, 11, bellHot ? Theme.TEXT : Theme.lerp(Theme.TEXT_MUTED, Theme.ACCENT_STRONG, pulse));
		if (unread > 0) {
			String count = unread > 9 ? "9+" : String.valueOf(unread);
			int w = Math.max(8, ui.textWidth(count) + 4);
			ui.roundRect(bellX() + 7, panelY + 6, w, 8, Theme.ACCENT_2, 4);
			ui.textScaled(count, bellX() + 7 + (w - ui.textWidth(count) * 0.75f) / 2, panelY + 7.5f, 0.75f, 0xFFFFFFFF);
		}

		if (bellHot) {
			ui.pointer();
			ui.tooltip("bell", unread == 0 ? "Notifications" : unread + " new notification(s)");
		}

		boolean closeHot = ui.hovered(closeX() - 1, panelY + 10, 12, 12);
		ui.icon(Icon.CLOSE, closeX(), panelY + 12, 8, closeHot ? Theme.TEXT : Theme.TEXT_MUTED);
		if (closeHot) {
			ui.pointer();
			ui.tooltip("close", "Close (Esc or " + ClientKeys.openMenu.getTranslatedKeyMessage().getString() + ")");
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
			int x = panelX + panelW - Theme.PAD - w;
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
			int w = Math.max(profileChipWidth(), 140);
			setBounds(profileChipX() + profileChipWidth() - w, panelY + 27, w, names.size() * 14 + 22);
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
				Notifications.show("Profile", problem == null ? "switched to " + name : problem, problem == null ? Theme.SUCCESS : Theme.ERROR);
			}

			return true;
		}
	}

	/** The latest client messages, newest first. */
	private final class NotificationList extends Widget {
		private final List<Notifications.Toast> shown;

		NotificationList() {
			List<Notifications.Toast> all = new ArrayList<>(Notifications.history());
			java.util.Collections.reverse(all);
			shown = all.subList(0, Math.min(8, all.size()));
			int w = 220;
			setBounds(closeX() + 12 - w, panelY + 27, w, Math.max(1, shown.size()) * 22 + 10);
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
		if (UiContext.inside(mx, my, closeX() - 2, panelY + 9, 14, 14)) {
			onClose();
			return true;
		}

		if (UiContext.inside(mx, my, profileChipX(), panelY + 9, profileChipWidth(), 15)) {
			ui.openOverlay(new ProfileMenu());
			return true;
		}

		if (UiContext.inside(mx, my, bellX() - 2, panelY + 9, 15, 15)) {
			ui.openOverlay(new NotificationList());
			return true;
		}

		if (search.mouseClicked(ui, mx, my, button)) {
			return true;
		}

		int top = navTop();
		for (int i = 0; i < pages.size(); i++) {
			if (UiContext.inside(mx, my, panelX + 6, top + i * navItem(), sidebar - 10, navItem() - 3)) {
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
		if (ui.overlay() != null) {
			ui.closeOverlay();
			return true;
		}

		return pages.get(current).mouseScrolled(ui, toVirtual(mouseX), toVirtual(mouseY), vertical);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (ui.overlay() != null && event.key() == GLFW.GLFW_KEY_ESCAPE) {
			// An open list (profiles, notifications, a dropdown) closes first, whatever has the keyboard.
			ui.closeOverlay();
			return true;
		}

		Widget focused = ui.focused();
		if (focused == search && event.key() == GLFW.GLFW_KEY_ESCAPE) {
			ui.focus(null);
			return true;
		}

		if (focused != null) {
			// A focused field or key capture owns the keyboard, including Escape.
			focused.keyPressed(ui, event);
			return true;
		}

		if (ui.overlay() != null && event.key() == GLFW.GLFW_KEY_ESCAPE) {
			ui.closeOverlay();
			return true;
		}

		if (ClientKeys.openMenu.matches(event)) {
			onClose();
			return true;
		}

		if (event.hasControlDown() && event.key() == GLFW.GLFW_KEY_F) {
			ui.focus(search);
			return true;
		}

		if (event.hasControlDown() && event.key() == GLFW.GLFW_KEY_TAB) {
			showPage((current + (event.hasShiftDown() ? pages.size() - 1 : 1)) % pages.size());
			return true;
		}

		if (pages.get(current).keyPressed(ui, event)) {
			return true;
		}

		return super.keyPressed(event);
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
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
}
