package io.github.theamethyx.femboyclient.client.diag;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;

import io.github.theamethyx.femboyclient.core.diag.PerformanceMonitor;
import io.github.theamethyx.femboyclient.core.module.Module;
import io.github.theamethyx.femboyclient.core.module.ModuleManager;

/**
 * Compares the frame time and the client's own drawing and tick cost with a module on and off, under the conditions of
 * the moment: it measures the module's current state for a few seconds, switches it, waits briefly, measures again, and
 * switches it back. Stand still and keep the view steady for a fair comparison; the result says how many frames each half
 * had. Only visual and assist modules can be profiled, because switching an automation module on would make it act.
 */
public final class ModuleProfiler {
	private static final long SETTLE_NANOS = 1_500_000_000L;
	private static final String[] CLIENT_KEYS = {"client/tick", "client/hud", "client/world"};

	/**
	 * One comparison. Frame times in milliseconds, client cost in microseconds per frame (tick work counted per frame of
	 * its half), frames per half.
	 */
	public record Result(String module, int seconds, double frameMsOn, double frameMsOff, double clientUsOn, double clientUsOff, long framesOn,
			long framesOff, long finishedAt) {
		public double frameDeltaMs() {
			return frameMsOn - frameMsOff;
		}

		public double clientDeltaUs() {
			return clientUsOn - clientUsOff;
		}
	}

	private enum Phase { IDLE, SETTLE_FIRST, FIRST, SETTLE_SECOND, SECOND }

	private final ModuleManager modules;
	private final PerformanceMonitor performance;
	private final List<Result> results = new ArrayList<>();
	private Phase phase = Phase.IDLE;
	private Module module;
	private boolean originalState;
	private int seconds;
	private long phaseStart;
	private long[] framesAtStart;
	private long clientAtStart;
	private double[] first;
	private String lastProblem;

	public ModuleProfiler(ModuleManager modules, PerformanceMonitor performance) {
		this.modules = modules;
		this.performance = performance;
	}

	/** Whether a module may be profiled; {@code null} if yes, otherwise why not. */
	public String check(Module candidate) {
		if (Minecraft.getInstance().level == null) {
			return "Load a world first.";
		}

		if (candidate.tier() == Module.Tier.AUTOMATION || candidate.tier() == Module.Tier.SANDBOX) {
			return "Only visual and assist modules can be profiled: switching " + candidate.name() + " on would make it act.";
		}

		Module.Availability availability = modules.availability(candidate);
		if (!availability.available()) {
			return candidate.name() + " cannot run here: " + availability.reason();
		}

		return null;
	}

	/** Starts a comparison of {@code seconds} per half. @return {@code null} on success, otherwise why not */
	public String start(Module candidate, int seconds) {
		if (running()) {
			return "A comparison is already running.";
		}

		String problem = check(candidate);
		if (problem != null) {
			return problem;
		}

		this.module = candidate;
		this.seconds = Math.clamp(seconds, 2, 60);
		this.originalState = candidate.isEnabled();
		this.lastProblem = null;
		enter(Phase.SETTLE_FIRST);
		return null;
	}

	public boolean running() {
		return phase != Phase.IDLE;
	}

	/** Progress from 0 to 1 while running. */
	public float progress() {
		if (!running()) {
			return 0;
		}

		double total = 2 * (SETTLE_NANOS + seconds * 1e9);
		double done = (System.nanoTime() - phaseStart) + switch (phase) {
			case SETTLE_FIRST, IDLE -> 0;
			case FIRST -> SETTLE_NANOS;
			case SETTLE_SECOND -> SETTLE_NANOS + seconds * 1e9;
			case SECOND -> 2 * SETTLE_NANOS + seconds * 1e9;
		};
		return (float) Math.min(1, done / total);
	}

	public String status() {
		if (!running()) {
			return lastProblem;
		}

		boolean on = module.isEnabled();
		return switch (phase) {
			case SETTLE_FIRST, SETTLE_SECOND -> "Settling with " + module.name() + (on ? " on" : " off") + "...";
			case FIRST, SECOND -> "Measuring with " + module.name() + (on ? " on" : " off") + "...";
			case IDLE -> null;
		};
	}

	/** Comparisons made this session, newest first. */
	public List<Result> results() {
		return List.copyOf(results.reversed());
	}

	/** Stops a running comparison and puts the module back as it was. */
	public void cancel(String reason) {
		if (running()) {
			modules.setEnabled(module, originalState);
			phase = Phase.IDLE;
			lastProblem = reason;
		}
	}

	/** Call once per client tick. */
	public void tick() {
		if (!running()) {
			return;
		}

		if (Minecraft.getInstance().level == null) {
			cancel("Stopped: the world was closed.");
			return;
		}

		if (module.lastError() != null && module.isEnabled() && !module.isRunning()) {
			cancel("Stopped: " + module.name() + " reported an error.");
			return;
		}

		long elapsed = System.nanoTime() - phaseStart;
		switch (phase) {
			case SETTLE_FIRST -> {
				if (elapsed >= SETTLE_NANOS) enter(Phase.FIRST);
			}
			case FIRST -> {
				if (elapsed >= seconds * 1_000_000_000L) {
					first = measure();
					modules.setEnabled(module, !originalState);
					enter(Phase.SETTLE_SECOND);
				}
			}
			case SETTLE_SECOND -> {
				if (elapsed >= SETTLE_NANOS) enter(Phase.SECOND);
			}
			case SECOND -> {
				if (elapsed >= seconds * 1_000_000_000L) {
					double[] second = measure();
					modules.setEnabled(module, originalState);
					phase = Phase.IDLE;
					double[] on = originalState ? first : second;
					double[] off = originalState ? second : first;
					results.add(new Result(module.name(), seconds, on[0], off[0], on[1], off[1], (long) on[2], (long) off[2], System.currentTimeMillis()));
					while (results.size() > 10) {
						results.removeFirst();
					}
				}
			}
			case IDLE -> {
			}
		}
	}

	private void enter(Phase next) {
		phase = next;
		phaseStart = System.nanoTime();
		framesAtStart = performance.frameTotals();
		clientAtStart = clientNanos();
	}

	/** {average frame ms, client µs per frame, frames} since the phase started. */
	private double[] measure() {
		long[] now = performance.frameTotals();
		long frames = Math.max(1, now[0] - framesAtStart[0]);
		double frameMs = (now[1] - framesAtStart[1]) / 1e6 / frames;
		double clientUs = (clientNanos() - clientAtStart) / 1e3 / frames;
		return new double[] {frameMs, clientUs, now[0] - framesAtStart[0]};
	}

	private long clientNanos() {
		long total = 0;
		for (String key : CLIENT_KEYS) {
			total += performance.totalNanos(key);
		}

		return total;
	}
}
