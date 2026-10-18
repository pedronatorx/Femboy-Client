package io.github.theamethyx.femboyclient.gametest;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Locale;

import org.lwjgl.glfw.GLFW;

import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.GuiMessage;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.phys.Vec3;

import io.github.theamethyx.femboyclient.client.FemboyClient;
import io.github.theamethyx.femboyclient.client.gui.HudEditorScreen;
import io.github.theamethyx.femboyclient.client.gui.Theme;
import io.github.theamethyx.femboyclient.client.hud.HudManager;
import io.github.theamethyx.femboyclient.client.hud.Notifications;
import io.github.theamethyx.femboyclient.client.mixin.GameRendererAccessor;
import io.github.theamethyx.femboyclient.client.modules.*;
import io.github.theamethyx.femboyclient.core.module.Module;
import io.github.theamethyx.femboyclient.core.setting.Setting;

/** GUI themes and HUD editor, and the visual, utility, inventory, movement and combat modules added for the full spec. */
final class SpecModulesTest {
	private SpecModulesTest() {
	}

	static FemboyClient femboy() {
		return FemboyClient.get();
	}

	private static void enable(ClientGameTestContext context, Class<? extends Module> type, boolean on) {
		context.runOnClient(client -> femboy().modules().setEnabled(femboy().modules().get(type), on));
	}

	@SuppressWarnings("unchecked")
	private static <T> void set(ClientGameTestContext context, Class<? extends Module> type, String id, T value) {
		context.runOnClient(client -> ((Setting<T>) femboy().modules().get(type).settings().get(id)).set(value));
	}

	private static float huskHealth(TestSingleplayerContext singleplayer) {
		return singleplayer.getServer().computeOnServer(s -> s.overworld().getEntitiesOfClass(Husk.class,
				s.getPlayerList().getPlayers().getFirst().getBoundingBox().inflate(16)).stream().filter(Husk::isAlive).findFirst().map(Husk::getHealth).orElse(-1f));
	}

	private static void freshHusk(ClientGameTestContext context, TestSingleplayerContext singleplayer, String position) {
		singleplayer.getServer().runCommand("kill @e[type=!player]");
		context.waitTicks(25);
		singleplayer.getServer().runCommand("summon husk " + position + " {NoAI:1b,PersistenceRequired:1b,Silent:1b}");
	}

	private static float serverHealth(TestSingleplayerContext singleplayer) {
		return singleplayer.getServer().computeOnServer(s -> s.getPlayerList().getPlayers().getFirst().getHealth());
	}

	private static void heal(TestSingleplayerContext singleplayer) {
		singleplayer.getServer().computeOnServer(s -> {
			s.getPlayerList().getPlayers().getFirst().setHealth(20);
			return null;
		});
	}

	private static void reset(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
		var server = singleplayer.getServer();
		context.runOnClient(client -> femboy().modules().disableAll());
		server.runCommand("clear @a");
		server.runCommand("effect clear @a");
		server.runCommand("kill @e[type=!player]");
		server.runCommand("gamemode survival @a");
		heal(singleplayer);
		context.runOnClient(client -> client.player.getInventory().setSelectedSlot(0));
	}

	static void run(ClientGameTestContext context, TestSingleplayerContext singleplayer, UiDriver ui, TestReport report) {
		themes(context, ui, report);
		hudEditor(context, ui, report);
		visuals(context, singleplayer, report);
		utilities(context, singleplayer, report);
		inventory(context, singleplayer, report);
		movement(context, singleplayer, report);
		combat(context, singleplayer, report);

		reset(context, singleplayer);
		singleplayer.getServer().runCommand("gamemode creative @a");
		singleplayer.getServer().runCommand("fill -3 -60 -3 3 -57 6 minecraft:air");
		singleplayer.getServer().runCommand("tp @a 0.5 -60 0.5 0 15");
		context.waitTicks(3);
	}

	// ----- GUI -------------------------------------------------------------------------------

	private static void themes(ClientGameTestContext context, UiDriver ui, TestReport report) {
		int before = context.computeOnClient(client -> Theme.ACCENT);
		context.runOnClient(client -> femboy().options().theme.set(Theme.Palette.MIDNIGHT_MAGENTA));
		int magenta = context.computeOnClient(client -> Theme.ACCENT);
		ClientGameTest.openMenu(context);
		context.waitTicks(3);
		context.takeScreenshot("theme-midnight-magenta");
		ClientGameTest.closeMenu(context, ui);
		context.runOnClient(client -> {
			femboy().options().accent.set(0xFF33CC99);
			femboy().options().customAccent.set(true);
		});
		int custom = context.computeOnClient(client -> Theme.ACCENT);
		context.runOnClient(client -> {
			femboy().options().customAccent.set(false);
			femboy().options().accent.reset();
			femboy().options().theme.reset();
		});
		int restored = context.computeOnClient(client -> Theme.ACCENT);
		report.check("Theme selector switches the menu colors; a custom accent overrides the theme's; default is the navy/magenta Femboy theme",
				before == 0xFF9D6BFF && magenta == 0xFFE05CC8 && custom == 0xFF33CC99 && restored == before,
				String.format(Locale.ROOT, "default %08X, midnight magenta %08X, custom %08X, back %08X", before, magenta, custom, restored));
	}

	private static void hudEditor(ClientGameTestContext context, UiDriver ui, TestReport report) {
		enable(context, CoordinatesHudModule.class, true);
		enable(context, KeystrokesModule.class, true);
		context.waitTicks(3);
		context.runOnClient(client -> client.setScreen(new HudEditorScreen(null, femboy().hud())));
		context.waitTicks(3);
		List<String> placedIds = context.computeOnClient(client -> femboy().hud().placed().stream().map(HudManager.Placed::moduleId).toList());
		context.runOnClient(client -> ((HudEditorScreen) client.screen).moveTo("coordinates_hud", 200, 120));
		context.waitTicks(3);
		context.takeScreenshot("hud-editor");
		HudManager.Placed moved = context.computeOnClient(client -> femboy().hud().placed().stream()
				.filter(p -> p.moduleId().equals("coordinates_hud")).findFirst().orElse(null));
		String saved = context.computeOnClient(client -> String.valueOf(femboy().config().section("hud_layout")));
		context.runOnClient(client -> client.setScreen(null));
		report.check("HUD editor: Coordinates and the Keystrokes widget are placed; dragging Coordinates moves it and saves the position",
				placedIds.contains("coordinates_hud") && placedIds.contains("keystrokes") && moved != null && moved.custom()
						&& Math.abs(moved.x() - 200) <= 1 && Math.abs(moved.y() - 120) <= 1 && saved.contains("coordinates_hud"),
				"placed " + placedIds + "; moved " + moved + "; saved " + saved);
		context.runOnClient(client -> femboy().hud().layout().resetAll());
		context.waitTicks(2);
		boolean back = context.computeOnClient(client -> femboy().hud().placed().stream().noneMatch(HudManager.Placed::custom));
		report.check("HUD editor reset puts blocks back into their corners", back);
		enable(context, CoordinatesHudModule.class, false);
		enable(context, KeystrokesModule.class, false);
	}

	// ----- visuals ---------------------------------------------------------------------------

	private static void visuals(ClientGameTestContext context, TestSingleplayerContext singleplayer, TestReport report) {
		var server = singleplayer.getServer();
		server.runCommand("tp @a 0.5 -60 0.5 0 0");
		context.waitTicks(3);

		enable(context, ZoomModule.class, true);
		float normal = context.computeOnClient(client -> ((GameRendererAccessor) client.gameRenderer).femboyclient$getFov(client.gameRenderer.getMainCamera(), 1, true));
		context.runOnClient(client -> femboy().modules().get(ZoomModule.class).setZooming(true));
		float zoomed = context.computeOnClient(client -> ((GameRendererAccessor) client.gameRenderer).femboyclient$getFov(client.gameRenderer.getMainCamera(), 1, true));
		context.takeScreenshot("visuals-zoom");
		context.runOnClient(client -> femboy().modules().get(ZoomModule.class).setZooming(false));
		enable(context, ZoomModule.class, false);
		report.check("Zoom divides the field of view by the zoom factor (4x)", Math.abs(normal / zoomed - 4) < 0.01,
				String.format(Locale.ROOT, "fov %.1f -> %.1f", normal, zoomed));

		// Two diamond ores: one buried in dirt, one replacing grass at the surface (exposed to air).
		server.runCommand("setblock 5 -62 5 minecraft:diamond_ore");
		server.runCommand("setblock 6 -61 5 minecraft:diamond_ore");
		context.waitTicks(3);
		enable(context, BlockEspModule.class, true);
		enable(context, BlockCounterModule.class, true);
		context.waitTicks(30);
		List<BlockPos> ores = context.computeOnClient(client -> femboy().modules().get(BlockEspModule.class).shown().stream().map(h -> h.pos()).toList());
		set(context, BlockEspModule.class, "exposed_only", true);
		context.waitTicks(3);
		List<BlockPos> exposed = context.computeOnClient(client -> femboy().modules().get(BlockEspModule.class).shown().stream().map(h -> h.pos()).toList());
		context.takeScreenshot("visuals-ore-esp");
		var counts = context.computeOnClient(client -> femboy().modules().get(BlockCounterModule.class).counts());
		String progress = context.computeOnClient(client -> femboy().modules().get(BlockCounterModule.class).scanner().scannedChunks() + "/"
				+ femboy().modules().get(BlockCounterModule.class).scanner().chunksInRange());
		context.runOnClient(client -> femboy().modules().resetToDefaults(femboy().modules().get(BlockEspModule.class)));
		enable(context, BlockCounterModule.class, false);
		report.check("Block ESP finds both placed diamond ores; 'only exposed' keeps just the one touching air",
				ores.contains(new BlockPos(5, -62, 5)) && ores.contains(new BlockPos(6, -61, 5)) && exposed.contains(new BlockPos(6, -61, 5))
						&& !exposed.contains(new BlockPos(5, -62, 5)), "all " + ores + ", exposed " + exposed);
		report.check("Block Counter counts the 2 diamond ores in loaded chunks and shows full scan progress",
				Integer.valueOf(2).equals(counts.get("Diamond Ore")) && progress.split("/")[0].equals(progress.split("/")[1]), counts + ", scanned " + progress);
		server.runCommand("setblock 5 -62 5 minecraft:dirt");
		server.runCommand("setblock 6 -61 5 minecraft:grass_block");

		enable(context, LightLevelModule.class, true);
		context.waitTicks(12);
		boolean darkSpot = context.computeOnClient(client -> femboy().modules().get(LightLevelModule.class).spots().stream()
				.anyMatch(s -> s.pos().equals(new BlockPos(3, -60, 0))));
		server.runCommand("setblock 2 -60 0 minecraft:torch");
		context.waitTicks(12);
		boolean litSpot = context.computeOnClient(client -> femboy().modules().get(LightLevelModule.class).spots().stream()
				.anyMatch(s -> s.pos().equals(new BlockPos(3, -60, 0))));
		context.takeScreenshot("visuals-light-level");
		server.runCommand("setblock 2 -60 0 minecraft:air");
		enable(context, LightLevelModule.class, false);
		report.check("Light Level marks grass with block light 0 as a spawn spot and stops once a torch lights it", darkSpot && !litSpot,
				"before torch " + darkSpot + ", after torch " + litSpot);

		server.runCommand("tp @a 0.5 -60 0.5 0 45");
		enable(context, BlockHighlightModule.class, true);
		context.waitTicks(3);
		String highlight = context.computeOnClient(client -> femboy().modules().get(BlockHighlightModule.class).highlighted() + " vs "
				+ (client.hitResult instanceof net.minecraft.world.phys.BlockHitResult hit ? hit.getBlockPos() : null));
		enable(context, BlockHighlightModule.class, false);
		report.check("Block Highlight outlines the block under the crosshair", highlight.split(" vs ")[0].equals(highlight.split(" vs ")[1])
				&& !highlight.startsWith("null"), highlight);
	}

	// ----- utilities -------------------------------------------------------------------------

	@SuppressWarnings("unchecked")
	private static void utilities(ClientGameTestContext context, TestSingleplayerContext singleplayer, TestReport report) {
		var server = singleplayer.getServer();
		enable(context, NotificationsModule.class, true);
		context.runOnClient(client -> Notifications.show("Test", "toast shown", Theme.SUCCESS));
		int toasts = context.computeOnClient(client -> Notifications.active(3000).size());
		context.takeScreenshot("utilities-notification");
		enable(context, NotificationsModule.class, false);
		report.check("Notifications shows client messages as toasts while on", toasts >= 1, toasts + " toast(s)");

		enable(context, BetterChatModule.class, true);
		context.runOnClient(client -> {
			client.gui.getChat().addMessage(Component.literal("femboy repeat"));
			client.gui.getChat().addMessage(Component.literal("femboy repeat"));
		});
		String chat = context.computeOnClient(client -> {
			try {
				Field field = client.gui.getChat().getClass().getDeclaredField("allMessages");
				field.setAccessible(true);
				List<GuiMessage> messages = (List<GuiMessage>) field.get(client.gui.getChat());
				long copies = messages.stream().filter(m -> m.content().getString().contains("femboy repeat")).count();
				return copies + "|" + messages.getFirst().content().getString();
			} catch (ReflectiveOperationException e) {
				return "error " + e;
			}
		});
		enable(context, BetterChatModule.class, false);
		report.check("Better Chat adds a timestamp and stacks a repeated message into one line with x2",
				chat.startsWith("1|[") && chat.endsWith("femboy repeat x2"), chat);

		enable(context, AutoReconnectModule.class, true);
		set(context, AutoReconnectModule.class, "delay", 60.0);
		context.runOnClient(client -> {
			AutoReconnectModule.rememberServer(new ServerData("Test", "localhost:25599", ServerData.Type.OTHER));
			client.setScreen(new DisconnectedScreen(new TitleScreen(), Component.literal("Disconnected"), Component.literal("test")));
		});
		context.waitTicks(3);
		String button = context.computeOnClient(client -> Screens.getButtons(client.screen).stream().map(w -> ((AbstractWidget) w).getMessage().getString())
				.filter(text -> text.startsWith("Reconnect")).findFirst().orElse("none"));
		context.takeScreenshot("utilities-auto-reconnect");
		context.runOnClient(client -> {
			client.setScreen(null);
			AutoReconnectModule.rememberServer(null);
			femboy().modules().resetToDefaults(femboy().modules().get(AutoReconnectModule.class));
		});
		report.check("Auto Reconnect adds a reconnect button with a countdown to the disconnected screen", button.startsWith("Reconnecting in"), button);

		server.runCommand("gamemode survival @a");
		server.runCommand("tp @a 3.5 -60 7.5 0 0");
		enable(context, DeathCoordinatesModule.class, true);
		enable(context, WaypointsModule.class, true);
		context.waitTicks(3);
		server.runCommand("kill @p");
		context.waitTicks(10);
		var death = context.computeOnClient(client -> femboy().modules().get(DeathCoordinatesModule.class).last());
		boolean waypoint = context.computeOnClient(client -> femboy().modules().get(WaypointsModule.class).all().stream().anyMatch(w -> w.name().startsWith("Death")));
		context.runOnClient(client -> {
			client.player.respawn();
			client.setScreen(null);
		});
		context.waitTicks(20);
		context.runOnClient(client -> {
			var waypoints = femboy().modules().get(WaypointsModule.class);
			List.copyOf(waypoints.all()).stream().filter(w -> w.name().startsWith("Death")).forEach(waypoints::remove);
		});
		enable(context, DeathCoordinatesModule.class, false);
		enable(context, WaypointsModule.class, false);
		report.check("Death Coordinates records where you died and saves a waypoint", death != null && death.x() == 3 && death.z() == 7 && waypoint,
				death + ", waypoint " + waypoint);
	}

	// ----- inventory -------------------------------------------------------------------------

	private static void inventory(ClientGameTestContext context, TestSingleplayerContext singleplayer, TestReport report) {
		var server = singleplayer.getServer();
		reset(context, singleplayer);

		enable(context, ShulkerPreviewModule.class, true);
		boolean preview = context.computeOnClient(client -> {
			ItemStack box = new ItemStack(Items.SHULKER_BOX);
			box.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(List.of(new ItemStack(Items.DIAMOND, 5))));
			return box.getTooltipImage().orElse(null) instanceof ShulkerPreviewModule.Preview p && p.items().getFirst().is(Items.DIAMOND);
		});
		enable(context, ShulkerPreviewModule.class, false);
		boolean off = context.computeOnClient(client -> {
			ItemStack box = new ItemStack(Items.SHULKER_BOX);
			box.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(List.of(new ItemStack(Items.DIAMOND, 5))));
			return !(box.getTooltipImage().orElse(null) instanceof ShulkerPreviewModule.Preview);
		});
		report.check("Shulker Preview gives shulker boxes an item-grid tooltip only while on", preview && off, "on " + preview + ", off " + off);

		// Auto Tool: empty hand, pickaxe in slot 2, a stone block in front.
		server.runCommand("item replace entity @a hotbar.1 with minecraft:stone_pickaxe");
		server.runCommand("item replace entity @a hotbar.2 with minecraft:iron_shovel");
		server.runCommand("setblock 0 -60 2 minecraft:stone");
		server.runCommand("tp @a 0.5 -60 0.5 0 29");
		enable(context, AutoToolModule.class, true);
		context.waitTicks(5);
		context.getInput().holdMouseFor(GLFW.GLFW_MOUSE_BUTTON_LEFT, 6);
		int mining = context.computeOnClient(client -> client.player.getInventory().getSelectedSlot());
		context.waitTicks(10);
		int after = context.computeOnClient(client -> client.player.getInventory().getSelectedSlot());
		enable(context, AutoToolModule.class, false);
		server.runCommand("setblock 0 -60 2 minecraft:air");
		report.check("Auto Tool selects the pickaxe while mining stone and switches back afterwards", mining == 1 && after == 0,
				"while mining slot " + mining + ", after " + after);

		reset(context, singleplayer);
		server.runCommand("item replace entity @a inventory.0 with minecraft:stone 5");
		server.runCommand("item replace entity @a inventory.1 with minecraft:dirt 10");
		server.runCommand("item replace entity @a inventory.2 with minecraft:stone 3");
		server.runCommand("item replace entity @a inventory.5 with minecraft:apple 1");
		context.waitTicks(3);
		enable(context, InventorySorterModule.class, true);
		context.runOnClient(client -> femboy().modules().get(InventorySorterModule.class).sort());
		context.waitTicks(5);
		String serverOrder = server.computeOnServer(s -> {
			var inv = s.getPlayerList().getPlayers().getFirst().getInventory();
			StringBuilder out = new StringBuilder();
			for (int i = 9; i < 13; i++) out.append(inv.getItem(i).getItem()).append(' ').append(inv.getItem(i).getCount()).append(", ");
			return out.toString();
		});
		enable(context, InventorySorterModule.class, false);
		report.check("Inventory Sorter orders the main inventory by item, merging stacks of the same item (server agrees)",
				serverOrder.equals("minecraft:apple 1, minecraft:dirt 10, minecraft:stone 8, minecraft:air 0, "), serverOrder);

		reset(context, singleplayer);
		server.runCommand("item replace entity @a armor.chest with minecraft:leather_chestplate");
		server.runCommand("item replace entity @a inventory.3 with minecraft:diamond_chestplate");
		context.waitTicks(3);
		enable(context, AutoArmorModule.class, true);
		context.waitTicks(10);
		boolean armor = server.computeOnServer(s -> s.getPlayerList().getPlayers().getFirst().getItemBySlot(EquipmentSlot.CHEST).is(Items.DIAMOND_CHESTPLATE)
				&& s.getPlayerList().getPlayers().getFirst().getInventory().countItem(Items.LEATHER_CHESTPLATE) == 1);
		enable(context, AutoArmorModule.class, false);
		report.check("Auto Armor swaps the leather chestplate for the diamond one from the inventory (server agrees)", armor);

		reset(context, singleplayer);
		server.runCommand("item replace entity @a hotbar.0 with minecraft:golden_apple 1");
		server.runCommand("item replace entity @a inventory.5 with minecraft:golden_apple 10");
		context.waitTicks(3);
		enable(context, AutoRefillModule.class, true);
		context.waitTicks(10);
		int refilled = server.computeOnServer(s -> s.getPlayerList().getPlayers().getFirst().getInventory().getItem(0).getCount());
		enable(context, AutoRefillModule.class, false);
		report.check("Auto Refill tops up a hotbar stack of 1 golden apple from the inventory (server: 11)", refilled == 11, String.valueOf(refilled));

		reset(context, singleplayer);
		server.runCommand("item replace entity @a inventory.2 with minecraft:shield");
		context.waitTicks(3);
		set(context, AutoTotemModule.class, "item", AutoTotemModule.OffhandItem.SHIELD);
		enable(context, AutoTotemModule.class, true);
		context.waitTicks(8);
		boolean shield = server.computeOnServer(s -> s.getPlayerList().getPlayers().getFirst().getOffhandItem().is(Items.SHIELD));
		context.runOnClient(client -> femboy().modules().resetToDefaults(femboy().modules().get(AutoTotemModule.class)));
		report.check("Auto Offhand with 'Shield' puts a shield into the offhand (server agrees)", shield);
	}

	// ----- movement --------------------------------------------------------------------------

	private static double walk(ClientGameTestContext context, TestSingleplayerContext singleplayer, String from, int ticks,
			java.util.function.Consumer<ClientGameTestContext> during) {
		singleplayer.getServer().runCommand("tp @a " + from + " 0 0");
		context.waitTicks(5);
		Vec3 start = context.computeOnClient(client -> client.player.position());
		context.getInput().holdKey(options -> options.keyUp);
		during.accept(context);
		context.waitTicks(ticks);
		context.getInput().releaseKey(options -> options.keyUp);
		Vec3 end = context.computeOnClient(client -> client.player.position());
		return end.z - start.z;
	}

	private static void movement(ClientGameTestContext context, TestSingleplayerContext singleplayer, TestReport report) {
		var server = singleplayer.getServer();
		reset(context, singleplayer);

		enable(context, SprintModule.class, true);
		server.runCommand("tp @a 0.5 -60 -30.5 0 0");
		context.waitTicks(3);
		context.getInput().holdKey(options -> options.keyUp);
		context.waitTicks(8);
		boolean sprinting = context.computeOnClient(client -> client.player.isSprinting());
		context.getInput().releaseKey(options -> options.keyUp);
		enable(context, SprintModule.class, false);
		report.check("Sprint keeps you sprinting while walking forward", sprinting);

		double baseline = walk(context, singleplayer, "0.5 -60 -30.5", 20, c -> { });
		set(context, SpeedModule.class, "speed", 0.7);
		enable(context, SpeedModule.class, true);
		double fast = walk(context, singleplayer, "0.5 -60 -30.5", 20, c -> { });
		enable(context, SpeedModule.class, false);
		report.check("Speed (0.5 blocks/tick): walking 20 ticks covers well over twice the normal distance", fast > baseline * 2,
				String.format(Locale.ROOT, "normal %.1f, with Speed %.1f blocks", baseline, fast));

		server.runCommand("item replace entity @a hotbar.0 with minecraft:bow");
		server.runCommand("item replace entity @a hotbar.1 with minecraft:arrow 16");
		double slowed = walk(context, singleplayer, "0.5 -60 -30.5", 20, c -> c.getInput().holdMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT));
		context.getInput().releaseMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
		enable(context, NoSlowModule.class, true);
		double unslowed = walk(context, singleplayer, "0.5 -60 -30.5", 20, c -> c.getInput().holdMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT));
		context.getInput().releaseMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
		enable(context, NoSlowModule.class, false);
		report.check("No Slow: walking while drawing a bow is no longer slowed", unslowed > slowed * 2,
				String.format(Locale.ROOT, "drawing without %.2f, with No Slow %.2f blocks", slowed, unslowed));
		server.runCommand("clear @a");

		// Step: a one-block-high wall, five blocks deep, across the path (so after 20 ticks you are still on top of it).
		server.runCommand("fill -2 -60 3 2 -60 7 minecraft:stone");
		enable(context, StepModule.class, true);
		walk(context, singleplayer, "0.5 -60 0.5", 20, c -> { });
		double stepped = context.computeOnClient(client -> client.player.getY());
		enable(context, StepModule.class, false);
		walk(context, singleplayer, "0.5 -60 0.5", 20, c -> { });
		double blocked = context.computeOnClient(client -> client.player.getY());
		server.runCommand("fill -2 -60 3 2 -60 7 minecraft:air");
		report.check("Step walks up a full block without jumping; without it the wall stops you", stepped >= -59.01 && blocked < -59.9,
				String.format(Locale.ROOT, "with Step y %.2f, without y %.2f", stepped, blocked));

		// Safe Walk: a 5x5 platform two blocks above the ground.
		server.runCommand("fill -2 -59 -2 2 -59 2 minecraft:stone");
		enable(context, SafeWalkModule.class, true);
		walk(context, singleplayer, "0.5 -58 0.5", 30, c -> { });
		double safe = context.computeOnClient(client -> client.player.getY());
		enable(context, SafeWalkModule.class, false);
		walk(context, singleplayer, "0.5 -58 0.5", 30, c -> { });
		double fell = context.computeOnClient(client -> client.player.getY());
		server.runCommand("fill -2 -59 -2 2 -59 2 minecraft:air");
		report.check("Safe Walk stops you at the platform edge; without it you walk off", safe > -58.1 && fell < -59.5,
				String.format(Locale.ROOT, "with Safe Walk y %.2f, without y %.2f", safe, fell));

		heal(singleplayer);
		enable(context, NoFallModule.class, true);
		server.runCommand("tp @a 0.5 -40 0.5 0 0");
		ClientGameTest.waitUntil(context, () -> context.computeOnClient(client -> client.player.getY() > -45), 40);
		boolean landed = ClientGameTest.waitUntil(context, () -> context.computeOnClient(client -> client.player.onGround() && client.player.getY() < -59), 200);
		context.waitTicks(5);
		float health = serverHealth(singleplayer);
		int sent = context.computeOnClient(client -> femboy().modules().get(NoFallModule.class).sent());
		enable(context, NoFallModule.class, false);
		report.check("No Fall: a 20-block fall (17 damage without it) deals no damage", landed && health == 20 && sent > 0,
				"landed " + landed + ", health " + health + ", ground packets " + sent);
	}

	// ----- combat ----------------------------------------------------------------------------

	private static void combat(ClientGameTestContext context, TestSingleplayerContext singleplayer, TestReport report) {
		var server = singleplayer.getServer();
		reset(context, singleplayer);
		server.runCommand("item replace entity @a hotbar.0 with minecraft:dirt");
		server.runCommand("item replace entity @a hotbar.2 with minecraft:iron_sword");
		freshHusk(context, singleplayer, "0.5 -60 3.5");
		server.runCommand("tp @a 0.5 -60 0.5 0 15");
		context.waitTicks(25);

		enable(context, AutoWeaponSwitchModule.class, true);
		ClientGameTest.waitUntil(context, () -> context.computeOnClient(client -> client.hitResult instanceof net.minecraft.world.phys.EntityHitResult), 20);
		// The sword is selected while aiming; switching restarts the attack charge (12.5 ticks for a sword), as by hand.
		ClientGameTest.waitUntil(context, () -> context.computeOnClient(client -> client.player.getInventory().getSelectedSlot() == 2), 10);
		context.waitTicks(15);
		context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
		context.waitTicks(5);
		int slot = context.computeOnClient(client -> client.player.getInventory().getSelectedSlot());
		float afterSwitch = huskHealth(singleplayer);
		enable(context, AutoWeaponSwitchModule.class, false);
		report.check("Auto Weapon Switch picks the iron sword while you aim at the husk; the hit deals sword damage", slot == 2 && afterSwitch < 15,
				"slot " + slot + ", husk health " + afterSwitch);

		freshHusk(context, singleplayer, "0.5 -60 3.5");
		context.waitTicks(25);
		enable(context, AutoCriticalsModule.class, true);
		ClientGameTest.waitUntil(context, () -> context.computeOnClient(client -> client.hitResult instanceof net.minecraft.world.phys.EntityHitResult), 20);
		context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
		ClientGameTest.waitUntil(context, () -> huskHealth(singleplayer) < 20, 30);
		context.waitTicks(5);
		float critHealth = huskHealth(singleplayer);
		int crits = context.computeOnClient(client -> femboy().modules().get(AutoCriticalsModule.class).crits());
		enable(context, AutoCriticalsModule.class, false);
		report.check("Auto Criticals: the click becomes a jump and a critical hit (more than a normal 5.9 damage)",
				crits == 1 && 20 - critHealth > 7.5, String.format(Locale.ROOT, "crits %d, damage %.2f", crits, 20 - critHealth));

		enable(context, WTapModule.class, true);
		context.runOnClient(client -> client.player.setSprinting(true));
		context.getInput().holdKey(options -> options.keyUp);
		context.runOnClient(client -> client.gameMode.attack(client.player, client.level.getEntitiesOfClass(Husk.class,
				client.player.getBoundingBox().inflate(8)).getFirst()));
		boolean paused = context.computeOnClient(client -> !client.player.isSprinting() && femboy().modules().get(WTapModule.class).pausing());
		context.waitTicks(5);
		boolean resumed = context.computeOnClient(client -> client.player.isSprinting());
		context.getInput().releaseKey(options -> options.keyUp);
		enable(context, WTapModule.class, false);
		report.check("Sprint Reset stops sprinting right after a sprinting hit and sprints again after the pause", paused && resumed,
				"paused " + paused + ", resumed " + resumed);
	}
}
