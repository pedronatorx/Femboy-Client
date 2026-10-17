package io.github.theamethyx.femboyclient.client.modules;

import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

import io.github.theamethyx.femboyclient.client.FemboyClient;
import io.github.theamethyx.femboyclient.core.module.Module;
import io.github.theamethyx.femboyclient.core.module.ModuleCategory;
import io.github.theamethyx.femboyclient.core.setting.BooleanSetting;
import io.github.theamethyx.femboyclient.core.setting.NumberSetting;

/**
 * Scales the knockback the server gives you. Applied where the client receives the motion update after a hit and the
 * knockback of an explosion. Your client decides its own position, so the server accepts the result. Runs only in
 * test environments.
 */
public final class VelocityModule extends Module {
	private final NumberSetting horizontal = settings().add(new NumberSetting("horizontal", "Horizontal",
			"Share of sideways knockback you keep. 0% means none.", 0, 0, 100, 5, "%"));
	private final NumberSetting vertical = settings().add(new NumberSetting("vertical", "Vertical",
			"Share of upward knockback you keep.", 0, 0, 100, 5, "%"));
	private final BooleanSetting onlyHits = settings().add(new BooleanSetting("only_hits", "Only knockback from damage",
			"Change motion updates only while you are taking damage, so wind charges, riptide and other pushes stay normal.", true));
	private final BooleanSetting explosions = settings().add(new BooleanSetting("explosions", "Explosions",
			"Also scale knockback from explosions (crystals, anchors, TNT).", true));
	private int changed;

	public VelocityModule() {
		super("velocity", "Velocity", "Reduces or removes the knockback you take from hits and explosions.", ModuleCategory.AUTOMATION, false, NO_KEY);
	}

	@Override
	public boolean testEnvironmentOnly() {
		return false;
	}

	/** Number of knockback updates changed since the module started (for tests). */
	public int changed() {
		return changed;
	}

	@Override
	protected void onEnable() {
		changed = 0;
	}

	private Vec3 scale(Vec3 motion) {
		changed++;
		double h = horizontal.get() / 100;
		double v = vertical.get() / 100;
		return new Vec3(motion.x * h, motion.y * v, motion.z * h);
	}

	/** Motion the server sets for your player; {@code null} module or other cases return it unchanged. */
	public static Vec3 filterMotion(Vec3 motion) {
		VelocityModule module = running();
		var player = Minecraft.getInstance().player;
		if (module == null || player == null || (module.onlyHits.get() && player.hurtTime <= 0)) {
			return motion;
		}

		return module.scale(motion);
	}

	public static Vec3 filterExplosion(Vec3 knockback) {
		VelocityModule module = running();
		return module == null || !module.explosions.get() ? knockback : module.scale(knockback);
	}

	private static VelocityModule running() {
		FemboyClient femboy = FemboyClient.get();
		VelocityModule module = femboy == null ? null : femboy.modules().get(VelocityModule.class);
		return module != null && module.isRunning() ? module : null;
	}
}
