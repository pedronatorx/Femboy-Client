package io.github.theamethyx.femboyclient.client.gui.widget;

import java.util.List;
import java.util.Locale;

import net.minecraft.client.Minecraft;

import io.github.theamethyx.femboyclient.client.gui.Icon;
import io.github.theamethyx.femboyclient.client.gui.Theme;
import io.github.theamethyx.femboyclient.client.gui.UiContext;
import io.github.theamethyx.femboyclient.client.modules.JavaRunnerModule;
import io.github.theamethyx.femboyclient.core.runner.JavaRunner;

/**
 * Java Runner's panel: toolbar, status, code editor, confirmation for risky code, and the console.
 * Only reads the run's state each frame; starting, waiting and reading output happen on other threads.
 */
public final class RunnerPanel extends Container {
	private static final int EDITOR_HEIGHT = 150;
	private static final int CONSOLE_HEIGHT = 120;
	private static final int LINE = 10;
	/** Shown in the closed examples menu; not one of the choices. */
	private static final JavaRunnerModule.Example EXAMPLES_LABEL = new JavaRunnerModule.Example("Examples...", "");

	private final JavaRunnerModule module;
	private final Button run;
	private final Button stop;
	private final Button clear;
	private final Button copy;
	private final Dropdown<JavaRunnerModule.Example> examples;
	private final CodeEditor editor;
	private final Button confirm;
	private final Button cancel;
	private int confirmBoxHeight;
	private int consoleScroll;
	private boolean followOutput = true;

	public RunnerPanel(JavaRunnerModule module) {
		this.module = module;
		run = add(new Button("Run", Button.Style.PRIMARY, () -> module.run()).icon(Icon.PLAY));
		run.tooltip("Compile and run the code in a separate Java process. Nothing runs until you press this.");
		stop = add(new Button("Stop", Button.Style.DANGER, module::stop).icon(Icon.STOP));
		stop.tooltip("Kill the running program.");
		clear = add(new Button("Clear", Button.Style.GHOST, module::clearOutput));
		clear.tooltip("Clear the output.");
		copy = add(new Button("Copy", Button.Style.SECONDARY, this::copyOutput));
		copy.tooltip("Copy the output to the clipboard.");
		examples = add(new Dropdown<>(() -> JavaRunnerModule.EXAMPLES, () -> EXAMPLES_LABEL, example -> {
			if (example != EXAMPLES_LABEL) {
				editorText(example.code());
				module.setSource(example.code());
			}
		}, JavaRunnerModule.Example::title));
		examples.tooltip("Replace the code with an example.");
		editor = add(new CodeEditor(EDITOR_HEIGHT));
		editor.setText(module.source());
		editor.onChange(module::setSource);
		confirm = add(new Button("Run anyway", Button.Style.DANGER, module::confirmRun));
		cancel = add(new Button("Cancel", Button.Style.SECONDARY, module::cancelRun));
	}

	public CodeEditor editor() {
		return editor;
	}

	private void editorText(String text) {
		editor.setText(text);
	}

	private void copyOutput() {
		JavaRunner.Execution execution = module.execution();
		if (execution != null) {
			Minecraft.getInstance().keyboardHandler.setClipboard(execution.text());
		}
	}

	private boolean confirming() {
		return !module.pendingRisks().isEmpty();
	}

	private int confirmHeight(UiContext ui, int width) {
		return confirming() ? 40 + ui.wrappedHeight(riskText(), width - 12) : 0;
	}

	private String riskText() {
		return "This code " + String.join(", ", module.pendingRisks()) + ". It runs with your normal permissions outside Minecraft, "
				+ "so it can really do that. Run it only if you trust it.";
	}

	@Override
	public int preferredHeight(UiContext ui, int width) {
		return 16 + 14 + EDITOR_HEIGHT + 4 + confirmHeight(ui, width) + CONSOLE_HEIGHT + 4 + ui.wrappedHeight(note(), width - 4) + 4;
	}

	private static String note() {
		return "Runs in a separate Java process with the limits set above, never inside the game. Your code can read a snapshot of the "
				+ "client through Femboy.* (versions, fps, module settings, diagnostics) but cannot touch your world or other players. "
				+ "Write plain statements or a class with main().";
	}

	@Override
	protected void onLayout() {
		int bx = x;
		run.setBounds(bx, y, 46, 14);
		stop.setBounds(bx += 50, y, 48, 14);
		clear.setBounds(bx += 52, y, 40, 14);
		copy.setBounds(bx += 44, y, 40, 14);
		examples.setBounds(x + width - 92, y, 92, 14);
		int cy = y + 30;
		confirm.setBounds(x + 6, cy + confirmBoxHeight - 20, 70, 14);
		cancel.setBounds(x + 80, cy + confirmBoxHeight - 20, 54, 14);
		editor.setBounds(x, y + 30 + confirmBoxHeight, width, EDITOR_HEIGHT);
	}

	@Override
	public void render(UiContext ui) {
		confirmBoxHeight = confirmHeight(ui, width);
		onLayout();
		JavaRunner.Execution execution = module.execution();
		boolean active = execution != null && execution.status().active();

		run.render(ui);
		if (active) {
			stop.render(ui);
		}

		clear.render(ui);
		copy.render(ui);

		// Status line.
		String status;
		int statusColor;
		if (!module.isRunning()) {
			status = "Turn Java Runner on (the switch above) to run code.";
			statusColor = Theme.WARNING;
		} else if (execution == null) {
			status = "Ready. Press Run to compile and run.";
			statusColor = Theme.TEXT_MUTED;
		} else {
			JavaRunner.Status s = execution.status();
			double seconds = execution.durationMillis() / 1000.0;
			status = s.label() + (s.active() ? String.format(Locale.ROOT, "  %.1f s", seconds) : String.format(Locale.ROOT, " after %.2f s", seconds))
					+ (execution.exitCode() != null && s == JavaRunner.Status.EXITED ? " (code " + execution.exitCode() + ")" : "");
			statusColor = switch (s) {
				case FINISHED -> Theme.SUCCESS;
				case STARTING, COMPILING, RUNNING -> Theme.ACCENT_STRONG;
				case STOPPED -> Theme.TEXT_SECONDARY;
				default -> Theme.ERROR;
			};
		}

		if (confirming()) {
			status = "Waiting for your confirmation below.";
			statusColor = Theme.WARNING;
		}

		ui.textClipped(status, x + 2, y + 18, width - 4, statusColor);
		int cy = y + 30;
		if (confirming()) {
			ui.roundRect(x, cy, width, confirmBoxHeight - 2, Theme.withAlpha(Theme.ERROR, 0.12f));
			ui.outline(x, cy, width, confirmBoxHeight - 2, Theme.withAlpha(Theme.ERROR, 0.6f));
			ui.icon(Icon.WARNING, x + 6, cy + 5, 9, Theme.WARNING);
			ui.text("Confirm before running", x + 19, cy + 6, Theme.WARNING);
			ui.wrapped(riskText(), x + 6, cy + 18, width - 12, Theme.TEXT_SECONDARY);
			confirm.render(ui);
			cancel.render(ui);
			cy += confirmBoxHeight;
		}

		editor.render(ui);
		cy += EDITOR_HEIGHT + 4;
		renderConsole(ui, execution, cy);
		ui.wrapped(note(), x + 2, cy + CONSOLE_HEIGHT + 4, width - 4, Theme.TEXT_MUTED);
		examples.render(ui);
	}

	private void renderConsole(UiContext ui, JavaRunner.Execution execution, int top) {
		ui.roundRect(x, top, width, CONSOLE_HEIGHT, 0xFF0B0B0E);
		ui.outline(x, top, width, CONSOLE_HEIGHT, Theme.BORDER);
		List<JavaRunner.Line> lines = execution == null ? List.of() : execution.lines();
		int visible = (CONSOLE_HEIGHT - 6) / LINE;
		int maxScroll = Math.max(0, lines.size() - visible);
		if (followOutput) {
			consoleScroll = maxScroll;
		}

		consoleScroll = Math.max(0, Math.min(consoleScroll, maxScroll));
		ui.pushClip(x + 2, top + 2, width - 4, CONSOLE_HEIGHT - 4);
		if (lines.isEmpty()) {
			ui.text("Output appears here.", x + 5, top + 4, Theme.TEXT_MUTED);
		}

		for (int i = 0; i < visible && consoleScroll + i < lines.size(); i++) {
			JavaRunner.Line line = lines.get(consoleScroll + i);
			ui.textClipped(line.text(), x + 5, top + 4 + i * LINE, width - 10, line.error() ? 0xFFFF8A8A : Theme.TEXT);
		}

		ui.popClip();
		if (maxScroll > 0) {
			int trackH = CONSOLE_HEIGHT - 4;
			int thumbH = Math.max(8, trackH * visible / lines.size());
			int thumbY = top + 2 + (trackH - thumbH) * consoleScroll / maxScroll;
			ui.rect(x + width - 3, thumbY, 2, thumbH, Theme.BORDER_STRONG);
		}
	}

	@Override
	public boolean mouseClicked(UiContext ui, double mouseX, double mouseY, int button) {
		if (examples.mouseClicked(ui, mouseX, mouseY, button)) return true;
		if (run.mouseClicked(ui, mouseX, mouseY, button)) return true;
		JavaRunner.Execution execution = module.execution();
		if (execution != null && execution.status().active() && stop.mouseClicked(ui, mouseX, mouseY, button)) return true;
		if (clear.mouseClicked(ui, mouseX, mouseY, button)) return true;
		if (copy.mouseClicked(ui, mouseX, mouseY, button)) return true;
		if (confirming() && (confirm.mouseClicked(ui, mouseX, mouseY, button) || cancel.mouseClicked(ui, mouseX, mouseY, button))) return true;
		return editor.mouseClicked(ui, mouseX, mouseY, button);
	}

	@Override
	public boolean mouseScrolled(UiContext ui, double mouseX, double mouseY, double amount) {
		if (editor.contains(mouseX, mouseY)) {
			return editor.mouseScrolled(ui, mouseX, mouseY, amount);
		}

		int top = y + 30 + confirmBoxHeight + EDITOR_HEIGHT + 4;
		if (UiContext.inside(mouseX, mouseY, x, top, width, CONSOLE_HEIGHT) && module.execution() != null) {
			int visible = (CONSOLE_HEIGHT - 6) / LINE;
			int maxScroll = Math.max(0, module.execution().lines().size() - visible);
			consoleScroll = Math.max(0, Math.min(maxScroll, consoleScroll - (int) Math.signum(amount) * 3));
			followOutput = consoleScroll >= maxScroll;
			return maxScroll > 0;
		}

		return false;
	}
}
