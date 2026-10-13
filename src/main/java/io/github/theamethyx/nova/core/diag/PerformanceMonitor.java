package io.github.theamethyx.nova.core.diag;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Measures how long each module spends in each lifecycle phase and keeps a short history of
 * frame times. Timings use {@link System#nanoTime()} and are only as precise as that clock.
 */
public final class PerformanceMonitor {
	/** Number of frames kept for the frame-time graph and the FPS statistics. */
	public static final int FRAME_HISTORY = 240;

	private final Map<String, Timing> timings = new ConcurrentHashMap<>();
	private final long[] frameNanos = new long[FRAME_HISTORY];
	private int frameCursor;
	private int frameCount;
	private long lastFrameStart = -1;

	/** Records the duration of one call. {@code key} is usually {@code "<module>/<phase>"}. */
	public void record(String key, long nanos) {
		timings.computeIfAbsent(key, Timing::new).add(nanos);
	}

	/** Called once at the start of every rendered frame. */
	public synchronized void markFrame(long nowNanos) {
		if (lastFrameStart >= 0) {
			frameNanos[frameCursor] = nowNanos - lastFrameStart;
			frameCursor = (frameCursor + 1) % FRAME_HISTORY;
			frameCount = Math.min(frameCount + 1, FRAME_HISTORY);
		}

		lastFrameStart = nowNanos;
	}

	/** Frame durations in nanoseconds, oldest first. */
	public synchronized long[] frameHistory() {
		long[] result = new long[frameCount];
		int start = frameCount < FRAME_HISTORY ? 0 : frameCursor;
		for (int i = 0; i < frameCount; i++) {
			result[i] = frameNanos[(start + i) % FRAME_HISTORY];
		}

		return result;
	}

	public FrameStats frameStats() {
		long[] history = frameHistory();
		if (history.length == 0) {
			return new FrameStats(0, 0, 0, 0);
		}

		long[] sorted = history.clone();
		java.util.Arrays.sort(sorted);
		double total = 0;
		for (long nanos : history) {
			total += nanos;
		}

		double avgMs = total / history.length / 1e6;
		double worstMs = sorted[sorted.length - 1] / 1e6;
		// The "1% low" FPS uses the slowest 1% of frames (at least one frame).
		int slowCount = Math.max(1, sorted.length / 100);
		double slowTotal = 0;
		for (int i = sorted.length - slowCount; i < sorted.length; i++) {
			slowTotal += sorted[i];
		}

		double onePercentLowFps = 1e9 / (slowTotal / slowCount);
		return new FrameStats(history.length, avgMs, worstMs, onePercentLowFps);
	}

	/** All timings, most expensive (by average) first. */
	public List<Timing> timings() {
		List<Timing> list = new ArrayList<>(timings.values());
		list.sort(Comparator.comparingDouble(Timing::averageMicros).reversed());
		return list;
	}

	public Timing timing(String key) {
		return timings.get(key);
	}

	public void reset() {
		timings.clear();
		synchronized (this) {
			frameCount = 0;
			frameCursor = 0;
			lastFrameStart = -1;
		}
	}

	public record FrameStats(int samples, double averageFrameMs, double worstFrameMs, double onePercentLowFps) {
	}

	/** Rolling statistics for one measured operation. */
	public static final class Timing {
		private static final double SMOOTHING = 0.05;
		private final String key;
		private long calls;
		private long lastNanos;
		private long maxNanos;
		private double averageNanos;

		Timing(String key) {
			this.key = key;
		}

		synchronized void add(long nanos) {
			calls++;
			lastNanos = nanos;
			maxNanos = Math.max(maxNanos, nanos);
			averageNanos = calls == 1 ? nanos : averageNanos + SMOOTHING * (nanos - averageNanos);
		}

		public String key() {
			return key;
		}

		public synchronized long calls() {
			return calls;
		}

		public synchronized double lastMicros() {
			return lastNanos / 1e3;
		}

		/** Exponential moving average, weighted towards recent calls. */
		public synchronized double averageMicros() {
			return averageNanos / 1e3;
		}

		public synchronized double maxMicros() {
			return maxNanos / 1e3;
		}
	}
}
