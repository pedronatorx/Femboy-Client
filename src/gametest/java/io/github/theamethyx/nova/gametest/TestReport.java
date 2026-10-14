package io.github.theamethyx.nova.gametest;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Collects named checks from a client game test and writes them to
 * {@code <run dir>/nova-test-report.txt}. A failed check fails the test immediately, after the
 * report (including the failure) has been written.
 */
@SuppressWarnings("UnstableApiUsage")
final class TestReport {
	private final ClientGameTestContext context;
	private final List<String> lines = new ArrayList<>();
	private final long started = System.currentTimeMillis();
	private int passed;

	TestReport(ClientGameTestContext context) {
		this.context = context;
		lines.add("NOVA client game test report");
		lines.add("Started: " + LocalDateTime.now());
		lines.add("");
	}

	void check(String name, boolean ok) {
		check(name, ok, "");
	}

	void check(String name, boolean ok, String detail) {
		lines.add((ok ? "PASS  " : "FAIL  ") + name + (detail.isEmpty() ? "" : "  -- " + detail));
		if (ok) {
			passed++;
			return;
		}

		write("FAILED");
		throw new AssertionError(name + (detail.isEmpty() ? "" : ": " + detail));
	}

	void note(String text) {
		lines.add("NOTE  " + text);
		write("IN PROGRESS");
	}

	void finish() {
		write("PASSED");
	}

	private void write(String verdict) {
		List<String> out = new ArrayList<>(lines);
		out.add("");
		out.add("Result: " + verdict + " (" + passed + " checks passed, " + (System.currentTimeMillis() - started) / 1000 + " s)");
		Path path = FabricLoader.getInstance().getGameDir().resolve("nova-test-report.txt");
		try {
			Files.write(path, out, StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new AssertionError("Could not write test report to " + path, e);
		}
	}
}
