package io.github.theamethyx.nova.client.gui;

import java.util.ArrayList;
import java.util.List;

import org.lwjgl.glfw.GLFW;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import io.github.theamethyx.nova.client.GlobalOptions;
import io.github.theamethyx.nova.client.NovaClient;
import io.github.theamethyx.nova.client.NovaKeys;
import io.github.theamethyx.nova.client.gui.page.ConfigPage;
import io.github.theamethyx.nova.client.gui.page.DashboardPage;
import io.github.theamethyx.nova.client.gui.page.DiagnosticsPage;
import io.github.theamethyx.nova.client.gui.page.ModulesPage;
import io.github.theamethyx.nova.client.gui.page.Page;
import io.github.theamethyx.nova.client.gui.page.ResultsPage;
import io.github.theamethyx.nova.client.gui.page.ScenariosPage;
import io.github.theamethyx.nova.client.gui.page.WorldAnalysisPage;
import io.github.theamethyx.nova.client.gui.widget.Widget;
import io.github.theamethyx.nova.core.module.ModuleCategory;

/**
 * The NOVA ClickGUI: a sidebar of pages and the active page's content.
 *
 * <p>The menu is laid out in its own "virtual" pixel space so it can use a different (always
 * whole-number, hence crisp) scale than Minecraft's GUI scale. {@code f} converts virtual pixels
 * to Minecraft GUI pixels.
 */
public class NovaScreen extends Screen {
	private static final int NAV_ITEM = 17;
	/** Smallest virtual size the layout is designed for; "Auto" scale keeps at least this much room. */
	private static final int MIN_WIDTH = 500;
	private static final int MIN_HEIGHT = 290;

	private final NovaClient nova = NovaClient.get();
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

	public NovaScreen() {
		super(Component.literal("NOVA"));
	}

	@Override
	protected void init() {
		if (ui == null) {
			ui = new UiContext(font);
			pages.add(new DashboardPage(ui, () -> pages.stream().map(Page::navLabel).toList(), this::showPage));
			pages.add(new ModulesPage(ui, "Modules", "Modules", "HUD elements, world overlays and utilities. Select a module to edit it.",
					Icon.VISUAL, ModuleCategory.HUD, ModuleCategory.VISUAL, ModuleCategory.UTILITY));
			pages.add(new WorldAnalysisPage(ui, this::openResult));
			pages.add(new ScenariosPage(ui));
			pages.add(new ResultsPage(ui));
			pages.add(new DiagnosticsPage(ui));
			pages.add(new ConfigPage(ui));
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
		GlobalOptions.MenuScale option = nova.options().menuScale.get();
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

		panelW = Math.min(vw - 16, 600);
		panelH = Math.min(vh - 16, 360);
		panelX = (vw - panelW) / 2;
		panelY = (vh - panelH) / 2;
		sidebar = panelW >= 480 ? 104 : 92;
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

	/** Shows a saved result on the Test Results page. */
	public void openResult(String recordId) {
		GuiState.selectedResult = recordId;
		for (int i = 0; i < pages.size(); i++) {
			if (pages.get(i) instanceof ResultsPage) {
				showPage(i);
				return;
			}
		}
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
		int wanted = nova.options().menuScale.get().ordinal() * 100;
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
		boolean animate = nova.options().animations.get();
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

		ui.endFrame(nova.options().tooltips.get(), vw, vh);
		graphics.pose().popMatrix();
	}

	private void renderPanel() {
		ui.roundRect(panelX - 1, panelY - 1, panelW + 2, panelH + 2, 0x60000000);
		ui.roundRect(panelX, panelY, panelW, panelH, Theme.PANEL);
		ui.outline(panelX, panelY, panelW, panelH, Theme.BORDER);
		ui.rect(panelX + 1, panelY + 1, sidebar, panelH - 2, Theme.SIDEBAR);
		ui.rect(panelX + sidebar + 1, panelY + 1, 1, panelH - 2, Theme.BORDER);
	}

	private void renderSidebar() {
		int x = panelX + 1;
		ui.icon(Icon.STAR, x + 9, panelY + 9, 10, Theme.ACCENT);
		ui.text("NOVA", x + 23, panelY + 10, Theme.TEXT);

		int top = navTop();
		float highlight = navHighlight.update(current, ui.frameSeconds(), ui.animate());
		int highlightY = top + Math.round(highlight * NAV_ITEM);
		ui.roundRect(x + 4, highlightY, sidebar - 8, NAV_ITEM - 2, Theme.ACCENT_TINT);
		ui.rect(x + 4, highlightY + 3, 2, NAV_ITEM - 8, Theme.ACCENT);

		for (int i = 0; i < pages.size(); i++) {
			Page page = pages.get(i);
			int itemY = top + i * NAV_ITEM;
			boolean hot = ui.hovered(x + 4, itemY, sidebar - 8, NAV_ITEM - 2);
			if (hot && i != current) {
				ui.roundRect(x + 4, itemY, sidebar - 8, NAV_ITEM - 2, Theme.withAlpha(Theme.SURFACE_HOVER, 0.8f));
				ui.pointer();
			}

			int color = i == current || hot ? Theme.TEXT : Theme.TEXT_SECONDARY;
			ui.icon(page.icon(), x + 10, itemY + 3, 9, i == current ? Theme.ACCENT : color);
			ui.textClipped(page.navLabel(), x + 23, itemY + 4, sidebar - 28, color);
			if (hot) {
				ui.tooltip(page, page.title() + ": " + page.subtitle());
			}
		}

		// Footer: active profile and whether restricted modules may run here.
		int footerY = panelY + panelH - 26;
		ui.rect(x + 6, footerY - 4, sidebar - 12, 1, Theme.BORDER);
		ui.icon(Icon.FOLDER, x + 8, footerY, 8, Theme.TEXT_MUTED);
		ui.textClipped(nova.config().activeProfile(), x + 20, footerY, sidebar - 26, Theme.TEXT_SECONDARY);
		boolean authorized = nova.modules().environment().isAuthorizedEnvironment();
		ui.icon(authorized ? Icon.LOCK : Icon.INFO, x + 8, footerY + 11, 8, authorized ? Theme.SUCCESS : Theme.TEXT_MUTED);
		ui.textClipped(authorized ? "Authorized" : "Restricted", x + 20, footerY + 11, sidebar - 26, authorized ? Theme.SUCCESS : Theme.TEXT_MUTED);
		if (ui.hovered(x + 4, footerY - 2, sidebar - 8, 24)) {
			ui.tooltip("footer", "Profile: " + nova.config().activeProfile() + "\nEnvironment: " + nova.modules().environment().describe()
					+ (authorized ? "" : "\nResearch modules are paused here. They run in singleplayer or on authorized test servers (Settings)."));
		}
	}

	private int navTop() {
		return panelY + 30;
	}

	private void renderHeader() {
		Page page = pages.get(current);
		int x = panelX + sidebar + Theme.PAD + 2;
		int right = panelX + panelW - Theme.PAD;
		ui.text(page.title(), x, panelY + 7, Theme.TEXT);
		boolean cut = ui.textClipped(page.subtitle(), x, panelY + 18, right - x - 14, Theme.TEXT_MUTED);
		if (cut && ui.hovered(x, panelY + 16, right - x - 14, 10)) {
			ui.tooltip(page.subtitle(), page.subtitle());
		}

		boolean closeHot = ui.hovered(right - 10, panelY + 6, 10, 10);
		ui.icon(Icon.CLOSE, right - 9, panelY + 7, 8, closeHot ? Theme.TEXT : Theme.TEXT_MUTED);
		if (closeHot) {
			ui.pointer();
			ui.tooltip("close", "Close (Esc or " + NovaKeys.openMenu.getTranslatedKeyMessage().getString() + ")");
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

		int top = navTop();
		for (int i = 0; i < pages.size(); i++) {
			if (UiContext.inside(mx, my, panelX + 5, top + i * NAV_ITEM, sidebar - 8, NAV_ITEM - 2)) {
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

		if (NovaKeys.openMenu.matches(event)) {
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
		nova.config().tick();
	}
}
