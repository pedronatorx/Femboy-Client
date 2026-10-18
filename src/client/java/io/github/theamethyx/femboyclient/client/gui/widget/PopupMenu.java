package io.github.theamethyx.femboyclient.client.gui.widget;

import java.util.ArrayList;
import java.util.List;

import io.github.theamethyx.femboyclient.client.gui.Icon;
import io.github.theamethyx.femboyclient.client.gui.Theme;
import io.github.theamethyx.femboyclient.client.gui.UiContext;

/**
 * A small list of actions that opens next to a "⋯" button (as an overlay). Each item has an icon, a label and an
 * optional tooltip; destructive items are drawn in the error color. A click runs the item and closes the menu; a click
 * outside closes it.
 */
public class PopupMenu extends Widget {
	private static final int ITEM = 15;

	public record Item(Icon icon, String label, String tooltip, boolean danger, boolean enabled, Runnable action) {
		public static Item of(Icon icon, String label, String tooltip, Runnable action) {
			return new Item(icon, label, tooltip, false, true, action);
		}
	}

	private final List<Item> items = new ArrayList<>();

	public PopupMenu(List<Item> items) {
		this.items.addAll(items);
	}

	public List<Item> items() {
		return items;
	}

	/** Opens the menu under (or above, near the bottom) the given anchor, right-aligned to it. */
	public PopupMenu openAt(UiContext ui, Widget anchor) {
		int w = 40;
		for (Item item : items) {
			w = Math.max(w, ui.textWidth(item.label()) + 30);
		}

		int h = items.size() * ITEM + 6;
		int top = anchor.y() + anchor.height() + 2;
		if (top + h > ui.screenHeight() - 4) {
			top = anchor.y() - h - 2;
		}

		setBounds(Math.max(4, anchor.x() + anchor.width() - w), Math.max(4, top), w, h);
		ui.openOverlay(this);
		return this;
	}

	@Override
	public void render(UiContext ui) {
		ui.shadow(x, y, width, height, Theme.RADIUS_PANEL, 1);
		ui.roundRect(x, y, width, height, Theme.SURFACE, Theme.RADIUS_PANEL);
		ui.outline(x, y, width, height, Theme.BORDER_STRONG, Theme.RADIUS_PANEL);
		for (int i = 0; i < items.size(); i++) {
			Item item = items.get(i);
			int rowY = y + 3 + i * ITEM;
			boolean hot = item.enabled() && ui.hovered(x + 3, rowY, width - 6, ITEM - 1);
			if (hot) {
				ui.roundRect(x + 3, rowY, width - 6, ITEM - 1, item.danger() ? Theme.withAlpha(Theme.ERROR, 0.18f) : Theme.SURFACE_HOVER, Theme.RADIUS_CONTROL);
				ui.pointer();
				if (item.tooltip() != null) {
					ui.tooltip(item, item.tooltip());
				}
			}

			int color = !item.enabled() ? Theme.TEXT_MUTED : item.danger() ? Theme.ERROR : Theme.TEXT;
			ui.icon(item.icon(), x + 8, rowY + 3, 8, item.danger() && item.enabled() ? Theme.ERROR : Theme.TEXT_SECONDARY);
			ui.text(item.label(), x + 21, rowY + 3, color);
		}
	}

	@Override
	public boolean mouseClicked(UiContext ui, double mouseX, double mouseY, int button) {
		ui.closeOverlay();
		if (!contains(mouseX, mouseY)) {
			return true;
		}

		int index = (int) ((mouseY - y - 3) / ITEM);
		if (index >= 0 && index < items.size() && items.get(index).enabled()) {
			items.get(index).action().run();
		}

		return true;
	}

	/** Runs the item with this label as if clicked (used by automated UI tests). */
	public boolean choose(UiContext ui, String label) {
		for (Item item : items) {
			if (item.label().equals(label) && item.enabled()) {
				ui.closeOverlay();
				item.action().run();
				return true;
			}
		}

		return false;
	}
}
