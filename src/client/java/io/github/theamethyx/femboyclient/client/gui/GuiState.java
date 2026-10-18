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
	/** Setting sections opened (true) or closed (false) by hand, by "module id/section"; unset ones use the default. */
	public static final java.util.Map<String, Boolean> openSections = new java.util.HashMap<>();
	/** Text of the search field in the header. */
	public static String search = "";
	/** When the notification list was last opened; newer messages count as unread. */
	public static long notificationsSeenAt;

	private GuiState() {
	}
}
