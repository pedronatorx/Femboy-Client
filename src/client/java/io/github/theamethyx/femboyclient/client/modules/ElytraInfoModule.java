package io.github.theamethyx.femboyclient.client.modules;

import java.util.List;
import java.util.Locale;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import io.github.theamethyx.femboyclient.client.combat.InventoryCounts;
import io.github.theamethyx.femboyclient.client.gui.Theme;
import io.github.theamethyx.femboyclient.client.hud.HudAnchor;
import io.github.theamethyx.femboyclient.client.hud.HudLine;
import io.github.theamethyx.femboyclient.client.module.ClientHooks;
import io.github.theamethyx.femboyclient.core.module.Module;
import io.github.theamethyx.femboyclient.core.module.ModuleCategory;
import io.github.theamethyx.femboyclient.core.setting.BooleanSetting;
import io.github.theamethyx.femboyclient.core.setting.EnumSetting;
import io.github.theamethyx.femboyclient.core.setting.NumberSetting;

/**
 * Flight information while wearing a glider: speed, height, pitch, durability, rockets and a collision warning.
 * The warning looks along your current velocity in a straight line; turns and rocket boosts change the real path.
 */
public final class ElytraInfoModule extends Module implements ClientHooks.HudBlock {
	private final EnumSetting<HudAnchor> anchor = settings().add(new EnumSetting<>("anchor", "Position",
			"Screen corner for this block.", HudAnchor.TOP_LEFT));
	private final BooleanSetting onlyGliding = settings().add(new BooleanSetting("only_gliding", "Only while gliding",
			"Hide the block while you walk with the elytra on.", true));
	private final NumberSetting lowDurability = settings().add(new NumberSetting("low_durability", "Low durability warning",
			"Durability shown in red at or below this percentage.", 15, 0, 50, 1, "%"));
	private final NumberSetting lookahead = settings().add(new NumberSetting("lookahead", "Collision warning",
			"Warn when your current direction hits a block within this time. 0 turns the warning off.", 1.5, 0, 5, 0.25, "s"));

	private boolean obstacleAhead;

	@Override
	public Tier tier() {
		return Tier.ASSIST;
	}

	public ElytraInfoModule() {
		super("elytra_info", "Elytra Info", "Flight speed, height, durability, rockets and a collision warning while gliding.",
				ModuleCategory.MOVEMENT, false, NO_KEY);
	}

	@Override
	public HudAnchor hudAnchor() {
		return anchor.get();
	}

	/** Whether the last frame warned about an obstacle (for tests). */
	public boolean obstacleAhead() {
		return obstacleAhead;
	}

	@Override
	public void buildHud(List<HudLine> lines) {
		obstacleAhead = false;
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null) {
			return;
		}

		ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);
		if (!chest.has(DataComponents.GLIDER) || (onlyGliding.get() && !player.isFallFlying())) {
			return;
		}

		Vec3 velocity = player.getDeltaMovement();
		lines.add(HudLine.of("Speed", String.format(Locale.ROOT, "%.1f b/s", velocity.length() * 20)));
		lines.add(HudLine.of("Height", String.format(Locale.ROOT, "Y %.0f  pitch %.0f°", player.getY(), player.getXRot())));
		if (chest.isDamageableItem()) {
			int left = chest.getMaxDamage() - chest.getDamageValue();
			double percent = 100.0 * left / chest.getMaxDamage();
			lines.add(HudLine.of("Elytra", left + "/" + chest.getMaxDamage(), percent <= lowDurability.get() ? Theme.ERROR : Theme.TEXT));
		}

		int rockets = InventoryCounts.count(player, stack -> stack.is(Items.FIREWORK_ROCKET));
		lines.add(HudLine.of("Rockets", String.valueOf(rockets), rockets == 0 ? Theme.WARNING : Theme.TEXT));

		double seconds = lookahead.get();
		if (seconds > 0 && player.isFallFlying() && velocity.lengthSqr() > 1.0E-4) {
			Double hitIn = timeToImpact(player, velocity, seconds);
			if (hitIn != null) {
				obstacleAhead = true;
				lines.add(HudLine.of("Obstacle", String.format(Locale.ROOT, "in %.1f s", hitIn), Theme.ERROR));
			}
		}
	}

	/** Seconds until the straight line along {@code velocity} hits a block, or null if not within {@code seconds}. */
	static Double timeToImpact(LocalPlayer player, Vec3 velocity, double seconds) {
		Vec3 from = player.position().add(0, player.getBbHeight() / 2, 0);
		Vec3 to = from.add(velocity.scale(20 * seconds));
		BlockHitResult hit = player.level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
		if (hit.getType() == HitResult.Type.MISS) {
			return null;
		}

		return from.distanceTo(hit.getLocation()) / (velocity.length() * 20);
	}
}
