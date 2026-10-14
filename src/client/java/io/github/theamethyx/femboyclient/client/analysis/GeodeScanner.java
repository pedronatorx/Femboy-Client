package io.github.theamethyx.femboyclient.client.analysis;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Util;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;

import io.github.theamethyx.femboyclient.core.research.GrowthStage;
import io.github.theamethyx.femboyclient.core.research.geode.BlockKey;
import io.github.theamethyx.femboyclient.core.research.geode.ChunkObservationCache;
import io.github.theamethyx.femboyclient.core.research.geode.GeodeClusterer;
import io.github.theamethyx.femboyclient.core.research.geode.GeodeObservation;
import io.github.theamethyx.femboyclient.core.research.geode.ScanData;
import io.github.theamethyx.femboyclient.world.AmethystBlocks;

/**
 * Keeps an up-to-date geode analysis of the loaded chunks around the player.
 *
 * <p>Each tick it reads a few chunk columns within a time budget: columns never read first, nearest
 * first, then columns whose data is older than the scan interval. Reading happens on the client thread
 * (Minecraft's chunk data is not thread-safe); grouping into geodes runs on a background thread and only
 * when the cached content actually changed. Chunks the client has not loaded are never requested and are
 * reported as unavailable.
 */
public final class GeodeScanner {
	/** Minimum time between two analyses, so a steady trickle of changes cannot keep a core busy. */
	private static final long MIN_ANALYSIS_GAP_MS = 1_500;
	private static final Direction[] DIRECTIONS = Direction.values();

	private final ChunkObservationCache cache = new ChunkObservationCache();
	private final GeodeClusterer clusterer = new GeodeClusterer();
	private ClientLevel level;
	private List<int[]> order = List.of();
	private int orderX = Integer.MIN_VALUE;
	private int orderZ;
	private int orderRadius = -1;
	private long forceBefore;

	private int loadedInRange;
	private int readInRange;
	private int unloadedInRange;

	private boolean analyzing;
	private int generation;
	private long analyzedVersion = -1;
	private long nextAnalysisAt;
	private long lastAnalysisMillis;
	private long resultsAt;
	private String failure;
	private List<GeodeObservation> results = List.of();
	private Map<String, Long> dataReadAt = Map.of();

	/** Forgets everything, e.g. when the module is turned off or the world changes. */
	public void clear() {
		generation++;
		analyzing = false;
		cache.clear();
		results = List.of();
		dataReadAt = Map.of();
		analyzedVersion = -1;
		resultsAt = 0;
		failure = null;
		order = List.of();
		orderRadius = -1;
		level = null;
	}

	/** Re-reads every loaded column on the next ticks without dropping the current results. */
	public void rescanAll() {
		forceBefore = System.currentTimeMillis();
	}

	/**
	 * Drops everything when the world or dimension changed. Called every tick, also while paused, so results
	 * from another world are never shown.
	 *
	 * @return whether a world is loaded
	 */
	public boolean checkWorld() {
		Minecraft client = Minecraft.getInstance();
		if (client.level == null || client.player == null) {
			if (level != null) {
				clear();
			}

			return false;
		}

		if (client.level != level) {
			// New world or dimension: nothing cached belongs here.
			clear();
			level = client.level;
		}

		return true;
	}

	public void tick(int radius, long intervalMillis, long budgetNanos) {
		Minecraft client = Minecraft.getInstance();
		if (!checkWorld()) {
			return;
		}

		int centerX = client.player.getBlockX() >> 4;
		int centerZ = client.player.getBlockZ() >> 4;
		if (centerX != orderX || centerZ != orderZ || radius != orderRadius) {
			order = squareNearestFirst(centerX, centerZ, radius);
			orderX = centerX;
			orderZ = centerZ;
			orderRadius = radius;
			cache.retainWithin(centerX, centerZ, radius);
		}

		long now = System.currentTimeMillis();
		long deadline = System.nanoTime() + budgetNanos;
		int loaded = 0;
		int read = 0;
		boolean outOfTime = false;
		for (int[] column : order) {
			int x = column[0];
			int z = column[1];
			LevelChunk chunk = level.getChunkSource().getChunk(x, z, ChunkStatus.FULL, false);
			if (chunk == null) {
				cache.remove(x, z); // unloaded since it was read: unknown again, never "empty"
				continue;
			}

			loaded++;
			boolean due = cache.due(x, z, now, intervalMillis) || cache.readAt(x, z) < forceBefore;
			if (due && !outOfTime) {
				cache.put(x, z, readColumn(chunk, x, z), now);
				outOfTime = System.nanoTime() >= deadline;
			}

			if (cache.contains(x, z)) {
				read++;
			}
		}

		loadedInRange = loaded;
		readInRange = read;
		unloadedInRange = order.size() - loaded;

		if (!analyzing && cache.version() != analyzedVersion && now >= nextAnalysisAt) {
			analyze(centerX, centerZ, radius, now);
		}
	}

	private static List<int[]> squareNearestFirst(int centerX, int centerZ, int radius) {
		List<int[]> columns = new ArrayList<>();
		for (int dx = -radius; dx <= radius; dx++) {
			for (int dz = -radius; dz <= radius; dz++) {
				columns.add(new int[] {centerX + dx, centerZ + dz, dx * dx + dz * dz});
			}
		}

		columns.sort(Comparator.comparingInt(c -> c[2]));
		return List.copyOf(columns);
	}

	// ----- reading ---------------------------------------------------------------------------

	private ChunkObservationCache.Column readColumn(LevelChunk chunk, int chunkX, int chunkZ) {
		List<ScanData.Cell> cells = new ArrayList<>();
		Map<Long, ScanData.FaceState[]> faces = new HashMap<>();
		LevelChunkSection[] sections = chunk.getSections();
		int minSectionY = level.getMinSectionY();
		for (int i = 0; i < sections.length; i++) {
			LevelChunkSection section = sections[i];
			if (section == null || section.hasOnlyAir() || !section.maybeHas(AmethystBlocks::isGeodeBlock)) {
				continue;
			}

			int baseY = (minSectionY + i) << 4;
			for (int y = 0; y < 16; y++) {
				for (int z = 0; z < 16; z++) {
					for (int x = 0; x < 16; x++) {
						BlockState state = section.getBlockState(x, y, z);
						if (AmethystBlocks.isGeodeBlock(state)) {
							record(state, (chunkX << 4) + x, baseY + y, (chunkZ << 4) + z, cells, faces);
						}
					}
				}
			}
		}

		return new ChunkObservationCache.Column(cells, faces);
	}

	private void record(BlockState state, int x, int y, int z, List<ScanData.Cell> cells, Map<Long, ScanData.FaceState[]> faces) {
		GrowthStage stage = AmethystBlocks.stageOf(state);
		if (stage != null) {
			cells.add(new ScanData.Cell(x, y, z, ScanData.Kind.BUD, stage, AmethystBlocks.facing(state)));
		} else if (state.is(Blocks.BUDDING_AMETHYST)) {
			cells.add(new ScanData.Cell(x, y, z, ScanData.Kind.BUDDING, null, -1));
			faces.put(BlockKey.pack(x, y, z), faces(x, y, z));
		} else {
			cells.add(new ScanData.Cell(x, y, z, ScanData.Kind.AMETHYST_BLOCK, null, -1));
		}
	}

	/** Same rules as BuddingAmethystBlock.randomTick, applied to the six neighbors. */
	private ScanData.FaceState[] faces(int x, int y, int z) {
		ScanData.FaceState[] faces = new ScanData.FaceState[6];
		BlockPos origin = new BlockPos(x, y, z);
		for (Direction direction : DIRECTIONS) {
			BlockPos neighbor = origin.relative(direction);
			if (level.isOutsideBuildHeight(neighbor)) {
				faces[direction.ordinal()] = ScanData.FaceState.BLOCKED;
			} else if (!level.hasChunk(neighbor.getX() >> 4, neighbor.getZ() >> 4)) {
				faces[direction.ordinal()] = ScanData.FaceState.UNAVAILABLE;
			} else {
				faces[direction.ordinal()] = AmethystBlocks.faceState(level.getBlockState(neighbor), direction);
			}
		}

		return faces;
	}

	// ----- analysis --------------------------------------------------------------------------

	private void analyze(int centerX, int centerZ, int radius, long now) {
		analyzing = true;
		int expected = generation;
		long version = cache.version();
		ScanData snapshot = cache.snapshot(level.dimension().identifier().toString(), level.getMinY(), level.getMaxY(), centerX, centerZ, radius);
		ChunkObservationCache readTimes = cache;
		Minecraft client = Minecraft.getInstance();
		long started = System.nanoTime();

		CompletableFuture.supplyAsync(() -> clusterer.cluster(snapshot, GeodeClusterer.Params.DEFAULT, now, "Geode Analyzer"), Util.backgroundExecutor())
				.whenComplete((geodes, error) -> client.execute(() -> {
					if (expected != generation) {
						return; // cleared or superseded meanwhile
					}

					analyzing = false;
					analyzedVersion = version;
					lastAnalysisMillis = (System.nanoTime() - started) / 1_000_000;
					nextAnalysisAt = System.currentTimeMillis() + MIN_ANALYSIS_GAP_MS;
					if (error != null) {
						failure = "Analysis failed: " + error.getMessage();
						return;
					}

					failure = null;
					Map<String, Long> readAt = new HashMap<>();
					for (GeodeObservation geode : geodes) {
						readAt.put(geode.id(), readTimes.oldestRead(geode.minX() >> 4, geode.minZ() >> 4, geode.maxX() >> 4, geode.maxZ() >> 4));
					}

					results = List.copyOf(geodes);
					dataReadAt = Map.copyOf(readAt);
					resultsAt = System.currentTimeMillis();
				}));
	}

	// ----- state for the GUI and tests -------------------------------------------------------

	public List<GeodeObservation> results() {
		return results;
	}

	/** When the oldest chunk data behind a geode was read (epoch ms), or -1 if unknown. */
	public long dataReadAt(GeodeObservation geode) {
		return dataReadAt.getOrDefault(geode.id(), -1L);
	}

	/** When the current results were produced (epoch ms), 0 before the first analysis. */
	public long resultsAt() {
		return resultsAt;
	}

	public boolean analyzing() {
		return analyzing;
	}

	/** Loaded columns in range that have been read at least once, vs. all loaded columns in range. */
	public int readColumns() {
		return readInRange;
	}

	public int loadedColumns() {
		return loadedInRange;
	}

	/** Columns inside the analysis radius that this client has not loaded. */
	public int unloadedColumns() {
		return unloadedInRange;
	}

	/** Whether everything currently loaded in range has been read and analyzed. */
	public boolean upToDate() {
		return readInRange == loadedInRange && !analyzing && analyzedVersion == cache.version();
	}

	public String failure() {
		return failure;
	}

	public long lastAnalysisMillis() {
		return lastAnalysisMillis;
	}

	public int cachedColumns() {
		return cache.size();
	}

	public int cachedCells() {
		return cache.cellCount();
	}
}
