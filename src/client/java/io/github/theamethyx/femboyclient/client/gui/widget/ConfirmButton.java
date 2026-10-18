package io.github.theamethyx.femboyclient.client.gui.widget;

import io.github.theamethyx.femboyclient.client.gui.UiContext;

/**
 * A button for actions that lose data: the first click arms it ("Click again to ..."), a second click within three seconds
 * runs the action. Routine actions do not need this; destructive ones do.
 */
public class ConfirmButton extends Button {
	private static final long WINDOW_MILLIS = 3000;
	private long armedAt;

	public ConfirmButton(String label, String confirmLabel, Style style, Runnable action) {
		super(label, style, action);
		this.label = () -> armed() ? confirmLabel : label;
	}

	private boolean armed() {
		return armedAt != 0 && System.currentTimeMillis() - armedAt < WINDOW_MILLIS;
	}

	@Override
	public boolean mouseClicked(UiContext ui, double mouseX, double mouseY, int button) {
		if (button != 0 || !isEnabled() || !contains(mouseX, mouseY)) {
			return false;
		}

		if (armed()) {
			armedAt = 0;
			return super.mouseClicked(ui, mouseX, mouseY, button);
		}

		armedAt = System.currentTimeMillis();
		return true;
	}
}
