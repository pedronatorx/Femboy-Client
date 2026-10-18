package io.github.theamethyx.femboyclient.client.world;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;

/**
 * Finds blocks matching a filter in chunks the client has loaded, a few chunks per tick within a time budget, nearest
 * chunks first. Never loads chunks or asks the server for anything.
 *
 * <p>After the first read of a chunk, the scanner follows changes as they happen ({@link BlockChanges}): a changed
 * block is added or removed at once, a chunk that arrives again is re-read, an unloaded chunk is dropped. A periodic
 * re-read ({@code rescanMillis}) remains only as a safety net. {@link #version()} changes whenever the hits change, so
 * users recompute their own lists only then.
 *
 * <p>Call {@link #start()} when the owner starts and {@link #stop()} when it stops, so it receives changes only while
 * needed.
 */
public final class BlockScanner implements BlockChanges.Listener {
	public record Hit(BlockPos pos, BlockState state) {
	}

	private final Map<Long, List<Hit>> byChunk = new HashMap<>();
	private final Map<Long, Long> scannedAt = new HashMap<>();
	/** Chunks that arrived (again) and must be read on the next pass. */
	private final Set<Long> dirty = new HashSet<>();
	private Predicate<BlockState> filter = state -> false;
	private ClientLevel level;
	private int total;
	private int inRange;
	private long version;
	private long hitsVersion = -1;
	private List<Hit> hits = List.of();
	private int lastCenterX = Integer.MIN_VALUE;
	private int lastCenterZ = Integer.MIN_VALUE;
	private int lastRadius = -1;
	private boolean complete;
	private long nextFullPassAt;
	private int chunkReads;
	private int incrementalUpdates;

	public void start() {
		BlockChanges.add(this);
	}

	public void stop() {
		BlockChanges.remove(this);
		clear();
	}

	public void setFilter(Predicate<BlockState> filter) {
		this.filter = filter;
		clear();
	}

	public void clear() {
		byChunk.clear();
		scannedAt.clear();
		dirty.clear();
		total = 0;
		complete = false;
		lastRadius = -1;
		version++;
	}

	/** Changes whenever the set of hits changes. */
	public long version() {
		return version;
	}

	/**
	 * Reads new, re-sent or stale chunks within {@code radiusChunks} of {@code center} until {@code budgetNanos} is used.
	 * Does nothing when every chunk in range is read and nothing changed.
	 */
	public void tick(ClientLevel current, BlockPos center, int radiusChunks, long rescanMillis, long budgetNanos) {
		if (current != level) {
			level = current;
			clear();
		}

		if (level == null) {
			return;
		}

		int cx = center.getX() >> 4;
		int cz = center.getZ() >> 4;
		long now = System.currentTimeMillis();
		boolean moved = cx != lastCenterX || cz != lastCenterZ || radiusChunks != lastRadius;
		if (!moved && complete && dirty.isEmpty() && now < nextFullPassAt) {
			return;
		}

		if (moved) {
			lastCenterX = cx;
			lastCenterZ = cz;
			lastRadius = radiusChunks;
			int before = byChunk.size();
			byChunk.entrySet().removeIf(entry -> {
				long key = entry.getKey();
				boolean outside = Math.abs(ChunkPos.getX(key) - cx) > radiusChunks || Math.abs(ChunkPos.getZ(key) - cz) > radiusChunks;
				if (outside) {
					total -= entry.getValue().size();
				}

				return outside;
			});
			scannedAt.keySet().retainAll(byChunk.keySet());
			if (byChunk.size() != before) {
				version++;
			}
		}

		long start = System.nanoTime();
		inRange = 0;
		boolean allRead = true;
		for (int ring = 0; ring <= radiusChunks; ring++) {
			for (int dx = -ring; dx <= ring; dx++) {
				for (int dz = -ring; dz <= ring; dz++) {
					if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) {
						continue;
					}

					LevelChunk chunk = level.getChunkSource().getChunk(cx + dx, cz + dz, ChunkStatus.FULL, false);
					if (chunk == null) {
						continue;
					}

					inRange++;
					long key = ChunkPos.asLong(cx + dx, cz + dz);
					Long when = scannedAt.get(key);
					boolean due = when == null || dirty.contains(key) || rescanMillis > 0 && now - when >= rescanMillis;
					if (!due) {
						continue;
					}

					if (System.nanoTime() - start > budgetNanos) {
						allRead = false;
						continue;
					}

					List<Hit> fresh = scan(chunk);
					List<Hit> old = byChunk.put(key, fresh);
					total += fresh.size() - (old == null ? 0 : old.size());
					if (old == null || !old.equals(fresh)) {
						version++;
					}

					scannedAt.put(key, now);
					dirty.remove(key);
					chunkReads++;
				}
			}
		}

		complete = allRead;
		if (allRead) {
			// Changes arrive as events; this pass only repeats as a safety net.
			nextFullPassAt = rescanMillis > 0 ? now + rescanMillis : Long.MAX_VALUE;
		}
	}

	private List<Hit> scan(LevelChunk chunk) {
		List<Hit> found = new ArrayList<>();
		LevelChunkSection[] sections = chunk.getSections();
		int minSectionY = level.getMinSectionY();
		int baseX = chunk.getPos().getMinBlockX();
		int baseZ = chunk.getPos().getMinBlockZ();
		for (int i = 0; i < sections.length; i++) {
			LevelChunkSection section = sections[i];
			if (section == null || section.hasOnlyAir() || !section.maybeHas(filter)) {
				continue;
			}

			int baseY = (minSectionY + i) << 4;
			for (int y = 0; y < 16; y++) {
				for (int z = 0; z < 16; z++) {
					for (int x = 0; x < 16; x++) {
						BlockState state = section.getBlockState(x, y, z);
						if (filter.test(state)) {
							found.add(new Hit(new BlockPos(baseX + x, baseY + y, baseZ + z), state));
						}
					}
				}
			}
		}

		return found;
	}

	// ----- live changes ----------------------------------------------------------------------

	@Override
	public void blockChanged(ClientLevel changedLevel, BlockPos pos, BlockState oldState, BlockState newState) {
		if (changedLevel != level) {
			return;
		}

		List<Hit> list = byChunk.get(ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4));
		if (list == null) {
			return; // not read yet: the first read sees the new state
		}

		boolean was = filter.test(oldState);
		boolean is = filter.test(newState);
		if (!was && !is) {
			return;
		}

		if (was) {
			for (int i = 0; i < list.size(); i++) {
				if (list.get(i).pos().equals(pos)) {
					list.remove(i);
					total--;
					break;
				}
			}
		}

		if (is) {
			list.add(new Hit(pos.immutable(), newState));
			total++;
		}

		incrementalUpdates++;
		version++;
	}

	@Override
	public void chunkLoaded(ClientLevel loadedLevel, LevelChunk chunk) {
		if (loadedLevel == level) {
			dirty.add(chunk.getPos().toLong());
		}
	}

	@Override
	public void chunkUnloaded(ClientLevel unloadedLevel, LevelChunk chunk) {
		if (unloadedLevel != level) {
			return;
		}

		long key = chunk.getPos().toLong();
		List<Hit> removed = byChunk.remove(key);
		scannedAt.remove(key);
		dirty.remove(key);
		if (removed != null) {
			total -= removed.size();
			version++;
		}
	}

	// ----- results ---------------------------------------------------------------------------

	/** All hits, as one immutable list that is rebuilt only when {@link #version()} changed. */
	public List<Hit> hits() {
		if (hitsVersion != version) {
			List<Hit> all = new ArrayList<>(Math.max(0, total));
			byChunk.values().forEach(all::addAll);
			hits = List.copyOf(all);
			hitsVersion = version;
		}

		return hits;
	}

	public int count() {
		return total;
	}

	/** Chunks read so far out of loaded chunks in range. */
	public int scannedChunks() {
		return byChunk.size();
	}

	public int chunksInRange() {
		return inRange;
	}

	/** Whole-chunk reads so far (for tests and the benchmark). */
	public int chunkReads() {
		return chunkReads;
	}

	/** Single-block updates applied from change events (for tests). */
	public int incrementalUpdates() {
		return incrementalUpdates;
	}
}
