package io.github.theamethyx.femboyclient.gametest;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.world.level.gamerules.GameRules;

import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.loader.api.FabricLoader;

import io.github.theamethyx.femboyclient.client.FemboyClient;
import io.github.theamethyx.femboyclient.client.modules.GeodeAnalyzerModule;
import io.github.theamethyx.femboyclient.core.research.geode.GeodeObservation;
import io.github.theamethyx.femboyclient.core.setting.NumberSetting;

/**
 * Naturally generated control geodes in a normal (non-flat) world with a fixed seed:
 * <ol>
 *   <li>observe every geode the analyzer finds right after the world is created (negative controls:
 *       almost no time has passed since generation);</li>
 *   <li>apply a documented simulation condition through the game's normal chunk ticking: random
 *       tick speed 300 for 600 game ticks with the player present;</li>
 *   <li>observe the same geodes again and record, per geode, the chunk's inhabited time and its
 *       distance from the player as ground truth.</li>
 * </ol>
 * The findings are written to {@code femboyclient-research/natural-world.md} in the run directory.
 */
@SuppressWarnings("UnstableApiUsage")
final class NaturalWorldTest {
	static final String SEED = "8675309";
	static final int BOOSTED_SPEED = 300;
	static final int BOOSTED_TICKS = 600;

	private NaturalWorldTest() {
	}

	static void run(ClientGameTestContext context, TestReport report) {
		try (TestSingleplayerContext world = context.worldBuilder().setUseConsistentSettings(false).adjustSettings(creator -> {
			creator.setSeed(SEED);
			creator.setGameMode(WorldCreationUiState.SelectedGameMode.CREATIVE);
		}).create()) {
			world.getClientWorld().waitForChunksRender();
			context.waitTicks(20);
			ExtraModulesTest.structureFinder(context, report);

			List<GeodeObservation> before = scan(context);
			List<GeodeObservation> natural = before.stream().filter(g -> g.scenarioId() == null).toList();
			long complete = natural.stream().filter(GeodeObservation::complete).count();
			report.note("Natural world (seed " + SEED + "): " + natural.size() + " geode(s) in the scanned area, " + complete + " complete");
			report.check("Naturally generated geodes are found and grouped in a normal world", !natural.isEmpty());
			report.check("Every natural geode's completeness is stated (complete or incomplete with a reason)",
					natural.stream().allMatch(g -> g.completenessNote() != null && !g.completenessNote().isBlank()));

			Map<String, long[]> chunkInfoBefore = chunkInfo(context, world, natural);
			int simulationDistance = context.computeOnClient(client -> client.getSingleplayerServer().getPlayerList().getSimulationDistance());

			world.getServer().runOnServer(server -> server.overworld().getGameRules().set(GameRules.RANDOM_TICK_SPEED, BOOSTED_SPEED, server));
			context.waitTicks(BOOSTED_TICKS);
			world.getServer().runOnServer(server -> server.overworld().getGameRules().set(GameRules.RANDOM_TICK_SPEED, 3, server));
			context.waitTicks(20);

			List<GeodeObservation> after = scan(context).stream().filter(g -> g.scenarioId() == null).toList();
			Map<String, long[]> chunkInfoAfter = chunkInfo(context, world, after);

			StringBuilder md = new StringBuilder("# Natural world control and natural ticking\n\n");
			md.append("World seed ").append(SEED).append(", normal world type. Observed by the client analyzer (same code as in the GUI).\n\n");
			md.append(String.format(Locale.ROOT, "Condition between the two observations: random_tick_speed %d for %d game ticks with the player present "
					+ "(server simulation distance %d chunks). For chunks that were randomly ticking, the expected number of random ticks per block is "
					+ "%d x %d / 4096 = %.1f (about %.1f h at default speed).\n\n", BOOSTED_SPEED, BOOSTED_TICKS, simulationDistance,
					BOOSTED_TICKS, BOOSTED_SPEED, BOOSTED_TICKS * BOOSTED_SPEED / 4096.0, BOOSTED_TICKS * BOOSTED_SPEED / 4096.0 / 52.73));
			md.append("| Geode | Complete | Chunk distance | Inhabited ticks before/after | Before (S/M/L/C) | After (S/M/L/C) | Clusters before/after |\n");
			md.append("|---|---|---:|---:|---|---|---:|\n");

			int matched = 0;
			int clustersLost = 0;
			int grewWithinSimulation = 0;
			int withinSimulation = 0;
			for (GeodeObservation b : natural) {
				GeodeObservation a = after.stream().filter(g -> g.id().equals(b.id())).findFirst().orElse(null);
				if (a == null) {
					continue;
				}

				matched++;
				long[] infoBefore = chunkInfoBefore.get(b.id());
				long[] infoAfter = chunkInfoAfter.get(a.id());
				long distance = infoAfter == null ? -1 : infoAfter[1];
				if (a.counts().cluster() < b.counts().cluster()) clustersLost++;
				if (distance >= 0 && distance <= simulationDistance) {
					withinSimulation++;
					if (a.counts().total() > b.counts().total() || a.counts().cluster() > b.counts().cluster()) grewWithinSimulation++;
				}

				md.append(String.format(Locale.ROOT, "| %s | %s/%s | %d | %d / %d | %s | %s | %d / %d |\n", b.id(), b.complete() ? "yes" : "no",
						a.complete() ? "yes" : "no", distance, infoBefore == null ? -1 : infoBefore[0], infoAfter == null ? -1 : infoAfter[0],
						stages(b), stages(a), b.counts().cluster(), a.counts().cluster()));
			}

			md.append("\nNotes:\n\n");
			md.append("- 'Inhabited ticks' is the chunk's inhabitedTime counter, which only advances while a non-spectator player is within 128 blocks; ")
					.append("random ticks reach every entity-ticking chunk, so the two need not agree.\n");
			md.append("- Natural geodes can be cut by caves, aquifers or the scan boundary; incomplete ones undercount.\n");
			md.append("- These are a handful of geodes from one seed: a qualitative check of the natural pathway, not a statistical evaluation.\n");
			try {
				Path dir = FabricLoader.getInstance().getGameDir().resolve("femboyclient-research");
				Files.createDirectories(dir);
				Files.writeString(dir.resolve("natural-world.md"), md.toString());
			} catch (java.io.IOException e) {
				report.note("Could not write natural-world.md: " + e.getMessage());
			}

			report.note(String.format(Locale.ROOT, "Natural ticking: %d geode(s) re-observed, %d within simulation distance, %d of those grew", matched, withinSimulation, grewWithinSimulation));
			report.check("Natural geodes are re-identified by the same id after time passes", matched > 0, matched + " matched");
			report.check("No natural geode lost clusters without block removal", clustersLost == 0, clustersLost + " lost clusters");
			report.check("At least one geode within simulation distance grew under boosted random ticking", withinSimulation == 0 || grewWithinSimulation > 0,
					grewWithinSimulation + " of " + withinSimulation);
			context.takeScreenshot("natural-world");
		}
	}

	private static List<GeodeObservation> scan(ClientGameTestContext context) {
		context.runOnClient(client -> {
			GeodeAnalyzerModule analyzer = FemboyClient.get().modules().get(GeodeAnalyzerModule.class);
			int radius = Math.max(2, Math.min(8, client.options.getEffectiveRenderDistance() - 1));
			((NumberSetting) analyzer.settings().get("radius")).set((double) radius);
		});
		// Off and on again: a fresh read of every loaded chunk, as after joining a world.
		List<GeodeObservation> geodes = ClientGameTest.analyzeFresh(context);
		return new ArrayList<>(geodes);
	}

	/** Per geode id: {inhabitedTime of the chunk at its center, chunk distance from the player}. */
	private static Map<String, long[]> chunkInfo(ClientGameTestContext context, TestSingleplayerContext world, List<GeodeObservation> geodes) {
		int[] playerChunk = context.computeOnClient(client -> new int[] {client.player.getBlockX() >> 4, client.player.getBlockZ() >> 4});
		Map<String, long[]> info = new LinkedHashMap<>();
		for (GeodeObservation geode : geodes) {
			int cx = ((int) Math.floor(geode.centerX())) >> 4;
			int cz = ((int) Math.floor(geode.centerZ())) >> 4;
			long inhabited = world.getServer().computeOnServer(server -> server.overworld().getChunk(cx, cz).getInhabitedTime());
			info.put(geode.id(), new long[] {inhabited, Math.max(Math.abs(cx - playerChunk[0]), Math.abs(cz - playerChunk[1]))});
		}

		return info;
	}

	private static String stages(GeodeObservation g) {
		return g.counts().small() + "/" + g.counts().medium() + "/" + g.counts().large() + "/" + g.counts().cluster();
	}
}
