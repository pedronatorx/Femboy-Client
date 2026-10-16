package io.github.theamethyx.femboyclient.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.player.LocalPlayer;

import io.github.theamethyx.femboyclient.client.modules.FreeCameraModule;

/**
 * Why this mixin exists: {@code LocalPlayer.isControlledCamera()} is false while another entity is the
 * camera, and vanilla then skips sending position and rotation updates ({@code sendPosition}) and stops
 * applying the player's movement input. With the free camera that would leave the server without updates
 * for the whole flight. Treating the player as still controlled keeps the normal updates going; its input
 * is an empty one while the camera is detached, so it stands still. No Slow also uses this mixin: vanilla slows
 * input to 20% while using an item in {@code itemUseSpeedMultiplier}, with no event to change it.
 */
@Mixin(LocalPlayer.class)
public abstract class LocalPlayerMixin {
	@Inject(method = "isControlledCamera", at = @At("HEAD"), cancellable = true)
	private void femboyclient$stayControlledInFreeCamera(CallbackInfoReturnable<Boolean> cir) {
		if (FreeCameraModule.detached) {
			cir.setReturnValue(true);
		}
	}

	/** No Slow: full input speed while using an item (vanilla multiplies by 0.2 in {@code itemUseSpeedMultiplier}). */
	@org.spongepowered.asm.mixin.injection.Inject(method = "itemUseSpeedMultiplier", at = @org.spongepowered.asm.mixin.injection.At("RETURN"), cancellable = true)
	private void femboyclient$noSlow(org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Float> cir) {
		if (io.github.theamethyx.femboyclient.client.modules.NoSlowModule.active()) {
			cir.setReturnValue(1.0F);
		}
	}
}
