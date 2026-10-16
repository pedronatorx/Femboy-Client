package io.github.theamethyx.femboyclient.client.hud;

import java.util.LinkedHashMap;
import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import io.github.theamethyx.femboyclient.core.config.ConfigurationManager;

/**
 * Positions of HUD blocks you dragged in the HUD editor, as fractions of the screen size (so they survive window
 * resizes and GUI scale changes). Blocks without a position stay stacked in their corner. Saved in the global config.
 */
public final class HudLayout {
	static final String SECTION = "hud_layout";

	public record Position(float x, float y) {
	}

	private final Map<String, Position> positions = new LinkedHashMap<>();
	private final ConfigurationManager config;

	public HudLayout(ConfigurationManager config) {
		this.config = config;
		reload();
	}

	/** Reads the saved layout; malformed entries are skipped. */
	public void reload() {
		positions.clear();
		if (config.section(SECTION) instanceof JsonObject json) {
			for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
				if (entry.getValue() instanceof JsonArray array && array.size() == 2) {
					try {
						float x = array.get(0).getAsFloat();
						float y = array.get(1).getAsFloat();
						if (x >= 0 && x <= 1 && y >= 0 && y <= 1) {
							positions.put(entry.getKey(), new Position(x, y));
						}
					} catch (RuntimeException ignored) {
						// Skip an entry that is not two numbers.
					}
				}
			}
		}
	}

	public Position get(String moduleId) {
		return positions.get(moduleId);
	}

	public void set(String moduleId, float x, float y) {
		positions.put(moduleId, new Position(Math.clamp(x, 0, 1), Math.clamp(y, 0, 1)));
		save();
	}

	public void reset(String moduleId) {
		positions.remove(moduleId);
		save();
	}

	public void resetAll() {
		positions.clear();
		save();
	}

	public int size() {
		return positions.size();
	}

	private void save() {
		JsonObject json = new JsonObject();
		positions.forEach((id, position) -> {
			JsonArray array = new JsonArray();
			array.add(position.x());
			array.add(position.y());
			json.add(id, array);
		});
		config.setSection(SECTION, json);
	}
}
