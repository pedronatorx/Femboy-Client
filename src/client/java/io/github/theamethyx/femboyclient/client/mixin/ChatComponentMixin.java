package io.github.theamethyx.femboyclient.client.mixin;

import java.util.List;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import net.minecraft.client.GuiMessage;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;

import io.github.theamethyx.femboyclient.client.modules.BetterChatModule;

/**
 * Why this mixin exists: Better Chat adds timestamps, stacks repeated messages and keeps a longer history. Fabric's
 * message events cannot change how a message is displayed or remove the previous line, so the displayed component is
 * replaced here and vanilla's fixed 100-line limit is read from the module. Messages sent to servers are unchanged.
 */
@Mixin(ChatComponent.class)
public abstract class ChatComponentMixin {
	@Shadow
	@Final
	private List<GuiMessage> allMessages;

	@Shadow
	protected abstract void refreshTrimmedMessages();

	@ModifyVariable(method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/GuiMessageTag;)V",
			at = @At("HEAD"), argsOnly = true)
	private Component femboyclient$decorate(Component component) {
		BetterChatModule.Decorated decorated = BetterChatModule.decorate(component);
		if (decorated.replacePrevious() && !allMessages.isEmpty()) {
			allMessages.removeFirst();
			refreshTrimmedMessages();
		}

		return decorated.message();
	}

	@ModifyConstant(method = {"addMessageToDisplayQueue", "addMessageToQueue"}, constant = @Constant(intValue = 100))
	private int femboyclient$historyLength(int vanilla) {
		return BetterChatModule.historyLength();
	}
}
