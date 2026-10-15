package io.github.theamethyx.femboyclient.client;

import java.net.InetSocketAddress;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.ServerData;

import io.github.theamethyx.femboyclient.core.module.EnvironmentPolicy;
import io.github.theamethyx.femboyclient.core.module.PrivateNetwork;

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
	public boolean testEnvironment() {
		if (!inWorld()) {
			return false;
		}

		if (client.hasSingleplayerServer()) {
			return true;
		}

		// The address actually connected to, not the name typed in the server list.
		ClientPacketListener connection = client.getConnection();
		return connection != null && connection.getConnection().getRemoteAddress() instanceof InetSocketAddress address
				&& PrivateNetwork.isPrivate(address.getAddress());
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
		return (server == null ? "Multiplayer" : "Server " + server.ip) + (testEnvironment() ? " (your network)" : "");
	}
}
