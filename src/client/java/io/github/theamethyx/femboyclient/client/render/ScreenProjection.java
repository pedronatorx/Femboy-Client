package io.github.theamethyx.femboyclient.client.render;

import org.joml.Vector3f;
import org.joml.Vector3fc;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

/**
 * Projects world positions to HUD coordinates, for labels that stay crisp at any distance. Uses the frame's real camera
 * ({@link FrameProjection}) once a world frame was drawn, and vanilla's projection before that.
 */
public final class ScreenProjection {
	private ScreenProjection() {
	}

	/**
	 * @return {@code [x, y]} in GUI pixels, or {@code null} if the point is behind the camera or
	 *     outside the screen
	 */
	public static int[] toScreen(Vec3 world, int screenWidth, int screenHeight) {
		if (FrameProjection.valid()) {
			// The matrices the world was drawn with this frame, so labels stay on their boxes while the view bobs.
			float[] out = new float[3];
			FrameProjection.project(world.x, world.y, world.z, screenWidth, screenHeight, out);
			if (out[2] <= 0.05f || out[0] < -0.1f * screenWidth || out[0] > 1.1f * screenWidth || out[1] < -0.1f * screenHeight
					|| out[1] > 1.1f * screenHeight) {
				return null;
			}

			return new int[] {Math.round(out[0]), Math.round(out[1])};
		}

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
