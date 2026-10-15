package io.github.theamethyx.femboyclient.client.modules;

import java.util.List;
import java.util.Locale;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import io.github.theamethyx.femboyclient.client.gui.Theme;
import io.github.theamethyx.femboyclient.client.hud.HudAnchor;
import io.github.theamethyx.femboyclient.client.hud.HudLine;
import io.github.theamethyx.femboyclient.client.module.ClientHooks;
import io.github.theamethyx.femboyclient.core.module.Module;
import io.github.theamethyx.femboyclient.core.module.ModuleCategory;
import io.github.theamethyx.femboyclient.core.setting.BooleanSetting;
import io.github.theamethyx.femboyclient.core.setting.EnumSetting;

/**
 * Speed, vertical speed, fall distance and a landing prediction with the fall damage vanilla would deal
 * ({@code LivingEntity.calculateFallDamage}: fall distance minus safe fall distance, times the landing block's
 * multiplier and the fall damage attribute). Feather Falling and Protection reduce it further on the server.
 */
public final class MovementInfoModule extends Module implements ClientHooks.HudBlock {
	private static final double MAX_DROP = 384;

	private final EnumSetting<HudAnchor> anchor = settings().add(new EnumSetting<>("anchor", "Position",
			"Screen corner for this block.", HudAnchor.TOP_LEFT));
	private final BooleanSetting speed = settings().add(new BooleanSetting("speed", "Speed",
			"Horizontal speed in blocks per second, measured from your movement over the last tick.", true));
	private final BooleanSetting vertical = settings().add(new BooleanSetting("vertical", "Vertical speed", "Up (+) or down (-) speed.", true));
	private final BooleanSetting landing = settings().add(new BooleanSetting("landing", "Landing prediction",
			"While falling: distance to the ground, what you land on and the fall damage before armor enchantments.", true));
	private final BooleanSetting warning = settings().add(new BooleanSetting("warning", "Fall damage warning",
			"Highlight the prediction in red when the fall would deal at least your current health.", true));

	private Vec3 previous;
	private Vec3 motion = Vec3.ZERO;

	public MovementInfoModule() {
		super("movement_info", "Movement Info", "Speed, vertical speed, fall distance and a landing and fall damage prediction.",
				ModuleCategory.MOVEMENT, false, NO_KEY);
	}

	@Override
	public HudAnchor hudAnchor() {
		return anchor.get();
	}

	@Override
	protected void onDisable() {
		previous = null;
	}

	@Override
	protected void onTick() {
		LocalPlayer player = Minecraft.getInstance().player;
		Vec3 now = player.position();
		motion = previous == null || now.distanceToSqr(previous) > 100 ? Vec3.ZERO : now.subtract(previous);
		previous = now;
	}

	/** Measured horizontal speed in blocks per second. */
	public double horizontalSpeed() {
		return motion.horizontalDistance() * 20;
	}

	@Override
	public void buildHud(List<HudLine> lines) {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null) {
			return;
		}

		if (speed.get()) {
			lines.add(HudLine.of("Speed", String.format(Locale.ROOT, "%.2f b/s", horizontalSpeed())));
		}

		if (vertical.get()) {
			lines.add(HudLine.of("Vertical", String.format(Locale.ROOT, "%+.2f b/s", motion.y * 20)));
		}

		if (landing.get() && !player.onGround() && !player.isFallFlying() && !player.getAbilities().flying && motion.y < 0) {
			Landing prediction = predict(player);
			if (prediction == null) {
				lines.add(HudLine.of("Landing", "No ground within " + (int) MAX_DROP + " blocks", Theme.TEXT_MUTED));
				return;
			}

			lines.add(HudLine.of("Fall", String.format(Locale.ROOT, "%.1f m so far, %.1f m to go", player.fallDistance, prediction.drop)));
			float health = player.getHealth() + player.getAbsorptionAmount();
			boolean fatal = warning.get() && prediction.damage >= health;
			String text = prediction.reason != null ? prediction.reason
					: prediction.damage <= 0 ? "No damage" : prediction.damage + " damage" + (fatal ? " - FATAL" : "");
			lines.add(HudLine.of("Landing", text, fatal ? Theme.ERROR : prediction.damage > 0 ? Theme.WARNING : Theme.SUCCESS));
		}
	}

	/** Where a straight drop from here ends and what it costs; {@code null} if there is no ground below. */
	public record Landing(double drop, int damage, String reason) {
	}

	public static Landing predict(LocalPlayer player) {
		Vec3 feet = player.position();
		BlockHitResult hit = player.level().clip(new ClipContext(feet, feet.add(0, -MAX_DROP, 0), ClipContext.Block.COLLIDER,
				ClipContext.Fluid.ANY, player));
		if (hit.getType() == HitResult.Type.MISS) {
			return null;
		}

		double drop = Math.max(0, feet.y - hit.getLocation().y);
		if (player.hasEffect(MobEffects.SLOW_FALLING)) {
			return new Landing(drop, 0, "Slow Falling: no damage");
		}

		BlockState surface = player.level().getBlockState(hit.getBlockPos());
		if (!surface.getFluidState().isEmpty()) {
			return new Landing(drop, 0, "Into liquid: no damage");
		}

		double multiplier = surfaceMultiplier(surface, player.isShiftKeyDown());
		double total = player.fallDistance + drop;
		double power = total + 1.0E-6 - player.getAttributeValue(Attributes.SAFE_FALL_DISTANCE);
		int damage = (int) Math.floor(Math.max(0, power) * multiplier * player.getAttributeValue(Attributes.FALL_DAMAGE_MULTIPLIER));
		return new Landing(drop, damage, null);
	}

	/** The landing block's fall damage multiplier from its {@code fallOn} (1 for ordinary blocks). */
	static double surfaceMultiplier(BlockState state, boolean sneaking) {
		if (state.is(Blocks.SLIME_BLOCK)) return sneaking ? 1 : 0;
		if (state.is(Blocks.HAY_BLOCK) || state.is(Blocks.HONEY_BLOCK)) return 0.2;
		if (state.is(BlockTags.BEDS)) return 0.5;
		if (state.is(Blocks.POWDER_SNOW) || state.is(Blocks.COBWEB)) return 0;
		return 1;
	}
}
