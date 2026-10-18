package io.github.theamethyx.femboyclient.core.tools;

/** Minecraft's experience formulas (Java Edition, unchanged since 1.8). Exact, not estimates. */
public final class Experience {
	private Experience() {
	}

	/** Points needed to go from {@code level} to {@code level + 1}. */
	public static int pointsForNextLevel(int level) {
		if (level >= 31) return 9 * level - 158;
		if (level >= 16) return 5 * level - 38;
		return 2 * level + 7;
	}

	/** Total points needed to reach {@code level} from zero. */
	public static long totalPointsForLevel(int level) {
		if (level <= 16) return (long) level * level + 6L * level;
		if (level <= 31) return Math.round(2.5 * level * level - 40.5 * level + 360);
		return Math.round(4.5 * level * level - 162.5 * level + 2220);
	}

	/** Total points of a player at {@code level} with {@code progress} (0-1) towards the next level. */
	public static long totalPoints(int level, float progress) {
		return totalPointsForLevel(level) + Math.round(Math.clamp(progress, 0, 1) * pointsForNextLevel(level));
	}

	/** Points still needed to reach {@code target} from the given state (0 when already there). */
	public static long pointsToReach(int level, float progress, int target) {
		return Math.max(0, totalPointsForLevel(target) - totalPoints(level, progress));
	}
}
