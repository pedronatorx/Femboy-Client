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
		// Auto StunSlam picks the axe and the mace itself; swapping on top of that would hit with the wrong item.
		return module != null && module.isRunning() && !io.github.theamethyx.femboyclient.client.modules.AutoStunSlamModule.sequenceActive ? module : null;
	}

	@Inject(method = "attack", at = @At("HEAD"))
	private void femboyclient$swapBeforeAttack(Player player, Entity target, CallbackInfo ci) {
		if (FemboyClient.get() != null && player instanceof net.minecraft.client.player.LocalPlayer local) {
			// Combat statistics (reach, combo) for the combat HUD modules; only reads state.
			FemboyClient.get().combat().onAttack(local, target);
			// Auto Weapon Switch (sandbox only) selects the strongest weapon before Auto Attribute Swap looks at the hand.
			io.github.theamethyx.femboyclient.client.modules.AutoWeaponSwitchModule.beforeAttack(local, target);
		}

		AutoAttributeSwapModule module = femboyclient$swap();
		if (module != null) {
			FemboyClient.get().modules().runGuarded(module, io.github.theamethyx.femboyclient.core.module.ModuleManager.Phase.TICK,
					() -> module.beforeAttack(player, target));
		}
	}

	@Inject(method = "attack", at = @At("RETURN"))
	private void femboyclient$swapBackAfterAttack(Player player, Entity target, CallbackInfo ci) {
		if (player instanceof net.minecraft.client.player.LocalPlayer local) {
			io.github.theamethyx.femboyclient.client.modules.WTapModule.afterAttack(local);
		}

		AutoAttributeSwapModule module = femboyclient$swap();
		if (module != null) {
			MultiPlayerGameModeAccessor self = (MultiPlayerGameModeAccessor) this;
			FemboyClient.get().modules().runGuarded(module, io.github.theamethyx.femboyclient.core.module.ModuleManager.Phase.TICK,
					() -> module.afterAttack(player, self::femboyclient$ensureHasSentCarriedItem));
		}
	}

	/** Auto Tool: picks the fastest hotbar tool when mining starts or continues (read-only otherwise). */
	@Inject(method = {"startDestroyBlock", "continueDestroyBlock"}, at = @At("HEAD"))
	private void femboyclient$autoTool(net.minecraft.core.BlockPos pos, net.minecraft.core.Direction direction,
			org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Boolean> cir) {
		io.github.theamethyx.femboyclient.client.modules.AutoToolModule.onMining(pos);
	}
}
