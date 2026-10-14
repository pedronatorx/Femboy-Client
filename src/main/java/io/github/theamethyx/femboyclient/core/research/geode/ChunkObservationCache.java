package io.github.theamethyx.femboyclient.core.research.geode;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Amethyst-related cells per chunk column, kept between analyses so unchanged chunks are not re-read and
 * re-analyzed on every pass.
 *
 * <p>Each column remembers when it was read. {@link #version()} changes only when the cached content
 * actually changes (new or different cells, or a column removed), so callers can skip re-analysis when
 * nothing changed. Columns that are not in the cache are reported as unavailable in {@link #snapshot},
 * never as empty.
 */
public final class ChunkObservationCache {
	/** What one chunk column contained when it was read. */
	public record Column(List<ScanData.Cell> cells, Map<Long, ScanData.FaceState[]> buddingFaces) {
		public Column {
			cells = List.copyOf(cells);
			buddingFaces = Map.copyOf(buddingFaces);
		}

		/** Content fingerprint; equal content gives an equal value. */
		int fingerprint() {
			int hash = cells.hashCode();
			for (Map.Entry<Long, ScanData.FaceState[]> entry : new java.util.TreeMap<>(buddingFaces).entrySet()) {
				hash = 31 * hash + Long.hashCode(entry.getKey());
				hash = 31 * hash + java.util.Arrays.hashCode(entry.getValue());
			}

			return hash;
		}

		/** Same cells and same budding faces (arrays compared by content). */
		boolean sameContent(Column other) {
			if (!cells.equals(other.cells) || !buddingFaces.keySet().equals(other.buddingFaces.keySet())) {
				return false;
			}

			for (Map.Entry<Long, ScanData.FaceState[]> entry : buddingFaces.entrySet()) {
				if (!java.util.Arrays.equals(entry.getValue(), other.buddingFaces.get(entry.getKey()))) {
					return false;
				}
			}

			return true;
		}
	}

	private record Entry(Column column, int fingerprint, long readAtMillis) {
	}

	private final Map<Long, Entry> columns = new HashMap<>();
	private long version;

	public static long key(int chunkX, int chunkZ) {
		return ((long) chunkX << 32) | (chunkZ & 0xFFFFFFFFL);
	}

	/** Whether a column has never been read or was read at least {@code intervalMillis} ago. */
	public boolean due(int chunkX, int chunkZ, long nowMillis, long intervalMillis) {
		Entry entry = columns.get(key(chunkX, chunkZ));
		return entry == null || nowMillis - entry.readAtMillis() >= intervalMillis;
	}

	/**
	 * Stores a freshly read column.
	 *
	 * @return whether the content differs from what was cached before
	 */
	public boolean put(int chunkX, int chunkZ, Column column, long nowMillis) {
		Objects.requireNonNull(column);
		int fingerprint = column.fingerprint();
		Entry previous = columns.put(key(chunkX, chunkZ), new Entry(column, fingerprint, nowMillis));
		boolean changed = previous == null || previous.fingerprint() != fingerprint || !previous.column().sameContent(column);
		if (changed) {
			version++;
		}

		return changed;
	}

	/** Forgets a column, e.g. because the client unloaded it. */
	public boolean remove(int chunkX, int chunkZ) {
		boolean removed = columns.remove(key(chunkX, chunkZ)) != null;
		if (removed) {
			version++;
		}

		return removed;
	}

	/** Drops columns outside the square of {@code radius} chunks around the center. Returns how many. */
	public int retainWithin(int centerX, int centerZ, int radius) {
		int removed = 0;
		Iterator<Map.Entry<Long, Entry>> it = columns.entrySet().iterator();
		while (it.hasNext()) {
			long key = it.next().getKey();
			int x = (int) (key >> 32);
			int z = (int) key;
			if (Math.abs(x - centerX) > radius || Math.abs(z - centerZ) > radius) {
				it.remove();
				removed++;
			}
		}

		if (removed > 0) {
			version++;
		}

		return removed;
	}

	/** When a column was last read (epoch ms), or {@link Long#MAX_VALUE} if it is not cached. */
	public long readAt(int chunkX, int chunkZ) {
		Entry entry = columns.get(key(chunkX, chunkZ));
		return entry == null ? Long.MAX_VALUE : entry.readAtMillis();
	}

	public boolean contains(int chunkX, int chunkZ) {
		return columns.containsKey(key(chunkX, chunkZ));
	}

	public void clear() {
		if (!columns.isEmpty()) {
			columns.clear();
			version++;
		}
	}

	/** Changes whenever the cached content changes. */
	public long version() {
		return version;
	}

	public int size() {
		return columns.size();
	}

	public int cellCount() {
		return columns.values().stream().mapToInt(e -> e.column().cells().size()).sum();
	}

	/** Oldest read time among cached columns overlapping the given chunk range, or -1 if none is cached. */
	public long oldestRead(int minChunkX, int minChunkZ, int maxChunkX, int maxChunkZ) {
		long oldest = Long.MAX_VALUE;
		for (int x = minChunkX; x <= maxChunkX; x++) {
			for (int z = minChunkZ; z <= maxChunkZ; z++) {
				Entry entry = columns.get(key(x, z));
				if (entry != null) {
					oldest = Math.min(oldest, entry.readAtMillis());
				}
			}
		}

		return oldest == Long.MAX_VALUE ? -1 : oldest;
	}

	/**
	 * Builds analysis input for the square of {@code radius} chunks around the center: every column in the
	 * square is requested, only cached ones are marked available, so the clusterer reports the rest as
	 * missing data rather than empty space.
	 */
	public ScanData snapshot(String dimension, int minY, int maxY, int centerX, int centerZ, int radius) {
		ScanData data = new ScanData(dimension, minY, maxY);
		for (int x = centerX - radius; x <= centerX + radius; x++) {
			for (int z = centerZ - radius; z <= centerZ + radius; z++) {
				data.requestColumn(x, z);
				Entry entry = columns.get(key(x, z));
				if (entry == null) {
					continue;
				}

				data.markAvailable(x, z);
				for (ScanData.Cell cell : entry.column().cells()) {
					data.addCell(cell);
					if (cell.kind() == ScanData.Kind.BUDDING) {
						ScanData.FaceState[] faces = entry.column().buddingFaces().get(BlockKey.pack(cell.x(), cell.y(), cell.z()));
						if (faces != null) {
							data.setBuddingFaces(cell.x(), cell.y(), cell.z(), faces);
						}
					}
				}
			}
		}

		return data;
	}
}
