package io.github.theamethyx.nova.client.gui;

import io.github.theamethyx.nova.core.module.ModuleCategory;

/** UI choices remembered while the game runs (not saved to disk): last page, selection, filters. */
public final class GuiState {
	public static int page;
	public static String selectedModule;
	public static ModuleCategory moduleFilter;
	public static String moduleSearch = "";

	private GuiState() {
	}
}
