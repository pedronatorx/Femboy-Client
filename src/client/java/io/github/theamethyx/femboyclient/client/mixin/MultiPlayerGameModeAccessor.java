package io.github.theamethyx.femboyclient.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import net.minecraft.client.multiplayer.MultiPlayerGameMode;

/** Exposes vanilla's private "send the selected hotbar slot if it changed" for Auto Attribute Swap. */
@Mixin(MultiPlayerGameMode.class)
public interface MultiPlayerGameModeAccessor {
	@Invoker("ensureHasSentCarriedItem")
	void femboyclient$ensureHasSentCarriedItem();
}
