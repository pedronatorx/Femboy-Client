package io.github.theamethyx.nova.core.research;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import io.github.theamethyx.nova.core.config.JsonFiles;
import io.github.theamethyx.nova.core.diag.DiagnosticLogger;

/**
 * Known conditions of every scenario run (for example "applied 2000 random ticks per budding
 * block"), stored in {@code research/ground_truth.json}, separately from observations.
 *
 * <p>Observations carry only a run id. The research engine joins the two for <em>evaluation</em>;
 * classifiers receive observations only and never see these records.
 */
public final class GroundTruthManager {
	/** One run's known conditions. {@code conditions} is free-form, recorded by the scenario. */
	public record Record(String runId, String scenarioId, String world, String dimension, long createdAt, JsonObject conditions) {
	}

	private final Path file;
	private final DiagnosticLogger diagnostics;
	private final Map<String, Record> records = new LinkedHashMap<>();

	public GroundTruthManager(Path file, DiagnosticLogger diagnostics) {
		this.file = file;
		this.diagnostics = diagnostics;
	}

	public synchronized void put(Record record) {
		records.put(record.runId(), record);
		save();
	}

	public synchronized Record get(String runId) {
		return runId == null ? null : records.get(runId);
	}

	public synchronized List<Record> all() {
		return new ArrayList<>(records.values());
	}

	public synchronized void remove(String runId) {
		if (records.remove(runId) != null) {
			save();
		}
	}

	public synchronized void load() {
		records.clear();
		if (!Files.exists(file)) {
			return;
		}

		try {
			for (JsonElement element : JsonFiles.readObject(file).getAsJsonArray("runs")) {
				JsonObject o = element.getAsJsonObject();
				Record record = new Record(o.get("runId").getAsString(), o.get("scenarioId").getAsString(), o.get("world").getAsString(),
						o.get("dimension").getAsString(), o.get("createdAt").getAsLong(), o.getAsJsonObject("conditions"));
				records.put(record.runId(), record);
			}
		} catch (IOException | RuntimeException e) {
			records.clear();
			diagnostics.warn("Ground truth", "Ground-truth records could not be read (" + e.getMessage() + "). Existing scenario runs "
					+ "cannot be evaluated until they are rebuilt.");
		}
	}

	private void save() {
		JsonArray runs = new JsonArray();
		for (Record record : records.values()) {
			JsonObject o = new JsonObject();
			o.addProperty("runId", record.runId());
			o.addProperty("scenarioId", record.scenarioId());
			o.addProperty("world", record.world());
			o.addProperty("dimension", record.dimension());
			o.addProperty("createdAt", record.createdAt());
			o.add("conditions", record.conditions());
			runs.add(o);
		}

		JsonObject json = new JsonObject();
		json.addProperty("schemaVersion", 1);
		json.add("runs", runs);
		try {
			JsonFiles.writeAtomically(file, json);
		} catch (IOException e) {
			diagnostics.error("Ground truth", "Could not save ground-truth records", e);
		}
	}
}
