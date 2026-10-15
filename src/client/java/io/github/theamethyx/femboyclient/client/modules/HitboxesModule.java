package io.github.theamethyx.femboyclient.client.modules;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import io.github.theamethyx.femboyclient.client.module.ClientHooks;
import io.github.theamethyx.femboyclient.client.render.RenderingUtilities;
import io.github.theamethyx.femboyclient.core.module.Module;
import io.github.theamethyx.femboyclient.core.module.ModuleCategory;
import io.github.theamethyx.femboyclient.core.setting.BooleanSetting;
import io.github.theamethyx.femboyclient.core.setting.ColorSetting;
import io.github.theamethyx.femboyclient.core.setting.NumberSetting;

/** Draws entity hitboxes with eye height and look direction, like vanilla's F3+B but filtered and styled. */
public final class HitboxesModule extends Module implements ClientHooks.WorldRender {
	{
		settings().section("Entities");
	}

	private final BooleanSetting players = settings().add(new BooleanSetting("players", "Players", "Other players.", true));
	private final BooleanSetting hostile = settings().add(new BooleanSetting("hostile", "Hostile mobs", "Monsters.", true));
	private final BooleanSetting passive = settings().add(new BooleanSetting("passive", "Passive mobs", "Animals, villagers and other mobs.", false));
	private final BooleanSetting items = settings().add(new BooleanSetting("items", "Dropped items", "Item entities on the ground.", false));
	private final BooleanSetting projectiles = settings().add(new BooleanSetting("projectiles", "Projectiles", "Arrows, tridents, pearls and others.", true));
	private final BooleanSetting other = settings().add(new BooleanSetting("other", "Other entities", "Crystals, boats, minecarts and the rest.", false));
	private final NumberSetting range = settings().add(new NumberSetting("range", "Range", "Only entities this close.", 32, 4, 128, 4, "blocks"));

	{
		settings().section("Style");
	}

	private final ColorSetting color = settings().add(new ColorSetting("color", "Box color", "Color of the hitbox outline.", 0xFFFFFF));
	private final NumberSetting opacity = settings().add(new NumberSetting("opacity", "Opacity", "Opacity of the outlines.", 80, 10, 100, 5, "%"));
	private final NumberSetting width = settings().add(new NumberSetting("width", "Line width", "Thickness of the outlines.", 1.5, 1, 5, 0.5, "px"));
	private final BooleanSetting eyes = settings().add(new BooleanSetting("eyes", "Eye height", "A red line at eye height, as in F3+B.", true));
	private final BooleanSetting look = settings().add(new BooleanSetting("look", "Look direction", "A blue line where the entity looks.", true));
	private final BooleanSetting throughWalls = settings().add(new BooleanSetting("through_walls", "Through walls",
			"Draw hitboxes even when blocks are in the way.", false));

	private int drawn;

	public HitboxesModule() {
		super("hitboxes", "Hitboxes", "Entity hitboxes with eye height and look direction, filtered by type.", ModuleCategory.VISUALS, false, NO_KEY);
	}

	/** Hitboxes drawn in the last frame (for tests). */
	public int drawn() {
		return drawn;
	}

	private boolean shown(Entity entity) {
		if (entity instanceof Player) return players.get();
		if (entity.getType() == EntityType.ITEM) return items.get();
		if (entity instanceof Projectile) return projectiles.get();
		if (entity instanceof LivingEntity) {
			return EntityEspModule.HOSTILE.equals(EntityEspModule.groupOf(entity.getType())) ? hostile.get() : passive.get();
		}

		return other.get();
	}

	@Override
	public void renderWorld(RenderingUtilities render) {
		Minecraft client = Minecraft.getInstance();
		LocalPlayer self = client.player;
		drawn = 0;
		if (self == null || client.level == null) {
			return;
		}

		float pt = render.partialTick();
		double max = range.get() * range.get();
		int box = color.withAlpha(opacity.get().floatValue() / 100f);
		float w = width.get().floatValue();
		for (Entity entity : client.level.entitiesForRendering()) {
			if (entity == self && client.options.getCameraType().isFirstPerson() || entity.isRemoved() || entity.distanceToSqr(self) > max || !shown(entity)) {
				continue;
			}

			Vec3 lerped = entity.getPosition(pt);
			Vec3 offset = lerped.subtract(entity.position());
			AABB bounds = entity.getBoundingBox().move(offset);
			render.boxOutline(bounds, box, w, throughWalls.get());
			if (eyes.get() && entity instanceof LivingEntity) {
				double eyeY = lerped.y + entity.getEyeHeight();
				render.boxOutline(new AABB(bounds.minX, eyeY - 0.01, bounds.minZ, bounds.maxX, eyeY + 0.01, bounds.maxZ), 0xFFFF4040, w, throughWalls.get());
			}

			if (look.get() && entity instanceof LivingEntity) {
				Vec3 eye = lerped.add(0, entity.getEyeHeight(), 0);
				render.line(eye, eye.add(entity.getViewVector(pt).scale(2)), 0xFF4080FF, w, throughWalls.get());
			}

			drawn++;
		}
	}
}
