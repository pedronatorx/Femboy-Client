package io.github.theamethyx.femboyclient.client.combat;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Projectile flight simulated with vanilla's per-tick rules (1.21.11):
 * <ul>
 *   <li>arrows and tridents ({@code AbstractArrow.tick}): move, then inertia 0.99 (water 0.6, trident 0.99), then gravity 0.05;</li>
 *   <li>thrown items ({@code ThrowableProjectile.tick}): gravity (0.03, potions 0.05, experience bottles 0.07), then inertia
 *       0.99 (water 0.8), then move.</li>
 * </ul>
 * Launch spread ({@code Projectile.getMovementToShoot}) is random and not predicted; the path is the center of it.
 */
public final class Trajectory {
	public enum Physics {
		ARROW(0.05, true, 0.6), TRIDENT(0.05, true, 0.99), THROWN(0.03, false, 0.8), POTION(0.05, false, 0.8), BOTTLE(0.07, false, 0.8);

		final double gravity;
		final boolean moveFirst;
		final double waterInertia;

		Physics(double gravity, boolean moveFirst, double waterInertia) {
			this.gravity = gravity;
			this.moveFirst = moveFirst;
			this.waterInertia = waterInertia;
		}
	}

	/** Simulated path: points per tick, and what it hits ({@code hitEntity} or {@code hitBlock}, or neither within the limit). */
	public record Result(List<Vec3> points, Vec3 end, Entity hitEntity, BlockPos hitBlock) {
	}

	private Trajectory() {
	}

	/** Launch direction like {@code Projectile.shootFromRotation}: pitch {@code xRot}, yaw {@code yRot}, extra pitch {@code offset}. */
	public static Vec3 direction(float xRot, float yRot, float offset) {
		float rad = Mth.DEG_TO_RAD;
		double x = -Mth.sin(yRot * rad) * Mth.cos(xRot * rad);
		double y = -Mth.sin((xRot + offset) * rad);
		double z = Mth.cos(yRot * rad) * Mth.cos(xRot * rad);
		return new Vec3(x, y, z).normalize();
	}

	public static Result simulate(Level level, Entity owner, Vec3 start, Vec3 velocity, Physics physics, int maxTicks) {
		List<Vec3> points = new ArrayList<>();
		points.add(start);
		List<Entity> candidates = level.getEntities(owner, new AABB(start, start).inflate(maxTicks * velocity.length() * 0.6 + 2),
				entity -> entity.isAlive() && !entity.isSpectator() && entity.canBeHitByProjectile());
		Vec3 pos = start;
		Vec3 v = velocity;
		for (int tick = 0; tick < maxTicks; tick++) {
			boolean inWater = level.getFluidState(BlockPos.containing(pos)).is(FluidTags.WATER);
			if (!physics.moveFirst) {
				v = v.add(0, -physics.gravity, 0).scale(inWater ? physics.waterInertia : 0.99);
			}

			Vec3 next = pos.add(v);
			BlockHitResult block = level.clip(new ClipContext(pos, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, owner));
			Vec3 limit = block.getType() == HitResult.Type.MISS ? next : block.getLocation();
			Entity hitEntity = null;
			double best = Double.MAX_VALUE;
			for (Entity entity : candidates) {
				var hit = entity.getBoundingBox().inflate(0.3).clip(pos, limit);
				if (hit.isPresent() && hit.get().distanceToSqr(pos) < best) {
					best = hit.get().distanceToSqr(pos);
					hitEntity = entity;
					limit = hit.get();
				}
			}

			points.add(limit);
			if (hitEntity != null) {
				return new Result(points, limit, hitEntity, null);
			}

			if (block.getType() != HitResult.Type.MISS) {
				return new Result(points, limit, null, block.getBlockPos());
			}

			pos = next;
			if (physics.moveFirst) {
				v = v.scale(inWater ? physics.waterInertia : 0.99).add(0, -physics.gravity, 0);
			}

			if (pos.y < level.getMinY() - 64) {
				break;
			}
		}

		return new Result(points, pos, null, null);
	}
}
