package io.github.theamethyx.femboyclient.client.gui;

import io.github.theamethyx.femboyclient.core.module.ModuleCategory;

/** UI choices remembered while the game runs (not saved to disk): last page, selection, filters. */
public final class GuiState {
	public static int page;
	public static String selectedModule;
	public static ModuleCategory moduleFilter;
	public static String moduleSearch = "";
	public static String selectedResult;
	public static int resultsTab;
	public static String selectedEvaluation;
	public static String selectedIndicator;

	private GuiState() {
	}
}
