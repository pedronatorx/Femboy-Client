package io.github.theamethyx.femboyclient.client.modules;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;

import io.github.theamethyx.femboyclient.client.combat.AttackTiming;
import io.github.theamethyx.femboyclient.client.combat.TargetSettings;
import io.github.theamethyx.femboyclient.core.module.Module;
import io.github.theamethyx.femboyclient.core.module.ModuleCategory;

/** Attacks when your crosshair is on a matching entity within your normal reach. Runs only in test environments. */
public final class TriggerBotModule extends Module {
	private final TargetSettings targets = new TargetSettings(settings(), 3.0, 6.0, 360, false);
	private final AttackTiming timing = new AttackTiming(settings());

	private int attacks;

	public TriggerBotModule() {
		super("trigger_bot", "Trigger Bot", "Attacks automatically when your crosshair is on a matching target.", ModuleCategory.AUTOMATION, false, NO_KEY);
		targets.fov.panelOnly();
		targets.priority.panelOnly();
	}

	@Override
	public boolean testEnvironmentOnly() {
		return true;
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
	protected void onTick() {
		Minecraft client = Minecraft.getInstance();
		LocalPlayer player = client.player;
		Entity entity = client.crosshairPickEntity;
		if (client.gameMode == null || client.screen != null || entity == null || !targets.accepts(player, entity)
				|| TargetSettings.distance(player, entity) > targets.range.get() || !timing.handReady(player) || !timing.ready(player)) {
			return;
		}

		client.gameMode.attack(player, entity);
		player.swing(InteractionHand.MAIN_HAND);
		timing.attacked();
		attacks++;
	}
}
