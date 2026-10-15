package io.github.theamethyx.femboyclient.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Slice;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;

import net.minecraft.client.renderer.LightTexture;

import io.github.theamethyx.femboyclient.client.modules.FullbrightModule;

/**
 * Why this mixin exists: Fullbright needs a brighter lightmap without touching the player's video
 * settings (writing the gamma option would be saved to options.txt and survive a crash). The lightmap
 * update reads {@code options.gamma().get().floatValue()} once; this replaces only that value, and only
 * while the Fullbright module runs.
 */
@Mixin(LightTexture.class)
public abstract class LightTextureMixin {
	@ModifyExpressionValue(method = "updateLightTexture", at = @At(value = "INVOKE", target = "Ljava/lang/Double;floatValue()F", ordinal = 0),
			slice = @Slice(from = @At(value = "INVOKE", target = "Lnet/minecraft/client/Options;gamma()Lnet/minecraft/client/OptionInstance;")))
	private float femboyclient$fullbright(float gamma) {
		return FullbrightModule.brightness(gamma);
	}
}
