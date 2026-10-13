package io.github.theamethyx.nova.client.gui.widget;

import java.util.ArrayList;
import java.util.List;

import io.github.theamethyx.nova.client.gui.Icon;
import io.github.theamethyx.nova.client.gui.Theme;
import io.github.theamethyx.nova.client.gui.UiContext;
import io.github.theamethyx.nova.core.setting.BooleanSetting;
import io.github.theamethyx.nova.core.setting.EnumSetting;
import io.github.theamethyx.nova.core.setting.NumberSetting;
import io.github.theamethyx.nova.core.setting.Setting;

/**
 * One editable setting: name, control, current value and a reset button that appears when the
 * value differs from the default. The setting's description is the tooltip.
 */
public class SettingRow extends Container {
	private static final int RESET = 11;

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

		throw new IllegalArgumentException("No control for " + setting.getClass().getSimpleName());
	}

	private static String defaultText(Setting<?> setting) {
		Object value = setting.defaultValue();
		if (setting instanceof BooleanSetting) return (Boolean) value ? "On" : "Off";
		if (setting instanceof NumberSetting number) return number.format((Double) value) + (number.unit().isEmpty() ? "" : " " + number.unit());
		if (value instanceof Enum<?> e) return EnumSetting.labelOf(e);
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
		return !(setting instanceof BooleanSetting);
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
		} else if (setting instanceof NumberSetting) {
			control.setBounds(x + 7, y + 18, width - 14, 12);
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
		} else if (setting instanceof NumberSetting number) {
			String value = number.displayValue();
			ui.textRight(value, labelRight, y + 5, Theme.ACCENT_STRONG);
			labelRight -= ui.textWidth(value) + 6;
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

	/** Builds rows for every setting of a group. */
	public static List<Widget> rowsFor(List<Setting<?>> settings) {
		List<Widget> rows = new ArrayList<>();
		settings.forEach(setting -> rows.add(new SettingRow(setting)));
		return rows;
	}
}
