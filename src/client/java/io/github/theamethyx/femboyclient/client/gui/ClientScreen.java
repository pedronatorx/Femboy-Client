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

import io.github.theamethyx.femboyclient.client.GlobalOptions;
import io.github.theamethyx.femboyclient.client.FemboyClient;
import io.github.theamethyx.femboyclient.client.ClientKeys;
import io.github.theamethyx.femboyclient.client.gui.page.HomePage;
import io.github.theamethyx.femboyclient.client.gui.page.ModulesPage;
import io.github.theamethyx.femboyclient.client.gui.page.Page;
import io.github.theamethyx.femboyclient.client.gui.page.ProfilesPage;
import io.github.theamethyx.femboyclient.client.gui.page.SettingsPage;
import io.github.theamethyx.femboyclient.client.gui.widget.Widget;
import io.github.theamethyx.femboyclient.core.module.Module;
import io.github.theamethyx.femboyclient.core.module.ModuleCategory;

/**
 * The Femboy Client ClickGUI: a sidebar of pages and the active page's content.
 *
 * <p>The menu is laid out in its own "virtual" pixel space so it can use a different (always
 * whole-number, hence crisp) scale than Minecraft's GUI scale. {@code f} converts virtual pixels
 * to Minecraft GUI pixels.
 */
public class ClientScreen extends Screen {
	/** Smallest virtual size the layout is designed for; "Auto" scale keeps at least this much room. */
	private static final int MIN_WIDTH = 500;
	private static final int MIN_HEIGHT = 290;

	private final FemboyClient femboy = FemboyClient.get();
	private final List<Page> pages = new ArrayList<>();
	private final Anim open = new Anim(0, 14);
	private final Anim pageFade = new Anim(1, 16);
	private final Anim navHighlight = new Anim(0, 20);
	private UiContext ui;
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
			pages.add(new HomePage(ui, this::openModule));
			for (ModuleCategory category : ModuleCategory.values()) {
				if (!femboy.modules().byCategory(category).isEmpty()) {
					// Only categories with actual modules get a sidebar entry.
					pages.add(new ModulesPage(ui, category.label(), category.label(), category.description(), Icon.byName(category.icon()), category));
				}
			}

			pages.add(new ProfilesPage(ui));
			pages.add(new SettingsPage(ui));
			current = Math.max(0, Math.min(pages.size() - 1, GuiState.page));
			navHighlight.snap(current);
			pages.get(current).onShow();
		}

		ui.closeOverlay();
		ui.focus(null);
		applyScale();
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

		panelW = Math.min(vw - 16, 880);
		panelH = Math.min(vh - 16, 500);
		panelX = (vw - panelW) / 2;
		panelY = (vh - panelH) / 2;
		sidebar = panelW >= 640 ? 118 : panelW >= 480 ? 104 : 92;
		layoutPage();
	}

	private void layoutPage() {
		int contentX = panelX + sidebar + Theme.PAD + 2;
		int contentY = panelY + Theme.HEADER + 4;
		pages.get(current).setBounds(contentX, contentY, panelX + panelW - Theme.PAD - contentX, panelY + panelH - Theme.PAD - contentY);
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

	@Override
	public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		float t = open.get();
		graphics.fillGradient(0, 0, width, height, Theme.withAlpha(Theme.BACKDROP, t), Theme.withAlpha(0xB0060608, t));
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		long now = System.nanoTime();
		float frameSeconds = lastFrame == 0 ? 0 : (now - lastFrame) / 1e9f;
		lastFrame = now;
		boolean animate = femboy.options().animations.get();
		double rawX = minecraft.mouseHandler.xpos() * width / Math.max(1, minecraft.getWindow().getScreenWidth());
		double rawY = minecraft.mouseHandler.ypos() * height / Math.max(1, minecraft.getWindow().getScreenHeight());
		int mx = (int) Math.floor(toVirtual(rawX));
		int my = (int) Math.floor(toVirtual(rawY));

		graphics.pose().pushMatrix();
		graphics.pose().scale(f, f);
		ui.beginFrame(graphics, mx, my, frameSeconds, animate);

		float t = open.update(1, frameSeconds, animate);
		float fade = pageFade.update(1, frameSeconds, animate);

		graphics.pose().pushMatrix();
		graphics.pose().translate(0, Math.round((1 - t) * 8));
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
		ui.shadow(panelX, panelY, panelW, panelH, 6, 1.5f);
		ui.roundRect(panelX, panelY, panelW, panelH, Theme.PANEL, 6);
		ui.roundRect(panelX, panelY, sidebar + 1, panelH, Theme.SIDEBAR, 6);
		ui.rect(panelX + sidebar - 5, panelY, 6, panelH, Theme.SIDEBAR);
		ui.rect(panelX + sidebar + 1, panelY + 6, 1, panelH - 12, Theme.BORDER);
	}

	private void renderSidebar() {
		int x = panelX + 1;
		// Logo: accent badge, name and tagline.
		ui.roundRect(x + 7, panelY + 7, 16, 16, Theme.ACCENT, 4);
		ui.icon(Icon.STAR, x + 11, panelY + 11, 8, 0xFFFFFFFF);
		ui.textClipped("Femboy Client", x + 28, panelY + 7, sidebar - 32, Theme.ACCENT_STRONG);
		ui.textScaled(ui.ellipsize("Play Your Way", Math.round((sidebar - 32) / 0.75f)), x + 28, panelY + 17, 0.75f, Theme.TEXT_MUTED);

		int top = navTop();
		float highlight = navHighlight.update(current, ui.frameSeconds(), ui.animate());
		int highlightY = top + Math.round(highlight * navItem());
		ui.roundRect(x + 4, highlightY, sidebar - 8, navItem() - 2, Theme.withAlpha(Theme.ACCENT, 0.85f), 4);

		for (int i = 0; i < pages.size(); i++) {
			Page page = pages.get(i);
			int itemY = top + i * navItem();
			boolean hot = ui.hovered(x + 4, itemY, sidebar - 8, navItem() - 2);
			if (hot && i != current) {
				ui.roundRect(x + 4, itemY, sidebar - 8, navItem() - 2, Theme.withAlpha(Theme.SURFACE_HOVER, 0.8f), 4);
				ui.pointer();
			}

			int color = i == current ? 0xFFFFFFFF : hot ? Theme.TEXT : Theme.TEXT_SECONDARY;
			ui.icon(page.icon(), x + 10, itemY + 3, 9, i == current ? 0xFFFFFFFF : Theme.lerp(color, Theme.ACCENT, 0.5f));
			ui.textClipped(page.navLabel(), x + 23, itemY + 4, sidebar - 28, color);
			if (hot) {
				ui.tooltip(page, page.title() + ": " + page.subtitle());
			}
		}

		// Footer card: client status, active profile and where the client is connected.
		int cardY = panelY + panelH - 44;
		ui.roundRect(x + 5, cardY, sidebar - 10, 38, Theme.SURFACE, 4);
		long enabled = femboy.modules().all().stream().filter(Module::isEnabled).count();
		boolean errors = femboy.modules().all().stream().anyMatch(m -> m.lastError() != null);
		ui.roundRect(x + 10, cardY + 6, 5, 5, errors ? Theme.ERROR : Theme.SUCCESS, 2);
		ui.textClipped(errors ? "Module error" : "Client ready", x + 19, cardY + 5, sidebar - 28, errors ? Theme.ERROR : Theme.SUCCESS);
		ui.textScaled(ui.ellipsize(enabled == 0 ? "No modules enabled" : enabled + " modules enabled", Math.round((sidebar - 24) / 0.75f)), x + 10, cardY + 16,
				0.75f, Theme.TEXT_MUTED);
		String where = femboy.modules().environment().describe();
		ui.textScaled(ui.ellipsize(femboy.config().activeProfile() + "  |  " + where, Math.round((sidebar - 24) / 0.75f)), x + 10, cardY + 26, 0.75f,
				Theme.TEXT_SECONDARY);
		if (ui.hovered(x + 5, cardY, sidebar - 10, 38)) {
			ui.tooltip("footer", "Profile: " + femboy.config().activeProfile() + "\nEnvironment: " + where + "\nEnabled modules: " + enabled);
		}
	}

	/** Height of one sidebar entry: roomier when the window is tall, never less than 15. */
	private int navItem() {
		return Math.max(15, Math.min(21, (panelH - 34 - 50) / Math.max(1, pages.size())));
	}

	private int navTop() {
		return panelY + 34;
	}

	private void renderHeader() {
		Page page = pages.get(current);
		int x = panelX + sidebar + Theme.PAD + 2;
		int right = panelX + panelW - Theme.PAD;
		ui.textScaled(page.title(), x, panelY + 5, 1.15f, Theme.TEXT);
		int subtitleWidth = right - x - ui.textWidth(femboy.config().activeProfile()) - 50;
		boolean cut = ui.textClipped(page.subtitle(), x, panelY + 18, subtitleWidth, Theme.TEXT_MUTED);
		if (cut && ui.hovered(x, panelY + 16, subtitleWidth, 10)) {
			ui.tooltip(page.subtitle(), page.subtitle());
		}

		// Profile chip: shows the active profile, opens the Profiles page.
		String profile = femboy.config().activeProfile();
		int chipWidth = ui.textWidth(profile) + 24;
		int chipX = right - 18 - chipWidth;
		boolean chipHot = ui.hovered(chipX, panelY + 5, chipWidth, 14);
		ui.roundRect(chipX, panelY + 5, chipWidth, 14, chipHot ? Theme.SURFACE_HOVER : Theme.SURFACE, 4);
		ui.icon(Icon.PLAYER, chipX + 4, panelY + 8, 8, Theme.ACCENT);
		ui.text(profile, chipX + 15, panelY + 8, Theme.TEXT);
		ui.icon(Icon.CHEVRON_DOWN, chipX + chipWidth - 9, panelY + 9, 6, Theme.TEXT_MUTED);
		if (chipHot) {
			ui.pointer();
			ui.tooltip("profile-chip", "Active profile. Click to manage profiles.");
		}

		boolean closeHot = ui.hovered(right - 10, panelY + 6, 10, 10);
		ui.icon(Icon.CLOSE, right - 9, panelY + 7, 8, closeHot ? Theme.TEXT : Theme.TEXT_MUTED);
		if (closeHot) {
			ui.pointer();
			ui.tooltip("close", "Close (Esc or " + ClientKeys.openMenu.getTranslatedKeyMessage().getString() + ")");
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
		int right = panelX + panelW - Theme.PAD;
		if (UiContext.inside(mx, my, right - 11, panelY + 5, 12, 12)) {
			onClose();
			return true;
		}

		String profile = femboy.config().activeProfile();
		int chipWidth = ui.textWidth(profile) + 24;
		if (UiContext.inside(mx, my, right - 18 - chipWidth, panelY + 5, chipWidth, 14)) {
			showPage(pageIndex(page -> page instanceof ProfilesPage));
			return true;
		}

		int top = navTop();
		for (int i = 0; i < pages.size(); i++) {
			if (UiContext.inside(mx, my, panelX + 5, top + i * navItem(), sidebar - 8, navItem() - 2)) {
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
		Widget focused = ui.focused();
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
