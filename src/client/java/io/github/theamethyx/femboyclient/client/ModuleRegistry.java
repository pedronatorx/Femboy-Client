package io.github.theamethyx.femboyclient.client;

import io.github.theamethyx.femboyclient.client.modules.AimAssistModule;
import io.github.theamethyx.femboyclient.client.modules.AutoArmorModule;
import io.github.theamethyx.femboyclient.client.modules.AutoAttributeSwapModule;
import io.github.theamethyx.femboyclient.client.modules.AutoCriticalsModule;
import io.github.theamethyx.femboyclient.client.modules.AutoReconnectModule;
import io.github.theamethyx.femboyclient.client.modules.AutoRefillModule;
import io.github.theamethyx.femboyclient.client.modules.AutoStunSlamModule;
import io.github.theamethyx.femboyclient.client.modules.AutoToolModule;
import io.github.theamethyx.femboyclient.client.modules.AutoTotemModule;
import io.github.theamethyx.femboyclient.client.modules.AutoWeaponSwitchModule;
import io.github.theamethyx.femboyclient.client.modules.BetterChatModule;
import io.github.theamethyx.femboyclient.client.modules.BlockCounterModule;
import io.github.theamethyx.femboyclient.client.modules.BlockHighlightModule;
import io.github.theamethyx.femboyclient.client.modules.ChunkBordersModule;
import io.github.theamethyx.femboyclient.client.modules.CombatInfoModule;
import io.github.theamethyx.femboyclient.client.modules.CoordinatesHudModule;
import io.github.theamethyx.femboyclient.client.modules.DeathCoordinatesModule;
import io.github.theamethyx.femboyclient.client.modules.EffectsModule;
import io.github.theamethyx.femboyclient.client.modules.ElytraFlightModule;
import io.github.theamethyx.femboyclient.client.modules.ElytraInfoModule;
import io.github.theamethyx.femboyclient.client.modules.EntityEspModule;
import io.github.theamethyx.femboyclient.client.modules.ExplosionInfoModule;
import io.github.theamethyx.femboyclient.client.modules.FlightModule;
import io.github.theamethyx.femboyclient.client.modules.FreeCameraModule;
import io.github.theamethyx.femboyclient.client.modules.FullbrightModule;
import io.github.theamethyx.femboyclient.client.modules.GeodeAnalyzerModule;
import io.github.theamethyx.femboyclient.client.modules.HealthArmorModule;
import io.github.theamethyx.femboyclient.client.modules.HitboxesModule;
import io.github.theamethyx.femboyclient.client.modules.InventorySorterModule;
import io.github.theamethyx.femboyclient.client.modules.ItemCountersModule;
import io.github.theamethyx.femboyclient.client.modules.JavaRunnerModule;
import io.github.theamethyx.femboyclient.client.modules.KeystrokesModule;
import io.github.theamethyx.femboyclient.client.modules.KillAuraModule;
import io.github.theamethyx.femboyclient.client.modules.LightLevelModule;
import io.github.theamethyx.femboyclient.client.modules.ModuleListHudModule;
import io.github.theamethyx.femboyclient.client.modules.MovementInfoModule;
import io.github.theamethyx.femboyclient.client.modules.NoFallModule;
import io.github.theamethyx.femboyclient.client.modules.NoSlowModule;
import io.github.theamethyx.femboyclient.client.modules.NotificationsModule;
import io.github.theamethyx.femboyclient.client.modules.OreEspModule;
import io.github.theamethyx.femboyclient.client.modules.PerformanceHudModule;
import io.github.theamethyx.femboyclient.client.modules.PlayerAlertModule;
import io.github.theamethyx.femboyclient.client.modules.PlayerFinderModule;
import io.github.theamethyx.femboyclient.client.modules.ReachModule;
import io.github.theamethyx.femboyclient.client.modules.SafeWalkModule;
import io.github.theamethyx.femboyclient.client.modules.ShulkerPreviewModule;
import io.github.theamethyx.femboyclient.client.modules.SpeedModule;
import io.github.theamethyx.femboyclient.client.modules.SprintModule;
import io.github.theamethyx.femboyclient.client.modules.StepModule;
import io.github.theamethyx.femboyclient.client.modules.StorageEspModule;
import io.github.theamethyx.femboyclient.client.modules.StrafeModule;
import io.github.theamethyx.femboyclient.client.modules.TargetHudModule;
import io.github.theamethyx.femboyclient.client.modules.TrackersModule;
import io.github.theamethyx.femboyclient.client.modules.TrajectoriesModule;
import io.github.theamethyx.femboyclient.client.modules.TriggerBotModule;
import io.github.theamethyx.femboyclient.client.modules.VelocityModule;
import io.github.theamethyx.femboyclient.client.modules.WTapModule;
import io.github.theamethyx.femboyclient.client.modules.WaypointsModule;
import io.github.theamethyx.femboyclient.client.modules.WeaponInfoModule;
import io.github.theamethyx.femboyclient.client.modules.ZoomModule;
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

		// Automation (test environments only)
		modules.register(new KillAuraModule());
		modules.register(new TriggerBotModule());
		modules.register(new AimAssistModule());
		modules.register(new ReachModule());
		modules.register(new VelocityModule());
		modules.register(new AutoStunSlamModule());
		modules.register(new AutoTotemModule());
		modules.register(new AutoCriticalsModule());
		modules.register(new WTapModule());
		modules.register(new AutoWeaponSwitchModule());

		// Movement
		modules.register(new FreeCameraModule());
		modules.register(new SprintModule());
		modules.register(new FlightModule());
		modules.register(new ElytraFlightModule());
		modules.register(new SpeedModule());
		modules.register(new StrafeModule());
		modules.register(new StepModule());
		modules.register(new SafeWalkModule());
		modules.register(new NoSlowModule());
		modules.register(new NoFallModule());
		modules.register(new MovementInfoModule());
		modules.register(new ElytraInfoModule());

		// Visuals
		modules.register(new EntityEspModule());
		modules.register(new StorageEspModule());
		modules.register(new HitboxesModule());
		modules.register(new TrajectoriesModule());
		modules.register(new FullbrightModule());
		modules.register(new ChunkBordersModule());
		modules.register(new OreEspModule());
		modules.register(new LightLevelModule());
		modules.register(new BlockHighlightModule());
		modules.register(new ZoomModule());

		// Inventory
		modules.register(new ItemCountersModule());
		modules.register(new HealthArmorModule());
		modules.register(new EffectsModule());
		modules.register(new AutoToolModule());
		modules.register(new AutoArmorModule());
		modules.register(new AutoRefillModule());
		modules.register(new InventorySorterModule());
		modules.register(new ShulkerPreviewModule());

		// Trackers
		modules.register(new PlayerFinderModule());
		modules.register(new TrackersModule());
		modules.register(new PlayerAlertModule());
		modules.register(new WaypointsModule());

		// World
		modules.register(new GeodeAnalyzerModule());
		modules.register(new BlockCounterModule());
		modules.register(new DeathCoordinatesModule());

		// HUD
		modules.register(new CoordinatesHudModule());
		modules.register(new PerformanceHudModule());
		modules.register(new ModuleListHudModule());
		modules.register(new KeystrokesModule());
		modules.register(new NotificationsModule());
		modules.register(new BetterChatModule());
		modules.register(new AutoReconnectModule());

		// Developer
		modules.register(new JavaRunnerModule());
	}
}
