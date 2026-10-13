package io.github.theamethyx.nova.core.module;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Toggles modules from key presses. The client feeds it raw GLFW key codes; whether a press should
 * be handled at all (no screen open, not typing in chat) is decided by the caller.
 */
public final class KeybindManager {
	private final ModuleManager modules;

	public KeybindManager(ModuleManager modules) {
		this.modules = modules;
	}

	/**
	 * Toggles every module bound to {@code key}.
	 *
	 * @return the modules that were toggled
	 */
	public List<Module> onKeyPressed(int key) {
		if (key == Module.NO_KEY) {
			return List.of();
		}

		List<Module> bound = modulesBoundTo(key);
		bound.forEach(modules::toggle);
		return bound;
	}

	public List<Module> modulesBoundTo(int key) {
		List<Module> result = new ArrayList<>();
		for (Module module : modules.all()) {
			if (module.key() == key && key != Module.NO_KEY) {
				result.add(module);
			}
		}

		return result;
	}

	/** Keys that are bound to more than one module, with the modules sharing them. */
	public Map<Integer, List<Module>> conflicts() {
		Map<Integer, List<Module>> byKey = new LinkedHashMap<>();
		for (Module module : modules.all()) {
			if (module.key() != Module.NO_KEY) {
				byKey.computeIfAbsent(module.key(), k -> new ArrayList<>()).add(module);
			}
		}

		byKey.values().removeIf(list -> list.size() < 2);
		return byKey;
	}
}
