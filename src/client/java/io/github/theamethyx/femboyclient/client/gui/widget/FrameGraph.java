package io.github.theamethyx.femboyclient.client.gui.widget;

import io.github.theamethyx.femboyclient.client.gui.Theme;
import io.github.theamethyx.femboyclient.client.gui.UiContext;
import io.github.theamethyx.femboyclient.core.diag.PerformanceMonitor;

/** Bar graph of recent frame times, newest right; lines mark 16.7 ms (60 FPS) and 33.3 ms (30 FPS). */
public class FrameGraph extends Widget {
	private final PerformanceMonitor performance;

	public FrameGraph(PerformanceMonitor performance) {
		this.performance = performance;
	}

	@Override
	public int preferredHeight(UiContext ui, int width) {
		return 60;
	}

	@Override
	public void render(UiContext ui) {
		ui.roundRect(x, y, width, height, Theme.SURFACE);
		ui.text("Frame times (newest right)", x + 6, y + 5, Theme.TEXT_SECONDARY);
		long[] frames = performance.frameHistory();
		int graphX = x + 6, graphY = y + 16, graphW = width - 12, graphH = height - 21;
		ui.rect(graphX, graphY, graphW, graphH, 0x30000000);
		double scaleMs = 50;
		int sixty = graphY + graphH - (int) Math.round(graphH * 16.7 / scaleMs);
		int thirty = graphY + graphH - (int) Math.round(graphH * 33.3 / scaleMs);
		ui.rect(graphX, sixty, graphW, 1, Theme.withAlpha(Theme.SUCCESS, 0.5f));
		ui.rect(graphX, thirty, graphW, 1, Theme.withAlpha(Theme.WARNING, 0.4f));

		int bars = Math.min(frames.length, graphW);
		for (int i = 0; i < bars; i++) {
			double ms = frames[frames.length - bars + i] / 1e6;
			int barHeight = (int) Math.min(graphH, Math.round(graphH * ms / scaleMs));
			int color = ms > 33.4 ? Theme.ERROR : ms > 16.7 ? Theme.WARNING : Theme.ACCENT;
			ui.rect(graphX + graphW - bars + i, graphY + graphH - barHeight, 1, barHeight, color);
		}

		if (frames.length == 0) {
			ui.textCentered("No frames yet: load a world.", x + width / 2, graphY + graphH / 2 - 4, Theme.TEXT_MUTED);
		}

		if (ui.hovered(x, y, width, height)) {
			ui.tooltip(this, "Each bar is one rendered frame; full height is 50 ms. Green line: 16.7 ms (60 FPS), amber line: 33.3 ms (30 FPS). "
					+ "Purple bars are faster than 60 FPS, amber faster than 30, red slower. Only frames with a world loaded are sampled.");
		}
	}
}
