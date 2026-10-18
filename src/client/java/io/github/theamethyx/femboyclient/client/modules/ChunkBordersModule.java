package io.github.theamethyx.femboyclient.client.modules;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import io.github.theamethyx.femboyclient.client.module.ClientHooks;
import io.github.theamethyx.femboyclient.client.render.OverlayColor;
import io.github.theamethyx.femboyclient.client.render.RenderingUtilities;
import io.github.theamethyx.femboyclient.core.module.Module;
import io.github.theamethyx.femboyclient.core.module.ModuleCategory;
import io.github.theamethyx.femboyclient.core.setting.BooleanSetting;
import io.github.theamethyx.femboyclient.core.setting.EnumSetting;
import io.github.theamethyx.femboyclient.core.setting.NumberSetting;

/**
 * Outlines chunk boundaries around the player, similar to vanilla F3+G but configurable. Uses
 * only the player's position, so it works identically everywhere.
 */
public final class ChunkBordersModule extends Module implements ClientHooks.WorldRender {
	private final NumberSetting radius = settings().add(new NumberSetting("radius", "Neighbor chunks",
			"How many chunks around yours also get their corners marked. 0 shows only your chunk.", 1, 0, 4, 1, "chunks"));
	private final NumberSetting height = settings().add(new NumberSetting("height", "Line height",
			"How far above and below you the lines extend.", 48, 8, 192, 8, "blocks"));
	private final BooleanSetting sections = settings().add(new BooleanSetting("sections", "Show section boundaries",
			"Draw rings every 16 blocks of height around your chunk (chunk sections).", true));
	private final EnumSetting<OverlayColor> color = settings().add(new EnumSetting<>("color", "Color",
			"Color of your chunk's lines; neighbor chunks use a fainter version.", OverlayColor.PURPLE));

	public ChunkBordersModule() {
		super("chunk_borders", "Chunk Borders", "Outlines the chunk you are standing in and its neighbors.",
				ModuleCategory.VISUALS, false, NO_KEY);
	}

	@Override
	public void renderWorld(RenderingUtilities render) {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null) {
			return;
		}

		Level level = player.level();
		int cx = player.getBlockX() >> 4;
		int cz = player.getBlockZ() >> 4;
		double minY = Math.max(level.getMinY(), player.getY() - height.get());
		double maxY = Math.min(level.getMaxY() + 1, player.getY() + height.get());
		int r = radius.intValue();
		int own = color.get().withAlpha(0.9f);
		int neighbor = color.get().withAlpha(0.35f);

		for (int dx = -r; dx <= r + 1; dx++) {
			for (int dz = -r; dz <= r + 1; dz++) {
				double x = (cx + dx) * 16.0;
				double z = (cz + dz) * 16.0;
				boolean ownCorner = (dx == 0 || dx == 1) && (dz == 0 || dz == 1);
				render.line(new Vec3(x, minY, z), new Vec3(x, maxY, z), ownCorner ? own : neighbor, ownCorner ? 1.5f : 1f, false);
			}
		}

		if (sections.get()) {
			double x0 = cx * 16.0, z0 = cz * 16.0, x1 = x0 + 16, z1 = z0 + 16;
			int firstSection = (int) Math.ceil(minY / 16.0) * 16;
			for (int y = firstSection; y <= maxY; y += 16) {
				int ring = color.get().withAlpha(0.6f);
				render.line(new Vec3(x0, y, z0), new Vec3(x1, y, z0), ring, 1f, false);
				render.line(new Vec3(x1, y, z0), new Vec3(x1, y, z1), ring, 1f, false);
				render.line(new Vec3(x1, y, z1), new Vec3(x0, y, z1), ring, 1f, false);
				render.line(new Vec3(x0, y, z1), new Vec3(x0, y, z0), ring, 1f, false);
			}
		}
	}

	@Override
	public java.util.List<String> aliases() {
		return java.util.List.of("chunk grid");
	}
}
