package io.github.theamethyx.femboyclient.client.combat;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;

/**
 * Inventory moves made with the same clicks a player makes by hand (pick up, put down, number-key swap, shift-click).
 * The server checks each click like any other, so nothing here can do what a player could not.
 *
 * <p>Slot numbers are menu slots. In the player's own inventory menu the main inventory is 9-35, the hotbar 36-44 and
 * the offhand 45.
 */
public final class InventoryActions {
	public static final int MAIN_FIRST = 9;
	public static final int MAIN_LAST = 35;
	public static final int HOTBAR_FIRST = 36;
	public static final int OFFHAND = 45;

	/** Sort order: items by registry id, then name, then larger stacks first; empty slots last. */
	public static final Comparator<ItemStack> ORDER = Comparator.<ItemStack, Boolean>comparing(ItemStack::isEmpty)
			.thenComparing(stack -> BuiltInRegistries.ITEM.getKey(stack.getItem()).toString())
			.thenComparing(stack -> stack.getHoverName().getString())
			.thenComparing(ItemStack::getCount, Comparator.reverseOrder());

	private InventoryActions() {
	}

	public static void click(AbstractContainerMenu menu, int slot, int button, ClickType type) {
		Minecraft client = Minecraft.getInstance();
		client.gameMode.handleInventoryMouseClick(menu.containerId, slot, button, type, client.player);
	}

	/** Swaps a menu slot with hotbar slot {@code hotbar} (0-8), like pressing that number key over the slot. */
	public static void swapWithHotbar(AbstractContainerMenu menu, int slot, int hotbar) {
		click(menu, slot, hotbar, ClickType.SWAP);
	}

	/** Shift-click: moves a stack to the other part of the menu (e.g. from a chest into your inventory). */
	public static void quickMove(AbstractContainerMenu menu, int slot) {
		click(menu, slot, 0, ClickType.QUICK_MOVE);
	}

	/** Hotbar slot (0-8) holding a matching item, or -1. */
	public static int findHotbar(LocalPlayer player, Predicate<ItemStack> matches) {
		for (int slot = 0; slot < 9; slot++) {
			if (matches.test(player.getInventory().getItem(slot))) {
				return slot;
			}
		}

		return -1;
	}

	/** Main-inventory menu slot (9-35 of the player's inventory menu) holding a matching item, or -1. */
	public static int findMain(LocalPlayer player, Predicate<ItemStack> matches) {
		for (int slot = MAIN_FIRST; slot <= MAIN_LAST; slot++) {
			if (matches.test(player.inventoryMenu.getSlot(slot).getItem())) {
				return slot;
			}
		}

		return -1;
	}

	/**
	 * Merges partial stacks of the same item (and components) into the earliest one, by picking a later stack up, clicking
	 * it onto the earlier one and putting any rest back.
	 *
	 * @return the number of clicks made
	 */
	public static int merge(AbstractContainerMenu menu, int first, int last) {
		int clicks = 0;
		for (int target = first; target <= last; target++) {
			for (int source = target + 1; source <= last; source++) {
				ItemStack into = menu.getSlot(target).getItem();
				ItemStack from = menu.getSlot(source).getItem();
				if (into.isEmpty() || into.getCount() >= into.getMaxStackSize() || !ItemStack.isSameItemSameComponents(into, from)) {
					continue;
				}

				click(menu, source, 0, ClickType.PICKUP);
				click(menu, target, 0, ClickType.PICKUP);
				clicks += 2;
				if (!menu.getCarried().isEmpty()) {
					click(menu, source, 0, ClickType.PICKUP);
					clicks++;
				}
			}
		}

		return clicks;
	}

	/**
	 * Sorts menu slots {@code first..last} with {@link #ORDER}, three clicks per move. Clicking a stack onto the same item
	 * merges them, as by hand; the real slots are read again after every move, so merges are accounted for.
	 *
	 * @return the number of clicks made
	 */
	public static int sort(AbstractContainerMenu menu, int first, int last) {
		int clicks = merge(menu, first, last);
		for (int guard = 0; guard < 300; guard++) {
			List<ItemStack> current = new ArrayList<>();
			for (int slot = first; slot <= last; slot++) {
				current.add(menu.getSlot(slot).getItem().copy());
			}

			List<ItemStack> wanted = new ArrayList<>(current);
			wanted.sort(ORDER);
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
				break;
			}

			click(menu, first + source, 0, ClickType.PICKUP);
			click(menu, first + target, 0, ClickType.PICKUP);
			click(menu, first + source, 0, ClickType.PICKUP);
			clicks += 3;
		}

		// A move can leave a stack on the cursor when merging overflowed; put it into any empty slot of the range.
		if (!menu.getCarried().isEmpty()) {
			for (int slot = first; slot <= last; slot++) {
				if (menu.getSlot(slot).getItem().isEmpty()) {
					click(menu, slot, 0, ClickType.PICKUP);
					clicks++;
					break;
				}
			}
		}

		return clicks;
	}
}
