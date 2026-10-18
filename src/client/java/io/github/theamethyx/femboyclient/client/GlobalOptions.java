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

	/** Global settings that each profile stores as well, so switching profiles switches them (look and performance). */
	public static final java.util.Set<String> PROFILE_OPTIONS = java.util.Set.of("theme", "custom_accent", "accent", "animations", "reduce_motion",
			"performance_mode");

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

	public final EnumSetting<Theme.Palette> theme = settings.add(new EnumSetting<>("theme", "Theme",
			"Colors of the menu and HUD.", Theme.Palette.FEMBOY));
	public final BooleanSetting customAccent = settings.add(new BooleanSetting("custom_accent", "Custom accent color",
			"Use your own accent color instead of the theme's.", false));
	public final ColorSetting accent = settings.add(new ColorSetting("accent", "Accent color",
			"Accent for highlights, switches and selected items.", 0xA66BFF)).visibleWhen(customAccent::get);

	{
		Runnable applyTheme = () -> Theme.apply(theme.get(), customAccent.get() ? accent.get() : null);
		theme.onChange(value -> applyTheme.run());
		customAccent.onChange(value -> applyTheme.run());
		accent.onChange(value -> applyTheme.run());
	}

	public final BooleanSetting animations = settings.add(new BooleanSetting("animations", "Animations",
			"Smoothly animate panels, toggles and sliders. Turn off for an instant, static interface.", true));
	public final BooleanSetting reduceMotion = settings.add(new BooleanSetting("reduce_motion", "Reduce motion",
			"Keep only short fades: panels and highlights no longer slide or grow.", false)).visibleWhen(animations::get);

	public final BooleanSetting tooltips = settings.add(new BooleanSetting("tooltips", "Tooltips",
			"Show an explanation when hovering over modules and settings.", true));

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
}
