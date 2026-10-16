package io.github.theamethyx.femboyclient.client.gui.widget;

import java.util.ArrayList;
import java.util.List;

import io.github.theamethyx.femboyclient.client.FemboyClient;
import io.github.theamethyx.femboyclient.client.gui.Anim;
import io.github.theamethyx.femboyclient.client.gui.Icon;
import io.github.theamethyx.femboyclient.client.gui.Theme;
import io.github.theamethyx.femboyclient.client.gui.UiContext;
import io.github.theamethyx.femboyclient.client.module.ClientHooks;
import io.github.theamethyx.femboyclient.core.module.Module;
import io.github.theamethyx.femboyclient.core.module.ModuleManager;

/**
 * The side panel that opens when a module is right-clicked: name, switch and description on top, then three tabs:
 * <b>Settings</b> (all settings and the module's own controls), <b>Keybinds</b> (key, toggle or hold) and <b>Info</b>
 * (tier, status, why it is paused), and a Reset button at the bottom.
 */
public class ModuleSettingsPanel extends Container {
	public static final String[] TABS = {"Settings", "Keybinds", "Info"};
	private static final int TAB_HEIGHT = 16;
	private static final int FOOTER = 22;

	private final UiContext ui;
	private final ModuleManager modules;
	private final Runnable onClose;
	private final ScrollList content;
	private final Button reset;
	private final Anim tabLine = new Anim(0, 20);
	private ToggleSwitch toggle;
	private Module module;
	private int tab;

	public ModuleSettingsPanel(UiContext ui, ModuleManager modules, Runnable onClose) {
		this.ui = ui;
		this.modules = modules;
		this.onClose = onClose;
		content = add(new ScrollList(ui, 3, 0));
		reset = add(new Button("Reset Module", Button.Style.SECONDARY, () -> {
			if (module != null) {
				modules.resetToDefaults(module);
				rebuild();
			}
		}).icon(Icon.RESET));
		reset.tooltip("Restore this module's settings, key, key mode and on/off state to the defaults.");
	}

	public Module module() {
		return module;
	}

	public int tab() {
		return tab;
	}

	public void show(Module module) {
		boolean same = module == this.module;
		this.module = module;
		children.remove(toggle);
		toggle = add(new ToggleSwitch(module::isEnabled, value -> modules.setEnabled(module, value)));
		if (!same) {
			tab = 0;
			tabLine.snap(0);
		}

		rebuild();
		layout();
	}

	public void showTab(int index) {
		tab = Math.max(0, Math.min(TABS.length - 1, index));
		rebuild();
	}

	private String description() {
		return module.testEnvironmentOnly() ? module.description() + " " + Module.TEST_ENVIRONMENT_ONLY : module.description();
	}

	private int headerHeight() {
		return module == null ? 0 : 32 + ui.wrappedHeight(description(), width - 16) + 6;
	}

	private void layout() {
		if (module == null) {
			return;
		}

		int top = y + headerHeight();
		toggle.setBounds(x + width - ToggleSwitch.WIDTH - 22, y + 9, ToggleSwitch.WIDTH, ToggleSwitch.HEIGHT);
		content.setBounds(x + 6, top + TAB_HEIGHT + 6, width - 12, height - (top - y) - TAB_HEIGHT - 6 - FOOTER - 4);
		reset.setBounds(x + 6, y + height - FOOTER, width - 12, 16);
	}

	@Override
	protected void onLayout() {
		layout();
	}

	private void rebuild() {
		if (module == null) {
			return;
		}

		List<Widget> rows = new ArrayList<>();
		switch (tab) {
			case 0 -> {
				if (module.settings().isEmpty()) {
					rows.add(new Note("This module has no settings.", Theme.TEXT_MUTED));
				} else {
					rows.addAll(SettingRow.rowsFor(module.settings().all()));
				}

				if (module instanceof ClientHooks.CustomControls custom) {
					rows.addAll(custom.createControls(ui, this::rebuild));
				}
			}
			case 1 -> {
				rows.add(new KeyRow(module));
				rows.add(new Note("Click the key field and press a key; Backspace removes it. Toggle turns the module on or off with each press, "
						+ "Hold keeps it on only while the key is held.", Theme.TEXT_MUTED));
				List<Module> shared = FemboyClient.get().keybinds().modulesBoundTo(module.key());
				shared.remove(module);
				if (module.key() != Module.NO_KEY && !shared.isEmpty()) {
					rows.add(new Note("Also bound to this key: " + String.join(", ", shared.stream().map(Module::name).toList()), Theme.WARNING));
				}
			}
			default -> {
				Module.Tier tier = module.tier();
				rows.add(new Note("Tier: " + tier.label() + ". " + tier.meaning(), tierColor(tier)));
				ModuleRow.StatusView status = ModuleRow.status(module, modules);
				rows.add(new Note("Status: " + status.text(), status.color()));
				rows.add(new Note("Category: " + module.category().label() + "   Id: " + module.id(), Theme.TEXT_SECONDARY));
				rows.add(new Note(module.settings().all().size() + " settings. Changes are saved to the active profile right away.", Theme.TEXT_MUTED));
			}
		}

		content.setChildren(rows);
		content.scrollToTop();
	}

	private static int tierColor(Module.Tier tier) {
		return tier == Module.Tier.SANDBOX ? Theme.WARNING : tier == Module.Tier.AUTOMATION ? Theme.INFO : Theme.ACCENT_STRONG;
	}

	@Override
	public void render(UiContext ui) {
		if (module == null) {
			return;
		}

		ui.shadow(x, y, width, height, 4, 1);
		ui.roundRect(x, y, width, height, Theme.PANEL, 4);
		ui.outline(x, y, width, height, Theme.BORDER);

		// Header: icon, name, badges, description.
		ui.roundRect(x + 8, y + 7, 20, 20, Theme.withAlpha(Theme.ACCENT, 0.3f), 4);
		ui.icon(Icon.byName(module.category().icon()), x + 13, y + 12, 10, 0xFFFFFFFF);
		ui.textScaled(ui.ellipsize(module.name(), Math.round((width - 90) / 1.25f)), x + 33, y + 6, 1.25f, Theme.TEXT);
		Module.Tier tier = module.tier();
		int badgeX = x + 33;
		int categoryWidth = Math.round(ui.textWidth(module.category().label()) * 0.75f) + 8;
		ui.roundRect(badgeX, y + 18, categoryWidth, 10, Theme.withAlpha(Theme.ACCENT, 0.25f), 2);
		ui.textScaled(module.category().label(), badgeX + 4, y + 19.5f, 0.75f, Theme.ACCENT_STRONG);
		int tierWidth = Math.round(ui.textWidth(tier.label()) * 0.75f) + 8;
		ui.roundRect(badgeX + categoryWidth + 3, y + 18, tierWidth, 10, Theme.withAlpha(tierColor(tier), 0.18f), 2);
		ui.textScaled(tier.label(), badgeX + categoryWidth + 7, y + 19.5f, 0.75f, tierColor(tier));
		if (ui.hovered(badgeX + categoryWidth + 3, y + 18, tierWidth, 10)) {
			ui.tooltip(this, tier.label() + ": " + tier.meaning());
		}

		ui.wrapped(description(), x + 8, y + 32, width - 16, Theme.TEXT_SECONDARY);

		boolean closeHot = ui.hovered(x + width - 16, y + 6, 12, 12);
		ui.icon(Icon.CLOSE, x + width - 14, y + 9, 7, closeHot ? Theme.TEXT : Theme.TEXT_MUTED);
		if (closeHot) {
			ui.pointer();
			ui.tooltip("close-panel", "Close (Esc)");
		}

		// Tabs with a sliding underline.
		int tabsY = y + headerHeight();
		int tabWidth = (width - 12) / TABS.length;
		ui.roundRect(x + 6, tabsY, width - 12, TAB_HEIGHT, Theme.SURFACE, 3);
		float line = tabLine.update(tab, ui.frameSeconds(), ui.animate());
		ui.roundRect(x + 6 + Math.round(line * tabWidth), tabsY, tabWidth, TAB_HEIGHT, Theme.withAlpha(Theme.ACCENT, 0.85f), 3);
		for (int i = 0; i < TABS.length; i++) {
			int tx = x + 6 + i * tabWidth;
			boolean hot = ui.hovered(tx, tabsY, tabWidth, TAB_HEIGHT);
			ui.textCentered(TABS[i], tx + tabWidth / 2, tabsY + 4, i == tab ? 0xFFFFFFFF : hot ? Theme.TEXT : Theme.TEXT_SECONDARY);
			if (hot && i != tab) {
				ui.pointer();
			}
		}

		ui.pushClip(content.x(), content.y(), content.width(), content.height());
		content.render(ui);
		ui.popClip();
		toggle.render(ui);
		reset.render(ui);
	}

	@Override
	public boolean mouseClicked(UiContext ui, double mouseX, double mouseY, int button) {
		if (module == null || !contains(mouseX, mouseY)) {
			return false;
		}

		if (UiContext.inside(mouseX, mouseY, x + width - 16, y + 6, 12, 12)) {
			onClose.run();
			return true;
		}

		int tabsY = y + headerHeight();
		int tabWidth = (width - 12) / TABS.length;
		if (UiContext.inside(mouseX, mouseY, x + 6, tabsY, tabWidth * TABS.length, TAB_HEIGHT)) {
			showTab((int) ((mouseX - x - 6) / tabWidth));
			return true;
		}

		super.mouseClicked(ui, mouseX, mouseY, button);
		return true;
	}

	/** Key field and Toggle/Hold switch. */
	private final class KeyRow extends Container {
		private final KeybindButton key;
		private final Button mode;

		KeyRow(Module module) {
			key = add(new KeybindButton(module::key, code -> {
				modules.setKey(module, code);
				rebuild();
			}));
			key.tooltip("Key for this module while playing. Click, then press a key; Backspace removes it.");
			mode = add(new Button(() -> module.isHoldKey() ? "Hold" : "Toggle", Button.Style.SECONDARY,
					() -> modules.setHoldKey(module, !module.isHoldKey())));
			mode.tooltip(() -> module.isHoldKey() ? "Hold: on only while the key is held. Click for Toggle." : "Toggle: each press turns it on or off. Click for Hold.");
		}

		@Override
		public int preferredHeight(UiContext ui, int width) {
			return 30;
		}

		@Override
		protected void onLayout() {
			key.setBounds(x + 4, y + 13, width - 60, 14);
			mode.setBounds(x + width - 52, y + 13, 48, 14);
		}

		@Override
		public void render(UiContext ui) {
			ui.roundRect(x, y, width, height, Theme.SURFACE, 3);
			ui.text("Key", x + 5, y + 3, Theme.TEXT);
			super.render(ui);
		}
	}

	/** A wrapped line of text. */
	public static final class Note extends Widget {
		private final String text;
		private final int color;

		public Note(String text, int color) {
			this.text = text;
			this.color = color;
		}

		@Override
		public int preferredHeight(UiContext ui, int width) {
			return ui.wrappedHeight(text, width - 8) + 6;
		}

		@Override
		public void render(UiContext ui) {
			ui.wrapped(text, x + 4, y + 3, width - 8, color);
		}
	}
}
