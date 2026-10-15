package io.github.theamethyx.femboyclient.client.world;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.chunk.status.ChunkStatus;

/** Whether the client actually holds a chunk. */
public final class LoadedChunks {
	private LoadedChunks() {
	}

	/**
	 * Use this instead of {@code ClientLevel.hasChunk}, which always returns {@code true} on the client
	 * (missing chunks then read as empty air).
	 */
	public static boolean isLoaded(ClientLevel level, int chunkX, int chunkZ) {
		return level.getChunkSource().getChunk(chunkX, chunkZ, ChunkStatus.FULL, false) != null;
	}
}
