package io.github.theamethyx.femboyclient.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Player;

import io.github.theamethyx.femboyclient.client.modules.SafeWalkModule;

/**
 * Why this mixin exists: Safe Walk stops you at edges like sneaking. Vanilla decides that in
 * {@code Player.isStayingOnGroundSurface}; there is no event for it. Only your own client-side player is affected
 * (the singleplayer server's players use the same class and are left alone).
 */
@Mixin(Player.class)
public abstract class PlayerMovementMixin {
	@Inject(method = "isStayingOnGroundSurface", at = @At("RETURN"), cancellable = true)
	private void femboyclient$safeWalk(CallbackInfoReturnable<Boolean> cir) {
		if (!cir.getReturnValueZ() && (Object) this instanceof LocalPlayer && SafeWalkModule.active()) {
			cir.setReturnValue(true);
		}
	}
}
