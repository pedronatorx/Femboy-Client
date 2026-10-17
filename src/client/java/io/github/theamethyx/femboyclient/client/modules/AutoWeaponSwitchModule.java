package io.github.theamethyx.femboyclient.client.modules;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;

import io.github.theamethyx.femboyclient.client.FemboyClient;
import io.github.theamethyx.femboyclient.client.combat.ItemStats;
import io.github.theamethyx.femboyclient.core.module.Module;
import io.github.theamethyx.femboyclient.core.module.ModuleCategory;

/**
 * Selects the hotbar item with the highest attack damage as soon as a living target is under your crosshair (and at the
 * latest when you attack), and keeps it. Switching early matters: the server applies a weapon's damage only after it has
 * seen you hold it for a tick, and switching restarts your attack charge as it does by hand. Sandbox only.
 */
public final class AutoWeaponSwitchModule extends Module {
	private int switches;

	public AutoWeaponSwitchModule() {
		super("auto_weapon_switch", "Auto Weapon Switch", "Selects your strongest hotbar weapon when a target is under your crosshair.", ModuleCategory.COMBAT, false, NO_KEY);
	}

	@Override
	public boolean testEnvironmentOnly() {
		return false;
	}

	public int switches() {
		return switches;
	}

	@Override
	protected void onTick() {
		Minecraft client = Minecraft.getInstance();
		if (client.screen == null && client.crosshairPickEntity instanceof LivingEntity living && living.isAlive()) {
			selectBest(client.player);
		}
	}

	/** Called before an attack is sent (before Auto Attribute Swap). */
	public static void beforeAttack(LocalPlayer player, Entity target) {
		FemboyClient femboy = FemboyClient.get();
		AutoWeaponSwitchModule module = femboy == null ? null : femboy.modules().get(AutoWeaponSwitchModule.class);
		if (module != null && module.isRunning() && target instanceof LivingEntity) {
			module.selectBest(player);
		}
	}

	private void selectBest(LocalPlayer player) {
		Inventory inventory = player.getInventory();
		int best = inventory.getSelectedSlot();
		double bestDamage = ItemStats.attackDamage(inventory.getItem(best));
		for (int slot = 0; slot < Inventory.getSelectionSize(); slot++) {
			double damage = ItemStats.attackDamage(inventory.getItem(slot));
			if (damage > bestDamage + 1.0E-3) {
				best = slot;
				bestDamage = damage;
			}
		}

		if (best != inventory.getSelectedSlot()) {
			inventory.setSelectedSlot(best);
			switches++;
		}
	}
}
