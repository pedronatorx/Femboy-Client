package io.github.theamethyx.femboyclient;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

import io.github.theamethyx.femboyclient.world.oresim.OreSimulationCommand;

/**
 * The part of Femboy Client that also runs on servers: only the operator command that compares the ore simulation with
 * the server's world (off until the server's config enables it). Everything else is client-side.
 */
public final class FemboyCommon implements ModInitializer {
	@Override
	public void onInitialize() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registries, environment) -> OreSimulationCommand.register(dispatcher));
	}
}
