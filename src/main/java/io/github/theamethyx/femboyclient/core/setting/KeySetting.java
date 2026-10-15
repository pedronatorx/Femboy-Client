package io.github.theamethyx.femboyclient.core.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

/** A keyboard key, stored as a GLFW key code; {@link #NONE} means unbound. */
public final class KeySetting extends Setting<Integer> {
	public static final int NONE = -1;
	/** GLFW_KEY_LAST; key codes above it do not exist. */
	private static final int LAST_KEY = 348;

	public KeySetting(String id, String name, String description, int defaultKey) {
		super(id, name, description, defaultKey);
		requireValidDefault();
	}

	@Override
	public String validate(Integer candidate) {
		return candidate == NONE || candidate >= 32 && candidate <= LAST_KEY ? null : "is not a keyboard key";
	}

	@Override
	public JsonElement toJson() {
		return new JsonPrimitive(get());
	}

	@Override
	protected Integer parse(JsonElement json) {
		if (!json.isJsonPrimitive() || !json.getAsJsonPrimitive().isNumber()) {
			throw new IllegalArgumentException("expected a key code");
		}

		return json.getAsInt();
	}

	public boolean isBound() {
		return get() != NONE;
	}
}
