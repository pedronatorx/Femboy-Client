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

	/**
	 * Test hook that can only restrict: when true, no connection counts as a test environment, so the in-game tests can
	 * show that sandbox modules stay off on a public server without needing one. It never enables anything.
	 */
	public static volatile boolean simulatePublicServer;

	/** The connection the cached answer belongs to; the address does not change during a connection. */
	private Object testedConnection;
	private boolean testResult;

	public ClientEnvironment(Minecraft client) {
		this.client = client;
	}

	@Override
	public boolean lowImpactMode() {
		return Performance.mode() == GlobalOptions.PerformanceMode.LOW_IMPACT;
	}

	@Override
	public boolean inWorld() {
		return client.level != null && client.player != null;
	}

	@Override
	public boolean testEnvironment() {
		return true;
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
