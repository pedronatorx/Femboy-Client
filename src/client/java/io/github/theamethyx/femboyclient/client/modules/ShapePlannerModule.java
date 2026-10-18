package io.github.theamethyx.femboyclient.client.modules;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import io.github.theamethyx.femboyclient.client.gui.Icon;
import io.github.theamethyx.femboyclient.client.gui.UiContext;
import io.github.theamethyx.femboyclient.client.gui.widget.Button;
import io.github.theamethyx.femboyclient.client.gui.widget.Widget;
import io.github.theamethyx.femboyclient.client.hud.HudAnchor;
import io.github.theamethyx.femboyclient.client.hud.HudLine;
import io.github.theamethyx.femboyclient.client.module.ClientHooks;
import io.github.theamethyx.femboyclient.client.render.FrameProjection;
import io.github.theamethyx.femboyclient.client.render.RenderingUtilities;
import io.github.theamethyx.femboyclient.core.module.Module;
import io.github.theamethyx.femboyclient.core.module.ModuleCategory;
import io.github.theamethyx.femboyclient.core.setting.BooleanSetting;
import io.github.theamethyx.femboyclient.core.setting.ColorSetting;
import io.github.theamethyx.femboyclient.core.setting.EnumSetting;
import io.github.theamethyx.femboyclient.core.setting.NumberSetting;
import io.github.theamethyx.femboyclient.core.tools.ShapeGenerator;

/**
 * Shape Planner: outlines where to place blocks for a circle, cylinder, sphere, dome, spiral staircase or wall, around a
 * center you choose, and counts the blocks needed. It only draws guides; it never places blocks. The shape is computed
 * once when a setting changes; each frame draws the nearest outlines that are in view, up to a limit.
 */
public final class ShapePlannerModule extends Module implements ClientHooks.WorldRender, ClientHooks.HudBlock, ClientHooks.CustomControls {
	private final EnumSetting<ShapeGenerator.Shape> shape = settings().add(new EnumSetting<>("shape", "Shape", "What to plan.", ShapeGenerator.Shape.CIRCLE));
	private final NumberSetting radius = settings().add(new NumberSetting("radius", "Radius",
			"Radius in blocks (for a wall: half its length). The center block counts as radius 0.", 8, 1, 64, 1, "blocks"));
	private final NumberSetting height = settings().add(new NumberSetting("height", "Height",
			"Layers for cylinders, staircases and walls.", 5, 1, 128, 1, "blocks"));
	private final BooleanSetting hollow = settings().add(new BooleanSetting("hollow", "Hollow",
			"Only the outer shell (cylinders: walls without floor and roof).", true));

	{
		settings().section("Display");
	}

	private final ColorSetting color = settings().add(new ColorSetting("color", "Color", "Color of the outlines.", 0xC084FC));
	private final BooleanSetting throughWalls = settings().add(new BooleanSetting("through_walls", "Through walls",
			"Draw outlines hidden behind blocks too.", false));
	private final NumberSetting maxBoxes = settings().add(new NumberSetting("max_boxes", "Outlines drawn",
			"At most this many outlines (the nearest ones) are drawn each frame.", 1500, 100, 6000, 100, ""));
	private final EnumSetting<HudAnchor> anchor = settings().add(new EnumSetting<>("anchor", "Position", "Screen corner for the summary.", HudAnchor.TOP_RIGHT));

	private BlockPos center;
	private ShapeGenerator.Result result;
	private boolean stale = true;
	private List<AABB> nearest = List.of();
	private int selectedTick;
	private int drawn;

	public ShapePlannerModule() {
		super("shape_planner", "Shape Planner", "Outlines circles, spheres, domes, cylinders, spiral staircases and walls to build, and counts "
				+ "their blocks. Draws guides only; it never places blocks.", ModuleCategory.WORLD, false, NO_KEY);
		settings().all().forEach(setting -> setting.onChange(value -> stale = true));
	}

	@Override
	public List<String> aliases() {
		return List.of("circle generator", "sphere generator", "dome", "building planner", "geometry");
	}

	@Override
	protected void onEnable() {
		LocalPlayer player = Minecraft.getInstance().player;
		if (center == null && player != null) {
			center = player.blockPosition();
		}

		stale = true;
	}

	@Override
	protected void onWorldChange() {
		center = null;
		nearest = List.of();
		LocalPlayer player = Minecraft.getInstance().player;
		if (player != null) {
			center = player.blockPosition();
		}
	}

	/** Puts the center at a block (from the controls or tests). */
	public void setCenter(BlockPos pos) {
		center = pos.immutable();
		stale = true;
	}

	public BlockPos center() {
		return center;
	}

	/** The current shape (computed if needed). */
	public ShapeGenerator.Result shapeResult() {
		if (stale || result == null) {
			result = ShapeGenerator.generate(shape.get(), radius.intValue(), height.intValue(), hollow.get());
			stale = false;
			selectedTick = -100;
		}

		return result;
	}

	@Override
	protected void onTick() {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null || center == null) {
			return;
		}

		ShapeGenerator.Result current = shapeResult();
		// Choosing the nearest outlines sorts the shape, so it is done twice a second, not every frame.
		if (++selectedTick < 10 && !nearest.isEmpty()) {
			return;
		}

		selectedTick = 0;
		double px = player.getX() - center.getX() - 0.5;
		double py = player.getY() - center.getY();
		double pz = player.getZ() - center.getZ() - 0.5;
		List<int[]> positions = new ArrayList<>(current.positions());
		int limit = Math.min(positions.size(), maxBoxes.intValue());
		if (positions.size() > limit) {
			positions.sort(Comparator.comparingDouble(p -> (p[0] - px) * (p[0] - px) + (p[1] - py) * (p[1] - py) + (p[2] - pz) * (p[2] - pz)));
		}

		List<AABB> boxes = new ArrayList<>(limit);
		for (int i = 0; i < limit; i++) {
			int[] p = positions.get(i);
			int x = center.getX() + p[0];
			int y = center.getY() + p[1];
			int z = center.getZ() + p[2];
			boxes.add(new AABB(x + 0.02, y + 0.02, z + 0.02, x + 0.98, y + 0.98, z + 0.98));
		}

		nearest = boxes;
	}

	@Override
	public void renderWorld(RenderingUtilities render) {
		int argb = 0xC0000000 | color.get();
		int count = 0;
		for (AABB box : nearest) {
			if (FrameProjection.inView(box)) {
				render.boxOutline(box, argb, 1.5f, throughWalls.get());
				count++;
			}
		}

		drawn = count;
	}

	@Override
	public String workload() {
		ShapeGenerator.Result current = result;
		return current == null ? null : String.format(Locale.ROOT, "%,d blocks planned, %d outlines drawn", current.count(), drawn);
	}

	@Override
	public HudAnchor hudAnchor() {
		return anchor.get();
	}

	@Override
	public void buildHud(List<HudLine> lines) {
		if (center == null) {
			return;
		}

		ShapeGenerator.Result current = shapeResult();
		String size = switch (shape.get()) {
			case CIRCLE, SPHERE, DOME -> "radius " + radius.intValue();
			case WALL -> (2 * radius.intValue() + 1) + " long, " + height.intValue() + " high";
			default -> "radius " + radius.intValue() + ", " + height.intValue() + " high";
		};
		lines.add(HudLine.of(shape.get().label(), size + (hollow.get() ? ", hollow" : "")));
		lines.add(HudLine.of("Blocks", String.format(Locale.ROOT, "%,d%s (%.1f stacks)", current.count(), current.truncated() ? "+" : "", current.count() / 64.0)));
		lines.add(HudLine.of("Center", center.getX() + " " + center.getY() + " " + center.getZ()));
	}

	@Override
	public List<Widget> createControls(UiContext ui, Runnable refresh) {
		Button here = new Button("Center at my position", Button.Style.SECONDARY, () -> {
			LocalPlayer player = Minecraft.getInstance().player;
			if (player != null) {
				setCenter(player.blockPosition());
				refresh.run();
			}
		}).icon(Icon.TARGET);
		here.tooltip("Put the center of the shape at the block you stand in.");
		Button look = new Button("Center at the block I look at", Button.Style.SECONDARY, () -> {
			HitResult hit = Minecraft.getInstance().hitResult;
			if (hit instanceof BlockHitResult block && hit.getType() == HitResult.Type.BLOCK) {
				setCenter(block.getBlockPos().relative(block.getDirection()));
				refresh.run();
			}
		}).icon(Icon.NAVIGATE);
		look.tooltip("Put the center on the block face you are looking at (within reach).");
		here.setBounds(0, 0, 100, 16);
		look.setBounds(0, 0, 100, 16);
		return List.of(here, look);
	}
}
