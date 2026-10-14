package io.github.theamethyx.femboyclient.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.Minecraft;

import io.github.theamethyx.femboyclient.client.modules.FreeCameraModule;

/**
 * Why this mixin exists: vanilla picks the block or entity under the crosshair from the camera entity.
 * With the free camera that is far from the player, so attacking, using an item or pick-block would act
 * on whatever is in front of the camera, which the server rejects as out of reach. There is no event to
 * cancel these clicks, so they are skipped while the camera is detached. Hotbar, inventory and chat keys
 * are unaffected.
 */
@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
	@Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
	private void femboyclient$noAttackInFreeCamera(CallbackInfoReturnable<Boolean> cir) {
		if (FreeCameraModule.detached) {
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "continueAttack", at = @At("HEAD"), cancellable = true)
	private void femboyclient$noMiningInFreeCamera(boolean attacking, CallbackInfo ci) {
		if (FreeCameraModule.detached) {
			ci.cancel();
		}
	}

	@Inject(method = "startUseItem", at = @At("HEAD"), cancellable = true)
	private void femboyclient$noUseInFreeCamera(CallbackInfo ci) {
		if (FreeCameraModule.detached) {
			ci.cancel();
		}
	}

	@Inject(method = "pickBlock", at = @At("HEAD"), cancellable = true)
	private void femboyclient$noPickInFreeCamera(CallbackInfo ci) {
		if (FreeCameraModule.detached) {
			ci.cancel();
		}
	}
}
