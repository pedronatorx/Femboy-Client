package io.github.theamethyx.femboyclient.client.render;

import io.github.theamethyx.femboyclient.core.setting.EnumSetting;

/** The fixed palette offered for world overlays, chosen to stay readable on most terrain. */
public enum OverlayColor implements EnumSetting.Labeled {
	PURPLE("Purple", 0xFF9D6EFF),
	CYAN("Cyan", 0xFF4FD1E8),
	GREEN("Green", 0xFF5BD68A),
	AMBER("Amber", 0xFFF2C14E),
	RED("Red", 0xFFF27272),
	WHITE("White", 0xFFF2F2F7);

	private final String label;
	public final int argb;

	OverlayColor(String label, int argb) {
		this.label = label;
		this.argb = argb;
	}

	@Override
	public String label() {
		return label;
	}

	public int withAlpha(float alpha) {
		return (Math.round(Math.max(0, Math.min(1, alpha)) * 255) << 24) | (argb & 0xFFFFFF);
	}
}
