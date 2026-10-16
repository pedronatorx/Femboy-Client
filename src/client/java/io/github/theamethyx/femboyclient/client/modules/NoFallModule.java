package io.github.theamethyx.femboyclient.client.modules;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;

import io.github.theamethyx.femboyclient.core.module.Module;
import io.github.theamethyx.femboyclient.core.module.ModuleCategory;
import io.github.theamethyx.femboyclient.core.setting.NumberSetting;

/**
 * Prevents fall damage by telling the server you touched the ground while your fall is still short (below the 3 safe
 * blocks), so it resets your fall distance without damage. Sandbox only.
 */
public final class NoFallModule extends Module {
	private final NumberSetting threshold = settings().add(new NumberSetting("threshold", "Reset after",
			"Send the ground status after falling this far (must stay below 3 blocks to deal no damage).", 2, 0.5, 2.9, 0.1, "blocks"));

	private int sent;

	public NoFallModule() {
		super("no_fall", "No Fall", "Prevents fall damage by resetting your fall before it becomes damaging.", ModuleCategory.MOVEMENT, false, NO_KEY);
	}

	@Override
	public boolean testEnvironmentOnly() {
		return true;
	}

	public int sent() {
		return sent;
	}

	@Override
	protected void onTick() {
		Minecraft client = Minecraft.getInstance();
		LocalPlayer player = client.player;
		if (player.isFallFlying() || player.getAbilities().flying || client.getConnection() == null) {
			return;
		}

		if (player.fallDistance >= threshold.get() && player.getDeltaMovement().y < 0) {
			client.getConnection().send(new ServerboundMovePlayerPacket.StatusOnly(true, player.horizontalCollision));
			player.resetFallDistance();
			sent++;
		}
	}
}
