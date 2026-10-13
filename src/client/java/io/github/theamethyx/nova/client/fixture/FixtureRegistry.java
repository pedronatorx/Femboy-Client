package io.github.theamethyx.nova.client.fixture;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import io.github.theamethyx.nova.core.config.JsonFiles;
import io.github.theamethyx.nova.core.diag.DiagnosticLogger;

/**
 * The explicit list of test fixtures NOVA created: entities by UUID and containers by position, each
 * tagged with the world and scenario that created it. Visualization modules highlight only what is
 * listed here, never arbitrary entities or containers.
 *
 * <p>Stored in {@code config/nova/fixtures.json}. Client thread only.
 */
public final class FixtureRegistry {
	public record EntityFixture(UUID uuid, String label, String world, String scenario) {
	}

	public record ContainerFixture(int x, int y, int z, String dimension, String label, String world, String scenario) {
	}

	private final Path file;
	private final DiagnosticLogger diagnostics;
	private final List<EntityFixture> entities = new ArrayList<>();
	private final List<ContainerFixture> containers = new ArrayList<>();

	public FixtureRegistry(Path file, DiagnosticLogger diagnostics) {
		this.file = file;
		this.diagnostics = diagnostics;
	}

	public List<EntityFixture> entities(String world) {
		return entities.stream().filter(fixture -> fixture.world().equals(world)).toList();
	}

	public List<ContainerFixture> containers(String world, String dimension) {
		return containers.stream().filter(fixture -> fixture.world().equals(world) && fixture.dimension().equals(dimension)).toList();
	}

	public EntityFixture entity(String world, UUID uuid) {
		for (EntityFixture fixture : entities) {
			if (fixture.uuid().equals(uuid) && fixture.world().equals(world)) {
				return fixture;
			}
		}

		return null;
	}

	public void addEntity(EntityFixture fixture) {
		entities.add(fixture);
		save();
	}

	public void addContainer(ContainerFixture fixture) {
		containers.add(fixture);
		save();
	}

	/** Removes every fixture a scenario created in a world. */
	public void removeScenario(String world, String scenario) {
		entities.removeIf(fixture -> fixture.world().equals(world) && fixture.scenario().equals(scenario));
		containers.removeIf(fixture -> fixture.world().equals(world) && fixture.scenario().equals(scenario));
		save();
	}

	public int count(String world) {
		return (int) (entities.stream().filter(f -> f.world().equals(world)).count() + containers.stream().filter(f -> f.world().equals(world)).count());
	}

	public void load() {
		entities.clear();
		containers.clear();
		if (!Files.exists(file)) {
			return;
		}

		try {
			JsonObject json = JsonFiles.readObject(file);
			for (JsonElement element : json.getAsJsonArray("entities")) {
				JsonObject o = element.getAsJsonObject();
				entities.add(new EntityFixture(UUID.fromString(o.get("uuid").getAsString()), o.get("label").getAsString(),
						o.get("world").getAsString(), o.get("scenario").getAsString()));
			}

			for (JsonElement element : json.getAsJsonArray("containers")) {
				JsonObject o = element.getAsJsonObject();
				containers.add(new ContainerFixture(o.get("x").getAsInt(), o.get("y").getAsInt(), o.get("z").getAsInt(),
						o.get("dimension").getAsString(), o.get("label").getAsString(), o.get("world").getAsString(), o.get("scenario").getAsString()));
			}
		} catch (IOException | RuntimeException e) {
			entities.clear();
			containers.clear();
			diagnostics.warn("Fixtures", "The fixture list could not be read (" + e.getMessage() + "); starting with an empty list. "
					+ "Rebuild fixtures from the Test Scenarios page.");
		}
	}

	private void save() {
		JsonObject json = new JsonObject();
		JsonArray entityArray = new JsonArray();
		for (EntityFixture fixture : entities) {
			JsonObject o = new JsonObject();
			o.addProperty("uuid", fixture.uuid().toString());
			o.addProperty("label", fixture.label());
			o.addProperty("world", fixture.world());
			o.addProperty("scenario", fixture.scenario());
			entityArray.add(o);
		}

		JsonArray containerArray = new JsonArray();
		for (ContainerFixture fixture : containers) {
			JsonObject o = new JsonObject();
			o.addProperty("x", fixture.x());
			o.addProperty("y", fixture.y());
			o.addProperty("z", fixture.z());
			o.addProperty("dimension", fixture.dimension());
			o.addProperty("label", fixture.label());
			o.addProperty("world", fixture.world());
			o.addProperty("scenario", fixture.scenario());
			containerArray.add(o);
		}

		json.add("entities", entityArray);
		json.add("containers", containerArray);

		try {
			JsonFiles.writeAtomically(file, json);
		} catch (IOException e) {
			diagnostics.error("Fixtures", "Could not save the fixture list", e);
		}
	}
}
