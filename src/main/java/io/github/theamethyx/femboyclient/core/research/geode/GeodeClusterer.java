package io.github.theamethyx.femboyclient.core.research.geode;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.github.theamethyx.femboyclient.core.research.StageCounts;

/**
 * Groups scanned amethyst blocks into geodes and records how complete each observation is.
 *
 * <p>Grouping: amethyst blocks, budding amethyst and growth objects that touch (including
 * diagonally, i.e. 26-connectivity) belong to the same geode. A geode's growth objects are attached
 * to its budding blocks, so they are always connected to its shell and are counted exactly once.
 *
 * <p>Limitation (documented in the README): two geodes whose shells touch are merged, and a geode
 * cut in two by a cave is reported as two geodes. Registered test fixtures avoid both cases.
 */
public final class GeodeClusterer {
	/**
	 * @param minShellBlocks smallest number of amethyst + budding blocks treated as a geode (filters
	 *                       out single placed blocks)
	 * @param margin         blocks around the geode that must also be readable for "complete"
	 */
	public record Params(int minShellBlocks, int margin) {
		public static final Params DEFAULT = new Params(8, 2);
	}

	public List<GeodeObservation> cluster(ScanData data, Params params, long timestampMillis, String source) {
		Map<Long, ScanData.Cell> cells = data.cells();
		Set<Long> visited = new HashSet<>();
		List<GeodeObservation> geodes = new ArrayList<>();

		for (Long start : cells.keySet()) {
			if (visited.contains(start)) {
				continue;
			}

			List<ScanData.Cell> component = new ArrayList<>();
			Deque<Long> queue = new ArrayDeque<>();
			queue.add(start);
			visited.add(start);

			while (!queue.isEmpty()) {
				long key = queue.poll();
				ScanData.Cell cell = cells.get(key);
				component.add(cell);

				for (int dx = -1; dx <= 1; dx++) {
					for (int dy = -1; dy <= 1; dy++) {
						for (int dz = -1; dz <= 1; dz++) {
							if (dx == 0 && dy == 0 && dz == 0) continue;
							long neighbor = BlockKey.pack(cell.x() + dx, cell.y() + dy, cell.z() + dz);
							if (cells.containsKey(neighbor) && visited.add(neighbor)) {
								queue.add(neighbor);
							}
						}
					}
				}
			}

			GeodeObservation geode = summarize(data, component, params, timestampMillis, source);
			if (geode != null) {
				geodes.add(geode);
			}
		}

		geodes.sort(Comparator.comparingInt(GeodeObservation::minX).thenComparingInt(GeodeObservation::minZ).thenComparingInt(GeodeObservation::minY));
		return geodes;
	}

	private GeodeObservation summarize(ScanData data, List<ScanData.Cell> component, Params params, long timestampMillis, String source) {
		int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
		int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
		StageCounts counts = StageCounts.EMPTY;
		int budding = 0;
		int amethyst = 0;
		int free = 0, own = 0, blocked = 0, unavailableFaces = 0;
		List<GeodeObservation.PositionedStage> growth = new ArrayList<>();

		for (ScanData.Cell cell : component) {
			minX = Math.min(minX, cell.x());
			minY = Math.min(minY, cell.y());
			minZ = Math.min(minZ, cell.z());
			maxX = Math.max(maxX, cell.x());
			maxY = Math.max(maxY, cell.y());
			maxZ = Math.max(maxZ, cell.z());

			switch (cell.kind()) {
				case BUD -> {
					counts = counts.plus(cell.stage());
					growth.add(new GeodeObservation.PositionedStage(cell.x(), cell.y(), cell.z(), cell.stage()));
				}
				case AMETHYST_BLOCK -> amethyst++;
				case BUDDING -> {
					budding++;
					ScanData.FaceState[] faces = data.buddingFaces(cell.x(), cell.y(), cell.z());
					if (faces == null) {
						unavailableFaces += 6;
					} else {
						for (ScanData.FaceState face : faces) {
							switch (face) {
								case FREE -> free++;
								case OWN_GROWTH -> own++;
								case BLOCKED -> blocked++;
								case UNAVAILABLE -> unavailableFaces++;
							}
						}
					}
				}
			}
		}

		if (amethyst + budding < params.minShellBlocks()) {
			return null;
		}

		// Count unreadable positions in the geode's box plus margin, column by column.
		int m = params.margin();
		int boxMinY = Math.max(data.minY(), minY - m);
		int boxMaxY = Math.min(data.maxY(), maxY + m);
		int height = Math.max(0, boxMaxY - boxMinY + 1);
		int unavailablePositions = 0;
		int unavailableColumns = 0;
		for (int x = minX - m; x <= maxX + m; x++) {
			for (int z = minZ - m; z <= maxZ + m; z++) {
				if (!data.isColumnAvailable(x >> 4, z >> 4)) {
					unavailablePositions += height;
				}
			}
		}

		for (int cx = (minX - m) >> 4; cx <= (maxX + m) >> 4; cx++) {
			for (int cz = (minZ - m) >> 4; cz <= (maxZ + m) >> 4; cz++) {
				if (!data.isColumnAvailable(cx, cz)) {
					unavailableColumns++;
				}
			}
		}

		boolean touchesHeightLimit = minY - m < data.minY() || maxY + m > data.maxY();
		boolean complete = unavailableColumns == 0 && unavailableFaces == 0 && !touchesHeightLimit;
		String note;
		if (complete) {
			note = "Complete: the geode and a " + m + "-block margin were fully readable.";
		} else {
			List<String> reasons = new ArrayList<>();
			if (unavailableColumns > 0) reasons.add(unavailableColumns + " chunk column(s) around it were not loaded or not scanned");
			if (unavailableFaces > 0) reasons.add(unavailableFaces + " budding-block face(s) could not be read");
			if (touchesHeightLimit) reasons.add("it reaches the edge of the scanned height range");
			note = "Incomplete: " + String.join("; ", reasons) + ". Counts are a lower bound for the whole geode.";
		}

		String id = data.dimension() + "@" + minX + "," + minY + "," + minZ;
		return new GeodeObservation(id, data.dimension(), minX, minY, minZ, maxX, maxY, maxZ, counts, budding, amethyst,
				new GeodeObservation.FaceSummary(free, own, blocked, unavailableFaces), unavailablePositions, complete, note,
				timestampMillis, null, source, List.copyOf(growth));
	}
}
