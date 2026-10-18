package io.github.theamethyx.femboyclient.core.module;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

import com.google.gson.JsonObject;

import io.github.theamethyx.femboyclient.core.diag.DiagnosticLogger;
import io.github.theamethyx.femboyclient.core.diag.PerformanceMonitor;

/**
 * Owns all modules and drives their lifecycle. Every call into module code goes through
 * {@link #runGuarded(Module, Phase, Runnable)}, so one failing module is stopped and reported
 * instead of crashing the client or affecting other modules.
 *
 * <p>Recovery: when a running module fails while ticking, drawing or changing worlds, it is suspended (stopped, its
 * cleanup run) for a few seconds and then started again, because many errors are one-off (a chunk unloading at the wrong
 * moment). After {@link #FAILURES_BEFORE_OFF} failures within {@link #FAILURE_WINDOW_MILLIS} it is turned off and stays off
 * until you turn it on again. Failures while starting, loading or registering turn it off at once.
 *
 * <p>Not thread-safe: use it from the client thread only.
 */
public final class ModuleManager {
	private final Map<String, Module> modules = new LinkedHashMap<>();
	/** Registration order, rebuilt only when a module is registered; {@link #all()} hands this out without copying. */
	private List<Module> ordered = List.of();
	private Module[] array = new Module[0];
	/** {@link #get(Class)} results; modules never change after registration, so a miss is cached too. */
	private final Map<Class<?>, Module> byClass = new HashMap<>();
	private final List<Consumer<Module>> listeners = new CopyOnWriteArrayList<>();
	private final List<FailureListener> failureListeners = new CopyOnWriteArrayList<>();
	private final List<ConflictListener> conflictListeners = new CopyOnWriteArrayList<>();
	private final DiagnosticLogger diagnostics;
	private final PerformanceMonitor performance;
	private final LongSupplier clock;
	private EnvironmentPolicy environment;
	/**
	 * Saved state of modules this version does not have (from a newer version, or one that was removed), from the last
	 * loaded profile. Written back by {@link #saveModules()} so they are not lost.
	 */
	private JsonObject retainedModules = new JsonObject();

	/** Failures within {@link #FAILURE_WINDOW_MILLIS} after which a module is turned off instead of suspended. */
	public static final int FAILURES_BEFORE_OFF = 3;
	public static final long FAILURE_WINDOW_MILLIS = 60_000;
	/** How long a module stays suspended after its first failure; doubled after the second. */
	public static final long SUSPEND_MILLIS = 5_000;

	public ModuleManager(DiagnosticLogger diagnostics, PerformanceMonitor performance, EnvironmentPolicy environment) {
		this(diagnostics, performance, environment, System::currentTimeMillis);
	}

	/** {@code clock} gives the time in milliseconds for failure windows and suspensions (tests pass a fake one). */
	public ModuleManager(DiagnosticLogger diagnostics, PerformanceMonitor performance, EnvironmentPolicy environment, LongSupplier clock) {
		this.diagnostics = Objects.requireNonNull(diagnostics);
		this.performance = Objects.requireNonNull(performance);
		this.environment = Objects.requireNonNull(environment);
		this.clock = Objects.requireNonNull(clock);
	}

	public enum Phase {
		REGISTER, ENABLE, DISABLE, TICK, RENDER_HUD, RENDER_WORLD, SAVE, LOAD, WORLD_CHANGE
	}

	/**
	 * Told when a module fails, with a readable reason (for notifications). {@code turnedOff} is false when the module was
	 * only suspended and will restart by itself.
	 */
	public interface FailureListener {
		void failed(Module module, Phase phase, String reason, boolean turnedOff);
	}

	/** Told when turning {@code enabled} on turned {@code turnedOff} off because they conflict. */
	public interface ConflictListener {
		void resolved(Module enabled, Module turnedOff);
	}

	// ----- registration ----------------------------------------------------------------------

	public <M extends Module> M register(M module) {
		if (modules.containsKey(module.id())) {
			throw new IllegalArgumentException("Duplicate module id: " + module.id());
		}

		modules.put(module.id(), module);
		ordered = List.copyOf(modules.values());
		array = ordered.toArray(new Module[0]);
		byClass.clear();
		module.settings().all().forEach(setting -> setting.onChange(value -> notifyChanged(module)));

		if (!runGuarded(module, Phase.REGISTER, module::onRegister)) {
			module.setEnabledFlag(false);
		}

		return module;
	}

	/** All modules in registration order. The list is immutable and shared; it does not change after startup. */
	public List<Module> all() {
		return ordered;
	}

	public Module get(String id) {
		return modules.get(id);
	}

	/** The module of this type, or {@code null}. Constant time after the first call: mixins call this every frame. */
	@SuppressWarnings("unchecked")
	public <M extends Module> M get(Class<M> type) {
		Module cached = byClass.get(type);
		if (cached != null || byClass.containsKey(type)) {
			return (M) cached;
		}

		Module found = null;
		for (Module module : array) {
			if (type.isInstance(module)) {
				found = module;
				break;
			}
		}

		byClass.put(type, found);
		return (M) found;
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
			String aliases = String.join(" ", module.aliases()).toLowerCase(Locale.ROOT);
			haystack.append(' ').append(aliases);
			module.settings().all().forEach(setting -> haystack.append(' ').append(setting.name().toLowerCase(Locale.ROOT)));

			boolean all = true;
			boolean inName = true;
			for (String term : terms) {
				if (term.isEmpty()) continue;
				if (haystack.indexOf(term) < 0) all = false;
				if (!name.contains(term) && !aliases.contains(term)) inName = false;
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

		module.offReason = null;
		if (module.isEnabled() != enabled) {
			module.setEnabledFlag(enabled);
			diagnostics.info(module.name(), enabled ? "Enabled" : "Disabled");
			if (enabled) {
				resolveConflicts(module);
			}
		}

		updateRunning(module);
		notifyChanged(module);
	}

	/** Whether two modules declare that they cannot run together (checked both ways). */
	public boolean conflict(Module a, Module b) {
		return a != b && (a.conflicts().contains(b.id()) || b.conflicts().contains(a.id()));
	}

	/** Enabled modules that conflict with {@code module}. */
	public List<Module> conflictsOf(Module module) {
		List<Module> result = new ArrayList<>();
		for (Module other : array) {
			if (other.isEnabled() && conflict(module, other)) {
				result.add(other);
			}
		}

		return result;
	}

	private void resolveConflicts(Module module) {
		for (Module other : conflictsOf(module)) {
			other.setEnabledFlag(false);
			other.offReason = "Turned off because it cannot run together with " + module.name() + ".";
			updateRunning(other);
			notifyChanged(other);
			diagnostics.info(other.name(), "Turned off: it cannot run together with " + module.name() + ".");
			for (ConflictListener listener : conflictListeners) {
				listener.resolved(module, other);
			}
		}
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

	/** Restores every module of a category to its defaults (settings, key, enabled state). */
	public void resetCategory(ModuleCategory category) {
		for (Module module : array) {
			if (module.category() == category) {
				resetToDefaults(module);
			}
		}

		diagnostics.info("Client", "Reset all " + category.label() + " modules to their defaults.");
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
		long bytes = performance.allocatedBytes();
		long start = System.nanoTime();
		for (Module module : array) {
			updateRunning(module);
			if (module.isRunning()) {
				runGuarded(module, Phase.TICK, module::onTick);
			}
		}

		performance.record("client/tick", System.nanoTime() - start, performance.allocatedBytes() - bytes);
	}

	/**
	 * Tells running modules that the client switched to another world or dimension while they keep running, so they drop
	 * cached state from the old one. Call once per change, from the client thread.
	 */
	public void worldChanged() {
		for (Module module : array) {
			if (module.isRunning()) {
				runGuarded(module, Phase.WORLD_CHANGE, module::onWorldChange);
			}
		}
	}

	/** Stops every running module, e.g. when the client shuts down. Enabled flags are kept. */
	public void stopAll() {
		for (Module module : array) {
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
		boolean shouldRun = module.isEnabled() && !suspended(module) && availability(module).available();

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

	/** Whether the module is waiting out a suspension after a failure. Clears the suspension once it has passed. */
	private boolean suspended(Module module) {
		if (module.suspendedUntil == 0) {
			return false;
		}

		if (clock.getAsLong() < module.suspendedUntil) {
			return true;
		}

		module.suspendedUntil = 0;
		diagnostics.info(module.name(), "Restarting after the suspension.");
		return false;
	}

	/** The module's current state and recent numbers, for the health monitor. */
	public ModuleHealth health(Module module) {
		long now = clock.getAsLong();
		long sinceSuccess = module.lastSuccessNanos == 0 ? -1 : (System.nanoTime() - module.lastSuccessNanos) / 1_000_000;
		int recent = recentFailures(module, now);
		String workload = null;
		if (module.isRunning()) {
			try {
				workload = module.workload();
			} catch (RuntimeException e) {
				workload = "Could not describe its work: " + e.getMessage();
			}
		}

		StateAndReason current = stateOf(module, now);
		ModuleHealth.State state = current.state();
		String reason = current.reason();
		return new ModuleHealth(state, reason, sinceSuccess, module.lastError(), recent, module.errorCount(), workload);
	}

	/** A module's state and the reason for it. */
	public record StateAndReason(ModuleHealth.State state, String reason) {
	}

	/**
	 * Just the state and its reason, as in {@link #health}, without asking the module to describe its work: cheap enough
	 * for the menu to show next to every module.
	 */
	public StateAndReason state(Module module) {
		return stateOf(module, clock.getAsLong());
	}

	private StateAndReason stateOf(Module module, long now) {
		if (!module.isEnabled()) {
			boolean failed = module.lastError() != null;
			return new StateAndReason(failed ? ModuleHealth.State.ERROR : ModuleHealth.State.OFF,
					module.offReason() != null ? module.offReason() : failed ? module.lastError() : "");
		}

		if (module.isRunning()) {
			return new StateAndReason(ModuleHealth.State.ACTIVE, "");
		}

		if (module.suspendedUntil > now) {
			return new StateAndReason(ModuleHealth.State.SUSPENDED,
					"Restarts in " + Math.max(1, (module.suspendedUntil - now + 999) / 1000) + " s after: " + module.lastError());
		}

		Module.Availability availability = availability(module);
		ModuleHealth.State state = switch (availability.kind()) {
			case WAITING, AVAILABLE -> ModuleHealth.State.WAITING;
			case ENVIRONMENT, OTHER -> ModuleHealth.State.UNSUPPORTED;
			case REDUCED -> ModuleHealth.State.REDUCED;
		};
		return new StateAndReason(state, availability.reason());
	}

	private static int recentFailures(Module module, long now) {
		int count = 0;
		for (int i = 0; i < module.recentFailureCount; i++) {
			if (now - module.recentFailures[i] < FAILURE_WINDOW_MILLIS) {
				count++;
			}
		}

		return count;
	}

	/**
	 * Runs module code with error isolation and timing.
	 *
	 * @return {@code true} if the code completed normally
	 */
	public boolean runGuarded(Module module, Phase phase, Runnable action) {
		long bytes = performance.allocatedBytes();
		long start = System.nanoTime();

		try {
			action.run();
			if (phase == Phase.TICK || phase == Phase.RENDER_HUD || phase == Phase.RENDER_WORLD) {
				module.lastSuccessNanos = start;
			}

			return true;
		} catch (VirtualMachineError fatal) {
			throw fatal;
		} catch (Throwable t) {
			handleFailure(module, phase, t);
			return false;
		} finally {
			long nanos = System.nanoTime() - start;
			performance.record(module.timingKeys[phase.ordinal()], nanos, performance.allocatedBytes() - bytes);
		}
	}

	private void handleFailure(Module module, Phase phase, Throwable t) {
		String message = "Failed during " + phase.name().toLowerCase(Locale.ROOT).replace('_', ' ') + ": "
				+ (t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage());
		module.recordError(message);
		long now = clock.getAsLong();
		int recent = rememberFailure(module, now);

		if (phase == Phase.DISABLE || phase == Phase.SAVE) {
			// Turning it off again would recurse; saving failures do not affect running state.
			diagnostics.error(module.name(), message + ". Other modules are unaffected.", t);
			notifyFailure(module, phase, message, false);
			return;
		}

		boolean recoverable = phase == Phase.TICK || phase == Phase.RENDER_HUD || phase == Phase.RENDER_WORLD || phase == Phase.WORLD_CHANGE;
		boolean turnOff = !recoverable || recent >= FAILURES_BEFORE_OFF;
		if (turnOff) {
			module.setEnabledFlag(false);
			module.suspendedUntil = 0;
			module.offReason = recoverable ? "Turned off after " + recent + " errors within a minute. Last: " + message
					: "Turned off after an error. " + message;
			diagnostics.error(module.name(), message + (recoverable ? ". It failed " + recent + " times within a minute and was turned off" : ". The module was turned off")
					+ "; other modules are unaffected. Turn it on again to retry.", t);
		} else {
			long pause = SUSPEND_MILLIS * recent;
			module.suspendedUntil = now + pause;
			diagnostics.error(module.name(), message + ". Suspended for " + pause / 1000 + " s, then it restarts by itself; other modules are unaffected.", t);
		}

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

		notifyFailure(module, phase, message, turnOff);
		notifyChanged(module);
	}

	/** Records a failure time and returns how many failures fall within the window, including this one. */
	private static int rememberFailure(Module module, long now) {
		long[] times = module.recentFailures;
		int kept = 0;
		for (int i = 0; i < module.recentFailureCount; i++) {
			if (now - times[i] < FAILURE_WINDOW_MILLIS) {
				times[kept++] = times[i];
			}
		}

		if (kept == times.length) {
			System.arraycopy(times, 1, times, 0, kept - 1);
			kept--;
		}

		times[kept++] = now;
		module.recentFailureCount = kept;
		return kept;
	}

	private void notifyFailure(Module module, Phase phase, String message, boolean turnedOff) {
		for (FailureListener listener : failureListeners) {
			try {
				listener.failed(module, phase, message, turnedOff);
			} catch (RuntimeException e) {
				diagnostics.warn("Client", "A failure notification could not be shown: " + e);
			}
		}
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

		retainedModules.entrySet().forEach(entry -> json.add(entry.getKey(), entry.getValue().deepCopy()));
		return json;
	}

	/** Ids of modules in the last loaded profile that this version does not have; their data is kept. */
	public java.util.Set<String> retainedModules() {
		return java.util.Set.copyOf(retainedModules.keySet());
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
		retainedModules = new JsonObject();
		for (String id : json.keySet()) {
			if (!modules.containsKey(id)) {
				retainedModules.add(id, json.get(id).deepCopy());
			}
		}

		if (!retainedModules.isEmpty()) {
			diagnostics.info("Client", "The profile has settings for " + retainedModules.size() + " module(s) this version does not have ("
					+ String.join(", ", retainedModules.keySet()) + "); they are kept in the file unchanged.");
		}

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

	public void addFailureListener(FailureListener listener) {
		failureListeners.add(listener);
	}

	public void addConflictListener(ConflictListener listener) {
		conflictListeners.add(listener);
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
