package io.github.theamethyx.femboyclient.client.gui;

import io.github.theamethyx.femboyclient.core.setting.EnumSetting;

/**
 * Colors (ARGB) and spacing shared by every Femboy Client screen and HUD element. The colors come from the selected
 * {@link Palette} (Settings → Client options → Theme) and an optional custom accent; {@link #apply} switches them at
 * runtime, so widgets read these fields when they draw.
 */
public final class Theme {
	private Theme() {
	}

	/** Built-in themes. Each sets surfaces, text and the accent pair; state colors are shared. */
	public enum Palette implements EnumSetting.Labeled {
		FEMBOY("Femboy (navy, purple, magenta)",
				0x8C05060C, 0xF20A0B16, 0xF20F1120, 0xFF161829, 0xFF1D2034, 0xFF24283F, 0xFF2A2E47, 0xFF3A3F5E,
				0xFFECEAF6, 0xFFA7A5BF, 0xFF6F6D88, 0xFFA66BFF, 0xFFE070E8),
		CHARCOAL("Charcoal (classic)",
				0x8C060608, 0xF20D0D10, 0xF2131317, 0xFF1A1A20, 0xFF212129, 0xFF272731, 0xFF2B2B35, 0xFF3A3A47,
				0xFFE9E9EF, 0xFFA3A3B2, 0xFF6E6E7D, 0xFF9D6EFF, 0xFFB58FFF),
		MIDNIGHT_MAGENTA("Midnight magenta",
				0x8C070409, 0xF20E0812, 0xF214101A, 0xFF1C1524, 0xFF241B2E, 0xFF2D2138, 0xFF33263F, 0xFF473552,
				0xFFF1E9F4, 0xFFB3A4BB, 0xFF7B6A84, 0xFFE05CC8, 0xFFFF8CE6),
		DEEP_OCEAN("Deep ocean",
				0x8C03070C, 0xF2070D16, 0xF20B1320, 0xFF111B2B, 0xFF172336, 0xFF1D2B41, 0xFF213048, 0xFF2E4262,
				0xFFE6EEF8, 0xFF9DB0C8, 0xFF65788F, 0xFF5B8CFF, 0xFF9C7BFF);

		private final String label;
		private final int[] colors;

		Palette(String label, int... colors) {
			this.label = label;
			this.colors = colors;
		}

		@Override
		public String label() {
			return label;
		}
	}

	// Surfaces, darkest to lightest.
	public static int BACKDROP;
	public static int SIDEBAR;
	public static int PANEL;
	public static int SURFACE;
	public static int SURFACE_HOVER;
	public static int SURFACE_ACTIVE;
	public static int BORDER;
	public static int BORDER_STRONG;

	// Text.
	public static int TEXT;
	public static int TEXT_SECONDARY;
	public static int TEXT_MUTED;

	// Accent pair and states.
	public static int ACCENT;
	public static int ACCENT_STRONG;
	public static int ACCENT_DIM;
	public static int ACCENT_TINT;
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

	private static Palette current;

	static {
		apply(Palette.FEMBOY, null);
	}

	/**
	 * Switches every color to {@code palette}. {@code customAccent} (RGB or ARGB) replaces the palette's accent when it is
	 * not {@code null}; the strong and dim variants are derived from it.
	 */
	public static void apply(Palette palette, Integer customAccent) {
		current = palette;
		int[] c = palette.colors;
		BACKDROP = c[0];
		SIDEBAR = c[1];
		PANEL = c[2];
		SURFACE = c[3];
		SURFACE_HOVER = c[4];
		SURFACE_ACTIVE = c[5];
		BORDER = c[6];
		BORDER_STRONG = c[7];
		TEXT = c[8];
		TEXT_SECONDARY = c[9];
		TEXT_MUTED = c[10];
		ACCENT = customAccent == null ? c[11] : 0xFF000000 | customAccent;
		ACCENT_STRONG = customAccent == null ? c[12] : lerp(ACCENT, 0xFFFFFFFF, 0.3f);
		ACCENT_DIM = lerp(ACCENT, 0xFF000000, 0.4f);
		ACCENT_TINT = withAlpha(ACCENT, 0.15f);
	}

	public static Palette current() {
		return current;
	}

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
