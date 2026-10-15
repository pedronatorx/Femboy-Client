package io.github.theamethyx.femboyclient.core.module;

/** The sidebar sections of the ClickGUI, in display order. Each section lists its modules. */
public enum ModuleCategory {
	COMBAT("Combat", "combat", "Fighting helpers such as automatic weapon swapping."),
	VISUALS("Visuals", "visual", "Highlights, trackers, brightness and the free camera."),
	WORLD("World", "analysis", "World information: geode analysis and waypoints."),
	PLAYER("Player", "player", "Player list, alerts and navigation."),
	HUD("HUD", "hud", "On-screen information such as coordinates and performance."),
	DEVELOPER("Developer", "developer", "Tools for writing and testing small Java programs.");

	private final String label;
	private final String icon;
	private final String description;

	ModuleCategory(String label, String icon, String description) {
		this.label = label;
		this.icon = icon;
		this.description = description;
	}

	public String label() {
		return label;
	}

	/** Name of the GUI icon texture for this category. */
	public String icon() {
		return icon;
	}

	public String description() {
		return description;
	}
}
