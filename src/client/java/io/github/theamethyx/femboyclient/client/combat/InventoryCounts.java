package io.github.theamethyx.femboyclient.client.combat;

import java.util.function.Predicate;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** Counting items in the local player's inventory (all slots including the hotbar, armor and offhand). */
public final class InventoryCounts {
	public static final int HOTBAR_SIZE = 9;

	private InventoryCounts() {
	}

	public static int count(Player player, Predicate<ItemStack> matches) {
		Inventory inventory = player.getInventory();
		int total = 0;
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			ItemStack stack = inventory.getItem(i);
			if (!stack.isEmpty() && matches.test(stack)) {
				total += stack.getCount();
			}
		}

		return total;
	}

	/** Items in the hotbar and offhand, i.e. usable without opening the inventory. */
	public static int countReady(Player player, Predicate<ItemStack> matches) {
		Inventory inventory = player.getInventory();
		int total = 0;
		for (int i = 0; i < HOTBAR_SIZE; i++) {
			ItemStack stack = inventory.getItem(i);
			if (!stack.isEmpty() && matches.test(stack)) {
				total += stack.getCount();
			}
		}

		ItemStack offhand = player.getOffhandItem();
		return total + (!offhand.isEmpty() && matches.test(offhand) ? offhand.getCount() : 0);
	}
}
