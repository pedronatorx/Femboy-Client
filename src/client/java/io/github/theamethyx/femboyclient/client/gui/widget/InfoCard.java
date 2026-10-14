package io.github.theamethyx.femboyclient.client.gui.widget;

import java.util.List;
import java.util.function.Supplier;

import io.github.theamethyx.femboyclient.client.gui.Icon;
import io.github.theamethyx.femboyclient.client.gui.Theme;
import io.github.theamethyx.femboyclient.client.gui.UiContext;

/** A titled card of label/value lines whose content is recomputed every frame. */
public class InfoCard extends Widget {
	public record Line(String label, String value, int color, String explanation) {
		public Line(String label, String value) {
			this(label, value, Theme.TEXT, null);
		}

		public Line(String label, String value, int color) {
			this(label, value, color, null);
		}
	}

	private final String title;
	private final Icon icon;
	private final Supplier<List<Line>> lines;

	public InfoCard(String title, Icon icon, Supplier<List<Line>> lines) {
		this.title = title;
		this.icon = icon;
		this.lines = lines;
	}

	@Override
	public int preferredHeight(UiContext ui, int width) {
		return 22 + lines.get().size() * 11 + 3;
	}

	@Override
	public void render(UiContext ui) {
		ui.roundRect(x, y, width, height, Theme.SURFACE);
		ui.icon(icon, x + 6, y + 6, 9, Theme.ACCENT);
		ui.text(title, x + 19, y + 7, Theme.TEXT);
		ui.rect(x + 6, y + 19, width - 12, 1, Theme.BORDER);

		List<Line> current = lines.get();
		for (int i = 0; i < current.size(); i++) {
			Line line = current.get(i);
			int rowY = y + 23 + i * 11;
			int valueWidth = Math.min(ui.textWidth(line.value()), (width - 12) * 3 / 5);
			int labelWidth = width - 12 - valueWidth - 6;
			ui.textClipped(line.label(), x + 6, rowY, labelWidth, Theme.TEXT_SECONDARY);
			boolean cut = ui.textClipped(line.value(), x + width - 6 - valueWidth, rowY, valueWidth, line.color());
			if (ui.hovered(x, rowY - 1, width, 11)) {
				String tip = line.explanation() != null ? line.explanation() : cut ? line.value() : null;
				if (tip != null) {
					ui.tooltip(line, tip);
				}
			}
		}
	}
}
