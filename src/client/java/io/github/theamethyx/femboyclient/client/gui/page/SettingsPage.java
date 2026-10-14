package io.github.theamethyx.femboyclient.client.gui.page;

import java.util.List;

import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;

import io.github.theamethyx.femboyclient.client.gui.GuiState;
import io.github.theamethyx.femboyclient.client.gui.Icon;
import io.github.theamethyx.femboyclient.client.gui.Theme;
import io.github.theamethyx.femboyclient.client.gui.UiContext;

/** Settings with two tabs: General (profiles, options, authorized servers, files) and Diagnostics. */
public class SettingsPage extends Page {
	private static final int TAB_HEIGHT = 16;
	private static final List<String> TABS = List.of("General", "Diagnostics");

	private final ConfigPage general;
	private final DiagnosticsPage diagnostics;

	public SettingsPage(UiContext ui) {
		super(ui);
		general = new ConfigPage(ui);
		diagnostics = new DiagnosticsPage(ui);
	}

	private Page active() {
		return GuiState.settingsTab == 1 ? diagnostics : general;
	}

	/** Switches tab (0 = General, 1 = Diagnostics). */
	public void showTab(int tab) {
		if (tab != GuiState.settingsTab && tab >= 0 && tab < TABS.size()) {
			ui.focus(null);
			GuiState.settingsTab = tab;
			active().onShow();
			onLayout();
		}
	}

	public Page activeTab() {
		return active();
	}

	@Override
	public String title() {
		return "Settings";
	}

	@Override
	public String subtitle() {
		return GuiState.settingsTab == 1 ? diagnostics.subtitle() : general.subtitle();
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

	private int tabWidth(UiContext ui, int index) {
		return ui.textWidth(TABS.get(index)) + 16;
	}

	@Override
	public void render(UiContext ui) {
		int tx = x;
		for (int i = 0; i < TABS.size(); i++) {
			int w = tabWidth(ui, i);
			boolean selected = i == GuiState.settingsTab;
			boolean hot = ui.hovered(tx, y, w, TAB_HEIGHT);
			if (selected || hot) {
				ui.roundRect(tx, y, w, TAB_HEIGHT, selected ? Theme.ACCENT_TINT : Theme.SURFACE_HOVER);
			}

			ui.text(TABS.get(i), tx + 8, y + 4, selected ? Theme.ACCENT_STRONG : hot ? Theme.TEXT : Theme.TEXT_SECONDARY);
			if (selected) {
				ui.rect(tx + 4, y + TAB_HEIGHT - 2, w - 8, 2, Theme.ACCENT);
			}

			if (hot && !selected) {
				ui.pointer();
			}

			tx += w + Theme.GAP;
		}

		ui.rect(x, y + TAB_HEIGHT, width, 1, Theme.BORDER);
		active().render(ui);
	}

	@Override
	public boolean mouseClicked(UiContext ui, double mouseX, double mouseY, int button) {
		if (mouseY >= y && mouseY < y + TAB_HEIGHT) {
			int tx = x;
			for (int i = 0; i < TABS.size(); i++) {
				int w = tabWidth(ui, i);
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
