package io.github.theamethyx.femboyclient.client.scenario;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.google.gson.JsonObject;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * A reproducible test setup built into a singleplayer world. {@link #build} runs on the integrated
 * server thread and reports everything it created, so the client can register fixtures and the
 * ground truth can be stored separately from any later observation.
 */
public interface TestScenario {
	String id();

	String name();

	/** What the scenario builds and what it is for, shown in the Test Scenarios page. */
	String description();

	/** Size of the area the scenario occupies, measured from its origin (minimum corner). */
	BlockPos footprint();

	/** Builds the scenario with its minimum corner at {@code origin}. Server thread only. */
	Build build(ServerLevel level, BlockPos origin);

	/** Everything a build created. */
	final class Build {
		public final List<EntityRecord> entities = new ArrayList<>();
		public final List<ContainerRecord> containers = new ArrayList<>();
		/** Regions cleared to air when the scenario is removed. */
		public final List<BoundingBox> regions = new ArrayList<>();
		/** Regions registered as test fixtures (e.g. a geode), so observations inside can be linked to this run. */
		public final List<RegionRecord> fixtureRegions = new ArrayList<>();
		/** Known conditions of the build (stored by the ground-truth store, never shown to classifiers). */
		public final JsonObject groundTruth = new JsonObject();
		public final List<String> notes = new ArrayList<>();
	}

	record EntityRecord(UUID uuid, String label, boolean fixture) {
	}

	record ContainerRecord(BlockPos pos, String label, boolean fixture) {
	}

	record RegionRecord(BoundingBox box, String label) {
	}
}
