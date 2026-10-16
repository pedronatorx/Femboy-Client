package io.github.theamethyx.femboyclient.client.modules;

import org.lwjgl.glfw.GLFW;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.Minecraft;

import io.github.theamethyx.femboyclient.client.FemboyClient;
import io.github.theamethyx.femboyclient.core.module.Module;
import io.github.theamethyx.femboyclient.core.module.ModuleCategory;
import io.github.theamethyx.femboyclient.core.setting.BooleanSetting;
import io.github.theamethyx.femboyclient.core.setting.KeySetting;
import io.github.theamethyx.femboyclient.core.setting.NumberSetting;

/** Hold a key to zoom in (like a spyglass), optionally adjusting the zoom with the scroll wheel. Only the view changes. */
public final class ZoomModule extends Module {
	private final KeySetting key = settings().add(new KeySetting("key", "Zoom key", "Hold to zoom.", GLFW.GLFW_KEY_C));
	private final NumberSetting factor = settings().add(new NumberSetting("factor", "Zoom", "How much closer things look.", 4, 1.5, 20, 0.5, "x"));
	private final BooleanSetting scroll = settings().add(new BooleanSetting("scroll", "Adjust with scroll wheel",
			"While zooming, the scroll wheel changes the zoom instead of the hotbar slot.", true));
	private final BooleanSetting slowMouse = settings().add(new BooleanSetting("slow_mouse", "Slower mouse while zoomed",
			"Turn slower in proportion to the zoom, so aiming stays precise.", true));

	private float current;
	private boolean zooming;

	public ZoomModule() {
		super("zoom", "Zoom", "Hold a key to zoom in, with scroll-wheel adjustment.", ModuleCategory.VISUALS, false, NO_KEY);
	}

	@Override
	protected void onTick() {
		Minecraft client = Minecraft.getInstance();
		boolean down = key.isBound() && client.screen == null && InputConstants.isKeyDown(client.getWindow(), key.get());
		if (down && !zooming) {
			current = factor.get().floatValue();
		}

		zooming = down;
	}

	@Override
	protected void onDisable() {
		zooming = false;
	}

	/** Sets the zoom state directly (used by tests instead of holding the key). */
	public void setZooming(boolean zooming) {
		this.zooming = zooming;
		current = factor.get().floatValue();
	}

	private static ZoomModule active() {
		FemboyClient femboy = FemboyClient.get();
		ZoomModule zoom = femboy == null ? null : femboy.modules().get(ZoomModule.class);
		return zoom != null && zoom.isRunning() && zoom.zooming ? zoom : null;
	}

	/** Divisor for the world field of view (1 when not zooming). */
	public static float fovDivisor() {
		ZoomModule zoom = active();
		return zoom == null ? 1 : zoom.current;
	}

	/** Divisor for mouse turning (1 when not zooming or when disabled). */
	public static double mouseDivisor() {
		ZoomModule zoom = active();
		return zoom == null || !zoom.slowMouse.get() ? 1 : zoom.current;
	}

	/** Called on mouse scroll; returns true when the scroll was used for zooming. */
	public static boolean onScroll(double amount) {
		ZoomModule zoom = active();
		if (zoom == null || !zoom.scroll.get() || amount == 0) {
			return false;
		}

		zoom.current = Math.clamp(zoom.current * (amount > 0 ? 1.25f : 0.8f), 1.1f, 50f);
		return true;
	}
}
