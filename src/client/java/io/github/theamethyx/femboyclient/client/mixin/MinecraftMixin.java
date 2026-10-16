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
 * are unaffected. The attack click is also offered to Auto Attribute Swap's spear parts first (there is no click event).
 */
@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
	@Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
	private void femboyclient$noAttackInFreeCamera(CallbackInfoReturnable<Boolean> cir) {
		if (FreeCameraModule.detached) {
			cir.setReturnValue(false);
			return;
		}

		// Aim Trainer: a click while training is a shot at the training targets, never an attack.
		if (io.github.theamethyx.femboyclient.client.modules.AimTrainerModule.onAttackClick()) {
			cir.setReturnValue(false);
			return;
		}

		// Auto Criticals (sandbox only) postpones a charged hit on the ground to the fall of a jump.
		if (io.github.theamethyx.femboyclient.client.modules.AutoCriticalsModule.onAttackClick()) {
			cir.setReturnValue(false);
			return;
		}

		// Auto Attribute Swap's spear parts: a jab at a target beyond normal reach, or a lunge click while holding food or
		// wind charges. When one of them handles the click, vanilla's own swing or miss for it is skipped.
		io.github.theamethyx.femboyclient.client.FemboyClient femboy = io.github.theamethyx.femboyclient.client.FemboyClient.get();
		var swap = femboy == null ? null : femboy.modules().get(io.github.theamethyx.femboyclient.client.modules.AutoAttributeSwapModule.class);
		if (swap != null && swap.isRunning()) {
			boolean[] handled = {false};
			femboy.modules().runGuarded(swap, io.github.theamethyx.femboyclient.core.module.ModuleManager.Phase.TICK, () -> handled[0] = swap.onAttackClick());
			if (handled[0]) {
				cir.setReturnValue(false);
			}
		}
	}

	/** Entity Outlines: vanilla's glowing outline for the entity groups the module chooses. */
	@Inject(method = "shouldEntityAppearGlowing", at = @At("RETURN"), cancellable = true)
	private void femboyclient$outlines(net.minecraft.world.entity.Entity entity, CallbackInfoReturnable<Boolean> cir) {
		if (!cir.getReturnValueZ() && io.github.theamethyx.femboyclient.client.modules.EntityOutlinesModule.glows(entity)) {
			cir.setReturnValue(true);
		}
	}

	@Inject(method = "continueAttack", at = @At("HEAD"), cancellable = true)
	private void femboyclient$noMiningInFreeCamera(boolean attacking, CallbackInfo ci) {
		if (FreeCameraModule.detached || io.github.theamethyx.femboyclient.client.modules.AimTrainerModule.active()) {
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
