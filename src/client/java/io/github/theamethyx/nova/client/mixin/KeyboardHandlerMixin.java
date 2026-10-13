package io.github.theamethyx.nova.client.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;

import io.github.theamethyx.nova.client.NovaClient;

/**
 * Why this mixin exists: module key binds must react to every key press while playing. Fabric API
 * offers key events only for open screens, and polling key state once per tick (50 ms) misses
 * short taps. Injecting at the head of {@code KeyboardHandler.keyPress} observes each press exactly
 * once without changing or cancelling vanilla handling.
 */
@Mixin(KeyboardHandler.class)
public abstract class KeyboardHandlerMixin {
	private static final int GLFW_PRESS = 1;

	@Shadow
	@Final
	private Minecraft minecraft;

	@Inject(method = "keyPress", at = @At("HEAD"))
	private void nova$onKeyPress(long window, int action, KeyEvent event, CallbackInfo ci) {
		if (action != GLFW_PRESS || minecraft.screen != null || window != minecraft.getWindow().handle()) {
			return;
		}

		NovaClient nova = NovaClient.get();
		if (nova != null) {
			nova.onKeyPressedInWorld(event.key());
		}
	}
}
