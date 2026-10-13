package io.github.theamethyx.nova.gametest;

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

import io.github.theamethyx.nova.client.NovaClient;
import io.github.theamethyx.nova.client.fixture.FixtureRegistry;
import io.github.theamethyx.nova.client.gui.GuiState;
import io.github.theamethyx.nova.client.gui.NovaScreen;
import io.github.theamethyx.nova.client.gui.widget.KeybindButton;
import io.github.theamethyx.nova.client.gui.widget.ModuleRow;
import io.github.theamethyx.nova.client.gui.widget.TextField;
import io.github.theamethyx.nova.client.modules.ChunkBordersModule;
import io.github.theamethyx.nova.client.modules.FixtureContainersModule;
import io.github.theamethyx.nova.client.modules.FixtureEspModule;
import io.github.theamethyx.nova.client.modules.FreeCameraModule;
import io.github.theamethyx.nova.client.modules.WaypointsModule;
import io.github.theamethyx.nova.client.scenario.TestScenarioManager;
import io.github.theamethyx.nova.client.scenario.VisualFixtureScenario;
import io.github.theamethyx.nova.client.world.WorldIdentity;
import io.github.theamethyx.nova.core.module.Module;

/**
 * End-to-end tests inside a real Minecraft client. Screenshots go to
 * {@code build/run/clientGameTest/screenshots}; the check list to {@code nova-test-report.txt}.
 */
@SuppressWarnings("UnstableApiUsage")
public class NovaClientGameTest implements FabricClientGameTest {
	/** Fixed test layout in the flat test world (surface block at y = -61). */
	static final BlockPos PLAYER = new BlockPos(0, -60, 0);
	static final BlockPos SCENARIO_ORIGIN = new BlockPos(-6, -61, 4);

	@Override
	public void runTest(ClientGameTestContext context) {
		TestReport report = new TestReport(context);
		UiDriver ui = new UiDriver(context);

		report.check("NOVA entrypoint ran", context.computeOnClient(client -> NovaClient.get() != null && NovaClient.get().modules() != null));
		report.check("All modules registered", context.computeOnClient(client -> NovaClient.get().modules().all().size()) == 8);
		// Deterministic screenshots: no easing between frames.
		context.runOnClient(client -> NovaClient.get().options().animations.set(false));

		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			singleplayer.getClientWorld().waitForChunksRender();
			singleplayer.getServer().runCommand("tp @a " + PLAYER.getX() + ".5 " + PLAYER.getY() + " " + PLAYER.getZ() + ".5 0 15");
			context.waitTicks(5);
			report.check("Singleplayer counts as an authorized environment",
					context.computeOnClient(client -> NovaClient.get().modules().environment().isAuthorizedEnvironment()));

			guiWalkthrough(context, ui, report);
			moduleInteraction(context, ui, report);
			fixturesAndOverlays(context, singleplayer, report);
			freeCamera(context, report);
			configAndProfiles(context, report);

			int errors = context.computeOnClient(client -> NovaClient.get().diagnostics().errorCount());
			report.check("No module errors were logged during the run", errors == 0, errors == 0 ? ""
					: errors + " error(s): " + context.computeOnClient(client -> NovaClient.get().diagnostics().snapshot().toString()));
		}

		report.finish();
	}

	/** Opens the ClickGUI with the real key binding and screenshots every page at two sizes. */
	private static void guiWalkthrough(ClientGameTestContext context, UiDriver ui, TestReport report) {
		context.getInput().pressKey(GLFW.GLFW_KEY_RIGHT_SHIFT);
		context.waitForScreen(NovaScreen.class);
		report.check("Right Shift opens the NOVA screen", true);

		for (int[] size : new int[][] {{854, 480}, {1280, 720}}) {
			context.getInput().resizeWindow(size[0], size[1]);
			context.waitTicks(2);
			int pages = context.computeOnClient(client -> ((NovaScreen) client.screen).pageCount());
			for (int page = 0; page < pages; page++) {
				int index = page;
				context.runOnClient(client -> ((NovaScreen) client.screen).showPage(index));
				context.waitTicks(3);
				String title = context.computeOnClient(client -> ((NovaScreen) client.screen).currentPage().title());
				context.takeScreenshot("gui-" + size[0] + "x" + size[1] + "-" + index + "-" + title.toLowerCase().replaceAll("[^a-z]+", "-"));
			}
		}

		report.check("Every page rendered at 854x480 and 1280x720", true);
		context.runOnClient(client -> ((NovaScreen) client.screen).showPage(0));
		ui.key(GLFW.GLFW_KEY_ESCAPE);
		context.waitForScreen(null);
		report.check("Escape closes the NOVA screen", true);
	}

	/** Search, toggle, key binding and the in-world key press, all through simulated input. */
	private static void moduleInteraction(ClientGameTestContext context, UiDriver ui, TestReport report) {
		GuiState.moduleSearch = "";
		context.getInput().pressKey(GLFW.GLFW_KEY_RIGHT_SHIFT);
		context.waitForScreen(NovaScreen.class);
		context.runOnClient(client -> ((NovaScreen) client.screen).showPage(1));
		context.waitTicks(2);

		TextField search = ui.find(TextField.class, field -> true);
		ui.click(search);
		ui.type("chunk");
		context.waitTicks(2);
		List<String> listed = context.computeOnClient(client -> ((NovaScreen) client.screen).currentPage().descendants(ModuleRow.class)
				.stream().map(row -> row.module().id()).toList());
		// Name matches come first; modules whose setting names match ("Show chunk" in Coordinates) follow.
		report.check("Search 'chunk' lists Chunk Borders first, then setting matches", !listed.isEmpty()
				&& listed.getFirst().equals("chunk_borders") && listed.contains("coordinates_hud") && listed.size() == 2, "listed " + listed);

		ModuleRow row = ui.find(ModuleRow.class, r -> r.module().id().equals("chunk_borders"));
		ui.click(row.toggle());
		boolean enabled = context.computeOnClient(client -> NovaClient.get().modules().get("chunk_borders").isEnabled());
		report.check("Clicking the switch enables Chunk Borders", enabled);
		context.takeScreenshot("modules-chunk-borders-selected");

		KeybindButton keyButton = ui.find(KeybindButton.class, button -> true);
		ui.click(keyButton);
		ui.key(GLFW.GLFW_KEY_K);
		int key = context.computeOnClient(client -> NovaClient.get().modules().get("chunk_borders").key());
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
		boolean afterFirst = context.computeOnClient(client -> NovaClient.get().modules().get("chunk_borders").isEnabled());
		ui.key(GLFW.GLFW_KEY_K);
		boolean afterSecond = context.computeOnClient(client -> NovaClient.get().modules().get("chunk_borders").isEnabled());
		report.check("Pressing K in the world toggles Chunk Borders off and on (keyboard mixin)", !afterFirst && afterSecond,
				"after first press " + afterFirst + ", after second " + afterSecond);
		context.waitTicks(3);
		context.takeScreenshot("world-chunk-borders");
	}

	/** Builds the visual fixtures and checks that only registered fixtures are highlighted. */
	private static void fixturesAndOverlays(ClientGameTestContext context, TestSingleplayerContext singleplayer, TestReport report) {
		context.runOnClient(client -> NovaClient.get().scenarios().buildAt(VisualFixtureScenario.ID, SCENARIO_ORIGIN));
		context.waitFor(client -> NovaClient.get().scenarios().run(VisualFixtureScenario.ID).state() == TestScenarioManager.State.BUILT, 200);
		context.waitTicks(10);

		String world = context.computeOnClient(client -> WorldIdentity.current());
		Set<UUID> registered = context.computeOnClient(client -> {
			Set<UUID> set = new HashSet<>();
			NovaClient.get().fixtures().entities(world).forEach(fixture -> set.add(fixture.uuid()));
			return set;
		});
		report.check("Scenario registered exactly 2 fixture entities", registered.size() == 2, "registered " + registered.size());

		context.runOnClient(client -> {
			var modules = NovaClient.get().modules();
			modules.setEnabled(modules.get("chunk_borders"), false);
			modules.setEnabled(modules.get(FixtureEspModule.class), true);
			modules.setEnabled(modules.get(FixtureContainersModule.class), true);
			WaypointsModule waypoints = modules.get(WaypointsModule.class);
			modules.setEnabled(waypoints, true);
		});
		// A waypoint at the scenario's far corner, added the same way the GUI button does.
		singleplayer.getServer().runCommand("tp @a 6.5 -60 12.5 180 15");
		context.waitTicks(3);
		String waypointError = context.computeOnClient(client -> NovaClient.get().modules().get(WaypointsModule.class).addAtPlayer("Test corner"));
		report.check("A waypoint can be added at the player position", waypointError == null, String.valueOf(waypointError));
		singleplayer.getServer().runCommand("tp @a 0.5 -60 0.5 0 20");
		context.waitTicks(10);

		Set<UUID> highlighted = new HashSet<>(context.computeOnClient(client -> NovaClient.get().modules().get(FixtureEspModule.class).lastHighlighted()));
		report.check("Fixture ESP highlights exactly the registered entities (decoy excluded)", highlighted.equals(registered),
				"highlighted " + highlighted.size() + ", registered " + registered.size());

		List<FixtureContainersModule.Observed> observed = context.computeOnClient(client -> NovaClient.get().modules().get(FixtureContainersModule.class).lastObserved());
		long present = observed.stream().filter(o -> o.status() == FixtureContainersModule.Status.PRESENT).count();
		report.check("Both registered containers observed as present (decoy chest not listed)", observed.size() == 2 && present == 2,
				"observed " + observed.size() + ", present " + present);
		context.takeScreenshot("world-fixtures");

		// Remove the registered chest on the server; the visualization must report it missing.
		FixtureRegistry.ContainerFixture chest = context.computeOnClient(client -> NovaClient.get().fixtures()
				.containers(world, WorldIdentity.dimension()).stream().filter(f -> f.label().contains("chest")).findFirst().orElseThrow());
		singleplayer.getServer().runOnServer(server -> server.overworld().setBlockAndUpdate(new BlockPos(chest.x(), chest.y(), chest.z()), Blocks.AIR.defaultBlockState()));
		context.waitTicks(10);
		List<FixtureContainersModule.Observed> afterRemoval = context.computeOnClient(client -> NovaClient.get().modules().get(FixtureContainersModule.class).lastObserved());
		boolean chestMissing = afterRemoval.stream().anyMatch(o -> o.fixture().label().contains("chest") && o.status() == FixtureContainersModule.Status.MISSING);
		report.check("A removed fixture chest is reported as missing", chestMissing, afterRemoval.toString());
		context.takeScreenshot("world-fixture-chest-missing");

		context.runOnClient(client -> {
			var modules = NovaClient.get().modules();
			modules.setEnabled(modules.get(FixtureEspModule.class), false);
			modules.setEnabled(modules.get(FixtureContainersModule.class), false);
		});
	}

	/** The camera must move while the player stays put, and snap back afterwards. */
	private static void freeCamera(ClientGameTestContext context, TestReport report) {
		Vec3 playerBefore = context.computeOnClient(client -> client.player.position());
		context.runOnClient(client -> NovaClient.get().modules().setEnabled(NovaClient.get().modules().get(FreeCameraModule.class), true));
		context.waitTicks(2);
		Vec3 cameraStart = context.computeOnClient(client -> NovaClient.get().modules().get(FreeCameraModule.class).cameraPosition());
		context.getInput().holdKeyFor(options -> options.keyUp, 15);
		context.waitTicks(2);
		Vec3 cameraEnd = context.computeOnClient(client -> NovaClient.get().modules().get(FreeCameraModule.class).cameraPosition());
		Vec3 playerAfter = context.computeOnClient(client -> client.player.position());
		context.takeScreenshot("world-free-camera");

		report.check("Free camera moves forward with the movement key", cameraStart != null && cameraEnd != null && cameraEnd.distanceTo(cameraStart) > 5,
				"moved " + (cameraStart == null || cameraEnd == null ? "n/a" : String.format("%.2f", cameraEnd.distanceTo(cameraStart))));
		report.check("The player does not move while the free camera is active", playerAfter.distanceTo(playerBefore) < 0.1,
				String.format("player moved %.3f", playerAfter.distanceTo(playerBefore)));

		context.runOnClient(client -> NovaClient.get().modules().setEnabled(NovaClient.get().modules().get(FreeCameraModule.class), false));
		context.waitTicks(2);
		report.check("Disabling the free camera returns the view to the player",
				context.computeOnClient(client -> client.getCameraEntity() == client.player));
	}

	/** Settings persist through save and reload; profiles keep independent state. */
	private static void configAndProfiles(ClientGameTestContext context, TestReport report) {
		boolean persisted = context.computeOnClient(client -> {
			NovaClient nova = NovaClient.get();
			ChunkBordersModule chunk = nova.modules().get(ChunkBordersModule.class);
			var radius = (io.github.theamethyx.nova.core.setting.NumberSetting) chunk.settings().get("radius");
			radius.set(3.0);
			nova.config().saveNow();
			radius.set(0.0);
			nova.config().load();
			return radius.get() == 3.0;
		});
		report.check("A changed setting survives save and reload", persisted);

		Path profileFile = context.computeOnClient(client -> NovaClient.get().config().root().resolve("profiles").resolve("default.json"));
		report.check("Profile file exists on disk", Files.exists(profileFile), profileFile.toString());

		String result = context.computeOnClient(client -> {
			NovaClient nova = NovaClient.get();
			Module coords = nova.modules().get("coordinates_hud");
			String problem = nova.config().createProfile("gametest");
			if (problem != null) return "create: " + problem;
			problem = nova.config().switchProfile("gametest");
			if (problem != null) return "switch: " + problem;
			nova.modules().setEnabled(coords, false);
			nova.config().switchProfile("default");
			boolean defaultHasCoords = coords.isEnabled();
			nova.config().switchProfile("gametest");
			boolean gametestHasCoords = coords.isEnabled();
			nova.config().switchProfile("default");
			nova.config().deleteProfile("gametest");
			return defaultHasCoords && !gametestHasCoords ? "ok" : "default=" + defaultHasCoords + " gametest=" + gametestHasCoords;
		});
		report.check("Profiles keep independent module state", result.equals("ok"), result);
	}
}
