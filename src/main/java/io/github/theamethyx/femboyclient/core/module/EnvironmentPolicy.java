package io.github.theamethyx.femboyclient.core.module;

/**
 * Describes where the client currently is, so modules can decide whether they may run.
 *
 * <p>Restricted modules (world scanning, fixture highlighting, free camera) only run in
 * singleplayer worlds or on servers the user has explicitly listed as authorized test
 * environments.
 */
public interface EnvironmentPolicy {
	/** Whether a world is loaded and a player exists. */
	boolean inWorld();

	/** Whether restricted research and visualization modules are allowed in the current world. */
	boolean isAuthorizedEnvironment();

	/** Short description such as "Singleplayer" or "Server localhost:25565 (not authorized)". */
	String describe();

	EnvironmentPolicy ALWAYS_AUTHORIZED = new EnvironmentPolicy() {
		@Override
		public boolean inWorld() {
			return true;
		}

		@Override
		public boolean isAuthorizedEnvironment() {
			return true;
		}

		@Override
		public String describe() {
			return "Test environment";
		}
	};
}
