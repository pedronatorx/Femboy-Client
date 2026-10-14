package io.github.theamethyx.femboyclient.client.render;

import java.util.List;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import io.github.theamethyx.femboyclient.client.gui.UiContext;

/**
 * Shared drawing for the highlight modules (Entity ESP, Storage ESP): every highlight is a box with an
 * optional outline, a translucent fill and a label above it. Keeping one implementation means both modules
 * look and behave the same and only have to decide <em>what</em> to highlight.
 */
public final class EspRenderer {
	/** One highlighted thing for this frame. {@code rgb} is opaque; the renderer applies alpha. */
	public record Target(AABB box, int rgb, String label) {
	}

	/** Drawing options, taken from the module's settings. */
	public record Style(boolean outline, boolean fill, boolean throughWalls, float lineWidth) {
	}

	private static final float FILL_ALPHA = 0.14f;

	private EspRenderer() {
	}

	public static void drawBoxes(RenderingUtilities render, List<Target> targets, Style style) {
		for (Target target : targets) {
			if (style.fill()) {
				render.filledBox(target.box(), withAlpha(target.rgb(), FILL_ALPHA), style.throughWalls());
			}

			if (style.outline()) {
				render.boxOutline(target.box(), target.rgb() | 0xFF000000, style.lineWidth(), style.throughWalls());
			}
		}
	}

	/** Draws up to {@code limit} labels, nearest first (targets must already be sorted by distance). */
	public static void drawLabels(UiContext ui, List<Target> targets, int screenWidth, int screenHeight, int limit) {
		int drawn = 0;
		for (Target target : targets) {
			if (drawn >= limit) {
				return;
			}

			if (target.label() == null || target.label().isEmpty()) {
				continue;
			}

			AABB box = target.box();
			Vec3 top = new Vec3((box.minX + box.maxX) / 2, box.maxY + 0.35, (box.minZ + box.maxZ) / 2);
			int[] at = ScreenProjection.toScreen(top, screenWidth, screenHeight);
			if (at != null) {
				ui.worldLabel(target.label(), at[0], at[1], target.rgb() | 0xFF000000);
				drawn++;
			}
		}
	}

	public static int withAlpha(int rgb, float alpha) {
		return (Math.round(255 * Math.max(0, Math.min(1, alpha))) << 24) | (rgb & 0xFFFFFF);
	}
}
