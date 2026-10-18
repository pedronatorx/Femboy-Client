package io.github.theamethyx.femboyclient.gametest;

import java.util.ArrayList;
import java.util.List;

import org.lwjgl.glfw.GLFW;

import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;

import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.screen.v1.Screens;

import io.github.theamethyx.femboyclient.client.hud.HudLine;
import io.github.theamethyx.femboyclient.client.modules.CooldownsModule;
import io.github.theamethyx.femboyclient.client.modules.InventorySearchModule;
import io.github.theamethyx.femboyclient.client.modules.LookInfoModule;
import io.github.theamethyx.femboyclient.client.modules.MinimapModule;

/**
 * The modules added in 1.2.0 in a flat test world: Look At on a block and a creature, Cooldowns with the seconds left,
 * the Minimap drawing its image, and Inventory Search marking items (also inside a shulker box) without letting typed
 * letters close the inventory.
 */
@SuppressWarnings("UnstableApiUsage")
final class ExpansionTest {
	private ExpansionTest() {
	}

	static void run(ClientGameTestContext context, TestSingleplayerContext singleplayer, TestReport report) {
		lookAt(context, singleplayer, report);
		cooldowns(context, singleplayer, report);
		minimap(context, report);
		inventorySearch(context, singleplayer, report);
	}

	private static List<HudLine> lines(ClientGameTestContext context, Class<? extends io.github.theamethyx.femboyclient.client.module.ClientHooks.HudBlock> type) {
		return context.computeOnClient(client -> {
			List<HudLine> lines = new ArrayList<>();
			((io.github.theamethyx.femboyclient.client.module.ClientHooks.HudBlock) ClientGameTest.femboy().modules().get(
					type.asSubclass(io.github.theamethyx.femboyclient.core.module.Module.class))).buildHud(lines);
			return lines;
		});
	}

	private static String text(List<HudLine> lines) {
		StringBuilder out = new StringBuilder();
		for (HudLine line : lines) {
			out.append(line.label() == null ? "" : line.label()).append(' ').append(line.value()).append(" | ");
		}

		return out.toString();
	}

	private static void lookAt(ClientGameTestContext context, TestSingleplayerContext singleplayer, TestReport report) {
		var server = singleplayer.getServer();
		server.runCommand("gamemode survival @a");
		server.runCommand("setblock 0 -60 2 minecraft:iron_ore");
		server.runCommand("tp @a 0.5 -60 0.5 0 28");
		ClientGameTest.enable(context, LookInfoModule.class, true);
		context.waitTicks(5);
		String block = text(lines(context, LookInfoModule.class));
		report.check("Look At names the block under the crosshair, its mod, and that a bare hand cannot harvest it",
				block.contains("Iron Ore") && block.contains("Minecraft") && block.contains("Needs a better tool") && block.contains("Break time"), block);

		server.runCommand("setblock 0 -60 2 minecraft:air");
		server.runCommand("summon minecraft:pig 0.5 -60 2.5 {NoAI:1b,Health:7f}");
		server.runCommand("tp @a 0.5 -60 0.5 0 35");
		context.waitTicks(5);
		String creature = text(lines(context, LookInfoModule.class));
		report.check("Look At shows a creature's health", creature.contains("Pig") && creature.contains("Health 7.0 / 10"), creature);
		server.runCommand("kill @e[type=minecraft:pig]");
		ClientGameTest.enable(context, LookInfoModule.class, false);
	}

	private static void cooldowns(ClientGameTestContext context, TestSingleplayerContext singleplayer, TestReport report) {
		singleplayer.getServer().runCommand("clear @a");
		singleplayer.getServer().runCommand("give @a minecraft:ender_pearl 4");
		ClientGameTest.enable(context, CooldownsModule.class, true);
		context.waitTicks(3);
		// The same call the client makes when the server sends a cooldown (20 ticks: one second).
		context.runOnClient(client -> client.player.getCooldowns().addCooldown(new ItemStack(Items.ENDER_PEARL), 20));
		context.waitTicks(4);
		String shown = text(lines(context, CooldownsModule.class));
		report.check("Cooldowns lists the ender pearl with the seconds left", shown.contains("Ender Pearl") && shown.contains(" s"), shown);
		context.waitTicks(30);
		String after = text(lines(context, CooldownsModule.class));
		report.check("Cooldowns drops the pearl when its cooldown is over", !after.contains("Ender Pearl"), after);
		ClientGameTest.enable(context, CooldownsModule.class, false);
		singleplayer.getServer().runCommand("clear @a");
	}

	private static void minimap(ClientGameTestContext context, TestReport report) {
		ClientGameTest.enable(context, MinimapModule.class, true);
		boolean drawn = ClientGameTest.waitUntil(context, () -> context.computeOnClient(client ->
				ClientGameTest.femboy().modules().get(MinimapModule.class).passes() >= 2), 200);
		context.takeScreenshot("hud-minimap");
		report.check("The minimap draws the loaded area and redraws it", drawn,
				context.computeOnClient(client -> ClientGameTest.femboy().modules().get(MinimapModule.class).workload()));
		ClientGameTest.enable(context, MinimapModule.class, false);
	}

	private static void inventorySearch(ClientGameTestContext context, TestSingleplayerContext singleplayer, TestReport report) {
		var server = singleplayer.getServer();
		server.runCommand("clear @a");
		server.runCommand("give @a minecraft:diamond 3");
		server.runCommand("give @a minecraft:dirt 5");
		ClientGameTest.enable(context, InventorySearchModule.class, true);
		context.waitTicks(3);
		context.runOnClient(client -> client.setScreen(new InventoryScreen(client.player)));
		context.waitForScreen(InventoryScreen.class);
		EditBox box = context.computeOnClient(client -> Screens.getButtons(client.screen).stream().filter(EditBox.class::isInstance)
				.map(EditBox.class::cast).findFirst().orElse(null));
		report.check("Inventory Search adds a search box to the inventory", box != null);
		if (box == null) {
			context.runOnClient(client -> client.setScreen(null));
			return;
		}

		context.runOnClient(client -> {
			client.screen.setFocused(box);
			box.setFocused(true);
		});
		context.getInput().typeChars("diamond");
		context.getInput().pressKey(GLFW.GLFW_KEY_E);
		context.waitTicks(2);
		boolean stillOpen = context.computeOnClient(client -> client.screen instanceof InventoryScreen);
		String typed = context.computeOnClient(client -> box.getValue());
		report.check("Typing in the search box (even E) does not close the inventory", stillOpen && typed.startsWith("diamond"), typed);

		boolean marks = context.computeOnClient(client -> {
			InventorySearchModule module = ClientGameTest.femboy().modules().get(InventorySearchModule.class);
			module.setQuery("diamond");
			ItemStack shulker = new ItemStack(Items.SHULKER_BOX);
			shulker.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(List.of(new ItemStack(Items.DIAMOND, 2))));
			return module.matches(new ItemStack(Items.DIAMOND)) && !module.matches(new ItemStack(Items.DIRT)) && module.matches(shulker);
		});
		report.check("Inventory Search matches diamonds, also inside a shulker box, and not dirt", marks);
		context.takeScreenshot("inventory-search");
		context.runOnClient(client -> client.setScreen(null));
		ClientGameTest.enable(context, InventorySearchModule.class, false);
		server.runCommand("clear @a");
	}
}
