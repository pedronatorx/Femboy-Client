package io.github.theamethyx.femboyclient.client.modules;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;

import io.github.theamethyx.femboyclient.core.module.Module;
import io.github.theamethyx.femboyclient.core.module.ModuleCategory;
import io.github.theamethyx.femboyclient.core.setting.NumberSetting;

/**
 * While gliding: fly where you look with forward, rise with jump, sink with sneak, and hover with no key. Durability is
 * used as normal. Sandbox only.
 */
public final class ElytraFlightModule extends Module {
	private final NumberSetting speed = settings().add(new NumberSetting("speed", "Speed", "Speed toward where you look.", 1.5, 0.2, 5, 0.1, "blocks/tick"));
	private final NumberSetting vertical = settings().add(new NumberSetting("vertical", "Vertical speed", "Up and down speed.", 0.6, 0.1, 3, 0.1, "blocks/tick"));

	public ElytraFlightModule() {
		super("elytra_flight", "Elytra Flight", "Controlled elytra flight: forward, up, down and hover.", ModuleCategory.MOVEMENT, false, NO_KEY);
	}

	@Override
	public boolean testEnvironmentOnly() {
		return true;
	}

	@Override
	protected void onTick() {
		LocalPlayer player = Minecraft.getInstance().player;
		if (!player.isFallFlying()) {
			return;
		}

		Options o = Minecraft.getInstance().options;
		Vec3 look = player.getLookAngle();
		Vec3 horizontal = new Vec3(look.x, 0, look.z).normalize();
		double forward = o.keyUp.isDown() ? speed.get() : o.keyDown.isDown() ? -speed.get() / 2 : 0;
		double up = o.keyJump.isDown() ? vertical.get() : o.keyShift.isDown() ? -vertical.get() : 0;
		player.setDeltaMovement(horizontal.x * forward, up, horizontal.z * forward);
	}
}
