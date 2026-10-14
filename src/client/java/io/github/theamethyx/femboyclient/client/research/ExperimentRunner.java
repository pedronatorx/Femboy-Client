package io.github.theamethyx.femboyclient.client.research;

import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import io.github.theamethyx.femboyclient.core.diag.DiagnosticLogger;
import io.github.theamethyx.femboyclient.core.research.TestResultStore;
import io.github.theamethyx.femboyclient.core.research.engine.ActivityResearchEngine;
import io.github.theamethyx.femboyclient.core.research.engine.EvaluationReportWriter;
import io.github.theamethyx.femboyclient.world.ExperimentGenerator;
import io.github.theamethyx.femboyclient.world.GeodeFixtureBuilder;

/**
 * Runs the (quick) research experiment inside the singleplayer world: a stone cube 48 blocks above
 * the player hosts each geode in turn; one step runs per server tick within a time budget, so the
 * game keeps running. The cube is cleared to air when the run ends or is cancelled.
 */
public final class ExperimentRunner {
	public enum State { IDLE, RUNNING, EVALUATING, DONE, CANCELLED, FAILED }

	private static final long BUDGET_NANOS = 15_000_000L;
	private static final int HEIGHT_ABOVE_PLAYER = 48;

	private final TestResultStore results;
	private final DiagnosticLogger diagnostics;
	private volatile State state = State.IDLE;
	private volatile String message = "Not run yet.";
	private volatile float progress;
	private volatile boolean cancelRequested;
	private volatile String lastEvaluationId;
	private ExperimentGenerator generator;
	private ResourceKey<Level> dimension;
	private BlockPos center;

	public ExperimentRunner(TestResultStore results, DiagnosticLogger diagnostics) {
		this.results = results;
		this.diagnostics = diagnostics;
		ServerTickEvents.END_SERVER_TICK.register(this::onServerTick);
	}

	/** @return null if started, otherwise why not */
	public String start() {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null || client.getSingleplayerServer() == null) {
			return "Experiments change the world, so they only run in singleplayer.";
		}

		if (state == State.RUNNING || state == State.EVALUATING) {
			return "An experiment is already running.";
		}

		center = client.player.blockPosition().above(HEIGHT_ABOVE_PLAYER);
		if (center.getY() + GeodeFixtureBuilder.HALF >= client.player.level().getMaxY()) {
			return "Not enough room above you; move lower and try again.";
		}

		dimension = client.player.level().dimension();
		generator = new ExperimentGenerator(ExperimentGenerator.Config.quick(), center);
		cancelRequested = false;
		progress = 0;
		state = State.RUNNING;
		message = "Starting...";
		diagnostics.info("Research", "Experiment started 48 blocks above " + client.player.blockPosition().toShortString());
		return null;
	}

	public void cancel() {
		if (state == State.RUNNING) {
			cancelRequested = true;
		}
	}

	/** Server thread (integrated server only). */
	private void onServerTick(MinecraftServer server) {
		if (state != State.RUNNING || !(server instanceof IntegratedServer)) {
			return;
		}

		ServerLevel level = server.getLevel(dimension);
		if (level == null) {
			finish(server, State.FAILED, "The experiment's dimension is no longer loaded.");
			return;
		}

		if (cancelRequested) {
			clearArea(level);
			finish(server, State.CANCELLED, "Cancelled; the test area was cleared.");
			return;
		}

		try {
			long deadline = System.nanoTime() + BUDGET_NANOS;
			do {
				generator.step(level);
			} while (!generator.finished() && System.nanoTime() < deadline);
		} catch (RuntimeException e) {
			clearArea(level);
			diagnostics.error("Research", "Experiment failed", e);
			finish(server, State.FAILED, "Failed: " + e.getMessage());
			return;
		}

		progress = generator.progress();
		message = generator.status();
		if (generator.finished()) {
			clearArea(level);
			state = State.EVALUATING;
			message = "Evaluating...";
			var dataset = generator.dataset();
			Minecraft.getInstance().execute(() -> evaluate(dataset));
		}
	}

	private void evaluate(io.github.theamethyx.femboyclient.core.research.engine.Dataset dataset) {
		try {
			ActivityResearchEngine.Report report = new ActivityResearchEngine().evaluate(dataset, ActivityResearchEngine.Config.defaults());
			String title = "Amethyst growth evaluation (in-game quick run)";
			TestResultStore.Evaluation evaluation = results.saveEvaluation(title, EvaluationReportWriter.markdown(report, title),
					id -> EvaluationReportWriter.summary(report, id, title, System.currentTimeMillis()));
			lastEvaluationId = evaluation == null ? null : evaluation.id();
			state = State.DONE;
			message = "Done: " + dataset.size() + " observations evaluated" + (evaluation == null ? " (saving failed, see Diagnostics)." : ".");
			diagnostics.info("Research", message);
		} catch (RuntimeException e) {
			state = State.FAILED;
			message = "Evaluation failed: " + e.getMessage();
			diagnostics.error("Research", "Evaluation failed", e);
		}
	}

	private void clearArea(ServerLevel level) {
		BoundingBox box = GeodeFixtureBuilder.clearedBox(new BlockPos(Math.floorDiv(center.getX(), 16) * 16, center.getY(), center.getZ()));
		for (BlockPos pos : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
			level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
		}
	}

	private void finish(MinecraftServer server, State end, String text) {
		state = end;
		message = text;
		diagnostics.info("Research", text);
	}

	public State state() {
		return state;
	}

	public String message() {
		return message;
	}

	public float progress() {
		return progress;
	}

	public String lastEvaluationId() {
		return lastEvaluationId;
	}
}
