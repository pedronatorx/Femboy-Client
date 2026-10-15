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
	@WrapOperation(method = "turnPlayer", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;turn(DD)V"))
	private void femboyclient$turnFreeCamera(LocalPlayer player, double dx, double dy, Operation<Void> original) {
		FreeCameraModule camera = FreeCameraModule.detached ? FemboyClient.get().modules().get(FreeCameraModule.class) : null;
		if (camera != null && camera.isActive()) {
			camera.turn(dx, dy);
		} else {
			original.call(player, dx, dy);
		}
	}
}
