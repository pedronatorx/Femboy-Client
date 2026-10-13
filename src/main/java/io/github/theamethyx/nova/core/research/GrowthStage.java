package io.github.theamethyx.nova.core.research;

/** The four amethyst growth objects, in growth order. */
public enum GrowthStage {
	SMALL_BUD("Small bud"),
	MEDIUM_BUD("Medium bud"),
	LARGE_BUD("Large bud"),
	CLUSTER("Cluster");

	private final String label;

	GrowthStage(String label) {
		this.label = label;
	}

	public String label() {
		return label;
	}
}
