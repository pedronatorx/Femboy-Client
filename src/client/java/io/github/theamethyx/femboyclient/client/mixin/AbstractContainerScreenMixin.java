package io.github.theamethyx.femboyclient.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;

import io.github.theamethyx.femboyclient.client.modules.InventorySearchModule;

/** Inventory Search: marks each slot as it is drawn, so the marks sit under the tooltip like vanilla's slot highlight. */
@Mixin(AbstractContainerScreen.class)
public abstract class AbstractContainerScreenMixin {
	@Inject(method = "renderSlot", at = @At("TAIL"))
	private void femboyclient$searchHighlight(GuiGraphics graphics, Slot slot, int mouseX, int mouseY, CallbackInfo ci) {
		InventorySearchModule.drawSlot((AbstractContainerScreen<?>) (Object) this, graphics, slot);
	}
}
