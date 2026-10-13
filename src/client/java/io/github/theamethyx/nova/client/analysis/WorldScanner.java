package io.github.theamethyx.nova.client.analysis;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Util;
import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BuddingAmethystBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;

import io.github.theamethyx.nova.core.research.GrowthStage;
import io.github.theamethyx.nova.core.research.geode.GeodeClusterer;
import io.github.theamethyx.nova.world.AmethystBlocks;
import io.github.theamethyx.nova.core.research.geode.GeodeObservation;
import io.github.theamethyx.nova.core.research.geode.ScanData;

/**
 * Reads amethyst-related block states from the chunks the client has loaded, a few chunks per tick
 * within a time budget, then groups them into geodes on a background thread.
 *
 * <p>Only data the client already has is read: chunks outside the client's loaded area are
 * recorded as unavailable. Scanning never loads chunks or asks the server for anything.
 */
public final class WorldScanner {
	public enum State { IDLE, SCANNING, ANALYZING, DONE, CANCELLED, FAILED }

	private static final Direction[] DIRECTIONS = Direction.values();

	private final GeodeClusterer clusterer = new GeodeClusterer();
	private State state = State.IDLE;
	private String message = "No scan yet.";
	private ClientLevel level;
	private ScanData data;
	private List<long[]> queue = List.of();
	private int nextIndex;
	private int totalChunks;
	private int unloadedChunks;
	private long startedAt;
	private long finishedAt;
	private int generation;
	private List<GeodeObservation> results = List.of();
	private Consumer<List<GeodeObservation>> onDone = r -> { };

	/** Starts a scan of a square of chunks around the player. Cancels a running scan. */
	public void start(int radiusChunks, Consumer<List<GeodeObservation>> onDone) {
		Minecraft client = Minecraft.getInstance();
		if (client.level == null || client.player == null) {
			state = State.FAILED;
			message = "No world loaded.";
			return;
		}

		generation++;
		this.onDone = onDone;
		level = client.level;
		data = new ScanData(level.dimension().identifier().toString(), level.getMinY(), level.getMaxY());

		int centerX = client.player.getBlockX() >> 4;
		int centerZ = client.player.getBlockZ() >> 4;
		List<long[]> chunks = new ArrayList<>();
		for (int dx = -radiusChunks; dx <= radiusChunks; dx++) {
			for (int dz = -radiusChunks; dz <= radiusChunks; dz++) {
				chunks.add(new long[] {centerX + dx, centerZ + dz, (long) dx * dx + (long) dz * dz});
				data.requestColumn(centerX + dx, centerZ + dz);
			}
		}

		// Nearest chunks first, so partial progress is most useful.
		chunks.sort(Comparator.comparingLong(chunk -> chunk[2]));
		queue = chunks;
		nextIndex = 0;
		totalChunks = chunks.size();
		unloadedChunks = 0;
		startedAt = System.currentTimeMillis();
		state = State.SCANNING;
		message = "Scanning " + totalChunks + " chunks...";
	}

	public void cancel() {
		if (state == State.SCANNING || state == State.ANALYZING) {
			generation++;
			state = State.CANCELLED;
			message = "Cancelled after " + nextIndex + " of " + totalChunks + " chunks.";
		}
	}

	/** Continues scanning for at most {@code budgetNanos}. Call once per client tick. */
	public void tick(long budgetNanos) {
		if (state != State.SCANNING) {
			return;
		}

		Minecraft client = Minecraft.getInstance();
		if (client.level != level) {
			// The world changed (dimension switch, disconnect); old data is meaningless.
			generation++;
			state = State.CANCELLED;
			message = "Cancelled: the world changed during the scan.";
			return;
		}

		long deadline = System.nanoTime() + budgetNanos;
		while (nextIndex < queue.size() && System.nanoTime() < deadline) {
			long[] chunk = queue.get(nextIndex++);
			scanChunk((int) chunk[0], (int) chunk[1]);
		}

		message = "Scanning: " + nextIndex + " / " + totalChunks + " chunks" + (unloadedChunks > 0 ? " (" + unloadedChunks + " not loaded)" : "");
		if (nextIndex >= queue.size()) {
			analyze();
		}
	}

	private void scanChunk(int chunkX, int chunkZ) {
		LevelChunk chunk = level.getChunkSource().getChunk(chunkX, chunkZ, ChunkStatus.FULL, false);
		if (chunk == null) {
			unloadedChunks++;
			return;
		}

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
						if (!AmethystBlocks.isGeodeBlock(state)) {
							continue;
						}

						int wx = (chunkX << 4) + x;
						int wy = baseY + y;
						int wz = (chunkZ << 4) + z;
						record(state, wx, wy, wz);
					}
				}
			}
		}

		data.markAvailable(chunkX, chunkZ);
	}

	private void record(BlockState state, int x, int y, int z) {
		GrowthStage stage = AmethystBlocks.stageOf(state);
		if (stage != null) {
			data.addCell(new ScanData.Cell(x, y, z, ScanData.Kind.BUD, stage, AmethystBlocks.facing(state)));
		} else if (state.is(Blocks.BUDDING_AMETHYST)) {
			data.addCell(new ScanData.Cell(x, y, z, ScanData.Kind.BUDDING, null, -1));
			data.setBuddingFaces(x, y, z, faces(x, y, z));
		} else {
			data.addCell(new ScanData.Cell(x, y, z, ScanData.Kind.AMETHYST_BLOCK, null, -1));
		}
	}

	/** Same rules as BuddingAmethystBlock.randomTick, applied to the six neighbors. */
	private ScanData.FaceState[] faces(int x, int y, int z) {
		ScanData.FaceState[] faces = new ScanData.FaceState[6];
		BlockPos origin = new BlockPos(x, y, z);
		for (Direction direction : DIRECTIONS) {
			BlockPos neighbor = origin.relative(direction);
			if (!level.hasChunk(neighbor.getX() >> 4, neighbor.getZ() >> 4) || level.isOutsideBuildHeight(neighbor)) {
				faces[direction.ordinal()] = level.isOutsideBuildHeight(neighbor) ? ScanData.FaceState.BLOCKED : ScanData.FaceState.UNAVAILABLE;
				continue;
			}

			BlockState state = level.getBlockState(neighbor);
			if (BuddingAmethystBlock.canClusterGrowAtState(state)) {
				faces[direction.ordinal()] = ScanData.FaceState.FREE;
			} else if (AmethystBlocks.stageOf(state) != null && state.getValue(AmethystClusterBlock.FACING) == direction) {
				faces[direction.ordinal()] = ScanData.FaceState.OWN_GROWTH;
			} else {
				faces[direction.ordinal()] = ScanData.FaceState.BLOCKED;
			}
		}

		return faces;
	}

	private void analyze() {
		state = State.ANALYZING;
		message = "Grouping blocks into geodes...";
		int expected = generation;
		ScanData scanned = data;
		long timestamp = System.currentTimeMillis();
		Minecraft client = Minecraft.getInstance();

		CompletableFuture.supplyAsync(() -> clusterer.cluster(scanned, GeodeClusterer.Params.DEFAULT, timestamp, "client world scan"), Util.backgroundExecutor())
				.whenComplete((geodes, error) -> client.execute(() -> {
					if (expected != generation) {
						return; // cancelled or superseded
					}

					finishedAt = System.currentTimeMillis();
					if (error != null) {
						state = State.FAILED;
						message = "Analysis failed: " + error.getMessage();
						return;
					}

					results = geodes;
					state = State.DONE;
					long complete = geodes.stream().filter(GeodeObservation::complete).count();
					message = "Found " + geodes.size() + " geode(s), " + complete + " complete. Read " + (totalChunks - unloadedChunks) + " of "
							+ totalChunks + " chunks in " + (finishedAt - startedAt) + " ms" + (unloadedChunks > 0 ? "; " + unloadedChunks + " were not loaded." : ".");
					onDone.accept(geodes);
				}));
	}

	public State state() {
		return state;
	}

	public String message() {
		return message;
	}

	/** Fraction of chunks processed, from 0 to 1. */
	public float progress() {
		return totalChunks == 0 ? 0 : (float) nextIndex / totalChunks;
	}

	public List<GeodeObservation> results() {
		return results;
	}

	public boolean busy() {
		return state == State.SCANNING || state == State.ANALYZING;
	}

	public long finishedAt() {
		return finishedAt;
	}

	/** Forgets results, e.g. after leaving the world they belong to. */
	public void clear() {
		generation++;
		state = State.IDLE;
		message = "No scan yet.";
		results = List.of();
	}
}
