package io.github.theamethyx.femboyclient.client.modules;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Random;

import com.google.gson.JsonObject;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import io.github.theamethyx.femboyclient.client.FemboyClient;
import io.github.theamethyx.femboyclient.client.gui.Theme;
import io.github.theamethyx.femboyclient.client.hud.HudAnchor;
import io.github.theamethyx.femboyclient.client.hud.HudLine;
import io.github.theamethyx.femboyclient.client.hud.Notifications;
import io.github.theamethyx.femboyclient.client.module.ClientHooks;
import io.github.theamethyx.femboyclient.client.render.RenderingUtilities;
import io.github.theamethyx.femboyclient.core.module.Module;
import io.github.theamethyx.femboyclient.core.module.ModuleCategory;
import io.github.theamethyx.femboyclient.core.setting.BooleanSetting;
import io.github.theamethyx.femboyclient.core.setting.ColorSetting;
import io.github.theamethyx.femboyclient.core.setting.NumberSetting;

/**
 * Aim practice with targets that exist only on your screen. Turning it on starts a round: targets appear in front of you,
 * and while it runs a left click is a shot at them, never an attack or a block break. Hits, misses, accuracy and reaction
 * time are shown on the HUD; the best score is kept.
 */
public final class AimTrainerModule extends Module implements ClientHooks.WorldRender, ClientHooks.HudBlock {
	private final NumberSetting count = settings().add(new NumberSetting("count", "Targets", "Targets at the same time.", 1, 1, 5, 1, ""));
	private final NumberSetting size = settings().add(new NumberSetting("size", "Target size", "Width of each target.", 0.6, 0.2, 1.5, 0.05, "blocks"));
	private final NumberSetting minDistance = settings().add(new NumberSetting("min_distance", "Nearest", "Closest a target appears.", 3, 2, 10, 0.5, "blocks"));
	private final NumberSetting maxDistance = settings().add(new NumberSetting("max_distance", "Farthest", "Farthest a target appears.", 6, 2, 16, 0.5, "blocks"));
	private final NumberSetting spread = settings().add(new NumberSetting("spread", "Spread", "Horizontal angle targets appear in, around where you looked at the start.",
			70, 10, 180, 5, "°"));
	private final BooleanSetting moving = settings().add(new BooleanSetting("moving", "Moving targets", "Targets strafe sideways, like a player.", false));
	private final NumberSetting speed = settings().add(new NumberSetting("speed", "Movement speed", "How fast moving targets strafe.", 3, 0.5, 10, 0.5, "blocks/s"))
			.visibleWhen(moving::get);
	private final NumberSetting duration = settings().add(new NumberSetting("duration", "Round length", "Seconds per round; 0 trains until you turn it off.",
			30, 0, 300, 5, "s"));
	private final ColorSetting color = settings().add(new ColorSetting("color", "Target color", "Color of the targets.", 0xE070E8));

	/** One target; {@code velocity} is in blocks per tick. */
	public record Target(Vec3 center, Vec3 velocity, long spawnedAt, int ticks) {
		public AABB box(double size) {
			return AABB.ofSize(center, size, size, size);
		}
	}

	private final Random random = new Random();
	private final List<Target> targets = new ArrayList<>();
	private float baseYaw;
	private float basePitch;
	private long startedAt;
	private boolean running;
	private int hits;
	private int misses;
	private long reactionTotal;
	private int best;

	public AimTrainerModule() {
		super("aim_trainer", "Aim Trainer", "Practice aiming at targets only you can see; tracks hits, accuracy and reaction time.",
				ModuleCategory.COMBAT, false, NO_KEY);
	}

	@Override
	public Tier tier() {
		return Tier.ASSIST;
	}

	public List<Target> targets() {
		return List.copyOf(targets);
	}

	public int hits() {
		return hits;
	}

	public int misses() {
		return misses;
	}

	public int best() {
		return best;
	}

	public boolean roundRunning() {
		return running;
	}

	@Override
	protected void onEnable() {
		LocalPlayer player = Minecraft.getInstance().player;
		targets.clear();
		hits = 0;
		misses = 0;
		reactionTotal = 0;
		running = player != null;
		if (player != null) {
			baseYaw = player.getYRot();
			basePitch = Math.clamp(player.getXRot(), -30, 30);
			startedAt = System.currentTimeMillis();
			while (targets.size() < count.intValue()) {
				targets.add(spawn(player));
			}
		}
	}

	@Override
	protected void onDisable() {
		targets.clear();
		running = false;
	}

	private Target spawn(LocalPlayer player) {
		double low = Math.min(minDistance.get(), maxDistance.get());
		double high = Math.max(minDistance.get(), maxDistance.get());
		double distance = low + random.nextDouble() * (high - low);
		float yaw = baseYaw + (float) ((random.nextDouble() - 0.5) * spread.get());
		float pitch = basePitch + (float) ((random.nextDouble() - 0.5) * spread.get() * 0.35);
		Vec3 direction = Vec3.directionFromRotation(pitch, yaw);
		Vec3 velocity = Vec3.ZERO;
		if (moving.get()) {
			Vec3 side = Vec3.directionFromRotation(0, yaw + 90).scale(speed.get() / 20 * (random.nextBoolean() ? 1 : -1));
			velocity = side;
		}

		return new Target(player.getEyePosition().add(direction.scale(distance)), velocity, System.currentTimeMillis(), 0);
	}

	@Override
	protected void onTick() {
		LocalPlayer player = Minecraft.getInstance().player;
		if (!running) {
			return;
		}

		for (int i = 0; i < targets.size(); i++) {
			Target target = targets.get(i);
			if (target.velocity() != Vec3.ZERO) {
				Vec3 next = target.center().add(target.velocity());
				// Turn around every 2 seconds, so the target strafes back and forth.
				Vec3 velocity = (target.ticks() + 1) % 40 == 0 ? target.velocity().scale(-1) : target.velocity();
				targets.set(i, new Target(next, velocity, target.spawnedAt(), target.ticks() + 1));
			}
		}

		if (duration.intValue() > 0 && System.currentTimeMillis() - startedAt >= duration.get() * 1000) {
			finishRound();
		}

		if (targets.size() < count.intValue()) {
			targets.add(spawn(player));
		}
	}

	private void finishRound() {
		running = false;
		targets.clear();
		boolean record = hits > best;
		best = Math.max(best, hits);
		Notifications.show("Aim Trainer", hits + " hits, " + accuracyText() + " accuracy, " + reactionText() + (record ? "  - new best!" : ""),
				record ? Theme.SUCCESS : Theme.ACCENT);
		FemboyClient femboy = FemboyClient.get();
		if (femboy != null && femboy.config() != null) {
			femboy.config().markDirty();
		}
	}

	/** Handles a left click; returns true when the trainer used it (the click then does nothing else). */
	public static boolean onAttackClick() {
		FemboyClient femboy = FemboyClient.get();
		AimTrainerModule module = femboy == null ? null : femboy.modules().get(AimTrainerModule.class);
		if (module == null || !module.isRunning() || Minecraft.getInstance().player == null) {
			return false;
		}

		if (module.running) {
			module.shoot(Minecraft.getInstance().player);
		}

		return true;
	}

	/** Whether the trainer is taking clicks (so holding attack does not mine). */
	public static boolean active() {
		FemboyClient femboy = FemboyClient.get();
		AimTrainerModule module = femboy == null ? null : femboy.modules().get(AimTrainerModule.class);
		return module != null && module.isRunning();
	}

	private void shoot(LocalPlayer player) {
		Vec3 eye = player.getEyePosition();
		Vec3 end = eye.add(player.getLookAngle().scale(32));
		int hitIndex = -1;
		double nearest = Double.MAX_VALUE;
		for (int i = 0; i < targets.size(); i++) {
			Optional<Vec3> point = targets.get(i).box(size.get()).clip(eye, end);
			if (point.isPresent() && point.get().distanceToSqr(eye) < nearest) {
				nearest = point.get().distanceToSqr(eye);
				hitIndex = i;
			}
		}

		if (hitIndex < 0) {
			misses++;
			return;
		}

		hits++;
		reactionTotal += System.currentTimeMillis() - targets.get(hitIndex).spawnedAt();
		targets.set(hitIndex, spawn(player));
	}

	private String accuracyText() {
		int shots = hits + misses;
		return shots == 0 ? "-" : Math.round(100.0 * hits / shots) + "%";
	}

	private String reactionText() {
		return hits == 0 ? "no hits" : String.format(Locale.ROOT, "avg %d ms", reactionTotal / hits);
	}

	@Override
	protected void saveExtra(JsonObject json) {
		json.addProperty("best", best);
	}

	@Override
	protected void loadExtra(JsonObject json, List<String> warnings) {
		if (json.get("best") != null && json.get("best").isJsonPrimitive()) {
			best = Math.max(0, json.get("best").getAsInt());
		}
	}

	@Override
	protected void resetExtra() {
		best = 0;
	}

	@Override
	public void renderWorld(RenderingUtilities render) {
		int rgb = color.get();
		for (Target target : targets) {
			AABB box = target.box(size.get());
			render.filledBox(box, (0x70 << 24) | (rgb & 0xFFFFFF), true);
			render.boxOutline(box, 0xFF000000 | rgb, 2f, true);
		}
	}

	@Override
	public HudAnchor hudAnchor() {
		return HudAnchor.TOP_LEFT;
	}

	@Override
	public void buildHud(List<HudLine> lines) {
		String left = running && duration.intValue() > 0
				? "  " + Math.max(0, (duration.intValue() * 1000 - (System.currentTimeMillis() - startedAt)) / 1000) + " s left" : running ? "" : "  round over";
		lines.add(HudLine.of("Aim Trainer", hits + " hits  " + misses + " misses  " + accuracyText() + "  " + reactionText() + left, Theme.ACCENT_STRONG));
		lines.add(HudLine.of("Best", best + " hits", Theme.TEXT_SECONDARY));
	}
}
