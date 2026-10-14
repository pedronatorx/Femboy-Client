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

		int track = Theme.lerp(Theme.lerp(0xFF2C2C36, 0xFF34343F, h), Theme.lerp(Theme.ACCENT_DIM, Theme.ACCENT, 0.55f + h * 0.25f), t);
		ui.roundRect(x, y, width, height, track);

		int knobSize = height - 4;
		int knobX = x + 2 + Math.round((width - knobSize - 4) * t);
		ui.roundRect(knobX, y + 2, knobSize, knobSize, Theme.lerp(0xFF9A9AA8, 0xFFFFFFFF, t));

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
