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
		ui.roundRect(x, y, width, height, Theme.lerp(Theme.SURFACE, Theme.SURFACE_HOVER, h), Theme.RADIUS_CONTROL);
		ui.outline(x, y, width, height, open ? Theme.ACCENT : Theme.lerp(Theme.BORDER, Theme.BORDER_STRONG, h), Theme.RADIUS_CONTROL);
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

	/** Options shown at once; longer lists scroll, and from {@link #FILTER_FROM} options typing narrows them. */
	private static final int MAX_VISIBLE = 10;
	private static final int FILTER_FROM = 9;

	/**
	 * The open option list. Positioned below the dropdown, or above it when space runs out. Long lists scroll with the
	 * mouse wheel; lists of {@link #FILTER_FROM} or more options show a filter line that typing fills.
	 */
	public final class OptionList extends Widget {
		private int scroll;
		private String filter = "";

		void open(UiContext ui) {
			scroll = 0;
			filter = "";
			int visible = Math.min(MAX_VISIBLE, options.get().size()) + (filterable() ? 1 : 0);
			int listHeight = visible * OPTION_HEIGHT + 2;
			int below = Dropdown.this.y + Dropdown.this.height + 1;
			int top = below + listHeight > ui.screenHeight() - 2 ? Dropdown.this.y - listHeight - 1 : below;
			setBounds(Dropdown.this.x, Math.max(2, top), Math.max(Dropdown.this.width, 90), listHeight);
			ui.openOverlay(this);
			int index = shown().indexOf(value.get());
			if (index >= MAX_VISIBLE) {
				scroll = Math.min(index - MAX_VISIBLE / 2, shown().size() - MAX_VISIBLE);
			}
		}

		private boolean filterable() {
			return options.get().size() >= FILTER_FROM;
		}

		/** Options matching the typed filter. */
		public List<T> shown() {
			if (filter.isEmpty()) {
				return options.get();
			}

			String needle = filter.toLowerCase(java.util.Locale.ROOT);
			return options.get().stream().filter(option -> label(option).toLowerCase(java.util.Locale.ROOT).contains(needle)).toList();
		}

		private int firstRowY() {
			return y + 1 + (filterable() ? OPTION_HEIGHT : 0);
		}

		@Override
		public void render(UiContext ui) {
			ui.shadow(x, y, width, height, Theme.RADIUS_CONTROL, 1);
			ui.roundRect(x, y, width, height, Theme.SURFACE, Theme.RADIUS_CONTROL);
			ui.outline(x, y, width, height, Theme.BORDER_STRONG, Theme.RADIUS_CONTROL);
			if (filterable()) {
				ui.icon(Icon.SEARCH, x + 4, y + 3, 7, Theme.TEXT_MUTED);
				ui.textClipped(filter.isEmpty() ? "Type to filter" : filter, x + 14, y + 3, width - 18, filter.isEmpty() ? Theme.TEXT_MUTED : Theme.TEXT);
				ui.rect(x + 2, y + OPTION_HEIGHT, width - 4, 1, Theme.BORDER);
			}

			List<T> all = shown();
			T current = value.get();
			int rows = Math.min(MAX_VISIBLE, all.size());
			for (int i = 0; i < rows; i++) {
				T option = all.get(i + scroll);
				int rowY = firstRowY() + i * OPTION_HEIGHT;
				boolean hot = ui.hovered(x + 1, rowY, width - 2, OPTION_HEIGHT);
				if (hot) {
					ui.roundRect(x + 2, rowY, width - 4, OPTION_HEIGHT, Theme.SURFACE_ACTIVE, 3);
					ui.pointer();
				}

				boolean selected = option.equals(current);
				if (selected) {
					ui.icon(Icon.CHECK, x + 4, rowY + 3, 7, Theme.ACCENT_STRONG);
				}

				ui.textClipped(label(option), x + 14, rowY + 3, width - 18, selected ? Theme.ACCENT_STRONG : Theme.TEXT);
			}

			if (all.isEmpty()) {
				ui.text("No match", x + 14, firstRowY() + 3, Theme.TEXT_MUTED);
			}

			if (all.size() > MAX_VISIBLE) {
				int track = rows * OPTION_HEIGHT;
				int bar = Math.max(8, track * MAX_VISIBLE / all.size());
				int barY = firstRowY() + (track - bar) * scroll / Math.max(1, all.size() - MAX_VISIBLE);
				ui.roundRect(x + width - 3, barY, 2, bar, Theme.BORDER_STRONG, 1);
			}
		}

		@Override
		public boolean mouseClicked(UiContext ui, double mouseX, double mouseY, int button) {
			if (contains(mouseX, mouseY)) {
				if (mouseY < firstRowY()) {
					return true; // the filter line
				}

				int index = (int) ((mouseY - firstRowY()) / OPTION_HEIGHT) + scroll;
				List<T> all = shown();
				if (button == 0 && index >= 0 && index < all.size()) {
					setter.accept(all.get(index));
				}
			}

			ui.closeOverlay();
			return true;
		}

		@Override
		public boolean mouseScrolled(UiContext ui, double mouseX, double mouseY, double amount) {
			scroll = Math.clamp(scroll - (int) Math.signum(amount), 0, Math.max(0, shown().size() - MAX_VISIBLE));
			return true;
		}

		@Override
		public boolean charTyped(UiContext ui, net.minecraft.client.input.CharacterEvent event) {
			if (!filterable() || filter.length() >= 30) {
				return false;
			}

			filter += Character.toString(event.codepoint());
			scroll = 0;
			return true;
		}

		@Override
		public boolean keyPressed(UiContext ui, net.minecraft.client.input.KeyEvent event) {
			switch (event.key()) {
				case org.lwjgl.glfw.GLFW.GLFW_KEY_BACKSPACE -> {
					if (!filter.isEmpty()) filter = filter.substring(0, filter.length() - 1);
					scroll = 0;
				}
				case org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER, org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER -> {
					List<T> all = shown();
					if (all.size() == 1) setter.accept(all.getFirst());
					ui.closeOverlay();
				}
				case org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE -> ui.closeOverlay();
				default -> {
					return false;
				}
			}

			return true;
		}
	}

	/** The option list (used by automated UI tests). */
	public OptionList optionList() {
		return list;
	}
}
