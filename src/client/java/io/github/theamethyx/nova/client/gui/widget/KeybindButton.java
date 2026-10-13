package io.github.theamethyx.nova.client.gui.widget;

import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

import org.lwjgl.glfw.GLFW;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.input.KeyEvent;

import io.github.theamethyx.nova.client.gui.Icon;
import io.github.theamethyx.nova.client.gui.Theme;
import io.github.theamethyx.nova.client.gui.UiContext;
import io.github.theamethyx.nova.core.module.Module;

/**
 * Shows a module's key. Click, then press a key to bind it; Escape cancels and Backspace or
 * Delete removes the binding.
 */
public class KeybindButton extends Widget {
	private final IntSupplier key;
	private final IntConsumer setter;

	public KeybindButton(IntSupplier key, IntConsumer setter) {
		this.key = key;
		this.setter = setter;
		this.height = 14;
	}

	public static String keyName(int key) {
		if (key == Module.NO_KEY) {
			return "None";
		}

		return InputConstants.Type.KEYSYM.getOrCreate(key).getDisplayName().getString();
	}

	@Override
	public void render(UiContext ui) {
		boolean listening = ui.isFocused(this);
		boolean hot = hovered(ui);
		ui.roundRect(x, y, width, height, listening ? Theme.ACCENT_TINT : Theme.lerp(Theme.SURFACE, Theme.SURFACE_HOVER, hot ? 1 : 0));
		ui.outline(x, y, width, height, listening ? Theme.ACCENT : hot ? Theme.BORDER_STRONG : Theme.BORDER);
		ui.icon(Icon.KEY, x + 4, y + (height - 9) / 2, 9, listening ? Theme.ACCENT : Theme.TEXT_MUTED);

		String label = listening ? "Press a key..." : keyName(key.getAsInt());
		int color = listening ? Theme.ACCENT_STRONG : key.getAsInt() == Module.NO_KEY ? Theme.TEXT_MUTED : Theme.TEXT;
		ui.textClipped(label, x + 16, y + (height - 8) / 2, width - 20, color);

		if (hot) {
			ui.pointer();
		}

		offerTooltip(ui);
	}

	@Override
	public boolean mouseClicked(UiContext ui, double mouseX, double mouseY, int button) {
		if (contains(mouseX, mouseY)) {
			ui.focus(ui.isFocused(this) ? null : this);
			return true;
		}

		return false;
	}

	@Override
	public boolean keyPressed(UiContext ui, KeyEvent event) {
		int pressed = event.key();
		if (pressed == GLFW.GLFW_KEY_ESCAPE) {
			// Cancel without changing the binding.
		} else if (pressed == GLFW.GLFW_KEY_BACKSPACE || pressed == GLFW.GLFW_KEY_DELETE) {
			setter.accept(Module.NO_KEY);
		} else if (Module.isValidKey(pressed)) {
			setter.accept(pressed);
		} else {
			return true;
		}

		ui.focus(null);
		return true;
	}
}
