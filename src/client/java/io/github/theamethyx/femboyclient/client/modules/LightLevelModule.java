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

	public record Spot(BlockPos pos, boolean darkNow) {
	}

	private List<Spot> spots = List.of();
	private int ticks;

	public LightLevelModule() {
		super("light_level", "Light Level", "Marks where hostile mobs can spawn: block light 0 (red now, yellow at night).",
				ModuleCategory.VISUALS, false, NO_KEY);
	}

	public List<Spot> spots() {
		return spots;
	}

	@Override
	protected void onTick() {
		if (ticks++ % 10 != 0) {
			return;
		}

		Minecraft client = Minecraft.getInstance();
		ClientLevel level = client.level;
		BlockPos center = client.player.blockPosition();
		int r = radius.intValue();
		int h = height.intValue();
		List<Spot> next = new ArrayList<>();
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int dx = -r; dx <= r; dx++) {
			for (int dz = -r; dz <= r; dz++) {
				for (int dy = -h; dy <= h; dy++) {
					pos.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
					BlockState ground = level.getBlockState(pos);
					BlockPos above = pos.above();
					BlockState air = level.getBlockState(above);
					if (!ground.isFaceSturdy(level, pos, Direction.UP) || !air.getCollisionShape(level, above).isEmpty() || !air.getFluidState().isEmpty()) {
						continue;
					}

					if (level.getBrightness(LightLayer.BLOCK, above) > 0) {
						continue;
					}

					boolean darkNow = level.getBrightness(LightLayer.SKY, above) <= 7;
					if (darkNow || showNight.get()) {
						next.add(new Spot(above.immutable(), darkNow));
					}
				}
			}
		}

		spots = next;
	}

	@Override
	protected void onDisable() {
		spots = List.of();
	}

	@Override
	public void renderWorld(RenderingUtilities render) {
		for (Spot spot : spots) {
			BlockPos p = spot.pos();
			AABB top = new AABB(p.getX() + 0.15, p.getY() + 0.01, p.getZ() + 0.15, p.getX() + 0.85, p.getY() + 0.03, p.getZ() + 0.85);
			render.filledBox(top, spot.darkNow() ? 0x70F24A4A : 0x60F2C14E, false);
		}
	}
}
