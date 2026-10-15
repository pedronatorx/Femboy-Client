package io.github.theamethyx.femboyclient.core.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

/** Free text up to a maximum length, without line breaks or control characters. */
public final class StringSetting extends Setting<String> {
	private final int maxLength;
	private final String placeholder;

	public StringSetting(String id, String name, String description, String defaultValue, int maxLength, String placeholder) {
		super(id, name, description, defaultValue);
		this.maxLength = maxLength;
		this.placeholder = placeholder == null ? "" : placeholder;
		requireValidDefault();
	}

	@Override
	public String validate(String candidate) {
		if (candidate.length() > maxLength) {
			return "is longer than " + maxLength + " characters";
		}

		return candidate.chars().anyMatch(c -> c < 32 || c == 127) ? "contains control characters" : null;
	}

	@Override
	public JsonElement toJson() {
		return new JsonPrimitive(get());
	}

	@Override
	protected String parse(JsonElement json) {
		if (!json.isJsonPrimitive() || !json.getAsJsonPrimitive().isString()) {
			throw new IllegalArgumentException("expected text");
		}

		return json.getAsString();
	}

	@Override
	public String displayValue() {
		return get().isEmpty() ? "(empty)" : get();
	}

	public int maxLength() {
		return maxLength;
	}

	public String placeholder() {
		return placeholder;
	}
}
