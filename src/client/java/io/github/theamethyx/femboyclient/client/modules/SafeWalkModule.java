package io.github.theamethyx.femboyclient.client.modules;

import io.github.theamethyx.femboyclient.client.FemboyClient;
import io.github.theamethyx.femboyclient.core.module.Module;
import io.github.theamethyx.femboyclient.core.module.ModuleCategory;

/** Stops you at block edges like sneaking does, without slowing you down. Sandbox only. */
public final class SafeWalkModule extends Module {
	public SafeWalkModule() {
		super("safe_walk", "Safe Walk", "Stops you at block edges like sneaking, at full speed.", ModuleCategory.MOVEMENT, false, NO_KEY);
	}

	@Override
	public boolean testEnvironmentOnly() {
		return true;
	}

	public static boolean active() {
		FemboyClient femboy = FemboyClient.get();
		SafeWalkModule module = femboy == null ? null : femboy.modules().get(SafeWalkModule.class);
		return module != null && module.isRunning();
	}
}
