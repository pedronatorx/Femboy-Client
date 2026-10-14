package io.github.theamethyx.femboyclient.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;

import io.github.theamethyx.femboyclient.core.module.EnvironmentPolicy;

/** Tells modules whether a world is loaded and describes the current connection for the GUI. */
public final class ClientEnvironment implements EnvironmentPolicy {
	private final Minecraft client;

	public ClientEnvironment(Minecraft client) {
		this.client = client;
	}

	@Override
	public boolean inWorld() {
		return client.level != null && client.player != null;
	}

	@Override
	public String describe() {
		if (!inWorld()) {
			return "No world loaded";
		}

		if (client.hasSingleplayerServer()) {
			return "Singleplayer";
		}

		ServerData server = client.getCurrentServer();
		return server == null ? "Multiplayer" : "Server " + server.ip;
	}
}
