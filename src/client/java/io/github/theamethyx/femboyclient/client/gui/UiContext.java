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
	public static final Identifier ROUND = Identifier.fromNamespaceAndPath(FemboyClient.MOD_ID, "textures/gui/round.png");
	public static final Identifier SHADOW = Identifier.fromNamespaceAndPath(FemboyClient.MOD_ID, "textures/gui/shadow.png");
	public static final Identifier GLOW = Identifier.fromNamespaceAndPath(FemboyClient.MOD_ID, "textures/gui/glow.png");
	public static final Identifier RAMP = Identifier.fromNamespaceAndPath(FemboyClient.MOD_ID, "textures/gui/ramp.png");
	public static final Identifier LOGO = Identifier.fromNamespaceAndPath(FemboyClient.MOD_ID, "textures/gui/logo.png");
	/** Whether the GUI textures are present; checked once. Without them shapes fall back to plain stepped drawing. */
	private static Boolean art;
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

	private String shownTooltip;
	private boolean reduceMotion;
	private boolean cheapShapes;
	/** Opacity applied to everything drawn (HUD elements can be faded); 1 = unchanged. */
	private float alpha = 1;

	public UiContext(Font font) {
		this.font = font;
	}

	/**
	 * Whether the smooth-shape textures loaded. A resource pack or a broken jar could remove them; then every shape is
	 * drawn with plain rectangles instead of showing missing-texture squares.
	 */
	public static boolean art() {
		if (art == null) {
			var resources = net.minecraft.client.Minecraft.getInstance().getResourceManager();
			art = resources.getResource(ROUND).isPresent() && resources.getResource(SHADOW).isPresent() && resources.getResource(GLOW).isPresent()
					&& resources.getResource(RAMP).isPresent() && resources.getResource(LOGO).isPresent();
		}

		return art;
	}

	/** Forgets the texture check, e.g. after resource packs were reloaded. */
	public static void recheckArt() {
		art = null;
	}

	/**
	 * For drawing done every frame during play (the HUD, ESP labels): plain shapes with 1-pixel cut corners instead of
	 * textured smooth corners. Textured corners switch the render pipeline between quads, which costs batching, and at HUD
	 * sizes the difference is barely visible. The menu keeps the smooth shapes.
	 */
	public void setCheapShapes(boolean cheapShapes) {
		this.cheapShapes = cheapShapes;
	}

	/**
	 * Multiplies the opacity of everything drawn until it is set back to 1 (used for faded HUD elements). Items drawn with
	 * {@link #item} are not faded: Minecraft draws them opaque.
	 */
	public void setAlpha(float alpha) {
		this.alpha = Math.clamp(alpha, 0, 1);
	}

	public float alpha() {
		return alpha;
	}

	/** {@code color} with the current opacity applied. */
	private int a(int color) {
		return alpha >= 1 ? color : Theme.withAlpha(color, alpha);
	}

	/** With reduced motion on, things no longer slide or grow; only short fades remain. */
	public void setReduceMotion(boolean reduceMotion) {
		this.reduceMotion = reduceMotion;
	}

	/** Whether positions and sizes may animate this frame (animations on and motion not reduced). */
	public boolean motion() {
		return animate && !reduceMotion;
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

		shownTooltip = null;
		if (tooltipsEnabled && tooltip != null && System.currentTimeMillis() - tooltipHoverStart >= TOOLTIP_DELAY_MS) {
			graphics.nextStratum();
			drawTooltip(tooltip, screenWidth, screenHeight);
			shownTooltip = tooltip;
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

	/** Text of the tooltip drawn in the last frame, or null (used by automated UI tests). */
	public String shownTooltip() {
		return shownTooltip;
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
			graphics.fill(x, y, x + width, y + height, a(color));
		}
	}

	/** The standard soft shape used throughout Femboy Client: a rectangle with smooth 3-pixel corners. */
	public void roundRect(int x, int y, int width, int height, int color) {
		roundRect(x, y, width, height, color, 3);
	}

	/**
	 * A rectangle with smooth, anti-aliased corners of {@code radius} pixels: the corners are quarters of a filtered disc
	 * texture, the rest plain fills, so translucent colors stay even. Falls back to stepped corners without the texture.
	 */
	public void roundRect(int x, int y, int width, int height, int color, int radius) {
		if (width <= 0 || height <= 0) {
			return;
		}

		if (alpha < 1) {
			// Applied once here; rect() below would apply it again.
			float saved = alpha;
			alpha = 1;
			roundRect(x, y, width, height, Theme.withAlpha(color, saved), radius);
			alpha = saved;
			return;
		}

		int r = Math.min(Math.round(radius * Theme.RADIUS_SCALE), Math.min(width, height) / 2);
		if (r <= 0) {
			rect(x, y, width, height, color);
			return;
		}

		if (cheapShapes) {
			cutRect(x, y, width, height, color);
			return;
		}

		if (!art()) {
			steppedRoundRect(x, y, width, height, color, r);
			return;
		}

		corner(x, y, r, 0, 0, color);
		corner(x + width - r, y, r, 32, 0, color);
		corner(x, y + height - r, r, 0, 32, color);
		corner(x + width - r, y + height - r, r, 32, 32, color);
		graphics.fill(x + r, y, x + width - r, y + height, color);
		graphics.fill(x, y + r, x + r, y + height - r, color);
		graphics.fill(x + width - r, y + r, x + width, y + height - r, color);
	}

	/** Three fills: a rectangle with its corner pixels left out. */
	private void cutRect(int x, int y, int width, int height, int color) {
		if (width < 3 || height < 3) {
			rect(x, y, width, height, color);
			return;
		}

		graphics.fill(x + 1, y, x + width - 1, y + height, color);
		graphics.fill(x, y + 1, x + 1, y + height - 1, color);
		graphics.fill(x + width - 1, y + 1, x + width, y + height - 1, color);
	}

	private void corner(int x, int y, int size, int u, int v, int color) {
		graphics.blit(RenderPipelines.GUI_TEXTURED, ROUND, x, y, u, v, size, size, 32, 32, 64, 64, color);
	}

	private void steppedRoundRect(int x, int y, int width, int height, int color, int r) {
		for (int i = 0; i < r; i++) {
			double dy = r - i - 0.5;
			int inset = r - (int) Math.round(Math.sqrt(r * r - dy * dy));
			graphics.fill(x + inset, y + i, x + width - inset, y + i + 1, color);
			graphics.fill(x + inset, y + height - i - 1, x + width - inset, y + height - i, color);
		}

		graphics.fill(x, y + r, x + width, y + height - r, color);
	}

	/**
	 * A rounded rectangle shaded from {@code left} to {@code right}: the left corners take the first color, the right
	 * corners the second, and a filtered alpha ramp blends the middle.
	 */
	public void gradientRect(int x, int y, int width, int height, int left, int right, int radius) {
		radius = Math.round(radius * Theme.RADIUS_SCALE);
		left = a(left);
		right = a(right);
		int r = Math.min(radius, Math.min(width, height) / 2);
		if (cheapShapes || !art() || width <= 2 * r + 1) {
			roundRect(x, y, width, height, Theme.lerp(left, right, 0.5f), radius);
			return;
		}

		corner(x, y, r, 0, 0, left);
		corner(x, y + height - r, r, 0, 32, left);
		corner(x + width - r, y, r, 32, 0, right);
		corner(x + width - r, y + height - r, r, 32, 32, right);
		graphics.fill(x, y + r, x + r, y + height - r, left);
		graphics.fill(x + width - r, y + r, x + width, y + height - r, right);
		graphics.fill(x + r, y, x + width - r, y + height, left);
		graphics.blit(RenderPipelines.GUI_TEXTURED, RAMP, x + r, y, 0, 0, width - 2 * r, height, 64, 4, 64, 4, right);
	}

	/**
	 * A soft drop shadow under a box, from a blurred 9-slice texture: {@code spread} pixels of blur around the box, a
	 * little lower than it. Falls back to a few fading layers without the texture.
	 */
	public void shadow(int x, int y, int width, int height, int radius, float strength) {
		int alpha = Math.round(Math.min(1, strength * 0.55f) * 255);
		if (!art()) {
			for (int i = 3; i >= 1; i--) {
				steppedRoundRect(x - i, y - i + 1, width + 2 * i, height + 2 * i, Math.round(strength * 40 / i) << 24, radius + i);
			}

			return;
		}

		int spread = 10 + radius;
		int c = spread + radius;
		int ox = x - spread;
		int oy = y - spread + 3;
		int ow = width + 2 * spread;
		int oh = height + 2 * spread;
		int tint = alpha << 24 | 0x000000;
		int middleW = Math.max(0, ow - 2 * c);
		int middleH = Math.max(0, oh - 2 * c);
		// Corners (24 px of texture each), edges (the 16 px middle of the texture, stretched), centre.
		graphics.blit(RenderPipelines.GUI_TEXTURED, SHADOW, ox, oy, 0, 0, c, c, 24, 24, 64, 64, tint);
		graphics.blit(RenderPipelines.GUI_TEXTURED, SHADOW, ox + ow - c, oy, 40, 0, c, c, 24, 24, 64, 64, tint);
		graphics.blit(RenderPipelines.GUI_TEXTURED, SHADOW, ox, oy + oh - c, 0, 40, c, c, 24, 24, 64, 64, tint);
		graphics.blit(RenderPipelines.GUI_TEXTURED, SHADOW, ox + ow - c, oy + oh - c, 40, 40, c, c, 24, 24, 64, 64, tint);
		graphics.blit(RenderPipelines.GUI_TEXTURED, SHADOW, ox + c, oy, 24, 0, middleW, c, 16, 24, 64, 64, tint);
		graphics.blit(RenderPipelines.GUI_TEXTURED, SHADOW, ox + c, oy + oh - c, 24, 40, middleW, c, 16, 24, 64, 64, tint);
		graphics.blit(RenderPipelines.GUI_TEXTURED, SHADOW, ox, oy + c, 0, 24, c, middleH, 24, 16, 64, 64, tint);
		graphics.blit(RenderPipelines.GUI_TEXTURED, SHADOW, ox + ow - c, oy + c, 40, 24, c, middleH, 24, 16, 64, 64, tint);
		graphics.blit(RenderPipelines.GUI_TEXTURED, SHADOW, ox + c, oy + c, 24, 24, middleW, middleH, 16, 16, 64, 64, tint);
	}

	/** A soft round glow of {@code color} (alpha included) centered on a point; nothing without the texture. */
	public void glow(int centerX, int centerY, int radius, int color) {
		if (art() && radius > 0 && Theme.BACKDROP_ART) {
			graphics.blit(RenderPipelines.GUI_TEXTURED, GLOW, centerX - radius, centerY - radius, 0, 0, radius * 2, radius * 2, 128, 128, 128, 128, color);
		}
	}

	/** The Femboy Client badge at {@code size} pixels; a rounded accent square with a heart when the texture is missing. */
	public void logo(int x, int y, int size) {
		if (art()) {
			graphics.blit(RenderPipelines.GUI_TEXTURED, LOGO, x, y, 0, 0, size, size, 128, 128, 128, 128, 0xFFFFFFFF);
		} else {
			roundRect(x, y, size, size, Theme.ACCENT, Math.max(2, size / 4));
			icon(Icon.HEART, x + size / 4, y + size / 4, size / 2, 0xFFFFFFFF);
		}
	}

	/**
	 * A one-pixel border following the {@link #roundRect} shape: the straight edges, stopping where the smooth corners
	 * begin, so it never looks squared off.
	 */
	public void outline(int x, int y, int width, int height, int color) {
		outline(x, y, width, height, color, 3);
	}

	public void outline(int x, int y, int width, int height, int color, int radius) {
		color = a(color);
		int r = Math.max(1, Math.min(radius, Math.min(width, height) / 2) - 1);
		graphics.fill(x + r, y, x + width - r, y + 1, color);
		graphics.fill(x + r, y + height - 1, x + width - r, y + height, color);
		graphics.fill(x, y + r, x + 1, y + height - r, color);
		graphics.fill(x + width - 1, y + r, x + width, y + height - r, color);
	}

	public void text(String text, int x, int y, int color) {
		graphics.drawString(font, text, x, y, a(color), false);
	}

	public void textCentered(String text, int centerX, int y, int color) {
		graphics.drawString(font, text, centerX - font.width(text) / 2, y, a(color), false);
	}

	public void textRight(String text, int rightX, int y, int color) {
		graphics.drawString(font, text, rightX - font.width(text), y, a(color), false);
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
			graphics.drawString(font, lines.get(i), x, y + i * 10, a(color), false);
		}

		return lines.size() * 10;
	}

	public int wrappedHeight(String text, int maxWidth) {
		return font.split(Component.literal(text), Math.max(10, maxWidth)).size() * 10;
	}

	/** Draws an icon at {@code size}x{@code size} GUI pixels, tinted with {@code color}. */
	public void icon(Icon icon, int x, int y, int size, int color) {
		graphics.blit(RenderPipelines.GUI_TEXTURED, ICONS, x, y, icon.u(), icon.v(), size, size, Icon.CELL, Icon.CELL,
				Icon.ATLAS_WIDTH, Icon.ATLAS_HEIGHT, a(color));
	}

	public int textWidth(String text) {
		return font.width(text);
	}

	/** Draws text scaled around its top-left corner; {@code scale} 1 is the normal GUI font size. */
	public void textScaled(String text, float x, float y, float scale, int color) {
		var pose = graphics.pose();
		pose.pushMatrix();
		pose.translate(x, y);
		pose.scale(scale, scale);
		graphics.drawString(font, text, 0, 0, a(color), false);
		pose.popMatrix();
	}

	/** Draws an item icon (with its durability bar and count) scaled around its top-left corner; 1 is 16 GUI pixels. */
	public void item(net.minecraft.world.item.ItemStack stack, float x, float y, float scale) {
		var pose = graphics.pose();
		pose.pushMatrix();
		pose.translate(x, y);
		pose.scale(scale, scale);
		graphics.renderItem(stack, 0, 0);
		graphics.renderItemDecorations(font, stack, 0, 0);
		pose.popMatrix();
	}

	/**
	 * Reserves room for a world label so labels placed later in the same frame avoid it. If the box
	 * overlaps an earlier label it is moved upwards, so nearby labels stack instead of overlapping.
	 *
	 * @return the (possibly moved) top edge
	 */
	public int placeLabel(int x, int y, int width, int height) {
		for (int attempt = 0; attempt < 8; attempt++) {
			boolean overlaps = false;
			for (int[] r : placedLabels) {
				if (x < r[0] + r[2] && x + width > r[0] && y < r[1] + r[3] && y + height > r[1]) {
					overlaps = true;
					y = r[1] - height - 1;
				}
			}

			if (!overlaps) {
				break;
			}
		}

		placedLabels.add(new int[] {x, y, width, height});
		return y;
	}

	/**
	 * Draws a simple world label: text in the given color in a small dark box, centered on
	 * {@code centerX} with its bottom at {@code bottomY}, stacked above earlier labels.
	 */
	public void worldLabel(String text, int centerX, int bottomY, int color) {
		int w = textWidth(text) + 8;
		int h = 12;
		int x = centerX - w / 2;
		int y = placeLabel(x, bottomY - h, w, h);
		roundRect(x, y, w, h, 0xA0101014);
		textCentered(text, centerX, y + 2, color | 0xFF000000);
	}
}
