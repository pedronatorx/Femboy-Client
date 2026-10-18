package io.github.theamethyx.femboyclient.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemCooldowns;

import io.github.theamethyx.femboyclient.client.modules.CooldownsModule;

/** Remembers how long each cooldown lasts, so the Cooldowns HUD can show seconds left (the game only exposes a fraction). */
@Mixin(ItemCooldowns.class)
public abstract class ItemCooldownsMixin {
	@Inject(method = "addCooldown(Lnet/minecraft/resources/Identifier;I)V", at = @At("HEAD"))
	private void femboyclient$rememberDuration(Identifier group, int ticks, CallbackInfo ci) {
		CooldownsModule.started(group, ticks);
	}
}
