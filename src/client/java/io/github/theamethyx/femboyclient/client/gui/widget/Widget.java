package io.github.theamethyx.femboyclient.client.gui.widget;

import java.util.function.Supplier;

import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;

import io.github.theamethyx.femboyclient.client.gui.UiContext;

/**
 * Minimal Femboy Client UI element. Coordinates are scaled GUI pixels. Widgets are laid out by their parent
 * through {@link #setBounds}; mouse events arrive only while the mouse is inside the parent's
 * visible area, except drags and releases, which go to the widget that started them.
 */
public abstract class Widget {
	protected int x;
	protected int y;
	protected int width;
	protected int height;
	private Supplier<String> tooltip = () -> null;

	public final Widget setBounds(int x, int y, int width, int height) {
		this.x = x;
		this.y = y;
		this.width = width;
		this.height = height;
		onLayout();
		return this;
	}

	/** Called after the bounds changed. */
	protected void onLayout() {
	}

	public abstract void render(UiContext ui);

	/** Preferred height for the given width, used by vertical lists. */
	public int preferredHeight(UiContext ui, int width) {
		return height;
	}

	public boolean mouseClicked(UiContext ui, double mouseX, double mouseY, int button) {
		return false;
	}

	public void mouseReleased(UiContext ui, double mouseX, double mouseY, int button) {
	}

	public void mouseDragged(UiContext ui, double mouseX, double mouseY, int button) {
	}

	public boolean mouseScrolled(UiContext ui, double mouseX, double mouseY, double amount) {
		return false;
	}

	public boolean keyPressed(UiContext ui, KeyEvent event) {
		return false;
	}

	public boolean charTyped(UiContext ui, CharacterEvent event) {
		return false;
	}

	public void onFocusLost() {
	}

	public boolean contains(double mouseX, double mouseY) {
		return UiContext.inside(mouseX, mouseY, x, y, width, height);
	}

	/** Hover state that respects clipping and overlays. */
	protected boolean hovered(UiContext ui) {
		return ui.hovered(x, y, width, height);
	}

	public Widget tooltip(String text) {
		this.tooltip = () -> text;
		return this;
	}

	public Widget tooltip(Supplier<String> text) {
		this.tooltip = text;
		return this;
	}

	/** Registers this widget's tooltip if hovered; call at the end of {@link #render}. */
	protected void offerTooltip(UiContext ui) {
		if (hovered(ui)) {
			ui.tooltip(this, tooltip.get());
		}
	}

	public int x() {
		return x;
	}

	public int y() {
		return y;
	}

	public int width() {
		return width;
	}

	public int height() {
		return height;
	}
}
