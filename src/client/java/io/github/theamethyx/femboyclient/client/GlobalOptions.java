package io.github.theamethyx.femboyclient.client;

import io.github.theamethyx.femboyclient.core.setting.BooleanSetting;
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

	public final ModuleSettings settings = new ModuleSettings();

	public final EnumSetting<MenuScale> menuScale = settings.add(new EnumSetting<>("menu_scale", "Menu scale",
			"Size of this menu. 'Auto' uses the largest sharp size at or below Minecraft's GUI scale that leaves enough room "
					+ "for the layout. Fixed sizes may be cut off on small windows.", MenuScale.AUTO));

	public final BooleanSetting animations = settings.add(new BooleanSetting("animations", "Animations",
			"Smoothly animate panels, toggles and sliders. Turn off for an instant, static interface.", true));

	public final BooleanSetting tooltips = settings.add(new BooleanSetting("tooltips", "Tooltips",
			"Show an explanation when hovering over modules and settings.", true));

	public final BooleanSetting toggleMessages = settings.add(new BooleanSetting("toggle_messages", "Key bind messages",
			"Show a short message above the hotbar when a key bind turns a module on or off.", true));
	public final BooleanSetting autoProfile = settings.add(new BooleanSetting("auto_profile", "Switch profiles automatically",
			"Switch to another profile when what you hold (or the dimension) matches one of your rules on the Profiles page. "
					+ "Choosing a profile by hand stays in effect until your held item changes to something another rule matches.", false));
	public final BooleanSetting profileMessages = settings.add(new BooleanSetting("profile_messages", "Profile switch messages",
			"Show the new profile's name above the hotbar after an automatic switch.", true));
}
