package io.github.theamethyx.nova.client.hud;

import io.github.theamethyx.nova.core.setting.EnumSetting;

public enum HudAnchor implements EnumSetting.Labeled {
	TOP_LEFT("Top left"), TOP_RIGHT("Top right"), BOTTOM_LEFT("Bottom left"), BOTTOM_RIGHT("Bottom right");

	private final String label;

	HudAnchor(String label) {
		this.label = label;
	}

	@Override
	public String label() {
		return label;
	}

	public boolean right() {
		return this == TOP_RIGHT || this == BOTTOM_RIGHT;
	}

	public boolean bottom() {
		return this == BOTTOM_LEFT || this == BOTTOM_RIGHT;
	}
}
