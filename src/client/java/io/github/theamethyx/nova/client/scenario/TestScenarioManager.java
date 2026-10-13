package io.github.theamethyx.nova.client.scenario;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import io.github.theamethyx.nova.client.fixture.FixtureRegistry;
import io.github.theamethyx.nova.client.world.WorldIdentity;
import io.github.theamethyx.nova.core.diag.DiagnosticLogger;

/**
 * Builds and removes {@link TestScenario}s in the singleplayer world. Building happens on the
 * integrated server thread; results are applied on the client thread.
 *
 * <p>Scenarios only run in singleplayer: they modify the world, which NOVA never does on a server.
 */
public final class TestScenarioManager {
	public enum State { NOT_BUILT, BUILDING, BUILT, FAILED }

	/** The latest build of a scenario in the current world. */
	public record Run(State state, String message, BlockPos origin, String dimension, String world, Instant time,
			TestScenario.Build build) {
	}

	private final Map<String, TestScenario> scenarios = new LinkedHashMap<>();
	private final Map<String, Run> runs = new LinkedHashMap<>();
	private final FixtureRegistry fixtures;
	private final DiagnosticLogger diagnostics;
	private final List<Consumer<String>> buildListeners = new ArrayList<>();

	public TestScenarioManager(FixtureRegistry fixtures, DiagnosticLogger diagnostics) {
		this.fixtures = fixtures;
		this.diagnostics = diagnostics;
	}

	public void register(TestScenario scenario) {
		scenarios.put(scenario.id(), scenario);
	}

	public List<TestScenario> scenarios() {
		return List.copyOf(scenarios.values());
	}

	public TestScenario scenario(String id) {
		return scenarios.get(id);
	}

	/** Called on the client thread with the scenario id after every successful build. */
	public void onBuilt(Consumer<String> listener) {
		buildListeners.add(listener);
	}

	/** The latest run of a scenario in the current world, or a NOT_BUILT placeholder. */
	public Run run(String id) {
		Run run = runs.get(key(id));
		return run != null ? run : new Run(State.NOT_BUILT, "Not built in this world.", null, null, null, null, null);
	}

	private static String key(String id) {
		return WorldIdentity.current() + "|" + id;
	}

	/** Why scenarios cannot run right now, or {@code null} if they can. */
	public String unavailableReason() {
		Minecraft client = Minecraft.getInstance();
		if (client.level == null || client.player == null) {
			return "Load a singleplayer world first.";
		}

		if (client.getSingleplayerServer() == null) {
			return "Scenarios modify the world, so they only run in singleplayer worlds.";
		}

		return null;
	}

	/**
	 * Builds a scenario a few blocks in front of the player, at the player's feet level.
	 *
	 * @return a future completed on the client thread with the run, or failed with a readable message
	 */
	public CompletableFuture<Run> build(String id) {
		return buildAt(id, null);
	}

	/** Builds with the scenario's minimum corner at {@code origin} (or in front of the player if null). */
	public CompletableFuture<Run> buildAt(String id, BlockPos origin) {
		CompletableFuture<Run> result = new CompletableFuture<>();
		TestScenario scenario = scenarios.get(id);
		String problem = scenario == null ? "Unknown scenario " + id : unavailableReason();
		if (problem != null) {
			result.completeExceptionally(new IllegalStateException(problem));
			return result;
		}

		Minecraft client = Minecraft.getInstance();
		LocalPlayer player = client.player;
		IntegratedServer server = client.getSingleplayerServer();
		ResourceKey<Level> dimension = player.level().dimension();
		String world = WorldIdentity.current();
		BlockPos start = origin != null ? origin : defaultOrigin(player, scenario);

		Run previous = runs.get(key(id));
		runs.put(key(id), new Run(State.BUILDING, "Building...", start, dimension.identifier().toString(), world, Instant.now(), null));
		diagnostics.info("Scenarios", "Building '" + scenario.name() + "' at " + start.toShortString());

		server.execute(() -> {
			try {
				ServerLevel level = server.getLevel(dimension);
				if (level == null) {
					throw new IllegalStateException("Dimension " + dimension.identifier() + " is not loaded on the server");
				}

				if (previous != null && previous.build() != null) {
					removeFromWorld(level, previous.build());
				}

				TestScenario.Build build = scenario.build(level, start);
				client.execute(() -> finishBuild(scenario, start, dimension, world, build, previous, result));
			} catch (RuntimeException e) {
				client.execute(() -> {
					runs.put(key(id), new Run(State.FAILED, e.getMessage(), start, dimension.identifier().toString(), world, Instant.now(), null));
					diagnostics.error("Scenarios", "Building '" + scenario.name() + "' failed", e);
					result.completeExceptionally(e);
				});
			}
		});

		return result;
	}

	private void finishBuild(TestScenario scenario, BlockPos origin, ResourceKey<Level> dimension, String world, TestScenario.Build build,
			Run previous, CompletableFuture<Run> result) {
		fixtures.removeScenario(world, scenario.id());
		for (TestScenario.EntityRecord entity : build.entities) {
			if (entity.fixture()) {
				fixtures.addEntity(new FixtureRegistry.EntityFixture(entity.uuid(), entity.label(), world, scenario.id()));
			}
		}

		for (TestScenario.ContainerRecord container : build.containers) {
			if (container.fixture()) {
				BlockPos pos = container.pos();
				fixtures.addContainer(new FixtureRegistry.ContainerFixture(pos.getX(), pos.getY(), pos.getZ(),
						dimension.identifier().toString(), container.label(), world, scenario.id()));
			}
		}

		Run run = new Run(State.BUILT, "Built at " + origin.toShortString() + (build.notes.isEmpty() ? "" : ". " + String.join(" ", build.notes)),
				origin, dimension.identifier().toString(), world, Instant.now(), build);
		runs.put(key(scenario.id()), run);
		diagnostics.info("Scenarios", "Built '" + scenario.name() + "': " + build.entities.size() + " entities, "
				+ build.containers.size() + " containers");
		buildListeners.forEach(listener -> listener.accept(scenario.id()));
		result.complete(run);
	}

	/** Removes a built scenario from the world and the fixture list. */
	public CompletableFuture<Void> remove(String id) {
		CompletableFuture<Void> result = new CompletableFuture<>();
		Run run = runs.get(key(id));
		String problem = unavailableReason();
		if (problem != null || run == null || run.build() == null) {
			result.completeExceptionally(new IllegalStateException(problem != null ? problem : "Nothing to remove."));
			return result;
		}

		Minecraft client = Minecraft.getInstance();
		IntegratedServer server = client.getSingleplayerServer();
		ResourceKey<Level> dimension = client.player.level().dimension();
		String world = run.world();

		server.execute(() -> {
			ServerLevel level = server.getLevel(dimension);
			if (level != null) {
				removeFromWorld(level, run.build());
			}

			client.execute(() -> {
				fixtures.removeScenario(world, id);
				runs.remove(key(id));
				diagnostics.info("Scenarios", "Removed '" + scenarios.get(id).name() + "'");
				result.complete(null);
			});
		});

		return result;
	}

	/** Server thread: removes entities the build spawned and clears its regions to air. */
	private static void removeFromWorld(ServerLevel level, TestScenario.Build build) {
		for (TestScenario.EntityRecord record : build.entities) {
			Entity entity = level.getEntity(record.uuid());
			if (entity != null) {
				entity.discard();
			}
		}

		for (BoundingBox box : build.regions) {
			for (BlockPos pos : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
				level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
			}
		}
	}

	private static BlockPos defaultOrigin(LocalPlayer player, TestScenario scenario) {
		Direction facing = player.getDirection();
		BlockPos feet = player.blockPosition();
		BlockPos size = scenario.footprint();
		// Start 3 blocks ahead, centered sideways, with the floor one block below the feet.
		BlockPos ahead = feet.relative(facing, 3).below();
		int halfX = size.getX() / 2;
		int halfZ = size.getZ() / 2;
		return switch (facing) {
			case NORTH -> ahead.offset(-halfX, 0, -(size.getZ() - 1));
			case SOUTH -> ahead.offset(-halfX, 0, 0);
			case WEST -> ahead.offset(-(size.getX() - 1), 0, -halfZ);
			default -> ahead.offset(0, 0, -halfZ);
		};
	}

	/** UUIDs of fixture entities for the current world, for quick lookups while rendering. */
	public static java.util.Set<UUID> fixtureUuids(FixtureRegistry registry, String world) {
		java.util.Set<UUID> set = new java.util.HashSet<>();
		registry.entities(world).forEach(fixture -> set.add(fixture.uuid()));
		return set;
	}
}
