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

	/** Whether help mode is on: the explanation is shown under the name instead of only on hover. */
	private static boolean helpMode() {
		var femboy = io.github.theamethyx.femboyclient.client.FemboyClient.get();
		return femboy != null && femboy.options().helpMode.get();
	}

	/** The explanation with what values are allowed and the default, e.g. "... 1 to 64 blocks, default 8." */
	public String helpText() {
		StringBuilder text = new StringBuilder(setting.description());
		if (setting instanceof NumberSetting number) {
			String unit = number.unit().isEmpty() ? "" : " " + number.unit();
			text.append(" Range ").append(number.format(number.min())).append(" to ").append(number.format(number.max())).append(unit)
					.append(", default ").append(defaultText(setting)).append('.');
		} else if (!(setting instanceof StringSetting)) {
			text.append(" Default: ").append(defaultText(setting)).append('.');
		}

		return text.toString();
	}

	/** Height of the explanation under the name in help mode, measured in {@link #preferredHeight}. */
	private int helpHeight;

	@Override
	public int preferredHeight(UiContext ui, int width) {
		if (!setting.isVisible() || collapsed.getAsBoolean()) {
			return 0;
		}

		helpHeight = helpMode() ? ui.wrappedHeightScaled(helpText(), width - 12, ui.small()) + 2 : 0;
		return (stacked() ? 32 : 20) + helpHeight + (error != null ? 10 : 0);
	}

	@Override
	protected void onLayout() {
		int right = x + width - 4;
		reset.setBounds(right - RESET, y + 4, RESET, RESET);
		int controlY = y + 15 + helpHeight;

		if (setting instanceof BooleanSetting) {
			control.setBounds(right - RESET - 4 - ToggleSwitch.WIDTH, y + 5, ToggleSwitch.WIDTH, ToggleSwitch.HEIGHT);
		} else if (setting instanceof KeySetting) {
			control.setBounds(right - RESET - 4 - KEY_WIDTH, y + 3, KEY_WIDTH, 14);
		} else if (setting instanceof NumberSetting) {
			control.setBounds(x + 7, controlY + 3, width - 14, 12);
			exact.setBounds(x + width - RESET - 10 - 58, y + 2, 58, 13);
		} else if (setting instanceof ColorSetting) {
			control.setBounds(x + 8, controlY + 2, width - 16, 10);
		} else {
			control.setBounds(x + 6, controlY, width - 12, 14);
		}
	}

	@Override
	public void render(UiContext ui) {
		if (!setting.isVisible() || collapsed.getAsBoolean()) {
			return;
		}

		boolean hot = ui.hovered(x, y, width, height);
		// No card per setting: rows sit on the panel, and only the hovered one is tinted.
		if (hot) {
			ui.roundRect(x, y, width, height, Theme.withAlpha(Theme.SURFACE_HOVER, 0.7f), Theme.RADIUS_CONTROL);
		}

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

		boolean changed = !setting.isDefault();
		ui.textClipped(setting.name(), x + 6, y + 5, labelRight - x - 6, Theme.TEXT);
		if (helpHeight > 0) {
			ui.wrappedScaled(helpText(), x + 6, y + 15, width - 12, ui.small(), Theme.TEXT_MUTED);
		} else if (hot && !valueHot) {
			ui.tooltip(setting, helpText());
		}

		if (error != null) {
			ui.textClipped(error, x + 6, y + height - 10, width - 12, Theme.ERROR);
		}

		control.render(ui);
		if (changed) {
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

	/** Opens every fold this row sits in (its section, "More options", Advanced), so it can be seen and used. */
	public void reveal() {
		revealer.run();
	}

	private Runnable revealer = () -> {
	};

	SettingRow revealedBy(Runnable revealer) {
		this.revealer = revealer;
		return this;
	}

	// ----- grouping: sections, "More options" and Advanced -----------------------------------

	/** Sections that start closed: details most people never need to see. Their headers stay; one click opens them. */
	public static final Set<String> COLLAPSED_BY_DEFAULT = Set.of("Health", "Style", "Keys", "Display");

	/**
	 * Sections that hold only tuning (update rates, limits): listed last, inside one "Advanced" fold that starts closed. The
	 * settings themselves are unchanged.
	 */
	public static final Set<String> ADVANCED_SECTIONS = Set.of("Performance", "Limits");

	/** Title of the fold that holds the {@link #ADVANCED_SECTIONS}. */
	public static final String ADVANCED = "Advanced";

	/** Title given to the settings before a module's first named section. */
	public static final String GENERAL = "General";

	/** Builds rows for every setting of a group, with a heading wherever a new section starts. */
	public static List<Widget> rowsFor(List<Setting<?>> all) {
		return rowsFor("", all);
	}

	/**
	 * Builds rows for a module's (or the options') settings:
	 * <ul>
	 * <li>settings before the first named section go under "General" (only when there are named sections);</li>
	 * <li>each section gets a collapsible heading, and its {@link Setting#isAdvanced() advanced} settings fold away behind
	 * "More options" at its end;</li>
	 * <li>{@link #ADVANCED_SECTIONS} come last, inside one "Advanced" fold.</li>
	 * </ul>
	 * {@code owner} (a module id, or "options") keys the remembered open/closed state of every fold.
	 */
	public static List<Widget> rowsFor(String owner, List<Setting<?>> all) {
		List<Setting<?>> settings = all.stream().filter(Setting::isListed).toList();
		boolean module = !owner.isEmpty() && !owner.equals("options");
		boolean anySection = settings.stream().anyMatch(setting -> setting.section() != null);

		// Sections in order of first appearance; the unnamed start becomes "General".
		java.util.LinkedHashMap<String, List<Setting<?>>> sections = new java.util.LinkedHashMap<>();
		for (Setting<?> setting : settings) {
			String section = setting.section() != null ? setting.section() : anySection ? GENERAL : "";
			sections.computeIfAbsent(section, key -> new ArrayList<>()).add(setting);
		}

		List<Widget> rows = new ArrayList<>();
		List<String> advancedSections = new ArrayList<>();
		for (var entry : sections.entrySet()) {
			if (module && ADVANCED_SECTIONS.contains(entry.getKey())) {
				advancedSections.add(entry.getKey());
				continue;
			}

			addSection(rows, owner, entry.getKey(), entry.getValue(), null, module && COLLAPSED_BY_DEFAULT.contains(entry.getKey()), module);
		}

		if (!advancedSections.isEmpty()) {
			List<Setting<?>> members = new ArrayList<>();
			advancedSections.forEach(section -> members.addAll(sections.get(section)));
			SectionHeader advanced = new SectionHeader(owner + "/" + ADVANCED, ADVANCED, members, true, null);
			advanced.caption = "Update rates, limits and other tuning";
			rows.add(advanced);
			for (String section : advancedSections) {
				addSection(rows, owner, section, sections.get(section), advanced, false, module);
			}
		}

		return rows;
	}

	private static void addSection(List<Widget> rows, String owner, String title, List<Setting<?>> members, SectionHeader parent, boolean closed,
			boolean module) {
		SectionHeader header = null;
		if (!title.isEmpty()) {
			header = new SectionHeader(owner + "/" + title, title, members, closed, parent);
			rows.add(header);
		}

		SectionHeader section = header;
		List<Setting<?>> everyday = module ? members.stream().filter(setting -> !setting.isAdvanced()).toList() : members;
		List<Setting<?>> extra = module ? members.stream().filter(Setting::isAdvanced).toList() : List.of();
		BooleanSupplier hidden = section == null ? () -> parent != null && parent.collapsed() : section::hidesContent;
		for (Setting<?> setting : everyday) {
			rows.add(row(setting, hidden, () -> {
				if (section != null) section.open();
			}));
		}

		if (!extra.isEmpty()) {
			MoreOptions more = new MoreOptions(owner + "/" + title + "/more", extra, hidden);
			rows.add(more);
			BooleanSupplier hiddenExtra = () -> hidden.getAsBoolean() || !more.open();
			for (Setting<?> setting : extra) {
				rows.add(row(setting, hiddenExtra, () -> {
					if (section != null) section.open();
					more.setOpen(true);
				}));
			}
		}
	}

	private static Widget row(Setting<?> setting, BooleanSupplier hidden, Runnable reveal) {
		return setting instanceof IdSetSetting ids ? new IdSetRow(ids).collapsedWhen(hidden) : new SettingRow(setting).collapsedWhen(hidden).revealedBy(reveal);
	}

	/**
	 * Marks every fold that holds {@code setting} as open, before its rows are built (the command palette uses this to
	 * jump straight to a setting).
	 */
	public static void revealLater(String owner, Setting<?> setting) {
		String section = setting.section() != null ? setting.section() : GENERAL;
		io.github.theamethyx.femboyclient.client.gui.GuiState.openSections.put(owner + "/" + section, true);
		if (ADVANCED_SECTIONS.contains(section)) {
			io.github.theamethyx.femboyclient.client.gui.GuiState.openSections.put(owner + "/" + ADVANCED, true);
		}

		if (setting.isAdvanced()) {
			io.github.theamethyx.femboyclient.client.gui.GuiState.openSections.put(owner + "/" + section + "/more", true);
		}
	}

	/**
	 * Heading above a section of settings, with a chevron: a click collapses or opens the section (remembered while the
	 * game runs). Hidden while none of its settings is visible, or while the fold it sits in is closed.
	 */
	public static final class SectionHeader extends Widget {
		private final String key;
		private final String title;
		private final List<Setting<?>> members;
		private final boolean closedByDefault;
		private final SectionHeader parent;
		private String caption;

		SectionHeader(String key, String title, List<Setting<?>> members, boolean closedByDefault, SectionHeader parent) {
			this.key = key;
			this.title = title;
			this.members = members;
			this.closedByDefault = closedByDefault;
			this.parent = parent;
		}

		public String title() {
			return title;
		}

		/** The fold this heading sits in (Advanced), or {@code null}. */
		public SectionHeader parent() {
			return parent;
		}

		public boolean collapsed() {
			Boolean open = io.github.theamethyx.femboyclient.client.gui.GuiState.openSections.get(key);
			return open == null ? closedByDefault : !open;
		}

		public void setCollapsed(boolean collapsed) {
			io.github.theamethyx.femboyclient.client.gui.GuiState.openSections.put(key, !collapsed);
		}

		/** Opens this section and the fold around it. */
		public void open() {
			setCollapsed(false);
			if (parent != null) {
				parent.open();
			}
		}

		/** Whether the settings under this heading are hidden (it, or the fold around it, is closed). */
		boolean hidesContent() {
			return collapsed() || parent != null && parent.collapsed();
		}

		private boolean shown() {
			return (parent == null || !parent.collapsed()) && members.stream().anyMatch(Setting::isVisible);
		}

		@Override
		public int preferredHeight(UiContext ui, int width) {
			return shown() ? (parent == null ? 20 : 15) : 0;
		}

		@Override
		public void render(UiContext ui) {
			if (!shown()) {
				return;
			}

			boolean hot = ui.hovered(x, y, width, height);
			boolean closed = collapsed();
			if (hot) {
				ui.pointer();
			}

			int indent = parent == null ? 0 : 8;
			int textY = y + height - 12;
			ui.icon(closed ? Icon.CHEVRON_RIGHT : Icon.CHEVRON_DOWN, x + 2 + indent, textY, 8, hot ? Theme.TEXT : Theme.TEXT_MUTED);
			int titleColor = parent == null ? Theme.TEXT : Theme.TEXT_SECONDARY;
			ui.text(title, x + 13 + indent, textY + 1, hot ? Theme.ACCENT_STRONG : titleColor);
			int after = x + 19 + indent + ui.textWidth(title);
			String note = closed ? members.size() + (members.size() == 1 ? " setting" : " settings") : caption;
			if (note != null && closed) {
				ui.textScaled(note, after, textY + 1 + (8 - 8 * ui.small()) / 2, ui.small(), Theme.TEXT_MUTED);
			}

			if (parent == null) {
				ui.rect(x + 2, y + height - 2, width - 4, 1, Theme.BORDER);
			}

			if (hot) {
				ui.tooltip(this, closed ? "Show the " + title + " settings." : "Hide the " + title + " settings.");
			}
		}

		@Override
		public boolean mouseClicked(UiContext ui, double mouseX, double mouseY, int button) {
			if (!shown() || !contains(mouseX, mouseY) || button != 0) {
				return false;
			}

			setCollapsed(!collapsed());
			return true;
		}
	}

	/** "More options (n)": shows or hides a section's advanced settings, right where they belong. */
	public static final class MoreOptions extends Widget {
		private final String key;
		private final List<Setting<?>> members;
		private final BooleanSupplier hidden;

		MoreOptions(String key, List<Setting<?>> members, BooleanSupplier hidden) {
			this.key = key;
			this.members = members;
			this.hidden = hidden;
		}

		public boolean open() {
			return Boolean.TRUE.equals(io.github.theamethyx.femboyclient.client.gui.GuiState.openSections.get(key));
		}

		public void setOpen(boolean open) {
			io.github.theamethyx.femboyclient.client.gui.GuiState.openSections.put(key, open);
		}

		private boolean shown() {
			return !hidden.getAsBoolean() && members.stream().anyMatch(Setting::isVisible);
		}

		@Override
		public int preferredHeight(UiContext ui, int width) {
			return shown() ? 14 : 0;
		}

		@Override
		public void render(UiContext ui) {
			if (!shown()) {
				return;
			}

			boolean hot = ui.hovered(x, y, width, height);
			String label = open() ? "Fewer options" : "More options (" + members.size() + ")";
			int color = hot ? Theme.ACCENT_STRONG : Theme.TEXT_SECONDARY;
			ui.icon(open() ? Icon.CHEVRON_DOWN : Icon.CHEVRON_RIGHT, x + 6, y + 3, 7, color);
			ui.text(label, x + 16, y + 3, color);
			if (hot) {
				ui.pointer();
				ui.tooltip(this, open() ? "Hide the less common settings of this section." : "Show " + members.size()
						+ " less common setting(s) of this section: " + String.join(", ", members.stream().map(Setting::name).toList()) + ".");
			}
		}

		@Override
		public boolean mouseClicked(UiContext ui, double mouseX, double mouseY, int button) {
			if (!shown() || !contains(mouseX, mouseY) || button != 0) {
				return false;
			}

			setOpen(!open());
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
