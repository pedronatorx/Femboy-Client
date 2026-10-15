package io.github.theamethyx.femboyclient.client.modules;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.LivingEntity;

import io.github.theamethyx.femboyclient.client.combat.Rotations;
import io.github.theamethyx.femboyclient.client.combat.TargetSettings;
import io.github.theamethyx.femboyclient.core.module.Module;
import io.github.theamethyx.femboyclient.core.module.ModuleCategory;
import io.github.theamethyx.femboyclient.core.setting.BooleanSetting;
import io.github.theamethyx.femboyclient.core.setting.EnumSetting;
import io.github.theamethyx.femboyclient.core.setting.NumberSetting;

/** Gradually turns your view toward the best target near your crosshair. It never attacks. Runs only in test environments. */
public final class AimAssistModule extends Module {
	private final TargetSettings targets = new TargetSettings(settings(), 5.0, 8.0, 90, false);

	{
		settings().section("Aiming");
	}

	private final NumberSetting speed = settings().add(new NumberSetting("speed", "Speed", "Most degrees turned per tick.", 4, 0.5, 30, 0.5, "°/tick"));
	private final BooleanSetting vertical = settings().add(new BooleanSetting("vertical", "Vertical too", "Also adjust up and down.", true));
	private final BooleanSetting onlyAttacking = settings().add(new BooleanSetting("only_attacking", "Only while holding attack",
			"Assist only while the attack button is held.", false));
	private final BooleanSetting stopOnTarget = settings().add(new BooleanSetting("stop_on_target", "Stop on target",
			"Stop turning while the crosshair is already on the target.", true));
	private final EnumSetting<TargetSettings.AimPoint> aim = settings().add(new EnumSetting<>("aim", "Aim at", "Point on the target to turn to.",
			TargetSettings.AimPoint.BODY));

	private LivingEntity target;

	public AimAssistModule() {
		super("aim_assist", "Aim Assist", "Turns your view toward the target nearest your crosshair, at an adjustable speed.",
				ModuleCategory.AUTOMATION, false, NO_KEY);
	}

	@Override
	public boolean testEnvironmentOnly() {
		return true;
	}

	public LivingEntity target() {
		return target;
	}

	@Override
	protected void onDisable() {
		target = null;
	}

	@Override
	protected void onTick() {
		Minecraft client = Minecraft.getInstance();
		LocalPlayer player = client.player;
		target = null;
		if (client.screen != null || (onlyAttacking.get() && !client.options.keyAttack.isDown())) {
			return;
		}

		List<LivingEntity> found = targets.find(player);
		if (found.isEmpty()) {
			return;
		}

		target = found.getFirst();
		if (stopOnTarget.get() && client.crosshairPickEntity == target) {
			return;
		}

		Rotations.turnToward(player, TargetSettings.aimPoint(player, target, aim.get()), speed.get().floatValue(), vertical.get());
	}
}
