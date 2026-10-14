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

		int trackY = y + height / 2 - 1;
		ui.rect(x, trackY, width, 3, Theme.lerp(0xFF2C2C36, 0xFF363642, h));
		int filled = Math.round((width - 1) * t);
		ui.rect(x, trackY, filled + 1, 3, Theme.lerp(Theme.ACCENT_DIM, Theme.ACCENT, 0.6f + 0.4f * h));

		int knobX = x + filled - 2;
		ui.roundRect(knobX, y + height / 2 - 4, 5, 8, Theme.lerp(0xFFD9D9E3, 0xFFFFFFFF, h));

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
}
