package io.github.theamethyx.femboyclient.world.oresim;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import com.google.gson.JsonObject;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

import net.fabricmc.loader.api.FabricLoader;

import io.github.theamethyx.femboyclient.core.config.JsonFiles;

/**
 * The server side of the ore simulation, for test servers you run: {@code /femboyclient oresim compare [radius]}
 * simulates the chunks around you from the server's own seed and compares the predictions with the real chunks the
 * server has loaded, then reports how many were right. Nothing about the seed or positions is sent to players; only the
 * totals are shown to the operator who ran it (and, with {@code export}, written to a CSV file on the server).
 *
 * <p>Two locks keep it to servers whose operator wants it: the command needs operator permission, and it does nothing
 * until {@code ore_simulation_command} is set to {@code true} in {@code config/femboyclient-server.json}.
 */
public final class OreSimulationCommand {
	public static final String CONFIG_FILE = "femboyclient-server.json";
	public static final String CONFIG_KEY = "ore_simulation_command";
	private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(runnable -> {
		Thread thread = new Thread(runnable, "Femboy Client ore simulation (server)");
		thread.setDaemon(true);
		thread.setPriority(Thread.MIN_PRIORITY);
		return thread;
	});
	private static final AtomicBoolean RUNNING = new AtomicBoolean();

	private OreSimulationCommand() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("femboyclient").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(Commands.literal("oresim")
						.then(Commands.literal("compare")
								.executes(context -> compare(context, 2, false))
								.then(Commands.argument("radius", IntegerArgumentType.integer(1, 4))
										.executes(context -> compare(context, IntegerArgumentType.getInteger(context, "radius"), false))
										.then(Commands.literal("export")
												.executes(context -> compare(context, IntegerArgumentType.getInteger(context, "radius"), true)))))));
	}

	/** Whether the server's operator turned the command on. */
	public static boolean enabled() {
		Path file = FabricLoader.getInstance().getConfigDir().resolve(CONFIG_FILE);
		try {
			if (!Files.exists(file)) {
				JsonObject defaults = new JsonObject();
				defaults.addProperty("_comment", "Set ore_simulation_command to true to allow operators to compare the ore simulation with this world.");
				defaults.addProperty(CONFIG_KEY, false);
				JsonFiles.writeAtomically(file, defaults);
				return false;
			}

			JsonObject json = JsonFiles.readObject(file);
			return json.has(CONFIG_KEY) && json.get(CONFIG_KEY).getAsBoolean();
		} catch (Exception e) {
			return false;
		}
	}

	private static int compare(CommandContext<CommandSourceStack> context, int radius, boolean export) {
		CommandSourceStack source = context.getSource();
		if (!enabled()) {
			source.sendFailure(Component.literal("The ore simulation command is off. Set \"" + CONFIG_KEY + "\" to true in config/" + CONFIG_FILE
					+ " on this server to allow it."));
			return 0;
		}

		ServerLevel level = source.getLevel();
		if (!OreSimulator.supports(level)) {
			source.sendFailure(Component.literal(OreSimulator.UNSUPPORTED_GENERATOR));
			return 0;
		}

		if (!RUNNING.compareAndSet(false, true)) {
			source.sendFailure(Component.literal("An ore simulation is already running; wait for it to finish."));
			return 0;
		}

		ChunkPos center = new ChunkPos(net.minecraft.core.BlockPos.containing(source.getPosition()));
		Set<OreKind> kinds = EnumSet.allOf(OreKind.class);
		MinecraftServer server = source.getServer();
		source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT, "Simulating ores in %d×%d chunks around chunk %d %d...", 2 * radius + 1,
				2 * radius + 1, center.x, center.z)), false);
		CompletableFuture.supplyAsync(() -> OreSimulator.simulate(level, center, radius, kinds, OreSimulator.Progress.NONE), WORKER)
				.whenCompleteAsync((result, error) -> {
					RUNNING.set(false);
					if (error != null) {
						Throwable cause = error.getCause() != null ? error.getCause() : error;
						source.sendFailure(Component.literal("The ore simulation failed: " + cause.getMessage()));
						return;
					}

					OreComparison.Outcome outcome = OreComparison.compare(result, kinds, (x, z) -> level.getChunkSource().getChunkNow(x, z));
					source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT, "Ore simulation (%d ms): %s", result.millis(),
							outcome.summary().describe())), false);
					if (export) {
						export(source, server, result, outcome.checks());
					}
				}, server);
		return 1;
	}

	private static void export(CommandSourceStack source, MinecraftServer server, OreSimulator.Result result, Map<net.minecraft.core.BlockPos, OreComparison.Check> checks) {
		Path file = server.getServerDirectory().resolve("femboyclient").resolve("oresim-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".csv");
		try {
			OreComparison.exportCsv(result, checks, file);
			source.sendSuccess(() -> Component.literal("Written to " + file.toAbsolutePath()), false);
		} catch (Exception e) {
			source.sendFailure(Component.literal("Could not write the CSV file: " + e.getMessage()));
		}
	}
}
