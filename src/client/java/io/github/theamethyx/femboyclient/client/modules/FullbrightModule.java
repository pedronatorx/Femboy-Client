package io.github.theamethyx.femboyclient.client.modules;

import io.github.theamethyx.femboyclient.core.module.Module;
import io.github.theamethyx.femboyclient.core.module.ModuleCategory;
import io.github.theamethyx.femboyclient.core.setting.NumberSetting;

/**
 * Makes dark areas bright by raising the brightness value that is sent to the lightmap shader.
 *
 * <p>Your video settings are never changed: {@code LightTextureMixin} only replaces the value used for
 * the lightmap while this module runs, so turning it off (or a crash) leaves the game exactly as it was.
 * The lightmap is recomputed every tick, so changes apply within a tick, in every dimension.
 */
public final class FullbrightModule extends Module {
	/** Brightness factor at 100 % intensity. Vanilla's slider goes up to 1 ("Bright"). */
	public static final float MAX_BRIGHTNESS = 12;

	private static volatile float override = Float.NaN;

	private final NumberSetting intensity = settings().add(new NumberSetting("intensity", "Intensity",
			"How bright dark areas become. 100% lights up caves like daylight; lower values keep some shading.", 1, 0.1, 1, 0.05, ""));

	public FullbrightModule() {
		super("fullbright", "Fullbright", "Lights up dark areas without changing your video settings.",
				ModuleCategory.VISUALS, false, NO_KEY);
		intensity.onChange(value -> {
			if (isRunning()) {
				apply();
			}
		});
	}

	@Override
	protected void onEnable() {
		apply();
	}

	@Override
	protected void onDisable() {
		override = Float.NaN;
	}

	private void apply() {
		override = (float) (1 + (MAX_BRIGHTNESS - 1) * intensity.get());
	}

	/**
	 * The brightness the lightmap should use instead of the video setting, given the vanilla value.
	 * Read by {@code LightTextureMixin} on the render thread.
	 */
	public static float brightness(float vanilla) {
		float value = override;
		return Float.isNaN(value) ? vanilla : Math.max(vanilla, value);
	}
}
