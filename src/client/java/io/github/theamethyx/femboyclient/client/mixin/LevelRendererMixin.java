package io.github.theamethyx.femboyclient.client.mixin;

import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;

import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.LevelRenderer;

import io.github.theamethyx.femboyclient.client.render.FrameProjection;

/**
 * Why this mixin exists: labels and target lines must land exactly where the world is drawn. Vanilla's
 * {@code GameRenderer.projectPointToScreen} leaves out view bobbing, the hurt tilt and the frame's field of view, so
 * labels drifted from boxes while walking. The real view rotation and projection of each frame are only available as
 * the arguments of this call (no Fabric event exposes the projection), so they are copied here, read-only.
 */
@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {
	@Inject(method = "renderLevel", at = @At("HEAD"))
	private void femboyclient$captureProjection(GraphicsResourceAllocator allocator, DeltaTracker deltaTracker, boolean renderBlockOutline, Camera camera,
			Matrix4f view, Matrix4f projection, Matrix4f cullingProjection, GpuBufferSlice fog, Vector4f fogColor, boolean renderSky, CallbackInfo ci) {
		FrameProjection.capture(camera.position(), view, projection);
	}
}
