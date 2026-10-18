package io.github.theamethyx.femboyclient.client.gui;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

import io.github.theamethyx.femboyclient.client.GlobalOptions;
import io.github.theamethyx.femboyclient.core.config.ConfigurationManager;
import io.github.theamethyx.femboyclient.core.setting.Setting;

/**
 * Theme Studio presets: built-in looks and the ones you save. A preset stores values for the theme settings
 * ({@link GlobalOptions#THEME_SETTINGS}); applying one resets the others to their defaults first, so presets do not mix.
 * Saved presets live in the global configuration (section "themes") and can be copied as text.
 */
public final class ThemePresets {
	static final String SECTION = "themes";

	/** A built-in preset: a name, a one-line description and the values that differ from the defaults. */
	public record Preset(String name, String description, Map<String, JsonElement> values) {
	}

	public static final List<Preset> BUILT_IN = List.of(
			preset("Default purple", "Navy panels with purple and magenta accents.", "theme", "FEMBOY"),
			preset("Deep violet", "Violet-tinted panels, softer contrast.", "theme", "DEEP_VIOLET", "corners", "ROUND"),
			preset("Midnight", "Near-black navy with a cool violet accent.", "theme", "MIDNIGHT", "hud_background", 70),
			preset("Minimal dark", "Plain charcoal, sharp corners, no background art.", "theme", "CHARCOAL", "corners", "SHARP",
					"backdrop_art", false, "accent_icons", false),
			preset("High contrast", "Black panels, white text and bright borders for readability.", "theme", "HIGH_CONTRAST", "hud_background", 95),
			preset("Reduced motion", "Default colors; only short fades, no sliding.", "theme", "FEMBOY", "reduce_motion", true, "backdrop_art", false));

	private static Preset preset(String name, String description, Object... pairs) {
		Map<String, JsonElement> values = new LinkedHashMap<>();
		for (int i = 0; i < pairs.length; i += 2) {
			Object value = pairs[i + 1];
			values.put((String) pairs[i], value instanceof Boolean b ? new JsonPrimitive(b) : value instanceof Number n ? new JsonPrimitive(n)
					: new JsonPrimitive(value.toString()));
		}

		return new Preset(name, description, Map.copyOf(values));
	}

	private final GlobalOptions options;
	private final ConfigurationManager config;

	public ThemePresets(GlobalOptions options, ConfigurationManager config) {
		this.options = options;
		this.config = config;
	}

	/** Applies values to the theme settings; settings not listed go back to their defaults. Returns problems found. */
	public List<String> apply(Map<String, JsonElement> values) {
		List<String> problems = new ArrayList<>();
		for (String id : GlobalOptions.THEME_SETTINGS) {
			Setting<?> setting = options.settings.get(id);
			JsonElement value = values.get(id);
			if (value == null) {
				setting.reset();
			} else {
				String problem = setting.load(value);
				if (problem != null) {
					problems.add(problem);
				}
			}
		}

		return problems;
	}

	/** The current theme settings that differ from the defaults. */
	public Map<String, JsonElement> current() {
		Map<String, JsonElement> values = new LinkedHashMap<>();
		for (String id : GlobalOptions.THEME_SETTINGS) {
			Setting<?> setting = options.settings.get(id);
			if (!setting.isDefault()) {
				values.put(id, setting.toJson());
			}
		}

		return values;
	}

	/** Saved presets by name, in the order saved. Invalid entries are skipped. */
	public Map<String, Map<String, JsonElement>> saved() {
		Map<String, Map<String, JsonElement>> result = new LinkedHashMap<>();
		if (config.section(SECTION) instanceof JsonObject json) {
			for (var entry : json.entrySet()) {
				if (entry.getValue() instanceof JsonObject values) {
					result.put(entry.getKey(), validValues(values, new ArrayList<>()));
				}
			}
		}

		return result;
	}

	/** @return {@code null} on success, otherwise a readable reason */
	public String save(String name) {
		String trimmed = name == null ? "" : name.strip();
		if (!trimmed.matches("[A-Za-z0-9 _-]{1,24}")) {
			return "Use 1-24 letters, digits, spaces, '-' or '_'.";
		}

		JsonObject all = config.section(SECTION) instanceof JsonObject json ? json : new JsonObject();
		JsonObject values = new JsonObject();
		current().forEach(values::add);
		all.add(trimmed, values);
		config.setSection(SECTION, all);
		return null;
	}

	public void delete(String name) {
		if (config.section(SECTION) instanceof JsonObject json) {
			json.remove(name);
			config.setSection(SECTION, json);
		}
	}

	/** A saved preset (or the current look when {@code name} is null) as shareable text. */
	public String export(String name) {
		Map<String, JsonElement> values = name == null ? current() : saved().get(name);
		if (values == null) {
			return null;
		}

		JsonObject json = new JsonObject();
		json.addProperty("femboyClientTheme", name == null ? "My theme" : name);
		JsonObject settings = new JsonObject();
		values.forEach(settings::add);
		json.add("settings", settings);
		return json.toString();
	}

	/** Result of {@link #importTheme}: {@code error} is null on success; {@code warnings} list values that were skipped. */
	public record ImportResult(String error, String name, List<String> warnings) {
	}

	/** Validates exported text and saves it as a preset (not applied). Invalid values are skipped and listed. */
	public ImportResult importTheme(String text) {
		JsonObject json;
		try {
			JsonElement parsed = JsonParser.parseString(text == null ? "" : text.strip());
			if (!(parsed instanceof JsonObject object) || !(object.get("settings") instanceof JsonObject)) {
				return new ImportResult("This is not a Femboy Client theme (no 'settings').", null, List.of());
			}

			json = object;
		} catch (JsonParseException e) {
			return new ImportResult("This is not valid JSON: " + e.getMessage(), null, List.of());
		}

		List<String> warnings = new ArrayList<>();
		Map<String, JsonElement> values = validValues(json.getAsJsonObject("settings"), warnings);
		String base = json.get("femboyClientTheme") instanceof JsonPrimitive primitive && primitive.isString()
				? primitive.getAsString().replaceAll("[^A-Za-z0-9 _-]", "").strip() : "";
		base = base.isEmpty() ? "Imported" : base.substring(0, Math.min(20, base.length()));
		String name = base;
		Map<String, Map<String, JsonElement>> existing = saved();
		for (int i = 2; existing.containsKey(name); i++) {
			name = base + " " + i;
		}

		JsonObject all = config.section(SECTION) instanceof JsonObject saved ? saved : new JsonObject();
		JsonObject stored = new JsonObject();
		values.forEach(stored::add);
		all.add(name, stored);
		config.setSection(SECTION, all);
		return new ImportResult(null, name, warnings);
	}

	private Map<String, JsonElement> validValues(JsonObject json, List<String> warnings) {
		Map<String, JsonElement> values = new LinkedHashMap<>();
		for (var entry : json.entrySet()) {
			Setting<?> setting = GlobalOptions.THEME_SETTINGS.contains(entry.getKey()) ? options.settings.get(entry.getKey()) : null;
			if (setting == null) {
				warnings.add("'" + entry.getKey() + "' is not a theme setting; skipped.");
				continue;
			}

			String problem = setting.check(entry.getValue());
			if (problem != null) {
				warnings.add(problem + "; skipped.");
			} else {
				values.put(entry.getKey(), entry.getValue().deepCopy());
			}
		}

		return values;
	}
}
