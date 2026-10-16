package io.github.theamethyx.femboyclient.client.modules;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

import io.github.theamethyx.femboyclient.core.module.Module;
import io.github.theamethyx.femboyclient.core.module.ModuleCategory;

/**
 * Keeps you sprinting while you walk forward, like vanilla's "toggle sprint" option: only when vanilla would let you
 * sprint (enough food, not sneaking, not using an item, not blocked).
 */
public final class SprintModule extends Module {
	public SprintModule() {
		super("sprint", "Sprint", "Sprints automatically while you walk forward, within vanilla's sprint rules.", ModuleCategory.MOVEMENT, false, NO_KEY);
	}

	@Override
	public Tier tier() {
		return Tier.AUTOMATION;
	}

	@Override
	protected void onTick() {
		Minecraft client = Minecraft.getInstance();
		LocalPlayer player = client.player;
		boolean canSprint = client.options.keyUp.isDown() && !player.isShiftKeyDown() && !player.isUsingItem() && !player.horizontalCollision
				&& (player.getFoodData().getFoodLevel() > 6 || player.getAbilities().mayfly) && !player.isInWater();
		if (canSprint && !player.isSprinting()) {
			player.setSprinting(true);
		}
	}
}
