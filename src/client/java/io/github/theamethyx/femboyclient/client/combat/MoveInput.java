package io.github.theamethyx.femboyclient.client.combat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** The horizontal direction your movement keys point to, relative to where you look; zero when no key is held. */
public final class MoveInput {
	private MoveInput() {
	}

	public static Vec3 direction(LocalPlayer player) {
		Options o = Minecraft.getInstance().options;
		double forward = (o.keyUp.isDown() ? 1 : 0) - (o.keyDown.isDown() ? 1 : 0);
		double strafe = (o.keyLeft.isDown() ? 1 : 0) - (o.keyRight.isDown() ? 1 : 0);
		double length = Math.sqrt(forward * forward + strafe * strafe);
		if (length == 0) {
			return Vec3.ZERO;
		}

		float yaw = player.getYRot() * Mth.DEG_TO_RAD;
		forward /= length;
		strafe /= length;
		return new Vec3(-Mth.sin(yaw) * forward + Mth.cos(yaw) * strafe, 0, Mth.cos(yaw) * forward + Mth.sin(yaw) * strafe);
	}
}
