package io.github.theamethyx.femboyclient.client;

/** Counts client ticks, so shared caches can tell whether they were built during the current tick. */
public final class ClientTicks {
	private static long count;

	private ClientTicks() {
	}

	/** Called once at the start of every client tick's module work. */
	static void advance() {
		count++;
	}

	public static long count() {
		return count;
	}
}
