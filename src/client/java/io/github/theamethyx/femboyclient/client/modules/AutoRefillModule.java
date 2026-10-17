package io.github.theamethyx.femboyclient.client.modules;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;

import io.github.theamethyx.femboyclient.core.module.Module;
import io.github.theamethyx.femboyclient.core.module.ModuleCategory;
import io.github.theamethyx.femboyclient.core.setting.NumberSetting;

/**
 * Tops up a hotbar stack from your inventory when it runs low or out (same item and components), with normal
 * inventory clicks, one stack per delay. Sandbox only.
 */
public final class AutoRefillModule extends Module {
	private final NumberSetting threshold = settings().add(new NumberSetting("threshold", "Refill at", "Refill when a stack has this many or fewer left.", 1, 0, 32, 1, ""));
	private final NumberSetting delay = settings().add(new NumberSetting("delay", "Delay", "Wait between two refills.", 100, 0, 2000, 25, "ms"));

	private final ItemStack[] remembered = new ItemStack[9];
	private long readyAt;
	private int refills;

	public AutoRefillModule() {
		super("auto_refill", "Auto Refill", "Tops up hotbar stacks from your inventory when they run low.", ModuleCategory.INVENTORY, false, NO_KEY);
	}

	@Override
	public boolean testEnvironmentOnly() {
		return false;
	}

	public int refills() {
		return refills;
	}

	@Override
	protected void onEnable() {
		java.util.Arrays.fill(remembered, null);
	}

	@Override
	protected void onTick() {
		Minecraft client = Minecraft.getInstance();
		LocalPlayer player = client.player;
		Inventory inventory = player.getInventory();
		for (int slot = 0; slot < 9; slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (!stack.isEmpty()) {
				remembered[slot] = stack.copyWithCount(1);
			}
		}

		if (System.currentTimeMillis() < readyAt || client.gameMode == null || (client.screen != null && !(client.screen instanceof InventoryScreen))) {
			return;
		}

		InventoryMenu menu = player.inventoryMenu;
		for (int slot = 0; slot < 9; slot++) {
			ItemStack stack = inventory.getItem(slot);
			ItemStack wanted = remembered[slot];
			if (wanted == null || stack.getCount() > threshold.intValue() || (!stack.isEmpty() && !ItemStack.isSameItemSameComponents(stack, wanted))
					|| stack.getCount() >= stack.getMaxStackSize() && !stack.isEmpty()) {
				continue;
			}

			for (int source = 9; source <= 35; source++) {
				if (ItemStack.isSameItemSameComponents(menu.getSlot(source).getItem(), wanted)) {
					// Pick up the inventory stack, drop it onto the hotbar stack (merging), put any rest back.
					client.gameMode.handleInventoryMouseClick(menu.containerId, source, 0, ClickType.PICKUP, player);
					client.gameMode.handleInventoryMouseClick(menu.containerId, 36 + slot, 0, ClickType.PICKUP, player);
					client.gameMode.handleInventoryMouseClick(menu.containerId, source, 0, ClickType.PICKUP, player);
					refills++;
					readyAt = System.currentTimeMillis() + delay.get().longValue();
					return;
				}
			}
		}
	}
}
