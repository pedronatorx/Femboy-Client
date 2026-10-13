package io.github.theamethyx.nova.client.render;

import org.joml.Vector3f;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;

import io.github.theamethyx.nova.client.NovaClient;

/**
 * World-space drawing for modules: box outlines, lines and filled boxes, either depth-tested
 * (hidden behind blocks) or drawn through walls. Geometry is collected while modules render and
 * drawn in one pass per pipeline at the end of the frame.
 *
 * <p>Must only be used on the render thread between {@link #beginFrame} and {@link #endFrame}.
 */
public final class RenderingUtilities implements AutoCloseable {
	private static final RenderPipeline LINES = RenderPipelines.register(RenderPipeline.builder(RenderPipelines.LINES_SNIPPET)
			.withLocation(Identifier.fromNamespaceAndPath(NovaClient.MOD_ID, "pipeline/lines"))
			.build());
	private static final RenderPipeline LINES_THROUGH_WALLS = RenderPipelines.register(RenderPipeline.builder(RenderPipelines.LINES_SNIPPET)
			.withLocation(Identifier.fromNamespaceAndPath(NovaClient.MOD_ID, "pipeline/lines_through_walls"))
			.withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
			.withDepthWrite(false)
			.build());
	private static final RenderPipeline FILL = RenderPipelines.register(RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
			.withLocation(Identifier.fromNamespaceAndPath(NovaClient.MOD_ID, "pipeline/filled"))
			.withDepthWrite(false)
			.build());
	private static final RenderPipeline FILL_THROUGH_WALLS = RenderPipelines.register(RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
			.withLocation(Identifier.fromNamespaceAndPath(NovaClient.MOD_ID, "pipeline/filled_through_walls"))
			.withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
			.withDepthWrite(false)
			.build());

	private final ImmediateBatch lines = new ImmediateBatch(LINES, "lines");
	private final ImmediateBatch linesThroughWalls = new ImmediateBatch(LINES_THROUGH_WALLS, "lines through walls");
	private final ImmediateBatch fill = new ImmediateBatch(FILL, "filled boxes");
	private final ImmediateBatch fillThroughWalls = new ImmediateBatch(FILL_THROUGH_WALLS, "filled boxes through walls");

	private final PoseStack poseStack = new PoseStack();
	private Vec3 camera = Vec3.ZERO;
	private float partialTick;
	private float lineWidth = 2;
	private boolean active;

	public void beginFrame(WorldRenderContext context, float partialTick) {
		camera = context.worldState().cameraRenderState.pos;
		this.partialTick = partialTick;
		poseStack.setIdentity();
		poseStack.last().pose().set(context.matrices().last().pose());
		poseStack.last().normal().set(context.matrices().last().normal());
		// Vertices are made camera-relative in double precision (see rel*), so no translation here.
		lineWidth = Minecraft.getInstance().getWindow().getAppropriateLineWidth();
		active = true;
	}

	public void endFrame() {
		active = false;
		fill.draw();
		lines.draw();
		fillThroughWalls.draw();
		linesThroughWalls.draw();
	}

	/** Camera position in world coordinates for the current frame. */
	public Vec3 camera() {
		return camera;
	}

	public float partialTick() {
		return partialTick;
	}

	/** Outline of a box. {@code argb} includes alpha; {@code width} multiplies the default line width. */
	public void boxOutline(AABB box, int argb, float width, boolean throughWalls) {
		requireActive();
		ImmediateBatch batch = throughWalls ? linesThroughWalls : lines;
		BufferBuilder buffer = batch.buffer();
		double x0 = box.minX, y0 = box.minY, z0 = box.minZ, x1 = box.maxX, y1 = box.maxY, z1 = box.maxZ;
		float w = lineWidth * width;
		// Bottom, top, then the four vertical edges.
		edge(buffer, x0, y0, z0, x1, y0, z0, argb, w); edge(buffer, x1, y0, z0, x1, y0, z1, argb, w);
		edge(buffer, x1, y0, z1, x0, y0, z1, argb, w); edge(buffer, x0, y0, z1, x0, y0, z0, argb, w);
		edge(buffer, x0, y1, z0, x1, y1, z0, argb, w); edge(buffer, x1, y1, z0, x1, y1, z1, argb, w);
		edge(buffer, x1, y1, z1, x0, y1, z1, argb, w); edge(buffer, x0, y1, z1, x0, y1, z0, argb, w);
		edge(buffer, x0, y0, z0, x0, y1, z0, argb, w); edge(buffer, x1, y0, z0, x1, y1, z0, argb, w);
		edge(buffer, x1, y0, z1, x1, y1, z1, argb, w); edge(buffer, x0, y0, z1, x0, y1, z1, argb, w);
		batch.countVertices(24);
	}

	public void line(Vec3 from, Vec3 to, int argb, float width, boolean throughWalls) {
		requireActive();
		ImmediateBatch batch = throughWalls ? linesThroughWalls : lines;
		edge(batch.buffer(), from.x, from.y, from.z, to.x, to.y, to.z, argb, lineWidth * width);
		batch.countVertices(2);
	}

	/** Translucent filled box; use a low alpha so blocks behind remain visible. */
	public void filledBox(AABB box, int argb, boolean throughWalls) {
		requireActive();
		ImmediateBatch batch = throughWalls ? fillThroughWalls : fill;
		BufferBuilder b = batch.buffer();
		PoseStack.Pose pose = poseStack.last();
		float x0 = (float) (box.minX - camera.x), y0 = (float) (box.minY - camera.y), z0 = (float) (box.minZ - camera.z);
		float x1 = (float) (box.maxX - camera.x), y1 = (float) (box.maxY - camera.y), z1 = (float) (box.maxZ - camera.z);
		quad(b, pose, argb, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1);
		quad(b, pose, argb, x1, y0, z0, x0, y0, z0, x0, y1, z0, x1, y1, z0);
		quad(b, pose, argb, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0);
		quad(b, pose, argb, x1, y0, z1, x1, y0, z0, x1, y1, z0, x1, y1, z1);
		quad(b, pose, argb, x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z0);
		quad(b, pose, argb, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1);
		batch.countVertices(24);
	}

	private static void quad(BufferBuilder b, PoseStack.Pose pose, int argb, float... v) {
		for (int i = 0; i < 12; i += 3) {
			b.addVertex(pose, v[i], v[i + 1], v[i + 2]).setColor(argb);
		}
	}

	private void edge(BufferBuilder buffer, double x0, double y0, double z0, double x1, double y1, double z1, int argb, float width) {
		PoseStack.Pose pose = poseStack.last();
		Vector3f normal = new Vector3f((float) (x1 - x0), (float) (y1 - y0), (float) (z1 - z0));
		if (normal.lengthSquared() > 0) {
			normal.normalize();
		}

		buffer.addVertex(pose, (float) (x0 - camera.x), (float) (y0 - camera.y), (float) (z0 - camera.z)).setColor(argb).setNormal(pose, normal).setLineWidth(width);
		buffer.addVertex(pose, (float) (x1 - camera.x), (float) (y1 - camera.y), (float) (z1 - camera.z)).setColor(argb).setNormal(pose, normal).setLineWidth(width);
	}

	private void requireActive() {
		if (!active) {
			throw new IllegalStateException("World rendering is only allowed during the world render phase");
		}
	}

	@Override
	public void close() {
		lines.close();
		linesThroughWalls.close();
		fill.close();
		fillThroughWalls.close();
	}
}
