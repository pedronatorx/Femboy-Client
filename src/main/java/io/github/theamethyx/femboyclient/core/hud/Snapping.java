package io.github.theamethyx.femboyclient.core.hud;

import java.util.ArrayList;
import java.util.List;

/**
 * Snaps a dragged box to the screen edges (with a margin), the screen center, a grid and the edges and centers of other
 * boxes, and reports which guide lines it snapped to so the editor can draw them.
 */
public final class Snapping {
	private Snapping() {
	}

	/** A box in GUI pixels. */
	public record Box(int x, int y, int width, int height) {
		int right() {
			return x + width;
		}

		int bottom() {
			return y + height;
		}

		int centerX() {
			return x + width / 2;
		}

		int centerY() {
			return y + height / 2;
		}
	}

	/** The snapped top-left corner, and the x and y coordinates of the guide lines it snapped to. */
	public record Result(int x, int y, List<Integer> guidesX, List<Integer> guidesY) {
	}

	/**
	 * @param box       the box at its unsnapped position
	 * @param others    other boxes to align with
	 * @param threshold how close (pixels) a candidate line must be to snap
	 * @param margin    distance kept from the screen edges when snapping to them
	 * @param grid      grid spacing in pixels, or 0 for no grid
	 */
	public static Result snap(Box box, int screenWidth, int screenHeight, List<Box> others, int threshold, int margin, int grid) {
		List<Integer> linesX = new ArrayList<>(List.of(margin, screenWidth / 2, screenWidth - margin));
		List<Integer> linesY = new ArrayList<>(List.of(margin, screenHeight / 2, screenHeight - margin));
		for (Box other : others) {
			linesX.addAll(List.of(other.x, other.centerX(), other.right()));
			linesY.addAll(List.of(other.y, other.centerY(), other.bottom()));
		}

		Axis horizontal = best(new int[] {box.x, box.centerX(), box.right()}, linesX, threshold);
		Axis vertical = best(new int[] {box.y, box.centerY(), box.bottom()}, linesY, threshold);
		int x = box.x;
		int y = box.y;
		List<Integer> guidesX = new ArrayList<>();
		List<Integer> guidesY = new ArrayList<>();
		if (horizontal != null) {
			x += horizontal.shift;
			guidesX.add(horizontal.line);
		} else if (grid > 0) {
			x = Math.round(box.x / (float) grid) * grid;
		}

		if (vertical != null) {
			y += vertical.shift;
			guidesY.add(vertical.line);
		} else if (grid > 0) {
			y = Math.round(box.y / (float) grid) * grid;
		}

		x = Math.clamp(x, 0, Math.max(0, screenWidth - box.width));
		y = Math.clamp(y, 0, Math.max(0, screenHeight - box.height));
		return new Result(x, y, guidesX, guidesY);
	}

	private record Axis(int shift, int line) {
	}

	/** The smallest shift that puts one of the box's edges or its center on a line, within the threshold. */
	private static Axis best(int[] points, List<Integer> lines, int threshold) {
		Axis best = null;
		for (int point : points) {
			for (int line : lines) {
				int shift = line - point;
				if (Math.abs(shift) <= threshold && (best == null || Math.abs(shift) < Math.abs(best.shift))) {
					best = new Axis(shift, line);
				}
			}
		}

		return best;
	}

	/** Whether two boxes overlap (touching edges do not count). */
	public static boolean overlap(Box a, Box b) {
		return a.x < b.right() && b.x < a.right() && a.y < b.bottom() && b.y < a.bottom();
	}
}
