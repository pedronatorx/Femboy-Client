package io.github.theamethyx.femboyclient.client.modules;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;

import io.github.theamethyx.femboyclient.client.combat.AttackTiming;
import io.github.theamethyx.femboyclient.client.combat.Rotations;
import io.github.theamethyx.femboyclient.client.combat.TargetSettings;
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
 * Attacks the best target in range automatically, turning your view toward it. Attacks go through the normal
 * attack code, so Auto Attribute Swap and the combat statistics see them. Runs only in test environments.
 */
public final class KillAuraModule extends Module implements ClientHooks.HudBlock {
	private final TargetSettings targets = new TargetSettings(settings(), 3.0, 6.0, 360, false);
	private final AttackTiming timing = new AttackTiming(settings());

	{
		settings().section("Rotation");
	}

	private final BooleanSetting rotate = settings().add(new BooleanSetting("rotate", "Turn toward the target",
			"Turn your view to the target before attacking.", true));
	private final NumberSetting turnSpeed = settings().add(new NumberSetting("turn_speed", "Turn speed",
			"Most degrees turned per tick. 180 turns instantly.", 180, 5, 180, 5, "°/tick")).visibleWhen(rotate::get);
	private final NumberSetting facing = settings().add(new NumberSetting("facing", "Attack within",
			"Only attack once the target is this close to your crosshair (after turning).", 30, 5, 180, 5, "°")).visibleWhen(rotate::get);
	private final EnumSetting<TargetSettings.AimPoint> aim = settings().add(new EnumSetting<>("aim", "Aim at", "Point on the target to turn to.",
			TargetSettings.AimPoint.BODY)).visibleWhen(rotate::get);

	{
		settings().section("Display");
	}

	private final BooleanSetting hud = settings().add(new BooleanSetting("hud", "Show on HUD", "Show the current target and attack count.", true));

	private LivingEntity target;
	private int attacks;

	public KillAuraModule() {
		super("kill_aura", "KillAura", "Attacks the best target in range automatically, with target filters, timing and rotation settings.",
				ModuleCategory.AUTOMATION, false, NO_KEY);
	}

	@Override
	public boolean testEnvironmentOnly() {
		return false;
	}

	public LivingEntity target() {
		return target;
	}

	public int attacks() {
		return attacks;
	}

	@Override
	protected void onEnable() {
		timing.reset();
		attacks = 0;
	}

	@Override
	protected void onDisable() {
		target = null;
	}

	@Override
	protected void onTick() {
		Minecraft client = Minecraft.getInstance();
		LocalPlayer player = client.player;
		if (client.gameMode == null || client.screen != null || !timing.handReady(player)) {
			target = null;
			return;
		}

		List<LivingEntity> found = targets.find(player);
		target = found.isEmpty() ? null : found.getFirst();
		if (target == null) {
			return;
		}

		if (rotate.get()) {
			double remaining = Rotations.turnToward(player, TargetSettings.aimPoint(player, target, aim.get()), turnSpeed.get().floatValue(), true);
			if (remaining > facing.get()) {
				return;
			}
		}

		if (!timing.ready(player)) {
			return;
		}

		client.gameMode.attack(player, target);
		player.swing(InteractionHand.MAIN_HAND);
		timing.attacked();
		attacks++;
	}

	@Override
	public HudAnchor hudAnchor() {
		return HudAnchor.BOTTOM_RIGHT;
	}

	@Override
	public void buildHud(List<HudLine> lines) {
		if (hud.get()) {
			lines.add(HudLine.of("KillAura", target == null ? "no target" : target.getDisplayName().getString() + "  (" + attacks + ")",
					target == null ? Theme.TEXT_MUTED : Theme.ACCENT_STRONG));
		}
	}
}
