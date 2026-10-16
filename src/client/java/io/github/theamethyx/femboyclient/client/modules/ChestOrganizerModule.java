package io.github.theamethyx.femboyclient.client.modules;

import java.util.HashSet;
import java.util.Set;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ShulkerBoxMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;

import io.github.theamethyx.femboyclient.client.FemboyClient;
import io.github.theamethyx.femboyclient.client.combat.InventoryActions;
import io.github.theamethyx.femboyclient.core.module.Module;
import io.github.theamethyx.femboyclient.core.module.ModuleCategory;
import io.github.theamethyx.femboyclient.core.setting.BooleanSetting;

/**
 * Buttons on chests, barrels, ender chests and shulker boxes: <b>Sort</b> the container, <b>Stack</b> (put in the items
 * that are already in the container), <b>Store</b> everything and <b>Take</b> everything. Moves use normal clicks and
 * shift-clicks, so the server handles them like your own.
 */
public final class ChestOrganizerModule extends Module {
	private final BooleanSetting hotbar = settings().add(new BooleanSetting("hotbar", "Include hotbar",
			"Stack and Store also move items from your hotbar (not only the main inventory).", false));

	private int actions;

	public ChestOrganizerModule() {
		super("chest_organizer", "Chest Organizer", "Sort, Stack, Store and Take buttons on chests, barrels and shulker boxes.",
				ModuleCategory.INVENTORY, false, NO_KEY);
	}

	@Override
	public Tier tier() {
		return Tier.AUTOMATION;
	}

	public int actions() {
		return actions;
	}

	/** Container slots of the open menu, or -1 when it is not one this module handles. */
	public static int containerSize(AbstractContainerMenu menu) {
		if (menu instanceof ChestMenu chest) {
			return chest.getRowCount() * 9;
		}

		return menu instanceof ShulkerBoxMenu ? 27 : -1;
	}

	public static void registerEvents() {
		ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
			ChestOrganizerModule module = FemboyClient.get().modules().get(ChestOrganizerModule.class);
			if (!(screen instanceof AbstractContainerScreen<?> container) || module == null || !module.isRunning()) {
				return;
			}

			int size = containerSize(container.getMenu());
			if (size < 0) {
				return;
			}

			// Same layout as vanilla: 176 wide; chests are 114 + 18 per row tall, shulker boxes 166.
			int imageHeight = container.getMenu() instanceof ChestMenu chest ? 114 + chest.getRowCount() * 18 : 166;
			int right = (width + 176) / 2;
			int top = (height - imageHeight) / 2 - 14;
			String[] labels = {"Take", "Store", "Stack", "Sort"};
			Runnable[] actions = {module::takeAll, module::storeAll, module::stack, module::sort};
			String[] tips = {"Move everything from the container into your inventory.", "Move your whole inventory into the container.",
					"Put in the items the container already has.", "Sort the container by item."};
			int x = right;
			for (int i = 0; i < labels.length; i++) {
				int w = 34;
				x -= w;
				Runnable action = actions[i];
				Button button = Button.builder(Component.literal(labels[i]), b -> action.run()).bounds(x, top, w - 2, 12)
						.tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal(tips[i]))).build();
				Screens.getButtons(screen).add(button);
			}
		});
	}

	private AbstractContainerMenu menu() {
		Minecraft client = Minecraft.getInstance();
		LocalPlayer player = client.player;
		if (player == null || client.gameMode == null || containerSize(player.containerMenu) < 0) {
			return null;
		}

		return player.containerMenu;
	}

	/** Sorts the container's slots. */
	public void sort() {
		AbstractContainerMenu menu = menu();
		if (menu != null) {
			InventoryActions.sort(menu, 0, containerSize(menu) - 1);
			actions++;
		}
	}

	/** Shift-clicks every player stack whose item the container already holds. */
	public void stack() {
		AbstractContainerMenu menu = menu();
		if (menu == null) {
			return;
		}

		int size = containerSize(menu);
		Set<Item> present = new HashSet<>();
		for (int slot = 0; slot < size; slot++) {
			ItemStack stack = menu.getSlot(slot).getItem();
			if (!stack.isEmpty()) {
				present.add(stack.getItem());
			}
		}

		movePlayerStacks(menu, size, stack -> present.contains(stack.getItem()));
		actions++;
	}

	/** Shift-clicks every player stack into the container. */
	public void storeAll() {
		AbstractContainerMenu menu = menu();
		if (menu != null) {
			movePlayerStacks(menu, containerSize(menu), stack -> true);
			actions++;
		}
	}

	/** Shift-clicks every container stack into the player's inventory. */
	public void takeAll() {
		AbstractContainerMenu menu = menu();
		if (menu == null) {
			return;
		}

		int size = containerSize(menu);
		for (int slot = 0; slot < size; slot++) {
			if (!menu.getSlot(slot).getItem().isEmpty()) {
				InventoryActions.quickMove(menu, slot);
			}
		}

		actions++;
	}

	private void movePlayerStacks(AbstractContainerMenu menu, int size, java.util.function.Predicate<ItemStack> which) {
		// After the container come 27 main inventory slots, then 9 hotbar slots.
		int last = size + (hotbar.get() ? 35 : 26);
		for (int slot = size; slot <= last; slot++) {
			ItemStack stack = menu.getSlot(slot).getItem();
			if (!stack.isEmpty() && which.test(stack)) {
				InventoryActions.quickMove(menu, slot);
			}
		}
	}
}
