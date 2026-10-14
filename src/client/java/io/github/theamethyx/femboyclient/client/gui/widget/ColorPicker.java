package io.github.theamethyx.femboyclient.client.gui.widget;

import java.util.ArrayList;
import java.util.List;

import io.github.theamethyx.femboyclient.client.gui.Theme;
import io.github.theamethyx.femboyclient.client.gui.UiContext;
import io.github.theamethyx.femboyclient.core.setting.ColorSetting;

/** A row of color swatches. A custom color from the config file is shown first so it stays visible. */
public class ColorPicker extends Widget {
	private static final int SWATCH = 10;
	private static final int GAP = 3;

	private final ColorSetting setting;

	public ColorPicker(ColorSetting setting) {
		this.setting = setting;
		this.height = SWATCH;
	}

	private List<Integer> swatches() {
		List<Integer> all = new ArrayList<>();
		if (!ColorSetting.PRESETS.contains(setting.get())) {
			all.add(setting.get());
		}

		all.addAll(ColorSetting.PRESETS);
		return all;
	}

	/** Swatch index under the mouse, or -1. */
	private int indexAt(double mouseX, double mouseY, int count) {
		if (mouseY < y || mouseY >= y + SWATCH || mouseX < x) {
			return -1;
		}

		int index = (int) ((mouseX - x) / (SWATCH + GAP));
		boolean onSwatch = (mouseX - x) - index * (SWATCH + GAP) < SWATCH;
		return onSwatch && index < count && x + index * (SWATCH + GAP) + SWATCH <= x + width ? index : -1;
	}

	@Override
	public void render(UiContext ui) {
		List<Integer> swatches = swatches();
		int hot = indexAt(ui.mouseX(), ui.mouseY(), swatches.size());
		for (int i = 0; i < swatches.size(); i++) {
			int sx = x + i * (SWATCH + GAP);
			if (sx + SWATCH > x + width) {
				break;
			}

			int color = swatches.get(i);
			boolean selected = color == setting.get();
			if (selected) {
				ui.roundRect(sx - 2, y - 2, SWATCH + 4, SWATCH + 4, Theme.TEXT);
				ui.roundRect(sx - 1, y - 1, SWATCH + 2, SWATCH + 2, Theme.SURFACE);
			} else if (i == hot && ui.hovered(x, y, width, height)) {
				ui.roundRect(sx - 1, y - 1, SWATCH + 2, SWATCH + 2, Theme.BORDER_STRONG);
			}

			ui.roundRect(sx, y, SWATCH, SWATCH, color);
		}

		if (hot >= 0 && ui.hovered(x, y, width, height)) {
			ui.pointer();
			ui.tooltip(this, ColorSetting.hex(swatches.get(hot)));
		}
	}

	@Override
	public boolean mouseClicked(UiContext ui, double mouseX, double mouseY, int button) {
		List<Integer> swatches = swatches();
		int index = indexAt(mouseX, mouseY, swatches.size());
		if (button != 0 || index < 0) {
			return false;
		}

		setting.set(swatches.get(index));
		return true;
	}
}
