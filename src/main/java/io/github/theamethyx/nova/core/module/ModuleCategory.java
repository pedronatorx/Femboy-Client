package io.github.theamethyx.nova.core.module;

public enum ModuleCategory {
	HUD("HUD", "hud", "On-screen information such as coordinates and performance."),
	VISUAL("Visual", "visual", "World overlays such as waypoints, chunk borders and test-fixture highlights."),
	WORLD_ANALYSIS("World Analysis", "analysis", "Research tools that read block states in controlled test worlds."),
	UTILITY("Utility", "utility", "Camera and testing helpers.");

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
