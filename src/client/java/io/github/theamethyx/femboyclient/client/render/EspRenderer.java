package io.github.theamethyx.femboyclient.client.render;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import io.github.theamethyx.femboyclient.client.gui.UiContext;
import io.github.theamethyx.femboyclient.core.setting.EnumSetting;

/**
 * Shared drawing for the highlight modules (Entity ESP, Storage ESP): a box with an optional outline and
 * a translucent fill, an optional tracer line, and a compact label. Keeping one implementation means both
 * modules look and behave the same and only have to decide <em>what</em> to highlight.
 *
 * <p>Every size and opacity here comes from a setting and is applied to what is drawn: outline and tracer
 * widths go into the line shader's per-vertex width, text scales into the GUI transform.
 */
public final class EspRenderer {
	/** One highlighted thing for this frame. {@code rgb} is opaque; the renderer applies alpha. */
	public record Target(AABB box, int rgb, boolean outline, Label label) {
		public Target(AABB box, int rgb, Label label) {
			this(box, rgb, true, label);
		}
	}

	/**
	 * Label content. {@code distance} may be null; {@code health} is NaN when no health should be shown.
	 */
	public record Label(String name, String distance, float health, float maxHealth) {
		public static Label of(String name, String distance) {
			return new Label(name, distance, Float.NaN, Float.NaN);
		}

		boolean hasHealth() {
			return !Float.isNaN(health) && maxHealth > 0;
		}
	}

	/** Box drawing options, taken from the module's settings. */
	public record Style(boolean outline, float outlineWidth, float outlineOpacity, boolean fill, float fillOpacity, boolean throughWalls) {
		public static Style simple(boolean outline, boolean fill, boolean throughWalls, float outlineWidth) {
			return new Style(outline, outlineWidth, 1, fill, 0.14f, throughWalls);
		}
	}

	public enum HealthStyle implements EnumSetting.Labeled {
		BAR("Bar"), TEXT("Number"), BAR_AND_TEXT("Bar and number");

		private final String label;

		HealthStyle(String label) {
			this.label = label;
		}

		@Override
		public String label() {
			return label;
		}
	}

	/** Label drawing options. Scales are relative to the normal GUI font; padding and spacing are GUI pixels. */
	public record LabelStyle(float nameScale, float distanceScale, float healthTextScale, float healthBarScale, boolean coloredNames,
			int textColor, float backgroundOpacity, int padding, int spacing, int maxNameWidth, HealthStyle healthStyle, float healthOpacity) {
		public static final LabelStyle DEFAULT = new LabelStyle(0.75f, 0.65f, 0.65f, 1, true, 0xFFFFFF, 0.5f, 2, 1, 110, HealthStyle.BAR, 0.9f);
	}

	/** Where a label ended up on screen; read by automated tests to check that size settings take effect. */
	public record DrawnLabel(String name, int x, int y, int width, int height, boolean healthBar, boolean healthText) {
	}

	private static final int LINE_HEIGHT = 8;

	private EspRenderer() {
	}

	public static void drawBoxes(RenderingUtilities render, List<Target> targets, Style style) {
		for (Target target : targets) {
			if (style.fill()) {
				render.filledBox(target.box(), withAlpha(target.rgb(), style.fillOpacity()), style.throughWalls());
			}

			if (style.outline() && target.outline()) {
				render.boxOutline(target.box(), withAlpha(target.rgb(), style.outlineOpacity()), style.outlineWidth(), style.throughWalls());
			}
		}
	}

	/** Lines from just in front of the camera to the center of each target, always drawn through walls. */
	public static void drawTracers(RenderingUtilities render, List<Target> targets, Vec3 from, float width, float opacity) {
		for (Target target : targets) {
			render.line(from, target.box().getCenter(), withAlpha(target.rgb(), opacity), width, true);
		}
	}

	/**
	 * Draws up to {@code limit} labels, nearest first (targets must already be sorted by distance).
	 *
	 * @return the labels as drawn, in drawing order
	 */
	public static List<DrawnLabel> drawLabels(UiContext ui, List<Target> targets, int screenWidth, int screenHeight, int limit, LabelStyle style) {
		List<DrawnLabel> drawn = new ArrayList<>();
		for (Target target : targets) {
			if (drawn.size() >= limit) {
				break;
			}

			Label label = target.label();
			if (label == null || (label.name() == null || label.name().isEmpty()) && label.distance() == null && !label.hasHealth()) {
				continue;
			}

			AABB box = target.box();
			Vec3 top = new Vec3((box.minX + box.maxX) / 2, box.maxY + 0.25, (box.minZ + box.maxZ) / 2);
			int[] at = ScreenProjection.toScreen(top, screenWidth, screenHeight);
			if (at != null) {
				drawn.add(drawLabel(ui, label, target.rgb(), at[0], at[1], style));
			}
		}

		return drawn;
	}

	private static DrawnLabel drawLabel(UiContext ui, Label label, int rgb, int centerX, int bottomY, LabelStyle style) {
		boolean healthBar = label.hasHealth() && style.healthStyle() != HealthStyle.TEXT;
		boolean healthText = label.hasHealth() && style.healthStyle() != HealthStyle.BAR;

		String name = label.name() == null || label.name().isEmpty() ? null
				: ui.ellipsize(label.name(), Math.max(10, Math.round(style.maxNameWidth() / style.nameScale())));
		String health = healthText ? String.format(Locale.ROOT, "%.0f❤", label.health()) : null;

		float nameW = name == null ? 0 : ui.textWidth(name) * style.nameScale();
		float nameH = name == null ? 0 : LINE_HEIGHT * style.nameScale();
		float distanceW = label.distance() == null ? 0 : ui.textWidth(label.distance()) * style.distanceScale();
		float healthW = health == null ? 0 : ui.textWidth(health) * style.healthTextScale();
		float gap = distanceW > 0 && healthW > 0 ? 3 + style.spacing() : 0;
		float rowW = distanceW + gap + healthW;
		float rowH = Math.max(distanceW > 0 ? LINE_HEIGHT * style.distanceScale() : 0, healthW > 0 ? LINE_HEIGHT * style.healthTextScale() : 0);
		float barH = healthBar ? Math.max(1, 2 * style.healthBarScale()) : 0;
		float barW = healthBar ? Math.max(20 * style.healthBarScale(), Math.max(nameW, rowW)) : 0;

		float contentW = Math.max(Math.max(nameW, rowW), barW);
		float contentH = nameH;
		if (rowH > 0) contentH += (contentH > 0 ? style.spacing() : 0) + rowH;
		if (barH > 0) contentH += (contentH > 0 ? style.spacing() + 1 : 0) + barH;

		int pad = style.padding();
		int w = Math.round(contentW) + 2 * pad;
		int h = Math.round(contentH) + 2 * pad;
		int x = centerX - w / 2;
		int y = ui.placeLabel(x, bottomY - h, w, h);

		if (style.backgroundOpacity() > 0) {
			ui.roundRect(x, y, w, h, withAlpha(0x101014, style.backgroundOpacity()));
		}

		float cy = y + pad;
		if (name != null) {
			int color = (style.coloredNames() ? rgb : style.textColor()) | 0xFF000000;
			ui.textScaled(name, centerX - nameW / 2, cy, style.nameScale(), color);
			cy += nameH;
		}

		if (rowH > 0) {
			cy += name != null ? style.spacing() : 0;
			float rx = centerX - rowW / 2;
			if (distanceW > 0) {
				ui.textScaled(label.distance(), rx, cy + (rowH - LINE_HEIGHT * style.distanceScale()), style.distanceScale(),
						withAlpha(style.textColor(), 0.75f));
				rx += distanceW + gap;
			}

			if (healthW > 0) {
				ui.textScaled(health, rx, cy + (rowH - LINE_HEIGHT * style.healthTextScale()), style.healthTextScale(),
						withAlpha(healthColor(label.health() / label.maxHealth()), style.healthOpacity()));
			}

			cy += rowH;
		}

		if (healthBar) {
			cy += (name != null || rowH > 0) ? style.spacing() + 1 : 0;
			int bx = Math.round(centerX - barW / 2);
			int by = Math.round(cy);
			int bw = Math.round(barW);
			int bh = Math.round(barH);
			float fraction = Math.max(0, Math.min(1, label.health() / label.maxHealth()));
			ui.rect(bx, by, bw, bh, withAlpha(0x000000, 0.6f * style.healthOpacity()));
			ui.rect(bx, by, Math.max(1, Math.round(bw * fraction)), bh, withAlpha(healthColor(fraction), style.healthOpacity()));
		}

		return new DrawnLabel(label.name(), x, y, w, h, healthBar, healthText);
	}

	/** Green at full health through yellow to red. */
	public static int healthColor(float fraction) {
		float f = Math.max(0, Math.min(1, fraction));
		int r = Math.round(255 * Math.min(1, 2 * (1 - f)));
		int g = Math.round(255 * Math.min(1, 2 * f));
		return (r << 16) | (g << 8) | 0x40;
	}

	public static int withAlpha(int rgb, float alpha) {
		return (Math.round(255 * Math.max(0, Math.min(1, alpha))) << 24) | (rgb & 0xFFFFFF);
	}
}
