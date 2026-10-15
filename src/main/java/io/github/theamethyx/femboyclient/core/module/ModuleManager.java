package io.github.theamethyx.femboyclient.core.module;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import com.google.gson.JsonObject;

import io.github.theamethyx.femboyclient.core.diag.DiagnosticLogger;
import io.github.theamethyx.femboyclient.core.diag.PerformanceMonitor;

/**
 * Owns all modules and drives their lifecycle. Every call into module code goes through
 * {@link #runGuarded(Module, Phase, Runnable)}, so one failing module is disabled and reported
 * instead of crashing the client or affecting other modules.
 *
 * <p>Not thread-safe: use it from the client thread only.
 */
public final class ModuleManager {
	private final Map<String, Module> modules = new LinkedHashMap<>();
	private final List<Consumer<Module>> listeners = new CopyOnWriteArrayList<>();
	private final DiagnosticLogger diagnostics;
	private final PerformanceMonitor performance;
	private EnvironmentPolicy environment;

	public ModuleManager(DiagnosticLogger diagnostics, PerformanceMonitor performance, EnvironmentPolicy environment) {
		this.diagnostics = Objects.requireNonNull(diagnostics);
		this.performance = Objects.requireNonNull(performance);
		this.environment = Objects.requireNonNull(environment);
	}

	public enum Phase {
		REGISTER, ENABLE, DISABLE, TICK, RENDER_HUD, RENDER_WORLD, SAVE, LOAD
	}

	// ----- registration ----------------------------------------------------------------------

	public <M extends Module> M register(M module) {
		if (modules.containsKey(module.id())) {
			throw new IllegalArgumentException("Duplicate module id: " + module.id());
		}

		modules.put(module.id(), module);
		module.settings().all().forEach(setting -> setting.onChange(value -> notifyChanged(module)));

		if (!runGuarded(module, Phase.REGISTER, module::onRegister)) {
			module.setEnabledFlag(false);
		}

		return module;
	}

	public List<Module> all() {
		return Collections.unmodifiableList(new ArrayList<>(modules.values()));
	}

	public Module get(String id) {
		return modules.get(id);
	}

	@SuppressWarnings("unchecked")
	public <M extends Module> M get(Class<M> type) {
		for (Module module : modules.values()) {
			if (type.isInstance(module)) {
				return (M) module;
			}
		}

		return null;
	}

	public List<Module> byCategory(ModuleCategory category) {
		return modules.values().stream().filter(module -> module.category() == category).toList();
	}

	/**
	 * Case-insensitive search over names, ids, descriptions, categories and setting names. Every
	 * whitespace-separated term must match. Name matches are listed first.
	 */
	public List<Module> search(String query, ModuleCategory categoryOrNull) {
		String[] terms = query.toLowerCase(Locale.ROOT).trim().split("\\s+");
		List<Module> nameMatches = new ArrayList<>();
		List<Module> otherMatches = new ArrayList<>();

		for (Module module : modules.values()) {
			if (categoryOrNull != null && module.category() != categoryOrNull) {
				continue;
			}

			String name = module.name().toLowerCase(Locale.ROOT);
			StringBuilder haystack = new StringBuilder(name).append(' ').append(module.id()).append(' ')
					.append(module.description().toLowerCase(Locale.ROOT)).append(' ')
					.append(module.category().label().toLowerCase(Locale.ROOT));
			module.settings().all().forEach(setting -> haystack.append(' ').append(setting.name().toLowerCase(Locale.ROOT)));

			boolean all = true;
			boolean inName = true;
			for (String term : terms) {
				if (term.isEmpty()) continue;
				if (haystack.indexOf(term) < 0) all = false;
				if (!name.contains(term)) inName = false;
			}

			if (all) {
				(inName ? nameMatches : otherMatches).add(module);
			}
		}

		nameMatches.addAll(otherMatches);
		return nameMatches;
	}

	// ----- enabling --------------------------------------------------------------------------

	public void toggle(Module module) {
		setEnabled(module, !module.isEnabled());
	}

	/** Records the user's choice and starts or stops the module as appropriate. */
	public void setEnabled(Module module, boolean enabled) {
		requireRegistered(module);
		if (enabled) {
			module.clearError();
		}

		if (module.isEnabled() != enabled) {
			module.setEnabledFlag(enabled);
			diagnostics.info(module.name(), enabled ? "Enabled" : "Disabled");
		}

		updateRunning(module);
		notifyChanged(module);
	}

	public void setKey(Module module, int key) {
		requireRegistered(module);
		if (!Module.isValidKey(key)) {
			throw new IllegalArgumentException("Invalid key code " + key);
		}

		module.setKeyInternal(key);
		notifyChanged(module);
	}

	/** Sets whether the module's key works while held (true) or toggles it (false). */
	public void setHoldKey(Module module, boolean holdKey) {
		requireRegistered(module);
		module.setHoldKeyInternal(holdKey);
		notifyChanged(module);
	}

	/**
	 * Emergency stop: turns every enabled module off, so each one restores what it changed in its
	 * {@code onDisable}.
	 *
	 * @return the modules that were turned off, in registration order
	 */
	public List<Module> disableAll() {
		List<Module> turnedOff = new ArrayList<>();
		for (Module module : modules.values()) {
			if (module.isEnabled()) {
				setEnabled(module, false);
				turnedOff.add(module);
			}
		}

		if (!turnedOff.isEmpty()) {
			diagnostics.info("Client", "Turned off all modules (" + turnedOff.size() + ").");
		}

		return turnedOff;
	}

	/** Restores a module's settings, key and enabled state to their defaults. */
	public void resetToDefaults(Module module) {
		requireRegistered(module);
		module.settings().resetAll();
		runGuarded(module, Phase.LOAD, module::resetExtra);
		module.setKeyInternal(module.defaultKey());
		module.setHoldKeyInternal(false);
		setEnabled(module, module.isEnabledByDefault());
	}

	// ----- per-tick work ---------------------------------------------------------------------

	public void setEnvironment(EnvironmentPolicy environment) {
		this.environment = Objects.requireNonNull(environment);
	}

	public EnvironmentPolicy environment() {
		return environment;
	}

	/**
	 * Starts or stops modules whose availability changed (e.g. a world was loaded), then ticks
	 * every running module. Call once per client tick.
	 */
	public void tick() {
		for (Module module : modules.values()) {
			updateRunning(module);
			if (module.isRunning()) {
				runGuarded(module, Phase.TICK, module::onTick);
			}
		}
	}

	/** Stops every running module, e.g. when the client shuts down. Enabled flags are kept. */
	public void stopAll() {
		for (Module module : modules.values()) {
			if (module.isRunning()) {
				module.setRunning(false);
				runGuarded(module, Phase.DISABLE, module::onDisable);
			}
		}
	}

	public Module.Availability availability(Module module) {
		try {
			return module.availability(environment);
		} catch (RuntimeException e) {
			return Module.Availability.unavailable("Availability check failed: " + e.getMessage());
		}
	}

	private void updateRunning(Module module) {
		boolean shouldRun = module.isEnabled() && availability(module).available();

		if (shouldRun && !module.isRunning()) {
			module.setRunning(true);
			if (!runGuarded(module, Phase.ENABLE, module::onEnable)) {
				return;
			}

			notifyChanged(module);
		} else if (!shouldRun && module.isRunning()) {
			module.setRunning(false);
			runGuarded(module, Phase.DISABLE, module::onDisable);
			notifyChanged(module);
		}
	}

	/**
	 * Runs module code with error isolation and timing.
	 *
	 * @return {@code true} if the code completed normally
	 */
	public boolean runGuarded(Module module, Phase phase, Runnable action) {
		long start = System.nanoTime();

		try {
			action.run();
			return true;
		} catch (VirtualMachineError fatal) {
			throw fatal;
		} catch (Throwable t) {
			handleFailure(module, phase, t);
			return false;
		} finally {
			performance.record(module.id() + "/" + phase.name().toLowerCase(Locale.ROOT), System.nanoTime() - start);
		}
	}

	private void handleFailure(Module module, Phase phase, Throwable t) {
		String message = "Failed during " + phase.name().toLowerCase(Locale.ROOT).replace('_', ' ') + ": "
				+ (t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage());
		module.recordError(message);
		diagnostics.error(module.name(), message + ". The module was turned off; other modules are unaffected.", t);

		if (phase == Phase.DISABLE || phase == Phase.SAVE) {
			// Turning it off again would recurse; saving failures do not affect running state.
			return;
		}

		module.setEnabledFlag(false);
		if (module.isRunning()) {
			module.setRunning(false);
			if (phase != Phase.ENABLE) {
				try {
					module.onDisable();
				} catch (VirtualMachineError fatal) {
					throw fatal;
				} catch (Throwable ignored) {
					// Already reported the original failure; a second one adds no information.
				}
			}
		}

		notifyChanged(module);
	}

	// ----- persistence -----------------------------------------------------------------------

	public JsonObject saveModules() {
		JsonObject json = new JsonObject();
		for (Module module : modules.values()) {
			JsonObject[] holder = new JsonObject[1];
			if (runGuarded(module, Phase.SAVE, () -> holder[0] = module.toJson())) {
				json.add(module.id(), holder[0]);
			}
		}

		return json;
	}

	/**
	 * Copies saved profile data, keeping only modules that exist and values they would accept. Nothing
	 * is applied. Each dropped value is described in {@code warnings}.
	 */
	public JsonObject sanitizeModules(JsonObject json, List<String> warnings) {
		JsonObject clean = new JsonObject();
		for (String id : json.keySet()) {
			Module module = modules.get(id);
			if (module == null) {
				warnings.add("Unknown module '" + id + "' was skipped.");
			} else if (json.get(id) instanceof JsonObject object) {
				clean.add(id, module.sanitize(object, warnings));
			} else {
				warnings.add(module.name() + ": the saved data was not an object; defaults will be used.");
			}
		}

		return clean;
	}

	/**
	 * Applies a saved profile. Modules missing from the profile are reset to defaults.
	 *
	 * @return readable warnings for anything that had to be replaced by defaults
	 */
	public List<String> loadModules(JsonObject json) {
		List<String> warnings = new ArrayList<>();

		for (Module module : modules.values()) {
			JsonObject moduleJson = json.get(module.id()) instanceof JsonObject object ? object : null;
			if (moduleJson == null) {
				if (json.has(module.id())) {
					warnings.add(module.name() + ": saved data was not an object; using defaults.");
				}

				resetToDefaults(module);
				continue;
			}

			boolean[] wantEnabled = {module.isEnabledByDefault()};
			if (!runGuarded(module, Phase.LOAD, () -> wantEnabled[0] = module.loadFromJson(moduleJson, warnings))) {
				continue;
			}

			setEnabled(module, wantEnabled[0]);
		}

		return warnings;
	}

	// ----- listeners -------------------------------------------------------------------------

	/** Called on the client thread whenever a module's state or settings change. */
	public void addListener(Consumer<Module> listener) {
		listeners.add(listener);
	}

	private void notifyChanged(Module module) {
		for (Consumer<Module> listener : listeners) {
			listener.accept(module);
		}
	}

	private void requireRegistered(Module module) {
		if (modules.get(module.id()) != module) {
			throw new IllegalArgumentException(module + " is not registered");
		}
	}
}
