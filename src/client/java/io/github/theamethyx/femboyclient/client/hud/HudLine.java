package io.github.theamethyx.femboyclient.client.hud;

import io.github.theamethyx.femboyclient.client.gui.Theme;

/**
 * One HUD row: a muted label and a value. {@code accent} draws a small colored bar at the edge,
 * used by the module list.
 */
public record HudLine(String label, String value, int valueColor, int accent) {
	public static HudLine of(String label, String value) {
		return new HudLine(label, value, Theme.TEXT, 0);
	}

	public static HudLine of(String label, String value, int color) {
		return new HudLine(label, value, color, 0);
	}

	public static HudLine text(String value, int color) {
		return new HudLine("", value, color, 0);
	}
}
