package io.github.theamethyx.femboyclient.gametest;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

import io.github.theamethyx.femboyclient.client.modules.OreSimulationModule;
import io.github.theamethyx.femboyclient.world.oresim.OreComparison;
import io.github.theamethyx.femboyclient.world.oresim.OreKind;
import io.github.theamethyx.femboyclient.world.oresim.OreSimulationCommand;
import io.github.theamethyx.femboyclient.world.oresim.OreSimulator;

/**
 * Ore Simulation in the naturally generated world: the module's predictions are compared with the real blocks the
 * integrated server generated, per ore kind, and the measured precision and recall are written to the report (they
 * are measurements of this seed and area, not guarantees).
 */
@SuppressWarnings("UnstableApiUsage")
final class OreSimulationTest {
	private OreSimulationTest() {
	}

	static void run(ClientGameTestContext context, TestSingleplayerContext world, TestReport report) {
		context.runOnClient(client -> {
			OreSimulationModule module = ClientGameTest.femboy().modules().get(OreSimulationModule.class);
			module.radiusSetting().set(2.0);
			((io.github.theamethyx.femboyclient.core.setting.IdSetSetting) module.settings().get("ores")).set(Set.of("minecraft:coal_ore", "minecraft:iron_ore", "minecraft:copper_ore", "minecraft:gold_ore",
					"minecraft:redstone_ore", "minecraft:lapis_ore", "minecraft:diamond_ore", "minecraft:emerald_ore"));
		});
		ClientGameTest.enable(context, OreSimulationModule.class, true);
		boolean simulated = ClientGameTest.waitUntil(context, () -> context.computeOnClient(client ->
				ClientGameTest.femboy().modules().get(OreSimulationModule.class).result() != null), 2400);
		OreSimulator.Result result = context.computeOnClient(client -> ClientGameTest.femboy().modules().get(OreSimulationModule.class).result());
		report.check("Ore Simulation predicts ores from the singleplayer seed", simulated && result != null && !result.predictions().isEmpty(),
				result == null ? "no result" : result.predictions().size() + " predictions");
		if (result == null) {
			ClientGameTest.enable(context, OreSimulationModule.class, false);
			return;
		}

		report.note(String.format(Locale.ROOT, "Ore Simulation: %d×%d chunks, %,d predictions, %d private terrain chunks, %,d feature runs (%d failed), %,d ms",
				2 * result.radius() + 1, 2 * result.radius() + 1, result.predictions().size(), result.terrainChunks(), result.featureRuns(),
				result.failedFeatures(), result.millis()));
		report.check("No vanilla ore feature fails inside the simulation", result.failedFeatures() == 0, result.failedFeatures() + " failed");

		// The real world, read on the server thread from the chunks the server generated.
		OreComparison.Summary all = world.getServer().computeOnServer(server -> {
			ServerLevel level = server.overworld();
			return OreComparison.compare(result, EnumSet.allOf(OreKind.class), (x, z) -> level.getChunkSource().getChunkNow(x, z)).summary();
		});
		report.note("Ore Simulation against the real world, all ores: " + all.describe());
		for (OreKind kind : EnumSet.range(OreKind.COAL, OreKind.EMERALD)) {
			OreComparison.Summary one = world.getServer().computeOnServer(server -> {
				ServerLevel level = server.overworld();
				return OreComparison.compare(result, EnumSet.of(kind), (x, z) -> level.getChunkSource().getChunkNow(x, z)).summary();
			});
			report.note(String.format(Locale.ROOT, "  %s: %,d predicted, %,d real; precision %s, recall %s", kind.label(), one.predicted(), one.actual(),
					percent(one.precision()), percent(one.recall())));
		}

		// A control area far from spawn, generated fresh just now: nothing (players, structures near spawn) has changed it.
		OreComparison.Summary fresh = world.getServer().computeOnServer(server -> {
			ServerLevel level = server.overworld();
			ChunkPos far = new ChunkPos(300, -250);
			for (int dx = -3; dx <= 3; dx++) {
				for (int dz = -3; dz <= 3; dz++) {
					level.getChunk(far.x + dx, far.z + dz);
				}
			}

			OreSimulator.Result control = OreSimulator.simulate(level, far, 2, EnumSet.allOf(OreKind.class), OreSimulator.Progress.NONE);
			return OreComparison.compare(control, EnumSet.allOf(OreKind.class), (x, z) -> level.getChunkSource().getChunkNow(x, z)).summary();
		});
		report.note("Ore Simulation in freshly generated chunks far from spawn: " + fresh.describe());
		report.check("In fresh chunks the simulation matches the world almost exactly (precision and recall above 97%)",
				fresh.precision() > 0.97 && fresh.recall() > 0.97, fresh.describe());
		report.check("Every chunk of the simulated area could be compared with the real world", all.checkedChunks() == all.totalChunks(),
				all.checkedChunks() + "/" + all.totalChunks());
		// Measured near spawn at 1.2.0: 96% precision, 99% recall (see VERIFICATION.md); the bounds leave room for the
		// order in which the world happened to generate chunks.
		report.check("Near spawn most predictions are real ores (precision above 90%)", all.precision() > 0.9, percent(all.precision()));
		report.check("Near spawn almost every real ore is predicted (recall above 95%)", all.recall() > 0.95, percent(all.recall()));

		boolean checked = ClientGameTest.waitUntil(context, () -> context.computeOnClient(client -> {
			OreSimulationModule module = ClientGameTest.femboy().modules().get(OreSimulationModule.class);
			return module.outcome() != null && module.outcome().summary().verified() > 0;
		}), 200);
		report.check("The module marks predictions in loaded chunks as verified", checked);
		boolean drawn = context.computeOnClient(client -> !ClientGameTest.femboy().modules().get(OreSimulationModule.class).shown().isEmpty());
		report.check("Ore Simulation draws highlights", drawn);
		context.takeScreenshot("world-ore-simulation");

		// Cancelling a larger run leaves no simulation running and keeps the previous result.
		context.runOnClient(client -> {
			OreSimulationModule module = ClientGameTest.femboy().modules().get(OreSimulationModule.class);
			module.runNow();
			module.cancelRun();
		});
		context.waitTicks(5);
		boolean cancelled = context.computeOnClient(client -> !ClientGameTest.femboy().modules().get(OreSimulationModule.class).simulating());
		report.check("A running ore simulation can be cancelled", cancelled);

		ClientGameTest.enable(context, OreSimulationModule.class, false);
		context.runOnClient(client -> ClientGameTest.femboy().modules().resetToDefaults(ClientGameTest.femboy().modules().get(OreSimulationModule.class)));

		report.check("The server's ore simulation command is off until its config enables it", !OreSimulationCommand.enabled());
		boolean registered = world.getServer().computeOnServer(server -> server.getCommands().getDispatcher().getRoot().getChild("femboyclient") != null);
		report.check("The operator command /femboyclient oresim is registered on the server", registered);
	}

	private static String percent(double value) {
		return value < 0 ? "n/a" : String.format(Locale.ROOT, "%.1f%%", value * 100);
	}
}
