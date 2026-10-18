package io.github.theamethyx.femboyclient.core.tools;

import java.util.ArrayList;
import java.util.List;

import io.github.theamethyx.femboyclient.core.setting.EnumSetting;

/**
 * Block positions of simple building shapes, relative to a center block: circles, cylinders, spheres, domes, spiral
 * staircases and walls. A block belongs to a round shape when its center is within the radius plus half a block, the
 * usual rule of building generators, so the outline is symmetric.
 */
public final class ShapeGenerator {
	/** The most positions returned; larger shapes are cut (the result says so). */
	public static final int LIMIT = 40_000;

	private ShapeGenerator() {
	}

	public enum Shape implements EnumSetting.Labeled {
		CIRCLE("Circle"), CYLINDER("Cylinder"), SPHERE("Sphere"), DOME("Dome"), SPIRAL_STAIRS("Spiral staircase"), WALL("Wall");

		private final String label;

		Shape(String label) {
			this.label = label;
		}

		@Override
		public String label() {
			return label;
		}
	}

	/** Positions as {dx, dy, dz} triples, and whether the shape was cut at {@link #LIMIT}. */
	public record Result(List<int[]> positions, boolean truncated) {
		public int count() {
			return positions.size();
		}
	}

	/**
	 * @param radius 1-64 (for a wall: half its length)
	 * @param height layers for cylinders, staircases and walls
	 * @param hollow only the outer shell (walls without caps for cylinders)
	 */
	public static Result generate(Shape shape, int radius, int height, boolean hollow) {
		int r = Math.clamp(radius, 1, 64);
		int h = Math.clamp(height, 1, 128);
		List<int[]> out = new ArrayList<>();
		boolean truncated = switch (shape) {
			case CIRCLE -> disc(out, r, 0, hollow);
			case CYLINDER -> {
				boolean cut = false;
				for (int y = 0; y < h && !cut; y++) cut = disc(out, r, y, hollow);
				yield cut;
			}
			case SPHERE -> ball(out, r, hollow, false);
			case DOME -> ball(out, r, hollow, true);
			case SPIRAL_STAIRS -> spiral(out, r, h);
			case WALL -> {
				boolean cut = false;
				for (int y = 0; y < h && !cut; y++) {
					for (int x = -r; x <= r && !cut; x++) cut = add(out, x, y, 0);
				}
				yield cut;
			}
		};
		return new Result(out, truncated);
	}

	private static boolean inside(double distanceSquared, int r) {
		return distanceSquared <= (r + 0.5) * (r + 0.5);
	}

	private static boolean disc(List<int[]> out, int r, int y, boolean hollow) {
		for (int x = -r; x <= r; x++) {
			for (int z = -r; z <= r; z++) {
				if (!inside(x * x + z * z, r)) continue;
				boolean edge = !inside((x + 1) * (x + 1) + z * z, r) || !inside((x - 1) * (x - 1) + z * z, r)
						|| !inside(x * x + (z + 1) * (z + 1), r) || !inside(x * x + (z - 1) * (z - 1), r);
				if ((!hollow || edge) && add(out, x, y, z)) return true;
			}
		}

		return false;
	}

	private static boolean ball(List<int[]> out, int r, boolean hollow, boolean upperHalf) {
		for (int y = upperHalf ? 0 : -r; y <= r; y++) {
			for (int x = -r; x <= r; x++) {
				for (int z = -r; z <= r; z++) {
					if (!inside(x * x + y * y + z * z, r)) continue;
					boolean edge = !inside((x + 1) * (x + 1) + y * y + z * z, r) || !inside((x - 1) * (x - 1) + y * y + z * z, r)
							|| !inside(x * x + (y + 1) * (y + 1) + z * z, r) || !inside(x * x + (y - 1) * (y - 1) + z * z, r)
							|| !inside(x * x + y * y + (z + 1) * (z + 1), r) || !inside(x * x + y * y + (z - 1) * (z - 1), r);
					if ((!hollow || edge) && add(out, x, y, z)) return true;
				}
			}
		}

		return false;
	}

	/** One step per layer, turning a sixteenth of a circle each time, from the center column out to the radius. */
	private static boolean spiral(List<int[]> out, int r, int h) {
		for (int y = 0; y < h; y++) {
			double angle = 2 * Math.PI * y / 16;
			int lastX = Integer.MIN_VALUE;
			int lastZ = Integer.MIN_VALUE;
			for (int step = 0; step <= r; step++) {
				int x = (int) Math.round(Math.cos(angle) * step);
				int z = (int) Math.round(Math.sin(angle) * step);
				if (x == lastX && z == lastZ) continue;
				lastX = x;
				lastZ = z;
				if (add(out, x, y, z)) return true;
			}
		}

		return false;
	}

	/** Adds a position; returns true when the limit is reached. */
	private static boolean add(List<int[]> out, int x, int y, int z) {
		if (out.size() >= LIMIT) {
			return true;
		}

		out.add(new int[] {x, y, z});
		return false;
	}
}
