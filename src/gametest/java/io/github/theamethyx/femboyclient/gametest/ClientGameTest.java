package io.github.theamethyx.femboyclient.gametest;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.lwjgl.glfw.GLFW;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

import io.github.theamethyx.femboyclient.client.FemboyClient;
import io.github.theamethyx.femboyclient.client.fixture.FixtureRegistry;
import io.github.theamethyx.femboyclient.client.gui.GuiState;
import io.github.theamethyx.femboyclient.client.gui.ClientScreen;
import io.github.theamethyx.femboyclient.client.gui.widget.KeybindButton;
import io.github.theamethyx.femboyclient.client.gui.widget.ModuleRow;
import io.github.theamethyx.femboyclient.client.gui.widget.TextField;
import io.github.theamethyx.femboyclient.client.analysis.WorldScanner;
import io.github.theamethyx.femboyclient.client.modules.AmethystGeodeAnalyzerModule;
import io.github.theamethyx.femboyclient.client.modules.ChunkBordersModule;
import io.github.theamethyx.femboyclient.client.modules.FixtureContainersModule;
import io.github.theamethyx.femboyclient.client.modules.FixtureEspModule;
import io.github.theamethyx.femboyclient.client.modules.FreeCameraModule;
import io.github.theamethyx.femboyclient.client.modules.WaypointsModule;
import io.github.theamethyx.femboyclient.client.scenario.TestScenarioManager;
import io.github.theamethyx.femboyclient.client.scenario.VisualFixtureScenario;
import io.github.theamethyx.femboyclient.client.world.WorldIdentity;
import io.github.theamethyx.femboyclient.core.module.Module;
import io.github.theamethyx.femboyclient.core.research.geode.GeodeObservation;

/**
 * End-to-end tests inside a real Minecraft client. Screenshots go to
 * {@code build/run/clientGameTest/screenshots}; the check list to {@code test-report.txt}.
 */
@SuppressWarnings("UnstableApiUsage")
public class ClientGameTest implements FabricClientGameTest {
	/** Fixed test layout in the flat test world (surface block at y = -61). */
	static final BlockPos PLAYER = new BlockPos(0, -60, 0);
	static final BlockPos SCENARIO_ORIGIN = new BlockPos(-6, -61, 4);
	static final BlockPos GEODE_ORIGIN = new BlockPos(-16, -61, 14);

	@Override
	public void runTest(ClientGameTestContext context) {
		TestReport report = new TestReport(context);
		UiDriver ui = new UiDriver(context);

		report.check("Femboy Client entrypoint ran", context.computeOnClient(client -> FemboyClient.get() != null && FemboyClient.get().modules() != null));
		report.check("All modules registered", context.computeOnClient(client -> FemboyClient.get().modules().all().size()) == 9);
		// Deterministic screenshots: no easing between frames.
		context.runOnClient(client -> FemboyClient.get().options().animations.set(false));

		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			singleplayer.getClientWorld().waitForChunksRender();
			// Creative mode: test travel (Nether, teleports into terrain) must not kill the player.
			singleplayer.getServer().runCommand("gamemode creative @a");
			singleplayer.getServer().runCommand("tp @a " + PLAYER.getX() + ".5 " + PLAYER.getY() + " " + PLAYER.getZ() + ".5 0 15");
			context.waitTicks(5);
			report.check("Singleplayer counts as an authorized environment",
					context.computeOnClient(client -> FemboyClient.get().modules().environment().isAuthorizedEnvironment()));

			guiWalkthrough(context, ui, report);
			moduleInteraction(context, ui, report);
			fixturesAndOverlays(context, singleplayer, report);
			freeCamera(context, report);
			geodeAnalysis(context, ui, singleplayer, report);
			dimensionChange(context, singleplayer, report);
			researchRun(context, ui, report);
			configAndProfiles(context, report);

			controlInteractions(context, ui, report);
			int errors = context.computeOnClient(client -> FemboyClient.get().diagnostics().errorCount());
			report.check("No module errors were logged during the run", errors == 0, errors == 0 ? ""
					: errors + " error(s): " + context.computeOnClient(client -> FemboyClient.get().diagnostics().snapshot().toString()));
		}

		NaturalWorldTest.run(context, report);
		MultiplayerRestrictionTest.run(context, ui, report);
		inspectLog(report);
		report.finish();
	}

	/** Opens the ClickGUI with the real key binding and screenshots every page at two sizes. */
	private static void guiWalkthrough(ClientGameTestContext context, UiDriver ui, TestReport report) {
		context.getInput().pressKey(GLFW.GLFW_KEY_RIGHT_SHIFT);
		context.waitForScreen(ClientScreen.class);
		report.check("Right Shift opens the Femboy Client screen", true);

		for (int[] size : new int[][] {{854, 480}, {1280, 720}}) {
			context.getInput().resizeWindow(size[0], size[1]);
			context.waitTicks(2);
			int pages = context.computeOnClient(client -> ((ClientScreen) client.screen).pageCount());
			for (int page = 0; page < pages; page++) {
				int index = page;
				context.runOnClient(client -> ((ClientScreen) client.screen).showPage(index));
				context.waitTicks(3);
				String title = context.computeOnClient(client -> ((ClientScreen) client.screen).currentPage().title());
				context.takeScreenshot("gui-" + size[0] + "x" + size[1] + "-" + index + "-" + title.toLowerCase().replaceAll("[^a-z]+", "-"));
			}
		}

		report.check("Every page rendered at 854x480 and 1280x720", true);
		context.runOnClient(client -> ((ClientScreen) client.screen).showPage(0));
		ui.key(GLFW.GLFW_KEY_ESCAPE);
		context.waitForScreen(null);
		report.check("Escape closes the Femboy Client screen", true);
	}

	/** Search, toggle, key binding and the in-world key press, all through simulated input. */
	private static void moduleInteraction(ClientGameTestContext context, UiDriver ui, TestReport report) {
		GuiState.moduleSearch = "";
		context.getInput().pressKey(GLFW.GLFW_KEY_RIGHT_SHIFT);
		context.waitForScreen(ClientScreen.class);
		context.runOnClient(client -> ((ClientScreen) client.screen).showPage(1));
		context.waitTicks(2);

		TextField search = ui.find(TextField.class, field -> true);
		ui.click(search);
		ui.type("chunk");
		context.waitTicks(2);
		List<String> listed = context.computeOnClient(client -> ((ClientScreen) client.screen).currentPage().descendants(ModuleRow.class)
				.stream().map(row -> row.module().id()).toList());
		// Name matches come first; modules whose setting names match ("Show chunk" in Coordinates) follow.
		report.check("Search 'chunk' lists Chunk Borders first, then setting matches", !listed.isEmpty()
				&& listed.getFirst().equals("chunk_borders") && listed.contains("coordinates_hud") && listed.size() == 2, "listed " + listed);

		ModuleRow row = ui.find(ModuleRow.class, r -> r.module().id().equals("chunk_borders"));
		ui.click(row.toggle());
		boolean enabled = context.computeOnClient(client -> FemboyClient.get().modules().get("chunk_borders").isEnabled());
		report.check("Clicking the switch enables Chunk Borders", enabled);
		context.takeScreenshot("modules-chunk-borders-selected");

		KeybindButton keyButton = ui.find(KeybindButton.class, button -> true);
		ui.click(keyButton);
		ui.key(GLFW.GLFW_KEY_K);
		int key = context.computeOnClient(client -> FemboyClient.get().modules().get("chunk_borders").key());
		report.check("Key capture binds K to Chunk Borders", key == GLFW.GLFW_KEY_K, "key=" + key);

		ui.click(search);
		// Clear the search field (Ctrl+A then Backspace) so later runs start clean.
		context.getInput().holdControl();
		context.getInput().pressKey(GLFW.GLFW_KEY_A);
		context.getInput().releaseControl();
		ui.key(GLFW.GLFW_KEY_BACKSPACE);
		ui.key(GLFW.GLFW_KEY_ESCAPE); // leaves the text field
		ui.key(GLFW.GLFW_KEY_ESCAPE); // closes the screen
		context.waitForScreen(null);

		ui.key(GLFW.GLFW_KEY_K);
		boolean afterFirst = context.computeOnClient(client -> FemboyClient.get().modules().get("chunk_borders").isEnabled());
		ui.key(GLFW.GLFW_KEY_K);
		boolean afterSecond = context.computeOnClient(client -> FemboyClient.get().modules().get("chunk_borders").isEnabled());
		report.check("Pressing K in the world toggles Chunk Borders off and on (keyboard mixin)", !afterFirst && afterSecond,
				"after first press " + afterFirst + ", after second " + afterSecond);
		context.waitTicks(3);
		context.takeScreenshot("world-chunk-borders");
	}

	/** Builds the visual fixtures and checks that only registered fixtures are highlighted. */
	private static void fixturesAndOverlays(ClientGameTestContext context, TestSingleplayerContext singleplayer, TestReport report) {
		context.runOnClient(client -> FemboyClient.get().scenarios().buildAt(VisualFixtureScenario.ID, SCENARIO_ORIGIN));
		context.waitFor(client -> FemboyClient.get().scenarios().run(VisualFixtureScenario.ID).state() == TestScenarioManager.State.BUILT, 200);
		context.waitTicks(10);

		String world = context.computeOnClient(client -> WorldIdentity.current());
		Set<UUID> registered = context.computeOnClient(client -> {
			Set<UUID> set = new HashSet<>();
			FemboyClient.get().fixtures().entities(world).forEach(fixture -> set.add(fixture.uuid()));
			return set;
		});
		report.check("Scenario registered exactly 2 fixture entities", registered.size() == 2, "registered " + registered.size());

		context.runOnClient(client -> {
			var modules = FemboyClient.get().modules();
			modules.setEnabled(modules.get("chunk_borders"), false);
			modules.setEnabled(modules.get(FixtureEspModule.class), true);
			modules.setEnabled(modules.get(FixtureContainersModule.class), true);
			WaypointsModule waypoints = modules.get(WaypointsModule.class);
			modules.setEnabled(waypoints, true);
		});
		// A waypoint at the scenario's far corner, added the same way the GUI button does.
		singleplayer.getServer().runCommand("tp @a 6.5 -60 12.5 180 15");
		context.waitTicks(3);
		String waypointError = context.computeOnClient(client -> FemboyClient.get().modules().get(WaypointsModule.class).addAtPlayer("Test corner"));
		report.check("A waypoint can be added at the player position", waypointError == null, String.valueOf(waypointError));
		singleplayer.getServer().runCommand("tp @a 0.5 -60 0.5 0 20");
		context.waitTicks(10);

		Set<UUID> highlighted = new HashSet<>(context.computeOnClient(client -> FemboyClient.get().modules().get(FixtureEspModule.class).lastHighlighted()));
		report.check("Fixture ESP highlights exactly the registered entities (decoy excluded)", highlighted.equals(registered),
				"highlighted " + highlighted.size() + ", registered " + registered.size());

		List<FixtureContainersModule.Observed> observed = context.computeOnClient(client -> FemboyClient.get().modules().get(FixtureContainersModule.class).lastObserved());
		long present = observed.stream().filter(o -> o.status() == FixtureContainersModule.Status.PRESENT).count();
		report.check("Both registered containers observed as present (decoy chest not listed)", observed.size() == 2 && present == 2,
				"observed " + observed.size() + ", present " + present);
		context.takeScreenshot("world-fixtures");

		// Remove the registered chest on the server; the visualization must report it missing.
		FixtureRegistry.ContainerFixture chest = context.computeOnClient(client -> FemboyClient.get().fixtures()
				.containers(world, WorldIdentity.dimension()).stream().filter(f -> f.label().contains("chest")).findFirst().orElseThrow());
		singleplayer.getServer().runOnServer(server -> server.overworld().setBlockAndUpdate(new BlockPos(chest.x(), chest.y(), chest.z()), Blocks.AIR.defaultBlockState()));
		context.waitTicks(10);
		List<FixtureContainersModule.Observed> afterRemoval = context.computeOnClient(client -> FemboyClient.get().modules().get(FixtureContainersModule.class).lastObserved());
		boolean chestMissing = afterRemoval.stream().anyMatch(o -> o.fixture().label().contains("chest") && o.status() == FixtureContainersModule.Status.MISSING);
		report.check("A removed fixture chest is reported as missing", chestMissing, afterRemoval.toString());
		context.takeScreenshot("world-fixture-chest-missing");

		context.runOnClient(client -> {
			var modules = FemboyClient.get().modules();
			modules.setEnabled(modules.get(FixtureEspModule.class), false);
			modules.setEnabled(modules.get(FixtureContainersModule.class), false);
		});
	}

	/** The camera must move while the player stays put, and snap back afterwards. */
	private static void freeCamera(ClientGameTestContext context, TestReport report) {
		Vec3 playerBefore = context.computeOnClient(client -> client.player.position());
		context.runOnClient(client -> FemboyClient.get().modules().setEnabled(FemboyClient.get().modules().get(FreeCameraModule.class), true));
		context.waitTicks(2);
		Vec3 cameraStart = context.computeOnClient(client -> FemboyClient.get().modules().get(FreeCameraModule.class).cameraPosition());
		context.getInput().holdKeyFor(options -> options.keyUp, 15);
		context.waitTicks(2);
		Vec3 cameraEnd = context.computeOnClient(client -> FemboyClient.get().modules().get(FreeCameraModule.class).cameraPosition());
		Vec3 playerAfter = context.computeOnClient(client -> client.player.position());
		context.takeScreenshot("world-free-camera");

		report.check("Free camera moves forward with the movement key", cameraStart != null && cameraEnd != null && cameraEnd.distanceTo(cameraStart) > 5,
				"moved " + (cameraStart == null || cameraEnd == null ? "n/a" : String.format("%.2f", cameraEnd.distanceTo(cameraStart))));
		report.check("The player does not move while the free camera is active", playerAfter.distanceTo(playerBefore) < 0.1,
				String.format("player moved %.3f", playerAfter.distanceTo(playerBefore)));

		context.runOnClient(client -> FemboyClient.get().modules().setEnabled(FemboyClient.get().modules().get(FreeCameraModule.class), false));
		context.waitTicks(2);
		report.check("Disabling the free camera returns the view to the player",
				context.computeOnClient(client -> client.getCameraEntity() == client.player));
	}

	/** The analyzer must reproduce the server-side ground truth exactly and flag partial scans. */
	private static void geodeAnalysis(ClientGameTestContext context, UiDriver ui, TestSingleplayerContext singleplayer, TestReport report) {
		singleplayer.getServer().runCommand("tp @a 0.5 -60 0.5 0 0");
		context.waitTicks(5);
		context.runOnClient(client -> FemboyClient.get().scenarios().buildAt("geode_180", GEODE_ORIGIN));
		context.waitFor(client -> FemboyClient.get().scenarios().run("geode_180").state() != TestScenarioManager.State.BUILDING, 600);
		TestScenarioManager.Run run = context.computeOnClient(client -> FemboyClient.get().scenarios().run("geode_180"));
		report.check("Geode fixture scenario built", run.state() == TestScenarioManager.State.BUILT, run.message());
		context.waitTicks(40);

		com.google.gson.JsonObject census = context.computeOnClient(client -> FemboyClient.get().groundTruth().get(run.runId()).conditions().getAsJsonObject("serverCensus"));
		io.github.theamethyx.femboyclient.core.research.StageCounts truth = new io.github.theamethyx.femboyclient.core.research.StageCounts(
				census.get("small").getAsInt(), census.get("medium").getAsInt(), census.get("large").getAsInt(), census.get("cluster").getAsInt());
		report.note("Ground truth (server census) for " + run.runId() + ": " + truth);

		context.runOnClient(client -> {
			var modules = FemboyClient.get().modules();
			AmethystGeodeAnalyzerModule analyzer = modules.get(AmethystGeodeAnalyzerModule.class);
			modules.setEnabled(analyzer, true);
			((io.github.theamethyx.femboyclient.core.setting.NumberSetting) analyzer.settings().get("radius")).set(1.0);
		});
		context.waitTicks(2);
		context.runOnClient(client -> FemboyClient.get().modules().get(AmethystGeodeAnalyzerModule.class).startScan());
		context.waitFor(client -> FemboyClient.get().modules().get(AmethystGeodeAnalyzerModule.class).scanner().state() == WorldScanner.State.DONE, 600);
		List<GeodeObservation> partial = context.computeOnClient(client -> FemboyClient.get().modules().get(AmethystGeodeAnalyzerModule.class).results());
		GeodeObservation partialGeode = partial.stream().filter(g -> run.runId().equals(g.scenarioId())).findFirst().orElse(null);
		report.check("With a 1-chunk radius the fixture geode is reported as incomplete, not as complete or absent",
				partialGeode != null && !partialGeode.complete() && partialGeode.counts().total() <= truth.total(),
				partialGeode == null ? "geode not found; results " + partial.size() : partialGeode.counts() + " complete=" + partialGeode.complete() + "; " + partialGeode.completenessNote());

		context.runOnClient(client -> {
			AmethystGeodeAnalyzerModule analyzer = FemboyClient.get().modules().get(AmethystGeodeAnalyzerModule.class);
			((io.github.theamethyx.femboyclient.core.setting.NumberSetting) analyzer.settings().get("radius")).set(4.0);
			analyzer.startScan();
		});
		context.waitFor(client -> FemboyClient.get().modules().get(AmethystGeodeAnalyzerModule.class).scanner().state() == WorldScanner.State.DONE, 600);
		List<GeodeObservation> full = context.computeOnClient(client -> FemboyClient.get().modules().get(AmethystGeodeAnalyzerModule.class).results());
		GeodeObservation geode = full.stream().filter(g -> run.runId().equals(g.scenarioId())).findFirst().orElse(null);
		report.note("4-chunk scan found " + full.size() + " geode(s)");
		report.check("With a 4-chunk radius the fixture geode is complete", geode != null && geode.complete(),
				geode == null ? "not found" : geode.completenessNote());
		report.check("Analyzer stage counts equal the server ground truth exactly", geode != null && geode.counts().equals(truth),
				"analyzer " + (geode == null ? "n/a" : geode.counts()) + " vs ground truth " + truth);
		report.check("Budding block count equals ground truth", geode != null && geode.buddingBlocks() == context.computeOnClient(client ->
				FemboyClient.get().groundTruth().get(run.runId()).conditions().get("buddingBlocks").getAsInt()));
		report.check("Exactly one geode is linked to the fixture run (no double counting)",
				full.stream().filter(g -> run.runId().equals(g.scenarioId())).count() == 1);

		// Open the World Analysis page, select the fixture geode, save it and follow the report link.
		context.getInput().pressKey(GLFW.GLFW_KEY_RIGHT_SHIFT);
		context.waitForScreen(ClientScreen.class);
		String geodeId = geode == null ? "" : geode.id();
		context.runOnClient(client -> {
			FemboyClient.get().modules().get(AmethystGeodeAnalyzerModule.class).select(geodeId);
			ClientScreen screen = (ClientScreen) client.screen;
			for (int i = 0; i < screen.pageCount(); i++) {
				screen.showPage(i);
				if (screen.currentPage().title().equals("World Analysis")) break;
			}
		});
		context.waitTicks(5);
		context.takeScreenshot("analysis-geode-180");

		io.github.theamethyx.femboyclient.client.gui.widget.Button save = ui.find(io.github.theamethyx.femboyclient.client.gui.widget.Button.class,
				b -> b.label().equals("Save observation"));
		report.check("Save observation button is present", save != null);
		ui.click(save);
		int saved = context.computeOnClient(client -> FemboyClient.get().results().all().size());
		report.check("Saving stores one observation record", saved == 1, "records " + saved);
		context.waitTicks(2);
		io.github.theamethyx.femboyclient.client.gui.widget.Button open = ui.find(io.github.theamethyx.femboyclient.client.gui.widget.Button.class,
				b -> b.label().equals("Open report"));
		ui.click(open);
		context.waitTicks(5);
		String page = context.computeOnClient(client -> ((ClientScreen) client.screen).currentPage().title());
		report.check("'Open report' navigates to the Test Results page", page.equals("Test Results"), page);
		context.takeScreenshot("results-saved-observation");

		Path reportFile = context.computeOnClient(client -> {
			var record = FemboyClient.get().results().all().getFirst();
			return FemboyClient.get().results().reportPath(record.id());
		});
		String markdown = Files.exists(reportFile) ? readQuietly(reportFile) : "";
		report.check("A Markdown report was written with direct observations and labelled estimates",
				markdown.contains("## Direct observation") && markdown.contains("## Statistical estimates") && markdown.contains("not probabilities"),
				reportFile.toString());
		ui.key(GLFW.GLFW_KEY_ESCAPE);
		context.waitForScreen(null);
	}

	/** Travelling to the Nether and back must not break modules or show stale results. */
	private static void dimensionChange(ClientGameTestContext context, TestSingleplayerContext singleplayer, TestReport report) {
		int resultsBefore = context.computeOnClient(client -> FemboyClient.get().modules().get(AmethystGeodeAnalyzerModule.class).results().size());
		context.runOnClient(client -> {
			var modules = FemboyClient.get().modules();
			modules.setEnabled(modules.get(WaypointsModule.class), true);
			modules.setEnabled(modules.get(ChunkBordersModule.class), true);
		});
		singleplayer.getServer().runCommand("execute in minecraft:the_nether run tp @a 0.5 128 0.5");
		context.waitFor(client -> client.level != null && client.level.dimension().identifier().getPath().equals("the_nether"), 400);
		context.waitForScreen(null); // the vanilla level-loading screen closes first
		context.waitTicks(40);
		var analyzerResults = context.computeOnClient(client -> FemboyClient.get().modules().get(AmethystGeodeAnalyzerModule.class).results().size());
		report.check("Overworld geode results are cleared after moving to the Nether", resultsBefore > 0 && analyzerResults == 0,
				resultsBefore + " before, " + analyzerResults + " after");
		report.check("Modules keep running after a dimension change", context.computeOnClient(client ->
				FemboyClient.get().modules().get(WaypointsModule.class).isRunning() && FemboyClient.get().modules().get(ChunkBordersModule.class).isRunning()));
		report.check("Overworld waypoints are not shown in the Nether",
				context.computeOnClient(client -> FemboyClient.get().modules().get(WaypointsModule.class).visibleHere().isEmpty()));
		context.takeScreenshot("world-nether-after-transition");
		singleplayer.getServer().runCommand("execute in minecraft:overworld run tp @a 0.5 -60 0.5 0 0");
		context.waitFor(client -> client.level != null && client.level.dimension().identifier().getPath().equals("overworld"), 400);
		context.waitForScreen(null);
		context.waitTicks(20);
		report.check("Overworld waypoints reappear after returning",
				!context.computeOnClient(client -> FemboyClient.get().modules().get(WaypointsModule.class).visibleHere().isEmpty()));
		context.runOnClient(client -> FemboyClient.get().modules().setEnabled(FemboyClient.get().modules().get(ChunkBordersModule.class), false));
		int errors = context.computeOnClient(client -> FemboyClient.get().diagnostics().errorCount());
		report.check("No module errors after the dimension changes", errors == 0, errors + " errors");
	}

	/** Runs the quick research experiment from the Results page and checks the stored evaluation. */
	private static void researchRun(ClientGameTestContext context, UiDriver ui, TestReport report) {
		report.note("Before opening the menu: screen=" + context.computeOnClient(client -> String.valueOf(client.screen))
				+ ", level=" + context.computeOnClient(client -> client.level == null ? "none" : client.level.dimension().identifier().toString())
				+ ", openMenu bound to " + context.computeOnClient(client -> io.github.theamethyx.femboyclient.client.ClientKeys.openMenu.saveString()));
		context.getInput().pressKey(GLFW.GLFW_KEY_RIGHT_SHIFT);
		context.waitForScreen(ClientScreen.class);
		context.runOnClient(client -> ((ClientScreen) client.screen).openResult(null));
		context.waitTicks(3);
		ui.click(ui.find(io.github.theamethyx.femboyclient.client.gui.widget.Button.class, b -> b.label().equals("Evaluations")));
		ui.click(ui.find(io.github.theamethyx.femboyclient.client.gui.widget.Button.class, b -> b.label().equals("Run quick experiment")));
		var state = context.computeOnClient(client -> FemboyClient.get().experiments().state());
		report.check("Run quick experiment starts the experiment", state == io.github.theamethyx.femboyclient.client.research.ExperimentRunner.State.RUNNING, String.valueOf(state));
		context.waitTicks(40);
		context.takeScreenshot("results-evaluation-running");
		context.waitFor(client -> {
			var s = FemboyClient.get().experiments().state();
			return s != io.github.theamethyx.femboyclient.client.research.ExperimentRunner.State.RUNNING
					&& s != io.github.theamethyx.femboyclient.client.research.ExperimentRunner.State.EVALUATING;
		}, 12000);
		String message = context.computeOnClient(client -> FemboyClient.get().experiments().message());
		state = context.computeOnClient(client -> FemboyClient.get().experiments().state());
		report.check("The quick experiment finishes and is evaluated", state == io.github.theamethyx.femboyclient.client.research.ExperimentRunner.State.DONE, message);
		var evaluations = context.computeOnClient(client -> FemboyClient.get().results().evaluations());
		report.check("An evaluation report and summary were saved", !evaluations.isEmpty() && Files.exists(evaluations.getFirst().markdown()));
		if (!evaluations.isEmpty()) {
			var summary = evaluations.getFirst().summary();
			report.note("Quick evaluation: " + summary.get("samples") + " observations");
			for (var h : summary.getAsJsonArray("hypotheses")) {
				report.note("  " + h.getAsJsonObject().get("id").getAsString() + " " + h.getAsJsonObject().get("verdict").getAsString()
						+ ": " + h.getAsJsonObject().get("reason").getAsString());
			}
		}

		boolean cleared = context.computeOnClient(client -> {
			var center = client.player.blockPosition().above(48);
			return client.level.getBlockState(new net.minecraft.core.BlockPos(Math.floorDiv(center.getX(), 16) * 16, center.getY(), center.getZ())).isAir();
		});
		report.check("The experiment's stone cube was removed afterwards", cleared);
		context.waitTicks(5);
		context.takeScreenshot("results-evaluation");
		ui.click(ui.find(io.github.theamethyx.femboyclient.client.gui.widget.Button.class, b -> b.label().equals("Indicators")));
		context.waitTicks(3);
		context.takeScreenshot("results-indicators");
		String amethystStatus = context.computeOnClient(client -> io.github.theamethyx.femboyclient.core.research.IndicatorRegistry
				.build(FemboyClient.get().results().evaluations().getFirst().summary()).getFirst().status().label());
		report.note("Amethyst indicator status after the quick run: " + amethystStatus);
		long untested = context.computeOnClient(client -> io.github.theamethyx.femboyclient.core.research.IndicatorRegistry.build(null).stream()
				.filter(i -> i.status() == io.github.theamethyx.femboyclient.core.research.IndicatorRegistry.Status.UNTESTED).count());
		report.check("Indicators without experiments stay 'Untested' (no fabricated results)", untested == 5);
		ui.key(GLFW.GLFW_KEY_ESCAPE);
		context.waitForScreen(null);
	}

	/** Slider drag and dropdown selection through simulated mouse input. */
	private static void controlInteractions(ClientGameTestContext context, UiDriver ui, TestReport report) {
		GuiState.moduleSearch = "";
		GuiState.selectedModule = "chunk_borders";
		context.getInput().pressKey(GLFW.GLFW_KEY_RIGHT_SHIFT);
		context.waitForScreen(ClientScreen.class);
		context.runOnClient(client -> ((ClientScreen) client.screen).showPage(1));
		context.waitTicks(3);

		io.github.theamethyx.femboyclient.client.gui.widget.SettingRow radiusRow = ui.find(io.github.theamethyx.femboyclient.client.gui.widget.SettingRow.class,
				row -> row.setting().id().equals("radius"));
		ui.click(radiusRow.control());
		var slider = radiusRow.control();
		ui.clickVirtual(slider.x() + slider.width() - 1, slider.y() + slider.height() / 2.0);
		double radius = context.computeOnClient(client -> (Double) FemboyClient.get().modules().get("chunk_borders").settings().get("radius").get());
		report.check("Clicking the right end of the 'Neighbor chunks' slider sets its maximum (4)", radius == 4.0, "value " + radius);

		io.github.theamethyx.femboyclient.client.gui.widget.SettingRow colorRow = ui.find(io.github.theamethyx.femboyclient.client.gui.widget.SettingRow.class,
				row -> row.setting().id().equals("color"));
		ui.click(colorRow.control());
		boolean open = context.computeOnClient(client -> ((io.github.theamethyx.femboyclient.client.gui.widget.Dropdown<?>) colorRow.control()).isOpen(
				((ClientScreen) client.screen).uiContext()));
		report.check("Clicking a dropdown opens its option list", open);
		context.takeScreenshot("modules-dropdown-open");
		// Options are 13 px tall below the control; the second option is Cyan.
		var dropdown = colorRow.control();
		ui.clickVirtual(dropdown.x() + 20, dropdown.y() + dropdown.height() + 1 + 13 + 6);
		Object color = context.computeOnClient(client -> FemboyClient.get().modules().get("chunk_borders").settings().get("color").get());
		report.check("Choosing 'Cyan' in the dropdown changes the setting", String.valueOf(color).equals("CYAN"), String.valueOf(color));
		ui.key(GLFW.GLFW_KEY_ESCAPE);
		context.waitForScreen(null);
	}

	/**
	 * Scans the client log for problems attributable to the game session: errors on the render or
	 * server thread, and any stack frame inside Femboy Client's own code. Known noise of the offline test
	 * environment is ignored and listed in the report.
	 */
	private static void inspectLog(TestReport report) {
		Path log = net.fabricmc.loader.api.FabricLoader.getInstance().getGameDir().resolve("logs/latest.log");
		List<String> problems = new java.util.ArrayList<>();
		for (String line : readQuietly(log).split("\\n")) {
			boolean knownNoise = line.contains("Anisotropic") || line.contains("cursor shape") || line.contains("GL ERROR ##")
					|| line.contains("@ Render") || line.contains("Realms") || line.contains("realms") || line.contains("user properties");
			boolean gameThreadError = line.contains("[Render thread/ERROR]") || line.contains("[Server thread/ERROR]");
			boolean clientFrame = line.contains("io.github.theamethyx") && (line.contains("Exception") || line.trim().startsWith("at "));
			boolean testFrame = line.contains("io.github.theamethyx.femboyclient.gametest");
			if ((gameThreadError || clientFrame) && !knownNoise && !testFrame) {
				problems.add(line.length() > 160 ? line.substring(0, 160) : line);
			}
		}

		report.note("Ignored known offline-test noise: missing X11 cursor shapes (Xvfb), anisotropic filtering option, Realms and "
				+ "account-properties requests failing without a real login.");
		report.check("No render-thread, server-thread or client-code errors in the client log", problems.isEmpty(), String.join(" | ", problems));
	}

	private static String readQuietly(Path path) {
		try {
			return Files.readString(path);
		} catch (java.io.IOException e) {
			return "";
		}
	}

	/** Settings persist through save and reload; profiles keep independent state. */
	private static void configAndProfiles(ClientGameTestContext context, TestReport report) {
		boolean persisted = context.computeOnClient(client -> {
			FemboyClient femboy = FemboyClient.get();
			ChunkBordersModule chunk = femboy.modules().get(ChunkBordersModule.class);
			var radius = (io.github.theamethyx.femboyclient.core.setting.NumberSetting) chunk.settings().get("radius");
			radius.set(3.0);
			femboy.config().saveNow();
			radius.set(0.0);
			femboy.config().load();
			return radius.get() == 3.0;
		});
		report.check("A changed setting survives save and reload", persisted);

		Path profileFile = context.computeOnClient(client -> FemboyClient.get().config().root().resolve("profiles").resolve("default.json"));
		report.check("Profile file exists on disk", Files.exists(profileFile), profileFile.toString());

		String result = context.computeOnClient(client -> {
			FemboyClient femboy = FemboyClient.get();
			Module coords = femboy.modules().get("coordinates_hud");
			String problem = femboy.config().createProfile("gametest");
			if (problem != null) return "create: " + problem;
			problem = femboy.config().switchProfile("gametest");
			if (problem != null) return "switch: " + problem;
			femboy.modules().setEnabled(coords, false);
			femboy.config().switchProfile("default");
			boolean defaultHasCoords = coords.isEnabled();
			femboy.config().switchProfile("gametest");
			boolean gametestHasCoords = coords.isEnabled();
			femboy.config().switchProfile("default");
			femboy.config().deleteProfile("gametest");
			return defaultHasCoords && !gametestHasCoords ? "ok" : "default=" + defaultHasCoords + " gametest=" + gametestHasCoords;
		});
		report.check("Profiles keep independent module state", result.equals("ok"), result);
	}
}
