package io.github.theamethyx.femboyclient.client.modules;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import io.github.theamethyx.femboyclient.client.FemboyClient;
import io.github.theamethyx.femboyclient.core.module.Module;
import io.github.theamethyx.femboyclient.core.module.ModuleCategory;
import io.github.theamethyx.femboyclient.core.setting.NumberSetting;

/**
 * Turns a fully charged click on the ground into a critical hit: jumps, then attacks on the way down (vanilla crits
 * need you falling, not sprinting, charged above 90%). Uses a real jump, nothing faked. Sandbox only.
 */
public final class AutoCriticalsModule extends Module {
	private final NumberSetting charge = settings().add(new NumberSetting("charge", "Minimum charge", "Only for clicks at this attack charge or more.", 90, 50, 100, 5, "%"));

	private LivingEntity pending;
	private int waited;
	private int crits;

	public AutoCriticalsModule() {
		super("auto_criticals", "Auto Criticals", "Jumps and hits on the way down so charged hits are critical.", ModuleCategory.COMBAT, false, NO_KEY);
	}

	@Override
	public boolean testEnvironmentOnly() {
		return true;
	}

	@Override
	public String lowImpactLimitation() {
		return "it adds a jump and a delayed attack to your hits.";
	}

	public int crits() {
		return crits;
	}

	/** Called on an attack click before vanilla; returns true when the hit is postponed to the fall. */
	public static boolean onAttackClick() {
		FemboyClient femboy = FemboyClient.get();
		AutoCriticalsModule module = femboy == null ? null : femboy.modules().get(AutoCriticalsModule.class);
		return module != null && module.isRunning() && module.tryStart();
	}

	private boolean tryStart() {
		Minecraft client = Minecraft.getInstance();
		LocalPlayer player = client.player;
		Entity target = client.crosshairPickEntity;
		if (pending != null || !(target instanceof LivingEntity living) || !player.onGround() || player.isInWater() || player.onClimbable()
				|| player.isPassenger() || player.getAttackStrengthScale(0.5f) * 100 < charge.get()) {
			return false;
		}

		player.jumpFromGround();
		pending = living;
		waited = 0;
		return true;
	}

	@Override
	protected void onTick() {
		if (pending == null) {
			return;
		}

		Minecraft client = Minecraft.getInstance();
		LocalPlayer player = client.player;
		boolean falling = !player.onGround() && player.getDeltaMovement().y < 0 && player.fallDistance > 0;
		if (falling || player.onGround() && waited > 2 || ++waited > 15) {
			if (pending.isAlive() && pending.distanceTo(player) <= player.entityInteractionRange() + 1) {
				player.setSprinting(false);
				if (falling) {
					crits++;
				}

				client.gameMode.attack(player, pending);
				player.swing(InteractionHand.MAIN_HAND);
			}

			pending = null;
		}
	}

	@Override
	protected void onDisable() {
		pending = null;
	}
}
