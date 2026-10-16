package io.github.theamethyx.femboyclient.client.gui;

import org.lwjgl.glfw.GLFW;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import io.github.theamethyx.femboyclient.client.hud.HudManager;

/**
 * Drag HUD blocks to place them anywhere. Right-click a block to put it back in its corner, R to reset all, Esc to
 * close (returns to the previous screen). Only blocks of running modules that currently show something can be moved.
 */
public final class HudEditorScreen extends Screen {
	private final Screen parent;
	private final HudManager hud;
	private String dragging;
	private double grabX;
	private double grabY;

	public HudEditorScreen(Screen parent, HudManager hud) {
		super(Component.literal("HUD editor"));
		this.parent = parent;
		this.hud = hud;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		graphics.fill(0, 0, width, height, 0x30000000);
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		super.render(graphics, mouseX, mouseY, partialTick);
		for (HudManager.Placed block : hud.placed()) {
			boolean hot = mouseX >= block.x() && mouseX < block.x() + block.width() && mouseY >= block.y() && mouseY < block.y() + block.height();
			int color = block.moduleId().equals(dragging) ? Theme.ACCENT_STRONG : hot ? Theme.ACCENT : Theme.withAlpha(Theme.ACCENT, 0.5f);
			graphics.renderOutline(block.x() - 1, block.y() - 1, block.width() + 2, block.height() + 2, color);
			if (hot || block.moduleId().equals(dragging)) {
				graphics.drawString(font, block.name() + (block.custom() ? "  (right-click: back to corner)" : ""), block.x(),
						Math.max(0, block.y() - 10), Theme.TEXT, true);
			}
		}

		String hint = hud.placed().isEmpty() ? "No HUD blocks are showing. Turn on HUD modules (Coordinates, Combat Info, ...) first."
				: "Drag HUD blocks to move them  ·  right-click: back to its corner  ·  R: reset all  ·  Esc: done";
		// Wrapped to the screen and drawn on a panel at the top, so it never runs off the edges or covers blocks in the middle.
		var lines = font.split(net.minecraft.network.chat.Component.literal(hint), Math.max(80, width - 2 * Theme.PAD * 2));
		int panelWidth = 0;
		for (var line : lines) {
			panelWidth = Math.max(panelWidth, font.width(line));
		}

		int panelHeight = lines.size() * (font.lineHeight + 2) + Theme.PAD;
		int left = (width - panelWidth) / 2 - Theme.PAD;
		graphics.fill(left, 2, left + panelWidth + 2 * Theme.PAD, 2 + panelHeight, Theme.withAlpha(Theme.SURFACE, 0.85f));
		for (int i = 0; i < lines.size(); i++) {
			graphics.drawCenteredString(font, lines.get(i), width / 2, 2 + Theme.PAD / 2 + 1 + i * (font.lineHeight + 2), Theme.TEXT);
		}
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		for (HudManager.Placed block : hud.placed().reversed()) {
			if (event.x() >= block.x() && event.x() < block.x() + block.width() && event.y() >= block.y() && event.y() < block.y() + block.height()) {
				if (event.button() == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
					hud.layout().reset(block.moduleId());
				} else {
					dragging = block.moduleId();
					grabX = event.x() - block.x();
					grabY = event.y() - block.y();
				}

				return true;
			}
		}

		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
		if (dragging == null) {
			return super.mouseDragged(event, deltaX, deltaY);
		}

		moveTo(dragging, event.x() - grabX, event.y() - grabY);
		return true;
	}

	/** Places a block's top-left corner at GUI coordinates (also used by tests). */
	public void moveTo(String moduleId, double x, double y) {
		hud.layout().set(moduleId, (float) (x / Math.max(1, width)), (float) (y / Math.max(1, height)));
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		dragging = null;
		return super.mouseReleased(event);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (event.key() == GLFW.GLFW_KEY_R) {
			hud.layout().resetAll();
			return true;
		}

		return super.keyPressed(event);
	}

	@Override
	public void onClose() {
		minecraft.setScreen(parent);
	}
}
