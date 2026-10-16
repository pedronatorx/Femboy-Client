package io.github.theamethyx.femboyclient.client.modules;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;

import io.github.theamethyx.femboyclient.client.FemboyClient;
import io.github.theamethyx.femboyclient.core.module.Module;
import io.github.theamethyx.femboyclient.core.module.ModuleCategory;
import io.github.theamethyx.femboyclient.core.setting.BooleanSetting;

/**
 * Saves hunger by not telling the server that you sprint. The server charges hunger for sprinting on the ground (0.1 per
 * block) and for sprint-jumps (0.2 instead of 0.05) from the sprint state you report; walking costs nothing in 1.21.11.
 * You still move at sprint speed. Because the server then treats your hits as non-sprinting, they lose the sprint
 * knockback unless "Report sprint when attacking" is on. Sandbox only.
 */
public final class AntiHungerModule extends Module {
	private final BooleanSetting whileAttacking = settings().add(new BooleanSetting("attack_sprint", "Report sprint when attacking",
			"Tell the server you sprint for a moment before each hit, so sprint knockback still works (that moment costs normal hunger).",
			true));

	/** Ticks left in which the real sprint state is reported (set when attacking). */
	private int reportRealFor;

	public AntiHungerModule() {
		super("anti_hunger", "Anti Hunger", "Sprinting costs no hunger: the server is not told that you sprint.", ModuleCategory.MOVEMENT,
				false, NO_KEY);
	}

	@Override
	public boolean testEnvironmentOnly() {
		return true;
	}

	@Override
	protected void onTick() {
		if (reportRealFor > 0) {
			reportRealFor--;
		}
	}

	/** Called before an attack; with the setting on, the real sprint state is reported for the next ticks. */
	public static void beforeAttack() {
		AntiHungerModule module = active();
		LocalPlayer player = Minecraft.getInstance().player;
		if (module != null && module.whileAttacking.get() && player != null && player.isSprinting()) {
			// The attack packet goes out before the player's tick, so the sprint state has to be sent right now.
			player.connection.send(new ServerboundPlayerCommandPacket(player, ServerboundPlayerCommandPacket.Action.START_SPRINTING));
			module.reportRealFor = 2;
		}
	}

	private static AntiHungerModule active() {
		FemboyClient femboy = FemboyClient.get();
		AntiHungerModule module = femboy == null ? null : femboy.modules().get(AntiHungerModule.class);
		return module != null && module.isRunning() ? module : null;
	}

	/** The sprint state to report to the server ({@code LocalPlayer.sendIsSprintingIfNeeded}). */
	public static boolean reportedSprinting(boolean actual) {
		AntiHungerModule module = active();
		LocalPlayer player = Minecraft.getInstance().player;
		if (module == null || !actual || player == null || module.reportRealFor > 0) {
			return actual;
		}

		return false;
	}
}
