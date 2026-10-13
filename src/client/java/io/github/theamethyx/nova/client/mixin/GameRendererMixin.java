package io.github.theamethyx.nova.client.mixin;

import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.renderer.GameRenderer;

import io.github.theamethyx.nova.client.modules.FreeCameraModule;

/**
 * Why this mixin exists: while the Free Camera module flies a detached camera, vanilla still draws
 * the first-person hand (it only skips it when sleeping, in spectator mode or with the HUD hidden),
 * which looks like a floating arm. There is no API to suppress it, so the hand pass is skipped while
 * the free camera is active. Nothing else about rendering is changed.
 */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
	@Inject(method = "renderItemInHand", at = @At("HEAD"), cancellable = true)
	private void nova$hideHandInFreeCamera(float partialTick, boolean sleeping, Matrix4f projection, CallbackInfo ci) {
		if (FreeCameraModule.hideHand) {
			ci.cancel();
		}
	}
}
