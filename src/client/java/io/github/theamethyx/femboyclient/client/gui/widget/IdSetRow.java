package io.github.theamethyx.femboyclient.client.gui.widget;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import io.github.theamethyx.femboyclient.client.gui.Icon;
import io.github.theamethyx.femboyclient.client.gui.Theme;
import io.github.theamethyx.femboyclient.client.gui.UiContext;
import io.github.theamethyx.femboyclient.core.setting.IdSetSetting;

/**
 * Editor for an {@link IdSetSetting}: a one-line summary that expands into a searchable checklist
 * grouped by category. Clicking a group header selects or clears the whole group.
 */
public class IdSetRow extends Container {
	private static final int HEADER = 20;
	private static final int TOOLBAR = 16;
	private static final int GROUP = 14;
	private static final int ITEM = 11;
	/** Expanded editors stay expanded while the panel is rebuilt. */
	private static final Set<IdSetSetting> EXPANDED = Collections.newSetFromMap(new WeakHashMap<>());

	private final IdSetSetting setting;
	private final TextField search;
	private final Button all;
	private final Button none;
	private final Button reset;
	private String filter = "";

	public IdSetRow(IdSetSetting setting) {
		this.setting = setting;
		search = add(new TextField("Filter", 32).icon(Icon.SEARCH).onChange(text -> filter = text.trim().toLowerCase(Locale.ROOT)));
		search.tooltip("Show only entries whose name or id contains this text.");
		all = add(new Button("All", Button.Style.SECONDARY, () -> setting.setAll(ids(filtered()), true)));
		all.tooltip("Select every entry shown below.");
		none = add(new Button("None", Button.Style.SECONDARY, () -> setting.setAll(ids(filtered()), false)));
		none.tooltip("Clear every entry shown below.");
		reset = add(new Button("", Button.Style.GHOST, setting::reset).icon(Icon.RESET));
		reset.tooltip("Reset '" + setting.name() + "' to its default selection.");
	}

	public IdSetSetting setting() {
		return setting;
	}

	public boolean expanded() {
		return EXPANDED.contains(setting);
	}

	public void setExpanded(boolean expanded) {
		if (expanded) {
			EXPANDED.add(setting);
		} else {
			EXPANDED.remove(setting);
		}
	}

	private List<IdSetSetting.Choice> filtered() {
		if (filter.isEmpty()) {
			return setting.choices();
		}

		List<IdSetSetting.Choice> result = new ArrayList<>();
		for (IdSetSetting.Choice choice : setting.choices()) {
			if (choice.label().toLowerCase(Locale.ROOT).contains(filter) || choice.id().contains(filter)) {
				result.add(choice);
			}
		}

		return result;
	}

	private static List<String> ids(List<IdSetSetting.Choice> choices) {
		return choices.stream().map(IdSetSetting.Choice::id).toList();
	}

	private Map<String, List<IdSetSetting.Choice>> grouped() {
		Map<String, List<IdSetSetting.Choice>> groups = new LinkedHashMap<>();
		for (IdSetSetting.Choice choice : filtered()) {
			groups.computeIfAbsent(choice.group(), g -> new ArrayList<>()).add(choice);
		}

		return groups;
	}

	private int columns() {
		return width >= 220 ? 3 : 2;
	}

	@Override
	public int preferredHeight(UiContext ui, int width) {
		if (!setting.isVisible()) {
			return 0;
		}

		if (!expanded()) {
			return HEADER;
		}

		int columns = width >= 220 ? 3 : 2;
		int body = TOOLBAR + 4;
		Map<String, List<IdSetSetting.Choice>> groups = grouped();
		for (List<IdSetSetting.Choice> items : groups.values()) {
			body += GROUP + ((items.size() + columns - 1) / columns) * ITEM + 3;
		}

		if (groups.isEmpty()) {
			body += 14;
		}

		return HEADER + body + 4;
	}

	@Override
	protected void onLayout() {
		int right = x + width - 4;
		reset.setBounds(right - 11, y + 4, 11, 11);
		int toolbarY = y + HEADER;
		int buttonWidth = 34;
		search.setBounds(x + 6, toolbarY, width - 12 - 2 * (buttonWidth + Theme.GAP), 14);
		all.setBounds(x + width - 6 - 2 * buttonWidth - Theme.GAP, toolbarY, buttonWidth, 14);
		none.setBounds(x + width - 6 - buttonWidth, toolbarY, buttonWidth, 14);
	}

	@Override
	public void render(UiContext ui) {
		if (!setting.isVisible()) {
			return;
		}

		boolean headerHot = ui.hovered(x, y, width - 18, HEADER);
		ui.roundRect(x, y, width, height, headerHot && !expanded() ? Theme.SURFACE_HOVER : Theme.SURFACE);

		int right = x + width - 4 - 11 - 6;
		String summary = setting.get().size() + " selected";
		ui.icon(Icon.CHEVRON_DOWN, right - 8, y + 6, 8, expanded() ? Theme.ACCENT : Theme.TEXT_SECONDARY);
		ui.textRight(summary, right - 12, y + 6, Theme.ACCENT_STRONG);
		ui.textClipped(setting.name(), x + 6, y + 6, right - 18 - ui.textWidth(summary) - x - 6, Theme.TEXT);
		if (headerHot) {
			ui.pointer();
			ui.tooltip(setting, setting.description() + "\n\nClick to " + (expanded() ? "collapse." : "choose entries."));
		}

		if (!setting.isDefault()) {
			reset.render(ui);
		}

		if (!expanded()) {
			return;
		}

		search.render(ui);
		all.render(ui);
		none.render(ui);

		int columns = columns();
		int columnWidth = (width - 12) / columns;
		int cy = y + HEADER + TOOLBAR + 4;
		Map<String, List<IdSetSetting.Choice>> groups = grouped();
		if (groups.isEmpty()) {
			ui.text("Nothing matches \"" + filter + "\".", x + 6, cy + 2, Theme.TEXT_MUTED);
		}

		for (Map.Entry<String, List<IdSetSetting.Choice>> group : groups.entrySet()) {
			List<IdSetSetting.Choice> items = group.getValue();
			long selected = items.stream().filter(c -> setting.contains(c.id())).count();
			boolean groupHot = ui.hovered(x + 4, cy, width - 8, GROUP - 2);
			if (groupHot) {
				ui.roundRect(x + 4, cy, width - 8, GROUP - 2, Theme.SURFACE_HOVER);
				ui.pointer();
				ui.tooltip(group.getKey(), selected == items.size() ? "Clear all " + group.getKey().toLowerCase(Locale.ROOT)
						: "Select all " + group.getKey().toLowerCase(Locale.ROOT));
			}

			checkbox(ui, x + 7, cy + 2, selected == items.size(), selected > 0 && selected < items.size());
			ui.text(group.getKey(), x + 18, cy + 2, Theme.TEXT);
			ui.textRight(selected + " / " + items.size(), x + width - 8, cy + 2, Theme.TEXT_MUTED);
			cy += GROUP;

			for (int i = 0; i < items.size(); i++) {
				IdSetSetting.Choice choice = items.get(i);
				int ix = x + 6 + (i % columns) * columnWidth;
				int iy = cy + (i / columns) * ITEM;
				boolean on = setting.contains(choice.id());
				boolean hot = ui.hovered(ix, iy, columnWidth - 2, ITEM);
				checkbox(ui, ix + 1, iy + 1, on, false);
				ui.textClipped(choice.label(), ix + 12, iy + 1, columnWidth - 14, hot ? Theme.TEXT : on ? Theme.TEXT_SECONDARY : Theme.TEXT_MUTED);
				if (hot) {
					ui.pointer();
					ui.tooltip(choice, choice.label() + "\n" + choice.id());
				}
			}

			cy += ((items.size() + columns - 1) / columns) * ITEM + 3;
		}
	}

	private static void checkbox(UiContext ui, int bx, int by, boolean on, boolean partial) {
		if (on) {
			ui.roundRect(bx, by, 8, 8, Theme.ACCENT);
			ui.icon(Icon.CHECK, bx, by, 8, 0xFF16161B);
		} else {
			ui.roundRect(bx, by, 8, 8, Theme.BORDER_STRONG);
			ui.roundRect(bx + 1, by + 1, 6, 6, Theme.SURFACE);
			if (partial) {
				ui.rect(bx + 2, by + 3, 4, 2, Theme.ACCENT);
			}
		}
	}

	@Override
	public boolean mouseClicked(UiContext ui, double mouseX, double mouseY, int button) {
		if (!setting.isVisible() || button != 0 || !contains(mouseX, mouseY)) {
			return false;
		}

		if (!setting.isDefault() && reset.mouseClicked(ui, mouseX, mouseY, button)) {
			return true;
		}

		if (mouseY < y + HEADER) {
			setExpanded(!expanded());
			return true;
		}

		if (!expanded()) {
			return false;
		}

		if (search.mouseClicked(ui, mouseX, mouseY, button) || all.mouseClicked(ui, mouseX, mouseY, button)
				|| none.mouseClicked(ui, mouseX, mouseY, button)) {
			return true;
		}

		int columns = columns();
		int columnWidth = (width - 12) / columns;
		int cy = y + HEADER + TOOLBAR + 4;
		for (Map.Entry<String, List<IdSetSetting.Choice>> group : grouped().entrySet()) {
			List<IdSetSetting.Choice> items = group.getValue();
			if (mouseY >= cy && mouseY < cy + GROUP - 2) {
				boolean allOn = items.stream().allMatch(c -> setting.contains(c.id()));
				setting.setAll(ids(items), !allOn);
				return true;
			}

			cy += GROUP;
			int rows = (items.size() + columns - 1) / columns;
			if (mouseY >= cy && mouseY < cy + rows * ITEM) {
				int index = (int) ((mouseY - cy) / ITEM) * columns + (int) Math.max(0, Math.min(columns - 1, (mouseX - x - 6) / columnWidth));
				if (index < items.size()) {
					setting.toggle(items.get(index).id());
				}

				return true;
			}

			cy += rows * ITEM + 3;
		}

		return true;
	}
}
