package io.github.theamethyx.femboyclient.client;

import io.github.theamethyx.femboyclient.client.modules.AmethystGeodeAnalyzerModule;
import io.github.theamethyx.femboyclient.client.modules.ChunkBordersModule;
import io.github.theamethyx.femboyclient.client.modules.CoordinatesHudModule;
import io.github.theamethyx.femboyclient.client.modules.FixtureContainersModule;
import io.github.theamethyx.femboyclient.client.modules.FixtureEspModule;
import io.github.theamethyx.femboyclient.client.modules.FreeCameraModule;
import io.github.theamethyx.femboyclient.client.modules.ModuleListHudModule;
import io.github.theamethyx.femboyclient.client.modules.PerformanceHudModule;
import io.github.theamethyx.femboyclient.client.modules.WaypointsModule;
import io.github.theamethyx.femboyclient.core.module.ModuleManager;

/** The single place where all built-in modules are registered, in display order. */
public final class ModuleRegistry {
	private ModuleRegistry() {
	}

	public static void registerAll(ModuleManager modules) {
		modules.register(new CoordinatesHudModule());
		modules.register(new PerformanceHudModule());
		modules.register(new ModuleListHudModule());
		modules.register(new WaypointsModule());
		modules.register(new ChunkBordersModule());
		modules.register(new FixtureEspModule());
		modules.register(new FixtureContainersModule());
		modules.register(new FreeCameraModule());
		modules.register(new AmethystGeodeAnalyzerModule());
	}
}
