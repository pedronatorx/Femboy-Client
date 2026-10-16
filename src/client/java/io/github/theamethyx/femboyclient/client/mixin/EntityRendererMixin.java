package io.github.theamethyx.femboyclient.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;

import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;

import io.github.theamethyx.femboyclient.client.modules.EntityOutlinesModule;

/**
 * Why this mixin exists: the outline color of a glowing entity is read from {@code entity.getTeamColor()} while the render
 * state is extracted, and there is no event to change it. Entity Outlines gives each entity group its own color here.
 */
@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin {
	@ModifyExpressionValue(method = "extractRenderState", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getTeamColor()I"))
	private int femboyclient$outlineColor(int original, @Local(argsOnly = true) Entity entity) {
		return EntityOutlinesModule.outlineColor(entity, original);
	}
}
