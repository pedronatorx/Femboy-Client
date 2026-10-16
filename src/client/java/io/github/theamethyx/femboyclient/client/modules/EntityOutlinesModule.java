package io.github.theamethyx.femboyclient.client.modules;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.Projectile;

import io.github.theamethyx.femboyclient.client.FemboyClient;
import io.github.theamethyx.femboyclient.core.module.Module;
import io.github.theamethyx.femboyclient.core.module.ModuleCategory;
import io.github.theamethyx.femboyclient.core.setting.BooleanSetting;
import io.github.theamethyx.femboyclient.core.setting.ColorSetting;
import io.github.theamethyx.femboyclient.core.setting.NumberSetting;

/**
 * Outlines entities the way the Glowing effect does: vanilla's own outline shader traces each entity's shape, visible
 * through walls. Only the client decides what glows; nothing is sent to the server.
 */
public final class EntityOutlinesModule extends Module {
	{
		settings().section("Entities");
	}

	private final BooleanSetting players = settings().add(new BooleanSetting("players", "Players", "Outline other players.", true));
	private final BooleanSetting hostile = settings().add(new BooleanSetting("hostile", "Hostile mobs", "Outline monsters.", true));
	private final BooleanSetting passive = settings().add(new BooleanSetting("passive", "Passive mobs", "Outline animals and villagers.", false));
	private final BooleanSetting items = settings().add(new BooleanSetting("items", "Dropped items", "Outline items on the ground.", false));
	private final BooleanSetting projectiles = settings().add(new BooleanSetting("projectiles", "Projectiles", "Outline arrows, tridents, pearls and others.", false));
	private final NumberSetting range = settings().add(new NumberSetting("range", "Range", "Only entities this close.", 64, 8, 256, 8, "blocks"));

	{
		settings().section("Colors");
	}

	private final BooleanSetting teamColors = settings().add(new BooleanSetting("team_colors", "Use team colors",
			"Players and mobs on a scoreboard team keep their team color.", false));
	private final ColorSetting playerColor = settings().add(new ColorSetting("color_players", "Players", "Outline color for players.", 0xE070E8));
	private final ColorSetting hostileColor = settings().add(new ColorSetting("color_hostile", "Hostile mobs", "Outline color for monsters.", 0xF27272));
	private final ColorSetting passiveColor = settings().add(new ColorSetting("color_passive", "Passive mobs", "Outline color for passive mobs.", 0x5BD68A));
	private final ColorSetting itemColor = settings().add(new ColorSetting("color_items", "Items", "Outline color for dropped items.", 0xF2C14E));
	private final ColorSetting projectileColor = settings().add(new ColorSetting("color_projectiles", "Projectiles", "Outline color for projectiles.", 0x6EA8FF));

	public EntityOutlinesModule() {
		super("entity_outlines", "Entity Outlines", "Glowing-style outlines around players, mobs, items and projectiles, through walls.",
				ModuleCategory.VISUALS, false, NO_KEY);
	}

	private static EntityOutlinesModule active() {
		FemboyClient femboy = FemboyClient.get();
		EntityOutlinesModule module = femboy == null ? null : femboy.modules().get(EntityOutlinesModule.class);
		return module != null && module.isRunning() ? module : null;
	}

	/** The outline color for {@code entity} (RGB), or -1 when it is not outlined. */
	public int colorOf(Entity entity) {
		Minecraft client = Minecraft.getInstance();
		if (entity == client.player || entity == client.getCameraEntity() || client.player == null
				|| entity.distanceToSqr(client.player) > range.get() * range.get()) {
			return -1;
		}

		if (entity instanceof ItemEntity) {
			return items.get() ? itemColor.get() : -1;
		}

		if (entity instanceof Projectile) {
			return projectiles.get() ? projectileColor.get() : -1;
		}

		String group = EntityEspModule.groupOf(entity.getType());
		return switch (group) {
			case EntityEspModule.PLAYERS -> players.get() ? playerColor.get() : -1;
			case EntityEspModule.HOSTILE -> hostile.get() ? hostileColor.get() : -1;
			case EntityEspModule.PASSIVE, EntityEspModule.VILLAGERS -> passive.get() ? passiveColor.get() : -1;
			default -> -1;
		};
	}

	/** Mixin hook for {@code Minecraft.shouldEntityAppearGlowing}. */
	public static boolean glows(Entity entity) {
		EntityOutlinesModule module = active();
		return module != null && module.colorOf(entity) != -1;
	}

	/** Mixin hook for the outline color; keeps {@code original} (the team color) when the module is off or uses team colors. */
	public static int outlineColor(Entity entity, int original) {
		EntityOutlinesModule module = active();
		if (module == null || entity.isCurrentlyGlowing() || module.teamColors.get() && entity.getTeam() != null) {
			return original;
		}

		int color = module.colorOf(entity);
		return color == -1 ? original : color & 0xFFFFFF;
	}
}
