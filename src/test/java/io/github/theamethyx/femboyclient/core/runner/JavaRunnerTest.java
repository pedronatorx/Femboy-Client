package io.github.theamethyx.femboyclient.core.runner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Runs real worker processes with the test JVM's Java and classpath, the same way the client starts them.
 */
class JavaRunnerTest {
	@TempDir
	Path work;

	private JavaRunner.Config config(int timeoutSeconds, int maxOutputBytes) {
		Path java = Path.of(System.getProperty("java.home"), "bin", "java");
		// Like the client: only the mod's own classes, no Minecraft. (The test classpath itself contains Minecraft.)
		List<Path> classpath = List.of(Path.of(System.getProperty("femboyclient.projectDir"), "build", "classes", "java", "main"));
		return new JavaRunner.Config(java, classpath, work, timeoutSeconds, 128, maxOutputBytes);
	}

	private JavaRunner.Execution run(String source, int timeoutSeconds) throws InterruptedException {
		Map<String, String> snapshot = new LinkedHashMap<>();
		snapshot.put("client.version", "0.4.0-test");
		snapshot.put("module.entity_esp.name", "Entity ESP");
		snapshot.put("module.entity_esp.enabled", "true");
		snapshot.put("module.entity_esp.running", "false");
		snapshot.put("setting.entity_esp.max_distance", "64 blocks");
		snapshot.put("diag.0", "line one\nwith = sign");
		JavaRunner.Execution execution = JavaRunner.start(source, snapshot, config(timeoutSeconds, 64 * 1024));
		assertTrue(execution.await((timeoutSeconds + 20) * 1000L), "run did not end");
		return execution;
	}

	@Test
	void plainStatementsRunAndPrint() throws Exception {
		JavaRunner.Execution run = run("int sum = 0;\nfor (int i = 1; i <= 10; i++) sum += i;\nSystem.out.println(\"sum=\" + sum);", 30);
		assertEquals(JavaRunner.Status.FINISHED, run.status(), run.text());
		assertEquals("sum=55", run.text());
		assertEquals(0, run.exitCode());
	}

	@Test
	void fullClassWithMainAndOwnImports() throws Exception {
		JavaRunner.Execution run = run("import java.time.Duration;\npublic class Hello {\n  public static void main(String[] args) {\n"
				+ "    System.out.println(Duration.ofSeconds(90).toMinutes());\n  }\n}", 30);
		assertEquals(JavaRunner.Status.FINISHED, run.status(), run.text());
		assertEquals("1", run.text());
	}

	@Test
	void snapshotIsReadableThroughTheApi() throws Exception {
		JavaRunner.Execution run = run("System.out.println(Femboy.clientVersion());\nSystem.out.println(Femboy.modules());\n"
				+ "System.out.println(Femboy.setting(\"entity_esp\", \"max_distance\"));\nSystem.out.println(Femboy.diagnostics().get(0));", 30);
		assertEquals(JavaRunner.Status.FINISHED, run.status(), run.text());
		assertEquals("0.4.0-test\n[entity_esp]\n64 blocks\nline one\nwith = sign", run.text());
	}

	@Test
	void compileErrorsReportTheLineTheUserTyped() throws Exception {
		JavaRunner.Execution run = run("import java.time.Instant;\n\nint ok = 1;\nint broken = \"text\";", 30);
		assertEquals(JavaRunner.Status.COMPILE_ERROR, run.status(), run.text());
		assertTrue(run.text().startsWith("Line 4: "), run.text());
		assertTrue(run.lines().stream().allMatch(JavaRunner.Line::error));
	}

	@Test
	void runtimeExceptionsAreReportedWithLines() throws Exception {
		JavaRunner.Execution run = run("System.out.println(\"before\");\nObject o = null;\no.toString();", 30);
		assertEquals(JavaRunner.Status.FAILED, run.status(), run.text());
		assertEquals("before", run.lines().getFirst().text());
		assertTrue(run.text().contains("NullPointerException"), run.text());
		assertTrue(run.text().contains("(line 3)"), run.text());
	}

	@Test
	void minecraftIsNotAvailableToSnippets() throws Exception {
		JavaRunner.Execution run = run("System.out.println(net.minecraft.client.Minecraft.getInstance());", 30);
		assertEquals(JavaRunner.Status.COMPILE_ERROR, run.status(), run.text());
		assertTrue(run.text().contains("Line 1: "), run.text());
	}

	@Test
	void endlessLoopsAreKilledAtTheTimeLimit() throws Exception {
		long started = System.currentTimeMillis();
		JavaRunner.Execution run = run("while (true) { Thread.onSpinWait(); }", 3);
		assertEquals(JavaRunner.Status.TIMED_OUT, run.status(), run.text());
		assertTrue(System.currentTimeMillis() - started < 15_000);
		assertTrue(run.text().contains("time limit"));
		assertFalse(ProcessHandle.of(run.pid()).map(ProcessHandle::isAlive).orElse(false), "worker still alive");
	}

	@Test
	void stopKillsTheWorker() throws Exception {
		JavaRunner.Execution run = JavaRunner.start("System.out.println(\"started\");\nThread.sleep(60_000);", Map.of(), config(120, 64 * 1024));
		long deadline = System.currentTimeMillis() + 30_000;
		while (run.lines().isEmpty() && System.currentTimeMillis() < deadline) {
			Thread.sleep(20);
		}

		assertEquals(JavaRunner.Status.RUNNING, run.status());
		run.stop();
		assertTrue(run.await(10_000));
		assertEquals(JavaRunner.Status.STOPPED, run.status());
		assertFalse(ProcessHandle.of(run.pid()).map(ProcessHandle::isAlive).orElse(false), "worker still alive");
	}

	@Test
	void excessiveOutputIsCutOff() throws Exception {
		JavaRunner.Execution run = JavaRunner.start("while (true) System.out.println(\"spam spam spam spam spam\");", Map.of(), config(60, 16 * 1024));
		assertTrue(run.await(60_000));
		assertEquals(JavaRunner.Status.OUTPUT_LIMIT, run.status());
		int kept = run.lines().stream().mapToInt(l -> l.text().length() + 1).sum();
		assertTrue(kept <= 17 * 1024, "kept " + kept + " bytes");
	}

	@Test
	void missingJavaIsReportedAsUnavailable() throws Exception {
		JavaRunner.Config broken = new JavaRunner.Config(work.resolve("no-such-java"), List.of(), work, 5, 64, 1024);
		JavaRunner.Execution run = JavaRunner.start("System.out.println(1);", Map.of(), broken);
		assertTrue(run.await(10_000));
		assertEquals(JavaRunner.Status.UNAVAILABLE, run.status());
		assertTrue(run.text().contains("Could not start the runner"));
	}

	@Test
	void systemExitIsReported() throws Exception {
		JavaRunner.Execution run = run("System.exit(7);", 30);
		assertEquals(JavaRunner.Status.EXITED, run.status(), run.text());
		assertEquals(7, run.exitCode());
	}

	@Test
	void riskyApisAreFound() {
		assertEquals(List.of(), RiskScanner.scan("System.out.println(\"Files.delete is just text\" + 1);\n// Files.delete(p);"));
		assertEquals(List.of("deletes files"), RiskScanner.scan("java.nio.file.Files.deleteIfExists(Path.of(\"x\"));"));
		assertEquals(List.of("starts or controls other programs", "uses the network"),
				RiskScanner.scan("new ProcessBuilder(\"ls\").start(); var s = new java.net.Socket(\"h\", 1);"));
		assertEquals(List.of("writes, moves or creates files", "exits the runner process"),
				RiskScanner.scan("try (var w = new FileWriter(\"a\")) {}\nSystem.exit(0);"));
	}

	@Test
	void wrappingMapsLines() {
		SnippetSource.Prepared prepared = SnippetSource.prepare("import java.time.*;\n\nSystem.out.println(1);");
		assertTrue(prepared.wrapped());
		String[] lines = prepared.javaSource().split("\n");
		for (int i = 0; i < lines.length; i++) {
			if (lines[i].equals("System.out.println(1);")) {
				assertEquals(3, prepared.userLine(i + 1));
			}
		}

		assertFalse(SnippetSource.prepare("class A { static void run() {} }").wrapped());
	}
}
