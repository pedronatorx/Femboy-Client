package io.github.theamethyx.femboyclient.client.gui.widget;

import java.util.function.BooleanSupplier;

import io.github.theamethyx.femboyclient.client.gui.Theme;
import io.github.theamethyx.femboyclient.client.gui.UiContext;

/** A two-line list entry that can be selected, with an optional colored status label on the right. */
public class SelectableRow extends Widget {
	private final String title;
	private final String subtitle;
	private final String status;
	private final int statusColor;
	private final BooleanSupplier selected;
	private final Runnable onSelect;

	public SelectableRow(String title, String subtitle, String status, int statusColor, BooleanSupplier selected, Runnable onSelect) {
		this.title = title;
		this.subtitle = subtitle;
		this.status = status;
		this.statusColor = statusColor;
		this.selected = selected;
		this.onSelect = onSelect;
	}

	@Override
	public int preferredHeight(UiContext ui, int width) {
		return 26;
	}

	@Override
	public void render(UiContext ui) {
		boolean isSelected = selected.getAsBoolean();
		boolean hot = ui.hovered(x, y, width, height);
		ui.roundRect(x, y, width, height, isSelected ? Theme.SURFACE_ACTIVE : hot ? Theme.SURFACE_HOVER : Theme.SURFACE);
		if (isSelected) {
			ui.rect(x, y + 3, 2, height - 6, Theme.ACCENT);
		}

		int statusWidth = status == null ? 0 : ui.textWidth(status) + 6;
		ui.textClipped(title, x + 6, y + 4, width - 12 - statusWidth, Theme.TEXT);
		if (status != null) {
			ui.textRight(status, x + width - 6, y + 4, statusColor);
		}

		ui.textClipped(subtitle, x + 6, y + 14, width - 12, Theme.TEXT_MUTED);
		if (hot) {
			ui.pointer();
			ui.tooltip(this, title + "\n" + subtitle + (status == null ? "" : "\n" + status));
		}
	}

	@Override
	public boolean mouseClicked(UiContext ui, double mouseX, double mouseY, int button) {
		if (button == 0 && contains(mouseX, mouseY)) {
			onSelect.run();
			return true;
		}

		return false;
	}
}
