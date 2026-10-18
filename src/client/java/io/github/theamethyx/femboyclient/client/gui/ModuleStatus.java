package io.github.theamethyx.femboyclient.client.gui;

import java.util.ArrayList;
import java.util.List;

import io.github.theamethyx.femboyclient.core.module.Module;
import io.github.theamethyx.femboyclient.core.module.ModuleManager;

/**
 * What a module is doing, in words, for every place the menu shows it (module rows, the settings panel, Home, the search).
 * Built from the health monitor ({@link ModuleManager#health}) plus the modules it uses for some features. Every state has
 * an icon and a label, so the state never depends on color alone; it never says "on" for a module that is not running.
 *
 * @param kind   the state
 * @param label  one or two words ("On", "Waiting", "Error")
 * @param detail one sentence: why, and what to do about it (empty when there is nothing to add)
 * @param color  the state color (used next to the icon and label, never alone)
 * @param icon   the state icon
 */
public record ModuleStatus(Kind kind, String label, String detail, int color, Icon icon) {
	public enum Kind {
		/** Turned off by the user. */
		OFF,
		/** On and running. */
		ON,
		/** On and running, but some features need another module that is off. */
		LIMITED,
		/** On, slowed down or paused by the performance mode. */
		REDUCED,
		/** On, waiting for something it needs (a world, a server, data). */
		WAITING,
		/** On, but not available here (for example, singleplayer only). */
		UNAVAILABLE,
		/** Stopped for a few seconds after an error; restarts by itself. */
		SUSPENDED,
		/** Turned off after repeated errors. */
		ERROR
	}

	/** Whether the user should look at this module (shown on Home and in the "Needs attention" filter). */
	public boolean needsAttention() {
		return kind == Kind.ERROR || kind == Kind.SUSPENDED || kind == Kind.UNAVAILABLE || kind == Kind.LIMITED;
	}

	/** Whether the module is doing its job right now, fully or reduced. */
	public boolean working() {
		return kind == Kind.ON || kind == Kind.LIMITED || kind == Kind.REDUCED;
	}

	/** Label and detail in one line, e.g. "Waiting: waiting for a world to load." */
	public String sentence() {
		return detail.isEmpty() ? label : label + ": " + detail;
	}

	/** Recent results by module: the menu asks for every visible module each frame, the answer changes rarely. */
	private static final java.util.Map<Module, ModuleStatus> CACHE = new java.util.IdentityHashMap<>();
	private static final java.util.Map<Module, Long> CACHED_AT = new java.util.IdentityHashMap<>();
	private static final long CACHE_NANOS = 150_000_000L;

	/** The module's status, at most {@value #CACHE_NANOS} ns old (also refreshed whenever it is switched on or off). */
	public static ModuleStatus of(Module module, ModuleManager modules) {
		long now = System.nanoTime();
		ModuleStatus cached = CACHE.get(module);
		Long at = CACHED_AT.get(module);
		if (cached != null && at != null && now - at < CACHE_NANOS && (cached.kind != Kind.OFF && cached.kind != Kind.ERROR) == module.isEnabled()) {
			return cached;
		}

		ModuleStatus fresh = compute(module, modules);
		CACHE.put(module, fresh);
		CACHED_AT.put(module, now);
		return fresh;
	}

	private static ModuleStatus compute(Module module, ModuleManager modules) {
		ModuleManager.StateAndReason state = modules.state(module);
		String reason = state.reason() == null ? "" : state.reason();
		return switch (state.state()) {
			case OFF -> new ModuleStatus(Kind.OFF, "Off", reason, Theme.TEXT_MUTED, Icon.POWER);
			case ERROR -> new ModuleStatus(Kind.ERROR, "Error", (reason.isEmpty() ? "it failed" : reason)
					+ ". It was turned off; turn it on again to retry, or see Settings › Developer.", Theme.ERROR, Icon.WARNING);
			case SUSPENDED -> new ModuleStatus(Kind.SUSPENDED, "Restarting", reason, Theme.WARNING, Icon.CLOCK);
			case WAITING -> new ModuleStatus(Kind.WAITING, "Waiting", reason, Theme.INFO, Icon.CLOCK);
			case UNSUPPORTED -> new ModuleStatus(Kind.UNAVAILABLE, "Not available here", reason, Theme.WARNING, Icon.LOCK);
			case REDUCED -> new ModuleStatus(Kind.REDUCED, "Reduced", reason.isEmpty() ? "slowed down by the performance mode" : reason,
					Theme.INFO, Icon.PERFORMANCE);
			case ACTIVE -> {
				List<String> missing = missingHelpers(module, modules);
				yield missing.isEmpty() ? new ModuleStatus(Kind.ON, "On", "", Theme.SUCCESS, Icon.CHECK)
						: new ModuleStatus(Kind.LIMITED, "On, limited", String.join("; ", missing), Theme.WARNING, Icon.INFO);
			}
		};
	}

	/** "Needs Player Finder on for last-seen positions" for each helper module that is off. */
	private static List<String> missingHelpers(Module module, ModuleManager modules) {
		List<String> missing = new ArrayList<>();
		for (Module.Requirement requirement : module.requirements()) {
			Module other = modules.get(requirement.moduleId());
			if (other != null && !other.isRunning()) {
				missing.add("turn on " + other.name() + " for " + requirement.purpose());
			}
		}

		return missing;
	}

	/** Draws the icon and label as a small pill; returns its width. */
	public int drawPill(UiContext ui, int x, int y) {
		int w = ui.textWidth(label) + 16;
		ui.roundRect(x, y, w, 12, Theme.withAlpha(color, 0.14f), Theme.RADIUS_CONTROL);
		ui.icon(icon, x + 3, y + 2, 8, color);
		ui.text(label, x + 13, y + 2, color);
		return w;
	}
}
