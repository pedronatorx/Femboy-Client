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
		FEMBOY("Default purple",
				0xA006070D, 0xF40B0C17, 0xF20F1121, 0xFF161A2B, 0xFF1E2237, 0xFF262B45, 0xFF252941, 0xFF3A3F63,
				0xFFEEEBFA, 0xFFA9A6C4, 0xFF6F6C8C, 0xFF9D6BFF, 0xFFE070E8),
		DEEP_VIOLET("Deep violet",
				0xA008040F, 0xF40E0819, 0xF2130C22, 0xFF1C1430, 0xFF261B3F, 0xFF30224F, 0xFF2F2448, 0xFF4A3870,
				0xFFF0EAFC, 0xFFB3A6CF, 0xFF7A6C96, 0xFF8B5CF6, 0xFFC084FC),
		MIDNIGHT("Midnight",
				0xB0020308, 0xF6050710, 0xF4080B16, 0xFF0E1220, 0xFF151A2C, 0xFF1C2238, 0xFF1A2033, 0xFF2D3550,
				0xFFE4E8F4, 0xFF98A0B8, 0xFF5F6780, 0xFF7C6CFF, 0xFFD06BE0),
		CHARCOAL("Minimal dark",
				0x8C060608, 0xF20D0D10, 0xF2131317, 0xFF1A1A20, 0xFF212129, 0xFF272731, 0xFF2B2B35, 0xFF3A3A47,
				0xFFE9E9EF, 0xFFA3A3B2, 0xFF6E6E7D, 0xFF9D6EFF, 0xFFB58FFF),
		HIGH_CONTRAST("High contrast",
				0xD0000000, 0xFF000000, 0xFF05050A, 0xFF111118, 0xFF26263A, 0xFF34344F, 0xFF8A8AA8, 0xFFFFFFFF,
				0xFFFFFFFF, 0xFFE6E6F0, 0xFFC0C0D0, 0xFFC9A2FF, 0xFFFF8CF0),
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

		/** Card color, accent and second accent, for swatches. */
		public int[] swatch() {
			return new int[] {colors[3], colors[11], colors[12]};
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

	/** Second accent, the far end of accent gradients (magenta in the default theme). */
	public static int ACCENT_2;

	// Spacing.
	public static final int PAD = 6;
	public static final int GAP = 4;
	public static final int ROW = 18;
	public static final int SIDEBAR_WIDTH = 92;
	public static final int HEADER = 28;

	private static Palette current;

	/** Corner radii are multiplied by this (Theme Studio's corner style). */
	public static float RADIUS_SCALE = 1;
	/** Opacity of HUD panel backgrounds (Theme Studio). */
	public static float HUD_BACKGROUND = 0.82f;
	/** Whether sidebar and page icons take the accent color (false: plain text color). */
	public static boolean ACCENT_ICONS = true;
	/** Whether the menu draws its backdrop glows and sparkles. */
	public static boolean BACKDROP_ART = true;

	/**
	 * Theme Studio's choices on top of a palette. Colors are RGB (or ARGB) or {@code null} for the palette's own;
	 * {@code panelOpacity} applies to the menu's panel and sidebar.
	 */
	public record Overrides(Integer accent, Integer accent2, Integer surface, Integer text, float panelOpacity) {
		public static final Overrides NONE = new Overrides(null, null, null, null, 1);
	}

	static {
		apply(Palette.FEMBOY, Overrides.NONE);
	}

	/**
	 * Switches every color to {@code palette}. {@code customAccent} (RGB or ARGB) replaces the palette's accent when it is
	 * not {@code null}; the strong and dim variants are derived from it.
	 */
	public static void apply(Palette palette, Integer customAccent) {
		apply(palette, new Overrides(customAccent, null, null, null, 1));
	}

	/** Switches every color to {@code palette} with Theme Studio's overrides. */
	public static void apply(Palette palette, Overrides overrides) {
		Integer customAccent = overrides.accent();
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
		ACCENT_2 = customAccent == null ? c[12] : lerp(ACCENT, 0xFFFF6FD8, 0.55f);
		if (overrides.accent2() != null) {
			ACCENT_2 = 0xFF000000 | overrides.accent2();
		}

		if (overrides.surface() != null) {
			// Derive the surface ladder from one color, keeping the palette's alpha for the translucent layers.
			int base = 0xFF000000 | overrides.surface();
			SIDEBAR = (c[1] & 0xFF000000) | (lerp(base, 0xFF000000, 0.35f) & 0xFFFFFF);
			PANEL = (c[2] & 0xFF000000) | (lerp(base, 0xFF000000, 0.2f) & 0xFFFFFF);
			SURFACE = base;
			SURFACE_HOVER = lerp(base, 0xFFFFFFFF, 0.07f);
			SURFACE_ACTIVE = lerp(base, 0xFFFFFFFF, 0.13f);
			BORDER = lerp(base, 0xFFFFFFFF, 0.1f);
			BORDER_STRONG = lerp(base, 0xFFFFFFFF, 0.22f);
		}

		if (overrides.text() != null) {
			TEXT = 0xFF000000 | overrides.text();
			TEXT_SECONDARY = lerp(TEXT, PANEL | 0xFF000000, 0.3f);
			TEXT_MUTED = lerp(TEXT, PANEL | 0xFF000000, 0.52f);
		}

		float opacity = Math.clamp(overrides.panelOpacity(), 0.5f, 1);
		if (opacity < 1) {
			PANEL = withAlpha(PANEL, opacity);
			SIDEBAR = withAlpha(SIDEBAR, opacity);
		}
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
