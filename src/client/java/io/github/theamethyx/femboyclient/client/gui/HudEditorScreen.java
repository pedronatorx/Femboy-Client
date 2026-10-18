package io.github.theamethyx.femboyclient.client.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.lwjgl.glfw.GLFW;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import io.github.theamethyx.femboyclient.client.FemboyClient;
import io.github.theamethyx.femboyclient.client.gui.widget.TextField;
import io.github.theamethyx.femboyclient.client.hud.HudLayout;
import io.github.theamethyx.femboyclient.client.hud.HudManager;
import io.github.theamethyx.femboyclient.client.hud.Notifications;
import io.github.theamethyx.femboyclient.client.module.ClientHooks;
import io.github.theamethyx.femboyclient.core.hud.HudLayouts;
import io.github.theamethyx.femboyclient.core.hud.Snapping;
import io.github.theamethyx.femboyclient.core.module.Module;

/**
 * The HUD editor. Elements are the HUD blocks and widgets of running modules, drawn live by the HUD underneath.
 *
 * <ul>
 *   <li>Drag an element to move it; it snaps to screen edges, the center, other elements and (with the grid on) the grid,
 *       and guide lines show what it snapped to. Hold Shift to place freely. Arrow keys nudge (Shift: 10 pixels).</li>
 *   <li>Select an element to change its scale, opacity, when it shows, or hide it; the mouse wheel scales the element under
 *       the mouse (Shift + wheel: opacity).</li>
 *   <li>The elements list turns HUD modules on and off. Hidden elements are drawn faintly while the editor is open.</li>
 *   <li>Several layouts can be kept and switched; they can be copied to and from the clipboard. Every change can be undone
 *       (Ctrl+Z, Ctrl+Y).</li>
 * </ul>
 *
 * <p>Positions are anchored to the nearest screen edge or center, so they hold at other window sizes and GUI scales.
 */
public final class HudEditorScreen extends Screen {
	private static final int TOOLBAR_H = 18;
	private static final int PANEL_W = 128;
	private static final int ROW_H = 13;
	private static final int SNAP = 4;
	private static final int MARGIN = 4;
	private static final int GRID = 8;

	private final Screen parent;
	private final HudManager hud;
	private final HudLayout layout;
	private final FemboyClient femboy = FemboyClient.get();
	private UiContext ui;
	private String selected;
	private String dragging;
	private int grabX;
	private int grabY;
	private boolean dragMoved;
	private List<Integer> guidesX = List.of();
	private List<Integer> guidesY = List.of();
	private boolean showGrid;
	private boolean snapping = true;
	private boolean showPanel = true;
	private boolean layoutMenu;
	private int panelScroll;
	/** Text entry for naming a layout: non-null while open. */
	private TextField nameField;
	private boolean renaming;
	private String message;
	private int messageColor;
	private long messageAt;
	private final List<Control> controls = new ArrayList<>();

	public HudEditorScreen(Screen parent, HudManager hud) {
		super(Component.literal("HUD editor"));
		this.parent = parent;
		this.hud = hud;
		this.layout = hud.layout();
	}

	@Override
	protected void init() {
		if (ui == null) {
			ui = new UiContext(font);
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	/** A clickable area of the toolbar or inspector, rebuilt every frame. */
	private record Control(int x, int y, int width, int height, Runnable action) {
		boolean contains(double mx, double my) {
			return mx >= x && my >= y && mx < x + width && my < y + height;
		}
	}

	private void say(String text, boolean ok) {
		message = text;
		messageColor = ok ? Theme.SUCCESS : Theme.ERROR;
		messageAt = System.currentTimeMillis();
	}

	// ----- drawing ---------------------------------------------------------------------------

	@Override
	public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		graphics.fill(0, 0, width, height, 0x28000000);
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		super.render(graphics, mouseX, mouseY, partialTick);
		ui.setScreenSize(width, height);
		ui.beginFrame(graphics, mouseX, mouseY, 0, false);
		controls.clear();

		if (showGrid) {
			drawGrid(graphics);
		}

		drawSafeArea(graphics);
		List<HudManager.Placed> placed = hud.placed();
		for (HudManager.Placed block : placed) {
			drawElementFrame(graphics, block, placed, mouseX, mouseY);
		}

		for (int gx : guidesX) {
			graphics.fill(gx, 0, gx + 1, height, Theme.withAlpha(Theme.ACCENT_2, 0.9f));
		}

		for (int gy : guidesY) {
			graphics.fill(0, gy, width, gy + 1, Theme.withAlpha(Theme.ACCENT_2, 0.9f));
		}

		graphics.nextStratum();
		drawToolbar(mouseX, mouseY);
		if (showPanel) {
			drawPanel(mouseX, mouseY);
		}

		HudManager.Placed current = find(selected);
		if (current != null && dragging == null) {
			drawInspector(current, mouseX, mouseY);
		}

		if (layoutMenu) {
			graphics.nextStratum();
			drawLayoutMenu(mouseX, mouseY);
		}

		if (nameField != null) {
			graphics.nextStratum();
			drawNameDialog();
		}

		if (message != null && System.currentTimeMillis() - messageAt < 4000) {
			int w = Math.min(width - 20, ui.textWidth(message) + 16);
			int x = (width - w) / 2;
			int y = height - 40;
			ui.roundRect(x, y, w, 14, Theme.withAlpha(Theme.SURFACE_ACTIVE, 0.95f), 4);
			ui.rect(x + 3, y + 3, 2, 8, messageColor);
			ui.textClipped(message, x + 9, y + 3, w - 12, Theme.TEXT);
		}

		ui.endFrame(femboy.options().tooltips.get(), width, height);
	}

	private void drawGrid(GuiGraphics graphics) {
		int color = 0x18FFFFFF;
		for (int x = 0; x < width; x += GRID) {
			graphics.fill(x, 0, x + 1, height, color);
		}

		for (int y = 0; y < height; y += GRID) {
			graphics.fill(0, y, width, y + 1, color);
		}
	}

	/** The screen margin, the center lines and the hotbar area, which HUD elements should keep clear of. */
	private void drawSafeArea(GuiGraphics graphics) {
		int edge = Theme.withAlpha(Theme.ACCENT, 0.25f);
		graphics.fill(MARGIN, MARGIN, width - MARGIN, MARGIN + 1, edge);
		graphics.fill(MARGIN, height - MARGIN - 1, width - MARGIN, height - MARGIN, edge);
		graphics.fill(MARGIN, MARGIN, MARGIN + 1, height - MARGIN, edge);
		graphics.fill(width - MARGIN - 1, MARGIN, width - MARGIN, height - MARGIN, edge);
		graphics.fill(width / 2, 0, width / 2 + 1, height, 0x14FFFFFF);
		graphics.fill(0, height / 2, width, height / 2 + 1, 0x14FFFFFF);
		// Hotbar (182 wide) with the health and food rows above it.
		int hotbarX = width / 2 - 91;
		graphics.fill(hotbarX, height - 40, hotbarX + 182, height, 0x16FFFFFF);
	}

	private void drawElementFrame(GuiGraphics graphics, HudManager.Placed block, List<HudManager.Placed> all, int mouseX, int mouseY) {
		boolean hot = inside(block, mouseX, mouseY) && overCanvas(mouseX, mouseY);
		boolean isSelected = block.moduleId().equals(selected);
		boolean overlaps = false;
		Snapping.Box box = box(block);
		for (HudManager.Placed other : all) {
			if (other != block && !other.hidden() && !block.hidden() && Snapping.overlap(box, box(other))) {
				overlaps = true;
				break;
			}
		}

		int color = isSelected || block.moduleId().equals(dragging) ? Theme.ACCENT_STRONG : overlaps ? Theme.WARNING
				: hot ? Theme.ACCENT : Theme.withAlpha(Theme.ACCENT, 0.45f);
		graphics.renderOutline(block.x() - 1, block.y() - 1, block.width() + 2, block.height() + 2, color);
		if (isSelected) {
			graphics.fill(block.x(), block.y(), block.x() + block.width(), block.y() + block.height(), Theme.withAlpha(Theme.ACCENT, 0.12f));
		}

		if (hot || isSelected || block.moduleId().equals(dragging)) {
			String label = block.name() + (block.hidden() ? "  (hidden)" : "") + (overlaps ? "  (overlaps)" : "");
			int labelY = block.y() >= 12 ? block.y() - 11 : block.y() + block.height() + 2;
			int w = ui.textWidth(label) + 6;
			ui.rect(block.x() - 1, labelY - 1, w, 10, Theme.withAlpha(Theme.SURFACE, 0.9f));
			ui.text(label, block.x() + 2, labelY, overlaps ? Theme.WARNING : Theme.TEXT);
			if (hot) {
				ui.pointer();
			}
		}
	}

	private static Snapping.Box box(HudManager.Placed block) {
		return new Snapping.Box(block.x(), block.y(), block.width(), block.height());
	}

	private static boolean inside(HudManager.Placed block, double x, double y) {
		return x >= block.x() && x < block.x() + block.width() && y >= block.y() && y < block.y() + block.height();
	}

	/** Toolbar button: an icon (and optional text), highlighted when {@code on}. */
	private int toolButton(int x, int y, Icon icon, String text, boolean on, boolean enabled, String tooltip, int mouseX, int mouseY, Runnable action) {
		int w = text == null ? 16 : ui.textWidth(text) + 22;
		boolean hot = enabled && mouseX >= x && mouseY >= y && mouseX < x + w && mouseY < y + 14;
		if (on || hot) {
			ui.roundRect(x, y, w, 14, on ? Theme.ACCENT_TINT : Theme.SURFACE_HOVER, 4);
		}

		int color = !enabled ? Theme.TEXT_MUTED : on ? Theme.ACCENT_STRONG : hot ? Theme.TEXT : Theme.TEXT_SECONDARY;
		ui.icon(icon, x + 4, y + 3, 8, color);
		if (text != null) {
			ui.text(text, x + 16, y + 3, color);
		}

		if (hot) {
			ui.pointer();
			ui.tooltip(tooltip, tooltip);
		}

		if (enabled) {
			controls.add(new Control(x, y, w, 14, action));
		}

		return x + w + 2;
	}

	private int toolbarWidth() {
		return Math.min(width - 8, 360);
	}

	private void drawToolbar(int mouseX, int mouseY) {
		int w = toolbarWidth();
		int x = (width - w) / 2;
		int y = 3;
		ui.shadow(x, y, w, TOOLBAR_H, 5, 0.6f);
		ui.roundRect(x, y, w, TOOLBAR_H, Theme.withAlpha(Theme.PANEL, 0.96f), 5);
		int cx = x + 2;
		String name = layout.layouts().activeName();
		String layoutLabel = ui.ellipsize(name, 70);
		cx = toolButton(cx, y + 2, Icon.LAYERS, layoutLabel + " ▾", layoutMenu, true, "Layouts: switch, create, rename, delete, import and export.",
				mouseX, mouseY, () -> layoutMenu = !layoutMenu);
		cx = toolButton(cx, y + 2, Icon.UNDO, null, false, layout.canUndo(), "Undo (Ctrl+Z)", mouseX, mouseY, this::undo);
		cx = toolButton(cx, y + 2, Icon.REDO, null, false, layout.canRedo(), "Redo (Ctrl+Y)", mouseX, mouseY, this::redo);
		cx = toolButton(cx, y + 2, Icon.GRID, null, showGrid, true, "Grid (G): show an 8-pixel grid and snap to it.", mouseX, mouseY,
				() -> showGrid = !showGrid);
		cx = toolButton(cx, y + 2, Icon.TARGET, null, snapping, true, "Snapping: align to edges, the center and other elements. Hold Shift "
				+ "while dragging to place freely.", mouseX, mouseY, () -> snapping = !snapping);
		cx = toolButton(cx, y + 2, Icon.SLIDERS, null, showPanel, true, "Elements list (Tab)", mouseX, mouseY, () -> showPanel = !showPanel);
		cx = toolButton(cx, y + 2, Icon.RESET, null, false, true, "Reset this layout: every element back in its corner, normal size, "
				+ "visible (R). Can be undone.", mouseX, mouseY, this::resetAll);
		int doneX = x + w - 2 - (ui.textWidth("Done") + 22);
		if (doneX > cx) {
			toolButton(doneX, y + 2, Icon.CHECK, "Done", false, true, "Close the editor (Esc). Changes are saved as you make them.", mouseX, mouseY,
					this::onClose);
		}

		if (hud.placed().isEmpty()) {
			String hint = "No HUD elements are showing. Turn some on in the list on the left.";
			int hw = ui.textWidth(hint) + 12;
			ui.roundRect((width - hw) / 2, y + TOOLBAR_H + 4, hw, 13, Theme.withAlpha(Theme.SURFACE, 0.92f), 4);
			ui.textCentered(hint, width / 2, y + TOOLBAR_H + 7, Theme.TEXT_SECONDARY);
		}
	}

	/** Modules that can show something on the HUD. */
	private List<Module> hudModules() {
		List<Module> list = new ArrayList<>();
		for (Module module : femboy.modules().all()) {
			if (module instanceof ClientHooks.HudBlock || module instanceof ClientHooks.HudWidget) {
				list.add(module);
			}
		}

		return list;
	}

	private int panelTop() {
		return TOOLBAR_H + 10;
	}

	private int panelHeight() {
		return Math.max(60, height - panelTop() - 50);
	}

	private void drawPanel(int mouseX, int mouseY) {
		int x = 4;
		int y = panelTop();
		int h = panelHeight();
		ui.shadow(x, y, PANEL_W, h, 5, 0.6f);
		ui.roundRect(x, y, PANEL_W, h, Theme.withAlpha(Theme.PANEL, 0.96f), 5);
		ui.text("HUD elements", x + 6, y + 5, Theme.TEXT);
		ui.icon(Icon.INFO, x + PANEL_W - 13, y + 4, 8, Theme.TEXT_MUTED);
		if (mouseX >= x + PANEL_W - 15 && mouseX < x + PANEL_W - 2 && mouseY >= y + 2 && mouseY < y + 14) {
			ui.tooltip("panel-info", "Switch: turn the module on or off. Eye: hide or show it in this layout. Click a name to select the element.");
		}

		List<Module> modules = hudModules();
		int listY = y + 17;
		int visibleRows = (h - 20) / ROW_H;
		panelScroll = Math.clamp(panelScroll, 0, Math.max(0, modules.size() - visibleRows));
		ui.pushClip(x, listY, PANEL_W, h - 20);
		for (int i = panelScroll; i < modules.size() && i < panelScroll + visibleRows; i++) {
			Module module = modules.get(i);
			int rowY = listY + (i - panelScroll) * ROW_H;
			boolean isSelected = module.id().equals(selected);
			boolean hot = mouseX >= x + 2 && mouseX < x + PANEL_W - 2 && mouseY >= rowY && mouseY < rowY + ROW_H - 1;
			if (isSelected || hot) {
				ui.roundRect(x + 2, rowY, PANEL_W - 4, ROW_H - 1, isSelected ? Theme.ACCENT_TINT : Theme.SURFACE_HOVER, 3);
			}

			// Module switch.
			boolean on = module.isEnabled();
			int sx = x + 5;
			ui.roundRect(sx, rowY + 3, 12, 6, on ? Theme.ACCENT : Theme.SURFACE_ACTIVE, 3);
			ui.roundRect(on ? sx + 6 : sx, rowY + 3, 6, 6, 0xFFFFFFFF, 3);
			controls.add(new Control(sx - 1, rowY, 14, ROW_H - 1, () -> femboy.modules().toggle(module)));

			// Visibility in this layout.
			HudLayouts.Element element = layout.element(module.id());
			int ex = x + PANEL_W - 14;
			ui.icon(element.hidden() ? Icon.LOCK : Icon.VISUAL, ex, rowY + 2, 8, element.hidden() ? Theme.TEXT_MUTED : Theme.TEXT_SECONDARY);
			controls.add(new Control(ex - 2, rowY, 12, ROW_H - 1, () -> {
				layout.checkpoint();
				layout.update(module.id(), e -> e.withHidden(!e.hidden()));
			}));

			int color = !on ? Theme.TEXT_MUTED : module.isRunning() ? Theme.TEXT : Theme.WARNING;
			ui.textClipped(module.name(), x + 21, rowY + 2, PANEL_W - 40, color);
			controls.add(new Control(x + 19, rowY, PANEL_W - 36, ROW_H - 1, () -> selected = module.id()));
			if (hot) {
				String state = !on ? "Off." : module.isRunning() ? "On." : "On, but not running: " + femboy.modules().availability(module).reason();
				String when = element.when() == HudLayouts.Condition.ALWAYS ? "" : " Shows: " + element.when().label() + ".";
				ui.tooltip(module, module.name() + ": " + state + (element.hidden() ? " Hidden in this layout." : "") + when);
			}
		}

		ui.popClip();
	}

	private void drawInspector(HudManager.Placed block, int mouseX, int mouseY) {
		HudLayouts.Element element = layout.element(block.moduleId());
		int w = Math.min(width - 8, 330);
		int x = (width - w) / 2;
		int y = height - 22;
		ui.shadow(x, y, w, 18, 5, 0.6f);
		ui.roundRect(x, y, w, 18, Theme.withAlpha(Theme.PANEL, 0.96f), 5);
		int cx = x + 6;
		ui.textClipped(block.name(), cx, y + 5, 70, Theme.ACCENT_STRONG);
		cx += Math.min(70, ui.textWidth(block.name())) + 8;

		cx = stepper(cx, y + 2, "Size", String.format(Locale.ROOT, "%.0f%%", element.scale() * 100), mouseX, mouseY,
				() -> change(block.moduleId(), e -> e.withScale(e.scale() - 0.1f)), () -> change(block.moduleId(), e -> e.withScale(e.scale() + 0.1f)));
		cx = stepper(cx, y + 2, "Opacity", String.format(Locale.ROOT, "%.0f%%", element.opacity() * 100), mouseX, mouseY,
				() -> change(block.moduleId(), e -> e.withOpacity(e.opacity() - 0.1f)), () -> change(block.moduleId(), e -> e.withOpacity(e.opacity() + 0.1f)));
		cx = toolButton(cx, y + 2, Icon.CLOCK, element.when().label(), element.when() != HudLayouts.Condition.ALWAYS, true,
				"When it shows. Click to cycle: always, in combat (8 s after you attack or are hurt), out of combat, holding a weapon, "
						+ "gliding, sneaking.", mouseX, mouseY, () -> change(block.moduleId(), e -> e.withCondition(next(e.when()))));
		cx = toolButton(cx, y + 2, element.hidden() ? Icon.LOCK : Icon.VISUAL, null, element.hidden(), true, element.hidden() ? "Show (H)" : "Hide (H)",
				mouseX, mouseY, () -> change(block.moduleId(), e -> e.withHidden(!e.hidden())));
		toolButton(cx, y + 2, Icon.RESET, null, false, element.placed() || element.scale() != 1 || element.opacity() != 1, "Back to its corner (Delete)",
				mouseX, mouseY, () -> change(block.moduleId(), e -> e.unplaced().withScale(1).withOpacity(1)));
	}

	private int stepper(int x, int y, String label, String value, int mouseX, int mouseY, Runnable down, Runnable up) {
		ui.text(label, x, y + 3, Theme.TEXT_MUTED);
		x += ui.textWidth(label) + 3;
		x = toolButton(x, y, Icon.CHEVRON_DOWN, null, false, true, label + ": less", mouseX, mouseY, down);
		ui.textCentered(value, x + 13, y + 3, Theme.TEXT);
		x += 26;
		return toolButton(x, y, Icon.PLUS, null, false, true, label + ": more", mouseX, mouseY, up) + 4;
	}

	private static HudLayouts.Condition next(HudLayouts.Condition condition) {
		HudLayouts.Condition[] all = HudLayouts.Condition.values();
		return all[(condition.ordinal() + 1) % all.length];
	}

	private void change(String moduleId, java.util.function.UnaryOperator<HudLayouts.Element> change) {
		layout.checkpoint();
		layout.update(moduleId, change);
	}

	private int menuX() {
		return (width - toolbarWidth()) / 2 + 2;
	}

	private void drawLayoutMenu(int mouseX, int mouseY) {
		List<String> names = layout.layouts().names();
		String[] actions = {"New layout (copy of this one)", "Rename this layout", "Delete this layout", "Copy to clipboard", "Import from clipboard"};
		int w = 170;
		int x = menuX();
		int y = TOOLBAR_H + 6;
		int h = names.size() * ROW_H + actions.length * ROW_H + 9;
		ui.shadow(x, y, w, h, 5, 0.8f);
		ui.roundRect(x, y, w, h, Theme.SURFACE, 5);
		ui.outline(x, y, w, h, Theme.BORDER_STRONG, 5);
		int rowY = y + 3;
		for (String name : names) {
			boolean active = name.equals(layout.layouts().activeName());
			if (menuRow(x, rowY, w, mouseX, mouseY)) {
				controls.add(new Control(x + 3, rowY, w - 6, ROW_H - 1, () -> switchLayout(name)));
			}

			if (active) {
				ui.icon(Icon.CHECK, x + 7, rowY + 2, 8, Theme.ACCENT);
			}

			int placed = layout.layouts().layout(name).placedCount();
			ui.textClipped(name, x + 19, rowY + 2, w - 60, active ? Theme.ACCENT_STRONG : Theme.TEXT);
			ui.textRight(placed + " moved", x + w - 6, rowY + 2, Theme.TEXT_MUTED);
			rowY += ROW_H;
		}

		ui.rect(x + 6, rowY + 1, w - 12, 1, Theme.BORDER);
		rowY += 3;
		Icon[] icons = {Icon.PLUS, Icon.CODE, Icon.TRASH, Icon.COPY, Icon.FOLDER};
		Runnable[] runs = {() -> openNameDialog(false), () -> openNameDialog(true), this::deleteLayout, this::exportLayout, this::importLayout};
		for (int i = 0; i < actions.length; i++) {
			boolean enabled = i != 2 || names.size() > 1;
			if (menuRow(x, rowY, w, mouseX, mouseY) && enabled) {
				Runnable run = runs[i];
				controls.add(new Control(x + 3, rowY, w - 6, ROW_H - 1, () -> {
					layoutMenu = false;
					run.run();
				}));
			}

			ui.icon(icons[i], x + 7, rowY + 2, 8, enabled ? Theme.TEXT_SECONDARY : Theme.TEXT_MUTED);
			ui.text(actions[i], x + 19, rowY + 2, enabled ? Theme.TEXT : Theme.TEXT_MUTED);
			rowY += ROW_H;
		}
	}

	private boolean menuRow(int x, int rowY, int w, int mouseX, int mouseY) {
		boolean hot = mouseX >= x + 3 && mouseX < x + w - 3 && mouseY >= rowY && mouseY < rowY + ROW_H - 1;
		if (hot) {
			ui.roundRect(x + 3, rowY, w - 6, ROW_H - 1, Theme.SURFACE_HOVER, 3);
			ui.pointer();
		}

		return true;
	}

	private void drawNameDialog() {
		int w = 200;
		int x = (width - w) / 2;
		int y = height / 2 - 26;
		ui.rect(0, 0, width, height, 0x60000000);
		ui.shadow(x, y, w, 52, 6, 1);
		ui.roundRect(x, y, w, 52, Theme.PANEL, 6);
		ui.text(renaming ? "Rename layout" : "New layout", x + 8, y + 7, Theme.TEXT);
		nameField.setBounds(x + 8, y + 19, w - 16, 14);
		nameField.render(ui);
		ui.text("Enter: save   Esc: cancel", x + 8, y + 38, Theme.TEXT_MUTED);
	}

	// ----- actions ---------------------------------------------------------------------------

	private void undo() {
		if (!layout.undo()) {
			say("Nothing to undo.", false);
		}
	}

	private void redo() {
		if (!layout.redo()) {
			say("Nothing to redo.", false);
		}
	}

	private void resetAll() {
		layout.checkpoint();
		layout.resetAll();
		say("Layout reset. Ctrl+Z brings it back.", true);
	}

	private void switchLayout(String name) {
		layoutMenu = false;
		String problem = layout.changeLayouts(layouts -> layouts.setActive(name));
		say(problem == null ? "Layout '" + name + "'." : problem, problem == null);
	}

	private void openNameDialog(boolean rename) {
		renaming = rename;
		nameField = new TextField("Layout name", 24).onSubmit(this::finishName);
		nameField.setText(rename ? layout.layouts().activeName() : "");
		ui.focus(nameField);
	}

	private void finishName(String name) {
		String trimmed = name.strip();
		String current = layout.layouts().activeName();
		String problem = layout.changeLayouts(layouts -> renaming ? layouts.rename(current, trimmed) : layouts.create(trimmed));
		if (problem == null) {
			closeNameDialog();
			say(renaming ? "Renamed to '" + trimmed + "'." : "Created layout '" + trimmed + "'.", true);
		} else {
			say(problem, false);
		}
	}

	private void closeNameDialog() {
		ui.focus(null);
		nameField = null;
	}

	private void deleteLayout() {
		String name = layout.layouts().activeName();
		String problem = layout.changeLayouts(layouts -> layouts.delete(name));
		say(problem == null ? "Deleted layout '" + name + "'." : problem, problem == null);
	}

	private void exportLayout() {
		String text = layout.layouts().export(layout.layouts().activeName());
		minecraft.keyboardHandler.setClipboard(text);
		say("Layout copied to the clipboard.", true);
	}

	private void importLayout() {
		String text = minecraft.keyboardHandler.getClipboard();
		HudLayouts.ImportResult[] result = new HudLayouts.ImportResult[1];
		String problem = layout.changeLayouts(layouts -> {
			result[0] = layouts.importLayout(text);
			return result[0].error();
		});

		if (problem != null) {
			say("Import failed: " + problem, false);
		} else {
			say("Imported layout '" + result[0].name() + "'" + (result[0].warnings().isEmpty() ? "." : " (" + result[0].warnings().size() + " entries skipped)."), true);
			Notifications.post(Notifications.Category.CONFIG, "HUD layout", "imported as " + result[0].name(), Theme.SUCCESS);
		}
	}

	// ----- input -----------------------------------------------------------------------------

	private HudManager.Placed find(String moduleId) {
		if (moduleId == null) {
			return null;
		}

		for (HudManager.Placed block : hud.placed()) {
			if (block.moduleId().equals(moduleId)) {
				return block;
			}
		}

		return null;
	}

	private boolean overCanvas(double mouseX, double mouseY) {
		if (mouseY < TOOLBAR_H + 4 && Math.abs(mouseX - width / 2.0) < toolbarWidth() / 2.0) {
			return false;
		}

		return !(showPanel && mouseX < 4 + PANEL_W && mouseY >= panelTop() && mouseY < panelTop() + panelHeight());
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		double mx = event.x();
		double my = event.y();
		if (nameField != null) {
			nameField.mouseClicked(ui, mx, my, event.button());
			return true;
		}

		for (Control control : List.copyOf(controls).reversed()) {
			if (control.contains(mx, my)) {
				control.action().run();
				return true;
			}
		}

		if (layoutMenu) {
			layoutMenu = false;
			return true;
		}

		if (!overCanvas(mx, my)) {
			return true;
		}

		for (HudManager.Placed block : hud.placed().reversed()) {
			if (inside(block, mx, my)) {
				selected = block.moduleId();
				if (event.button() == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
					change(block.moduleId(), HudLayouts.Element::unplaced);
				} else {
					dragging = block.moduleId();
					grabX = (int) Math.round(mx) - block.x();
					grabY = (int) Math.round(my) - block.y();
					dragMoved = false;
				}

				return true;
			}
		}

		selected = null;
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
		if (dragging == null) {
			return super.mouseDragged(event, deltaX, deltaY);
		}

		HudManager.Placed block = find(dragging);
		if (block == null) {
			return true;
		}

		if (!dragMoved) {
			layout.checkpoint();
			dragMoved = true;
		}

		int x = (int) Math.round(event.x()) - grabX;
		int y = (int) Math.round(event.y()) - grabY;
		boolean free = minecraft.hasShiftDown();
		if (snapping && !free || showGrid && !free) {
			List<Snapping.Box> others = new ArrayList<>();
			for (HudManager.Placed other : hud.placed()) {
				if (!other.moduleId().equals(dragging) && !other.hidden()) {
					others.add(box(other));
				}
			}

			Snapping.Result snapped = Snapping.snap(new Snapping.Box(x, y, block.width(), block.height()), width, height,
					snapping ? others : List.of(), snapping ? SNAP : 0, MARGIN, showGrid ? GRID : 0);
			x = snapped.x();
			y = snapped.y();
			guidesX = snapped.guidesX();
			guidesY = snapped.guidesY();
		} else {
			guidesX = List.of();
			guidesY = List.of();
		}

		layout.placeAt(dragging, x, y, block.width(), block.height(), width, height);
		return true;
	}

	/** Places an element's top-left corner at GUI coordinates without snapping (used by automated tests). */
	public void moveTo(String moduleId, double x, double y) {
		HudManager.Placed block = find(moduleId);
		int w = block == null ? 1 : block.width();
		int h = block == null ? 1 : block.height();
		layout.checkpoint();
		layout.placeAt(moduleId, (int) Math.round(x), (int) Math.round(y), w, h, width, height);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		dragging = null;
		guidesX = List.of();
		guidesY = List.of();
		return super.mouseReleased(event);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
		if (showPanel && mouseX < 4 + PANEL_W && mouseY >= panelTop()) {
			panelScroll -= (int) Math.signum(vertical);
			return true;
		}

		for (HudManager.Placed block : hud.placed().reversed()) {
			if (inside(block, mouseX, mouseY)) {
				selected = block.moduleId();
				float step = (float) Math.signum(vertical) * 0.1f;
				if (minecraft.hasShiftDown()) {
					change(block.moduleId(), e -> e.withOpacity(e.opacity() + step));
				} else {
					change(block.moduleId(), e -> e.withScale(e.scale() + step));
				}

				return true;
			}
		}

		return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (nameField != null) {
			if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
				closeNameDialog();
			} else {
				nameField.keyPressed(ui, event);
			}

			return true;
		}

		int key = event.key();
		if (layoutMenu && key == GLFW.GLFW_KEY_ESCAPE) {
			layoutMenu = false;
			return true;
		}

		if (control(event) && key == GLFW.GLFW_KEY_Z) {
			if (event.hasShiftDown()) redo(); else undo();
			return true;
		}

		if (control(event) && key == GLFW.GLFW_KEY_Y) {
			redo();
			return true;
		}

		switch (key) {
			case GLFW.GLFW_KEY_G -> showGrid = !showGrid;
			case GLFW.GLFW_KEY_TAB -> showPanel = !showPanel;
			case GLFW.GLFW_KEY_R -> resetAll();
			default -> {
				if (!editSelected(event)) {
					return super.keyPressed(event);
				}
			}
		}

		return true;
	}

	/** Keys that change the selected element: arrows nudge, Delete puts it back, H hides, +/- scale. */
	private boolean editSelected(KeyEvent event) {
		HudManager.Placed block = find(selected);
		if (block == null) {
			return false;
		}

		int step = event.hasShiftDown() ? 10 : 1;
		int dx = 0;
		int dy = 0;
		switch (event.key()) {
			case GLFW.GLFW_KEY_LEFT -> dx = -step;
			case GLFW.GLFW_KEY_RIGHT -> dx = step;
			case GLFW.GLFW_KEY_UP -> dy = -step;
			case GLFW.GLFW_KEY_DOWN -> dy = step;
			case GLFW.GLFW_KEY_DELETE, GLFW.GLFW_KEY_BACKSPACE -> {
				change(block.moduleId(), HudLayouts.Element::unplaced);
				return true;
			}
			case GLFW.GLFW_KEY_H -> {
				change(block.moduleId(), e -> e.withHidden(!e.hidden()));
				return true;
			}
			case GLFW.GLFW_KEY_EQUAL, GLFW.GLFW_KEY_KP_ADD -> {
				change(block.moduleId(), e -> e.withScale(e.scale() + 0.1f));
				return true;
			}
			case GLFW.GLFW_KEY_MINUS, GLFW.GLFW_KEY_KP_SUBTRACT -> {
				change(block.moduleId(), e -> e.withScale(e.scale() - 0.1f));
				return true;
			}
			default -> {
				return false;
			}
		}

		layout.checkpoint();
		layout.placeAt(block.moduleId(), block.x() + dx, block.y() + dy, block.width(), block.height(), width, height);
		return true;
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		if (nameField != null) {
			return nameField.charTyped(ui, event);
		}

		return super.charTyped(event);
	}

	@Override
	public void onClose() {
		minecraft.setScreen(parent);
	}

	/** Ctrl (Cmd on macOS) is held: from the event, or from the window's key state when the event lacks it. */
	private boolean control(KeyEvent event) {
		var window = minecraft.getWindow();
		return event.hasControlDownWithQuirk() || com.mojang.blaze3d.platform.InputConstants.isKeyDown(window, GLFW.GLFW_KEY_LEFT_CONTROL)
				|| com.mojang.blaze3d.platform.InputConstants.isKeyDown(window, GLFW.GLFW_KEY_RIGHT_CONTROL);
	}
}
