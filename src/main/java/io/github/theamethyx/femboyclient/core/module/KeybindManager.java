package io.github.theamethyx.femboyclient.core.module;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.IntPredicate;

/**
 * Turns modules on and off from key presses, in toggle or hold mode. The client feeds it raw GLFW key codes; whether a press should
 * be handled at all (no screen open, not typing in chat) is decided by the caller.
 */
public final class KeybindManager {
	private final ModuleManager modules;
	/** Hold-mode modules turned on by a key press that has not been released yet. */
	private final Set<Module> held = new LinkedHashSet<>();

	public KeybindManager(ModuleManager modules) {
		this.modules = modules;
	}

	/**
	 * Handles a key press: toggle-mode modules bound to {@code key} are toggled, hold-mode modules are
	 * turned on until {@link #onKeyReleased(int)} or {@link #releaseUnheld}.
	 *
	 * @return the modules whose state was changed
	 */
	public List<Module> onKeyPressed(int key) {
		if (key == Module.NO_KEY) {
			return List.of();
		}

		List<Module> changed = new ArrayList<>();
		for (Module module : modulesBoundTo(key)) {
			if (!module.isHoldKey()) {
				modules.toggle(module);
				changed.add(module);
			} else if (!module.isEnabled()) {
				modules.setEnabled(module, true);
				held.add(module);
				changed.add(module);
			}
		}

		return changed;
	}

	/**
	 * Handles a key release: hold-mode modules that this key turned on are turned off again.
	 *
	 * @return the modules that were turned off
	 */
	public List<Module> onKeyReleased(int key) {
		List<Module> released = new ArrayList<>();
		for (Module module : List.copyOf(held)) {
			if (module.key() == key || !module.isHoldKey()) {
				release(module, released);
			}
		}

		return released;
	}

	/**
	 * Safety net for missed release events (e.g. the window lost focus while a key was held): turns off
	 * hold-mode modules whose key is no longer down. Call once per tick.
	 */
	public List<Module> releaseUnheld(IntPredicate isKeyDown) {
		List<Module> released = new ArrayList<>();
		for (Module module : List.copyOf(held)) {
			if (!module.isHoldKey() || module.key() == Module.NO_KEY || !isKeyDown.test(module.key())) {
				release(module, released);
			}
		}

		return released;
	}

	/** Forgets which modules are held, e.g. after the emergency stop already turned them off. */
	public void clearHeld() {
		held.clear();
	}

	private void release(Module module, List<Module> released) {
		held.remove(module);
		if (module.isEnabled()) {
			modules.setEnabled(module, false);
			released.add(module);
		}
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
