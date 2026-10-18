package io.github.theamethyx.femboyclient.client.gui.widget;

import io.github.theamethyx.femboyclient.client.gui.Theme;
import io.github.theamethyx.femboyclient.client.gui.UiContext;
import io.github.theamethyx.femboyclient.core.setting.BooleanSetting;
import io.github.theamethyx.femboyclient.core.setting.ColorSetting;
import io.github.theamethyx.femboyclient.core.setting.EnumSetting;
import io.github.theamethyx.femboyclient.core.setting.ModuleSettings;
import io.github.theamethyx.femboyclient.core.setting.NumberSetting;
import io.github.theamethyx.femboyclient.core.setting.Setting;

/**
 * A small live picture of an ESP module's look, drawn from its current settings: a near and a far target with outline,
 * fill, label and health bar, and target lines from the crosshair with their color, thickness, opacity and fading. It reads
 * the settings by their ids (shared by Entity, Block and Storage ESP), so it follows every change at once. It is an
 * illustration in the menu, not a rendering of the world.
 */
public class EspPreview extends Widget {
	private static final int HEIGHT = 70;
	private final ModuleSettings settings;

	public EspPreview(ModuleSettings settings) {
		this.settings = settings;
	}

	/** Whether a module's settings are ESP-like enough to preview (highlight boxes, optionally with target lines). */
	public static boolean applies(ModuleSettings settings) {
		return settings.get("outline") instanceof BooleanSetting && settings.get("fill") instanceof BooleanSetting;
	}

	@Override
	public int preferredHeight(UiContext ui, int width) {
		return HEIGHT;
	}

	private boolean on(String id, boolean fallback) {
		Setting<?> setting = settings.get(id);
		return setting instanceof BooleanSetting bool ? bool.get() : fallback;
	}

	private double number(String id, double fallback) {
		Setting<?> setting = settings.get(id);
		return setting instanceof NumberSetting number ? number.get() : fallback;
	}

	/** An opacity setting as 0..1 (some modules use percent). */
	private float opacity(String id, float fallback) {
		double value = number(id, fallback);
		return (float) Math.clamp(value > 1 ? value / 100 : value, 0, 1);
	}

	/** The color of the first target group (players, or the custom color), or the accent. */
	private int targetColor() {
		for (String id : new String[] {"color_players", "color", "color_chests"}) {
			if (settings.get(id) instanceof ColorSetting color) {
				return 0xFF000000 | color.get();
			}
		}

		return Theme.ACCENT;
	}

	private int lineColor(int target) {
		if (settings.get("tracer_color_mode") instanceof EnumSetting<?> mode && mode.get().name().equals("CUSTOM")
				&& settings.get("tracer_color") instanceof ColorSetting custom) {
			return 0xFF000000 | custom.get();
		}

		return target;
	}

	@Override
	public void render(UiContext ui) {
		ui.roundRect(x, y, width, height, Theme.withAlpha(0xFF000000, 0.25f), Theme.RADIUS_CONTROL);
		ui.outline(x, y, width, height, Theme.BORDER, Theme.RADIUS_CONTROL);
		ui.textScaled("Preview", x + 5, y + 4, ui.small(), Theme.TEXT_MUTED);

		int target = targetColor();
		int crossX = x + 22;
		int crossY = y + height / 2 + 6;
		// Near target (large) and far target (small, higher up).
		int nearW = 22, nearH = 34;
		int nearX = x + width - 54, nearY = y + height - nearH - 6;
		int farW = 10, farH = 15;
		int farX = x + width / 2 - 4, farY = y + 14;

		boolean lines = on("tracers", false);
		if (lines) {
			int color = lineColor(target);
			float alpha = opacity("tracer_opacity", 0.6f);
			int thickness = Math.max(1, (int) Math.round(number("tracer_width", 1)));
			boolean fade = on("tracer_fade", true);
			line(ui, crossX, crossY, nearX + nearW / 2, nearY + nearH / 2, Theme.withAlpha(color, alpha), thickness);
			line(ui, crossX, crossY, farX + farW / 2, farY + farH / 2, Theme.withAlpha(color, fade ? alpha * 0.4f : alpha), thickness);
		}

		box(ui, nearX, nearY, nearW, nearH, target);
		box(ui, farX, farY, farW, farH, target);

		// Crosshair.
		ui.rect(crossX - 3, crossY, 7, 1, Theme.TEXT);
		ui.rect(crossX, crossY - 3, 1, 7, Theme.TEXT);

		boolean label = on("names", false) || on("labels", false);
		if (label) {
			String text = (settings.get("names") != null ? "Player" : "Chest") + (on("distance", true) ? "  12 m" : "");
			float scale = (float) Math.clamp(number("name_scale", 1) * ui.small(), 0.5, 1);
			int w = Math.round(ui.textWidth(text) * scale) + 6;
			int lx = nearX + nearW / 2 - w / 2;
			int ly = nearY - 12;
			ui.roundRect(lx, ly, w, 10, Theme.withAlpha(0xFF101014, Math.max(0.25f, opacity("label_background", 0.55f))), 2);
			ui.textScaled(text, lx + 3, ly + (10 - 8 * scale) / 2, scale, on("colored_names", false) ? target : 0xFFFFFFFF);
		}

		if (on("health", false)) {
			ui.rect(nearX - 4, nearY, 2, nearH, Theme.withAlpha(0xFF000000, 0.5f));
			ui.rect(nearX - 4, nearY + nearH / 3, 2, nearH - nearH / 3, Theme.SUCCESS);
		}

		if (hovered(ui)) {
			ui.tooltip(this, "How highlights look with the settings below: outline, fill, label" + (settings.get("tracers") != null ? " and target lines" : "")
					+ ". The far target shows fading. Changes appear here at once.");
		}
	}

	private void box(UiContext ui, int bx, int by, int bw, int bh, int color) {
		if (on("fill", false)) {
			ui.rect(bx, by, bw, bh, Theme.withAlpha(color, Math.max(0.12f, opacity("fill_opacity", 0.2f))));
		}

		if (on("outline", true)) {
			float alpha = opacity(settings.get("outline_opacity") != null ? "outline_opacity" : "opacity", 0.9f);
			int t = Math.max(1, Math.min(3, (int) Math.round(number("outline_width", 1))));
			int c = Theme.withAlpha(color, Math.max(0.2f, alpha));
			ui.rect(bx, by, bw, t, c);
			ui.rect(bx, by + bh - t, bw, t, c);
			ui.rect(bx, by + t, t, bh - 2 * t, c);
			ui.rect(bx + bw - t, by + t, t, bh - 2 * t, c);
		}
	}

	/** A straight line of small squares (the menu has no line primitive; a preview needs only a few dozen). */
	private static void line(UiContext ui, int x0, int y0, int x1, int y1, int color, int thickness) {
		int steps = Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0));
		for (int i = 0; i <= steps; i += 1) {
			int px = x0 + (x1 - x0) * i / Math.max(1, steps);
			int py = y0 + (y1 - y0) * i / Math.max(1, steps);
			ui.rect(px, py, thickness, thickness, color);
		}
	}
}
