package io.github.theamethyx.femboyclient.client.modules;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.ShulkerBoxBlock;

import net.fabricmc.fabric.api.client.rendering.v1.TooltipComponentCallback;

import io.github.theamethyx.femboyclient.client.FemboyClient;
import io.github.theamethyx.femboyclient.client.gui.Theme;
import io.github.theamethyx.femboyclient.core.module.Module;
import io.github.theamethyx.femboyclient.core.module.ModuleCategory;

/** Shows a shulker box's contents as a 9×3 item grid in its tooltip. Reads the item's own container data. */
public final class ShulkerPreviewModule extends Module {
	public ShulkerPreviewModule() {
		super("shulker_preview", "Shulker Preview", "Shows a shulker box's contents as an item grid in its tooltip.", ModuleCategory.INVENTORY, false, NO_KEY);
	}

	@Override
	public boolean requiresWorld() {
		return false;
	}

	/** The tooltip data a shulker box shows; created by ItemStackMixin. */
	public record Preview(List<ItemStack> items) implements TooltipComponent {
	}

	/** The preview for {@code stack}, or empty if it is not a shulker box with contents or the module is off. */
	public static Optional<TooltipComponent> previewFor(ItemStack stack) {
		FemboyClient femboy = FemboyClient.get();
		ShulkerPreviewModule module = femboy == null ? null : femboy.modules().get(ShulkerPreviewModule.class);
		if (module == null || !module.isRunning() || !(stack.getItem() instanceof BlockItem block) || !(block.getBlock() instanceof ShulkerBoxBlock)) {
			return Optional.empty();
		}

		ItemContainerContents contents = stack.get(DataComponents.CONTAINER);
		if (contents == null) {
			return Optional.empty();
		}

		NonNullList<ItemStack> items = NonNullList.withSize(27, ItemStack.EMPTY);
		contents.copyInto(items);
		if (items.stream().allMatch(ItemStack::isEmpty)) {
			return Optional.empty();
		}

		return Optional.of(new Preview(new ArrayList<>(items)));
	}

	public static void registerTooltip() {
		TooltipComponentCallback.EVENT.register(data -> data instanceof Preview preview ? new Grid(preview) : null);
	}

	private record Grid(Preview preview) implements ClientTooltipComponent {
		@Override
		public int getHeight(Font font) {
			return 3 * 18 + 4;
		}

		@Override
		public int getWidth(Font font) {
			return 9 * 18 + 2;
		}

		@Override
		public void renderImage(Font font, int x, int y, int width, int height, GuiGraphics graphics) {
			graphics.fill(x, y, x + 9 * 18 + 2, y + 3 * 18 + 2, Theme.withAlpha(Theme.SURFACE, 0.9f));
			for (int i = 0; i < preview.items().size() && i < 27; i++) {
				int sx = x + 1 + (i % 9) * 18;
				int sy = y + 1 + (i / 9) * 18;
				ItemStack stack = preview.items().get(i);
				graphics.fill(sx, sy, sx + 17, sy + 17, Theme.withAlpha(Theme.SURFACE_ACTIVE, 0.9f));
				if (!stack.isEmpty()) {
					graphics.renderItem(stack, sx, sy);
					graphics.renderItemDecorations(font, stack, sx, sy);
				}
			}
		}
	}
}
