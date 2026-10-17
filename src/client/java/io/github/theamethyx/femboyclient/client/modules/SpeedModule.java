package io.github.theamethyx.femboyclient.client.modules;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;

import io.github.theamethyx.femboyclient.client.combat.MoveInput;
import io.github.theamethyx.femboyclient.core.module.Module;
import io.github.theamethyx.femboyclient.core.module.ModuleCategory;
import io.github.theamethyx.femboyclient.core.setting.BooleanSetting;
import io.github.theamethyx.femboyclient.core.setting.NumberSetting;

/** Moves you at a set ground speed in the direction of your movement keys. Sandbox only. */
public final class SpeedModule extends Module {
	private final NumberSetting speed = settings().add(new NumberSetting("speed", "Speed", "Ground speed (sprinting is about 0.28).", 0.5, 0.2, 2, 0.05, "blocks/tick"));
	private final BooleanSetting groundOnly = settings().add(new BooleanSetting("ground_only", "Only on the ground", "Do not change your speed in the air.", true));

	public SpeedModule() {
		super("speed", "Speed", "Moves you at a set speed in the direction of your movement keys.", ModuleCategory.MOVEMENT, false, NO_KEY);
	}

	@Override
	public boolean testEnvironmentOnly() {
		return false;
	}

	@Override
	protected void onTick() {
		LocalPlayer player = Minecraft.getInstance().player;
		if ((groundOnly.get() && !player.onGround()) || player.isFallFlying() || player.isInWater() || player.isPassenger()) {
			return;
		}

		Vec3 direction = MoveInput.direction(player);
		if (direction.lengthSqr() > 0) {
			player.setDeltaMovement(direction.x * speed.get(), player.getDeltaMovement().y, direction.z * speed.get());
		}
	}
}
