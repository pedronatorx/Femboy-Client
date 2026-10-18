package io.github.theamethyx.femboyclient.client.gui.widget;

import io.github.theamethyx.femboyclient.client.gui.Anim;
import io.github.theamethyx.femboyclient.client.gui.Theme;
import io.github.theamethyx.femboyclient.client.gui.UiContext;
import io.github.theamethyx.femboyclient.core.setting.NumberSetting;

/** Horizontal slider for a {@link NumberSetting}; click or drag to set, values snap to the step. */
public class Slider extends Widget {
	private final NumberSetting setting;
	private final Anim fill;
	private final Anim hover = new Anim(0, 18);
	private boolean dragging;

	public Slider(NumberSetting setting) {
		this.setting = setting;
		this.fill = new Anim((float) setting.fraction(), 20);
		this.height = 12;
	}

	@Override
	public void render(UiContext ui) {
		boolean hot = hovered(ui) || dragging;
		float h = hover.update(hot ? 1 : 0, ui.frameSeconds(), ui.animate());
		float t = fill.update((float) setting.fraction(), ui.frameSeconds(), ui.animate() && !dragging);

		int trackY = y + height / 2 - 2;
		ui.roundRect(x, trackY, width, 4, Theme.lerp(Theme.SURFACE_ACTIVE, Theme.BORDER_STRONG, h * 0.6f), 2);
		int filled = Math.round((width - 1) * t);
		if (filled > 0) {
			ui.gradientRect(x, trackY, filled + 1, 4, Theme.ACCENT, Theme.lerp(Theme.ACCENT, Theme.ACCENT_2, t), 2);
		}

		int knob = 8 + Math.round(h);
		int knobX = x + filled - knob / 2;
		if (h > 0.01f) {
			ui.roundRect(knobX - 2, y + height / 2 - knob / 2 - 2, knob + 4, knob + 4, Theme.withAlpha(Theme.ACCENT, 0.3f * h), (knob + 4) / 2);
		}

		ui.roundRect(knobX, y + height / 2 - knob / 2, knob, knob, 0xFFFFFFFF, knob / 2);

		if (hot) {
			ui.resizeCursor();
		}

		offerTooltip(ui);
	}

	@Override
	public boolean mouseClicked(UiContext ui, double mouseX, double mouseY, int button) {
		if (button == 0 && UiContext.inside(mouseX, mouseY, x - 2, y, width + 4, height)) {
			dragging = true;
			ui.capture(this);
			apply(mouseX);
			return true;
		}

		return false;
	}

	@Override
	public void mouseDragged(UiContext ui, double mouseX, double mouseY, int button) {
		if (dragging) {
			apply(mouseX);
		}
	}

	@Override
	public void mouseReleased(UiContext ui, double mouseX, double mouseY, int button) {
		dragging = false;
	}

	private void apply(double mouseX) {
		double fraction = Math.max(0, Math.min(1, (mouseX - x) / Math.max(1, width - 1)));
		setting.setSnapped(setting.min() + fraction * (setting.max() - setting.min()));
	}

	/**
	 * Shift + mouse wheel over the slider moves it one step, for precise values without dragging. Without Shift the wheel
	 * keeps scrolling the list, so passing over a slider never changes a value by accident.
	 */
	@Override
	public boolean mouseScrolled(UiContext ui, double mouseX, double mouseY, double amount) {
		var window = net.minecraft.client.Minecraft.getInstance().getWindow();
		boolean shift = com.mojang.blaze3d.platform.InputConstants.isKeyDown(window, org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_SHIFT)
				|| com.mojang.blaze3d.platform.InputConstants.isKeyDown(window, org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_SHIFT);
		if (!shift || !contains(mouseX, mouseY) || amount == 0) {
			return false;
		}

		setting.set(setting.get() + Math.signum(amount) * setting.step());
		return true;
	}
}
