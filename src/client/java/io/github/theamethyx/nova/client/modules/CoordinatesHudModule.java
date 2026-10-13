package io.github.theamethyx.nova.client.modules;

import java.util.List;
import java.util.Locale;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;

import io.github.theamethyx.nova.client.gui.Theme;
import io.github.theamethyx.nova.client.hud.HudAnchor;
import io.github.theamethyx.nova.client.hud.HudLine;
import io.github.theamethyx.nova.client.module.ClientHooks;
import io.github.theamethyx.nova.core.module.Module;
import io.github.theamethyx.nova.core.module.ModuleCategory;
import io.github.theamethyx.nova.core.setting.BooleanSetting;
import io.github.theamethyx.nova.core.setting.EnumSetting;
import io.github.theamethyx.nova.core.setting.NumberSetting;

/** Shows the player's position, facing and related coordinates. */
public final class CoordinatesHudModule extends Module implements ClientHooks.HudBlock {
	private final EnumSetting<HudAnchor> anchor = settings().add(new EnumSetting<>("anchor", "Position",
			"Screen corner for this block. Blocks in the same corner stack without overlapping.", HudAnchor.TOP_LEFT));
	private final NumberSetting decimals = settings().add(new NumberSetting("decimals", "Decimal places",
			"Digits shown after the decimal point for X, Y and Z.", 1, 0, 3, 1, ""));
	private final BooleanSetting facing = settings().add(new BooleanSetting("facing", "Show facing",
			"Show the direction you are looking and which axis that increases or decreases.", true));
	private final BooleanSetting otherDimension = settings().add(new BooleanSetting("other_dimension", "Show Nether/Overworld equivalent",
			"In the Overworld show the matching Nether position (divided by 8) and vice versa.", true));
	private final BooleanSetting chunk = settings().add(new BooleanSetting("chunk", "Show chunk",
			"Show the chunk coordinates and your position inside the chunk.", false));
	private final BooleanSetting dimension = settings().add(new BooleanSetting("dimension", "Show dimension",
			"Show the dimension identifier, e.g. minecraft:overworld.", false));

	public CoordinatesHudModule() {
		super("coordinates_hud", "Coordinates", "Shows your position, facing direction and related coordinates on the HUD.",
				ModuleCategory.HUD, true, NO_KEY);
	}

	@Override
	public HudAnchor hudAnchor() {
		return anchor.get();
	}

	@Override
	public void buildHud(List<HudLine> lines) {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null) {
			return;
		}

		int digits = decimals.intValue();
		String format = "%." + digits + "f";
		String xyz = String.format(Locale.ROOT, format + " / " + format + " / " + format, player.getX(), player.getY(), player.getZ());
		lines.add(HudLine.of("XYZ", xyz));

		if (facing.get()) {
			Direction direction = player.getDirection();
			String axis = switch (direction) {
				case NORTH -> "-Z";
				case SOUTH -> "+Z";
				case EAST -> "+X";
				case WEST -> "-X";
				default -> "";
			};
			String name = direction.getName();
			lines.add(HudLine.of("Facing", Character.toUpperCase(name.charAt(0)) + name.substring(1) + " (" + axis + ")"));
		}

		Level level = player.level();
		if (otherDimension.get()) {
			if (level.dimension() == Level.OVERWORLD) {
				lines.add(HudLine.of("Nether", String.format(Locale.ROOT, "%.0f / %.0f", player.getX() / 8, player.getZ() / 8), Theme.TEXT_SECONDARY));
			} else if (level.dimension() == Level.NETHER) {
				lines.add(HudLine.of("Overworld", String.format(Locale.ROOT, "%.0f / %.0f", player.getX() * 8, player.getZ() * 8), Theme.TEXT_SECONDARY));
			}
		}

		if (chunk.get()) {
			int bx = player.getBlockX();
			int bz = player.getBlockZ();
			lines.add(HudLine.of("Chunk", (bx >> 4) + ", " + (bz >> 4) + "  in " + (bx & 15) + ", " + (bz & 15), Theme.TEXT_SECONDARY));
		}

		if (dimension.get()) {
			lines.add(HudLine.of("Dimension", level.dimension().identifier().toString(), Theme.TEXT_SECONDARY));
		}
	}
}
