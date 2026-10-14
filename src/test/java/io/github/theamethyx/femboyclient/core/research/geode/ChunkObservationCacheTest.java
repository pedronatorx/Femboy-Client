package io.github.theamethyx.femboyclient.core.research.geode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.github.theamethyx.femboyclient.core.research.GrowthStage;

class ChunkObservationCacheTest {
	private static ChunkObservationCache.Column column(int x, GrowthStage stage) {
		return new ChunkObservationCache.Column(List.of(new ScanData.Cell(x, 10, 2, ScanData.Kind.BUD, stage, 1)), Map.of());
	}

	@Test
	void unchangedContentDoesNotChangeTheVersion() {
		ChunkObservationCache cache = new ChunkObservationCache();
		assertTrue(cache.put(0, 0, column(1, GrowthStage.SMALL_BUD), 1_000));
		long version = cache.version();

		assertFalse(cache.put(0, 0, column(1, GrowthStage.SMALL_BUD), 2_000), "same cells read again");
		assertEquals(version, cache.version());

		assertTrue(cache.put(0, 0, column(1, GrowthStage.MEDIUM_BUD), 3_000), "the bud grew");
		assertTrue(cache.version() > version);
	}

	@Test
	void columnsBecomeDueAfterTheInterval() {
		ChunkObservationCache cache = new ChunkObservationCache();
		assertTrue(cache.due(3, 4, 0, 10_000), "never read");
		cache.put(3, 4, column(50, GrowthStage.CLUSTER), 5_000);
		assertFalse(cache.due(3, 4, 14_999, 10_000));
		assertTrue(cache.due(3, 4, 15_000, 10_000));
	}

	@Test
	void snapshotReportsUncachedColumnsAsUnavailable() {
		ChunkObservationCache cache = new ChunkObservationCache();
		cache.put(0, 0, column(1, GrowthStage.CLUSTER), 0);
		ScanData data = cache.snapshot("minecraft:overworld", -64, 320, 0, 0, 1);

		assertEquals(9, data.requestedColumns());
		assertEquals(1, data.availableColumns());
		assertTrue(data.isColumnAvailable(0, 0));
		assertFalse(data.isColumnAvailable(1, 0), "not read yet means unknown, not empty");
		assertEquals(1, data.cells().size());
	}

	@Test
	void buddingFacesSurviveTheSnapshot() {
		ScanData.FaceState[] faces = {ScanData.FaceState.FREE, ScanData.FaceState.BLOCKED, ScanData.FaceState.FREE,
				ScanData.FaceState.UNAVAILABLE, ScanData.FaceState.OWN_GROWTH, ScanData.FaceState.FREE};
		ChunkObservationCache cache = new ChunkObservationCache();
		cache.put(0, 0, new ChunkObservationCache.Column(List.of(new ScanData.Cell(5, 20, 5, ScanData.Kind.BUDDING, null, -1)),
				Map.of(BlockKey.pack(5, 20, 5), faces)), 0);

		ScanData data = cache.snapshot("minecraft:overworld", -64, 320, 0, 0, 0);
		assertEquals(ScanData.FaceState.UNAVAILABLE, data.buddingFaces(5, 20, 5)[3]);
	}

	@Test
	void movingAwayEvictsDistantColumns() {
		ChunkObservationCache cache = new ChunkObservationCache();
		cache.put(0, 0, column(1, GrowthStage.SMALL_BUD), 0);
		cache.put(10, 0, column(161, GrowthStage.SMALL_BUD), 0);
		long version = cache.version();

		assertEquals(1, cache.retainWithin(10, 0, 2));
		assertFalse(cache.contains(0, 0));
		assertTrue(cache.contains(10, 0));
		assertTrue(cache.version() > version);
		assertEquals(0, cache.retainWithin(10, 0, 2), "nothing left to evict");
	}

	@Test
	void oldestReadCoversTheGeodeArea() {
		ChunkObservationCache cache = new ChunkObservationCache();
		cache.put(0, 0, column(1, GrowthStage.SMALL_BUD), 4_000);
		cache.put(1, 0, column(17, GrowthStage.SMALL_BUD), 2_000);
		assertEquals(2_000, cache.oldestRead(0, 0, 1, 0));
		assertEquals(-1, cache.oldestRead(5, 5, 6, 6));
	}
}
