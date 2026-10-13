package io.github.theamethyx.nova.core.research;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import io.github.theamethyx.nova.core.config.JsonFiles;
import io.github.theamethyx.nova.core.diag.DiagnosticLogger;
import io.github.theamethyx.nova.core.research.geode.EvidenceQuality;
import io.github.theamethyx.nova.core.research.geode.GeodeObservation;

/**
 * Saved geode observations ({@code research/observations.json}) and a human-readable report for each
 * ({@code research/reports/<id>.md}). Evaluation reports from the research engine are stored in the
 * same reports folder.
 */
public final class TestResultStore {
	private static final DateTimeFormatter ID_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS");
	private static final DateTimeFormatter HUMAN_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

	/** A saved observation plus the context it was taken in (world, settings, game version). */
	public record Record(String id, long savedAt, GeodeObservation observation, Map<String, String> context) {
	}

	private final Path root;
	private final DiagnosticLogger diagnostics;
	private final Map<String, Record> records = new LinkedHashMap<>();

	public TestResultStore(Path root, DiagnosticLogger diagnostics) {
		this.root = root;
		this.diagnostics = diagnostics;
	}

	public Path reportsDir() {
		return root.resolve("reports");
	}

	public synchronized List<Record> all() {
		return new ArrayList<>(records.values());
	}

	public synchronized Record get(String id) {
		return records.get(id);
	}

	/** Saves an observation and writes its report. */
	public synchronized Record save(GeodeObservation observation, Map<String, String> context, int minimumSample) {
		String id = "obs-" + LocalDateTime.now().format(ID_TIME);
		Record record = new Record(id, System.currentTimeMillis(), observation, Map.copyOf(context));
		records.put(id, record);
		persist();
		writeReport(record, minimumSample);
		return record;
	}

	/** A stored research evaluation: Markdown report plus JSON summary in the reports folder. */
	public record Evaluation(String id, Path markdown, JsonObject summary) {
	}

	public synchronized Evaluation saveEvaluation(String title, String markdown, java.util.function.Function<String, JsonObject> summary) {
		String id = "evaluation-" + LocalDateTime.now().format(ID_TIME);
		try {
			Files.createDirectories(reportsDir());
			Files.writeString(reportsDir().resolve(id + ".md"), markdown, StandardCharsets.UTF_8);
			JsonObject json = summary.apply(id);
			JsonFiles.writeAtomically(reportsDir().resolve(id + ".json"), json);
			return new Evaluation(id, reportsDir().resolve(id + ".md"), json);
		} catch (IOException e) {
			diagnostics.error("Results", "Could not write evaluation " + title, e);
			return null;
		}
	}

	/** Stored evaluations, newest first. Unreadable summaries are skipped with a warning. */
	public synchronized List<Evaluation> evaluations() {
		List<Evaluation> list = new ArrayList<>();
		if (!Files.isDirectory(reportsDir())) {
			return list;
		}

		try (var files = Files.list(reportsDir())) {
			for (Path file : files.filter(f -> f.getFileName().toString().startsWith("evaluation-") && f.getFileName().toString().endsWith(".json")).toList()) {
				String id = file.getFileName().toString().replace(".json", "");
				try {
					list.add(new Evaluation(id, reportsDir().resolve(id + ".md"), JsonFiles.readObject(file)));
				} catch (IOException | RuntimeException e) {
					diagnostics.warn("Results", "Skipped unreadable evaluation summary " + file.getFileName() + ": " + e.getMessage());
				}
			}
		} catch (IOException e) {
			diagnostics.warn("Results", "Could not list evaluations: " + e.getMessage());
		}

		list.sort((a, b) -> b.id().compareTo(a.id()));
		return list;
	}

	public synchronized void delete(String id) {
		if (records.remove(id) != null) {
			persist();
			try {
				Files.deleteIfExists(reportPath(id));
			} catch (IOException e) {
				diagnostics.warn("Results", "Could not delete report " + id + ": " + e.getMessage());
			}
		}
	}

	public Path reportPath(String id) {
		return reportsDir().resolve(id + ".md");
	}

	// ----- persistence -----------------------------------------------------------------------

	public synchronized void load() {
		records.clear();
		Path file = root.resolve("observations.json");
		if (!Files.exists(file)) {
			return;
		}

		try {
			for (JsonElement element : JsonFiles.readObject(file).getAsJsonArray("records")) {
				JsonObject o = element.getAsJsonObject();
				Map<String, String> context = new LinkedHashMap<>();
				o.getAsJsonObject("context").entrySet().forEach(e -> context.put(e.getKey(), e.getValue().getAsString()));
				Record record = new Record(o.get("id").getAsString(), o.get("savedAt").getAsLong(), ObservationJson.read(o.getAsJsonObject("observation")), context);
				records.put(record.id(), record);
			}
		} catch (IOException | RuntimeException e) {
			records.clear();
			diagnostics.warn("Results", "Saved observations could not be read (" + e.getMessage() + "); the list starts empty. "
					+ "Reports already written are still in " + reportsDir());
		}
	}

	private void persist() {
		JsonArray array = new JsonArray();
		for (Record record : records.values()) {
			JsonObject o = new JsonObject();
			o.addProperty("id", record.id());
			o.addProperty("savedAt", record.savedAt());
			o.add("observation", ObservationJson.write(record.observation()));
			JsonObject context = new JsonObject();
			record.context().forEach(context::addProperty);
			o.add("context", context);
			array.add(o);
		}

		JsonObject json = new JsonObject();
		json.addProperty("schemaVersion", 1);
		json.add("records", array);
		try {
			JsonFiles.writeAtomically(root.resolve("observations.json"), json);
		} catch (IOException e) {
			diagnostics.error("Results", "Could not save observations", e);
		}
	}

	private void writeReport(Record record, int minimumSample) {
		try {
			Files.createDirectories(reportsDir());
			Files.writeString(reportPath(record.id()), report(record, minimumSample), StandardCharsets.UTF_8);
		} catch (IOException e) {
			diagnostics.error("Results", "Could not write report for " + record.id(), e);
		}
	}

	/** Markdown report of one observation: direct observations first, estimates clearly labelled. */
	public static String report(Record record, int minimumSample) {
		GeodeObservation o = record.observation();
		StageCounts c = o.counts();
		StringBuilder md = new StringBuilder();
		md.append("# Geode observation ").append(record.id()).append("\n\n");
		md.append("- Saved: ").append(HUMAN_TIME.format(Instant.ofEpochMilli(record.savedAt()))).append('\n');
		md.append("- Observed: ").append(HUMAN_TIME.format(Instant.ofEpochMilli(o.timestampMillis()))).append('\n');
		md.append("- Geode: `").append(o.id()).append("` in `").append(o.dimension()).append("`\n");
		md.append("- Bounds: ").append(o.minX()).append(", ").append(o.minY()).append(", ").append(o.minZ()).append(" to ")
				.append(o.maxX()).append(", ").append(o.maxY()).append(", ").append(o.maxZ()).append('\n');
		md.append("- Test scenario run: ").append(o.scenarioId() == null ? "none (not inside a registered fixture region)" : "`" + o.scenarioId() + "`").append('\n');
		record.context().forEach((key, value) -> md.append("- ").append(key).append(": ").append(value).append('\n'));

		md.append("\n## Direct observation (block states)\n\n");
		md.append("| Stage | Count | Share of all growth objects |\n|---|---:|---:|\n");
		for (GrowthStage stage : GrowthStage.values()) {
			Proportion share = c.share(stage);
			md.append("| ").append(stage.label()).append(" | ").append(c.get(stage)).append(" | ")
					.append(share.defined() ? String.format(Locale.ROOT, "%.1f%%", share.value() * 100) : "n/a").append(" |\n");
		}

		md.append("| **Total growth objects** | **").append(c.total()).append("** | 100% |\n\n");
		md.append("- Budding amethyst blocks: ").append(o.buddingBlocks()).append('\n');
		md.append("- Amethyst blocks: ").append(o.amethystBlocks()).append('\n');
		md.append("- Budding faces: ").append(o.faces().free()).append(" free, ").append(o.faces().ownGrowth()).append(" with growth, ")
				.append(o.faces().blocked()).append(" blocked, ").append(o.faces().unavailable()).append(" unreadable\n");
		md.append("- Unavailable positions near the geode: ").append(o.unavailablePositions()).append('\n');
		md.append("- Completeness: ").append(o.completenessNote()).append('\n');

		md.append("\n## Statistical estimates\n\n");
		md.append("Intervals describe sampling uncertainty of the observed fractions only. They are not probabilities about player activity.\n\n");
		append(md, "Clusters among all growth objects", c.clustersOfAll());
		append(md, "Clusters among large buds and clusters only", c.clustersOfLargeAndClusters());
		EvidenceQuality quality = EvidenceQuality.rate(o, minimumSample);
		md.append("\nEvidence quality (data rating, not a probability): **").append(quality.label()).append("** - ").append(quality.explanation())
				.append(" Minimum sample size: ").append(minimumSample).append(" growth objects.\n");
		return md.toString();
	}

	private static void append(StringBuilder md, String name, Proportion p) {
		double[] ci = p.wilson95();
		md.append("- ").append(name).append(": ").append(p.describe());
		if (p.defined()) {
			md.append(String.format(Locale.ROOT, ", 95%% Wilson interval %.1f%% to %.1f%%", ci[0] * 100, ci[1] * 100));
		}

		md.append('\n');
	}
}
