package io.github.theamethyx.femboyclient.client.gui.widget;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import io.github.theamethyx.femboyclient.client.gui.Anim;
import io.github.theamethyx.femboyclient.client.gui.Theme;
import io.github.theamethyx.femboyclient.client.gui.UiContext;

/** An on/off switch with a sliding knob. */
public class ToggleSwitch extends Widget {
	public static final int WIDTH = 20;
	public static final int HEIGHT = 10;

	private final BooleanSupplier value;
	private final Consumer<Boolean> setter;
	private final Anim position;
	private final Anim hover = new Anim(0, 18);

	public ToggleSwitch(BooleanSupplier value, Consumer<Boolean> setter) {
		this.value = value;
		this.setter = setter;
		this.position = new Anim(value.getAsBoolean() ? 1 : 0, 16);
		this.width = WIDTH;
		this.height = HEIGHT;
	}

	@Override
	public void render(UiContext ui) {
		boolean on = value.getAsBoolean();
		float t = position.update(on ? 1 : 0, ui.frameSeconds(), ui.animate());
		float h = hover.update(hovered(ui) ? 1 : 0, ui.frameSeconds(), ui.animate());

		// A pill: grey when off, the accent fading in when on. The knob's side shows the state too, not only the color.
		int off = Theme.lerp(Theme.SURFACE_ACTIVE, Theme.BORDER_STRONG, h * 0.6f);
		ui.roundRect(x, y, width, height, off, height / 2);
		if (t > 0.01f) {
			ui.roundRect(x, y, width, height, Theme.withAlpha(Theme.lerp(Theme.ACCENT, Theme.ACCENT_STRONG, h * 0.4f), t), height / 2);
		}

		int knobSize = height - 4;
		int knobX = x + 2 + Math.round((width - knobSize - 4) * t);
		ui.roundRect(knobX, y + 2, knobSize, knobSize, Theme.lerp(0xFFB4B2C8, 0xFFFFFFFF, Math.max(t, h * 0.5f)), knobSize / 2);

		if (hovered(ui)) {
			ui.pointer();
		}

		offerTooltip(ui);
	}

	@Override
	public boolean mouseClicked(UiContext ui, double mouseX, double mouseY, int button) {
		if (button == 0 && contains(mouseX, mouseY)) {
			setter.accept(!value.getAsBoolean());
			return true;
		}

		return false;
	}
}
