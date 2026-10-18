package io.github.theamethyx.femboyclient.core.module;

/**
 * What a module is doing right now, for the health monitor: its state, why, and a few cheap numbers. Built on demand by
 * {@link ModuleManager#health(Module)}; nothing is collected for it while nobody looks.
 *
 * @param state            the operating state
 * @param reason           why the module is in this state (empty when it is simply on or off)
 * @param lastSuccessMillis how long ago its last tick or render call completed, in milliseconds, or -1 if never
 * @param lastError        the last error message, or {@code null}
 * @param recentFailures   failures in the last {@link ModuleManager#FAILURE_WINDOW_MILLIS} milliseconds
 * @param totalErrors      failures since the client started
 * @param workload         the module's own description of its current work, or {@code null}
 */
public record ModuleHealth(State state, String reason, long lastSuccessMillis, String lastError, int recentFailures, int totalErrors,
		String workload) {
	public enum State {
		ACTIVE("Active", "Running."),
		REDUCED("Reduced", "Paused or slowed down by the performance mode."),
		WAITING("Waiting", "Waiting for data it needs, such as a loaded world."),
		SUSPENDED("Suspended", "Stopped for a few seconds after an error; it restarts by itself."),
		UNSUPPORTED("Unsupported here", "Not available in this environment."),
		ERROR("Error", "Turned off after repeated errors; turn it on again to retry."),
		OFF("Off", "Turned off.");

		private final String label;
		private final String meaning;

		State(String label, String meaning) {
			this.label = label;
			this.meaning = meaning;
		}

		public String label() {
			return label;
		}

		public String meaning() {
			return meaning;
		}
	}
}
