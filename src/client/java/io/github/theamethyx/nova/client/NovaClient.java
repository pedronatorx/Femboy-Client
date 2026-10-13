package io.github.theamethyx.nova.client;

import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class NovaClient implements ClientModInitializer {
	public static final String MOD_ID = "nova";
	public static final Logger LOGGER = LoggerFactory.getLogger("NOVA");

	@Override
	public void onInitializeClient() {
		LOGGER.info("NOVA client initialized");
	}
}
