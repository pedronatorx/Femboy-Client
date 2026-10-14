package io.github.theamethyx.femboyclient.core.setting;

import java.util.List;
import java.util.Locale;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

/**
 * An opaque RGB color, stored as {@code "#RRGGBB"}. {@link #get()} returns {@code 0xFFRRGGBB}; renderers
 * apply their own alpha. The GUI offers {@link #PRESETS}, but any valid hex value from a config file is
 * accepted.
 */
public final class ColorSetting extends Setting<Integer> {
	/** Swatches offered in the GUI: the client's purple accent first, then distinct hues. */
	public static final List<Integer> PRESETS = List.of(
			0xFF9D6EFF, 0xFFE070C8, 0xFFF27272, 0xFFF29E4C, 0xFFF2C14E,
			0xFF5BD68A, 0xFF4CC9C0, 0xFF6EA8FF, 0xFFE9E9EF, 0xFF8A8A99);

	public ColorSetting(String id, String name, String description, int defaultRgb) {
		super(id, name, description, 0xFF000000 | defaultRgb);
		requireValidDefault();
	}

	@Override
	public String validate(Integer candidate) {
		return (candidate >>> 24) == 0xFF ? null : "must be an opaque color";
	}

	@Override
	public JsonElement toJson() {
		return new JsonPrimitive(hex(get()));
	}

	@Override
	protected Integer parse(JsonElement json) {
		if (!json.isJsonPrimitive() || !json.getAsJsonPrimitive().isString()) {
			throw new IllegalArgumentException("expected a color like \"#9D6EFF\"");
		}

		String raw = json.getAsString().trim();
		if (!raw.matches("#[0-9a-fA-F]{6}")) {
			throw new IllegalArgumentException("'" + raw + "' is not a color like \"#9D6EFF\"");
		}

		return 0xFF000000 | Integer.parseInt(raw.substring(1), 16);
	}

	@Override
	public String displayValue() {
		return hex(get());
	}

	/** The color with the given opacity (0..1), for translucent fills. */
	public int withAlpha(float alpha) {
		int a = Math.round(255 * Math.max(0, Math.min(1, alpha)));
		return (a << 24) | (get() & 0x00FFFFFF);
	}

	public static String hex(int argb) {
		return String.format(Locale.ROOT, "#%06X", argb & 0xFFFFFF);
	}
}
