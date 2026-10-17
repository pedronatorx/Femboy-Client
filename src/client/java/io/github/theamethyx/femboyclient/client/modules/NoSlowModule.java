package io.github.theamethyx.femboyclient.client.modules;

import io.github.theamethyx.femboyclient.client.FemboyClient;
import io.github.theamethyx.femboyclient.core.module.Module;
import io.github.theamethyx.femboyclient.core.module.ModuleCategory;

/** Keeps your full walking speed while eating, drinking, blocking or drawing a bow. Sandbox only. */
public final class NoSlowModule extends Module {
	public NoSlowModule() {
		super("no_slow", "No Slow", "Full walking speed while using items (eating, blocking, drawing a bow).", ModuleCategory.MOVEMENT, false, NO_KEY);
	}

	@Override
	public boolean testEnvironmentOnly() {
		return false;
	}

	public static boolean active() {
		FemboyClient femboy = FemboyClient.get();
		NoSlowModule module = femboy == null ? null : femboy.modules().get(NoSlowModule.class);
		return module != null && module.isRunning();
	}
}
