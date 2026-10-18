package io.github.theamethyx.femboyclient.client;

import io.github.theamethyx.femboyclient.client.GlobalOptions.PerformanceMode;

/**
 * What the performance mode (Settings, also stored per profile) changes. Modules ask here instead of reading their
 * settings directly where the mode matters, so one switch changes the work done across the client:
 *
 * <table>
 *   <tr><th></th><th>Performance</th><th>Balanced</th><th>Low impact</th></tr>
 *   <tr><td>update and scan intervals</td><td>x2</td><td>as set</td><td>x3</td></tr>
 *   <tr><td>block scan time per tick</td><td>1 ms</td><td>2.5 ms</td><td>1 ms</td></tr>
 *   <tr><td>highlights, labels and lines drawn</td><td>capped</td><td>as set</td><td>capped</td></tr>
 *   <tr><td>HUD text rebuilt</td><td>every 2 ticks</td><td>every tick</td><td>every 2 ticks</td></tr>
 *   <tr><td>menu animations</td><td>off</td><td>as set</td><td>as set</td></tr>
 *   <tr><td>pauses between automatic actions</td><td>as set</td><td>as set</td><td>x2, at least +250 ms</td></tr>
 *   <tr><td>modules that repeat actions many times a second</td><td>run</td><td>run</td><td>paused, with the reason shown</td></tr>
 * </table>
 *
 * Nothing here changes what is sent to a server or how it looks to one; Low impact only means less work and fewer
 * actions.
 */
public final class Performance {
	private static volatile PerformanceMode mode = PerformanceMode.BALANCED;

	private Performance() {
	}

	/** Connects to the option; called once at startup. */
	static void bind(GlobalOptions options) {
		mode = options.performanceMode.get();
		options.performanceMode.onChange(value -> mode = value);
	}

	public static PerformanceMode mode() {
		return mode;
	}

	/** Test hook and GUI preview: sets the mode directly (the option is the normal way). */
	public static void setForTest(PerformanceMode value) {
		mode = value;
	}

	/** An update or scan interval in ticks, as the mode runs it. */
	public static int interval(int ticks) {
		int base = Math.max(1, ticks);
		return switch (mode) {
			case PERFORMANCE -> base * 2;
			case BALANCED -> base;
			case LOW_IMPACT -> base * 3;
		};
	}

	/** A rescan or refresh period in milliseconds, as the mode runs it. */
	public static long periodMillis(long millis) {
		return switch (mode) {
			case PERFORMANCE -> millis * 2;
			case BALANCED -> millis;
			case LOW_IMPACT -> millis * 3;
		};
	}

	/** Time a block scanner may use per tick. */
	public static long scanBudgetNanos() {
		return mode == PerformanceMode.BALANCED ? 2_500_000 : 1_000_000;
	}

	/** A configured limit on drawn targets, capped at {@code cap} outside Balanced mode. */
	public static int cap(int configured, int cap) {
		return mode == PerformanceMode.BALANCED ? configured : Math.min(configured, cap);
	}

	/** Ticks between two rebuilds of the HUD text. */
	public static int hudInterval() {
		return mode == PerformanceMode.BALANCED ? 1 : 2;
	}

	/** Whether the menu animates, given the user's animation option. */
	public static boolean animations(boolean option) {
		return option && mode != PerformanceMode.PERFORMANCE;
	}

	/** A pause between automatic actions in milliseconds, as the mode runs it. */
	public static long actionDelayMillis(long millis) {
		return mode == PerformanceMode.LOW_IMPACT ? Math.max(millis * 2, millis + 250) : millis;
	}

	/** A pause between automatic actions in ticks, as the mode runs it. */
	public static int actionDelayTicks(int ticks) {
		return mode == PerformanceMode.LOW_IMPACT ? Math.max(ticks * 2, ticks + 5) : ticks;
	}

	/** One line for the GUI describing the current mode. */
	public static String summary() {
		return switch (mode) {
			case PERFORMANCE -> "Performance: updates half as often, shorter lists, no menu animations.";
			case BALANCED -> "Balanced: every module runs as you set it.";
			case LOW_IMPACT -> "Low impact: slow updates, longer pauses between actions, rapid-action modules paused.";
		};
	}
}
