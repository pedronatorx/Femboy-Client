package io.github.theamethyx.femboyclient.core.research;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class StatisticsTest {
	@Test
	void wilsonIntervalMatchesHandComputedValues() {
		// p = 8/32 = 0.25, z = 1.96: center 0.2768, half-width 0.1443 (computed by hand).
		double[] interval = new Proportion(8, 32, "clusters", "growth objects").wilson95();
		assertEquals(0.1325, interval[0], 5e-4);
		assertEquals(0.4211, interval[1], 5e-4);
	}

	@Test
	void wilsonStaysInsideZeroOneAtTheExtremes() {
		double[] none = Proportion.wilson(0, 10, Proportion.Z95);
		double[] all = Proportion.wilson(10, 10, Proportion.Z95);
		assertEquals(0.0, none[0], 1e-12);
		assertTrue(none[1] > 0.2 && none[1] < 0.35, "upper bound for 0/10 is about 0.28");
		assertEquals(1.0, all[1], 1e-12);
		assertTrue(Double.isNaN(Proportion.wilson(0, 0, Proportion.Z95)[0]));
	}

	@Test
	void denominatorsAreExplicitAndDifferent() {
		StageCounts counts = new StageCounts(10, 10, 5, 15);
		Proportion ofAll = counts.clustersOfAll();
		Proportion ofLarge = counts.clustersOfLargeAndClusters();

		assertEquals(40, ofAll.trials());
		assertEquals(20, ofLarge.trials());
		assertEquals(0.375, ofAll.value(), 1e-12);
		assertEquals(0.75, ofLarge.value(), 1e-12);
		assertEquals("growth objects", ofAll.denominator());
		assertEquals("large buds + clusters", ofLarge.denominator());
		assertEquals("37.5% (15 / 40 growth objects)", ofAll.describe());
	}

	@Test
	void emptyCountsAreUndefinedNotZero() {
		Proportion p = StageCounts.EMPTY.clustersOfAll();
		assertFalse(p.defined());
		assertTrue(Double.isNaN(p.value()));
		assertTrue(p.describe().startsWith("undefined"));
	}

	@Test
	void invalidCountsAreRejected() {
		assertThrows(IllegalArgumentException.class, () -> new StageCounts(-1, 0, 0, 0));
		assertThrows(IllegalArgumentException.class, () -> new Proportion(5, 4, "a", "b"));
	}
}
