package io.github.theamethyx.femboyclient.client.mixin;

import java.util.Optional;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundExplodePacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import io.github.theamethyx.femboyclient.client.modules.VelocityModule;

/**
 * Why this mixin exists: Velocity scales the knockback the server sends to your own player. Knockback arrives as
 * the motion update ({@code handleSetEntityMotion}) and as an explosion's player knockback ({@code handleExplosion});
 * Fabric API has no event for either. Other entities and every other packet are unchanged.
 */
@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {
	@WrapOperation(method = "handleSetEntityMotion", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;lerpMotion(Lnet/minecraft/world/phys/Vec3;)V"))
	private void femboyclient$scaleKnockback(Entity entity, Vec3 motion, Operation<Void> original) {
		original.call(entity, entity == Minecraft.getInstance().player ? VelocityModule.filterMotion(motion) : motion);
	}

	@WrapOperation(method = "handleExplosion", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/network/protocol/game/ClientboundExplodePacket;playerKnockback()Ljava/util/Optional;"))
	private Optional<Vec3> femboyclient$scaleExplosion(ClientboundExplodePacket packet, Operation<Optional<Vec3>> original) {
		return original.call(packet).map(VelocityModule::filterExplosion);
	}
}
