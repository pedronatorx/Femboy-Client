package io.github.theamethyx.femboyclient.client.gui;

import io.github.theamethyx.femboyclient.core.module.ModuleCategory;

/** UI choices remembered while the game runs (not saved to disk): last page, selection, filters. */
public final class GuiState {
	public static int page;
	/** Module to select when a category page is shown next (set when a module is opened from Home). */
	public static String selectedModule;
	public static ModuleCategory moduleFilter;
	public static int settingsTab;
	/** Module whose settings panel is open on Home, or null. */
	public static String homeSettings;

	private GuiState() {
	}
}
