package io.github.theamethyx.femboyclient.gametest;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.lwjgl.glfw.GLFW;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;

import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

import io.github.theamethyx.femboyclient.client.ClientEnvironment;
import io.github.theamethyx.femboyclient.client.ClientTicks;
import io.github.theamethyx.femboyclient.client.FemboyClient;
import io.github.theamethyx.femboyclient.client.GlobalOptions;
import io.github.theamethyx.femboyclient.client.Performance;
import io.github.theamethyx.femboyclient.client.gui.ClientScreen;
import io.github.theamethyx.femboyclient.client.gui.GuiState;
import io.github.theamethyx.femboyclient.client.gui.page.ModulesPage;
import io.github.theamethyx.femboyclient.client.gui.widget.Button;
import io.github.theamethyx.femboyclient.client.gui.widget.SettingRow;
import io.github.theamethyx.femboyclient.client.hud.Notifications;
import io.github.theamethyx.femboyclient.client.modules.AimAssistModule;
import io.github.theamethyx.femboyclient.client.modules.BlockEspModule;
import io.github.theamethyx.femboyclient.client.modules.ChunkBordersModule;
import io.github.theamethyx.femboyclient.client.modules.EntityEspModule;
import io.github.theamethyx.femboyclient.client.modules.HitboxesModule;
import io.github.theamethyx.femboyclient.client.modules.KillAuraModule;
import io.github.theamethyx.femboyclient.client.modules.StorageEspModule;
import io.github.theamethyx.femboyclient.client.modules.TrackersModule;
import io.github.theamethyx.femboyclient.client.modules.WaypointsModule;
import io.github.theamethyx.femboyclient.client.render.FrameProjection;
import io.github.theamethyx.femboyclient.client.render.TargetLines;
import io.github.theamethyx.femboyclient.client.world.EntityIndex;
import io.github.theamethyx.femboyclient.core.module.Module;
import io.github.theamethyx.femboyclient.core.setting.Setting;

/**
 * The 0.9.0 overhaul in the running client: Block ESP (follows block changes, target lines with every visibility mode,
 * singleplayer/LAN only), target lines of Entity and Storage ESP and Trackers, the exact projection, performance modes,
 * module conflicts, the shared entity index, the per-tick HUD, and the new menu controls.
 */
@SuppressWarnings({"UnstableApiUsage", "unchecked"})
final class OverhaulTest {
	private OverhaulTest() {
	}

	static FemboyClient femboy() {
		return FemboyClient.get();
	}

	private static void enable(ClientGameTestContext context, Class<? extends Module> type, boolean on) {
		context.runOnClient(client -> femboy().modules().setEnabled(femboy().modules().get(type), on));
	}

	private static <T> void set(ClientGameTestContext context, Class<? extends Module> type, String id, T value) {
		context.runOnClient(client -> {
			String problem = ((Setting<T>) femboy().modules().get(type).settings().get(id)).set(value);
			if (problem != null) {
				throw new IllegalStateException(id + ": " + problem);
			}
		});
	}

	private static void reset(ClientGameTestContext context, Class<? extends Module> type) {
		context.runOnClient(client -> femboy().modules().resetToDefaults(femboy().modules().get(type)));
	}

	private static void look(ClientGameTestContext context, float yaw, float pitch) {
		context.runOnClient(client -> {
			client.player.setYRot(yaw);
			client.player.setXRot(pitch);
			client.player.yRotO = yaw;
			client.player.xRotO = pitch;
		});
		context.waitTicks(2);
	}

	static void run(ClientGameTestContext context, TestSingleplayerContext singleplayer, UiDriver ui, TestReport report) {
		var server = singleplayer.getServer();
		server.runCommand("kill @e[type=!player]");
		server.runCommand("tp @a 0.5 -60 0.5 0 20");
		context.waitTicks(5);
		blockEsp(context, singleplayer, report);
		entityAndStorageLines(context, singleplayer, report);
		projection(context, report);
		modesAndConflicts(context, report);
		sharedWork(context, singleplayer, report);
		gui(context, ui, report);
	}

	private static void blockEsp(ClientGameTestContext context, TestSingleplayerContext singleplayer, TestReport report) {
		var server = singleplayer.getServer();
		// One ore standing on the grass (nothing between it and your eyes), one buried in the dirt.
		BlockPos exposed = new BlockPos(3, -60, 6);
		BlockPos buried = new BlockPos(-3, -62, 6);
		server.runCommand("setblock 3 -60 6 minecraft:diamond_ore");
		server.runCommand("setblock -3 -62 6 minecraft:diamond_ore");
		look(context, 0, 20);
		enable(context, BlockEspModule.class, true);
		context.waitTicks(20);

		List<BlockPos> shown = context.computeOnClient(client -> femboy().modules().get(BlockEspModule.class).shown().stream().map(s -> s.pos()).toList());
		report.check("Block ESP (formerly Ore ESP) highlights both placed diamond ores", shown.contains(exposed) && shown.contains(buried), shown.toString());
		int readsBefore = context.computeOnClient(client -> femboy().modules().get(BlockEspModule.class).scanner().chunkReads());
		int updatesBefore = context.computeOnClient(client -> femboy().modules().get(BlockEspModule.class).scanner().incrementalUpdates());

		server.runCommand("setblock 3 -60 6 minecraft:air");
		context.waitTicks(3);
		boolean gone = context.computeOnClient(client -> femboy().modules().get(BlockEspModule.class).shown().stream().noneMatch(s -> s.pos().equals(exposed)));
		server.runCommand("setblock 3 -60 6 minecraft:diamond_ore");
		context.waitTicks(3);
		boolean back = context.computeOnClient(client -> femboy().modules().get(BlockEspModule.class).shown().stream().anyMatch(s -> s.pos().equals(exposed)));
		int readsAfter = context.computeOnClient(client -> femboy().modules().get(BlockEspModule.class).scanner().chunkReads());
		int updatesAfter = context.computeOnClient(client -> femboy().modules().get(BlockEspModule.class).scanner().incrementalUpdates());
		report.check("Block ESP follows a broken and a placed ore within 3 ticks, without re-reading any chunk", gone && back && readsAfter == readsBefore
				&& updatesAfter >= updatesBefore + 2, "gone " + gone + ", back " + back + ", chunk reads " + readsBefore + " -> " + readsAfter
				+ ", single-block updates " + updatesBefore + " -> " + updatesAfter);

		set(context, BlockEspModule.class, "tracers", true);
		set(context, BlockEspModule.class, "tracer_visibility", TargetLines.Visibility.ALWAYS);
		context.waitTicks(3);
		int always = context.computeOnClient(client -> femboy().modules().get(BlockEspModule.class).lines().drawn());
		context.takeScreenshot("block-esp-target-lines");
		set(context, BlockEspModule.class, "tracer_visibility", TargetLines.Visibility.LINE_OF_SIGHT);
		context.waitTicks(12);
		int sight = context.computeOnClient(client -> femboy().modules().get(BlockEspModule.class).lines().drawn());
		set(context, BlockEspModule.class, "tracer_visibility", TargetLines.Visibility.ON_SCREEN);
		look(context, 180, 20);
		context.waitTicks(2);
		int behind = context.computeOnClient(client -> femboy().modules().get(BlockEspModule.class).lines().drawn());
		look(context, 0, 20);
		context.waitTicks(2);
		int inFront = context.computeOnClient(client -> femboy().modules().get(BlockEspModule.class).lines().drawn());
		report.check("Block ESP target lines: all targets 2, only visible 1 (the buried ore is hidden), only on screen 0 behind you and 2 in front",
				always == 2 && sight == 1 && behind == 0 && inFront == 2,
				String.format(Locale.ROOT, "always %d, line of sight %d, on screen behind %d / in front %d", always, sight, behind, inFront));

		context.runOnClient(client -> ClientEnvironment.simulatePublicServer = true);
		context.waitTicks(2);
		String reason = context.computeOnClient(client -> femboy().modules().availability(femboy().modules().get(BlockEspModule.class)).reason());
		boolean stopped = context.computeOnClient(client -> !femboy().modules().get(BlockEspModule.class).isRunning());
		context.runOnClient(client -> ClientEnvironment.simulatePublicServer = false);
		context.waitTicks(2);
		report.check("Block ESP reads every loaded block, so it stops outside singleplayer and your own network and says why",
				stopped && reason.equals(Module.TEST_ENVIRONMENT_ONLY), reason);

		reset(context, BlockEspModule.class);
		enable(context, BlockEspModule.class, false);
		server.runCommand("setblock 3 -60 6 minecraft:air");
		server.runCommand("setblock -3 -62 6 minecraft:dirt");
	}

	private static void entityAndStorageLines(ClientGameTestContext context, TestSingleplayerContext singleplayer, TestReport report) {
		var server = singleplayer.getServer();
		server.runCommand("summon minecraft:husk 0.5 -60 7.5 {NoAI:1b,Silent:1b,PersistenceRequired:1b}");
		server.runCommand("summon minecraft:item 2.5 -60 5.5 {Item:{id:\"minecraft:stone\",count:1},PickupDelay:32767}");
		look(context, 0, 15);
		context.runOnClient(client -> {
			EntityEspModule esp = femboy().modules().get(EntityEspModule.class);
			Set<String> types = new HashSet<>((Set<String>) esp.settings().get("types").get());
			types.add("minecraft:item");
			((Setting<Set<String>>) esp.settings().get("types")).set(types);
		});
		set(context, EntityEspModule.class, "tracers", true);
		set(context, EntityEspModule.class, "lines_items", false);
		enable(context, EntityEspModule.class, true);
		context.waitTicks(5);
		int tracked = context.computeOnClient(client -> femboy().modules().get(EntityEspModule.class).trackedTargets().size());
		int withoutItems = context.computeOnClient(client -> femboy().modules().get(EntityEspModule.class).lines().drawn());
		set(context, EntityEspModule.class, "lines_items", true);
		context.waitTicks(3);
		int withItems = context.computeOnClient(client -> femboy().modules().get(EntityEspModule.class).lines().drawn());
		context.takeScreenshot("entity-esp-target-lines");
		report.check("Entity ESP lines are set per group: the item gets a line only with 'Lines to items' on", tracked == 2 && withoutItems == 1 && withItems == 2,
				"tracked " + tracked + ", lines " + withoutItems + " then " + withItems);
		reset(context, EntityEspModule.class);

		server.runCommand("setblock -2 -60 5 minecraft:chest");
		enable(context, StorageEspModule.class, true);
		set(context, StorageEspModule.class, "tracers", true);
		context.waitTicks(25);
		boolean found = context.computeOnClient(client -> femboy().modules().get(StorageEspModule.class).highlighted().contains(new BlockPos(-2, -60, 5)));
		int storageLines = context.computeOnClient(client -> femboy().modules().get(StorageEspModule.class).lines().drawn());
		server.runCommand("setblock -2 -60 5 minecraft:air");
		context.waitTicks(4);
		boolean removed = context.computeOnClient(client -> !femboy().modules().get(StorageEspModule.class).highlighted().contains(new BlockPos(-2, -60, 5)));
		report.check("Storage ESP draws a line to the chest and drops it 4 ticks after it is broken (its scan interval is 20 ticks)",
				found && storageLines >= 1 && removed, "found " + found + ", lines " + storageLines + ", removed " + removed);
		reset(context, StorageEspModule.class);
		enable(context, StorageEspModule.class, false);

		context.runOnClient(client -> femboy().modules().get(WaypointsModule.class).addAtPlayer("Line test"));
		server.runCommand("tp @a 0.5 -60 20.5 180 10");
		context.waitTicks(3);
		enable(context, WaypointsModule.class, true);
		enable(context, TrackersModule.class, true);
		context.waitTicks(10);
		int trackerLines = context.computeOnClient(client -> femboy().modules().get(TrackersModule.class).lines().drawn());
		report.check("Trackers is not arrow-only any more: it draws target lines (here to a waypoint and the husk)", trackerLines >= 1,
				"lines " + trackerLines);
		enable(context, TrackersModule.class, false);
		enable(context, WaypointsModule.class, false);
		context.runOnClient(client -> {
			WaypointsModule waypoints = femboy().modules().get(WaypointsModule.class);
			waypoints.all().stream().filter(w -> w.name().equals("Line test")).forEach(waypoints::remove);
		});
		server.runCommand("kill @e[type=!player]");
		server.runCommand("tp @a 0.5 -60 0.5 0 20");
		context.waitTicks(5);
	}

	private static void projection(ClientGameTestContext context, TestReport report) {
		float[] center = context.computeOnClient(client -> {
			int w = client.getWindow().getGuiScaledWidth();
			int h = client.getWindow().getGuiScaledHeight();
			var origin = FrameProjection.crosshairOrigin(0.6);
			float[] out = new float[3];
			FrameProjection.project(origin.x, origin.y, origin.z, w, h, out);
			return new float[] {out[0], out[1], w / 2f, h / 2f, FrameProjection.valid() ? 1 : 0};
		});
		report.check("Target lines start exactly at the crosshair: their origin projects to the middle of the screen",
				center[4] == 1 && Math.abs(center[0] - center[2]) < 0.75 && Math.abs(center[1] - center[3]) < 0.75,
				String.format(Locale.ROOT, "projected %.2f, %.2f; middle %.1f, %.1f", center[0], center[1], center[2], center[3]));
	}

	private static void modesAndConflicts(ClientGameTestContext context, TestReport report) {
		context.runOnClient(client -> femboy().options().performanceMode.set(GlobalOptions.PerformanceMode.LOW_IMPACT));
		enable(context, KillAuraModule.class, true);
		context.waitTicks(2);
		boolean paused = context.computeOnClient(client -> femboy().modules().get(KillAuraModule.class).isEnabled()
				&& !femboy().modules().get(KillAuraModule.class).isRunning());
		String reason = context.computeOnClient(client -> femboy().modules().availability(femboy().modules().get(KillAuraModule.class)).reason());
		int lowInterval = context.computeOnClient(client -> Performance.interval(2));
		context.runOnClient(client -> femboy().options().performanceMode.set(GlobalOptions.PerformanceMode.PERFORMANCE));
		context.waitTicks(2);
		int fastInterval = context.computeOnClient(client -> Performance.interval(2));
		boolean animationsOff = context.computeOnClient(client -> !Performance.animations(true));
		context.runOnClient(client -> femboy().options().performanceMode.set(GlobalOptions.PerformanceMode.BALANCED));
		context.waitTicks(2);
		boolean resumed = context.computeOnClient(client -> femboy().modules().get(KillAuraModule.class).isRunning());
		report.check("Low impact pauses KillAura and says why; it resumes in Balanced; intervals 2 ticks -> 6 (low impact), 4 (performance)",
				paused && reason.startsWith("Paused in Low-Impact mode") && resumed && lowInterval == 6 && fastInterval == 4 && animationsOff,
				reason + "; intervals " + lowInterval + "/" + fastInterval);

		enable(context, AimAssistModule.class, true);
		context.waitTicks(1);
		boolean auraOff = context.computeOnClient(client -> !femboy().modules().get(KillAuraModule.class).isEnabled());
		String message = context.computeOnClient(client -> {
			List<Notifications.Toast> history = Notifications.history();
			return history.isEmpty() ? "" : history.getLast().title() + ": " + history.getLast().text();
		});
		report.check("Turning Aim Assist on turns KillAura off (both turn your view) and says so", auraOff && message.contains("KillAura turned off"), message);
		enable(context, AimAssistModule.class, false);
	}

	private static void sharedWork(ClientGameTestContext context, TestSingleplayerContext singleplayer, TestReport report) {
		singleplayer.getServer().runCommand("summon minecraft:husk 0.5 -60 6.5 {NoAI:1b,Silent:1b,PersistenceRequired:1b}");
		enable(context, EntityEspModule.class, true);
		enable(context, TrackersModule.class, true);
		enable(context, HitboxesModule.class, true);
		set(context, TrackersModule.class, "update_interval", 1.0);
		context.waitTicks(5);
		long[] before = context.computeOnClient(client -> new long[] {EntityIndex.builds(), ClientTicks.count(), femboy().hud().rebuilds()});
		context.waitTicks(40);
		long[] after = context.computeOnClient(client -> new long[] {EntityIndex.builds(), ClientTicks.count(), femboy().hud().rebuilds()});
		long ticks = after[1] - before[1];
		long builds = after[0] - before[0];
		long rebuilds = after[2] - before[2];
		boolean sharedEntities = context.computeOnClient(client -> femboy().modules().get(EntityEspModule.class).tracked().stream()
				.map(Entity::getType).anyMatch(type -> type == net.minecraft.world.entity.EntityType.HUSK));
		report.check("Three entity modules share one entity list: built at most once per tick", builds <= ticks + 1 && builds > 0 && sharedEntities,
				builds + " builds in " + ticks + " ticks");
		report.check("HUD text is rebuilt once per tick, not every frame", rebuilds <= ticks + 2, rebuilds + " rebuilds in " + ticks + " ticks");
		enable(context, EntityEspModule.class, false);
		enable(context, TrackersModule.class, false);
		enable(context, HitboxesModule.class, false);
		reset(context, TrackersModule.class);
		singleplayer.getServer().runCommand("kill @e[type=!player]");
	}

	private static void gui(ClientGameTestContext context, UiDriver ui, TestReport report) {
		ClientGameTest.openMenu(context);
		ClientGameTest.openModule(context, EntityEspModule.class);
		context.waitTicks(3);
		context.takeScreenshot("gui-v2-entity-esp");

		SettingRow maxRow = ui.find(SettingRow.class, row -> row.setting().id().equals("max_entities"));
		var header = ui.find(SettingRow.SectionHeader.class, h -> h.title().equals("Performance"));
		boolean startsClosed = header != null && context.computeOnClient(client -> header.collapsed() && maxRow.preferredHeight(
				((ClientScreen) client.screen).uiContext(), 200) == 0);
		ui.click(header);
		context.waitTicks(2);
		boolean opened = context.computeOnClient(client -> !header.collapsed() && maxRow.preferredHeight(((ClientScreen) client.screen).uiContext(), 200) > 0);
		report.check("Fine-tuning sections start collapsed and open with a click (Entity ESP 'Performance')", startsClosed && opened);

		context.runOnClient(client -> maxRow.editValue(((ClientScreen) client.screen).uiContext()));
		ui.key(GLFW.GLFW_KEY_BACKSPACE);
		ui.key(GLFW.GLFW_KEY_BACKSPACE);
		ui.key(GLFW.GLFW_KEY_BACKSPACE);
		ui.type("96");
		ui.key(GLFW.GLFW_KEY_ENTER);
		double typed = context.computeOnClient(client -> (Double) femboy().modules().get(EntityEspModule.class).settings().get("max_entities").get());
		report.check("A number can be typed exactly: 96 entered for 'Maximum highlights'", typed == 96, String.valueOf(typed));
		reset(context, EntityEspModule.class);

		int[] chip = context.computeOnClient(client -> ((ClientScreen) client.screen).profileChipBounds());
		ui.clickVirtual(chip[0] + chip[2] / 2.0, chip[1] + chip[3] / 2.0);
		context.waitTicks(2);
		String overlay = context.computeOnClient(client -> {
			var shown = ((ClientScreen) client.screen).uiContext().overlay();
			return shown == null ? "none" : shown.getClass().getSimpleName();
		});
		context.takeScreenshot("gui-v2-profile-menu");
		ui.key(GLFW.GLFW_KEY_ESCAPE);
		report.check("The profile selector in the header opens the profile list", overlay.equals("ProfileMenu"), overlay);

		context.runOnClient(client -> Notifications.show("Test", "a notification for the bell", 0xFF5BD68A));
		int[] bell = context.computeOnClient(client -> ((ClientScreen) client.screen).bellBounds());
		ui.clickVirtual(bell[0] + bell[2] / 2.0, bell[1] + bell[3] / 2.0);
		context.waitTicks(2);
		String list = context.computeOnClient(client -> {
			var shown = ((ClientScreen) client.screen).uiContext().overlay();
			return shown == null ? "none" : shown.getClass().getSimpleName();
		});
		boolean seen = context.computeOnClient(client -> GuiState.notificationsSeenAt >= Notifications.history().getLast().shownAt());
		context.takeScreenshot("gui-v2-notifications");
		ui.key(GLFW.GLFW_KEY_ESCAPE);
		report.check("The bell opens the notification list and marks the messages read", list.equals("NotificationList") && seen, list);

		set(context, ChunkBordersModule.class, "radius", 3.0);
		ClientGameTest.showPage(context, "Visuals");
		Button resetButton = ui.find(Button.class, b -> b.label().equals("Reset Visuals"));
		ui.click(resetButton);
		double afterOne = context.computeOnClient(client -> (Double) femboy().modules().get(ChunkBordersModule.class).settings().get("radius").get());
		String armedLabel = resetButton.label();
		ui.click(resetButton);
		double afterTwo = context.computeOnClient(client -> (Double) femboy().modules().get(ChunkBordersModule.class).settings().get("radius").get());
		report.check("Resetting a category asks for a second click, then restores its modules (Chunk Borders radius 3 -> 1)",
				afterOne == 3 && armedLabel.startsWith("Click again") && afterTwo == 1, afterOne + " / '" + armedLabel + "' / " + afterTwo);

		context.runOnClient(client -> femboy().options().reduceMotion.set(true));
		ClientGameTest.showPage(context, "Home");
		context.waitTicks(3);
		context.takeScreenshot("gui-v2-home");
		context.runOnClient(client -> femboy().options().reduceMotion.set(false));
		boolean pages = context.computeOnClient(client -> client.screen instanceof ClientScreen screen && screen.currentPage().title().equals("Home")
				&& screen.pageIndex(page -> page instanceof ModulesPage) > 0);
		report.check("The menu keeps working with reduced motion", pages);
		ClientGameTest.closeMenu(context, ui);
	}
}
