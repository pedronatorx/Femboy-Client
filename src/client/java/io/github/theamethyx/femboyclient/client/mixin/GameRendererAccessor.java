package io.github.theamethyx.femboyclient.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;

/** Read-only access to the field of view, so the in-game tests can measure Zoom. */
@Mixin(GameRenderer.class)
public interface GameRendererAccessor {
	@Invoker("getFov")
	float femboyclient$getFov(Camera camera, float partialTick, boolean useFovSetting);
}
