package io.github.theamethyx.nova.client.gui.widget;

import java.util.function.Consumer;
import java.util.function.IntPredicate;

import org.lwjgl.glfw.GLFW;

import net.minecraft.client.Minecraft;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;

import io.github.theamethyx.nova.client.gui.Icon;
import io.github.theamethyx.nova.client.gui.Theme;
import io.github.theamethyx.nova.client.gui.UiContext;

/** Single-line text input with a cursor, select-all, copy and paste. */
public class TextField extends Widget {
	private final String placeholder;
	private final int maxLength;
	private IntPredicate allowedChars = codePoint -> codePoint >= 32 && codePoint != 127;
	private Consumer<String> onChange = text -> { };
	private Consumer<String> onSubmit = text -> { };
	private Icon icon;
	private String text = "";
	private int cursor;
	private boolean allSelected;
	private int scroll;

	public TextField(String placeholder, int maxLength) {
		this.placeholder = placeholder;
		this.maxLength = maxLength;
		this.height = 15;
	}

	public TextField icon(Icon icon) {
		this.icon = icon;
		return this;
	}

	public TextField allow(IntPredicate allowedChars) {
		this.allowedChars = allowedChars;
		return this;
	}

	public TextField onChange(Consumer<String> onChange) {
		this.onChange = onChange;
		return this;
	}

	public TextField onSubmit(Consumer<String> onSubmit) {
		this.onSubmit = onSubmit;
		return this;
	}

	public String text() {
		return text;
	}

	public void setText(String value) {
		String clipped = value.length() > maxLength ? value.substring(0, maxLength) : value;
		if (!clipped.equals(text)) {
			text = clipped;
			onChange.accept(text);
		}

		cursor = text.length();
		allSelected = false;
	}

	@Override
	public void render(UiContext ui) {
		boolean focused = ui.isFocused(this);
		boolean hot = hovered(ui);
		ui.roundRect(x, y, width, height, focused ? 0xFF15151A : Theme.lerp(Theme.SURFACE, Theme.SURFACE_HOVER, hot ? 1 : 0));
		ui.outline(x, y, width, height, focused ? Theme.ACCENT_DIM : hot ? Theme.BORDER_STRONG : Theme.BORDER);

		int textX = x + 5;
		if (icon != null) {
			ui.icon(icon, x + 4, y + (height - 9) / 2, 9, focused ? Theme.ACCENT : Theme.TEXT_MUTED);
			textX += 12;
		}

		int textWidth = x + width - 4 - textX;
		int textY = y + (height - 8) / 2;
		ui.pushClip(textX, y + 1, textWidth, height - 2);

		if (text.isEmpty() && !focused) {
			ui.text(placeholder, textX, textY, Theme.TEXT_MUTED);
		} else {
			int cursorPixels = ui.textWidth(text.substring(0, cursor));
			if (cursorPixels - scroll > textWidth - 2) scroll = cursorPixels - textWidth + 2;
			if (cursorPixels - scroll < 0) scroll = cursorPixels;
			if (allSelected && !text.isEmpty()) {
				ui.rect(textX - scroll, textY - 1, ui.textWidth(text), 10, Theme.withAlpha(Theme.ACCENT, 0.45f));
			}

			ui.text(text, textX - scroll, textY, Theme.TEXT);
			if (focused && (System.currentTimeMillis() / 500) % 2 == 0) {
				ui.rect(textX - scroll + cursorPixels, textY - 1, 1, 10, Theme.ACCENT_STRONG);
			}
		}

		ui.popClip();
		if (hot) {
			ui.textCursor();
		}

		offerTooltip(ui);
	}

	@Override
	public boolean mouseClicked(UiContext ui, double mouseX, double mouseY, int button) {
		if (contains(mouseX, mouseY)) {
			ui.focus(this);
			allSelected = false;
			cursor = text.length();
			return true;
		}

		return false;
	}

	@Override
	public boolean keyPressed(UiContext ui, KeyEvent event) {
		Minecraft client = Minecraft.getInstance();

		if (event.isSelectAll()) {
			allSelected = true;
			return true;
		}

		if (event.isCopy()) {
			client.keyboardHandler.setClipboard(text);
			return true;
		}

		if (event.isPaste()) {
			insert(client.keyboardHandler.getClipboard());
			return true;
		}

		switch (event.key()) {
			case GLFW.GLFW_KEY_BACKSPACE -> {
				if (allSelected) {
					replaceAll("");
				} else if (cursor > 0) {
					text = text.substring(0, cursor - 1) + text.substring(cursor);
					cursor--;
					onChange.accept(text);
				}
			}
			case GLFW.GLFW_KEY_DELETE -> {
				if (allSelected) {
					replaceAll("");
				} else if (cursor < text.length()) {
					text = text.substring(0, cursor) + text.substring(cursor + 1);
					onChange.accept(text);
				}
			}
			case GLFW.GLFW_KEY_LEFT -> moveCursor(cursor - 1);
			case GLFW.GLFW_KEY_RIGHT -> moveCursor(cursor + 1);
			case GLFW.GLFW_KEY_HOME -> moveCursor(0);
			case GLFW.GLFW_KEY_END -> moveCursor(text.length());
			case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> onSubmit.accept(text);
			case GLFW.GLFW_KEY_ESCAPE, GLFW.GLFW_KEY_TAB -> ui.focus(null);
			default -> {
				return false;
			}
		}

		return true;
	}

	@Override
	public boolean charTyped(UiContext ui, CharacterEvent event) {
		if (!allowedChars.test(event.codepoint())) {
			return true;
		}

		insert(event.codepointAsString());
		return true;
	}

	private void insert(String value) {
		StringBuilder filtered = new StringBuilder();
		value.codePoints().filter(allowedChars).forEach(filtered::appendCodePoint);
		if (allSelected) {
			text = "";
			cursor = 0;
			allSelected = false;
		}

		String result = text.substring(0, cursor) + filtered + text.substring(cursor);
		if (result.length() > maxLength) {
			return;
		}

		text = result;
		cursor += filtered.length();
		onChange.accept(text);
	}

	private void replaceAll(String value) {
		text = value;
		cursor = value.length();
		allSelected = false;
		onChange.accept(text);
	}

	private void moveCursor(int position) {
		allSelected = false;
		cursor = Math.max(0, Math.min(text.length(), position));
	}

	@Override
	public void onFocusLost() {
		allSelected = false;
	}
}
