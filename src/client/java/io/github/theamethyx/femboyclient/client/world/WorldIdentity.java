package io.github.theamethyx.femboyclient.client.world;

import java.nio.file.Path;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.world.level.storage.LevelResource;

/**
 * A stable key for "which world am I in", used to keep waypoints and test fixtures apart:
 * {@code singleplayer/<save folder>} or {@code server/<address>}.
 */
public final class WorldIdentity {
	private WorldIdentity() {
	}

	/** @return the key, or {@code null} when no world is loaded */
	public static String current() {
		Minecraft client = Minecraft.getInstance();
		if (client.level == null) {
			return null;
		}

		IntegratedServer server = client.getSingleplayerServer();
		if (server != null) {
			Path root = server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
			Path folder = root.getFileName();
			return "singleplayer/" + (folder == null ? server.getWorldData().getLevelName() : folder.toString());
		}

		ServerData data = client.getCurrentServer();
		return data == null ? "unknown" : "server/" + data.ip.toLowerCase(java.util.Locale.ROOT);
	}

	/** Dimension identifier of the client world, e.g. {@code minecraft:overworld}. */
	public static String dimension() {
		Minecraft client = Minecraft.getInstance();
		return client.level == null ? null : client.level.dimension().identifier().toString();
	}
}
