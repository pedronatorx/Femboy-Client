package io.github.theamethyx.femboyclient.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;

import io.github.theamethyx.femboyclient.core.config.ConfigurationManager;
import io.github.theamethyx.femboyclient.core.module.EnvironmentPolicy;

/**
 * Decides where restricted modules may run: the integrated (singleplayer) server, or a
 * multiplayer server whose address the user added to the authorized test-server list.
 */
public final class ClientEnvironment implements EnvironmentPolicy {
	private final Minecraft client;
	private final ConfigurationManager config;

	public ClientEnvironment(Minecraft client, ConfigurationManager config) {
		this.client = client;
		this.config = config;
	}

	@Override
	public boolean inWorld() {
		return client.level != null && client.player != null;
	}

	@Override
	public boolean isAuthorizedEnvironment() {
		if (!inWorld()) {
			return false;
		}

		if (client.hasSingleplayerServer()) {
			return true;
		}

		ServerData server = client.getCurrentServer();
		return server != null && config.isAuthorizedServer(server.ip);
	}

	@Override
	public String describe() {
		if (!inWorld()) {
			return "No world loaded";
		}

		if (client.hasSingleplayerServer()) {
			return "Singleplayer (authorized)";
		}

		ServerData server = client.getCurrentServer();
		if (server == null) {
			return "Unknown connection (not authorized)";
		}

		return "Server " + server.ip + (config.isAuthorizedServer(server.ip) ? " (authorized test server)" : " (not authorized)");
	}
}
