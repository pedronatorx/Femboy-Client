package io.github.theamethyx.femboyclient.client;

import io.github.theamethyx.femboyclient.client.gui.Theme;
import io.github.theamethyx.femboyclient.core.setting.BooleanSetting;
import io.github.theamethyx.femboyclient.core.setting.ColorSetting;
import io.github.theamethyx.femboyclient.core.setting.EnumSetting;
import io.github.theamethyx.femboyclient.core.setting.ModuleSettings;

/** Client-wide options shown on the Configuration screen and stored in {@code femboyclient.json}. */
public final class GlobalOptions {
	/** Size of the Femboy Client menu, independent of Minecraft's GUI scale. Whole-number scales keep text crisp. */
	public enum MenuScale implements EnumSetting.Labeled {
		AUTO("Auto", 0), MINECRAFT("Same as Minecraft", -1), X1("1x", 1), X2("2x", 2), X3("3x", 3), X4("4x", 4);

		private final String label;
		public final int pixels;

		MenuScale(String label, int pixels) {
			this.label = label;
			this.pixels = pixels;
		}

		@Override
		public String label() {
			return label;
		}
	}

	/** How much work the client does for its features; see {@link io.github.theamethyx.femboyclient.client.Performance}. */
	public enum PerformanceMode implements EnumSetting.Labeled {
		PERFORMANCE("Performance"), BALANCED("Balanced"), LOW_IMPACT("Low impact");

		private final String label;

		PerformanceMode(String label) {
			this.label = label;
		}

		@Override
		public String label() {
			return label;
		}
	}

	public static final String THEME_SECTION = "Theme";
	public static final String MOTION_SECTION = "Motion";

	/** Global settings that each profile stores as well, so switching profiles switches them (look and performance). */
	public static final java.util.Set<String> PROFILE_OPTIONS = java.util.Set.of("theme", "custom_accent", "accent", "accent_2", "custom_surface",
			"surface", "custom_text", "text", "panel_opacity", "corners", "accent_icons", "backdrop_art", "hud_background", "animations",
			"reduce_motion", "animation_speed", "performance_mode");

	/** Ids of the settings Theme Studio saves in a theme preset. */
	public static final java.util.List<String> THEME_SETTINGS = java.util.List.of("theme", "custom_accent", "accent", "accent_2", "custom_surface",
			"surface", "custom_text", "text", "panel_opacity", "corners", "accent_icons", "backdrop_art", "hud_background", "animations",
			"reduce_motion", "animation_speed");

	public final ModuleSettings settings = new ModuleSettings();

	{
		settings.section("Performance");
	}

	public final EnumSetting<PerformanceMode> performanceMode = settings.add(new EnumSetting<>("performance_mode", "Performance mode",
			"Performance: fewer updates and shorter lists for the highest frame rate, no menu animations. Balanced: your settings as "
					+ "they are. Low impact: slow update rates, longer pauses between automatic actions, and modules that repeat "
					+ "actions many times a second pause (they say so). Stored in each profile.", PerformanceMode.BALANCED));

	{
		settings.section("Menu");
	}


	public final EnumSetting<MenuScale> menuScale = settings.add(new EnumSetting<>("menu_scale", "Menu scale",
			"Size of this menu. 'Auto' uses the largest sharp size at or below Minecraft's GUI scale that leaves enough room "
					+ "for the layout. Fixed sizes may be cut off on small windows.", MenuScale.AUTO));

	public final BooleanSetting tooltips = settings.add(new BooleanSetting("tooltips", "Tooltips",
			"Show an explanation when hovering over modules and settings.", true));

	{
		settings.section(THEME_SECTION);
	}

	/** Corner shape of panels and controls. */
	public enum CornerStyle implements EnumSetting.Labeled {
		SHARP("Sharp", 0.35f), SOFT("Soft", 1), ROUND("Round", 1.6f);

		private final String label;
		public final float scale;

		CornerStyle(String label, float scale) {
			this.label = label;
			this.scale = scale;
		}

		@Override
		public String label() {
			return label;
		}
	}

	public final EnumSetting<Theme.Palette> theme = settings.add(new EnumSetting<>("theme", "Theme",
			"Base colors of the menu and HUD. The options below adjust it.", Theme.Palette.FEMBOY));
	public final BooleanSetting customAccent = settings.add(new BooleanSetting("custom_accent", "Custom accent colors",
			"Use your own accent colors instead of the theme's.", false));
	public final ColorSetting accent = settings.add(new ColorSetting("accent", "Accent color",
			"Highlights, switches, selected items and the start of gradients.", 0xA66BFF)).visibleWhen(customAccent::get);
	public final ColorSetting accent2 = settings.add(new ColorSetting("accent_2", "Second accent",
			"The far end of gradients and the notification badge.", 0xE070E8)).visibleWhen(customAccent::get);
	public final BooleanSetting customSurface = settings.add(new BooleanSetting("custom_surface", "Custom background",
			"Derive panels, cards and borders from your own color.", false));
	public final ColorSetting surface = settings.add(new ColorSetting("surface", "Background color",
			"Cards and controls use this color; panels and borders are derived from it.", 0x161A2B)).visibleWhen(customSurface::get);
	public final BooleanSetting customText = settings.add(new BooleanSetting("custom_text", "Custom text color",
			"Use your own text color; secondary and muted text are derived from it.", false));
	public final ColorSetting text = settings.add(new ColorSetting("text", "Text color", "Main text color.", 0xEEEBFA)).visibleWhen(customText::get);
	public final io.github.theamethyx.femboyclient.core.setting.NumberSetting panelOpacity = settings.add(
			new io.github.theamethyx.femboyclient.core.setting.NumberSetting("panel_opacity", "Panel opacity",
					"How opaque the menu panel and sidebar are; lower lets the game show through.", 100, 50, 100, 5, "%"));
	public final EnumSetting<CornerStyle> corners = settings.add(new EnumSetting<>("corners", "Corners",
			"Corner shape of panels, cards and controls.", CornerStyle.SOFT));
	public final BooleanSetting accentIcons = settings.add(new BooleanSetting("accent_icons", "Colored icons",
			"Tint sidebar icons with the accent color; off: plain text color.", true));
	public final BooleanSetting backdropArt = settings.add(new BooleanSetting("backdrop_art", "Background art",
			"Soft accent glows and sparkles around the menu.", true));
	public final io.github.theamethyx.femboyclient.core.setting.NumberSetting hudBackground = settings.add(
			new io.github.theamethyx.femboyclient.core.setting.NumberSetting("hud_background", "HUD background",
					"Opacity of HUD panel backgrounds.", 82, 0, 100, 5, "%"));

	{
		settings.section(MOTION_SECTION);
	}

	public final BooleanSetting animations = settings.add(new BooleanSetting("animations", "Animations",
			"Smoothly animate panels, toggles and sliders. Turn off for an instant, static interface.", true));
	public final BooleanSetting reduceMotion = settings.add(new BooleanSetting("reduce_motion", "Reduce motion",
			"Keep only short fades: panels and highlights no longer slide or grow.", false)).visibleWhen(animations::get);
	public final io.github.theamethyx.femboyclient.core.setting.NumberSetting animationSpeed = settings.add(
			new io.github.theamethyx.femboyclient.core.setting.NumberSetting("animation_speed", "Animation speed",
					"How fast animations run: 0.5 is half speed, 2 twice as fast.", 1, 0.5, 2, 0.25, "x")).visibleWhen(animations::get);

	{
		Runnable applyTheme = this::applyTheme;
		for (var setting : java.util.List.of(theme, customAccent, accent, accent2, customSurface, surface, customText, text, panelOpacity, corners,
				accentIcons, backdropArt, hudBackground, animationSpeed)) {
			setting.onChange(value -> applyTheme.run());
		}
	}

	/** Applies the theme settings to {@link Theme} (and the animation speed to {@link io.github.theamethyx.femboyclient.client.gui.Anim}). */
	public void applyTheme() {
		Theme.apply(theme.get(), new Theme.Overrides(customAccent.get() ? accent.get() : null, customAccent.get() ? accent2.get() : null,
				customSurface.get() ? surface.get() : null, customText.get() ? text.get() : null, (float) (panelOpacity.get() / 100)));
		Theme.RADIUS_SCALE = corners.get().scale;
		Theme.ACCENT_ICONS = accentIcons.get();
		Theme.BACKDROP_ART = backdropArt.get();
		Theme.HUD_BACKGROUND = (float) (hudBackground.get() / 100);
		io.github.theamethyx.femboyclient.client.gui.Anim.SPEED = animationSpeed.get().floatValue();
	}

	{
		settings.section("Messages and profiles");
	}

	public final BooleanSetting toggleMessages = settings.add(new BooleanSetting("toggle_messages", "Key bind messages",
			"Show a short message above the hotbar when a key bind turns a module on or off.", true));
	public final BooleanSetting autoProfile = settings.add(new BooleanSetting("auto_profile", "Switch profiles automatically",
			"Switch to another profile when what you hold (or the dimension) matches one of your rules on the Profiles page. "
					+ "Choosing a profile by hand stays in effect until your held item changes to something another rule matches.", false));
	public final io.github.theamethyx.femboyclient.core.setting.NumberSetting profileDelay = settings.add(new io.github.theamethyx.femboyclient.core.setting.NumberSetting(
			"profile_delay", "Switch delay", "The held item must match a rule this long before the profile switches, so scrolling "
					+ "through the hotbar does not flicker between profiles.", 4, 0, 40, 1, "ticks"));
	public final BooleanSetting profileMessages = settings.add(new BooleanSetting("profile_messages", "Profile switch messages",
			"Show the new profile's name above the hotbar after an automatic switch.", true));

	public static final String SMART_SECTION = "Rules and timeline";

	{
		settings.section(SMART_SECTION);
	}

	public final BooleanSetting rulesEnabled = settings.add(new BooleanSetting("rules_enabled", "Rules",
			"Run your rules (Settings, Rules). Off: no rule runs; they are kept.", true));

	/** Which kinds of events the session timeline records. */
	public final java.util.Map<io.github.theamethyx.femboyclient.client.smart.SessionTimeline.Kind, BooleanSetting> timelineKinds = timelineSettings();

	private java.util.Map<io.github.theamethyx.femboyclient.client.smart.SessionTimeline.Kind, BooleanSetting> timelineSettings() {
		var map = new java.util.EnumMap<io.github.theamethyx.femboyclient.client.smart.SessionTimeline.Kind, BooleanSetting>(
				io.github.theamethyx.femboyclient.client.smart.SessionTimeline.Kind.class);
		for (var kind : io.github.theamethyx.femboyclient.client.smart.SessionTimeline.Kind.values()) {
			map.put(kind, settings.add(new BooleanSetting("timeline_" + kind.name().toLowerCase(java.util.Locale.ROOT), "Record: " + kind.label(),
					"Whether the session timeline records " + kind.label().toLowerCase(java.util.Locale.ROOT) + ".", true)));
		}

		return map;
	}

	/** The kinds the timeline records, from the settings above. */
	public java.util.Set<io.github.theamethyx.femboyclient.client.smart.SessionTimeline.Kind> recordedKinds() {
		var set = java.util.EnumSet.noneOf(io.github.theamethyx.femboyclient.client.smart.SessionTimeline.Kind.class);
		timelineKinds.forEach((kind, setting) -> {
			if (setting.get()) set.add(kind);
		});
		return set;
	}
}
