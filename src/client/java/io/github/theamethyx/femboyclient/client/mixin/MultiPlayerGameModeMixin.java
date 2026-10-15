package io.github.theamethyx.femboyclient.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import io.github.theamethyx.femboyclient.client.FemboyClient;
import io.github.theamethyx.femboyclient.client.modules.AutoAttributeSwapModule;

/**
 * Why this mixin exists: Auto Attribute Swap must change the selected hotbar slot exactly around one
 * attack. {@code attack} sends the selected slot and then the attack packet; there is no event between
 * deciding to attack and sending it. Before: the module may select another slot (vanilla then sends it).
 * After: the module selects the original slot again and sends it immediately, in the same tick.
 * The head injection also records the attack for the combat statistics (no game state is changed).
 */
@Mixin(MultiPlayerGameMode.class)
public abstract class MultiPlayerGameModeMixin {
	private static AutoAttributeSwapModule femboyclient$swap() {
		FemboyClient femboy = FemboyClient.get();
		AutoAttributeSwapModule module = femboy == null ? null : femboy.modules().get(AutoAttributeSwapModule.class);
		return module != null && module.isRunning() ? module : null;
	}

	@Inject(method = "attack", at = @At("HEAD"))
	private void femboyclient$swapBeforeAttack(Player player, Entity target, CallbackInfo ci) {
		if (FemboyClient.get() != null && player instanceof net.minecraft.client.player.LocalPlayer local) {
			// Combat statistics (reach, combo) for the combat HUD modules; only reads state.
			FemboyClient.get().combat().onAttack(local, target);
		}

		AutoAttributeSwapModule module = femboyclient$swap();
		if (module != null) {
			FemboyClient.get().modules().runGuarded(module, io.github.theamethyx.femboyclient.core.module.ModuleManager.Phase.TICK,
					() -> module.beforeAttack(player, target));
		}
	}

	@Inject(method = "attack", at = @At("RETURN"))
	private void femboyclient$swapBackAfterAttack(Player player, Entity target, CallbackInfo ci) {
		AutoAttributeSwapModule module = femboyclient$swap();
		if (module != null) {
			MultiPlayerGameModeAccessor self = (MultiPlayerGameModeAccessor) this;
			FemboyClient.get().modules().runGuarded(module, io.github.theamethyx.femboyclient.core.module.ModuleManager.Phase.TICK,
					() -> module.afterAttack(player, self::femboyclient$ensureHasSentCarriedItem));
		}
	}
}
