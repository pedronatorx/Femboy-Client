package io.github.theamethyx.femboyclient.client.mixin;

import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.renderer.GameRenderer;

import io.github.theamethyx.femboyclient.client.modules.FreeCameraModule;

/**
 * Why this mixin exists: while the Free Camera module flies a detached camera, vanilla still draws
 * the first-person hand (it only skips it when sleeping, in spectator mode or with the HUD hidden),
 * which looks like a floating arm. There is no API to suppress it, so the hand pass is skipped while
 * the free camera is active. The field of view is also divided while Zoom is held (there is no FOV event).
 */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
	@Inject(method = "renderItemInHand", at = @At("HEAD"), cancellable = true)
	private void femboyclient$hideHandInFreeCamera(float partialTick, boolean sleeping, Matrix4f projection, CallbackInfo ci) {
		if (FreeCameraModule.detached) {
			ci.cancel();
		}
	}

	/** Zoom: divides the world field of view (not the hand's) while the zoom key is held. */
	@Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
	private void femboyclient$zoom(net.minecraft.client.Camera camera, float partialTick, boolean useFovSetting,
			org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Float> cir) {
		float divisor = io.github.theamethyx.femboyclient.client.modules.ZoomModule.fovDivisor();
		if (useFovSetting && divisor > 1) {
			cir.setReturnValue(cir.getReturnValue() / divisor);
		}
	}
}
