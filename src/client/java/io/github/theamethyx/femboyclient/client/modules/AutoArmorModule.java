package io.github.theamethyx.femboyclient.client.modules;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

import io.github.theamethyx.femboyclient.client.combat.ItemStats;
import io.github.theamethyx.femboyclient.core.module.Module;
import io.github.theamethyx.femboyclient.core.module.ModuleCategory;
import io.github.theamethyx.femboyclient.core.setting.BooleanSetting;
import io.github.theamethyx.femboyclient.core.setting.NumberSetting;

/**
 * Puts on the best armor from your inventory (armor points plus half of toughness), one piece per delay, with the same
 * clicks as by hand. Keeps an elytra on if you want, and never touches items with Curse of Binding. Sandbox only.
 */
public final class AutoArmorModule extends Module {
	private static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

	private final NumberSetting delay = settings().add(new NumberSetting("delay", "Delay", "Wait between two pieces.", 150, 0, 2000, 25, "ms"));
	private final BooleanSetting keepElytra = settings().add(new BooleanSetting("keep_elytra", "Keep elytra on", "Do not replace a worn elytra with a chestplate.", true));

	private long readyAt;
	private int equipped;

	public AutoArmorModule() {
		super("auto_armor", "Auto Armor", "Puts on the best armor from your inventory.", ModuleCategory.INVENTORY, false, NO_KEY);
	}

	@Override
	public boolean testEnvironmentOnly() {
		return true;
	}

	public int equipped() {
		return equipped;
	}

	/** Inventory menu slot of an armor slot (5 = head ... 8 = feet). */
	private static int menuSlot(EquipmentSlot slot) {
		return 8 - slot.getIndex();
	}

	@Override
	protected void onTick() {
		Minecraft client = Minecraft.getInstance();
		LocalPlayer player = client.player;
		if (System.currentTimeMillis() < readyAt || client.gameMode == null || (client.screen != null && !(client.screen instanceof InventoryScreen))) {
			return;
		}

		InventoryMenu menu = player.inventoryMenu;
		if (!menu.getCarried().isEmpty()) {
			return; // the player is moving an item by hand; clicks now would drop or swap it
		}

		for (EquipmentSlot slot : SLOTS) {
			ItemStack worn = player.getItemBySlot(slot);
			if (EnchantmentHelper.has(worn, EnchantmentEffectComponents.PREVENT_ARMOR_CHANGE) || keepElytra.get() && worn.has(DataComponents.GLIDER)) {
				continue;
			}

			double wornScore = ItemStats.armorScore(worn);
			int best = -1;
			double bestScore = wornScore;
			for (int menuIndex = 9; menuIndex <= 44; menuIndex++) {
				ItemStack candidate = menu.getSlot(menuIndex).getItem();
				if (ItemStats.armorSlot(candidate) == slot && ItemStats.armorScore(candidate) > bestScore + 1.0E-3) {
					best = menuIndex;
					bestScore = ItemStats.armorScore(candidate);
				}
			}

			if (best >= 0) {
				// Pick up the better piece, swap it into the armor slot, put the old one where the new one was.
				client.gameMode.handleInventoryMouseClick(menu.containerId, best, 0, ClickType.PICKUP, player);
				client.gameMode.handleInventoryMouseClick(menu.containerId, menuSlot(slot), 0, ClickType.PICKUP, player);
				client.gameMode.handleInventoryMouseClick(menu.containerId, best, 0, ClickType.PICKUP, player);
				equipped++;
				readyAt = System.currentTimeMillis() + io.github.theamethyx.femboyclient.client.Performance.actionDelayMillis(delay.get().longValue());
				return;
			}
		}
	}
}
