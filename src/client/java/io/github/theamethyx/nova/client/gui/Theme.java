package io.github.theamethyx.nova.client.gui;

/** Colors (ARGB) and spacing shared by every NOVA screen and HUD element. */
public final class Theme {
	private Theme() {
	}

	// Surfaces, darkest to lightest.
	public static final int BACKDROP = 0x8C060608;
	public static final int SIDEBAR = 0xF20D0D10;
	public static final int PANEL = 0xF2131317;
	public static final int SURFACE = 0xFF1A1A20;
	public static final int SURFACE_HOVER = 0xFF212129;
	public static final int SURFACE_ACTIVE = 0xFF272731;
	public static final int BORDER = 0xFF2B2B35;
	public static final int BORDER_STRONG = 0xFF3A3A47;

	// Text.
	public static final int TEXT = 0xFFE9E9EF;
	public static final int TEXT_SECONDARY = 0xFFA3A3B2;
	public static final int TEXT_MUTED = 0xFF6E6E7D;

	// Accent (muted purple) and states.
	public static final int ACCENT = 0xFF9D6EFF;
	public static final int ACCENT_STRONG = 0xFFB58FFF;
	public static final int ACCENT_DIM = 0xFF5E44A0;
	public static final int ACCENT_TINT = 0x269D6EFF;
	public static final int SUCCESS = 0xFF5BD68A;
	public static final int WARNING = 0xFFF2C14E;
	public static final int ERROR = 0xFFF27272;
	public static final int INFO = 0xFF6EA8FF;

	// Spacing.
	public static final int PAD = 6;
	public static final int GAP = 4;
	public static final int ROW = 18;
	public static final int SIDEBAR_WIDTH = 92;
	public static final int HEADER = 28;

	public static int withAlpha(int color, float alpha) {
		int a = Math.round(((color >>> 24) & 0xFF) * Math.max(0, Math.min(1, alpha)));
		return (a << 24) | (color & 0x00FFFFFF);
	}

	public static int lerp(int from, int to, float t) {
		t = Math.max(0, Math.min(1, t));
		int a = (int) (((from >>> 24) & 0xFF) + (((to >>> 24) & 0xFF) - ((from >>> 24) & 0xFF)) * t);
		int r = (int) (((from >> 16) & 0xFF) + (((to >> 16) & 0xFF) - ((from >> 16) & 0xFF)) * t);
		int g = (int) (((from >> 8) & 0xFF) + (((to >> 8) & 0xFF) - ((from >> 8) & 0xFF)) * t);
		int b = (int) ((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * t);
		return (a << 24) | (r << 16) | (g << 8) | b;
	}
}
