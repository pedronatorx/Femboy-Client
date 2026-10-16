package io.github.theamethyx.femboyclient.client.modules;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;

import io.github.theamethyx.femboyclient.client.FemboyClient;
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
		int first = 9;
		int last = hotbar.get() ? 44 : 35;
		Comparator<ItemStack> order = Comparator.<ItemStack, Boolean>comparing(ItemStack::isEmpty)
				.thenComparing(stack -> BuiltInRegistries.ITEM.getKey(stack.getItem()).toString())
				.thenComparing(stack -> stack.getHoverName().getString())
				.thenComparing(ItemStack::getCount, Comparator.reverseOrder());
		// Each move is three clicks; clicking a stack onto the same item merges them, like by hand. The real slots are
		// read again after every move, so merges are accounted for.
		for (int guard = 0; guard < 200; guard++) {
			List<ItemStack> current = new ArrayList<>();
			for (int slot = first; slot <= last; slot++) {
				current.add(menu.getSlot(slot).getItem().copy());
			}

			List<ItemStack> wanted = new ArrayList<>(current);
			wanted.sort(order);
			int target = -1;
			int source = -1;
			for (int t = 0; t < wanted.size() && source < 0; t++) {
				if (ItemStack.matches(current.get(t), wanted.get(t))) {
					continue;
				}

				for (int i = t + 1; i < current.size(); i++) {
					if (ItemStack.matches(current.get(i), wanted.get(t))) {
						target = t;
						source = i;
						break;
					}
				}
			}

			if (source < 0) {
				return;
			}

			click(client, menu, first + source);
			click(client, menu, first + target);
			click(client, menu, first + source);
		}
	}

	private void click(Minecraft client, InventoryMenu menu, int slot) {
		client.gameMode.handleInventoryMouseClick(menu.containerId, slot, 0, ClickType.PICKUP, client.player);
		clicks++;
	}
}
