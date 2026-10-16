package io.github.theamethyx.femboyclient.client.modules;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.shapes.VoxelShape;

import io.github.theamethyx.femboyclient.client.module.ClientHooks;
import io.github.theamethyx.femboyclient.client.render.RenderingUtilities;
import io.github.theamethyx.femboyclient.core.module.Module;
import io.github.theamethyx.femboyclient.core.module.ModuleCategory;
import io.github.theamethyx.femboyclient.core.setting.ColorSetting;
import io.github.theamethyx.femboyclient.core.setting.NumberSetting;

/** A colored outline and fill on the block you are looking at, on top of vanilla's thin outline. */
public final class BlockHighlightModule extends Module implements ClientHooks.WorldRender {
	private final ColorSetting color = settings().add(new ColorSetting("color", "Color", "Highlight color.", 0xE070E8));
	private final NumberSetting fill = settings().add(new NumberSetting("fill", "Fill opacity", "Opacity of the fill.", 20, 0, 100, 5, "%"));
	private final NumberSetting width = settings().add(new NumberSetting("width", "Outline width", "Outline thickness.", 2, 1, 5, 0.5, "px"));

	private BlockPos highlighted;

	public BlockHighlightModule() {
		super("block_highlight", "Block Highlight", "A colored outline and fill on the block under your crosshair.", ModuleCategory.VISUALS, false, NO_KEY);
	}

	public BlockPos highlighted() {
		return highlighted;
	}

	@Override
	public void renderWorld(RenderingUtilities render) {
		Minecraft client = Minecraft.getInstance();
		highlighted = null;
		if (!(client.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK || client.level == null) {
			return;
		}

		BlockPos pos = hit.getBlockPos();
		VoxelShape shape = client.level.getBlockState(pos).getShape(client.level, pos);
		if (shape.isEmpty()) {
			return;
		}

		highlighted = pos;
		AABB box = shape.bounds().move(pos).inflate(0.002);
		if (fill.get() > 0) {
			render.filledBox(box, color.withAlpha(fill.get().floatValue() / 100f), false);
		}

		render.boxOutline(box, color.withAlpha(0.95f), width.get().floatValue(), false);
	}
}
