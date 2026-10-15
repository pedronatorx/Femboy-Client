package io.github.theamethyx.femboyclient.client.combat;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** Turning the player's real view toward a point. The view turns visibly; nothing is hidden from you or the server. */
public final class Rotations {
	private Rotations() {
	}

	/** Yaw and pitch (degrees) that look from {@code from} at {@code to}. */
	public static float[] anglesTo(Vec3 from, Vec3 to) {
		double dx = to.x - from.x;
		double dy = to.y - from.y;
		double dz = to.z - from.z;
		float yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90;
		float pitch = (float) -(Mth.atan2(dy, Math.sqrt(dx * dx + dz * dz)) * Mth.RAD_TO_DEG);
		return new float[] {Mth.wrapDegrees(yaw), Mth.clamp(pitch, -90, 90)};
	}

	/** Angle in degrees between where the player looks and the direction to {@code point}. */
	public static double angleTo(LocalPlayer player, Vec3 point) {
		Vec3 direction = point.subtract(player.getEyePosition()).normalize();
		double dot = Mth.clamp(player.getLookAngle().dot(direction), -1, 1);
		return Math.toDegrees(Math.acos(dot));
	}

	/**
	 * Turns the view toward {@code point} by at most {@code maxStep} degrees per axis.
	 *
	 * @return the remaining angle after turning
	 */
	public static double turnToward(LocalPlayer player, Vec3 point, float maxStep, boolean vertical) {
		float[] target = anglesTo(player.getEyePosition(), point);
		float yaw = player.getYRot() + Mth.clamp(Mth.wrapDegrees(target[0] - player.getYRot()), -maxStep, maxStep);
		float pitch = vertical ? player.getXRot() + Mth.clamp(target[1] - player.getXRot(), -maxStep, maxStep) : player.getXRot();
		player.setYRot(yaw);
		player.setYHeadRot(yaw);
		player.setXRot(Mth.clamp(pitch, -90, 90));
		return angleTo(player, point);
	}
}
