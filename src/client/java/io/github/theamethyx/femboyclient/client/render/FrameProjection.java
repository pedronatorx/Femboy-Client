package io.github.theamethyx.femboyclient.client.render;

import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector4f;

import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The camera of the frame being drawn, exactly as the world was rendered with it: view rotation and projection including
 * view bobbing, hurt tilt, nausea and the frame's field of view (captured by {@code LevelRendererMixin}), plus the
 * frustum vanilla culls with. Labels and target lines project through this so they sit exactly on the world, at any
 * window size and GUI scale.
 *
 * <p>Render thread only. Before the first world frame (or without a world) {@link #valid()} is false.
 */
public final class FrameProjection {
	private static final Matrix4f VIEW_PROJECTION = new Matrix4f();
	private static final Matrix4f INVERSE = new Matrix4f();
	private static final Vector4f CLIP = new Vector4f();
	private static final Vector3f NEAR = new Vector3f();
	private static final Vector3f FAR = new Vector3f();
	private static double cameraX;
	private static double cameraY;
	private static double cameraZ;
	private static boolean valid;
	private static Frustum frustum;
	private static long frames;

	private FrameProjection() {
	}

	/** Called at the start of each world frame with the matrices the world is drawn with. */
	public static void capture(Vec3 camera, Matrix4fc view, Matrix4fc projection) {
		VIEW_PROJECTION.set(projection).mul(view);
		INVERSE.set(VIEW_PROJECTION).invert();
		cameraX = camera.x;
		cameraY = camera.y;
		cameraZ = camera.z;
		valid = Float.isFinite(INVERSE.m00());
		frames++;
	}

	/** The frustum of the current frame (from the Fabric extraction event); boxes outside it are skipped. */
	public static void captureFrustum(Frustum current) {
		frustum = current;
	}

	public static boolean valid() {
		return valid;
	}

	/** Number of captured frames (for tests). */
	public static long frames() {
		return frames;
	}

	public static Vec3 camera() {
		return new Vec3(cameraX, cameraY, cameraZ);
	}

	/** Whether any part of {@code box} can be on screen this frame; true when no frustum is known. */
	public static boolean inView(AABB box) {
		Frustum current = frustum;
		return current == null || current.isVisible(box);
	}

	/**
	 * Projects a world point. {@code out} receives the GUI x and y (for a screen {@code width} x {@code height} GUI pixels)
	 * and the clip-space w, whose sign says whether the point is in front of the camera.
	 *
	 * @return whether the point is in front of the camera and inside the screen
	 */
	public static boolean project(double x, double y, double z, int width, int height, float[] out) {
		CLIP.set((float) (x - cameraX), (float) (y - cameraY), (float) (z - cameraZ), 1f);
		VIEW_PROJECTION.transform(CLIP);
		float w = CLIP.w;
		out[2] = w;
		if (w <= 1.0E-4f) {
			// Behind the camera: keep the on-screen direction (x and y without the flip a negative w would cause).
			out[0] = width / 2f + CLIP.x * width;
			out[1] = height / 2f - CLIP.y * height;
			return false;
		}

		float ndcX = CLIP.x / w;
		float ndcY = CLIP.y / w;
		out[0] = (ndcX + 1) / 2 * width;
		out[1] = (1 - ndcY) / 2 * height;
		return Math.abs(ndcX) <= 1 && Math.abs(ndcY) <= 1;
	}

	/**
	 * The world point {@code distance} blocks along the ray through the middle of the screen. A line drawn from here
	 * starts exactly at the crosshair, also while the view bobs.
	 */
	public static Vec3 crosshairOrigin(double distance) {
		INVERSE.transformProject(0, 0, -1, NEAR);
		INVERSE.transformProject(0, 0, 0.5f, FAR);
		FAR.sub(NEAR).normalize();
		return new Vec3(cameraX + NEAR.x + FAR.x * distance, cameraY + NEAR.y + FAR.y * distance, cameraZ + NEAR.z + FAR.z * distance);
	}
}
