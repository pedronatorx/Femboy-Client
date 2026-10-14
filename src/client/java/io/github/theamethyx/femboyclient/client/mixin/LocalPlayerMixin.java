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
 * is an empty one while the camera is detached, so it stands still. Nothing else is changed.
 */
@Mixin(LocalPlayer.class)
public abstract class LocalPlayerMixin {
	@Inject(method = "isControlledCamera", at = @At("HEAD"), cancellable = true)
	private void femboyclient$stayControlledInFreeCamera(CallbackInfoReturnable<Boolean> cir) {
		if (FreeCameraModule.detached) {
			cir.setReturnValue(true);
		}
	}
}
