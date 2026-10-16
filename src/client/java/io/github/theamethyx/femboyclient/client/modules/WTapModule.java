package io.github.theamethyx.femboyclient.client.modules;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

import io.github.theamethyx.femboyclient.client.FemboyClient;
import io.github.theamethyx.femboyclient.core.module.Module;
import io.github.theamethyx.femboyclient.core.module.ModuleCategory;
import io.github.theamethyx.femboyclient.core.setting.NumberSetting;

/**
 * Sprint reset (W-tap): after a sprinting hit, stops sprinting briefly and starts again, so your next hit carries the
 * extra sprint knockback again. Sandbox only.
 */
public final class WTapModule extends Module {
	private final NumberSetting pause = settings().add(new NumberSetting("pause", "Pause", "Ticks without sprinting after a hit.", 2, 1, 6, 1, "ticks"));

	private int left;
	private int resets;

	public WTapModule() {
		super("w_tap", "Sprint Reset", "After a sprinting hit, pauses sprint for a moment so the next hit gets sprint knockback.", ModuleCategory.COMBAT, false, NO_KEY);
	}

	@Override
	public boolean testEnvironmentOnly() {
		return true;
	}

	public int resets() {
		return resets;
	}

	public boolean pausing() {
		return left > 0;
	}

	/** Called after every attack. */
	public static void afterAttack(LocalPlayer player) {
		FemboyClient femboy = FemboyClient.get();
		WTapModule module = femboy == null ? null : femboy.modules().get(WTapModule.class);
		if (module != null && module.isRunning() && player.isSprinting()) {
			player.setSprinting(false);
			module.left = module.pause.intValue();
			module.resets++;
		}
	}

	@Override
	protected void onTick() {
		LocalPlayer player = Minecraft.getInstance().player;
		if (left > 0) {
			player.setSprinting(false);
			if (--left == 0 && Minecraft.getInstance().options.keyUp.isDown()) {
				player.setSprinting(true);
			}
		}
	}
}
