package io.github.theamethyx.femboyclient.client.combat;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Client-side combat bookkeeping shared by the combat HUD modules: clicks per second, attacks, hits that landed,
 * combo, reach and the last attacked entity.
 *
 * <p>A hit counts as landed when the target's hurt animation starts within a few ticks of the attack. The server
 * sends that animation for every damaging hit, so this works on any server, but it cannot tell your hit from
 * someone else's hit on the same target in the same moment.
 */
public final class CombatTracker {
	/** Ticks after an attack during which the target's hurt animation counts as our hit. */
	static final int LAND_WINDOW_TICKS = 12;
	private static final long CPS_WINDOW_MS = 1000;

	private final Deque<Long> leftClicks = new ArrayDeque<>();
	private final Deque<Long> rightClicks = new ArrayDeque<>();
	private final List<PendingHit> pending = new ArrayList<>();
	private ClientLevel level;
	private int lastTargetId = -1;
	private long lastAttackMillis;
	private long lastLandedMillis;
	private int attacks;
	private int landed;
	private int combo;
	private int bestCombo;
	private double lastReach = Double.NaN;
	private int ownHurtTime;
	private long lastHurtMillis;

	private static final class PendingHit {
		final int entityId;
		final int deadline;
		final double reach;
		/** Lowest hurt-animation value seen since the attack; a jump above it means a new hit. */
		int lowestHurtTime;

		PendingHit(int entityId, int deadline, int hurtTime, double reach) {
			this.entityId = entityId;
			this.deadline = deadline;
			this.lowestHurtTime = hurtTime;
			this.reach = reach;
		}
	}

	private int tickCount;

	public void onClick(int button) {
		long now = System.currentTimeMillis();
		if (button == 0) {
			leftClicks.addLast(now);
		} else if (button == 1) {
			rightClicks.addLast(now);
		}
	}

	/** Called before the attack packet is sent. */
	public void onAttack(LocalPlayer player, Entity target) {
		attacks++;
		lastAttackMillis = System.currentTimeMillis();
		lastTargetId = target.getId();
		if (target instanceof LivingEntity living) {
			Vec3 eye = player.getEyePosition();
			double reach = Math.sqrt(target.getBoundingBox().distanceToSqr(eye));
			pending.add(new PendingHit(target.getId(), tickCount + LAND_WINDOW_TICKS, living.hurtTime, reach));
		}
	}

	public void tick(Minecraft client, long comboTimeoutMillis) {
		tickCount++;
		LocalPlayer player = client.player;
		if (client.level != level) {
			reset();
			level = client.level;
		}

		if (player == null || level == null) {
			return;
		}

		long now = System.currentTimeMillis();
		trim(leftClicks, now);
		trim(rightClicks, now);

		// Taking damage ends the combo.
		if (player.hurtTime > ownHurtTime) {
			lastHurtMillis = now;
			combo = 0;
		}

		ownHurtTime = player.hurtTime;
		if (combo > 0 && now - lastLandedMillis > comboTimeoutMillis) {
			combo = 0;
		}

		for (Iterator<PendingHit> it = pending.iterator(); it.hasNext(); ) {
			PendingHit hit = it.next();
			Entity entity = level.getEntity(hit.entityId);
			if (entity instanceof LivingEntity living && living.hurtTime > hit.lowestHurtTime) {
				landed++;
				combo++;
				bestCombo = Math.max(bestCombo, combo);
				lastReach = hit.reach;
				lastLandedMillis = now;
				it.remove();
			} else if (tickCount > hit.deadline || !(entity instanceof LivingEntity)) {
				it.remove();
			} else {
				// The animation counts down; follow it so a fresh hit is still seen as an increase.
				hit.lowestHurtTime = Math.min(hit.lowestHurtTime, ((LivingEntity) entity).hurtTime);
			}
		}
	}

	private static void trim(Deque<Long> clicks, long now) {
		while (!clicks.isEmpty() && now - clicks.peekFirst() > CPS_WINDOW_MS) {
			clicks.removeFirst();
		}
	}

	public void reset() {
		leftClicks.clear();
		rightClicks.clear();
		pending.clear();
		lastTargetId = -1;
		attacks = 0;
		landed = 0;
		combo = 0;
		bestCombo = 0;
		lastReach = Double.NaN;
		ownHurtTime = 0;
	}

	/**
	 * Whether you attacked or were hurt within the last {@code windowMillis} (used by HUD visibility conditions and
	 * rules). Being hurt by a fall counts too: the client cannot always tell damage sources apart.
	 */
	public boolean inCombat(long windowMillis) {
		long now = System.currentTimeMillis();
		return now - lastAttackMillis < windowMillis || now - lastHurtMillis < windowMillis;
	}

	public int leftCps() {
		trim(leftClicks, System.currentTimeMillis());
		return leftClicks.size();
	}

	public int rightCps() {
		trim(rightClicks, System.currentTimeMillis());
		return rightClicks.size();
	}

	public int attacks() {
		return attacks;
	}

	public int landed() {
		return landed;
	}

	public int combo() {
		return combo;
	}

	public int bestCombo() {
		return bestCombo;
	}

	/** Distance from your eyes to the target's hitbox for the last hit that landed, or NaN. */
	public double lastReach() {
		return lastReach;
	}

	/** The entity you attacked last, if it still exists and was attacked within {@code maxAgeMillis}. */
	public LivingEntity lastTarget(long maxAgeMillis) {
		if (level == null || lastTargetId < 0 || System.currentTimeMillis() - lastAttackMillis > maxAgeMillis) {
			return null;
		}

		return level.getEntity(lastTargetId) instanceof LivingEntity living && living.isAlive() ? living : null;
	}
}
