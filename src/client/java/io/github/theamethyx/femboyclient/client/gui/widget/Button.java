package io.github.theamethyx.femboyclient.client.gui.widget;

import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import io.github.theamethyx.femboyclient.client.gui.Anim;
import io.github.theamethyx.femboyclient.client.gui.Icon;
import io.github.theamethyx.femboyclient.client.gui.Theme;
import io.github.theamethyx.femboyclient.client.gui.UiContext;

/** A clickable button with an optional icon. */
public class Button extends Widget {
	public enum Style { PRIMARY, SECONDARY, GHOST, DANGER }

	protected Supplier<String> label;
	private final Runnable action;
	private final Style style;
	private final Anim hover = new Anim(0, 18);
	private Icon icon;
	private BooleanSupplier enabled = () -> true;

	public Button(String label, Style style, Runnable action) {
		this(() -> label, style, action);
	}

	public Button(Supplier<String> label, Style style, Runnable action) {
		this.label = label;
		this.style = style;
		this.action = action;
		this.height = 16;
	}

	public Button icon(Icon icon) {
		this.icon = icon;
		return this;
	}

	public Button enabledWhen(BooleanSupplier enabled) {
		this.enabled = enabled;
		return this;
	}

	public String label() {
		return label.get();
	}

	public boolean isEnabled() {
		return enabled.getAsBoolean();
	}

	/** Width needed to show the label and icon without clipping. */
	public int naturalWidth(UiContext ui) {
		String text = label.get();
		int textWidth = text.isEmpty() ? 0 : ui.textWidth(text);
		int iconWidth = icon == null ? 0 : 9 + (text.isEmpty() ? 0 : 4);
		return textWidth + iconWidth + 12;
	}

	@Override
	public void render(UiContext ui) {
		boolean active = isEnabled();
		boolean hot = active && hovered(ui);
		float h = hover.update(hot ? 1 : 0, ui.frameSeconds(), ui.animate());

		int background;
		int foreground;
		switch (style) {
			case PRIMARY -> {
				background = Theme.lerp(Theme.ACCENT_DIM, Theme.ACCENT, h * 0.6f);
				foreground = Theme.TEXT;
			}
			case DANGER -> {
				background = Theme.lerp(0xFF3A1E22, 0xFF5A2329, h);
				foreground = Theme.ERROR;
			}
			case GHOST -> {
				background = Theme.withAlpha(Theme.SURFACE_HOVER, h);
				foreground = Theme.lerp(Theme.TEXT_SECONDARY, Theme.TEXT, h);
			}
			default -> {
				background = Theme.lerp(Theme.SURFACE, Theme.SURFACE_ACTIVE, h);
				foreground = Theme.TEXT;
			}
		}

		if (!active) {
			background = Theme.SURFACE;
			foreground = Theme.TEXT_MUTED;
		}

		ui.roundRect(x, y, width, height, background);
		if (style == Style.SECONDARY) {
			ui.outline(x, y, width, height, Theme.lerp(Theme.BORDER, Theme.BORDER_STRONG, h));
		}

		String text = label.get();
		int contentWidth = (icon == null ? 0 : 9) + (text.isEmpty() ? 0 : ui.textWidth(text) + (icon == null ? 0 : 4));
		int cx = x + Math.max(4, (width - contentWidth) / 2);
		int textY = y + (height - 8) / 2;
		if (icon != null) {
			ui.icon(icon, cx, y + (height - 9) / 2, 9, foreground);
			cx += text.isEmpty() ? 9 : 13;
		}

		if (!text.isEmpty()) {
			ui.textClipped(text, cx, textY, x + width - cx - 3, foreground);
		}

		if (hot) {
			ui.pointer();
		}

		offerTooltip(ui);
	}

	@Override
	public boolean mouseClicked(UiContext ui, double mouseX, double mouseY, int button) {
		if (button == 0 && isEnabled() && contains(mouseX, mouseY)) {
			action.run();
			return true;
		}

		return false;
	}
}
