package io.github.theamethyx.nova.client;

import io.github.theamethyx.nova.core.setting.BooleanSetting;
import io.github.theamethyx.nova.core.setting.ModuleSettings;

/** Client-wide options shown on the Configuration screen and stored in {@code nova.json}. */
public final class GlobalOptions {
	public final ModuleSettings settings = new ModuleSettings();

	public final BooleanSetting animations = settings.add(new BooleanSetting("animations", "Animations",
			"Smoothly animate panels, toggles and sliders. Turn off for an instant, static interface.", true));

	public final BooleanSetting tooltips = settings.add(new BooleanSetting("tooltips", "Tooltips",
			"Show an explanation when hovering over modules and settings.", true));

	public final BooleanSetting toggleMessages = settings.add(new BooleanSetting("toggle_messages", "Key bind messages",
			"Show a short message above the hotbar when a key bind turns a module on or off.", true));
}
