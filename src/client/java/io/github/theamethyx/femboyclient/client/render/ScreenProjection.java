package io.github.theamethyx.femboyclient.client.render;

import org.joml.Vector3f;
import org.joml.Vector3fc;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

/** Projects world positions to HUD coordinates, for labels that stay crisp at any distance. */
public final class ScreenProjection {
	private ScreenProjection() {
	}

	/**
	 * @return {@code [x, y]} in GUI pixels, or {@code null} if the point is behind the camera or
	 *     outside the screen
	 */
	public static int[] toScreen(Vec3 world, int screenWidth, int screenHeight) {
		Minecraft client = Minecraft.getInstance();
		Camera camera = client.gameRenderer.getMainCamera();
		Vec3 relative = world.subtract(camera.position());
		Vector3fc forward = camera.forwardVector();
		if (new Vector3f((float) relative.x, (float) relative.y, (float) relative.z).dot(forward) <= 0.05f) {
			return null;
		}

		Vec3 ndc = client.gameRenderer.projectPointToScreen(world);
		if (Math.abs(ndc.x) > 1.2 || Math.abs(ndc.y) > 1.2) {
			return null;
		}

		int x = (int) Math.round((ndc.x + 1) / 2 * screenWidth);
		int y = (int) Math.round((1 - ndc.y) / 2 * screenHeight);
		return new int[] {x, y};
	}
}
