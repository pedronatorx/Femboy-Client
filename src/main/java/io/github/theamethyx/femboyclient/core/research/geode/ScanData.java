package io.github.theamethyx.femboyclient.core.research.geode;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import io.github.theamethyx.femboyclient.core.research.GrowthStage;

/**
 * Raw result of scanning a region: the amethyst-related blocks that were seen, the state of the
 * six faces of every budding block, and which chunk columns could actually be read.
 *
 * <p>Pure data with no Minecraft dependency, so the grouping logic can be unit tested. Columns that
 * were not loaded or not scanned are <em>unavailable</em>; nothing about them is assumed.
 */
public final class ScanData {
	public enum Kind { AMETHYST_BLOCK, BUDDING, BUD }

	/** What is next to one face of a budding amethyst block. */
	public enum FaceState {
		/** Air or a water source: a new bud can grow here. */
		FREE,
		/** A bud or cluster attached to this budding block (facing away from it). */
		OWN_GROWTH,
		/** Anything else (stone, another budding block, a bud attached elsewhere): no growth. */
		BLOCKED,
		/** The neighboring block could not be read. */
		UNAVAILABLE
	}

	/** {@code facing} is a direction index (0-5) for buds, -1 otherwise. */
	public record Cell(int x, int y, int z, Kind kind, GrowthStage stage, int facing) {
	}

	private final String dimension;
	private final int minY;
	private final int maxY;
	private final Map<Long, Cell> cells = new HashMap<>();
	private final Map<Long, FaceState[]> buddingFaces = new HashMap<>();
	private final Set<Long> availableColumns = new HashSet<>();
	private final Set<Long> requestedColumns = new HashSet<>();

	public ScanData(String dimension, int minY, int maxY) {
		this.dimension = dimension;
		this.minY = minY;
		this.maxY = maxY;
	}

	public void addCell(Cell cell) {
		cells.put(BlockKey.pack(cell.x(), cell.y(), cell.z()), cell);
	}

	public void setBuddingFaces(int x, int y, int z, FaceState[] faces) {
		if (faces.length != 6) {
			throw new IllegalArgumentException("A block has six faces");
		}

		buddingFaces.put(BlockKey.pack(x, y, z), faces.clone());
	}

	/** Marks a chunk column as part of the requested analysis region. */
	public void requestColumn(int chunkX, int chunkZ) {
		requestedColumns.add(BlockKey.column(chunkX, chunkZ));
	}

	/** Marks a chunk column as fully read. */
	public void markAvailable(int chunkX, int chunkZ) {
		availableColumns.add(BlockKey.column(chunkX, chunkZ));
	}

	public boolean isColumnAvailable(int chunkX, int chunkZ) {
		return availableColumns.contains(BlockKey.column(chunkX, chunkZ));
	}

	public Map<Long, Cell> cells() {
		return cells;
	}

	public FaceState[] buddingFaces(int x, int y, int z) {
		return buddingFaces.get(BlockKey.pack(x, y, z));
	}

	public String dimension() {
		return dimension;
	}

	public int minY() {
		return minY;
	}

	public int maxY() {
		return maxY;
	}

	public int requestedColumns() {
		return requestedColumns.size();
	}

	public int availableColumns() {
		return availableColumns.size();
	}
}
