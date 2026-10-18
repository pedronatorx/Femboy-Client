package io.github.theamethyx.femboyclient.client.modules;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.phys.Vec3;

import io.github.theamethyx.femboyclient.core.module.Module;
import io.github.theamethyx.femboyclient.core.module.ModuleCategory;
import io.github.theamethyx.femboyclient.core.setting.EnumSetting;
import io.github.theamethyx.femboyclient.core.setting.NumberSetting;

/**
 * Flying outside creative mode. A dedicated server kicks flying players unless {@code allow-flight=true} is set in
 * its server.properties; nothing here hides flying from that check. Fall damage still applies when you land after
 * descending. Runs only in test environments.
 */
public final class FlightModule extends Module {
	public enum Mode implements EnumSetting.Labeled {
		MOTION("Hover and move"), CREATIVE("Creative-style");

		private final String label;

		Mode(String label) {
			this.label = label;
		}

		@Override
		public String label() {
			return label;
		}
	}

	private final EnumSetting<Mode> mode = settings().add(new EnumSetting<>("mode", "Mode", "Hover and move: you hover in place and move with "
			+ "your movement keys, jump and sneak. Creative-style: flying works like in creative mode.", Mode.MOTION));
	private final NumberSetting speed = settings().add(new NumberSetting("speed", "Speed", "Horizontal speed.", 1, 0.1, 5, 0.1, "blocks/tick")
			.visibleWhen(() -> mode.get() == Mode.MOTION));
	private final NumberSetting verticalSpeed = settings().add(new NumberSetting("vertical_speed", "Vertical speed",
			"Up and down speed with jump and sneak.", 0.5, 0.1, 3, 0.1, "blocks/tick")).visibleWhen(() -> mode.get() == Mode.MOTION);
	private final NumberSetting creativeSpeed = settings().add(new NumberSetting("creative_speed", "Flying speed",
			"Creative flying speed; 1x is vanilla.", 1, 0.5, 5, 0.25, "x")).visibleWhen(() -> mode.get() == Mode.CREATIVE);

	private boolean changedAbilities;
	private float originalFlyingSpeed;

	public FlightModule() {
		super("flight", "Flight", "Fly outside creative mode: hover-and-move or creative-style.", ModuleCategory.MOVEMENT, false, NO_KEY);
	}

	@Override
	public boolean testEnvironmentOnly() {
		return true;
	}

	@Override
	public java.util.Set<String> conflicts() {
		// All three set your motion every tick; together the last one wins and the others only fight it.
		return java.util.Set.of("elytra_flight", "speed");
	}

	@Override
	protected void onTick() {
		LocalPlayer player = Minecraft.getInstance().player;
		Abilities abilities = player.getAbilities();
		if (mode.get() == Mode.CREATIVE) {
			if (!changedAbilities) {
				originalFlyingSpeed = abilities.getFlyingSpeed();
				changedAbilities = true;
			}

			abilities.mayfly = true;
			abilities.setFlyingSpeed(0.05f * creativeSpeed.get().floatValue());
			return;
		}

		restoreAbilities(player);
		Options options = Minecraft.getInstance().options;
		double forward = (options.keyUp.isDown() ? 1 : 0) - (options.keyDown.isDown() ? 1 : 0);
		double strafe = (options.keyLeft.isDown() ? 1 : 0) - (options.keyRight.isDown() ? 1 : 0);
		double up = (options.keyJump.isDown() ? 1 : 0) - (options.keyShift.isDown() ? 1 : 0);
		double length = Math.sqrt(forward * forward + strafe * strafe);
		double x = 0;
		double z = 0;
		if (length > 0) {
			float yaw = player.getYRot() * Mth.DEG_TO_RAD;
			double f = forward / length * speed.get();
			double s = strafe / length * speed.get();
			x = -Mth.sin(yaw) * f + Mth.cos(yaw) * s;
			z = Mth.cos(yaw) * f + Mth.sin(yaw) * s;
		}

		player.setDeltaMovement(new Vec3(x, up * verticalSpeed.get(), z));
	}

	@Override
	protected void onDisable() {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player != null) {
			restoreAbilities(player);
		}
	}

	private void restoreAbilities(LocalPlayer player) {
		if (!changedAbilities) {
			return;
		}

		Abilities abilities = player.getAbilities();
		boolean creativeLike = player.isCreative() || player.isSpectator();
		abilities.mayfly = creativeLike;
		if (!creativeLike) {
			abilities.flying = false;
		}

		abilities.setFlyingSpeed(originalFlyingSpeed);
		changedAbilities = false;
	}
}
