package io.github.theamethyx.femboyclient.gametest;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import org.lwjgl.glfw.GLFW;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

import io.github.theamethyx.femboyclient.client.FemboyClient;
import io.github.theamethyx.femboyclient.client.gui.ClientScreen;
import io.github.theamethyx.femboyclient.client.gui.GuiState;
import io.github.theamethyx.femboyclient.client.gui.page.HomePage;
import io.github.theamethyx.femboyclient.client.gui.page.ModulesPage;
import io.github.theamethyx.femboyclient.client.gui.page.SettingsPage;
import io.github.theamethyx.femboyclient.client.gui.widget.Button;
import io.github.theamethyx.femboyclient.client.gui.widget.ColorPicker;
import io.github.theamethyx.femboyclient.client.gui.widget.Dropdown;
import io.github.theamethyx.femboyclient.client.gui.widget.GeodePanel;
import io.github.theamethyx.femboyclient.client.gui.widget.IdSetRow;
import io.github.theamethyx.femboyclient.client.gui.widget.KeybindButton;
import io.github.theamethyx.femboyclient.client.gui.widget.ModuleRow;
import io.github.theamethyx.femboyclient.client.gui.CommandPalette;
import io.github.theamethyx.femboyclient.client.gui.widget.SettingRow;
import io.github.theamethyx.femboyclient.client.gui.widget.TextField;
import io.github.theamethyx.femboyclient.client.gui.widget.Widget;
import io.github.theamethyx.femboyclient.client.modules.AutoAttributeSwapModule;
import io.github.theamethyx.femboyclient.client.modules.ChunkBordersModule;
import io.github.theamethyx.femboyclient.client.modules.JavaRunnerModule;
import io.github.theamethyx.femboyclient.client.modules.TrackersModule;
import io.github.theamethyx.femboyclient.client.modules.EntityEspModule;
import io.github.theamethyx.femboyclient.client.modules.FreeCameraModule;
import io.github.theamethyx.femboyclient.client.modules.GeodeAnalyzerModule;
import io.github.theamethyx.femboyclient.client.modules.PlayerFinderModule;
import io.github.theamethyx.femboyclient.client.modules.StorageEspModule;
import io.github.theamethyx.femboyclient.client.modules.WaypointsModule;
import io.github.theamethyx.femboyclient.core.module.Module;
import io.github.theamethyx.femboyclient.core.research.StageCounts;
import io.github.theamethyx.femboyclient.core.research.geode.GeodeObservation;
import io.github.theamethyx.femboyclient.core.setting.ColorSetting;
import io.github.theamethyx.femboyclient.core.setting.IdSetSetting;
import io.github.theamethyx.femboyclient.core.setting.NumberSetting;
import io.github.theamethyx.femboyclient.world.GeodeCensus;
import io.github.theamethyx.femboyclient.world.GeodeFixtureBuilder;

/**
 * End-to-end tests inside a real Minecraft client. Screenshots go to
 * {@code build/run/clientGameTest/screenshots}; the check list to {@code test-report.txt}.
 */
@SuppressWarnings("UnstableApiUsage")
public class ClientGameTest implements FabricClientGameTest {
	/** Fixed test layout in the flat test world (surface block at y = -61). */
	static final BlockPos PLAYER = new BlockPos(0, -60, 0);
	/** Center of the geode fixture's stone cube; the cube spans chunks x -1..1, z 0..2. */
	static final BlockPos GEODE_CENTER = new BlockPos(0, -41, 30);
	static final List<String> NAVIGATION = List.of("Home", "Combat", "Movement", "Visuals", "Inventory", "World", "Automation", "Profiles",
			"Settings");

	@Override
	public void runTest(ClientGameTestContext context) {
		TestReport report = new TestReport(context);
		UiDriver ui = new UiDriver(context);
		if ("1".equals(System.getenv("FEMBOY_BENCHMARK"))) {
			// scripts/run-benchmark.sh: only the performance measurement, no functional checks.
			context.runOnClient(client -> FemboyClient.get().options().animations.set(false));
			BenchmarkTest.run(context, report);
			report.finish();
			return;
		}

		report.check("Femboy Client entrypoint ran", context.computeOnClient(client -> FemboyClient.get() != null && FemboyClient.get().modules() != null));
		int moduleCount = context.computeOnClient(client -> FemboyClient.get().modules().all().size());
		report.check("All 77 modules registered", moduleCount == 77, String.valueOf(moduleCount));
		FoundationTest.firstLaunch(context, report);
		// Deterministic screenshots: no easing between frames.
		context.runOnClient(client -> FemboyClient.get().options().animations.set(false));

		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			singleplayer.getClientWorld().waitForChunksRender();
			// Creative mode: test travel (Nether, teleports into terrain) must not kill the player.
			singleplayer.getServer().runCommand("gamemode creative @a");
			singleplayer.getServer().runCommand("tp @a " + PLAYER.getX() + ".5 " + PLAYER.getY() + " " + PLAYER.getZ() + ".5 0 15");
			context.waitTicks(5);

			navigationAndScreenshots(context, ui, report);
			moduleBrowser(context, ui, report);
			entityEsp(context, ui, singleplayer, report);
			storageEsp(context, singleplayer, report);
			freeCamera(context, report);
			geodeAnalyzer(context, ui, singleplayer, report);
			playerFinder(context, ui, report);
			dimensionChange(context, singleplayer, report);
			configAndProfiles(context, report);
			controlInteractions(context, ui, report);
			FoundationTest.emergencyOff(context, report);
			FoundationTest.holdKeys(context, report);
			FoundationTest.automaticProfiles(context, singleplayer, ui, report);
			InfoModulesTest.presets(context, report);
			InfoModulesTest.combat(context, singleplayer, report);
			InfoModulesTest.movement(context, singleplayer, report);
			InfoModulesTest.inventory(context, singleplayer, report);
			InfoModulesTest.visuals(context, singleplayer, report);
			InfoModulesTest.explosions(context, singleplayer, report);
			AutomationTest.run(context, singleplayer, report);
			SpecModulesTest.run(context, singleplayer, ui, report);
			ExtraModulesTest.run(context, singleplayer, report);
			AdvancedFeaturesTest.pages(context, ui, report);
			AdvancedFeaturesTest.espOverhaul(context, singleplayer, report);
			AdvancedFeaturesTest.trackers(context, singleplayer, report);
			AdvancedFeaturesTest.fullbright(context, singleplayer, report);
			AdvancedFeaturesTest.freeCamera(context, ui, singleplayer, report);
			AdvancedFeaturesTest.javaRunner(context, ui, report);
			OverhaulTest.run(context, singleplayer, ui, report);
			ProductTest.run(context, singleplayer, ui, report);
			UxTest.run(context, singleplayer, ui, report);

			int errors = context.computeOnClient(client -> FemboyClient.get().diagnostics().errorCount());
			report.check("No module errors were logged during the run", errors == 0, errors == 0 ? ""
					: errors + " error(s): " + context.computeOnClient(client -> FemboyClient.get().diagnostics().snapshot().toString()));
		}

		NaturalWorldTest.run(context, report);
		MultiplayerTest.run(context, ui, report);
		inspectLog(report);
		report.finish();
	}

	// ----- helpers ---------------------------------------------------------------------------

	static FemboyClient femboy() {
		return FemboyClient.get();
	}

	static <M extends Module> void enable(ClientGameTestContext context, Class<M> type, boolean on) {
		context.runOnClient(client -> femboy().modules().setEnabled(femboy().modules().get(type), on));
	}

	static void openMenu(ClientGameTestContext context) {
		context.getInput().pressKey(GLFW.GLFW_KEY_RIGHT_SHIFT);
		context.waitForScreen(ClientScreen.class);
	}

	static void closeMenu(ClientGameTestContext context, UiDriver ui) {
		ui.key(GLFW.GLFW_KEY_ESCAPE);
		context.waitForScreen(null);
	}

	/** Shows the sidebar page with this title. */
	static void showPage(ClientGameTestContext context, String title) {
		context.runOnClient(client -> {
			ClientScreen screen = (ClientScreen) client.screen;
			screen.showPage(screen.pageIndex(page -> page.title().equals(title)));
		});
		context.waitTicks(3);
	}

	/** Opens a module on its category page, the same way clicking it on Home does. */
	static void openModule(ClientGameTestContext context, Class<? extends Module> type) {
		context.runOnClient(client -> ((ClientScreen) client.screen).openModule(femboy().modules().get(type)));
		context.waitTicks(3);
	}

	static String currentTitle(ClientGameTestContext context) {
		return context.computeOnClient(client -> ((ClientScreen) client.screen).currentPage().title());
	}

	/** Turns the analyzer off and on (clearing its cache) and waits until everything loaded is read and analyzed. */
	static List<GeodeObservation> analyzeFresh(ClientGameTestContext context) {
		enable(context, GeodeAnalyzerModule.class, false);
		enable(context, GeodeAnalyzerModule.class, true);
		context.waitTicks(2);
		awaitAnalysis(context);
		return context.computeOnClient(client -> List.copyOf(femboy().modules().get(GeodeAnalyzerModule.class).allGeodes()));
	}

	static void awaitAnalysis(ClientGameTestContext context) {
		context.waitFor(client -> {
			var scanner = femboy().modules().get(GeodeAnalyzerModule.class).scanner();
			return scanner.loadedColumns() > 0 && scanner.upToDate() && scanner.resultsAt() > 0;
		}, 1200);
	}

	static StageCounts fixtureCounts(ClientGameTestContext context) {
		return context.computeOnClient(client -> {
			GeodeObservation g = fixtureGeode(femboy().modules().get(GeodeAnalyzerModule.class).allGeodes());
			return g == null ? null : g.counts();
		});
	}

	/** Waits up to {@code ticks} for a condition; returns whether it became true (never throws on timeout). */
	static boolean waitUntil(ClientGameTestContext context, java.util.function.BooleanSupplier condition, int ticks) {
		for (int i = 0; i < ticks; i++) {
			if (condition.getAsBoolean()) {
				return true;
			}

			context.waitTick();
		}

		return condition.getAsBoolean();
	}

	/** Bounds of the fixture geode according to the server census. */
	static net.minecraft.world.level.levelgen.structure.BoundingBox fixtureBounds;

	static boolean overlapsFixture(GeodeObservation g) {
		var b = fixtureBounds;
		return b != null && g.minX() <= b.maxX() && b.minX() <= g.maxX() && g.minY() <= b.maxY() && b.minY() <= g.maxY()
				&& g.minZ() <= b.maxZ() && b.minZ() <= g.maxZ();
	}

	static GeodeObservation fixtureGeode(List<GeodeObservation> geodes) {
		return geodes.stream().filter(ClientGameTest::overlapsFixture).findFirst().orElse(null);
	}

	static GeodeObservation geodeAt(List<GeodeObservation> geodes, BlockPos inside) {
		return geodes.stream().filter(g -> g.minX() <= inside.getX() && inside.getX() <= g.maxX() && g.minY() <= inside.getY()
				&& inside.getY() <= g.maxY() && g.minZ() <= inside.getZ() && inside.getZ() <= g.maxZ()).findFirst().orElse(null);
	}

	// ----- navigation ------------------------------------------------------------------------

	/** Opens the ClickGUI with the real key binding, checks the navigation and screenshots every page at two sizes. */
	private static void navigationAndScreenshots(ClientGameTestContext context, UiDriver ui, TestReport report) {
		openMenu(context);
		report.check("Right Shift opens the Femboy Client screen", true);

		List<String> titles = context.computeOnClient(client -> {
			ClientScreen screen = (ClientScreen) client.screen;
			List<String> list = new java.util.ArrayList<>();
			for (int i = 0; i < screen.pageCount(); i++) {
				screen.showPage(i);
				list.add(screen.currentPage().navLabel());
			}

			screen.showPage(0);
			return list;
		});
		report.check("Sidebar is exactly " + String.join(", ", NAVIGATION), titles.equals(NAVIGATION), titles.toString());
		report.check("No development pages (Scenarios, Analysis, Results, Dashboard) remain",
				titles.stream().noneMatch(t -> Set.of("Scenarios", "Analysis", "Results", "Dashboard", "Test Results", "World Analysis").contains(t)));

		for (int[] size : new int[][] {{854, 480}, {1280, 720}}) {
			context.getInput().resizeWindow(size[0], size[1]);
			context.waitTicks(2);
			for (int page = 0; page < titles.size(); page++) {
				int index = page;
				context.runOnClient(client -> ((ClientScreen) client.screen).showPage(index));
				context.waitTicks(3);
				context.takeScreenshot("gui-" + size[0] + "x" + size[1] + "-" + index + "-" + titles.get(index).toLowerCase());
			}
		}

		report.check("Every page rendered at 854x480 and 1280x720", true);
		context.runOnClient(client -> ((ClientScreen) client.screen).showPage(0));
		closeMenu(context, ui);
		report.check("Escape closes the Femboy Client screen", true);
	}

	/** Home and the search open modules on their section page; toggle, key bind and in-world key press. */
	private static void moduleBrowser(ClientGameTestContext context, UiDriver ui, TestReport report) {
		openMenu(context);
		showPage(context, "Home");
		context.takeScreenshot("home");
		boolean noWall = context.computeOnClient(client -> ((HomePage) ((ClientScreen) client.screen).currentPage()).rows().size() <= 16);
		report.check("Home shows an overview (favorites and recent modules), not every module", noWall);

		// Typing on Home starts the search.
		ui.type("geode");
		context.waitTicks(2);
		List<String> found = context.computeOnClient(client -> ((ClientScreen) client.screen).uiContext().overlay() instanceof CommandPalette palette
				? palette.entries().stream().map(entry -> entry.group() + ":" + entry.title()).toList() : List.of());
		report.check("Typing on Home searches; 'geode' finds the Geode Analyzer under Modules", found.contains("Modules:Geode Analyzer"), found.toString());
		context.takeScreenshot("search-geode");
		ui.key(GLFW.GLFW_KEY_ESCAPE);
		context.waitTicks(1);
		boolean menuStillOpen = context.computeOnClient(client -> client.screen instanceof ClientScreen screen && screen.uiContext().overlay() == null);
		report.check("Escape closes the search and keeps the menu open", menuStillOpen);

		// Enter on a module opens it (and does not switch it on).
		context.runOnClient(client -> ((ClientScreen) client.screen).openSearch("geode analyzer"));
		context.waitTicks(1);
		ui.key(GLFW.GLFW_KEY_ENTER);
		context.waitTicks(3);
		String page = currentTitle(context);
		String selected = context.computeOnClient(client -> ((ClientScreen) client.screen).currentPage() instanceof ModulesPage modules
				&& modules.selected() != null ? modules.selected().id() : "none");
		boolean stillOff = context.computeOnClient(client -> !femboy().modules().get("geode_analyzer").isEnabled());
		report.check("Enter on a search result opens the module on its section (World) without switching it on",
				page.equals("World") && selected.equals("geode_analyzer") && stillOff, page + " / " + selected + " / off " + stillOff);
		boolean keyField = context.computeOnClient(client -> !((ClientScreen) client.screen).currentPage().descendants(KeybindButton.class).isEmpty());
		report.check("The module panel shows its key field right away (no tabs)", keyField);
		context.takeScreenshot("world-geode-selected");

		// Tab on a module result switches it on and off.
		context.runOnClient(client -> ((ClientScreen) client.screen).openSearch("geode analyzer"));
		context.waitTicks(1);
		ui.key(GLFW.GLFW_KEY_TAB);
		boolean toggledOn = context.computeOnClient(client -> femboy().modules().get("geode_analyzer").isEnabled());
		context.runOnClient(client -> ((ClientScreen) client.screen).openSearch("geode analyzer"));
		context.waitTicks(1);
		ui.key(GLFW.GLFW_KEY_TAB);
		boolean toggledOff = context.computeOnClient(client -> !femboy().modules().get("geode_analyzer").isEnabled());
		report.check("Tab on a search result turns the module on, and again off", toggledOn && toggledOff);

		showPage(context, "Visuals");
		List<String> visuals = context.computeOnClient(client -> ((ClientScreen) client.screen).currentPage().descendants(ModuleRow.class)
				.stream().map(r -> r.module().id()).toList());
		boolean allVisuals = context.computeOnClient(client -> ((ClientScreen) client.screen).currentPage().descendants(ModuleRow.class)
				.stream().allMatch(r -> r.module().category() == io.github.theamethyx.femboyclient.core.module.ModuleCategory.VISUALS
						|| r.module().category() == io.github.theamethyx.femboyclient.core.module.ModuleCategory.HUD));
		report.check("The Visuals section lists its own modules (visuals, HUD and utilities) and no others", visuals.contains("chunk_borders") && allVisuals
				&& !visuals.contains("geode_analyzer"), visuals.toString());

		ModuleRow chunkRow = ui.find(ModuleRow.class, r -> r.module().id().equals("chunk_borders"));
		ui.click(chunkRow.toggle());
		report.check("Clicking the switch enables Chunk Borders",
				context.computeOnClient(client -> femboy().modules().get("chunk_borders").isEnabled()));
		KeybindButton keyButton = ui.find(KeybindButton.class, button -> true);
		ui.click(keyButton);
		ui.key(GLFW.GLFW_KEY_K);
		int key = context.computeOnClient(client -> femboy().modules().get("chunk_borders").key());
		report.check("Key capture binds K to Chunk Borders", key == GLFW.GLFW_KEY_K, "key=" + key);
		context.takeScreenshot("visuals-chunk-borders-selected");

		closeMenu(context, ui);

		ui.key(GLFW.GLFW_KEY_K);
		boolean afterFirst = context.computeOnClient(client -> femboy().modules().get("chunk_borders").isEnabled());
		ui.key(GLFW.GLFW_KEY_K);
		boolean afterSecond = context.computeOnClient(client -> femboy().modules().get("chunk_borders").isEnabled());
		report.check("Pressing K in the world toggles Chunk Borders off and on (keyboard mixin)", !afterFirst && afterSecond,
				"after first press " + afterFirst + ", after second " + afterSecond);
		context.waitTicks(3);
		context.takeScreenshot("world-chunk-borders");
		enable(context, ChunkBordersModule.class, false);
	}

	// ----- Entity ESP ------------------------------------------------------------------------

	private static Set<String> trackedTypes(ClientGameTestContext context) {
		return context.computeOnClient(client -> {
			Set<String> types = new java.util.TreeSet<>();
			for (Entity e : femboy().modules().get(EntityEspModule.class).tracked()) {
				types.add(BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getPath());
			}

			return types;
		});
	}

	private static void entityEsp(ClientGameTestContext context, UiDriver ui, TestSingleplayerContext singleplayer, TestReport report) {
		var server = singleplayer.getServer();
		server.runCommand("summon zombie 3.5 -60 5.5 {NoAI:1b,PersistenceRequired:1b}");
		server.runCommand("summon pig -4.5 -60 8.5 {NoAI:1b}");
		server.runCommand("summon villager 0.5 -60 10.5 {NoAI:1b}");
		server.runCommand("summon wolf 2.5 -60 12.5 {NoAI:1b}");
		server.runCommand("summon husk -2.5 -60 12.5 {NoAI:1b,PersistenceRequired:1b}");
		server.runCommand("effect give @e[type=minecraft:husk] minecraft:invisibility 100000 0 true");
		server.runCommand("summon skeleton 0.5 -60 80.5 {NoAI:1b,PersistenceRequired:1b}");
		// Tame the wolf with the game's own method so it counts as a pet.
		server.runOnServer(s -> {
			var player = s.getPlayerList().getPlayers().getFirst();
			for (Wolf wolf : s.overworld().getEntitiesOfClass(Wolf.class, new AABB(-20, -64, -20, 20, -50, 30))) {
				// Same as tame(), minus the advancement trigger (its toast would cover the screenshots).
				wolf.setTame(true, true);
				wolf.setOwner(player);
			}
		});
		server.runCommand("tp @a 0.5 -60 0.5 0 15");
		context.waitTicks(20);

		enable(context, EntityEspModule.class, true);
		context.waitTicks(3);
		Set<String> defaults = trackedTypes(context);
		report.check("Entity ESP defaults: hostile mobs highlighted, passive mobs and villagers not", defaults.contains("zombie")
				&& !defaults.contains("pig") && !defaults.contains("villager") && !defaults.contains("wolf"), defaults.toString());
		report.check("Entities invisible to the player are skipped by default", !defaults.contains("husk"), defaults.toString());
		report.check("Entities beyond the maximum distance (64) are skipped", !defaults.contains("skeleton"), defaults.toString());

		// Choose pigs through the GUI: expand the type list, filter for "pig" and press All.
		openMenu(context);
		openModule(context, EntityEspModule.class);
		IdSetRow types = ui.find(IdSetRow.class, row -> row.setting().id().equals("types"));
		ui.clickVirtual(types.x() + 20, types.y() + 8);
		context.waitTicks(2);
		report.check("Clicking the type list header expands it", context.computeOnClient(client -> types.expanded()));
		context.takeScreenshot("visuals-entity-esp-types");
		TextField filter = ui.find(TextField.class, field -> types.descendants(TextField.class).contains(field));
		ui.click(filter);
		ui.type("pig");
		ui.key(GLFW.GLFW_KEY_ESCAPE);
		context.waitTicks(2);
		context.takeScreenshot("visuals-entity-esp-filter-pig");
		ui.click(ui.find(Button.class, b -> b.label().equals("All") && types.descendants(Button.class).contains(b)));
		boolean pigSelected = context.computeOnClient(client -> ((IdSetSetting) femboy().modules().get(EntityEspModule.class).settings().get("types"))
				.contains("minecraft:pig"));
		report.check("Filtering for 'pig' and pressing All selects minecraft:pig (and keeps the stable id)", pigSelected);
		boolean zombieKept = context.computeOnClient(client -> ((IdSetSetting) femboy().modules().get(EntityEspModule.class).settings().get("types"))
				.contains("minecraft:zombie"));
		report.check("'All' only affects the filtered entries", zombieKept);
		ui.clickVirtual(types.x() + 20, types.y() + 8); // collapse again
		closeMenu(context, ui);
		context.waitTicks(3);
		report.check("Selected pigs are now highlighted", trackedTypes(context).contains("pig"), trackedTypes(context).toString());

		context.runOnClient(client -> ((io.github.theamethyx.femboyclient.core.setting.EnumSetting<EntityEspModule.PetMode>) femboy().modules()
				.get(EntityEspModule.class).settings().get("pets")).set(EntityEspModule.PetMode.ALWAYS));
		context.waitTicks(3);
		report.check("Pets: 'Always show pets' highlights the tamed wolf although wolves are not selected", trackedTypes(context).contains("wolf"),
				trackedTypes(context).toString());
		context.takeScreenshot("world-entity-esp");

		context.runOnClient(client -> ((NumberSetting) femboy().modules().get(EntityEspModule.class).settings().get("max_distance")).set(8.0));
		context.waitTicks(3);
		Set<String> near = trackedTypes(context);
		report.check("Lowering the maximum distance to 8 blocks drops the farther entities", !near.contains("wolf") && near.contains("zombie"), near.toString());
		enable(context, EntityEspModule.class, false);
		report.check("Disabling Entity ESP clears its highlights", context.computeOnClient(client ->
				femboy().modules().get(EntityEspModule.class).tracked().isEmpty()));
		server.runCommand("kill @e[type=!minecraft:player]");
		context.waitTicks(2);
		server.runCommand("kill @e[type=minecraft:item]");
	}

	// ----- Storage ESP -----------------------------------------------------------------------

	private static Set<String> highlightedBlocks(ClientGameTestContext context) {
		return context.computeOnClient(client -> {
			Set<String> blocks = new java.util.TreeSet<>();
			for (BlockPos pos : femboy().modules().get(StorageEspModule.class).highlighted()) {
				blocks.add(BuiltInRegistries.BLOCK.getKey(client.level.getBlockState(pos).getBlock()).getPath());
			}

			return blocks;
		});
	}

	private static void storageEsp(ClientGameTestContext context, TestSingleplayerContext singleplayer, TestReport report) {
		var server = singleplayer.getServer();
		String[] blocks = {"chest", "barrel", "furnace", "white_shulker_box", "copper_chest", "trapped_chest", "hopper"};
		for (int i = 0; i < blocks.length; i++) {
			server.runCommand("setblock " + (4 + i * 2) + " -60 -4 minecraft:" + blocks[i]);
		}

		server.runCommand("tp @a -2.5 -60 4.5 235 20"); // face the row of storage blocks (east-northeast)
		context.waitTicks(30);
		enable(context, StorageEspModule.class, true);
		context.waitTicks(25); // first scan plus the 1 s interval
		Set<String> defaults = highlightedBlocks(context);
		report.check("Storage ESP defaults highlight chests, copper chests, trapped chests, barrels and shulker boxes",
				defaults.containsAll(Set.of("chest", "copper_chest", "trapped_chest", "barrel", "white_shulker_box")), defaults.toString());
		report.check("Furnaces and hoppers are not highlighted until selected", !defaults.contains("furnace") && !defaults.contains("hopper"), defaults.toString());
		context.takeScreenshot("world-storage-esp");

		context.runOnClient(client -> ((IdSetSetting) femboy().modules().get(StorageEspModule.class).settings().get("blocks"))
				.setAll(List.of("minecraft:furnace"), true));
		context.waitTicks(25);
		report.check("Selecting minecraft:furnace adds the furnace", highlightedBlocks(context).contains("furnace"), highlightedBlocks(context).toString());

		server.runCommand("setblock 4 -60 -4 minecraft:air");
		context.waitTicks(25);
		Set<String> after = highlightedBlocks(context);
		report.check("A removed chest disappears at the next scan", !after.contains("chest"), after.toString());
		server.runCommand("tp @a 0.5 -60 0.5 0 15");
		context.waitTicks(10); // let the teleport reach the client before the next test reads positions
		enable(context, StorageEspModule.class, false);
		report.check("Disabling Storage ESP clears its highlights",
				context.computeOnClient(client -> femboy().modules().get(StorageEspModule.class).highlighted().isEmpty()));
	}

	// ----- Free camera -----------------------------------------------------------------------

	/** The camera must move while the player stays put, and snap back afterwards. */
	private static void freeCamera(ClientGameTestContext context, TestReport report) {
		Vec3 playerBefore = context.computeOnClient(client -> client.player.position());
		enable(context, FreeCameraModule.class, true);
		context.waitTicks(2);
		Vec3 cameraStart = context.computeOnClient(client -> femboy().modules().get(FreeCameraModule.class).cameraPosition());
		context.getInput().holdKeyFor(options -> options.keyUp, 15);
		context.waitTicks(2);
		Vec3 cameraEnd = context.computeOnClient(client -> femboy().modules().get(FreeCameraModule.class).cameraPosition());
		Vec3 playerAfter = context.computeOnClient(client -> client.player.position());
		context.takeScreenshot("world-free-camera");

		report.check("Free camera moves forward with the movement key", cameraStart != null && cameraEnd != null && cameraEnd.distanceTo(cameraStart) > 5,
				"moved " + (cameraStart == null || cameraEnd == null ? "n/a" : String.format("%.2f", cameraEnd.distanceTo(cameraStart))));
		report.check("The player does not move while the free camera is active", playerAfter.distanceTo(playerBefore) < 0.1,
				String.format("player moved %.3f", playerAfter.distanceTo(playerBefore)));
		enable(context, FreeCameraModule.class, false);
		context.waitTicks(2);
		report.check("Disabling the free camera returns the view to the player", context.computeOnClient(client -> client.getCameraEntity() == client.player));
	}

	// ----- Geode Analyzer --------------------------------------------------------------------

	/** The analyzer must match the server's exact census, flag partial views, update itself, and pause. */
	private static void geodeAnalyzer(ClientGameTestContext context, UiDriver ui, TestSingleplayerContext singleplayer, TestReport report) {
		var server = singleplayer.getServer();
		// Only the growth the test applies may happen: natural random ticks would make the census stale.
		server.runCommand("gamerule random_tick_speed 0");
		server.runCommand("tp @a 0.5 -60 0.5 0 0");
		context.waitTicks(5);
		GeodeFixtureBuilder.Result built = server.computeOnServer(s -> GeodeFixtureBuilder.build(s.overworld(), GEODE_CENTER, 1001, 10));
		report.check("Geode fixture built with the vanilla feature", built.placed() && built.census().geodeBounds() != null);
		fixtureBounds = built.census().geodeBounds();
		StageCounts truth = built.census().counts();
		report.note("Ground truth (server census) after 10 random ticks per budding block: " + truth + ", budding " + built.census().buddingBlocks());
		context.waitTicks(40);

		context.runOnClient(client -> {
			var analyzer = femboy().modules().get(GeodeAnalyzerModule.class);
			((NumberSetting) analyzer.settings().get("radius")).set(1.0);
			((NumberSetting) analyzer.settings().get("scan_interval")).set(2.0);
		});
		List<GeodeObservation> partial = analyzeFresh(context);
		GeodeObservation partialGeode = fixtureGeode(partial);
		report.check("With a 1-chunk radius the fixture geode is reported as incomplete, not complete or absent",
				partialGeode != null && !partialGeode.complete() && partialGeode.counts().total() <= truth.total(),
				partialGeode == null ? "not found among " + partial.size() : partialGeode.counts() + " complete=" + partialGeode.complete());

		context.runOnClient(client -> ((NumberSetting) femboy().modules().get(GeodeAnalyzerModule.class).settings().get("radius")).set(4.0));
		context.waitTicks(2);
		awaitAnalysis(context);
		List<GeodeObservation> full = context.computeOnClient(client -> List.copyOf(femboy().modules().get(GeodeAnalyzerModule.class).allGeodes()));
		GeodeObservation geode = fixtureGeode(full);
		report.check("Raising the radius to 4 picks the geode up automatically and it is complete", geode != null && geode.complete(),
				geode == null ? "not found" : geode.completenessNote());
		report.check("Analyzer stage counts equal the server ground truth exactly", geode != null && geode.counts().equals(truth),
				"analyzer " + (geode == null ? "n/a" : geode.counts()) + " vs ground truth " + truth);
		report.check("Budding block count equals ground truth", geode != null && geode.buddingBlocks() == built.census().buddingBlocks());
		report.check("The fixture geode is counted once (no double counting)",
				full.stream().filter(ClientGameTest::overlapsFixture).count() == 1);

		// Automatic operation: let the geode grow on the server; the analyzer must follow without any button press.
		var region = built.cleared();
		server.runOnServer(s -> GeodeFixtureBuilder.applyRandomTicks(s.overworld(), region, 20, 77L));
		StageCounts grown = server.computeOnServer(s -> GeodeCensus.of(s.overworld(), region).counts());
		report.note("Ground truth after 20 more random ticks: " + grown);
		boolean followed = waitUntil(context, () -> grown.equals(fixtureCounts(context)), 600);
		report.check("After growth the analyzer updates by itself (scan interval 2 s) and matches the new census", followed,
				"analyzer " + fixtureCounts(context) + " vs " + grown);

		// Pause: further growth must not be picked up until resumed.
		context.runOnClient(client -> femboy().modules().get(GeodeAnalyzerModule.class).setPaused(true));
		server.runOnServer(s -> GeodeFixtureBuilder.applyRandomTicks(s.overworld(), region, 200, 78L));
		StageCounts grownAgain = server.computeOnServer(s -> GeodeCensus.of(s.overworld(), region).counts());
		context.waitTicks(100);
		StageCounts whilePaused = fixtureCounts(context);
		report.check("While paused nothing is re-read (counts stay at the previous census)", grown.equals(whilePaused) && !grownAgain.equals(grown),
				"paused " + whilePaused + ", server now " + grownAgain);
		context.runOnClient(client -> femboy().modules().get(GeodeAnalyzerModule.class).setPaused(false));
		boolean caughtUp = waitUntil(context, () -> grownAgain.equals(fixtureCounts(context)), 600);
		report.check("After resuming the analyzer catches up with the server census", caughtUp, "analyzer " + fixtureCounts(context) + " vs " + grownAgain);

		// The panel inside the module: status, cards, details on click.
		openMenu(context);
		openModule(context, GeodeAnalyzerModule.class);
		GeodePanel panel = ui.find(GeodePanel.class, p -> true);
		report.check("The Geode Analyzer's settings panel contains its live geode panel", panel != null);
		context.takeScreenshot("world-geode-analyzer-panel");
		Widget card = ui.find(Widget.class, w -> w.getClass().getSimpleName().equals("GeodeCard"));
		report.check("At least one geode card is listed", card != null);
		if (card != null) {
			ui.click(card);
			context.waitTicks(3);
			boolean details = ui.find(Widget.class, w -> w.getClass().getSimpleName().equals("StageTable")) != null;
			report.check("Clicking a geode card shows its exact counts and details", details);
			context.takeScreenshot("world-geode-analyzer-details");
		}

		closeMenu(context, ui);
		context.runOnClient(client -> ((io.github.theamethyx.femboyclient.core.setting.BooleanSetting) femboy().modules()
				.get(GeodeAnalyzerModule.class).settings().get("stage_overlay")).set(true));
		server.runCommand("tp @a 0.5 -38 12.5 0 20");
		context.waitTicks(10);
		context.takeScreenshot("world-geode-stage-overlay");
		server.runCommand("tp @a 0.5 -60 0.5 0 0");
		context.waitTicks(5);
	}

	// ----- Player Finder ---------------------------------------------------------------------

	private static void playerFinder(ClientGameTestContext context, UiDriver ui, TestReport report) {
		enable(context, PlayerFinderModule.class, true);
		enable(context, WaypointsModule.class, true);
		String added = context.computeOnClient(client -> femboy().modules().get(WaypointsModule.class).addAtPlayer("Spawn"));
		report.check("A waypoint can be added at the player position", added == null, String.valueOf(added));

		int others = context.computeOnClient(client -> femboy().modules().get(PlayerFinderModule.class).entries().size());
		report.check("In singleplayer the list has no other players (yourself excluded by default)", others == 0, others + " entries");
		context.runOnClient(client -> ((io.github.theamethyx.femboyclient.core.setting.BooleanSetting) femboy().modules()
				.get(PlayerFinderModule.class).settings().get("include_self")).set(true));
		var self = context.computeOnClient(client -> femboy().modules().get(PlayerFinderModule.class).entries());
		report.check("With 'Include yourself' your own entry is listed with a known position", self.size() == 1
				&& self.getFirst().knowledge() == PlayerFinderModule.Knowledge.IN_RANGE && self.getFirst().position() != null, self.toString());

		openMenu(context);
		openModule(context, PlayerFinderModule.class);
		Widget waypointRow = ui.find(Widget.class, w -> w.getClass().getSimpleName().equals("WaypointRow"));
		report.check("The waypoint of this world is offered as a navigation target", waypointRow != null);
		if (waypointRow != null) {
			ui.click(waypointRow);
		}

		var target = context.computeOnClient(client -> femboy().modules().get(PlayerFinderModule.class).target());
		report.check("Clicking the waypoint starts navigation to it", target != null && target.name().equals("Spawn") && target.position() != null,
				String.valueOf(target));
		context.takeScreenshot("player-finder-panel");
		closeMenu(context, ui);
		context.waitTicks(3);
		context.takeScreenshot("world-navigation-hud");
		context.runOnClient(client -> femboy().modules().get(PlayerFinderModule.class).stopNavigation());
		report.check("Stopping navigation clears the target", context.computeOnClient(client -> femboy().modules().get(PlayerFinderModule.class).target() == null));
	}

	// ----- world changes ---------------------------------------------------------------------

	/** Travelling to the Nether and back must not break modules or show stale results. */
	private static void dimensionChange(ClientGameTestContext context, TestSingleplayerContext singleplayer, TestReport report) {
		int resultsBefore = context.computeOnClient(client -> femboy().modules().get(GeodeAnalyzerModule.class).allGeodes().size());
		// Paused on purpose: results from the old world must be dropped even while the analyzer is paused.
		context.runOnClient(client -> femboy().modules().get(GeodeAnalyzerModule.class).setPaused(true));
		enable(context, ChunkBordersModule.class, true);
		enable(context, FreeCameraModule.class, true);
		enable(context, TrackersModule.class, true);
		enable(context, io.github.theamethyx.femboyclient.client.modules.FullbrightModule.class, true);
		singleplayer.getServer().runCommand("execute in minecraft:the_nether run tp @a 0.5 128 0.5");
		context.waitFor(client -> client.level != null && client.level.dimension().identifier().getPath().equals("the_nether"), 400);
		context.waitForScreen(null); // the vanilla level-loading screen closes first
		context.waitTicks(40);
		int resultsAfter = context.computeOnClient(client -> (int) femboy().modules().get(GeodeAnalyzerModule.class).allGeodes().stream()
				.filter(g -> g.dimension().endsWith("overworld")).count());
		report.check("Overworld geode results are dropped in the Nether, even while the analyzer is paused", resultsBefore > 0 && resultsAfter == 0,
				resultsBefore + " before, " + resultsAfter + " overworld results after");
		context.runOnClient(client -> femboy().modules().get(GeodeAnalyzerModule.class).setPaused(false));
		report.check("Modules keep running after a dimension change", context.computeOnClient(client ->
				femboy().modules().get(WaypointsModule.class).isRunning() && femboy().modules().get(ChunkBordersModule.class).isRunning()
						&& femboy().modules().get(GeodeAnalyzerModule.class).isRunning()));
		report.check("Overworld waypoints are not shown in the Nether",
				context.computeOnClient(client -> femboy().modules().get(WaypointsModule.class).visibleHere().isEmpty()));
		report.check("Free camera re-attaches in the new dimension", context.computeOnClient(client -> {
			FreeCameraModule camera = femboy().modules().get(FreeCameraModule.class);
			return camera.isActive() && client.getCameraEntity() != client.player && client.getCameraEntity().level() == client.level;
		}));
		report.check("Trackers show no overworld waypoints in the Nether", context.computeOnClient(client ->
				femboy().modules().get(TrackersModule.class).lastIndicators().stream().noneMatch(i -> i.source() == TrackersModule.Source.WAYPOINT)));
		report.check("Fullbright keeps running in the Nether", context.computeOnClient(client ->
				femboy().modules().get(io.github.theamethyx.femboyclient.client.modules.FullbrightModule.class).isRunning()));
		enable(context, FreeCameraModule.class, false);
		enable(context, TrackersModule.class, false);
		enable(context, io.github.theamethyx.femboyclient.client.modules.FullbrightModule.class, false);
		context.takeScreenshot("world-nether-after-transition");
		singleplayer.getServer().runCommand("execute in minecraft:overworld run tp @a 0.5 -60 0.5 0 0");
		context.waitFor(client -> client.level != null && client.level.dimension().identifier().getPath().equals("overworld"), 400);
		context.waitForScreen(null);
		context.waitTicks(20);
		report.check("Overworld waypoints reappear after returning",
				!context.computeOnClient(client -> femboy().modules().get(WaypointsModule.class).visibleHere().isEmpty()));
		enable(context, ChunkBordersModule.class, false);
		enable(context, GeodeAnalyzerModule.class, false);
		int errors = context.computeOnClient(client -> femboy().diagnostics().errorCount());
		report.check("No module errors after the dimension changes", errors == 0, errors + " errors");
	}

	// ----- configuration ---------------------------------------------------------------------

	/** Settings of every type persist through save and reload; profiles keep independent state. */
	private static void configAndProfiles(ClientGameTestContext context, TestReport report) {
		String persisted = context.computeOnClient(client -> {
			FemboyClient femboy = femboy();
			var chunkRadius = (NumberSetting) femboy.modules().get(ChunkBordersModule.class).settings().get("radius");
			var types = (IdSetSetting) femboy.modules().get(EntityEspModule.class).settings().get("types");
			var color = (ColorSetting) femboy.modules().get(EntityEspModule.class).settings().get("color_players");
			chunkRadius.set(3.0);
			types.setAll(List.of("minecraft:cow"), true);
			color.set(0xFF4CC9C0);
			femboy.config().saveNow();
			chunkRadius.set(0.0);
			types.setAll(List.of("minecraft:cow"), false);
			color.reset();
			femboy.config().load();
			return chunkRadius.get() == 3.0 && types.contains("minecraft:cow") && color.get() == 0xFF4CC9C0 ? "ok"
					: "radius " + chunkRadius.get() + ", cow " + types.contains("minecraft:cow") + ", color " + ColorSetting.hex(color.get());
		});
		report.check("Number, id-list and color settings survive save and reload", persisted.equals("ok"), persisted);

		String persistedNew = context.computeOnClient(client -> {
			FemboyClient femboy = femboy();
			var key = (io.github.theamethyx.femboyclient.core.setting.KeySetting) femboy.modules().get(FreeCameraModule.class).settings().get("key_up");
			var javaPath = (io.github.theamethyx.femboyclient.core.setting.StringSetting) femboy.modules().get(JavaRunnerModule.class).settings().get("java_path");
			var maceFall = (NumberSetting) femboy.modules().get(AutoAttributeSwapModule.class).settings().get("mace_min_fall");
			var runner = femboy.modules().get(JavaRunnerModule.class);
			key.set(GLFW.GLFW_KEY_R);
			javaPath.set("/opt/jdk/bin/java");
			maceFall.set(3.0);
			runner.setSource("System.out.println(\"saved\");");
			femboy.config().saveNow();
			key.reset();
			javaPath.reset();
			maceFall.reset();
			runner.setSource("changed");
			femboy.config().load();
			String result = key.get() == GLFW.GLFW_KEY_R && javaPath.get().equals("/opt/jdk/bin/java") && maceFall.get() == 3.0
					&& runner.source().equals("System.out.println(\"saved\");") ? "ok"
					: "key " + key.get() + ", java " + javaPath.get() + ", mace " + maceFall.get() + ", source " + runner.source();
			key.reset();
			javaPath.reset();
			maceFall.reset();
			femboy.config().saveNow();
			return result;
		});
		report.check("Key, text and panel settings and the Java Runner program survive save and reload", persistedNew.equals("ok"), persistedNew);

		Path profileFile = context.computeOnClient(client -> femboy().config().root().resolve("profiles").resolve("default.json"));
		String json = readQuietly(profileFile);
		report.check("The profile file stores registry ids and hex colors", json.contains("\"minecraft:cow\"") && json.contains("#4CC9C0"), profileFile.toString());

		String result = context.computeOnClient(client -> {
			FemboyClient femboy = femboy();
			Module coords = femboy.modules().get("coordinates_hud");
			femboy.modules().setEnabled(coords, true);
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
			femboy.modules().setEnabled(coords, false);
			return defaultHasCoords && !gametestHasCoords ? "ok" : "default=" + defaultHasCoords + " gametest=" + gametestHasCoords;
		});
		report.check("Profiles keep independent module state", result.equals("ok"), result);
	}

	/** Slider, dropdown and color swatches through simulated mouse input; the Settings tabs. */
	private static void controlInteractions(ClientGameTestContext context, UiDriver ui, TestReport report) {
		openMenu(context);
		openModule(context, ChunkBordersModule.class);
		SettingRow radiusRow = ui.find(SettingRow.class, row -> row.setting().id().equals("radius"));
		ui.click(radiusRow.control());
		var slider = radiusRow.control();
		ui.clickVirtual(slider.x() + slider.width() - 1, slider.y() + slider.height() / 2.0);
		double radius = context.computeOnClient(client -> (Double) femboy().modules().get("chunk_borders").settings().get("radius").get());
		report.check("Clicking the right end of the 'Neighbor chunks' slider sets its maximum (4)", radius == 4.0, "value " + radius);

		// The reset button appears once the value differs from the default; clicking it restores the default.
		Button resetButton = context.computeOnClient(client -> radiusRow.descendants(Button.class).stream()
				.filter(b -> b.label().isEmpty()).findFirst().orElse(null));
		ui.click(resetButton);
		boolean restored = context.computeOnClient(client -> femboy().modules().get("chunk_borders").settings().get("radius").isDefault());
		report.check("A setting's reset button restores its default", restored);

		// Help mode (on by default) shows the explanation under the name; with it off, hovering shows it as a tooltip.
		String description = context.computeOnClient(client -> femboy().modules().get("chunk_borders").settings().get("radius").description());
		int helpHeight = radiusRow.height();
		context.runOnClient(client -> femboy().options().helpMode.set(false));
		context.waitTicks(3);
		SettingRow compactRow = ui.find(SettingRow.class, row -> row.setting().id().equals("radius"));
		int compactHeight = compactRow.height();
		ui.hoverVirtual(compactRow.x() + 20, compactRow.y() + 6);
		context.waitTicks(15);
		String tooltip = context.computeOnClient(client -> ((ClientScreen) client.screen).uiContext().shownTooltip());
		report.check("Help mode shows a setting's explanation under its name; off, the row is compact and hovering shows it",
				helpHeight > compactHeight && tooltip != null && tooltip.startsWith(description) && tooltip.contains("default"),
				helpHeight + " vs " + compactHeight + ", tooltip " + tooltip);
		context.takeScreenshot("visuals-tooltip");
		context.runOnClient(client -> femboy().options().helpMode.set(true));
		context.waitTicks(2);

		SettingRow colorRow = ui.find(SettingRow.class, row -> row.setting().id().equals("color"));
		ui.click(colorRow.control());
		boolean open = context.computeOnClient(client -> ((Dropdown<?>) colorRow.control()).isOpen(((ClientScreen) client.screen).uiContext()));
		report.check("Clicking a dropdown opens its option list", open);
		context.takeScreenshot("visuals-dropdown-open");
		// The list opens below the dropdown, or above it near the bottom of the screen: aim at the second option where it is.
		var options = ((Dropdown<?>) colorRow.control()).optionList();
		ui.clickVirtual(options.x() + 20, options.y() + 1 + 13 + 6);
		Object color = context.computeOnClient(client -> femboy().modules().get("chunk_borders").settings().get("color").get());
		report.check("Choosing 'Cyan' in the dropdown changes the setting", String.valueOf(color).equals("CYAN"), String.valueOf(color));

		openModule(context, EntityEspModule.class);
		context.runOnClient(client -> ((NumberSetting) femboy().modules().get(EntityEspModule.class).settings().get("max_labels")).set(5.0));
		Button moduleResetButton = ui.find(Button.class, b -> b.label().equals("Reset to defaults"));
		ui.click(moduleResetButton);
		boolean notYet = context.computeOnClient(client -> !femboy().modules().get(EntityEspModule.class).settings().allDefault());
		ui.click(moduleResetButton);
		report.check("Resetting a module asks for a second click first", notYet);
		boolean moduleReset = context.computeOnClient(client -> femboy().modules().get(EntityEspModule.class).settings().allDefault());
		report.check("The module Reset button restores all of a module's settings", moduleReset);
		ui.openSection("Colors");
		SettingRow hostile = ui.find(SettingRow.class, row -> row.setting().id().equals("color_hostile"));
		ui.click(hostile);
		ColorPicker picker = (ColorPicker) hostile.control();
		// Swatches are 10 px wide with 3 px gaps; the fourth preset is orange (#F29E4C).
		ui.clickVirtual(picker.x() + 3 * 13 + 5, picker.y() + 5);
		int chosen = context.computeOnClient(client -> (Integer) femboy().modules().get(EntityEspModule.class).settings().get("color_hostile").get());
		report.check("Clicking a color swatch sets that color", chosen == ColorSetting.PRESETS.get(3), ColorSetting.hex(chosen));
		context.takeScreenshot("visuals-entity-esp-colors");

		showPage(context, "Settings");
		context.runOnClient(client -> ((SettingsPage) ((ClientScreen) client.screen).currentPage()).showTab("Developer"));
		context.waitTicks(3);
		boolean diagnostics = context.computeOnClient(client -> ((SettingsPage) ((ClientScreen) client.screen).currentPage()).activeTab().title()
				.equals("Developer"));
		report.check("Settings has a Developer tab (health monitor, diagnostics and the log)", diagnostics);
		context.takeScreenshot("settings-diagnostics");
		context.runOnClient(client -> ((SettingsPage) ((ClientScreen) client.screen).currentPage()).showTab(0));
		closeMenu(context, ui);
		GuiState.settingsTab = 0;
	}

	// ----- log -------------------------------------------------------------------------------

	/**
	 * Scans the client log for problems attributable to the game session: errors on the render or
	 * server thread, and any stack frame inside the client's own code. Known noise of the offline test
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

	static String readQuietly(Path path) {
		try {
			return Files.readString(path);
		} catch (java.io.IOException e) {
			return "";
		}
	}
}
