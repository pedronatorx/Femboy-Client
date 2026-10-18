package io.github.theamethyx.femboyclient.client.render;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import io.github.theamethyx.femboyclient.client.Performance;
import io.github.theamethyx.femboyclient.core.setting.BooleanSetting;
import io.github.theamethyx.femboyclient.core.setting.ColorSetting;
import io.github.theamethyx.femboyclient.core.setting.EnumSetting;
import io.github.theamethyx.femboyclient.core.setting.ModuleSettings;
import io.github.theamethyx.femboyclient.core.setting.NumberSetting;

/**
 * Target lines for the ESP modules: a line from the crosshair to each selected target, drawn in the world with
 * Minecraft's line pipeline. It starts on the ray through the middle of the screen of the frame being drawn
 * ({@link FrameProjection}), so it leaves exactly from the crosshair at any resolution and GUI scale and stays put while
 * the camera moves and bobs; targets behind you are reached through the screen edge on their side.
 *
 * <p>Each ESP module owns one {@link Settings} group (its own color, opacity, thickness, distance, count and visibility),
 * so their lines are configured independently. Setting ids keep the old Entity ESP "tracer" names so saved
 * configurations carry over.
 */
public final class TargetLines {
	private TargetLines() {
	}

	/** Which targets get a line. */
	public enum Visibility implements EnumSetting.Labeled {
		ALWAYS("All targets (through walls)"), ON_SCREEN("Only targets on screen"), LINE_OF_SIGHT("Only targets you can see");

		private final String label;

		Visibility(String label) {
			this.label = label;
		}

		@Override
		public String label() {
			return label;
		}
	}

	public enum ColorMode implements EnumSetting.Labeled {
		TARGET("Same as the highlight"), CUSTOM("One custom color");

		private final String label;

		ColorMode(String label) {
			this.label = label;
		}

		@Override
		public String label() {
			return label;
		}
	}

	/** Something a line can point at. Positions are asked every frame, so moving targets are followed smoothly. */
	public interface Target {
		/** World point the line ends at. */
		Vec3 linePoint(float partialTick);

		/** RGB of the target's highlight. */
		int lineColor();

		/** Whether the target was in your line of sight at its last check (used by {@link Visibility#LINE_OF_SIGHT}). */
		boolean lineOfSight();

		/** Whether this target's category has lines switched on. */
		default boolean wantsLine() {
			return true;
		}
	}

	/** The "Target lines" settings of one ESP module. */
	public static final class Settings {
		public final BooleanSetting enabled;
		public final EnumSetting<Visibility> visibility;
		public final NumberSetting maxDistance;
		public final NumberSetting maxCount;
		public final EnumSetting<ColorMode> colorMode;
		public final ColorSetting color;
		public final NumberSetting opacity;
		public final NumberSetting width;
		public final BooleanSetting fade;
		public final BooleanSetting depth;
		private int drawn;

		public Settings(ModuleSettings settings, boolean defaultOn, Visibility defaultVisibility, double defaultDistance, double maxDistanceLimit,
				int defaultCount) {
			settings.section("Target lines");
			enabled = settings.add(new BooleanSetting("tracers", "Target lines",
					"Draw a line from your crosshair to each highlighted target.", defaultOn));
			visibility = settings.add(new EnumSetting<>("tracer_visibility", "Lines to",
					"All targets, only targets inside your view, or only targets nothing blocks your view of (checked a few times a second).",
					defaultVisibility)).visibleWhen(enabled::get);
			maxDistance = settings.add(new NumberSetting("tracer_max_distance", "Line distance",
					"Targets farther away get no line (the highlight stays).", defaultDistance, 4, maxDistanceLimit, 4, "blocks")).visibleWhen(enabled::get);
			maxCount = settings.add(new NumberSetting("tracer_max_count", "Most lines",
					"Only the nearest targets up to this number get a line.", defaultCount, 1, 128, 1, "")).visibleWhen(enabled::get).advanced();
			colorMode = settings.add(new EnumSetting<>("tracer_color_mode", "Line color",
					"Use each target's highlight color, or one color for all lines.", ColorMode.TARGET)).visibleWhen(enabled::get);
			color = settings.add(new ColorSetting("tracer_color", "Custom line color", "Color of every line when 'One custom color' is chosen.",
					0xE070E8)).visibleWhen(() -> enabled.get() && colorMode.get() == ColorMode.CUSTOM);
			opacity = settings.add(new NumberSetting("tracer_opacity", "Line opacity", "How solid the lines are.", 0.6, 0.1, 1, 0.05, ""))
					.visibleWhen(enabled::get);
			width = settings.add(new NumberSetting("tracer_width", "Line thickness",
					"Thickness, as a multiple of the normal line width.", 1, 0.5, 4, 0.5, "x")).visibleWhen(enabled::get);
			fade = settings.add(new BooleanSetting("tracer_fade", "Fade with distance",
					"Lines to far targets are fainter, so near ones stand out.", true)).visibleWhen(enabled::get).advanced();
			depth = settings.add(new BooleanSetting("tracer_depth", "Hide behind blocks",
					"Lines are hidden where blocks are in front of them, like normal world geometry.", false)).visibleWhen(enabled::get).advanced();
		}

		/** Lines drawn in the last frame (for tests). */
		public int drawn() {
			return drawn;
		}

		/**
		 * Draws lines to {@code targets} (nearest first) within the limits.
		 *
		 * @return the number of lines drawn
		 */
		public int draw(RenderingUtilities render, List<? extends Target> targets) {
			drawn = 0;
			if (!enabled.get() || targets.isEmpty()) {
				return 0;
			}

			Vec3 camera = render.camera();
			Vec3 origin = FrameProjection.valid() ? FrameProjection.crosshairOrigin(0.6) : fallbackOrigin(camera);
			int limit = Performance.cap(maxCount.intValue(), 32);
			double max = maxDistance.get();
			Visibility mode = visibility.get();
			boolean custom = colorMode.get() == ColorMode.CUSTOM;
			float alpha = opacity.get().floatValue();
			float lineWidth = width.get().floatValue();
			Minecraft client = Minecraft.getInstance();
			int screenWidth = client.getWindow().getGuiScaledWidth();
			int screenHeight = client.getWindow().getGuiScaledHeight();
			float[] projected = new float[3];
			for (Target target : targets) {
				if (drawn >= limit) {
					break;
				}

				if (!target.wantsLine() || mode == Visibility.LINE_OF_SIGHT && !target.lineOfSight()) {
					continue;
				}

				Vec3 point = target.linePoint(render.partialTick());
				double distance = point.distanceTo(camera);
				if (distance > max) {
					continue;
				}

				if (mode == Visibility.ON_SCREEN && FrameProjection.valid()
						&& !FrameProjection.project(point.x, point.y, point.z, screenWidth, screenHeight, projected)) {
					continue;
				}

				float a = fade.get() ? alpha * (float) (1 - 0.65 * Math.min(1, distance / max)) : alpha;
				int rgb = custom ? color.get() : target.lineColor();
				render.line(origin, point, EspRenderer.withAlpha(rgb, a), lineWidth, !depth.get());
				drawn++;
			}

			return drawn;
		}

		private static Vec3 fallbackOrigin(Vec3 camera) {
			var forward = Minecraft.getInstance().gameRenderer.getMainCamera().forwardVector();
			return camera.add(forward.x() * 0.6, forward.y() * 0.6, forward.z() * 0.6);
		}
	}

	/**
	 * Whether nothing solid lies between your eyes and {@code point}. {@code target} (may be null) is the block the line
	 * points at, which does not count as blocking itself. A raycast, so call it at update intervals, not every frame.
	 */
	public static boolean lineOfSight(Vec3 point, BlockPos target) {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null) {
			return false;
		}

		Vec3 eye = player.getEyePosition();
		BlockHitResult hit = player.level().clip(new ClipContext(eye, point, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
		return hit.getType() == HitResult.Type.MISS || target != null && hit.getBlockPos().equals(target);
	}
}
