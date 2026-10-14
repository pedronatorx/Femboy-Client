package io.github.theamethyx.femboyclient.core.research;

import java.util.List;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import io.github.theamethyx.femboyclient.core.research.geode.GeodeObservation;

/** JSON form of a {@link GeodeObservation}. Growth positions are not stored (they can be large). */
public final class ObservationJson {
	private ObservationJson() {
	}

	public static JsonObject write(GeodeObservation o) {
		JsonObject json = new JsonObject();
		json.addProperty("id", o.id());
		json.addProperty("dimension", o.dimension());
		json.addProperty("minX", o.minX());
		json.addProperty("minY", o.minY());
		json.addProperty("minZ", o.minZ());
		json.addProperty("maxX", o.maxX());
		json.addProperty("maxY", o.maxY());
		json.addProperty("maxZ", o.maxZ());
		JsonObject counts = new JsonObject();
		counts.addProperty("small", o.counts().small());
		counts.addProperty("medium", o.counts().medium());
		counts.addProperty("large", o.counts().large());
		counts.addProperty("cluster", o.counts().cluster());
		json.add("counts", counts);
		json.addProperty("buddingBlocks", o.buddingBlocks());
		json.addProperty("amethystBlocks", o.amethystBlocks());
		JsonObject faces = new JsonObject();
		faces.addProperty("free", o.faces().free());
		faces.addProperty("ownGrowth", o.faces().ownGrowth());
		faces.addProperty("blocked", o.faces().blocked());
		faces.addProperty("unavailable", o.faces().unavailable());
		json.add("faces", faces);
		json.addProperty("unavailablePositions", o.unavailablePositions());
		json.addProperty("complete", o.complete());
		json.addProperty("completenessNote", o.completenessNote());
		json.addProperty("timestampMillis", o.timestampMillis());
		if (o.scenarioId() != null) {
			json.addProperty("scenarioId", o.scenarioId());
		}

		json.addProperty("source", o.source());
		return json;
	}

	public static GeodeObservation read(JsonObject json) {
		JsonObject c = json.getAsJsonObject("counts");
		JsonObject f = json.getAsJsonObject("faces");
		JsonElement scenario = json.get("scenarioId");
		return new GeodeObservation(json.get("id").getAsString(), json.get("dimension").getAsString(),
				json.get("minX").getAsInt(), json.get("minY").getAsInt(), json.get("minZ").getAsInt(),
				json.get("maxX").getAsInt(), json.get("maxY").getAsInt(), json.get("maxZ").getAsInt(),
				new StageCounts(c.get("small").getAsInt(), c.get("medium").getAsInt(), c.get("large").getAsInt(), c.get("cluster").getAsInt()),
				json.get("buddingBlocks").getAsInt(), json.get("amethystBlocks").getAsInt(),
				new GeodeObservation.FaceSummary(f.get("free").getAsInt(), f.get("ownGrowth").getAsInt(), f.get("blocked").getAsInt(), f.get("unavailable").getAsInt()),
				json.get("unavailablePositions").getAsInt(), json.get("complete").getAsBoolean(), json.get("completenessNote").getAsString(),
				json.get("timestampMillis").getAsLong(), scenario == null || scenario.isJsonNull() ? null : scenario.getAsString(),
				json.get("source").getAsString(), List.of());
	}
}
