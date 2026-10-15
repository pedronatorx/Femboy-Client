package io.github.theamethyx.femboyclient.client.gui.widget;

import java.util.ArrayList;
import java.util.List;

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

	public SettingRow(Setting<?> setting) {
		this.setting = setting;
		this.control = add(createControl(setting));
		this.reset = add(new Button("", Button.Style.GHOST, setting::reset).icon(Icon.RESET));
		this.reset.tooltip("Reset '" + setting.name() + "' to its default (" + defaultText(setting) + ").");
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
		if (!setting.isVisible()) {
			return 0;
		}

		return stacked() ? 32 : 20;
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
		} else if (setting instanceof ColorSetting) {
			control.setBounds(x + 8, y + 17, width - 16, 10);
		} else {
			control.setBounds(x + 6, y + 15, width - 12, 14);
		}
	}

	@Override
	public void render(UiContext ui) {
		if (!setting.isVisible()) {
			return;
		}

		boolean hot = ui.hovered(x, y, width, height);
		ui.roundRect(x, y, width, height, hot ? Theme.SURFACE_HOVER : Theme.SURFACE);

		int labelRight = x + width - 4 - RESET - 6;
		if (setting instanceof BooleanSetting) {
			labelRight -= ToggleSwitch.WIDTH + 4;
		} else if (setting instanceof KeySetting) {
			labelRight -= KEY_WIDTH + 4;
		} else if (setting instanceof NumberSetting number) {
			String value = number.displayValue();
			ui.textRight(value, labelRight, y + 5, Theme.ACCENT_STRONG);
			labelRight -= ui.textWidth(value) + 6;
		} else if (setting instanceof ColorSetting color) {
			ui.textRight(color.displayValue(), labelRight, y + 5, Theme.TEXT_MUTED);
			labelRight -= ui.textWidth(color.displayValue()) + 6;
		}

		ui.textClipped(setting.name(), x + 6, y + 5, labelRight - x - 6, Theme.TEXT);
		if (hot) {
			ui.tooltip(setting, setting.description());
		}

		control.render(ui);
		if (!setting.isDefault()) {
			reset.render(ui);
		}
	}

	@Override
	public boolean mouseClicked(UiContext ui, double mouseX, double mouseY, int button) {
		if (!setting.isVisible()) {
			return false;
		}

		if (!setting.isDefault() && reset.mouseClicked(ui, mouseX, mouseY, button)) {
			return true;
		}

		return control.mouseClicked(ui, mouseX, mouseY, button);
	}

	/** Builds rows for every setting of a group, with a heading wherever a new section starts. */
	public static List<Widget> rowsFor(List<Setting<?>> all) {
		List<Setting<?>> settings = all.stream().filter(Setting::isListed).toList();
		List<Widget> rows = new ArrayList<>();
		String section = null;
		for (int i = 0; i < settings.size(); i++) {
			Setting<?> setting = settings.get(i);
			if (setting.section() != null && !setting.section().equals(section)) {
				section = setting.section();
				List<Setting<?>> members = new ArrayList<>();
				for (int j = i; j < settings.size() && section.equals(settings.get(j).section()); j++) {
					members.add(settings.get(j));
				}

				rows.add(new SectionHeader(section, members));
			}

			rows.add(setting instanceof IdSetSetting ids ? new IdSetRow(ids) : new SettingRow(setting));
		}

		return rows;
	}

	/** Heading above a section of settings; hidden while none of its settings is visible. */
	public static final class SectionHeader extends Widget {
		private final String title;
		private final List<Setting<?>> members;

		SectionHeader(String title, List<Setting<?>> members) {
			this.title = title;
			this.members = members;
		}

		public String title() {
			return title;
		}

		private boolean anyVisible() {
			return members.stream().anyMatch(Setting::isVisible);
		}

		@Override
		public int preferredHeight(UiContext ui, int width) {
			return anyVisible() ? 15 : 0;
		}

		@Override
		public void render(UiContext ui) {
			if (!anyVisible()) {
				return;
			}

			ui.text(title.toUpperCase(java.util.Locale.ROOT), x + 2, y + 5, Theme.ACCENT);
			int lineX = x + 8 + ui.textWidth(title.toUpperCase(java.util.Locale.ROOT));
			ui.rect(lineX, y + 9, x + width - lineX - 2, 1, Theme.BORDER);
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
