package io.github.theamethyx.femboyclient.core.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

class ToolsTest {
	@Test
	void experienceMatchesTheWikiTable() {
		assertEquals(7, Experience.pointsForNextLevel(0));
		assertEquals(37, Experience.pointsForNextLevel(15));
		assertEquals(42, Experience.pointsForNextLevel(16));
		assertEquals(112, Experience.pointsForNextLevel(30));
		assertEquals(121, Experience.pointsForNextLevel(31));
		assertEquals(352, Experience.totalPointsForLevel(16));
		assertEquals(1395, Experience.totalPointsForLevel(30));
		assertEquals(1628, Experience.totalPointsForLevel(32));
		// Each total equals the previous total plus the step, across all three formula ranges.
		for (int level = 0; level < 60; level++) {
			assertEquals(Experience.totalPointsForLevel(level) + Experience.pointsForNextLevel(level), Experience.totalPointsForLevel(level + 1), "level " + level);
		}

		assertEquals(1395 - 352 - 21, Experience.pointsToReach(16, 0.5f, 30));
		assertEquals(0, Experience.pointsToReach(31, 0, 30));
	}

	@Test
	void roundShapesAreSymmetricAndHollowOnesAreShells() {
		ShapeGenerator.Result circle = ShapeGenerator.generate(ShapeGenerator.Shape.CIRCLE, 5, 1, false);
		ShapeGenerator.Result ring = ShapeGenerator.generate(ShapeGenerator.Shape.CIRCLE, 5, 1, true);
		assertEquals(97, circle.count(), "filled circle of radius 5 (centers within 5.5 blocks)");
		assertTrue(ring.count() < circle.count());
		Set<String> cells = new HashSet<>();
		circle.positions().forEach(p -> cells.add(p[0] + "," + p[2]));
		for (int[] p : circle.positions()) {
			assertTrue(cells.contains(-p[0] + "," + p[2]) && cells.contains(p[0] + "," + -p[2]) && cells.contains(p[2] + "," + p[0]), "symmetric");
		}

		ShapeGenerator.Result sphere = ShapeGenerator.generate(ShapeGenerator.Shape.SPHERE, 4, 1, false);
		ShapeGenerator.Result dome = ShapeGenerator.generate(ShapeGenerator.Shape.DOME, 4, 1, false);
		assertTrue(dome.positions().stream().allMatch(p -> p[1] >= 0));
		assertTrue(dome.count() > sphere.count() / 2 && dome.count() < sphere.count());
		assertEquals(5 * 97, ShapeGenerator.generate(ShapeGenerator.Shape.CYLINDER, 5, 5, false).count());
		assertEquals(3 * 11, ShapeGenerator.generate(ShapeGenerator.Shape.WALL, 5, 3, false).count());
	}

	@Test
	void spiralStairsClimbOneLayerPerStepAndHugeShapesAreCut() {
		ShapeGenerator.Result stairs = ShapeGenerator.generate(ShapeGenerator.Shape.SPIRAL_STAIRS, 3, 16, false);
		Set<Integer> layers = new HashSet<>();
		stairs.positions().forEach(p -> layers.add(p[1]));
		assertEquals(16, layers.size());
		assertFalse(stairs.truncated());

		ShapeGenerator.Result huge = ShapeGenerator.generate(ShapeGenerator.Shape.CYLINDER, 64, 128, false);
		assertTrue(huge.truncated());
		assertEquals(ShapeGenerator.LIMIT, huge.count());
	}
}
