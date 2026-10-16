package io.github.theamethyx.femboyclient.client.mixin;

import java.util.Optional;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;

import io.github.theamethyx.femboyclient.client.modules.ShulkerPreviewModule;

/**
 * Why this mixin exists: Shulker Preview shows a box's contents as an item grid. Tooltip images come from
 * {@code ItemStack.getTooltipImage}, which shulker boxes do not provide; Fabric only converts existing tooltip data.
 * While the module is on, shulker boxes with contents return the preview data here. Nothing else is affected.
 */
@Mixin(ItemStack.class)
public abstract class ItemStackMixin {
	@Inject(method = "getTooltipImage", at = @At("HEAD"), cancellable = true)
	private void femboyclient$shulkerPreview(CallbackInfoReturnable<Optional<TooltipComponent>> cir) {
		Optional<TooltipComponent> preview = ShulkerPreviewModule.previewFor((ItemStack) (Object) this);
		if (preview.isPresent()) {
			cir.setReturnValue(preview);
		}
	}
}
