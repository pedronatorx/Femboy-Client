package io.github.theamethyx.femboyclient.client;

import io.github.theamethyx.femboyclient.client.modules.AutoAttributeSwapModule;
import io.github.theamethyx.femboyclient.client.modules.ChunkBordersModule;
import io.github.theamethyx.femboyclient.client.modules.CombatInfoModule;
import io.github.theamethyx.femboyclient.client.modules.CoordinatesHudModule;
import io.github.theamethyx.femboyclient.client.modules.EffectsModule;
import io.github.theamethyx.femboyclient.client.modules.ElytraInfoModule;
import io.github.theamethyx.femboyclient.client.modules.EntityEspModule;
import io.github.theamethyx.femboyclient.client.modules.ExplosionInfoModule;
import io.github.theamethyx.femboyclient.client.modules.FreeCameraModule;
import io.github.theamethyx.femboyclient.client.modules.FullbrightModule;
import io.github.theamethyx.femboyclient.client.modules.GeodeAnalyzerModule;
import io.github.theamethyx.femboyclient.client.modules.HealthArmorModule;
import io.github.theamethyx.femboyclient.client.modules.HitboxesModule;
import io.github.theamethyx.femboyclient.client.modules.ItemCountersModule;
import io.github.theamethyx.femboyclient.client.modules.JavaRunnerModule;
import io.github.theamethyx.femboyclient.client.modules.ModuleListHudModule;
import io.github.theamethyx.femboyclient.client.modules.MovementInfoModule;
import io.github.theamethyx.femboyclient.client.modules.PerformanceHudModule;
import io.github.theamethyx.femboyclient.client.modules.PlayerAlertModule;
import io.github.theamethyx.femboyclient.client.modules.PlayerFinderModule;
import io.github.theamethyx.femboyclient.client.modules.StorageEspModule;
import io.github.theamethyx.femboyclient.client.modules.TargetHudModule;
import io.github.theamethyx.femboyclient.client.modules.TrackersModule;
import io.github.theamethyx.femboyclient.client.modules.TrajectoriesModule;
import io.github.theamethyx.femboyclient.client.modules.WaypointsModule;
import io.github.theamethyx.femboyclient.client.modules.WeaponInfoModule;
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
		modules.register(new CombatInfoModule());
		modules.register(new TargetHudModule());
		modules.register(new WeaponInfoModule());
		modules.register(new ExplosionInfoModule());

		// Movement
		modules.register(new FreeCameraModule());
		modules.register(new MovementInfoModule());
		modules.register(new ElytraInfoModule());

		// Visuals
		modules.register(new EntityEspModule());
		modules.register(new StorageEspModule());
		modules.register(new HitboxesModule());
		modules.register(new TrajectoriesModule());
		modules.register(new FullbrightModule());
		modules.register(new ChunkBordersModule());

		// Inventory
		modules.register(new ItemCountersModule());
		modules.register(new HealthArmorModule());
		modules.register(new EffectsModule());

		// Trackers
		modules.register(new PlayerFinderModule());
		modules.register(new TrackersModule());
		modules.register(new PlayerAlertModule());
		modules.register(new WaypointsModule());

		// World
		modules.register(new GeodeAnalyzerModule());

		// HUD
		modules.register(new CoordinatesHudModule());
		modules.register(new PerformanceHudModule());
		modules.register(new ModuleListHudModule());

		// Developer
		modules.register(new JavaRunnerModule());
	}
}
