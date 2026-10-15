package io.github.theamethyx.femboyclient.client.combat;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import io.github.theamethyx.femboyclient.client.modules.EntityEspModule;
import io.github.theamethyx.femboyclient.core.setting.BooleanSetting;
import io.github.theamethyx.femboyclient.core.setting.EnumSetting;
import io.github.theamethyx.femboyclient.core.setting.ModuleSettings;
import io.github.theamethyx.femboyclient.core.setting.NumberSetting;

/** The "Targets" settings shared by KillAura, Trigger Bot and Aim Assist, and finding entities that match them. */
public final class TargetSettings {
	public enum Priority implements EnumSetting.Labeled {
		CLOSEST("Closest"), LOWEST_HEALTH("Lowest health"), SMALLEST_ANGLE("Closest to crosshair");

		private final String label;

		Priority(String label) {
			this.label = label;
		}

		@Override
		public String label() {
			return label;
		}
	}

	public enum AimPoint implements EnumSetting.Labeled {
		BODY("Body"), HEAD("Head"), NEAREST("Nearest point");

		private final String label;

		AimPoint(String label) {
			this.label = label;
		}

		@Override
		public String label() {
			return label;
		}
	}

	public final BooleanSetting players;
	public final BooleanSetting hostile;
	public final BooleanSetting passive;
	public final BooleanSetting ignoreAllies;
	public final BooleanSetting ignoreInvisible;
	public final BooleanSetting ignoreCreative;
	public final NumberSetting range;
	public final NumberSetting fov;
	public final BooleanSetting throughWalls;
	public final EnumSetting<Priority> priority;

	public TargetSettings(ModuleSettings settings, double defaultRange, double maxRange, double defaultFov, boolean passiveDefault) {
		settings.section("Targets");
		players = settings.add(new BooleanSetting("target_players", "Players", "Target other players.", true));
		hostile = settings.add(new BooleanSetting("target_hostile", "Hostile mobs", "Target monsters.", true));
		passive = settings.add(new BooleanSetting("target_passive", "Passive mobs", "Target animals, villagers and other mobs.", passiveDefault));
		ignoreAllies = settings.add(new BooleanSetting("ignore_allies", "Ignore teammates", "Skip players and mobs on your scoreboard team.", true));
		ignoreInvisible = settings.add(new BooleanSetting("ignore_invisible", "Ignore invisible", "Skip entities you cannot see.", false));
		ignoreCreative = settings.add(new BooleanSetting("ignore_creative", "Ignore creative players",
				"Skip players in creative mode; they cannot be hurt.", true));
		range = settings.add(new NumberSetting("range", "Range", "Distance from your eyes to the target's hitbox. Vanilla servers "
				+ "reject attacks beyond your interaction range plus 3 blocks.", defaultRange, 1, maxRange, 0.1, "blocks"));
		fov = settings.add(new NumberSetting("fov", "Field of view", "Only targets within this angle around your crosshair.",
				defaultFov, 10, 360, 5, "°"));
		throughWalls = settings.add(new BooleanSetting("through_walls", "Through walls", "Also target entities you have no line of sight to.", false));
		priority = settings.add(new EnumSetting<>("priority", "Priority", "Which target comes first.", Priority.CLOSEST));
	}

	/** Whether an entity passes the type, team, visibility and game-mode filters (not range or angle). */
	public boolean accepts(LocalPlayer self, Entity entity) {
		if (!(entity instanceof LivingEntity living) || entity == self || !living.isAlive() || living.isSpectator() || entity instanceof ArmorStand) {
			return false;
		}

		if (ignoreInvisible.get() && entity.isInvisibleTo(self)) return false;
		if (ignoreAllies.get() && self.isAlliedTo(entity)) return false;
		if (entity instanceof Player player) {
			return players.get() && !(ignoreCreative.get() && player.isCreative());
		}

		String group = EntityEspModule.groupOf(entity.getType());
		return EntityEspModule.HOSTILE.equals(group) ? hostile.get() : passive.get();
	}

	public static double distance(LocalPlayer self, Entity entity) {
		return Math.sqrt(entity.getBoundingBox().distanceToSqr(self.getEyePosition()));
	}

	/** Matching entities within range and field of view, best first. */
	public List<LivingEntity> find(LocalPlayer self) {
		List<LivingEntity> found = new ArrayList<>();
		Minecraft client = Minecraft.getInstance();
		if (client.level == null) {
			return found;
		}

		double max = range.get();
		double halfFov = fov.get() / 2;
		AABB area = self.getBoundingBox().inflate(max + 1);
		for (Entity entity : client.level.getEntities(self, area, entity -> accepts(self, entity))) {
			if (distance(self, entity) > max) continue;
			if (fov.get() < 360 && Rotations.angleTo(self, entity.getBoundingBox().getCenter()) > halfFov) continue;
			if (!throughWalls.get() && !self.hasLineOfSight(entity)) continue;
			found.add((LivingEntity) entity);
		}

		Comparator<LivingEntity> order = switch (priority.get()) {
			case CLOSEST -> Comparator.comparingDouble(e -> distance(self, e));
			case LOWEST_HEALTH -> Comparator.comparingDouble(e -> e.getHealth() + e.getAbsorptionAmount());
			case SMALLEST_ANGLE -> Comparator.comparingDouble(e -> Rotations.angleTo(self, e.getBoundingBox().getCenter()));
		};
		found.sort(order);
		return found;
	}

	public static Vec3 aimPoint(LocalPlayer self, LivingEntity target, AimPoint point) {
		AABB box = target.getBoundingBox();
		return switch (point) {
			case BODY -> box.getCenter();
			case HEAD -> new Vec3(target.getX(), target.getEyeY(), target.getZ());
			case NEAREST -> {
				Vec3 eye = self.getEyePosition();
				yield new Vec3(Math.clamp(eye.x, box.minX, box.maxX), Math.clamp(eye.y, box.minY, box.maxY), Math.clamp(eye.z, box.minZ, box.maxZ));
			}
		};
	}
}
