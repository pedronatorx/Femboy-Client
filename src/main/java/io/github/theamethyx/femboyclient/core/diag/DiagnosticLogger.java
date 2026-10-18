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
	/** Window in which an identical message (same level, source and text) is counted instead of logged again. */
	static final long REPEAT_WINDOW_MILLIS = 10_000;
	private String lastKey;
	private long lastAt;
	private int repeats;
	private Entry lastRepeated;

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
		long now = System.currentTimeMillis();
		String key = level + "|" + source + "|" + message;
		Entry summary = null;

		synchronized (entries) {
			// The same message again and again (e.g. a failing save retried every few seconds) is counted, not repeated:
			// the log stays readable and the first occurrence keeps its stack trace.
			if (key.equals(lastKey) && now - lastAt < REPEAT_WINDOW_MILLIS) {
				repeats++;
				lastAt = now;
				return;
			}

			if (repeats > 0) {
				summary = new Entry(Instant.now(), Level.INFO, lastRepeated.source(), "The previous message repeated " + repeats + " more time(s).", null);
				addEntry(summary);
			}

			lastKey = key;
			lastAt = now;
			repeats = 0;
			lastRepeated = entry;

			addEntry(entry);
			if (level == Level.WARN) warningCount++;
			if (level == Level.ERROR) errorCount++;
		}

		if (summary != null) {
			LOG.info("[{}] {}", summary.source(), summary.message());
		}

		switch (level) {
			case INFO -> LOG.info("[{}] {}", source, message);
			case WARN -> LOG.warn("[{}] {}", source, message);
			case ERROR -> LOG.error("[{}] {}", source, message, cause);
		}

		listeners.forEach(listener -> listener.accept(entry));
	}

	private void addEntry(Entry entry) {
		if (entries.size() == CAPACITY) {
			entries.removeFirst();
		}

		entries.addLast(entry);
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
			lastKey = null;
			repeats = 0;
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
