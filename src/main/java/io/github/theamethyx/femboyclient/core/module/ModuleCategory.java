package io.github.theamethyx.femboyclient.core.module;

/** The sidebar sections of the ClickGUI, in display order. Each section lists its modules. */
public enum ModuleCategory {
	COMBAT("Combat", "combat", "Combat information: target, weapon timing, clicks, combo and explosions, plus Auto Attribute Swap."),
	MOVEMENT("Movement", "movement", "Free camera, speed, falls and elytra flight."),
	VISUALS("Visuals", "visual", "Highlights, hitboxes, projectile paths, brightness and chunk borders."),
	INVENTORY("Inventory", "inventory", "Item counters, refill reminders, equipment durability, health and effects."),
	TRACKERS("Trackers", "trackers", "Player list, trackers, alerts and waypoints."),
	WORLD("World", "analysis", "World information: geode analysis."),
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
