package io.github.theamethyx.femboyclient.core.runner;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * Starts {@link RunnerWorker} in a separate Java process for one snippet and collects its output.
 *
 * <p>Everything blocking happens on background threads, so the caller (the game's render thread) only
 * reads the current state. Limits: a memory cap for the worker JVM, a wall-clock timeout, and a maximum
 * amount of output; hitting any of them kills the worker and its child processes.
 */
public final class JavaRunner {
	/** Where and how the worker runs. */
	public record Config(Path javaExecutable, List<Path> classpath, Path workDirectory, int timeoutSeconds, int memoryMb, int maxOutputBytes) {
	}

	public enum Status {
		STARTING("Starting"), COMPILING("Compiling"), RUNNING("Running"), FINISHED("Finished"), COMPILE_ERROR("Compile error"),
		FAILED("Exception"), EXITED("Exited"), TIMED_OUT("Timed out"), STOPPED("Stopped"), OUTPUT_LIMIT("Output limit reached"),
		UNAVAILABLE("Runner unavailable"), INTERNAL_ERROR("Runner error");

		private final String label;

		Status(String label) {
			this.label = label;
		}

		public String label() {
			return label;
		}

		public boolean active() {
			return this == STARTING || this == COMPILING || this == RUNNING;
		}
	}

	/** One line of output; {@code error} for stderr and runner messages. */
	public record Line(String text, boolean error) {
	}

	/** A single run. All methods are safe to call from any thread. */
	public static final class Execution {
		private final List<Line> lines = new ArrayList<>();
		private final AtomicLong outputBytes = new AtomicLong();
		private final long startedNanos = System.nanoTime();
		private volatile long endedNanos;
		private volatile Status status = Status.STARTING;
		private volatile Integer exitCode;
		private volatile Process process;
		private volatile Status stopReason;

		public Status status() {
			return status;
		}

		public Integer exitCode() {
			return exitCode;
		}

		public long durationMillis() {
			long end = endedNanos == 0 ? System.nanoTime() : endedNanos;
			return (end - startedNanos) / 1_000_000;
		}

		public List<Line> lines() {
			synchronized (lines) {
				return List.copyOf(lines);
			}
		}

		public String text() {
			return lines().stream().map(Line::text).collect(Collectors.joining("\n"));
		}

		/** Kills the worker if it is still running. */
		public void stop() {
			kill(Status.STOPPED);
		}

		/** Waits until the run has ended; for tests. */
		public boolean await(long millis) throws InterruptedException {
			long deadline = System.currentTimeMillis() + millis;
			while (status.active()) {
				if (System.currentTimeMillis() > deadline) {
					return false;
				}

				Thread.sleep(10);
			}

			return true;
		}

		/** PID of the worker process, or -1 before it started; for tests. */
		public long pid() {
			Process p = process;
			return p == null ? -1 : p.pid();
		}

		void kill(Status reason) {
			if (stopReason == null) {
				stopReason = reason;
			}

			Process p = process;
			if (p != null) {
				p.descendants().forEach(ProcessHandle::destroyForcibly);
				p.destroyForcibly();
			}
		}

		void add(String text, boolean error) {
			synchronized (lines) {
				lines.add(new Line(text, error));
			}
		}

		void finish(Status result) {
			endedNanos = System.nanoTime();
			status = result;
		}
	}

	private JavaRunner() {
	}

	/** Starts a run on a background thread and returns immediately. */
	public static Execution start(String source, Map<String, String> snapshot, Config config) {
		Execution execution = new Execution();
		Thread thread = new Thread(() -> runWorker(execution, source, snapshot, config), "Femboy Java Runner");
		thread.setDaemon(true);
		thread.start();
		return execution;
	}

	private static void runWorker(Execution execution, String source, Map<String, String> snapshot, Config config) {
		Process process;
		try {
			Files.createDirectories(config.workDirectory());
			String classpath = config.classpath().stream().map(Path::toString).collect(Collectors.joining(java.io.File.pathSeparator));
			ProcessBuilder builder = new ProcessBuilder(config.javaExecutable().toString(), "-Xmx" + config.memoryMb() + "m",
					"-XX:+UseSerialGC", "-XX:TieredStopAtLevel=1", "-Dfile.encoding=UTF-8", "-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8",
					"-cp", classpath, RunnerWorker.class.getName());
			builder.directory(config.workDirectory().toFile());
			process = builder.start();
		} catch (IOException | RuntimeException e) {
			execution.add("Could not start the runner: " + e.getMessage(), true);
			execution.add("Check 'Java for the runner' in the module settings (now: " + config.javaExecutable() + ").", true);
			execution.finish(Status.UNAVAILABLE);
			return;
		}

		execution.process = process;
		if (execution.stopReason != null) {
			execution.kill(execution.stopReason);
		}

		execution.status = Status.COMPILING;
		Thread out = pump(process.getInputStream(), false, execution, config);
		Thread err = pump(process.getErrorStream(), true, execution, config);
		try (OutputStream stdin = process.getOutputStream()) {
			stdin.write((RunnerWorker.encodeSnapshot(snapshot) + "\n" + RunnerWorker.SOURCE_MARKER + "\n" + source).getBytes(StandardCharsets.UTF_8));
		} catch (IOException e) {
			// The worker died before reading its input; the exit code below says why.
		}

		boolean finished;
		try {
			finished = process.waitFor(config.timeoutSeconds(), TimeUnit.SECONDS);
			if (!finished) {
				execution.kill(Status.TIMED_OUT);
				process.waitFor(5, TimeUnit.SECONDS);
			}

			out.join(2000);
			err.join(2000);
		} catch (InterruptedException e) {
			execution.kill(Status.STOPPED);
			Thread.currentThread().interrupt();
		}

		Status reason = execution.stopReason;
		if (reason != null) {
			execution.add(switch (reason) {
				case TIMED_OUT -> "Stopped after " + config.timeoutSeconds() + " s (time limit).";
				case OUTPUT_LIMIT -> "Stopped: more than " + config.maxOutputBytes() / 1024 + " KB of output (output limit).";
				default -> "Stopped.";
			}, true);
			execution.finish(reason);
			return;
		}

		int code = process.exitValue();
		execution.exitCode = code;
		execution.finish(switch (code) {
			case RunnerWorker.EXIT_OK -> Status.FINISHED;
			case RunnerWorker.EXIT_COMPILE_ERROR -> Status.COMPILE_ERROR;
			case RunnerWorker.EXIT_EXCEPTION -> Status.FAILED;
			case RunnerWorker.EXIT_NO_COMPILER -> Status.UNAVAILABLE;
			case RunnerWorker.EXIT_INTERNAL -> Status.INTERNAL_ERROR;
			default -> {
				execution.add("The program exited with code " + code + ".", true);
				yield Status.EXITED;
			}
		});
	}

	private static Thread pump(InputStream stream, boolean error, Execution execution, Config config) {
		Thread thread = new Thread(() -> {
			try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
				StringBuilder line = new StringBuilder();
				char[] buffer = new char[4096];
				int read;
				while ((read = reader.read(buffer)) >= 0) {
					if (execution.outputBytes.addAndGet(read) > config.maxOutputBytes()) {
						execution.kill(Status.OUTPUT_LIMIT);
						return;
					}

					for (int i = 0; i < read; i++) {
						char c = buffer[i];
						if (c == '\n') {
							emit(execution, line.toString(), error);
							line.setLength(0);
						} else if (c != '\r') {
							line.append(c);
						}
					}
				}

				if (!line.isEmpty()) {
					emit(execution, line.toString(), error);
				}
			} catch (IOException e) {
				// Stream closed because the process was killed.
			}
		}, "Femboy Java Runner output");
		thread.setDaemon(true);
		thread.start();
		return thread;
	}

	private static void emit(Execution execution, String text, boolean error) {
		if (error && text.equals(RunnerWorker.RUNNING_MARKER)) {
			if (execution.status == Status.COMPILING) {
				execution.status = Status.RUNNING;
			}

			return;
		}

		execution.add(text, error);
	}
}
