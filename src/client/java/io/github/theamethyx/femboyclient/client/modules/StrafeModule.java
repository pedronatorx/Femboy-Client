package io.github.theamethyx.femboyclient.client.modules;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;

import io.github.theamethyx.femboyclient.client.combat.MoveInput;
import io.github.theamethyx.femboyclient.core.module.Module;
import io.github.theamethyx.femboyclient.core.module.ModuleCategory;

/** In the air, points your current horizontal speed where your movement keys point, for sharp turns mid-jump. Sandbox only. */
public final class StrafeModule extends Module {
	public StrafeModule() {
		super("strafe", "Strafe Assist", "Mid-air, turns your horizontal speed toward your movement keys.", ModuleCategory.MOVEMENT, false, NO_KEY);
	}

	@Override
	public boolean testEnvironmentOnly() {
		return false;
	}

	@Override
	protected void onTick() {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player.onGround() || player.isFallFlying() || player.isInWater() || player.getAbilities().flying) {
			return;
		}

		Vec3 direction = MoveInput.direction(player);
		Vec3 motion = player.getDeltaMovement();
		double speed = motion.horizontalDistance();
		if (direction.lengthSqr() > 0 && speed > 0.01) {
			player.setDeltaMovement(direction.x * speed, motion.y, direction.z * speed);
		}
	}
}
