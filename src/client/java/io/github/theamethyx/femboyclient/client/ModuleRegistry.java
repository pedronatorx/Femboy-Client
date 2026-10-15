package io.github.theamethyx.femboyclient.client;

import io.github.theamethyx.femboyclient.client.modules.AutoAttributeSwapModule;
import io.github.theamethyx.femboyclient.client.modules.ChunkBordersModule;
import io.github.theamethyx.femboyclient.client.modules.CoordinatesHudModule;
import io.github.theamethyx.femboyclient.client.modules.EntityEspModule;
import io.github.theamethyx.femboyclient.client.modules.FreeCameraModule;
import io.github.theamethyx.femboyclient.client.modules.FullbrightModule;
import io.github.theamethyx.femboyclient.client.modules.GeodeAnalyzerModule;
import io.github.theamethyx.femboyclient.client.modules.JavaRunnerModule;
import io.github.theamethyx.femboyclient.client.modules.ModuleListHudModule;
import io.github.theamethyx.femboyclient.client.modules.PerformanceHudModule;
import io.github.theamethyx.femboyclient.client.modules.PlayerAlertModule;
import io.github.theamethyx.femboyclient.client.modules.PlayerFinderModule;
import io.github.theamethyx.femboyclient.client.modules.StorageEspModule;
import io.github.theamethyx.femboyclient.client.modules.TrackersModule;
import io.github.theamethyx.femboyclient.client.modules.WaypointsModule;
import io.github.theamethyx.femboyclient.core.module.ModuleManager;

/**
 * The single place where all built-in modules are registered, in display order. A new module only needs
 * a line here; the GUI builds its pages and settings panels from the module's category and settings.
 */
public final class ModuleRegistry {
	private ModuleRegistry() {
	}

	public static void registerAll(ModuleManager modules) {
		// Combat
		modules.register(new AutoAttributeSwapModule());
		// Visuals
		modules.register(new EntityEspModule());
		modules.register(new StorageEspModule());
		modules.register(new TrackersModule());
		modules.register(new FullbrightModule());
		modules.register(new FreeCameraModule());
		modules.register(new ChunkBordersModule());
		// World
		modules.register(new GeodeAnalyzerModule());
		modules.register(new WaypointsModule());
		// Player
		modules.register(new PlayerFinderModule());
		modules.register(new PlayerAlertModule());
		// HUD
		modules.register(new CoordinatesHudModule());
		modules.register(new PerformanceHudModule());
		modules.register(new ModuleListHudModule());
		// Developer
		modules.register(new JavaRunnerModule());
	}
}
