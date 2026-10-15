package io.github.theamethyx.femboyclient.client.combat;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import io.github.theamethyx.femboyclient.client.FemboyClient;
import io.github.theamethyx.femboyclient.client.modules.TargetHudModule;

/** The "current target" shared by the combat HUD modules: Target HUD's choice when it runs, otherwise what you look at or hit last. */
public final class Targets {
	private static final long DEFAULT_MEMORY_MS = 6000;

	private Targets() {
	}

	public static LivingEntity current() {
		FemboyClient femboy = FemboyClient.get();
		TargetHudModule hud = femboy.modules().get(TargetHudModule.class);
		if (hud != null && hud.isRunning()) {
			return hud.target();
		}

		LivingEntity looked = crosshair();
		return looked != null ? looked : femboy.combat().lastTarget(DEFAULT_MEMORY_MS);
	}

	/** The living entity under the crosshair (vanilla's own pick, limited to attack range), or null. */
	public static LivingEntity crosshair() {
		Entity entity = Minecraft.getInstance().crosshairPickEntity;
		return entity instanceof LivingEntity living && living.isAlive() ? living : null;
	}
}
