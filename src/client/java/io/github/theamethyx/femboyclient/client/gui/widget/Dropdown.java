package io.github.theamethyx.femboyclient.client.gui.widget;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import io.github.theamethyx.femboyclient.client.gui.Anim;
import io.github.theamethyx.femboyclient.client.gui.Icon;
import io.github.theamethyx.femboyclient.client.gui.Theme;
import io.github.theamethyx.femboyclient.client.gui.UiContext;

/** Shows the current choice; clicking opens a list of options as an overlay. */
public class Dropdown<T> extends Widget {
	private static final int OPTION_HEIGHT = 13;

	private final Supplier<List<T>> options;
	private final Supplier<T> value;
	private final Consumer<T> setter;
	private final Function<T, String> labeler;
	private final Anim hover = new Anim(0, 18);
	private final OptionList list = new OptionList();

	public Dropdown(Supplier<List<T>> options, Supplier<T> value, Consumer<T> setter, Function<T, String> labeler) {
		this.options = options;
		this.value = value;
		this.setter = setter;
		this.labeler = labeler;
		this.height = 14;
	}

	/** The label of a value; a labeler that cannot name {@code null} shows "-". */
	private String label(T option) {
		if (option != null) {
			return labeler.apply(option);
		}

		try {
			String text = labeler.apply(null);
			return text == null ? "-" : text;
		} catch (RuntimeException e) {
			return "-";
		}
	}

	public boolean isOpen(UiContext ui) {
		return ui.overlay() == list;
	}

	@Override
	public void render(UiContext ui) {
		boolean open = isOpen(ui);
		float h = hover.update(hovered(ui) || open ? 1 : 0, ui.frameSeconds(), ui.animate());
		ui.roundRect(x, y, width, height, Theme.lerp(Theme.SURFACE, Theme.SURFACE_ACTIVE, h));
		ui.outline(x, y, width, height, open ? Theme.ACCENT_DIM : Theme.lerp(Theme.BORDER, Theme.BORDER_STRONG, h));
		T current = value.get();
		ui.textClipped(label(current), x + 5, y + (height - 8) / 2, width - 18, Theme.TEXT);
		ui.icon(Icon.CHEVRON_DOWN, x + width - 12, y + (height - 8) / 2, 8, open ? Theme.ACCENT : Theme.TEXT_SECONDARY);

		if (hovered(ui)) {
			ui.pointer();
		}

		offerTooltip(ui);
	}

	@Override
	public boolean mouseClicked(UiContext ui, double mouseX, double mouseY, int button) {
		if (button == 0 && contains(mouseX, mouseY)) {
			if (isOpen(ui)) {
				ui.closeOverlay();
			} else {
				list.open(ui);
			}

			return true;
		}

		return false;
	}

	/** The open option list. Positioned below the dropdown, or above it when space runs out. */
	private final class OptionList extends Widget {
		private int screenHeight;

		void open(UiContext ui) {
			int count = options.get().size();
			int listHeight = count * OPTION_HEIGHT + 2;
			int below = Dropdown.this.y + Dropdown.this.height + 1;
			screenHeight = ui.screenHeight();
			int top = below + listHeight > screenHeight - 2 ? Dropdown.this.y - listHeight - 1 : below;
			setBounds(Dropdown.this.x, Math.max(2, top), Dropdown.this.width, listHeight);
			ui.openOverlay(this);
		}

		@Override
		public void render(UiContext ui) {
			ui.roundRect(x, y, width, height, 0xFC16161B);
			ui.outline(x, y, width, height, Theme.BORDER_STRONG);
			List<T> all = options.get();
			T current = value.get();

			for (int i = 0; i < all.size(); i++) {
				T option = all.get(i);
				int rowY = y + 1 + i * OPTION_HEIGHT;
				boolean hot = ui.hovered(x + 1, rowY, width - 2, OPTION_HEIGHT);
				if (hot) {
					ui.rect(x + 1, rowY, width - 2, OPTION_HEIGHT, Theme.SURFACE_ACTIVE);
					ui.pointer();
				}

				boolean selected = option.equals(current);
				if (selected) {
					ui.rect(x + 1, rowY + 2, 2, OPTION_HEIGHT - 4, Theme.ACCENT);
				}

				ui.textClipped(label(option), x + 6, rowY + 3, width - 10, selected ? Theme.ACCENT_STRONG : Theme.TEXT);
			}
		}

		@Override
		public boolean mouseClicked(UiContext ui, double mouseX, double mouseY, int button) {
			if (contains(mouseX, mouseY)) {
				int index = (int) ((mouseY - y - 1) / OPTION_HEIGHT);
				List<T> all = options.get();
				if (button == 0 && index >= 0 && index < all.size()) {
					setter.accept(all.get(index));
				}
			}

			ui.closeOverlay();
			return true;
		}
	}
}
