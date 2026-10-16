package io.github.theamethyx.femboyclient.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import net.minecraft.client.MouseHandler;
import net.minecraft.client.player.LocalPlayer;

import io.github.theamethyx.femboyclient.client.FemboyClient;
import io.github.theamethyx.femboyclient.client.modules.FreeCameraModule;

/**
 * Why this mixin exists: with the free camera, the mouse should turn the camera and leave the player's
 * head where it was. Vanilla always applies mouse movement to the player ({@code turnPlayer} calls
 * {@code player.turn}); there is no event to redirect it. While the camera is detached the movement goes
 * to the camera instead. Sensitivity, smoothing and inverted axes are already applied by vanilla.
 */
@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {
	@org.spongepowered.asm.mixin.Shadow
	@org.spongepowered.asm.mixin.Final
	private net.minecraft.client.Minecraft minecraft;

	/**
	 * Counts mouse presses for the clicks-per-second display. Fabric API has no mouse event outside screens;
	 * this only observes the press and never changes or cancels vanilla handling.
	 */
	@org.spongepowered.asm.mixin.injection.Inject(method = "onButton", at = @At("HEAD"))
	private void femboyclient$countClick(long window, net.minecraft.client.input.MouseButtonInfo info, int action,
			org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
		if (action == 1 && minecraft.screen == null && FemboyClient.get() != null) {
			FemboyClient.get().combat().onClick(info.button());
		}
	}

	@WrapOperation(method = "turnPlayer", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;turn(DD)V"))
	private void femboyclient$turnFreeCamera(LocalPlayer player, double dx, double dy, Operation<Void> original) {
		FreeCameraModule camera = FreeCameraModule.detached ? FemboyClient.get().modules().get(FreeCameraModule.class) : null;
		if (camera != null && camera.isActive()) {
			camera.turn(dx, dy);
		} else {
			double divisor = io.github.theamethyx.femboyclient.client.modules.ZoomModule.mouseDivisor();
			original.call(player, dx / divisor, dy / divisor);
		}
	}

	/** Zoom: while zooming, the scroll wheel changes the zoom instead of the selected hotbar slot. */
	@org.spongepowered.asm.mixin.injection.Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
	private void femboyclient$zoomScroll(long window, double horizontal, double vertical,
			org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
		if (minecraft.screen == null && io.github.theamethyx.femboyclient.client.modules.ZoomModule.onScroll(vertical)) {
			ci.cancel();
		}
	}
}
