package io.github.theamethyx.nova.core.research.geode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.theamethyx.nova.core.research.GrowthStage;
import io.github.theamethyx.nova.core.research.StageCounts;

class GeodeClustererTest {
	private static final GeodeClusterer CLUSTERER = new GeodeClusterer();

	/** A hollow 5x5x5 amethyst shell at (ox, oy, oz) with one budding block and buds inside. */
	private static void shell(ScanData data, int ox, int oy, int oz, GrowthStage... buds) {
		for (int x = 0; x < 5; x++) {
			for (int y = 0; y < 5; y++) {
				for (int z = 0; z < 5; z++) {
					boolean surface = x == 0 || x == 4 || y == 0 || y == 4 || z == 0 || z == 4;
					if (!surface) continue;
					boolean budding = x == 2 && y == 0 && z == 2;
					data.addCell(new ScanData.Cell(ox + x, oy + y, oz + z, budding ? ScanData.Kind.BUDDING : ScanData.Kind.AMETHYST_BLOCK, null, -1));
				}
			}
		}

		ScanData.FaceState[] faces = new ScanData.FaceState[6];
		Arrays.fill(faces, ScanData.FaceState.BLOCKED);
		faces[1] = buds.length > 0 ? ScanData.FaceState.OWN_GROWTH : ScanData.FaceState.FREE;
		data.setBuddingFaces(ox + 2, oy, oz + 2, faces);

		// Buds inside the cavity, touching the floor (and therefore the shell).
		for (int i = 0; i < buds.length; i++) {
			data.addCell(new ScanData.Cell(ox + 1 + (i % 3), oy + 1, oz + 1 + (i / 3), ScanData.Kind.BUD, buds[i], 1));
		}
	}

	private static ScanData availableData() {
		ScanData data = new ScanData("minecraft:overworld", -64, 319);
		for (int cx = -4; cx <= 4; cx++) {
			for (int cz = -4; cz <= 4; cz++) {
				data.requestColumn(cx, cz);
				data.markAvailable(cx, cz);
			}
		}

		return data;
	}

	@Test
	void separateGeodesAreCountedOnceEach() {
		ScanData data = availableData();
		shell(data, 2, 10, 2, GrowthStage.SMALL_BUD, GrowthStage.CLUSTER, GrowthStage.CLUSTER);
		shell(data, 20, 10, 2, GrowthStage.LARGE_BUD);

		List<GeodeObservation> geodes = CLUSTERER.cluster(data, GeodeClusterer.Params.DEFAULT, 1000, "test");

		assertEquals(2, geodes.size());
		assertEquals(new StageCounts(1, 0, 0, 2), geodes.get(0).counts());
		assertEquals(new StageCounts(0, 0, 1, 0), geodes.get(1).counts());
		assertEquals(1, geodes.get(0).buddingBlocks());
		assertEquals(97, geodes.get(0).amethystBlocks(), "98 surface blocks minus one budding block");
		assertTrue(geodes.get(0).complete(), geodes.get(0).completenessNote());
		assertEquals(0, geodes.get(0).unavailablePositions());
		assertEquals("minecraft:overworld@2,10,2", geodes.get(0).id());
	}

	@Test
	void unloadedColumnsMakeAnObservationIncompleteInsteadOfEmpty() {
		ScanData data = new ScanData("minecraft:overworld", -64, 319);
		data.requestColumn(0, 0);
		data.requestColumn(1, 0);
		data.markAvailable(0, 0);
		// Column (1, 0) was requested but not loaded: the geode below crosses into it.
		shell(data, 13, 10, 2, GrowthStage.CLUSTER);

		GeodeObservation geode = CLUSTERER.cluster(data, GeodeClusterer.Params.DEFAULT, 1000, "test").getFirst();

		assertFalse(geode.complete());
		assertTrue(geode.unavailablePositions() > 0);
		assertTrue(geode.completenessNote().contains("not loaded or not scanned"), geode.completenessNote());
	}

	@Test
	void unreadableFacesAreReportedNotTreatedAsAir() {
		ScanData data = availableData();
		shell(data, 2, 10, 2);
		ScanData.FaceState[] faces = new ScanData.FaceState[6];
		Arrays.fill(faces, ScanData.FaceState.UNAVAILABLE);
		data.setBuddingFaces(4, 10, 4, faces);

		GeodeObservation geode = CLUSTERER.cluster(data, GeodeClusterer.Params.DEFAULT, 1000, "test").getFirst();

		assertFalse(geode.complete());
		assertEquals(6, geode.faces().unavailable());
		assertEquals(0, geode.faces().free(), "unreadable faces must not count as free");
	}

	@Test
	void smallBlockGroupsAreNotGeodes() {
		ScanData data = availableData();
		data.addCell(new ScanData.Cell(0, 0, 0, ScanData.Kind.AMETHYST_BLOCK, null, -1));
		data.addCell(new ScanData.Cell(1, 0, 0, ScanData.Kind.AMETHYST_BLOCK, null, -1));
		data.addCell(new ScanData.Cell(2, 1, 0, ScanData.Kind.BUD, GrowthStage.CLUSTER, 1));

		assertTrue(CLUSTERER.cluster(data, GeodeClusterer.Params.DEFAULT, 1000, "test").isEmpty());
	}

	@Test
	void touchingGeodesMergeAsDocumented() {
		ScanData data = availableData();
		shell(data, 2, 10, 2, GrowthStage.CLUSTER);
		shell(data, 7, 10, 2, GrowthStage.CLUSTER);

		List<GeodeObservation> geodes = CLUSTERER.cluster(data, GeodeClusterer.Params.DEFAULT, 1000, "test");
		assertEquals(1, geodes.size(), "shells that touch cannot be separated from block data alone");
		assertEquals(2, geodes.getFirst().counts().cluster());
	}

	@Test
	void occupancyAndEvidenceQuality() {
		GeodeObservation.FaceSummary faces = new GeodeObservation.FaceSummary(3, 1, 2, 0);
		assertEquals(0.25, faces.occupancy(), 1e-12);

		ScanData data = availableData();
		shell(data, 2, 10, 2, GrowthStage.CLUSTER, GrowthStage.CLUSTER, GrowthStage.SMALL_BUD);
		GeodeObservation geode = CLUSTERER.cluster(data, GeodeClusterer.Params.DEFAULT, 1000, "test").getFirst();
		assertEquals(EvidenceQuality.INSUFFICIENT, EvidenceQuality.rate(geode, 20));
		assertEquals(EvidenceQuality.LIMITED, EvidenceQuality.rate(geode, 2));
		assertEquals(EvidenceQuality.ADEQUATE, EvidenceQuality.rate(geode, 1));
	}

	@Test
	void blockKeyRoundTripsNegativeCoordinates() {
		long key = BlockKey.pack(-30_000_000, -64, 29_999_999);
		assertEquals(-30_000_000, BlockKey.x(key));
		assertEquals(-64, BlockKey.y(key));
		assertEquals(29_999_999, BlockKey.z(key));
	}
}
