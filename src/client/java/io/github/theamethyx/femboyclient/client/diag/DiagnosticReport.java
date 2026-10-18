package io.github.theamethyx.femboyclient.client.diag;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;

import net.fabricmc.loader.api.FabricLoader;

import io.github.theamethyx.femboyclient.client.FemboyClient;
import io.github.theamethyx.femboyclient.client.Performance;
import io.github.theamethyx.femboyclient.core.diag.DiagnosticLogger;
import io.github.theamethyx.femboyclient.core.diag.PerformanceMonitor;
import io.github.theamethyx.femboyclient.core.module.Module;
import io.github.theamethyx.femboyclient.core.module.ModuleHealth;

/**
 * A plain-text report for development and debugging: versions, the performance mode, frame statistics, memory, the cost
 * of every measured call, the health of every enabled module, profiler results and the recent log. It contains no server
 * address, chat or player names; the environment is described only as singleplayer, private network or public server.
 */
public final class DiagnosticReport {
	private DiagnosticReport() {
	}

	/** Builds the text on the calling (client) thread. */
	public static String build() {
		FemboyClient femboy = FemboyClient.get();
		StringBuilder out = new StringBuilder();
		String version = FabricLoader.getInstance().getModContainer(FemboyClient.MOD_ID).map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("?");
		out.append("Femboy Client diagnostic report\n");
		out.append("Created: ").append(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)).append('\n');
		out.append("Femboy Client ").append(version).append(", Minecraft ").append(SharedConstants.getCurrentVersion().name())
				.append(", Java ").append(Runtime.version()).append(", ").append(System.getProperty("os.name")).append('\n');
		var environment = femboy.modules().environment();
		out.append("Environment: ").append(!environment.inWorld() ? "no world" : environment.testEnvironment() ? "singleplayer or private network"
				: "public server").append('\n');
		out.append("Performance mode: ").append(Performance.mode().label()).append(" (").append(Performance.summary()).append(")\n");
		out.append("Profile: ").append(femboy.config().activeProfile()).append("\n\n");

		PerformanceMonitor.FrameStats stats = femboy.performance().frameStats();
		out.append("== Frames ==\n");
		out.append(String.format(Locale.ROOT, "FPS %d, average %.2f ms, slowest %.2f ms, 1%% low %.0f FPS, %d samples%n", Minecraft.getInstance().getFps(),
				stats.averageFrameMs(), stats.worstFrameMs(), stats.onePercentLowFps(), stats.samples()));
		Runtime runtime = Runtime.getRuntime();
		long used = runtime.totalMemory() - runtime.freeMemory();
		out.append(String.format(Locale.ROOT, "Memory: %d MB used of %d MB (max %d MB)%n%n", used >> 20, runtime.totalMemory() >> 20, runtime.maxMemory() >> 20));

		out.append("== Measured calls (average µs, max µs, calls) ==\n");
		for (PerformanceMonitor.Timing timing : femboy.performance().timings()) {
			out.append(String.format(Locale.ROOT, "%-40s %9.1f %9.0f %9d%n", timing.key(), timing.averageMicros(), timing.maxMicros(), timing.calls()));
		}

		out.append("\n== Enabled modules ==\n");
		for (Module module : femboy.modules().all()) {
			if (!module.isEnabled() && module.lastError() == null) {
				continue;
			}

			ModuleHealth health = femboy.modules().health(module);
			out.append(module.id()).append(": ").append(health.state().label());
			if (!health.reason().isEmpty()) out.append(" - ").append(health.reason());
			if (health.workload() != null) out.append(" [").append(health.workload()).append(']');
			if (health.totalErrors() > 0) out.append(" errors: ").append(health.totalErrors());
			out.append('\n');
		}

		List<ModuleProfiler.Result> results = femboy.profiler().results();
		if (!results.isEmpty()) {
			out.append("\n== On/off comparisons ==\n");
			for (ModuleProfiler.Result result : results) {
				out.append(String.format(Locale.ROOT, "%s (%d s each): frame %.2f ms on / %.2f ms off, client %.0f µs on / %.0f µs off, frames %d / %d%n",
						result.module(), result.seconds(), result.frameMsOn(), result.frameMsOff(), result.clientUsOn(), result.clientUsOff(),
						result.framesOn(), result.framesOff()));
			}
		}

		out.append("\n== Configuration problems at the last load ==\n");
		List<String> problems = femboy.config().lastReport();
		out.append(problems.isEmpty() ? "none\n" : String.join("\n", problems) + "\n");

		out.append("\n== Recent log ==\n");
		List<DiagnosticLogger.Entry> entries = femboy.diagnostics().snapshot();
		for (int i = Math.max(0, entries.size() - 200); i < entries.size(); i++) {
			DiagnosticLogger.Entry entry = entries.get(i);
			out.append(entry.time()).append(' ').append(entry.level()).append(' ').append(entry.source()).append(": ").append(entry.message());
			if (entry.detail() != null) out.append(" (").append(entry.detail()).append(')');
			out.append('\n');
		}

		return out.toString();
	}

	/** Builds the report now and writes it to {@code reports/} on a background thread; {@code done} gets the file or an error. */
	public static void export(Consumer<String> done) {
		String text = build();
		Path file = FemboyClient.get().config().root().resolve("reports")
				.resolve("report-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".txt");
		CompletableFuture.runAsync(() -> {
			try {
				Files.createDirectories(file.getParent());
				Files.writeString(file, text, StandardCharsets.UTF_8);
				Minecraft.getInstance().execute(() -> done.accept("Saved " + file.getFileName() + " in the reports folder."));
			} catch (IOException e) {
				Minecraft.getInstance().execute(() -> done.accept("Could not save the report: " + e.getMessage()));
			}
		});
	}
}
