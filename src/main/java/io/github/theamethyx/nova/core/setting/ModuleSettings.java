package io.github.theamethyx.nova.core.setting;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/** The ordered, validated settings of one module. */
public final class ModuleSettings {
	private final Map<String, Setting<?>> settings = new LinkedHashMap<>();

	public <S extends Setting<?>> S add(S setting) {
		if (settings.putIfAbsent(setting.id(), setting) != null) {
			throw new IllegalArgumentException("Duplicate setting id: " + setting.id());
		}

		return setting;
	}

	public List<Setting<?>> all() {
		return Collections.unmodifiableList(new ArrayList<>(settings.values()));
	}

	public Setting<?> get(String id) {
		return settings.get(id);
	}

	public boolean isEmpty() {
		return settings.isEmpty();
	}

	public void resetAll() {
		settings.values().forEach(Setting::reset);
	}

	public boolean allDefault() {
		return settings.values().stream().allMatch(Setting::isDefault);
	}

	public JsonObject toJson() {
		JsonObject json = new JsonObject();
		settings.values().forEach(setting -> json.add(setting.id(), setting.toJson()));
		return json;
	}

	/**
	 * Applies saved values. Unknown keys are ignored (they may come from a newer or older version)
	 * and missing keys fall back to defaults.
	 *
	 * @return readable warnings for values that had to be replaced by defaults
	 */
	public List<String> load(JsonObject json) {
		List<String> warnings = new ArrayList<>();

		for (Setting<?> setting : settings.values()) {
			JsonElement value = json.get(setting.id());
			if (value == null) {
				setting.reset();
				continue;
			}

			String warning = setting.load(value);
			if (warning != null) {
				warnings.add(warning);
			}
		}

		return warnings;
	}
}
