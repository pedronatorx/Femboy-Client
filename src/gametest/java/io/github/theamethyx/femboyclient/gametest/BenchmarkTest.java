package io.github.theamethyx.femboyclient.gametest;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.loader.api.FabricLoader;

import io.github.theamethyx.femboyclient.client.FemboyClient;
import io.github.theamethyx.femboyclient.core.diag.PerformanceMonitor;
import io.github.theamethyx.femboyclient.core.module.Module;
import io.github.theamethyx.femboyclient.core.setting.Setting;

/**
 * A fixed, repeatable load for measuring the client's own cost: 100 husks, 40 cows, 20 dropped items, 121 chests and
 * a layer of about 4,800 diamond ores around a player who turns slowly in place, with the visual and HUD modules on.
 *
 * <p>Measured with {@link PerformanceMonitor}: time and allocated bytes of every module call, and of the three places
 * the client calls into modules (tick, HUD, world rendering), which include the dispatch overhead itself. Frame times
 * are recorded too but depend on the GPU, the window and the test harness, so module time is the number to compare.
 *
 * <p>Run with {@code scripts/run-benchmark.sh}; the result is written to {@code build/run/clientGameTest/benchmark.txt}.
 */
@SuppressWarnings({"UnstableApiUsage", "unchecked"})
final class BenchmarkTest {
	/** Modules switched on for the "all on" rounds. Ids missing from a build are skipped and listed in the result. */
	static final List<String> MODULES = List.of("entity_esp", "storage_esp", "block_esp|ore_esp", "trackers", "entity_outlines", "hitboxes",
			"light_level", "chunk_borders", "player_finder", "trajectories", "block_highlight", "coordinates_hud", "performance_hud",
			"module_list_hud", "keystrokes", "movement_info", "combat_info", "target_hud", "health_armor", "effects", "item_counters",
			"explosion_info", "weapon_info", "player_alert", "waypoints", "block_counter", "elytra_info", "death_coordinates",
			// Added in 1.2.0; older builds report them as "not in this build".
			"minimap", "look_info", "cooldowns");
	static final int WARMUP_TICKS = 100;
	static final int ROUND_TICKS = 300;
	static final int ROUNDS = 3;

	private BenchmarkTest() {
	}

	static FemboyClient femboy() {
		return FemboyClient.get();
	}

	static void run(ClientGameTestContext context, TestReport report) {
		List<String> out = new ArrayList<>();
		out.add("Femboy Client benchmark");
		out.add("Build: " + FabricLoader.getInstance().getModContainer("femboyclient").map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("?"));
		out.add("Java: " + System.getProperty("java.version") + ", " + Runtime.getRuntime().availableProcessors() + " CPUs, max heap "
				+ Runtime.getRuntime().maxMemory() / (1024 * 1024) + " MB");

		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			singleplayer.getClientWorld().waitForChunksRender();
			var server = singleplayer.getServer();
			for (String command : List.of("gamemode creative @a", "gamerule doMobSpawning false", "gamerule doDaylightCycle false", "time set 6000",
					"difficulty easy", "tp @a 0.5 -60 0.5 0 10")) {
				server.runCommand(command);
			}

			// Ores under the floor: the flat world is bedrock at -64, dirt at -63 and -62, grass at -61.
			server.runCommand("fill -24 -63 -24 24 -62 24 minecraft:diamond_ore");
			for (int x = -20; x <= 20; x += 4) {
				for (int z = -44; z <= -4; z += 4) {
					server.runCommand("setblock " + x + " -60 " + z + " minecraft:chest");
				}
			}

			for (int x = -18; x <= 18; x += 4) {
				for (int z = 6; z <= 42; z += 4) {
					server.runCommand("summon minecraft:husk " + x + " -60 " + z + " {NoAI:1b,Silent:1b,PersistenceRequired:1b}");
				}
			}

			for (int i = 0; i < 40; i++) {
				server.runCommand("summon minecraft:cow " + (-30 + (i % 10) * 6) + " -60 " + (48 + (i / 10) * 4) + " {NoAI:1b,Silent:1b}");
			}

			for (int i = 0; i < 20; i++) {
				server.runCommand("summon minecraft:item " + (-19 + i * 2) + " -60 3 {Item:{id:\"minecraft:stone\",count:1},PickupDelay:32767,Age:-32768}");
			}

			context.waitTicks(40);
			singleplayer.getClientWorld().waitForChunksRender();

			List<String> missing = configure(context);
			out.add("Scene: 100 husks, 40 cows, 20 items, 121 chests, 4802 diamond ores; " + MODULES.size() + " modules listed"
					+ (missing.isEmpty() ? "" : ", not in this build: " + String.join(", ", missing)));
			out.add("Each round: " + ROUND_TICKS + " ticks while turning 1.5 degrees per tick; " + WARMUP_TICKS + " ticks of warm-up first.");
			out.add("");

			context.runOnClient(client -> femboy().performance().setMeasureAllocations(true));
			setAll(context, false);
			Round off = measure(context, "all modules off");
			out.addAll(off.summary());

			setAll(context, true);
			turn(context, WARMUP_TICKS);
			List<Round> rounds = new ArrayList<>();
			for (int i = 1; i <= ROUNDS; i++) {
				Round round = measure(context, "all on, round " + i);
				rounds.add(round);
				out.addAll(round.summary());
			}

			// The performance modes, where the build has them: the same scene, measured the same way.
			for (String mode : List.of("PERFORMANCE", "LOW_IMPACT")) {
				if (setMode(context, mode)) {
					turn(context, 40);
					out.addAll(measure(context, "mode " + mode.toLowerCase(Locale.ROOT)).summary());
				}
			}

			setMode(context, "BALANCED");
			out.add("");
			out.add("Per module, median of the " + ROUNDS + " rounds (time and allocation per game tick, all phases together):");
			out.add(String.format(Locale.ROOT, "  %-22s %10s %12s", "module", "us/tick", "KB/tick"));
			Map<String, double[]> perModule = new LinkedHashMap<>();
			for (Round round : rounds) {
				round.perModule().forEach((id, values) -> perModule.computeIfAbsent(id, k -> new double[ROUNDS * 2]));
			}

			for (Map.Entry<String, double[]> entry : perModule.entrySet()) {
				double[] micros = new double[ROUNDS];
				double[] kb = new double[ROUNDS];
				for (int i = 0; i < ROUNDS; i++) {
					double[] values = rounds.get(i).perModule().getOrDefault(entry.getKey(), new double[2]);
					micros[i] = values[0];
					kb[i] = values[1];
				}

				entry.setValue(new double[] {median(micros), median(kb)});
			}

			perModule.entrySet().stream().sorted((a, b) -> Double.compare(b.getValue()[0], a.getValue()[0]))
					.forEach(e -> out.add(String.format(Locale.ROOT, "  %-22s %10.1f %12.2f", e.getKey(), e.getValue()[0], e.getValue()[1])));

			context.runOnClient(client -> femboy().performance().setMeasureAllocations(false));
			setAll(context, false);
		}

		Path file = FabricLoader.getInstance().getGameDir().resolve("benchmark.txt");
		try {
			Files.write(file, out, StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new IllegalStateException("Could not write " + file, e);
		}

		out.forEach(report::note);
		report.check("Benchmark finished and wrote " + file.getFileName(), Files.exists(file));
	}

	/** Sets the performance mode option if this build has one; returns whether it did. */
	private static boolean setMode(ClientGameTestContext context, String mode) {
		return context.computeOnClient(client -> {
			Setting<Object> setting = (Setting<Object>) femboy().options().settings.get("performance_mode");
			if (setting == null) {
				return false;
			}

			for (Object constant : setting.defaultValue().getClass().getEnumConstants()) {
				if (((Enum<?>) constant).name().equals(mode)) {
					setting.set(constant);
					return true;
				}
			}

			return false;
		});
	}

	private static double median(double[] values) {
		double[] sorted = values.clone();
		java.util.Arrays.sort(sorted);
		return sorted[sorted.length / 2];
	}

	/** Settings that make the modules do their full work in this scene. Returns listed ids this build does not have. */
	private static List<String> configure(ClientGameTestContext context) {
		return context.computeOnClient(client -> {
			List<String> missing = new ArrayList<>();
			for (String spec : MODULES) {
				if (module(spec) == null) {
					missing.add(spec);
				}
			}

			Module esp = module("entity_esp");
			Set<String> types = new HashSet<>((Set<String>) esp.settings().get("types").get());
			types.addAll(List.of("minecraft:cow", "minecraft:item", "minecraft:husk"));
			set(esp, "types", types);
			set(esp, "health", true);
			set(esp, "tracers", true);
			set(esp, "equipment", true);
			set(esp, "max_labels", 64.0);
			set(module("storage_esp"), "labels", true);
			Module trackers = module("trackers");
			set(trackers, "hostile", true);
			set(trackers, "passive", true);
			set(trackers, "max_indicators", 32.0);
			return missing;
		});
	}

	private static void set(Module module, String id, Object value) {
		Setting<Object> setting = (Setting<Object>) module.settings().get(id);
		if (setting == null) {
			return; // renamed or removed in this build; the default applies
		}

		String problem = setting.set(value);
		if (problem != null) {
			throw new IllegalStateException(module.id() + "." + id + ": " + problem);
		}
	}

	/** Resolves "new_id|old_id" to whichever exists. */
	private static Module module(String spec) {
		for (String id : spec.split("\\|")) {
			Module module = femboy().modules().get(id);
			if (module != null) {
				return module;
			}
		}

		return null;
	}

	private static void setAll(ClientGameTestContext context, boolean on) {
		context.runOnClient(client -> {
			for (String spec : MODULES) {
				Module module = module(spec);
				if (module != null) {
					femboy().modules().setEnabled(module, on);
				}
			}
		});
		context.waitTicks(5);
	}

	private static void turn(ClientGameTestContext context, int ticks) {
		for (int i = 0; i < ticks; i++) {
			context.runOnClient(client -> client.player.setYRot(client.player.getYRot() + 1.5f));
			context.waitTick();
		}
	}

	private record Round(String name, double tickMs, double hudMs, double worldMs, double tickKb, double hudKb, double worldKb, long framesDrawn,
			double frameMs, double worstFrameMs, Map<String, double[]> perModule) {
		List<String> summary() {
			double perFrame = framesDrawn == 0 ? 0 : (hudMs + worldMs) * ROUND_TICKS / framesDrawn;
			return List.of(name + ":",
					String.format(Locale.ROOT, "  client cost per tick: modules tick %.3f ms (%.1f KB), HUD %.3f ms (%.1f KB), world %.3f ms (%.1f KB)",
							tickMs, tickKb, hudMs, hudKb, worldMs, worldKb),
					String.format(Locale.ROOT, "  %d frames in %d ticks; client drawing per frame %.3f ms; frame time average %.2f ms, worst %.2f ms "
							+ "(last 240 frames, GPU and harness dependent)", framesDrawn, ROUND_TICKS, perFrame, frameMs, worstFrameMs));
		}
	}

	private static Round measure(ClientGameTestContext context, String name) {
		context.runOnClient(client -> femboy().performance().reset());
		turn(context, ROUND_TICKS);
		return context.computeOnClient(client -> {
			PerformanceMonitor performance = femboy().performance();
			double[] tick = perTick(performance.timing("client/tick"));
			double[] hud = perTick(performance.timing("client/hud"));
			double[] world = perTick(performance.timing("client/world"));
			Map<String, double[]> perModule = new LinkedHashMap<>();
			for (PerformanceMonitor.Timing timing : performance.timings()) {
				String key = timing.key();
				if (key.startsWith("client/")) {
					continue;
				}

				String id = key.substring(0, key.indexOf('/'));
				double[] values = perModule.computeIfAbsent(id, k -> new double[2]);
				values[0] += timing.totalNanos() / 1e3 / ROUND_TICKS;
				values[1] += timing.totalBytes() / 1024.0 / ROUND_TICKS;
			}

			PerformanceMonitor.FrameStats frames = performance.frameStats();
			PerformanceMonitor.Timing hudTiming = performance.timing("client/hud");
			long drawn = hudTiming == null ? 0 : hudTiming.calls();
			return new Round(name, tick[0], hud[0], world[0], tick[1], hud[1], world[1], drawn, frames.averageFrameMs(), frames.worstFrameMs(), perModule);
		});
	}

	/** {milliseconds, kilobytes} per game tick for one framework timing. */
	private static double[] perTick(PerformanceMonitor.Timing timing) {
		if (timing == null) {
			return new double[2];
		}

		return new double[] {timing.totalNanos() / 1e6 / ROUND_TICKS, timing.totalBytes() / 1024.0 / ROUND_TICKS};
	}
}
