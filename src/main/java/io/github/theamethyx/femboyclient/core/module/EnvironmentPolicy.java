package io.github.theamethyx.femboyclient.core.module;

/** Describes where the client currently is: whether a world is loaded, and a short label for the GUI. */
public interface EnvironmentPolicy {
	/** Whether a world is loaded and a player exists. */
	boolean inWorld();

	/** Short description such as "Singleplayer" or "Server example.org:25565". */
	String describe();

	EnvironmentPolicy IN_WORLD = new EnvironmentPolicy() {
		@Override
		public boolean inWorld() {
			return true;
		}

		@Override
		public String describe() {
			return "Test environment";
		}
	};
}
