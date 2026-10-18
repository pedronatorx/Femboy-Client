package io.github.theamethyx.femboyclient.client.gui.page;

import java.util.List;

import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;

import io.github.theamethyx.femboyclient.client.gui.GuiState;
import io.github.theamethyx.femboyclient.client.gui.Icon;
import io.github.theamethyx.femboyclient.client.gui.Theme;
import io.github.theamethyx.femboyclient.client.gui.UiContext;

/**
 * Settings, in tabs: General (options, backups and files), Appearance (Theme Studio), Performance (Performance Center),
 * Rules, Timeline, Help (guides and glossary) and Developer (module health, tools and the log). Tabs show only their icon
 * when the panel is narrow.
 */
public class SettingsPage extends Page {
	private static final int TAB_HEIGHT = 16;

	private final List<Page> tabs;

	public SettingsPage(UiContext ui) {
		super(ui);
		tabs = List.of(new ConfigPage(ui), new ThemeStudioPage(ui), new PerformanceCenterPage(ui), new RulesPage(ui), new TimelinePage(ui),
				new HelpPage(ui), new DiagnosticsPage(ui));
	}

	private Page active() {
		return tabs.get(Math.clamp(GuiState.settingsTab, 0, tabs.size() - 1));
	}

	/** Switches tab by index (0 = General). */
	public void showTab(int tab) {
		if (tab != GuiState.settingsTab && tab >= 0 && tab < tabs.size()) {
			ui.focus(null);
			GuiState.settingsTab = tab;
			active().onShow();
			onLayout();
		}
	}

	/** Switches to the tab with this label (General, Appearance, Performance, Rules, Timeline, Help, Developer). */
	public void showTab(String label) {
		for (int i = 0; i < tabs.size(); i++) {
			if (tabs.get(i).navLabel().equalsIgnoreCase(label)) {
				showTab(i);
				return;
			}
		}
	}

	public Page activeTab() {
		return active();
	}

	public List<String> tabLabels() {
		return tabs.stream().map(Page::navLabel).toList();
	}

	@Override
	public String title() {
		return "Settings";
	}

	@Override
	public String subtitle() {
		return active().subtitle();
	}

	@Override
	public Icon icon() {
		return Icon.SETTINGS;
	}

	@Override
	public void onShow() {
		active().onShow();
	}

	@Override
	public void tick() {
		active().tick();
	}

	@Override
	protected void onLayout() {
		active().setBounds(x, y + TAB_HEIGHT + Theme.GAP, width, height - TAB_HEIGHT - Theme.GAP);
	}

	/** Whether every tab label fits; when not, only the selected tab keeps its label and the others show their icon. */
	private boolean compact(UiContext ui) {
		int total = 0;
		for (Page tab : tabs) {
			total += ui.textWidth(tab.navLabel()) + 22 + Theme.GAP;
		}

		return total > width;
	}

	private int tabWidth(UiContext ui, int index, boolean compact) {
		return compact && index != GuiState.settingsTab ? 22 : ui.textWidth(tabs.get(index).navLabel()) + 22;
	}

	@Override
	public void render(UiContext ui) {
		boolean compact = compact(ui);
		int tx = x;
		for (int i = 0; i < tabs.size(); i++) {
			Page tab = tabs.get(i);
			int w = tabWidth(ui, i, compact);
			boolean selected = i == GuiState.settingsTab;
			boolean hot = ui.hovered(tx, y, w, TAB_HEIGHT);
			if (selected || hot) {
				ui.roundRect(tx, y, w, TAB_HEIGHT, selected ? Theme.ACCENT_TINT : Theme.SURFACE_HOVER);
			}

			int color = selected ? Theme.ACCENT_STRONG : hot ? Theme.TEXT : Theme.TEXT_SECONDARY;
			ui.icon(tab.icon(), tx + 7, y + 4, 8, color);
			if (!compact || selected) {
				ui.text(tab.navLabel(), tx + 18, y + 4, color);
			}

			if (selected) {
				ui.rect(tx + 4, y + TAB_HEIGHT - 2, w - 8, 2, Theme.ACCENT);
			}

			if (hot) {
				ui.tooltip(tab, tab.navLabel() + ": " + tab.subtitle());
				if (!selected) {
					ui.pointer();
				}
			}

			tx += w + Theme.GAP;
		}

		ui.rect(x, y + TAB_HEIGHT, width, 1, Theme.BORDER);
		active().render(ui);
	}

	@Override
	public boolean mouseClicked(UiContext ui, double mouseX, double mouseY, int button) {
		if (mouseY >= y && mouseY < y + TAB_HEIGHT) {
			boolean compact = compact(ui);
			int tx = x;
			for (int i = 0; i < tabs.size(); i++) {
				int w = tabWidth(ui, i, compact);
				if (mouseX >= tx && mouseX < tx + w) {
					showTab(i);
					return true;
				}

				tx += w + Theme.GAP;
			}
		}

		return active().mouseClicked(ui, mouseX, mouseY, button);
	}

	@Override
	public boolean mouseScrolled(UiContext ui, double mouseX, double mouseY, double amount) {
		return active().mouseScrolled(ui, mouseX, mouseY, amount);
	}

	@Override
	public boolean keyPressed(UiContext ui, KeyEvent event) {
		return active().keyPressed(ui, event);
	}

	@Override
	public boolean charTyped(UiContext ui, CharacterEvent event) {
		return active().charTyped(ui, event);
	}

	@Override
	public <W extends io.github.theamethyx.femboyclient.client.gui.widget.Widget> List<W> descendants(Class<W> type) {
		return active().descendants(type);
	}
}
