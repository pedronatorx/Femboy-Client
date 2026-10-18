package io.github.theamethyx.femboyclient.client.gui.widget;

import java.util.ArrayList;
import java.util.List;

import io.github.theamethyx.femboyclient.client.FemboyClient;
import io.github.theamethyx.femboyclient.client.gui.GuiState;
import io.github.theamethyx.femboyclient.client.gui.Icon;
import io.github.theamethyx.femboyclient.client.gui.ModuleStatus;
import io.github.theamethyx.femboyclient.client.gui.Theme;
import io.github.theamethyx.femboyclient.client.gui.UiContext;
import io.github.theamethyx.femboyclient.client.module.ClientHooks;
import io.github.theamethyx.femboyclient.core.module.Module;
import io.github.theamethyx.femboyclient.core.module.ModuleManager;

/**
 * Everything about one module, the same wherever a module is opened:
 * <ul>
 * <li>a fixed header: name, favorite star and on/off switch; category and kind; what it does; its status in words;</li>
 * <li>a scrolling body: its key, a live preview for ESP modules, its settings grouped into sections (rarely used ones
 * folded behind "More options" and "Advanced"), its own controls, and an "About" fold with the technical details;</li>
 * <li>a footer with "Reset to defaults", which asks for a second click.</li>
 * </ul>
 */
public class ModuleSettingsPanel extends Container {
	private static final int FOOTER = 20;

	private final UiContext ui;
	private final ModuleManager modules;
	private final ScrollList content;
	private final ConfirmButton reset;
	private final FavoriteStar star;
	private final HelpToggle help;
	private ToggleSwitch toggle;
	private Module module;
	private boolean helpShown;

	public ModuleSettingsPanel(UiContext ui, ModuleManager modules) {
		this.ui = ui;
		this.modules = modules;
		content = add(new ScrollList(ui, 1, 0));
		reset = add(new ConfirmButton("Reset to defaults", "Click again to reset this module", Button.Style.GHOST, () -> {
			if (module != null) {
				modules.resetToDefaults(module);
				rebuild();
			}
		}));
		reset.icon(Icon.RESET);
		reset.tooltip("Put this module's settings, key and on/off state back to how they were after installing. Asks for a second click.");
		star = add(new FavoriteStar());
		help = add(new HelpToggle());
	}

	public Module module() {
		return module;
	}

	public void show(Module module) {
		boolean same = module == this.module;
		this.module = module;
		children.remove(toggle);
		toggle = add(new ToggleSwitch(module::isEnabled, value -> modules.setEnabled(module, value)));
		toggle.tooltip(() -> module.isEnabled() ? "On. Click to turn " + module.name() + " off." : "Off. Click to turn " + module.name() + " on.");
		rebuild();
		layout();
		if (!same) {
			content.scrollToTop();
		}
	}

	/** Rebuilds the body (after a reset, or when a module's own controls change). */
	public void rebuild() {
		if (module == null) {
			return;
		}

		helpShown = FemboyClient.get().options().helpMode.get();
		List<Widget> rows = new ArrayList<>();
		rows.add(new KeyRow(module));
		if (EspPreview.applies(module.settings())) {
			rows.add(new EspPreview(module.settings()));
		}

		if (module.settings().all().stream().noneMatch(setting -> setting.isListed())) {
			rows.add(new Note("Nothing to set up: turn it on and it works.", Theme.TEXT_MUTED));
		} else {
			rows.addAll(SettingRow.rowsFor(module.id(), module.settings().all()));
		}

		if (module instanceof ClientHooks.CustomControls custom) {
			rows.addAll(custom.createControls(ui, this::rebuildKeepScroll));
		}

		rows.add(new AboutFold());
		rows.addAll(aboutNotes());
		content.setChildren(rows);
	}

	private void rebuildKeepScroll() {
		rebuild();
	}

	private String description() {
		return module.testEnvironmentOnly() ? module.description() + " " + Module.TEST_ENVIRONMENT_ONLY : module.description();
	}

	/** "Visuals  ·  Shows information": the section group and, in plain words, what kind of module it is. */
	private String kindLabel() {
		String kind = switch (module.tier()) {
			case VISUAL -> "Shows information";
			case ASSIST -> "Assists";
			case AUTOMATION -> "Acts for you";
			case SANDBOX -> "Test environments only";
		};
		return io.github.theamethyx.femboyclient.client.gui.page.ModulesPage.groupLabel(module.category()) + "  ·  " + kind;
	}

	/** Height of the fixed header for the current width. */
	private int headerHeight() {
		if (module == null) {
			return 0;
		}

		ModuleStatus status = ModuleStatus.of(module, modules);
		int h = 8 + 12 + 11 + ui.wrappedHeight(description(), width - 16) + 4 + 14;
		if (!status.detail().isEmpty()) {
			h += ui.wrappedHeightScaled(status.detail(), width - 16, ui.small()) + 2;
		}

		return h + 4;
	}

	private int layoutHeader = -1;

	private void layout() {
		if (module == null) {
			return;
		}

		int header = headerHeight();
		layoutHeader = header;
		toggle.setBounds(x + width - ToggleSwitch.WIDTH - 8, y + 9, ToggleSwitch.WIDTH, ToggleSwitch.HEIGHT);
		star.setBounds(x + width - ToggleSwitch.WIDTH - 26, y + 7, 14, 14);
		help.setBounds(x + width - ToggleSwitch.WIDTH - 42, y + 7, 14, 14);
		content.setBounds(x + 4, y + header, width - 8, height - header - FOOTER - 4);
		reset.setBounds(x + 6, y + height - FOOTER, Math.min(width - 12, reset.naturalWidth(ui) + 8), 16);
	}

	@Override
	protected void onLayout() {
		layout();
	}

	@Override
	public void render(UiContext ui) {
		if (module == null) {
			return;
		}

		if (layoutHeader != headerHeight()) {
			// The status line changed length (e.g. a reason appeared); move the body to fit.
			layout();
		}

		if (helpShown != FemboyClient.get().options().helpMode.get()) {
			rebuild();
		}

		ui.roundRect(x, y, width, height, Theme.withAlpha(Theme.SURFACE, 0.55f), Theme.RADIUS_PANEL);

		// Header: name, kind, description, status.
		int left = x + 8;
		ui.textScaled(ui.ellipsize(module.name(), Math.round((width - 70) / Theme.TEXT_TITLE)), left, y + 8, Theme.TEXT_TITLE, Theme.TEXT);
		int ty = y + 21;
		ui.textScaled(kindLabel(), left, ty, ui.small(), Theme.TEXT_MUTED);
		if (ui.hovered(left, ty, Math.round(ui.textWidth(kindLabel()) * ui.small()), 8)) {
			ui.tooltip(this, module.tier().label() + ": " + module.tier().meaning());
		}

		ty += 11;
		ty += ui.wrapped(description(), left, ty, width - 16, Theme.TEXT_SECONDARY) + 4;
		ModuleStatus status = ModuleStatus.of(module, modules);
		status.drawPill(ui, left, ty);
		String key = module.key() == Module.NO_KEY ? null : KeybindButton.keyName(module.key()) + (module.isHoldKey() ? " (hold)" : "");
		if (key != null) {
			ui.icon(Icon.KEY, left + status.label().length() * 6 + 26, ty + 2, 8, Theme.TEXT_MUTED);
			ui.text(key, left + status.label().length() * 6 + 37, ty + 2, Theme.TEXT_SECONDARY);
		}

		ty += 14;
		if (!status.detail().isEmpty()) {
			ui.wrappedScaled(status.detail(), left, ty, width - 16, ui.small(), status.color());
		}

		ui.rect(x + 6, content.y() - 2, width - 12, 1, Theme.BORDER);
		ui.pushClip(content.x(), content.y(), content.width(), content.height());
		content.render(ui);
		ui.popClip();
		toggle.render(ui);
		star.render(ui);
		help.render(ui);
		reset.render(ui);
	}

	@Override
	public boolean mouseClicked(UiContext ui, double mouseX, double mouseY, int button) {
		if (module == null || !contains(mouseX, mouseY)) {
			return false;
		}

		super.mouseClicked(ui, mouseX, mouseY, button);
		return true;
	}

	// ----- parts -----------------------------------------------------------------------------

	/** Star in the header: marks the module as a favorite (Home, the search and the quick toggle wheel). */
	private final class FavoriteStar extends Widget {
		@Override
		public void render(UiContext ui) {
			if (module == null) {
				return;
			}

			boolean favorite = FemboyClient.get().quickAccess().isFavorite(module);
			boolean hot = hovered(ui);
			ui.icon(Icon.STAR, x + 2, y + 2, 10, favorite ? Theme.WARNING : hot ? Theme.TEXT : Theme.TEXT_MUTED);
			if (hot) {
				ui.pointer();
				ui.tooltip(this, favorite ? "Favorite. Click to remove it from your favorites."
						: "Add to favorites: shown on Home, first in the search and on the quick toggle wheel.");
			}
		}

		@Override
		public boolean mouseClicked(UiContext ui, double mouseX, double mouseY, int button) {
			if (module == null || button != 0 || !contains(mouseX, mouseY)) {
				return false;
			}

			FemboyClient.get().quickAccess().toggleFavorite(module);
			return true;
		}
	}

	/** "?" in the header: turns help mode (explanations under every setting) on or off. */
	private final class HelpToggle extends Widget {
		@Override
		public void render(UiContext ui) {
			boolean on = FemboyClient.get().options().helpMode.get();
			boolean hot = hovered(ui);
			if (on) {
				ui.roundRect(x, y, width, height, Theme.ACCENT_TINT, Theme.RADIUS_CONTROL);
			}

			ui.icon(Icon.INFO, x + 2, y + 2, 10, on ? Theme.ACCENT_STRONG : hot ? Theme.TEXT : Theme.TEXT_MUTED);
			if (hot) {
				ui.pointer();
				ui.tooltip(this, on ? "Help mode is on: every setting shows its explanation. Click for a compact list."
						: "Help mode is off. Click to show every setting's explanation, range and default.");
			}
		}

		@Override
		public boolean mouseClicked(UiContext ui, double mouseX, double mouseY, int button) {
			if (button != 0 || !contains(mouseX, mouseY)) {
				return false;
			}

			var option = FemboyClient.get().options().helpMode;
			option.set(!option.get());
			return true;
		}
	}

	/** The key that turns the module on or off while playing, and whether it toggles or must be held. */
	private final class KeyRow extends Container {
		private final KeybindButton key;
		private final Dropdown<Boolean> mode;
		private final Module module;

		KeyRow(Module module) {
			this.module = module;
			key = add(new KeybindButton(module::key, code -> modules.setKey(module, code)));
			key.tooltip("Click, then press the key that turns " + module.name() + " on or off while playing. Backspace removes it; Escape cancels.");
			mode = add(new Dropdown<>(() -> List.of(false, true), module::isHoldKey, hold -> modules.setHoldKey(module, hold),
					hold -> hold ? "Hold" : "Toggle"));
			mode.tooltip("Toggle: each press turns the module on or off. Hold: it is on only while you hold the key.");
		}

		private String conflict() {
			if (module.key() == Module.NO_KEY) {
				return null;
			}

			List<Module> shared = FemboyClient.get().keybinds().modulesBoundTo(module.key());
			shared.remove(module);
			return shared.isEmpty() ? null : "This key also switches " + String.join(", ", shared.stream().map(Module::name).toList()) + ".";
		}

		@Override
		public int preferredHeight(UiContext ui, int width) {
			String conflict = conflict();
			return 22 + (conflict == null ? 0 : ui.wrappedHeightScaled(conflict, width - 12, ui.small()) + 2);
		}

		@Override
		protected void onLayout() {
			int modeWidth = 58;
			key.setBounds(x + 44, y + 4, width - 44 - modeWidth - 10, 14);
			mode.setBounds(x + width - modeWidth - 4, y + 4, modeWidth, 14);
		}

		@Override
		public void render(UiContext ui) {
			ui.text("Key", x + 6, y + 7, Theme.TEXT);
			String conflict = conflict();
			if (conflict != null) {
				ui.icon(Icon.WARNING, x + 6, y + 21, 7, Theme.WARNING);
				ui.wrappedScaled(conflict, x + 16, y + 21, width - 22, ui.small(), Theme.WARNING);
			}

			super.render(ui);
		}
	}

	/** "About this module": closed by default; holds the details most people never need. */
	private final class AboutFold extends Widget {
		@Override
		public int preferredHeight(UiContext ui, int width) {
			return 20;
		}

		boolean open() {
			return Boolean.TRUE.equals(GuiState.openSections.get(module.id() + "/About"));
		}

		@Override
		public void render(UiContext ui) {
			boolean hot = hovered(ui);
			ui.icon(open() ? Icon.CHEVRON_DOWN : Icon.CHEVRON_RIGHT, x + 2, y + 8, 8, hot ? Theme.TEXT : Theme.TEXT_MUTED);
			ui.text("About this module", x + 13, y + 9, hot ? Theme.ACCENT_STRONG : Theme.TEXT);
			ui.rect(x + 2, y + height - 2, width - 4, 1, Theme.BORDER);
			if (hot) {
				ui.pointer();
			}
		}

		@Override
		public boolean mouseClicked(UiContext ui, double mouseX, double mouseY, int button) {
			if (button != 0 || !contains(mouseX, mouseY)) {
				return false;
			}

			GuiState.openSections.put(module.id() + "/About", !open());
			return true;
		}
	}

	private List<Widget> aboutNotes() {
		List<Widget> notes = new ArrayList<>();
		java.util.function.BooleanSupplier closed = () -> !Boolean.TRUE.equals(GuiState.openSections.get(module.id() + "/About"));
		Module.Tier tier = module.tier();
		notes.add(new Note(tier.label() + ": " + tier.meaning(), Theme.TEXT_SECONDARY).hiddenWhen(closed));
		String limitation = module.lowImpactLimitation();
		if (limitation != null) {
			notes.add(new Note("In Low-impact mode it pauses, because " + limitation, Theme.TEXT_SECONDARY).hiddenWhen(closed));
		}

		for (Module.Requirement requirement : module.requirements()) {
			Module other = modules.get(requirement.moduleId());
			if (other != null) {
				notes.add(new Note("Uses " + other.name() + " for " + requirement.purpose() + (other.isRunning() ? " (on)." : " (off: turn it on for this)."),
						other.isRunning() ? Theme.TEXT_SECONDARY : Theme.WARNING).hiddenWhen(closed));
			}
		}

		for (Module other : modules.all()) {
			if (modules.conflict(module, other)) {
				notes.add(new Note("Cannot run together with " + other.name() + ": turning one on turns the other off.", Theme.TEXT_SECONDARY).hiddenWhen(closed));
			}
		}

		if (!module.aliases().isEmpty()) {
			notes.add(new Note("Also found by searching: " + String.join(", ", module.aliases()) + ".", Theme.TEXT_MUTED).hiddenWhen(closed));
		}

		notes.add(new Note("Id: " + module.id() + ". " + module.settings().all().size() + " settings, saved to the active profile as you change them.",
				Theme.TEXT_MUTED).hiddenWhen(closed));
		return notes;
	}

	/** A wrapped line of text. */
	public static final class Note extends Widget {
		private final String text;
		private final int color;
		private java.util.function.BooleanSupplier hidden = () -> false;

		public Note(String text, int color) {
			this.text = text;
			this.color = color;
		}

		Note hiddenWhen(java.util.function.BooleanSupplier hidden) {
			this.hidden = hidden;
			return this;
		}

		@Override
		public int preferredHeight(UiContext ui, int width) {
			return hidden.getAsBoolean() ? 0 : ui.wrappedHeight(text, width - 12) + 6;
		}

		@Override
		public void render(UiContext ui) {
			if (!hidden.getAsBoolean()) {
				ui.wrapped(text, x + 6, y + 3, width - 12, color);
			}
		}
	}
}
