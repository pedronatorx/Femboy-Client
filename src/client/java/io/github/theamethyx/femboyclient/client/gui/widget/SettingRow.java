package io.github.theamethyx.femboyclient.client.gui.widget;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.BooleanSupplier;

import io.github.theamethyx.femboyclient.client.gui.Icon;
import io.github.theamethyx.femboyclient.client.gui.Theme;
import io.github.theamethyx.femboyclient.client.gui.UiContext;
import io.github.theamethyx.femboyclient.core.setting.BooleanSetting;
import io.github.theamethyx.femboyclient.core.setting.ColorSetting;
import io.github.theamethyx.femboyclient.core.setting.IdSetSetting;
import io.github.theamethyx.femboyclient.core.setting.KeySetting;
import io.github.theamethyx.femboyclient.core.setting.StringSetting;
import io.github.theamethyx.femboyclient.core.setting.EnumSetting;
import io.github.theamethyx.femboyclient.core.setting.NumberSetting;
import io.github.theamethyx.femboyclient.core.setting.Setting;

/**
 * One editable setting: name, control, current value and a reset button that appears when the
 * value differs from the default. The setting's description is the tooltip.
 */
public class SettingRow extends Container {
	private static final int RESET = 11;
	private static final int KEY_WIDTH = 74;

	private final Setting<?> setting;
	private final Widget control;
	private final Button reset;
	/** For number settings: a field for typing the exact value, shown after a click on the value. */
	private final TextField exact;
	private boolean editing;
	private UiContext editingUi;
	private boolean valueHot;
	private String error;
	private BooleanSupplier collapsed = () -> false;

	public SettingRow(Setting<?> setting) {
		this.setting = setting;
		this.control = add(createControl(setting));
		this.reset = add(new Button("", Button.Style.GHOST, setting::reset).icon(Icon.RESET));
		this.reset.tooltip("Reset '" + setting.name() + "' to its default (" + defaultText(setting) + ").");
		if (setting instanceof NumberSetting number) {
			exact = new TextField(number.format(number.get()), 16) {
				@Override
				public void onFocusLost() {
					super.onFocusLost();
					finishEditing(text());
				}
			};
			exact.allow(c -> Character.isDigit(c) || c == '.' || c == '-' || c == ',');
			exact.onSubmit(this::finishEditing);
		} else {
			exact = null;
		}
	}

	/** Hides the row while its section is collapsed. */
	SettingRow collapsedWhen(BooleanSupplier condition) {
		this.collapsed = condition;
		return this;
	}

	private void finishEditing(String text) {
		if (!editing || !(setting instanceof NumberSetting number)) {
			return;
		}

		editing = false;
		error = null;
		if (editingUi != null && editingUi.isFocused(exact)) {
			// Enter ends the entry: the keyboard goes back to the menu (Escape, shortcuts).
			editingUi.focus(null);
		}

		editingUi = null;
		try {
			String problem = number.set(Double.parseDouble(text.trim().replace(',', '.')));
			if (problem != null) {
				error = problem;
			}
		} catch (NumberFormatException e) {
			error = "Not a number: " + text;
		}
	}

	/** Starts typing an exact value (what a click on the value does). */
	public void editValue(UiContext ui) {
		if (exact != null && setting instanceof NumberSetting number) {
			editing = true;
			editingUi = ui;
			exact.setText(number.format(number.get()));
			ui.focus(exact);
		}
	}

	public boolean isEditing() {
		return editing;
	}

	private static <E extends Enum<E>> Widget enumDropdown(EnumSetting<E> setting) {
		return new Dropdown<E>(setting::options, setting::get, value -> setting.set(value), EnumSetting::labelOf);
	}

	private static Widget createControl(Setting<?> setting) {
		if (setting instanceof BooleanSetting bool) {
			return new ToggleSwitch(bool::get, bool::set);
		}

		if (setting instanceof NumberSetting number) {
			return new Slider(number);
		}

		if (setting instanceof EnumSetting<?> enumSetting) {
			return enumDropdown(enumSetting);
		}

		if (setting instanceof ColorSetting color) {
			return new ColorPicker(color);
		}

		if (setting instanceof KeySetting key) {
			return new KeybindButton(key::get, key::set);
		}

		if (setting instanceof StringSetting text) {
			return new SettingTextField(text);
		}

		throw new IllegalArgumentException("No control for " + setting.getClass().getSimpleName());
	}

	private static String defaultText(Setting<?> setting) {
		Object value = setting.defaultValue();
		if (setting instanceof BooleanSetting) return (Boolean) value ? "On" : "Off";
		if (setting instanceof NumberSetting number) return number.format((Double) value) + (number.unit().isEmpty() ? "" : " " + number.unit());
		if (value instanceof Enum<?> e) return EnumSetting.labelOf(e);
		if (setting instanceof ColorSetting) return ColorSetting.hex((Integer) value);
		if (setting instanceof KeySetting) return KeybindButton.keyName((Integer) value);
		if (setting instanceof StringSetting) return ((String) value).isEmpty() ? "empty" : "\"" + value + "\"";
		return String.valueOf(value);
	}

	public Setting<?> setting() {
		return setting;
	}

	public Widget control() {
		return control;
	}

	private boolean stacked() {
		// Sliders and dropdowns get their own line so labels are never squeezed.
		return !(setting instanceof BooleanSetting || setting instanceof KeySetting);
	}

	@Override
	public int preferredHeight(UiContext ui, int width) {
		if (!setting.isVisible() || collapsed.getAsBoolean()) {
			return 0;
		}

		return (stacked() ? 32 : 20) + (error != null ? 10 : 0);
	}

	@Override
	protected void onLayout() {
		int right = x + width - 4;
		reset.setBounds(right - RESET, y + 4, RESET, RESET);

		if (setting instanceof BooleanSetting) {
			control.setBounds(right - RESET - 4 - ToggleSwitch.WIDTH, y + 5, ToggleSwitch.WIDTH, ToggleSwitch.HEIGHT);
		} else if (setting instanceof KeySetting) {
			control.setBounds(right - RESET - 4 - KEY_WIDTH, y + 3, KEY_WIDTH, 14);
		} else if (setting instanceof NumberSetting) {
			control.setBounds(x + 7, y + 18, width - 14, 12);
			exact.setBounds(x + width - RESET - 10 - 58, y + 2, 58, 13);
		} else if (setting instanceof ColorSetting) {
			control.setBounds(x + 8, y + 17, width - 16, 10);
		} else {
			control.setBounds(x + 6, y + 15, width - 12, 14);
		}
	}

	@Override
	public void render(UiContext ui) {
		if (!setting.isVisible() || collapsed.getAsBoolean()) {
			return;
		}

		boolean hot = ui.hovered(x, y, width, height);
		ui.roundRect(x, y, width, height, hot ? Theme.SURFACE_HOVER : Theme.SURFACE, 4);

		int labelRight = x + width - 4 - RESET - 6;
		if (setting instanceof BooleanSetting) {
			labelRight -= ToggleSwitch.WIDTH + 4;
		} else if (setting instanceof KeySetting) {
			labelRight -= KEY_WIDTH + 4;
		} else if (setting instanceof NumberSetting number) {
			valueHot = false;
			if (editing) {
				exact.render(ui);
				labelRight = exact.x() - 6;
			} else {
				String value = number.displayValue();
				valueHot = ui.hovered(labelRight - ui.textWidth(value) - 2, y + 2, ui.textWidth(value) + 4, 12);
				if (valueHot) {
					ui.roundRect(labelRight - ui.textWidth(value) - 3, y + 2, ui.textWidth(value) + 6, 12, Theme.withAlpha(Theme.ACCENT, 0.2f), 3);
					ui.pointer();
					ui.tooltip(this, "Click to type an exact value. Shift + mouse wheel over the slider changes it by one step.");
				}

				ui.textRight(value, labelRight, y + 5, Theme.ACCENT_STRONG);
				labelRight -= ui.textWidth(value) + 6;
			}
		} else if (setting instanceof ColorSetting color) {
			ui.textRight(color.displayValue(), labelRight, y + 5, Theme.TEXT_MUTED);
			labelRight -= ui.textWidth(color.displayValue()) + 6;
		}

		ui.textClipped(setting.name(), x + 6, y + 5, labelRight - x - 6, Theme.TEXT);
		if (hot && !valueHot) {
			ui.tooltip(setting, setting.description());
		}

		if (error != null) {
			ui.textClipped(error, x + 6, y + height - 10, width - 12, Theme.ERROR);
		}

		control.render(ui);
		if (!setting.isDefault()) {
			reset.render(ui);
		}
	}

	@Override
	public boolean mouseClicked(UiContext ui, double mouseX, double mouseY, int button) {
		if (!setting.isVisible() || collapsed.getAsBoolean()) {
			return false;
		}

		if (!setting.isDefault() && reset.mouseClicked(ui, mouseX, mouseY, button)) {
			return true;
		}

		if (setting instanceof NumberSetting number && !editing) {
			String value = number.displayValue();
			int right = x + width - 4 - RESET - 6;
			if (UiContext.inside(mouseX, mouseY, right - ui.textWidth(value) - 3, y + 2, ui.textWidth(value) + 6, 12)) {
				editValue(ui);
				return true;
			}
		}

		if (editing && exact.mouseClicked(ui, mouseX, mouseY, button)) {
			return true;
		}

		return control.mouseClicked(ui, mouseX, mouseY, button);
	}

	@Override
	public boolean mouseScrolled(UiContext ui, double mouseX, double mouseY, double amount) {
		return setting.isVisible() && !collapsed.getAsBoolean() && control.mouseScrolled(ui, mouseX, mouseY, amount);
	}

	/** Sections that start collapsed: fine-tuning most people never need to see. Their headers stay, one click opens them. */
	public static final Set<String> COLLAPSED_BY_DEFAULT = Set.of("Performance", "Colors", "Health", "Style", "Keys", "Display");

	/** Builds rows for every setting of a group, with a heading wherever a new section starts. */
	public static List<Widget> rowsFor(List<Setting<?>> all) {
		return rowsFor("", all);
	}

	/**
	 * Builds rows for every setting of a group, with a collapsible heading wherever a new section starts. {@code owner}
	 * (a module id) keys the remembered open/closed state of each section.
	 */
	public static List<Widget> rowsFor(String owner, List<Setting<?>> all) {
		List<Setting<?>> settings = all.stream().filter(Setting::isListed).toList();
		List<Widget> rows = new ArrayList<>();
		String section = null;
		SectionHeader header = null;
		for (int i = 0; i < settings.size(); i++) {
			Setting<?> setting = settings.get(i);
			if (setting.section() != null && !setting.section().equals(section)) {
				section = setting.section();
				List<Setting<?>> members = new ArrayList<>();
				for (int j = i; j < settings.size() && section.equals(settings.get(j).section()); j++) {
					members.add(settings.get(j));
				}

				// Only module settings start with fine-tuning folded away; client options are always shown open.
				boolean foldable = !owner.isEmpty() && !owner.equals("options");
				header = new SectionHeader(owner + "/" + section, section, members, foldable && COLLAPSED_BY_DEFAULT.contains(section));
				rows.add(header);
			}

			SectionHeader current = header;
			BooleanSupplier hidden = current == null ? () -> false : current::collapsed;
			rows.add(setting instanceof IdSetSetting ids ? new IdSetRow(ids).collapsedWhen(hidden) : new SettingRow(setting).collapsedWhen(hidden));
		}

		return rows;
	}

	/**
	 * Heading above a section of settings, with a chevron: a click collapses or opens the section (remembered while the
	 * game runs). Hidden while none of its settings is visible.
	 */
	public static final class SectionHeader extends Widget {
		private final String key;
		private final String title;
		private final List<Setting<?>> members;
		private final boolean closedByDefault;

		SectionHeader(String key, String title, List<Setting<?>> members, boolean closedByDefault) {
			this.key = key;
			this.title = title;
			this.members = members;
			this.closedByDefault = closedByDefault;
		}

		public String title() {
			return title;
		}

		public boolean collapsed() {
			Boolean open = io.github.theamethyx.femboyclient.client.gui.GuiState.openSections.get(key);
			return open == null ? closedByDefault : !open;
		}

		public void setCollapsed(boolean collapsed) {
			io.github.theamethyx.femboyclient.client.gui.GuiState.openSections.put(key, !collapsed);
		}

		private boolean anyVisible() {
			return members.stream().anyMatch(Setting::isVisible);
		}

		@Override
		public int preferredHeight(UiContext ui, int width) {
			return anyVisible() ? 16 : 0;
		}

		@Override
		public void render(UiContext ui) {
			if (!anyVisible()) {
				return;
			}

			boolean hot = ui.hovered(x, y, width, height);
			boolean closed = collapsed();
			if (hot) {
				ui.roundRect(x, y + 1, width, height - 2, Theme.withAlpha(Theme.SURFACE_HOVER, 0.6f), 4);
				ui.pointer();
			}

			ui.icon(closed ? Icon.CHEVRON_RIGHT : Icon.CHEVRON_DOWN, x + 2, y + 4, 8, hot ? Theme.TEXT : Theme.ACCENT);
			String label = title.toUpperCase(java.util.Locale.ROOT);
			ui.text(label, x + 13, y + 5, hot ? Theme.TEXT : Theme.ACCENT);
			int lineX = x + 18 + ui.textWidth(label);
			if (closed) {
				String count = members.size() + (members.size() == 1 ? " setting" : " settings");
				ui.textRight(count, x + width - 3, y + 5, Theme.TEXT_MUTED);
				ui.rect(lineX, y + 9, x + width - lineX - ui.textWidth(count) - 10, 1, Theme.BORDER);
			} else {
				ui.rect(lineX, y + 9, x + width - lineX - 2, 1, Theme.BORDER);
			}

			if (hot) {
				ui.tooltip(this, closed ? "Show the " + title + " settings." : "Hide the " + title + " settings.");
			}
		}

		@Override
		public boolean mouseClicked(UiContext ui, double mouseX, double mouseY, int button) {
			if (!anyVisible() || !contains(mouseX, mouseY) || button != 0) {
				return false;
			}

			setCollapsed(!collapsed());
			return true;
		}
	}

	/** Text input bound to a {@link StringSetting}; follows the setting when it changes elsewhere (e.g. reset). */
	private static final class SettingTextField extends TextField {
		private final StringSetting setting;

		SettingTextField(StringSetting setting) {
			super(setting.placeholder(), setting.maxLength());
			this.setting = setting;
			setText(setting.get());
			onChange(text -> setting.set(text));
		}

		@Override
		public void render(UiContext ui) {
			if (!ui.isFocused(this) && !text().equals(setting.get())) {
				setText(setting.get());
			}

			super.render(ui);
		}
	}
}
