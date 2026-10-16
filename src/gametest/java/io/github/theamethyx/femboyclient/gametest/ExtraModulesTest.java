package io.github.theamethyx.femboyclient.gametest;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.lwjgl.glfw.GLFW;

import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.ByteArrayTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import io.github.theamethyx.femboyclient.client.FemboyClient;
import io.github.theamethyx.femboyclient.client.combat.Rotations;
import io.github.theamethyx.femboyclient.client.modules.AimTrainerModule;
import io.github.theamethyx.femboyclient.client.modules.AntiHungerModule;
import io.github.theamethyx.femboyclient.client.modules.AutoAnchorModule;
import io.github.theamethyx.femboyclient.client.modules.AutoCrystalModule;
import io.github.theamethyx.femboyclient.client.modules.AutoPotModule;
import io.github.theamethyx.femboyclient.client.modules.ChestOrganizerModule;
import io.github.theamethyx.femboyclient.client.modules.EntityEspModule;
import io.github.theamethyx.femboyclient.client.modules.EntityOutlinesModule;
import io.github.theamethyx.femboyclient.client.modules.HotbarProfilesModule;
import io.github.theamethyx.femboyclient.client.modules.ItemSwapModule;
import io.github.theamethyx.femboyclient.client.modules.MaterialListModule;
import io.github.theamethyx.femboyclient.client.modules.SchematicViewerModule;
import io.github.theamethyx.femboyclient.client.modules.StructureFinderModule;
import io.github.theamethyx.femboyclient.client.render.EspRenderer;
import io.github.theamethyx.femboyclient.client.schematic.Schematic;
import io.github.theamethyx.femboyclient.core.module.Module;
import io.github.theamethyx.femboyclient.core.setting.Setting;

/** The modules that were missing after 0.7.0: inventory tools, Anti Hunger, crystal/anchor/potion automation, outlines, world tools. */
final class ExtraModulesTest {
	private ExtraModulesTest() {
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

	private static void reset(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
		var server = singleplayer.getServer();
		context.runOnClient(client -> femboy().modules().disableAll());
		server.runCommand("clear @a");
		server.runCommand("effect clear @a");
		server.runCommand("kill @e[type=!player]");
		server.runCommand("gamemode survival @a");
		server.runCommand("tp @a 0.5 -60 0.5 0 0");
		server.computeOnServer(s -> {
			s.getPlayerList().getPlayers().getFirst().setHealth(20);
			return null;
		});
		context.runOnClient(client -> client.player.getInventory().setSelectedSlot(0));
		context.waitTicks(25);
	}

	private static float huskHealth(TestSingleplayerContext singleplayer) {
		return singleplayer.getServer().computeOnServer(s -> s.overworld().getEntitiesOfClass(Husk.class,
				s.getPlayerList().getPlayers().getFirst().getBoundingBox().inflate(16)).stream().filter(Husk::isAlive).findFirst().map(Husk::getHealth).orElse(-1f));
	}

	/** Puts the flat world's ground back after explosions. */
	private static void repairGround(TestSingleplayerContext singleplayer) {
		var server = singleplayer.getServer();
		server.runCommand("fill -8 -63 -8 8 -62 12 minecraft:dirt");
		server.runCommand("fill -8 -61 -8 8 -61 12 minecraft:grass_block");
		server.runCommand("fill -8 -60 -8 8 -55 12 minecraft:air");
	}

	static void run(ClientGameTestContext context, TestSingleplayerContext singleplayer, TestReport report) {
		inventory(context, singleplayer, report);
		antiHunger(context, singleplayer, report);
		autoPot(context, singleplayer, report);
		crystalAndAnchor(context, singleplayer, report);
		visuals(context, singleplayer, report);
		aimTrainer(context, singleplayer, report);
		schematics(context, singleplayer, report);

		reset(context, singleplayer);
		repairGround(singleplayer);
		singleplayer.getServer().runCommand("gamemode creative @a");
		context.waitTicks(3);
	}

	// ----- inventory -------------------------------------------------------------------------

	private static void inventory(ClientGameTestContext context, TestSingleplayerContext singleplayer, TestReport report) {
		var server = singleplayer.getServer();
		reset(context, singleplayer);
		server.runCommand("item replace entity @a hotbar.0 with minecraft:iron_sword");
		server.runCommand("item replace entity @a inventory.5 with minecraft:totem_of_undying");
		server.runCommand("item replace entity @a hotbar.4 with minecraft:golden_apple 3");
		context.waitTicks(3);
		enable(context, ItemSwapModule.class, true);
		context.runOnClient(client -> femboy().modules().get(ItemSwapModule.class).press(0));
		context.waitTicks(3);
		String pulled = server.computeOnServer(s -> {
			var inv = s.getPlayerList().getPlayers().getFirst().getInventory();
			return inv.getItem(0).getItem() + " / " + inv.getItem(14).getItem();
		});
		context.runOnClient(client -> femboy().modules().get(ItemSwapModule.class).press(0));
		context.waitTicks(3);
		String back = server.computeOnServer(s -> {
			var inv = s.getPlayerList().getPlayers().getFirst().getInventory();
			return inv.getItem(0).getItem() + " / " + inv.getItem(14).getItem();
		});
		report.check("Item Swap brings the totem from the inventory into the held slot and a second press puts it back (server agrees)",
				pulled.equals("minecraft:totem_of_undying / minecraft:iron_sword") && back.equals("minecraft:iron_sword / minecraft:totem_of_undying"),
				pulled + " -> " + back);

		context.runOnClient(client -> femboy().modules().get(ItemSwapModule.class).press(1));
		int selected = context.computeOnClient(client -> client.player.getInventory().getSelectedSlot());
		context.runOnClient(client -> femboy().modules().get(ItemSwapModule.class).press(1));
		int returned = context.computeOnClient(client -> client.player.getInventory().getSelectedSlot());
		enable(context, ItemSwapModule.class, false);
		report.check("Item Swap selects the golden apple already in the hotbar and goes back on the second press", selected == 4 && returned == 0,
				selected + " -> " + returned);

		// Hotbar Profiles: save a layout, scramble the items, load it back.
		reset(context, singleplayer);
		server.runCommand("item replace entity @a hotbar.0 with minecraft:diamond_sword");
		server.runCommand("item replace entity @a hotbar.1 with minecraft:diamond_pickaxe");
		server.runCommand("item replace entity @a hotbar.2 with minecraft:cobblestone 32");
		context.waitTicks(3);
		enable(context, HotbarProfilesModule.class, true);
		String saved = context.computeOnClient(client -> femboy().modules().get(HotbarProfilesModule.class).save("test_layout"));
		server.runCommand("clear @a");
		server.runCommand("item replace entity @a hotbar.5 with minecraft:diamond_sword");
		server.runCommand("item replace entity @a inventory.7 with minecraft:diamond_pickaxe");
		server.runCommand("item replace entity @a inventory.20 with minecraft:cobblestone 32");
		context.waitTicks(3);
		String loaded = context.computeOnClient(client -> femboy().modules().get(HotbarProfilesModule.class).load("test_layout"));
		context.waitTicks(3);
		String hotbar = server.computeOnServer(s -> {
			var inv = s.getPlayerList().getPlayers().getFirst().getInventory();
			return inv.getItem(0).getItem() + ", " + inv.getItem(1).getItem() + ", " + inv.getItem(2).getItem();
		});
		context.runOnClient(client -> femboy().modules().get(HotbarProfilesModule.class).delete("test_layout"));
		enable(context, HotbarProfilesModule.class, false);
		report.check("Hotbar Profiles saves a layout and moves the items back into place from inventory and hotbar (server agrees)",
				saved == null && loaded == null && hotbar.equals("minecraft:diamond_sword, minecraft:diamond_pickaxe, minecraft:cobblestone"),
				"save " + saved + ", load " + loaded + ", hotbar " + hotbar);

		chestOrganizer(context, singleplayer, report);
	}

	private static void chestOrganizer(ClientGameTestContext context, TestSingleplayerContext singleplayer, TestReport report) {
		var server = singleplayer.getServer();
		reset(context, singleplayer);
		BlockPos chest = new BlockPos(0, -60, 2);
		server.runCommand("setblock 0 -60 2 minecraft:chest[facing=north]");
		server.runCommand("item replace block 0 -60 2 container.0 with minecraft:stone 3");
		server.runCommand("item replace block 0 -60 2 container.4 with minecraft:dirt 5");
		server.runCommand("item replace block 0 -60 2 container.9 with minecraft:stone 4");
		server.runCommand("item replace entity @a inventory.0 with minecraft:stone 10");
		server.runCommand("item replace entity @a inventory.1 with minecraft:apple 2");
		context.waitTicks(3);
		enable(context, ChestOrganizerModule.class, true);
		context.runOnClient(client -> client.gameMode.useItemOn(client.player, InteractionHand.MAIN_HAND,
				new BlockHitResult(Vec3.atCenterOf(chest).add(0, 0, -0.5), Direction.NORTH, chest, false)));
		ClientGameTest.waitUntil(context, () -> context.computeOnClient(client -> client.screen instanceof ContainerScreen), 40);
		context.waitTicks(3);
		List<String> buttons = context.computeOnClient(client -> client.screen == null ? List.<String>of()
				: Screens.getButtons(client.screen).stream().map(AbstractWidget::getMessage).map(c -> c.getString()).toList());
		report.check("Chest Organizer adds Sort, Stack, Store and Take buttons to the chest screen",
				buttons.containsAll(List.of("Sort", "Stack", "Store", "Take")), buttons.toString());
		context.takeScreenshot("inventory-chest-organizer");

		context.runOnClient(client -> femboy().modules().get(ChestOrganizerModule.class).sort());
		context.waitTicks(5);
		String sorted = server.computeOnServer(s -> {
			ChestBlockEntity entity = (ChestBlockEntity) s.overworld().getBlockEntity(chest);
			return entity.getItem(0).getItem() + " " + entity.getItem(0).getCount() + ", " + entity.getItem(1).getItem() + " " + entity.getItem(1).getCount()
					+ ", " + entity.getItem(2).getItem();
		});
		report.check("Chest Organizer: Sort orders the chest and merges stacks (server: dirt 5, stone 7)",
				sorted.equals("minecraft:dirt 5, minecraft:stone 7, minecraft:air"), sorted);

		context.runOnClient(client -> femboy().modules().get(ChestOrganizerModule.class).stack());
		context.waitTicks(5);
		String stacked = server.computeOnServer(s -> {
			var player = s.getPlayerList().getPlayers().getFirst();
			return player.getInventory().countItem(Items.STONE) + " stone, " + player.getInventory().countItem(Items.APPLE) + " apples left";
		});
		report.check("Chest Organizer: Stack puts in only items the chest already has (stone in, apples stay)", stacked.equals("0 stone, 2 apples left"),
				stacked);

		context.runOnClient(client -> femboy().modules().get(ChestOrganizerModule.class).takeAll());
		context.waitTicks(5);
		boolean empty = server.computeOnServer(s -> ((ChestBlockEntity) s.overworld().getBlockEntity(chest)).isEmpty());
		int stone = server.computeOnServer(s -> s.getPlayerList().getPlayers().getFirst().getInventory().countItem(Items.STONE));
		report.check("Chest Organizer: Take moves everything into the inventory (server agrees)", empty && stone == 17, "chest empty " + empty + ", stone " + stone);

		context.runOnClient(client -> client.player.closeContainer());
		enable(context, ChestOrganizerModule.class, false);
		server.runCommand("setblock 0 -60 2 minecraft:air");
	}

	// ----- movement --------------------------------------------------------------------------

	private static int sprintedCm(TestSingleplayerContext singleplayer) {
		return singleplayer.getServer().computeOnServer(s -> s.getPlayerList().getPlayers().getFirst().getStats().getValue(Stats.CUSTOM.get(Stats.SPRINT_ONE_CM)));
	}

	private static int sprintRun(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
		singleplayer.getServer().runCommand("tp @a 0.5 -60 0.5 180 0");
		context.waitTicks(5);
		int before = sprintedCm(singleplayer);
		context.getInput().holdKey(options -> options.keySprint);
		context.getInput().holdKey(options -> options.keyUp);
		context.waitTicks(30);
		context.getInput().releaseKey(options -> options.keyUp);
		context.getInput().releaseKey(options -> options.keySprint);
		context.waitTicks(5);
		return sprintedCm(singleplayer) - before;
	}

	/**
	 * The server charges sprint hunger (0.1 per block) in the same branch where it counts the "sprint one cm" statistic,
	 * so that statistic shows whether the server treated the movement as sprinting.
	 */
	private static void antiHunger(ClientGameTestContext context, TestSingleplayerContext singleplayer, TestReport report) {
		reset(context, singleplayer);
		int normal = sprintRun(context, singleplayer);
		enable(context, AntiHungerModule.class, true);
		int saved = sprintRun(context, singleplayer);
		boolean stillFast = context.computeOnClient(client -> client.player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED) > 0);
		enable(context, AntiHungerModule.class, false);
		report.check("Anti Hunger: the server no longer counts the run as sprinting, so it charges no sprint hunger",
				normal > 300 && saved == 0 && stillFast, String.format(Locale.ROOT, "sprinted %d cm without, %d cm with", normal, saved));
	}

	// ----- combat ----------------------------------------------------------------------------

	private static void autoPot(ClientGameTestContext context, TestSingleplayerContext singleplayer, TestReport report) {
		var server = singleplayer.getServer();
		reset(context, singleplayer);
		server.runCommand("item replace entity @a hotbar.0 with minecraft:iron_sword");
		server.runCommand("item replace entity @a hotbar.3 with minecraft:splash_potion[potion_contents={potion:\"minecraft:healing\"}]");
		server.computeOnServer(s -> {
			s.getPlayerList().getPlayers().getFirst().setHealth(6);
			return null;
		});
		context.waitTicks(5);
		enable(context, AutoPotModule.class, true);
		boolean healed = ClientGameTest.waitUntil(context, () -> server.computeOnServer(s -> s.getPlayerList().getPlayers().getFirst().getHealth()) >= 9, 60);
		context.waitTicks(3);
		float health = server.computeOnServer(s -> s.getPlayerList().getPlayers().getFirst().getHealth());
		int thrown = context.computeOnClient(client -> femboy().modules().get(AutoPotModule.class).throwsMade());
		String view = context.computeOnClient(client -> client.player.getInventory().getSelectedSlot() + " / " + Math.round(client.player.getXRot()));
		enable(context, AutoPotModule.class, false);
		report.check("Auto Pot throws the healing potion when health is low; health rises and the view and slot come back",
				healed && thrown == 1 && view.equals("0 / 0"), String.format(Locale.ROOT, "health %.1f, thrown %d, slot/pitch %s", health, thrown, view));
	}

	private static void crystalAndAnchor(ClientGameTestContext context, TestSingleplayerContext singleplayer, TestReport report) {
		var server = singleplayer.getServer();
		reset(context, singleplayer);
		server.runCommand("gamemode creative @a");
		server.runCommand("fill -2 -61 2 2 -61 6 minecraft:obsidian");
		server.runCommand("item replace entity @a hotbar.1 with minecraft:end_crystal 16");
		server.runCommand("summon husk 0.5 -60 5.5 {NoAI:1b,PersistenceRequired:1b,Silent:1b}");
		context.waitTicks(25);
		enable(context, AutoCrystalModule.class, true);
		boolean hurt = ClientGameTest.waitUntil(context, () -> huskHealth(singleplayer) < 20, 80);
		context.waitTicks(10);
		String crystal = context.computeOnClient(client -> {
			AutoCrystalModule module = femboy().modules().get(AutoCrystalModule.class);
			return "placed " + module.placed() + ", broken " + module.broken();
		});
		float after = huskHealth(singleplayer);
		enable(context, AutoCrystalModule.class, false);
		context.takeScreenshot("automation-auto-crystal");
		report.check("AutoCrystal places a crystal on obsidian next to the husk and breaks it; the husk is hurt or killed",
				hurt && crystal.matches("placed [1-9]\\d*, broken [1-9]\\d*"), crystal + ", husk health " + after);

		reset(context, singleplayer);
		repairGround(singleplayer);
		server.runCommand("gamemode creative @a");
		server.runCommand("item replace entity @a hotbar.0 with minecraft:respawn_anchor 16");
		server.runCommand("item replace entity @a hotbar.1 with minecraft:glowstone 16");
		server.runCommand("summon husk 0.5 -60 4.5 {NoAI:1b,PersistenceRequired:1b,Silent:1b}");
		context.waitTicks(25);
		enable(context, AutoAnchorModule.class, true);
		boolean anchorHurt = ClientGameTest.waitUntil(context, () -> huskHealth(singleplayer) < 20, 80);
		context.waitTicks(5);
		String anchor = context.computeOnClient(client -> "explosions " + femboy().modules().get(AutoAnchorModule.class).explosions() + ", "
				+ femboy().modules().get(AutoAnchorModule.class).status());
		context.takeScreenshot("automation-auto-anchor");
		enable(context, AutoAnchorModule.class, false);
		report.check("AutoAnchor places, charges and detonates a respawn anchor next to the husk (overworld)", anchorHurt && !anchor.startsWith("explosions 0"),
				anchor + ", husk health " + huskHealth(singleplayer));
		repairGround(singleplayer);
	}

	// ----- visuals ---------------------------------------------------------------------------

	private static void visuals(ClientGameTestContext context, TestSingleplayerContext singleplayer, TestReport report) {
		var server = singleplayer.getServer();
		reset(context, singleplayer);
		server.runCommand("summon husk 0.5 -60 5.5 {NoAI:1b,PersistenceRequired:1b,Silent:1b,"
				+ "equipment:{head:{id:\"minecraft:diamond_helmet\",count:1},mainhand:{id:\"minecraft:iron_sword\",count:1}}}");
		context.waitTicks(25);
		enable(context, EntityOutlinesModule.class, true);
		context.waitTicks(3);
		boolean glowing = context.computeOnClient(client -> client.level.getEntitiesOfClass(Husk.class, client.player.getBoundingBox().inflate(10)).stream()
				.anyMatch(client::shouldEntityAppearGlowing));
		context.takeScreenshot("visuals-entity-outlines");
		enable(context, EntityOutlinesModule.class, false);
		boolean off = context.computeOnClient(client -> client.level.getEntitiesOfClass(Husk.class, client.player.getBoundingBox().inflate(10)).stream()
				.noneMatch(client::shouldEntityAppearGlowing));
		report.check("Entity Outlines makes the husk glow (vanilla outline) only while on", glowing && off, "on " + glowing + ", off " + off);

		set(context, EntityEspModule.class, "types", Set.of("minecraft:husk"));
		set(context, EntityEspModule.class, "equipment", true);
		enable(context, EntityEspModule.class, true);
		context.waitTicks(5);
		List<EspRenderer.DrawnLabel> labels = context.computeOnClient(client -> femboy().modules().get(EntityEspModule.class).lastLabels());
		context.takeScreenshot("visuals-esp-equipment");
		context.runOnClient(client -> femboy().modules().resetToDefaults(femboy().modules().get(EntityEspModule.class)));
		report.check("Entity ESP shows the husk's helmet and sword as icons above its label", labels.stream().anyMatch(l -> l.equipment() == 2),
				labels.toString());
	}

	private static void aimTrainer(ClientGameTestContext context, TestSingleplayerContext singleplayer, TestReport report) {
		reset(context, singleplayer);
		enable(context, AimTrainerModule.class, true);
		context.waitTicks(3);
		context.runOnClient(client -> {
			Vec3 target = femboy().modules().get(AimTrainerModule.class).targets().getFirst().center();
			Rotations.turnToward(client.player, target, 180, true);
		});
		context.waitTicks(2);
		context.takeScreenshot("combat-aim-trainer");
		context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
		context.waitTicks(2);
		context.runOnClient(client -> client.player.setXRot(-80));
		context.waitTicks(1);
		context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
		context.waitTicks(2);
		String score = context.computeOnClient(client -> {
			AimTrainerModule module = femboy().modules().get(AimTrainerModule.class);
			return module.hits() + " hit, " + module.misses() + " missed, targets " + module.targets().size();
		});
		enable(context, AimTrainerModule.class, false);
		report.check("Aim Trainer: aiming at a target and clicking is a hit (a new target appears), clicking at the sky is a miss",
				score.equals("1 hit, 1 missed, targets 1"), score);
	}

	// ----- world -----------------------------------------------------------------------------

	private static void writeSchematics() throws java.io.IOException {
		Path folder = SchematicViewerModule.folder();
		Files.createDirectories(folder);

		// Vanilla structure file: stone at x=0, oak planks at x=1.
		CompoundTag structure = new CompoundTag();
		ListTag size = new ListTag();
		size.add(IntTag.valueOf(2));
		size.add(IntTag.valueOf(1));
		size.add(IntTag.valueOf(1));
		structure.put("size", size);
		ListTag palette = new ListTag();
		CompoundTag stone = new CompoundTag();
		stone.putString("Name", "minecraft:stone");
		CompoundTag planks = new CompoundTag();
		planks.putString("Name", "minecraft:oak_planks");
		palette.add(stone);
		palette.add(planks);
		structure.put("palette", palette);
		ListTag blocks = new ListTag();
		for (int i = 0; i < 2; i++) {
			CompoundTag block = new CompoundTag();
			ListTag pos = new ListTag();
			pos.add(IntTag.valueOf(i));
			pos.add(IntTag.valueOf(0));
			pos.add(IntTag.valueOf(0));
			block.put("pos", pos);
			block.putInt("state", i);
			blocks.add(block);
		}

		structure.put("blocks", blocks);
		NbtIo.writeCompressed(structure, folder.resolve("test_build.nbt"));

		// Sponge schematic v2: palette by state string, varint block data.
		CompoundTag sponge = new CompoundTag();
		sponge.putInt("Version", 2);
		sponge.putShort("Width", (short) 2);
		sponge.putShort("Height", (short) 1);
		sponge.putShort("Length", (short) 1);
		CompoundTag spongePalette = new CompoundTag();
		spongePalette.putInt("minecraft:stone", 0);
		spongePalette.putInt("minecraft:oak_planks", 1);
		sponge.put("Palette", spongePalette);
		sponge.put("BlockData", new ByteArrayTag(new byte[] {0, 1}));
		NbtIo.writeCompressed(sponge, folder.resolve("test_build.schem"));

		// Litematica: palette air/stone/planks, 2 bits per block, values 1 then 2 packed into one long.
		CompoundTag litematic = new CompoundTag();
		CompoundTag regions = new CompoundTag();
		CompoundTag region = new CompoundTag();
		CompoundTag position = new CompoundTag();
		position.putInt("x", 0);
		position.putInt("y", 0);
		position.putInt("z", 0);
		CompoundTag regionSize = new CompoundTag();
		regionSize.putInt("x", 2);
		regionSize.putInt("y", 1);
		regionSize.putInt("z", 1);
		region.put("Position", position);
		region.put("Size", regionSize);
		ListTag litePalette = new ListTag();
		CompoundTag air = new CompoundTag();
		air.putString("Name", "minecraft:air");
		litePalette.add(air);
		litePalette.add(stone.copy());
		litePalette.add(planks.copy());
		region.put("BlockStatePalette", litePalette);
		region.put("BlockStates", new LongArrayTag(new long[] {1L | 2L << 2}));
		regions.put("main", region);
		litematic.put("Regions", regions);
		NbtIo.writeCompressed(litematic, folder.resolve("test_build.litematic"));
	}

	private static void schematics(ClientGameTestContext context, TestSingleplayerContext singleplayer, TestReport report) {
		var server = singleplayer.getServer();
		reset(context, singleplayer);
		try {
			writeSchematics();
		} catch (java.io.IOException e) {
			report.check("Test schematic files written", false, e.toString());
			return;
		}

		String formats = context.computeOnClient(client -> {
			StringBuilder out = new StringBuilder();
			for (String file : List.of("test_build.nbt", "test_build.schem", "test_build.litematic")) {
				try {
					Schematic schematic = Schematic.load(SchematicViewerModule.folder().resolve(file));
					out.append(file).append(": ").append(schematic.blockCount()).append(' ')
							.append(schematic.state(0).getBlock() == Blocks.STONE && schematic.state(1).getBlock() == Blocks.OAK_PLANKS
									&& schematic.position(1).getX() == 1 ? "ok" : "wrong").append("; ");
				} catch (Exception e) {
					out.append(file).append(": ").append(e.getMessage()).append("; ");
				}
			}

			return out.toString();
		});
		report.check("Schematic Viewer reads .nbt, .schem and .litematic files (stone then oak planks)",
				formats.equals("test_build.nbt: 2 ok; test_build.schem: 2 ok; test_build.litematic: 2 ok; "), formats);

		server.runCommand("setblock 4 -60 4 minecraft:stone");
		server.runCommand("setblock 5 -60 4 minecraft:dirt");
		enable(context, SchematicViewerModule.class, true);
		enable(context, MaterialListModule.class, true);
		String loaded = context.computeOnClient(client -> {
			SchematicViewerModule viewer = femboy().modules().get(SchematicViewerModule.class);
			String problem = viewer.load("test_build.nbt");
			viewer.placeAt(new BlockPos(4, -60, 4));
			return problem == null ? "ok" : problem;
		});
		ClientGameTest.waitUntil(context, () -> context.computeOnClient(client -> femboy().modules().get(SchematicViewerModule.class).progress() != null), 20);
		context.waitTicks(3);
		String compared = context.computeOnClient(client -> {
			var progress = femboy().modules().get(SchematicViewerModule.class).progress();
			return progress.placed() + " placed, " + progress.missing() + " missing, " + progress.wrong() + " wrong";
		});
		context.takeScreenshot("world-schematic-viewer");
		report.check("Schematic Viewer compares the placed schematic with the world (stone right, dirt where planks belong)",
				loaded.equals("ok") && compared.equals("1 placed, 0 missing, 1 wrong"), loaded + "; " + compared);

		server.runCommand("setblock 5 -60 4 minecraft:air");
		server.runCommand("item replace entity @a inventory.0 with minecraft:oak_planks 3");
		context.waitTicks(10);
		String materials = context.computeOnClient(client -> {
			var progress = femboy().modules().get(SchematicViewerModule.class).progress();
			List<MaterialListModule.Entry> entries = femboy().modules().get(MaterialListModule.class).entries();
			return progress.missing() + " missing; " + entries.stream().map(e -> e.name() + " " + e.left() + "/" + e.needed() + " carrying " + e.carried()).toList();
		});
		report.check("Material List shows what is left to place and what you carry (1 oak planks left, carrying 3)",
				materials.equals("1 missing; [Oak Planks 1/1 carrying 3]"), materials);

		enable(context, MaterialListModule.class, false);
		enable(context, SchematicViewerModule.class, false);
		context.runOnClient(client -> femboy().modules().resetToDefaults(femboy().modules().get(SchematicViewerModule.class)));
		server.runCommand("fill 4 -60 4 5 -60 4 minecraft:air");
	}

	/** Structure Finder in a normal world: the same search as /locate, run on the integrated server. */
	static void structureFinder(ClientGameTestContext context, TestReport report) {
		set(context, StructureFinderModule.class, "structures", Set.of("minecraft:stronghold", "minecraft:village_plains"));
		enable(context, StructureFinderModule.class, true);
		boolean searched = ClientGameTest.waitUntil(context, () -> context.computeOnClient(client ->
				femboy().modules().get(StructureFinderModule.class).searches() > 0), 600);
		List<StructureFinderModule.Found> found = context.computeOnClient(client -> femboy().modules().get(StructureFinderModule.class).found());
		context.takeScreenshot("world-structure-finder");
		enable(context, StructureFinderModule.class, false);
		context.runOnClient(client -> femboy().modules().resetToDefaults(femboy().modules().get(StructureFinderModule.class)));
		report.check("Structure Finder finds the nearest stronghold in a normal singleplayer world", searched
				&& found.stream().anyMatch(f -> f.id().equals("minecraft:stronghold")), found.toString());
	}
}
