package io.github.theamethyx.nova.core.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

public final class BooleanSetting extends Setting<Boolean> {
	public BooleanSetting(String id, String name, String description, boolean defaultValue) {
		super(id, name, description, defaultValue);
	}

	@Override
	public String validate(Boolean candidate) {
		return null;
	}

	@Override
	public JsonElement toJson() {
		return new JsonPrimitive(get());
	}

	@Override
	protected Boolean parse(JsonElement json) {
		if (!json.isJsonPrimitive() || !json.getAsJsonPrimitive().isBoolean()) {
			throw new IllegalArgumentException("expected true or false");
		}

		return json.getAsBoolean();
	}

	@Override
	public String displayValue() {
		return get() ? "On" : "Off";
	}

	public boolean isOn() {
		return get();
	}

	public void toggle() {
		set(!get());
	}
}
