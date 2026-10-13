package io.github.theamethyx.nova.client;

import io.github.theamethyx.nova.core.module.ModuleManager;

/** The single place where all built-in modules are registered, in display order. */
public final class ModuleRegistry {
	private ModuleRegistry() {
	}

	public static void registerAll(ModuleManager modules) {
		// Modules are added here as they are implemented (Stage 5 onwards).
	}
}
