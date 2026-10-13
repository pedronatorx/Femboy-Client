package io.github.theamethyx.nova.client.gui.widget;

import java.util.List;
import java.util.function.Supplier;

import io.github.theamethyx.nova.client.gui.Theme;
import io.github.theamethyx.nova.client.gui.UiContext;

/**
 * A titled card of label/value rows. Rows that do not fit side by side put the value on its own
 * wrapped line, so long values are never cut off. A row with an empty label is a paragraph.
 */
public class DetailCard extends Widget {
	/** {@code color} 0 means the default text color. */
	public record Row(String label, String value, int color) {
		public Row(String label, String value) {
			this(label, value, 0);
		}
	}

	private final String title;
	private final Supplier<List<Row>> rows;

	public DetailCard(String title, Supplier<List<Row>> rows) {
		this.title = title;
		this.rows = rows;
	}

	private boolean stacked(UiContext ui, Row row, int width) {
		return row.label().isEmpty() || ui.textWidth(row.label()) + ui.textWidth(row.value()) + 18 > width;
	}

	@Override
	public int preferredHeight(UiContext ui, int width) {
		int h = 20;
		for (Row row : rows.get()) {
			if (stacked(ui, row, width)) {
				h += (row.label().isEmpty() ? 0 : 10) + ui.wrappedHeight(row.value(), width - 18) + 1;
			} else {
				h += 11;
			}
		}

		return h + 2;
	}

	@Override
	public void render(UiContext ui) {
		ui.roundRect(x, y, width, height, Theme.SURFACE);
		boolean cut = ui.textClipped(title, x + 6, y + 6, width - 12, Theme.TEXT);
		if (cut && ui.hovered(x, y, width, 16)) {
			ui.tooltip(this, title);
		}

		int rowY = y + 19;
		for (Row row : rows.get()) {
			int color = row.color() == 0 ? Theme.TEXT : row.color();
			if (stacked(ui, row, width)) {
				if (!row.label().isEmpty()) {
					ui.text(row.label(), x + 6, rowY, Theme.TEXT_SECONDARY);
					rowY += 10;
				}

				rowY += ui.wrapped(row.value(), row.label().isEmpty() ? x + 6 : x + 12, rowY, width - 18, row.label().isEmpty() ? Theme.TEXT_SECONDARY : color) + 1;
			} else {
				ui.text(row.label(), x + 6, rowY, Theme.TEXT_SECONDARY);
				ui.textRight(row.value(), x + width - 6, rowY, color);
				rowY += 11;
			}
		}
	}
}
