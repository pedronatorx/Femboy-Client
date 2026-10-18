package io.github.theamethyx.femboyclient.client.world;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;

/**
 * Client-side block and chunk changes, for caches that describe loaded blocks: a single block changed (server update or
 * the client's own prediction), a chunk arrived, or a chunk was unloaded. Listeners run on the client thread.
 */
public final class BlockChanges {
	public interface Listener {
		void blockChanged(ClientLevel level, BlockPos pos, BlockState oldState, BlockState newState);

		void chunkLoaded(ClientLevel level, LevelChunk chunk);

		void chunkUnloaded(ClientLevel level, LevelChunk chunk);
	}

	private static final List<Listener> LISTENERS = new CopyOnWriteArrayList<>();
	private static long blockEvents;

	private BlockChanges() {
	}

	/** Connects the chunk events; called once at startup. */
	public static void register() {
		ClientChunkEvents.CHUNK_LOAD.register((level, chunk) -> LISTENERS.forEach(listener -> listener.chunkLoaded(level, chunk)));
		ClientChunkEvents.CHUNK_UNLOAD.register((level, chunk) -> LISTENERS.forEach(listener -> listener.chunkUnloaded(level, chunk)));
	}

	public static void add(Listener listener) {
		if (!LISTENERS.contains(listener)) {
			LISTENERS.add(listener);
		}
	}

	public static void remove(Listener listener) {
		LISTENERS.remove(listener);
	}

	/** Called by {@code ClientLevelMixin}. */
	public static void fire(ClientLevel level, BlockPos pos, BlockState oldState, BlockState newState) {
		blockEvents++;
		for (Listener listener : LISTENERS) {
			listener.blockChanged(level, pos, oldState, newState);
		}
	}

	/** Block changes seen so far (for tests). */
	public static long blockEvents() {
		return blockEvents;
	}
}
