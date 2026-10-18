package io.github.theamethyx.femboyclient.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

import io.github.theamethyx.femboyclient.client.world.BlockChanges;

/**
 * Why this mixin exists: block scanners (Block ESP, Block Counter) used to re-read every loaded chunk every few seconds to
 * notice changed blocks. {@code setBlocksDirty} is where the client learns that one block changed (from the server or
 * its own prediction), and there is no Fabric event for it; forwarding it lets scanners update just that block, at once.
 */
@Mixin(ClientLevel.class)
public abstract class ClientLevelMixin {
	@Inject(method = "setBlocksDirty", at = @At("HEAD"))
	private void femboyclient$blockChanged(BlockPos pos, BlockState oldState, BlockState newState, CallbackInfo ci) {
		BlockChanges.fire((ClientLevel) (Object) this, pos, oldState, newState);
	}
}
