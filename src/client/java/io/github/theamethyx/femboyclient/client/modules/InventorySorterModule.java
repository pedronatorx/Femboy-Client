package io.github.theamethyx.femboyclient.client.modules;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.InventoryMenu;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;

import io.github.theamethyx.femboyclient.client.FemboyClient;
import io.github.theamethyx.femboyclient.client.combat.InventoryActions;
import io.github.theamethyx.femboyclient.core.module.Module;
import io.github.theamethyx.femboyclient.core.module.ModuleCategory;
import io.github.theamethyx.femboyclient.core.setting.BooleanSetting;

/**
 * Adds a "Sort" button to your inventory that orders the 27 main slots by item, then by stack size, using the same
 * clicks you would make by hand (stacks of the same item merge on the way). The hotbar is left alone unless included.
 */
public final class InventorySorterModule extends Module {
	private final BooleanSetting hotbar = settings().add(new BooleanSetting("hotbar", "Include hotbar", "Also sort the 9 hotbar slots.", false));

	private int clicks;

	public InventorySorterModule() {
		super("inventory_sorter", "Inventory Sorter", "A Sort button in your inventory that orders items by type and amount.",
				ModuleCategory.INVENTORY, false, NO_KEY);
	}

	@Override
	public Tier tier() {
		return Tier.AUTOMATION;
	}

	public int clicks() {
		return clicks;
	}

	public static void registerEvents() {
		ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
			InventorySorterModule module = FemboyClient.get().modules().get(InventorySorterModule.class);
			if (screen instanceof InventoryScreen && module != null && module.isRunning()) {
				int left = (width - 176) / 2;
				int top = (height - 166) / 2;
				Screens.getButtons(screen).add(Button.builder(Component.literal("Sort"), b -> module.sort())
						.bounds(left + 176 - 30, top - 14, 30, 12).build());
			}
		});
	}

	/** Sorts the inventory now (the button calls this). */
	public void sort() {
		Minecraft client = Minecraft.getInstance();
		LocalPlayer player = client.player;
		if (player == null || client.gameMode == null) {
			return;
		}

		InventoryMenu menu = player.inventoryMenu;
		clicks += InventoryActions.sort(menu, InventoryActions.MAIN_FIRST, hotbar.get() ? 44 : InventoryActions.MAIN_LAST);
	}
}
