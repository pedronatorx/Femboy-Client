package io.github.theamethyx.femboyclient.core.diag;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Collects diagnostic events in memory for the in-game Diagnostics panel and mirrors them to the
 * game log. Safe to call from any thread.
 */
public final class DiagnosticLogger {
	public static final int CAPACITY = 500;
	private static final Logger LOG = LoggerFactory.getLogger("FemboyClient");

	private final Deque<Entry> entries = new ArrayDeque<>();
	private final List<Consumer<Entry>> listeners = new CopyOnWriteArrayList<>();
	private int warningCount;
	private int errorCount;

	public void info(String source, String message) {
		record(Level.INFO, source, message, null);
	}

	public void warn(String source, String message) {
		record(Level.WARN, source, message, null);
	}

	public void error(String source, String message, Throwable cause) {
		record(Level.ERROR, source, message, cause);
	}

	private void record(Level level, String source, String message, Throwable cause) {
		String detail = cause == null ? null : cause.getClass().getSimpleName() + ": " + cause.getMessage();
		Entry entry = new Entry(Instant.now(), level, source, message, detail);

		synchronized (entries) {
			if (entries.size() == CAPACITY) {
				entries.removeFirst();
			}

			entries.addLast(entry);
			if (level == Level.WARN) warningCount++;
			if (level == Level.ERROR) errorCount++;
		}

		switch (level) {
			case INFO -> LOG.info("[{}] {}", source, message);
			case WARN -> LOG.warn("[{}] {}", source, message);
			case ERROR -> LOG.error("[{}] {}", source, message, cause);
		}

		listeners.forEach(listener -> listener.accept(entry));
	}

	/** Newest entries last. */
	public List<Entry> snapshot() {
		synchronized (entries) {
			return new ArrayList<>(entries);
		}
	}

	public int warningCount() {
		synchronized (entries) {
			return warningCount;
		}
	}

	public int errorCount() {
		synchronized (entries) {
			return errorCount;
		}
	}

	public void clear() {
		synchronized (entries) {
			entries.clear();
			warningCount = 0;
			errorCount = 0;
		}
	}

	public void addListener(Consumer<Entry> listener) {
		listeners.add(listener);
	}

	public enum Level {
		INFO, WARN, ERROR
	}

	/** {@code detail} is the exception summary for errors, otherwise {@code null}. */
	public record Entry(Instant time, Level level, String source, String message, String detail) {
	}
}
