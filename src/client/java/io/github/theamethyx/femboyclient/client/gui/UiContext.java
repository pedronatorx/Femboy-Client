package io.github.theamethyx.femboyclient.client.gui;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

import com.mojang.blaze3d.platform.cursor.CursorType;
import com.mojang.blaze3d.platform.cursor.CursorTypes;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;

import io.github.theamethyx.femboyclient.client.FemboyClient;
import io.github.theamethyx.femboyclient.client.gui.widget.Widget;

/**
 * Per-screen UI state plus drawing helpers. Holds the current {@link GuiGraphics} during rendering,
 * the clip stack used for hover tests, keyboard focus, the open overlay and the pending tooltip.
 */
public final class UiContext {
	public static final Identifier ICONS = Identifier.fromNamespaceAndPath(FemboyClient.MOD_ID, "textures/gui/icons.png");
	private static final long TOOLTIP_DELAY_MS = 450;

	public final Font font;
	private final Deque<int[]> clips = new ArrayDeque<>();
	private GuiGraphics graphics;
	private int mouseX;
	private int mouseY;
	private float frameSeconds;
	private boolean animate = true;
	private Widget focused;
	private Widget overlay;
	private String tooltip;
	private Object tooltipOwner;
	private Object lastTooltipOwner;
	private long tooltipHoverStart;
	private CursorType cursor;
	private final java.util.List<int[]> placedLabels = new java.util.ArrayList<>();
	private int screenWidth = 10_000;
	private int screenHeight = 10_000;

	public UiContext(Font font) {
		this.font = font;
	}

	// ----- frame lifecycle -------------------------------------------------------------------

	public void beginFrame(GuiGraphics graphics, int mouseX, int mouseY, float frameSeconds, boolean animate) {
		this.graphics = graphics;
		this.mouseX = mouseX;
		this.mouseY = mouseY;
		this.frameSeconds = Math.min(frameSeconds, 0.1f);
		this.animate = animate;
		this.tooltip = null;
		this.tooltipOwner = null;
		this.cursor = null;
		this.clips.clear();
		this.placedLabels.clear();
	}

	/** Draws the tooltip (after a short hover delay) and applies the requested cursor. */
	public void endFrame(boolean tooltipsEnabled, int screenWidth, int screenHeight) {
		if (tooltipOwner != lastTooltipOwner) {
			lastTooltipOwner = tooltipOwner;
			tooltipHoverStart = System.currentTimeMillis();
		}

		if (tooltipsEnabled && tooltip != null && System.currentTimeMillis() - tooltipHoverStart >= TOOLTIP_DELAY_MS) {
			graphics.nextStratum();
			drawTooltip(tooltip, screenWidth, screenHeight);
		}

		if (cursor != null) {
			graphics.requestCursor(cursor);
		}
	}

	private void drawTooltip(String text, int screenWidth, int screenHeight) {
		int maxWidth = Math.min(220, screenWidth - 16);
		List<FormattedCharSequence> lines = font.split(Component.literal(text), maxWidth - 10);
		int width = 0;
		for (FormattedCharSequence line : lines) {
			width = Math.max(width, font.width(line));
		}

		int boxWidth = width + 10;
		int boxHeight = lines.size() * 10 + 7;
		int x = mouseX + 10;
		int y = mouseY + 12;
		if (x + boxWidth > screenWidth - 4) x = Math.max(4, mouseX - boxWidth - 6);
		if (y + boxHeight > screenHeight - 4) y = Math.max(4, mouseY - boxHeight - 6);

		roundRect(x, y, boxWidth, boxHeight, 0xF6101014);
		outline(x, y, boxWidth, boxHeight, Theme.BORDER_STRONG);
		graphics.fill(x + 1, y + 1, x + 2, y + boxHeight - 1, Theme.ACCENT_DIM);
		for (int i = 0; i < lines.size(); i++) {
			graphics.drawString(font, lines.get(i), x + 6, y + 4 + i * 10, Theme.TEXT_SECONDARY, false);
		}
	}

	/** Size of the drawable area in this UI's coordinate space (used to keep popups on screen). */
	public void setScreenSize(int width, int height) {
		this.screenWidth = width;
		this.screenHeight = height;
	}

	public int screenWidth() {
		return screenWidth;
	}

	public int screenHeight() {
		return screenHeight;
	}

	// ----- interaction state -----------------------------------------------------------------

	public GuiGraphics graphics() {
		return graphics;
	}

	public int mouseX() {
		return mouseX;
	}

	public int mouseY() {
		return mouseY;
	}

	public float frameSeconds() {
		return frameSeconds;
	}

	public boolean animate() {
		return animate;
	}

	/** Whether the mouse is over the rectangle, inside the current clip and not under an overlay. */
	public boolean hovered(int x, int y, int width, int height) {
		if (overlay != null && !renderingOverlay) {
			return false;
		}

		return inside(mouseX, mouseY, x, y, width, height) && insideClip(mouseX, mouseY);
	}

	private boolean renderingOverlay;

	public void setRenderingOverlay(boolean renderingOverlay) {
		this.renderingOverlay = renderingOverlay;
	}

	public static boolean inside(double px, double py, int x, int y, int width, int height) {
		return px >= x && py >= y && px < x + width && py < y + height;
	}

	private boolean insideClip(double px, double py) {
		int[] clip = clips.peek();
		return clip == null || (px >= clip[0] && py >= clip[1] && px < clip[2] && py < clip[3]);
	}

	public void pushClip(int x, int y, int width, int height) {
		int[] parent = clips.peek();
		int x1 = x, y1 = y, x2 = x + width, y2 = y + height;
		if (parent != null) {
			x1 = Math.max(x1, parent[0]);
			y1 = Math.max(y1, parent[1]);
			x2 = Math.min(x2, parent[2]);
			y2 = Math.min(y2, parent[3]);
		}

		clips.push(new int[] {x1, y1, Math.max(x1, x2), Math.max(y1, y2)});
		graphics.enableScissor(x1, y1, Math.max(x1, x2), Math.max(y1, y2));
	}

	public void popClip() {
		clips.pop();
		graphics.disableScissor();
	}

	public Widget focused() {
		return focused;
	}

	public void focus(Widget widget) {
		if (focused != widget && focused != null) {
			Widget previous = focused;
			focused = null;
			previous.onFocusLost();
		}

		focused = widget;
	}

	public boolean isFocused(Widget widget) {
		return focused == widget;
	}

	private Widget captured;

	/** The widget that receives drag and release events until the mouse button is released. */
	public void capture(Widget widget) {
		captured = widget;
	}

	public Widget captured() {
		return captured;
	}

	public void releaseCapture() {
		captured = null;
	}

	public Widget overlay() {
		return overlay;
	}

	public void openOverlay(Widget widget) {
		overlay = widget;
	}

	public void closeOverlay() {
		overlay = null;
	}

	/** Requests a tooltip for this frame; {@code owner} identifies the hovered element for the delay. */
	public void tooltip(Object owner, String text) {
		if (text != null && !text.isEmpty()) {
			tooltip = text;
			tooltipOwner = owner;
		}
	}

	public void pointer() {
		cursor = CursorTypes.POINTING_HAND;
	}

	public void textCursor() {
		cursor = CursorTypes.IBEAM;
	}

	public void resizeCursor() {
		cursor = CursorTypes.RESIZE_EW;
	}

	// ----- drawing ---------------------------------------------------------------------------

	public void rect(int x, int y, int width, int height, int color) {
		if (width > 0 && height > 0) {
			graphics.fill(x, y, x + width, y + height, color);
		}
	}

	/** A rectangle with 1-pixel cut corners, the "soft" panel shape used throughout Femboy Client. */
	public void roundRect(int x, int y, int width, int height, int color) {
		if (width < 3 || height < 3) {
			rect(x, y, width, height, color);
			return;
		}

		graphics.fill(x + 1, y, x + width - 1, y + height, color);
		graphics.fill(x, y + 1, x + 1, y + height - 1, color);
		graphics.fill(x + width - 1, y + 1, x + width, y + height - 1, color);
	}

	/** One-pixel border following the {@link #roundRect} shape. */
	public void outline(int x, int y, int width, int height, int color) {
		graphics.fill(x + 1, y, x + width - 1, y + 1, color);
		graphics.fill(x + 1, y + height - 1, x + width - 1, y + height, color);
		graphics.fill(x, y + 1, x + 1, y + height - 1, color);
		graphics.fill(x + width - 1, y + 1, x + width, y + height - 1, color);
	}

	public void text(String text, int x, int y, int color) {
		graphics.drawString(font, text, x, y, color, false);
	}

	public void textCentered(String text, int centerX, int y, int color) {
		graphics.drawString(font, text, centerX - font.width(text) / 2, y, color, false);
	}

	public void textRight(String text, int rightX, int y, int color) {
		graphics.drawString(font, text, rightX - font.width(text), y, color, false);
	}

	/** Draws text cut to {@code maxWidth} with an ellipsis; returns whether it was cut. */
	public boolean textClipped(String text, int x, int y, int maxWidth, int color) {
		String shown = ellipsize(text, maxWidth);
		text(shown, x, y, color);
		return !shown.equals(text);
	}

	public String ellipsize(String text, int maxWidth) {
		if (font.width(text) <= maxWidth) {
			return text;
		}

		String ellipsis = "...";
		int available = maxWidth - font.width(ellipsis);
		return available <= 0 ? "" : font.plainSubstrByWidth(text, available) + ellipsis;
	}

	/** Draws wrapped text and returns the height used. */
	public int wrapped(String text, int x, int y, int maxWidth, int color) {
		List<FormattedCharSequence> lines = font.split(Component.literal(text), Math.max(10, maxWidth));
		for (int i = 0; i < lines.size(); i++) {
			graphics.drawString(font, lines.get(i), x, y + i * 10, color, false);
		}

		return lines.size() * 10;
	}

	public int wrappedHeight(String text, int maxWidth) {
		return font.split(Component.literal(text), Math.max(10, maxWidth)).size() * 10;
	}

	/** Draws an icon at {@code size}x{@code size} GUI pixels, tinted with {@code color}. */
	public void icon(Icon icon, int x, int y, int size, int color) {
		graphics.blit(RenderPipelines.GUI_TEXTURED, ICONS, x, y, icon.u(), icon.v(), size, size, Icon.CELL, Icon.CELL,
				Icon.ATLAS_WIDTH, Icon.ATLAS_HEIGHT, color);
	}

	public int textWidth(String text) {
		return font.width(text);
	}

	/**
	 * Draws a world label (text in a small dark box with a colored underline) centered on
	 * {@code centerX} with its bottom at {@code bottomY}. Labels placed earlier in the same frame are
	 * avoided by moving this one upwards, so nearby labels stack instead of overlapping.
	 */
	public void worldLabel(String text, int centerX, int bottomY, int accent) {
		int w = textWidth(text) + 8;
		int h = 12;
		int x = centerX - w / 2;
		int y = bottomY - h;
		for (int attempt = 0; attempt < 8; attempt++) {
			boolean overlaps = false;
			for (int[] r : placedLabels) {
				if (x < r[0] + r[2] && x + w > r[0] && y < r[1] + r[3] && y + h > r[1]) {
					overlaps = true;
					y = r[1] - h - 1;
				}
			}

			if (!overlaps) {
				break;
			}
		}

		placedLabels.add(new int[] {x, y, w, h});
		roundRect(x, y, w, h, 0xB0101014);
		rect(x + 1, y + h - 1, w - 2, 1, accent);
		textCentered(text, centerX, y + 2, Theme.TEXT);
	}
}
