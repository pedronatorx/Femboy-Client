package io.github.theamethyx.femboyclient.core.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * Upgrades saved module data written by older versions, so renamed modules keep their settings instead of being
 * dropped as unknown. Applied to every place module data enters: profile files, exported profiles being imported and
 * built-in presets.
 *
 * <p>Schema history:
 * <ul>
 *   <li>1: up to 0.8.0.</li>
 *   <li>2: 0.9.0 renamed Ore ESP ({@code ore_esp}) to Block ESP ({@code block_esp}); the settings are unchanged.</li>
 * </ul>
 */
public final class ConfigMigrations {
	/** Old module id to new module id. */
	public static final Map<String, String> RENAMED_MODULES = Map.of("ore_esp", "block_esp");

	private ConfigMigrations() {
	}

	/**
	 * Renames old module ids in {@code modules} in place. When both the old and the new id are present (a profile edited by
	 * two versions), the new one wins and the old one is removed.
	 *
	 * @return readable notes about what was migrated, empty when nothing was
	 */
	public static List<String> migrateModules(JsonObject modules) {
		List<String> notes = new ArrayList<>();
		for (Map.Entry<String, String> rename : RENAMED_MODULES.entrySet()) {
			String from = rename.getKey();
			String to = rename.getValue();
			JsonElement old = modules.remove(from);
			if (old == null) {
				continue;
			}

			if (modules.has(to)) {
				notes.add("Dropped old '" + from + "' data: '" + to + "' is already present.");
			} else {
				modules.add(to, old);
				notes.add("Moved the settings of '" + from + "' to '" + to + "' (module renamed).");
			}
		}

		return notes;
	}
}
