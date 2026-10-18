package io.github.theamethyx.femboyclient.client.modules;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import io.github.theamethyx.femboyclient.client.module.ClientHooks;
import io.github.theamethyx.femboyclient.client.render.RenderingUtilities;
import io.github.theamethyx.femboyclient.core.module.Module;
import io.github.theamethyx.femboyclient.core.module.ModuleCategory;
import io.github.theamethyx.femboyclient.core.setting.BooleanSetting;
import io.github.theamethyx.femboyclient.core.setting.NumberSetting;

/**
 * Marks block tops where hostile mobs could spawn: red where block light is 0 and sky light is 7 or less (dark now),
 * yellow where block light is 0 but the sky is bright (spawns at night). Since 1.18 most hostile mobs need block light 0.
 */
public final class LightLevelModule extends Module implements ClientHooks.WorldRender {
	private final NumberSetting radius = settings().add(new NumberSetting("radius", "Radius", "Blocks around you.", 12, 4, 32, 1, "blocks"));
	private final NumberSetting height = settings().add(new NumberSetting("height", "Height", "Blocks above and below you.", 4, 1, 16, 1, "blocks"));
	private final BooleanSetting showNight = settings().add(new BooleanSetting("night", "Show night-only spots",
			"Also mark spots that are dark only at night (yellow).", true));

	/** A spawnable block top; {@code box} is the marker drawn on it, made once per scan. */
	public record Spot(BlockPos pos, boolean darkNow, AABB box) {
		Spot(BlockPos pos, boolean darkNow) {
			this(pos, darkNow, new AABB(pos.getX() + 0.15, pos.getY() + 0.01, pos.getZ() + 0.15, pos.getX() + 0.85, pos.getY() + 0.03, pos.getZ() + 0.85));
		}
	}

	private List<Spot> spots = List.of();
	private int ticksUntilScan;

	public LightLevelModule() {
		super("light_level", "Light Level", "Marks where hostile mobs can spawn: block light 0 (red now, yellow at night).",
				ModuleCategory.VISUALS, false, NO_KEY);
	}

	public List<Spot> spots() {
		return spots;
	}

	/**
	 * Scans every half second (light changes slowly). Each column is walked from the top once, reusing the block above
	 * instead of reading it again, and the cheap checks (is the space above free?) come before the expensive ones.
	 */
	@Override
	protected void onTick() {
		if (--ticksUntilScan > 0) {
			return;
		}

		ticksUntilScan = io.github.theamethyx.femboyclient.client.Performance.interval(10);
		Minecraft client = Minecraft.getInstance();
		ClientLevel level = client.level;
		BlockPos center = client.player.blockPosition();
		int r = radius.intValue();
		int h = height.intValue();
		boolean night = showNight.get();
		List<Spot> next = new ArrayList<>();
		BlockPos.MutableBlockPos ground = new BlockPos.MutableBlockPos();
		BlockPos.MutableBlockPos above = new BlockPos.MutableBlockPos();
		for (int dx = -r; dx <= r; dx++) {
			for (int dz = -r; dz <= r; dz++) {
				int x = center.getX() + dx;
				int z = center.getZ() + dz;
				BlockState upper = level.getBlockState(above.set(x, center.getY() + h + 1, z));
				for (int y = center.getY() + h; y >= center.getY() - h; y--) {
					BlockState state = level.getBlockState(ground.set(x, y, z));
					above.set(x, y + 1, z);
					boolean free = upper.isAir() || upper.getFluidState().isEmpty() && upper.getCollisionShape(level, above).isEmpty();
					BlockState top = upper;
					upper = state;
					if (!free || !top.getFluidState().isEmpty() || !state.isFaceSturdy(level, ground, Direction.UP)
							|| level.getBrightness(LightLayer.BLOCK, above) > 0) {
						continue;
					}

					boolean darkNow = level.getBrightness(LightLayer.SKY, above) <= 7;
					if (darkNow || night) {
						next.add(new Spot(above.immutable(), darkNow));
					}
				}
			}
		}

		spots = List.copyOf(next);
	}

	@Override
	protected void onDisable() {
		spots = List.of();
		ticksUntilScan = 0;
	}

	@Override
	protected void onWorldChange() {
		spots = List.of();
		ticksUntilScan = 0;
	}

	@Override
	public void renderWorld(RenderingUtilities render) {
		for (Spot spot : spots) {
			if (io.github.theamethyx.femboyclient.client.render.FrameProjection.inView(spot.box())) {
				render.filledBox(spot.box(), spot.darkNow() ? 0x70F24A4A : 0x60F2C14E, false);
			}
		}
	}
}
