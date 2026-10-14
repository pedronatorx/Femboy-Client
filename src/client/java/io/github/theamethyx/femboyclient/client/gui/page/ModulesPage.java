package io.github.theamethyx.femboyclient.client.gui.page;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import io.github.theamethyx.femboyclient.client.gui.GuiState;
import io.github.theamethyx.femboyclient.client.gui.Icon;
import io.github.theamethyx.femboyclient.client.gui.Theme;
import io.github.theamethyx.femboyclient.client.gui.UiContext;
import io.github.theamethyx.femboyclient.client.gui.widget.Button;
import io.github.theamethyx.femboyclient.client.gui.widget.Container;
import io.github.theamethyx.femboyclient.client.gui.widget.Dropdown;
import io.github.theamethyx.femboyclient.client.gui.widget.KeybindButton;
import io.github.theamethyx.femboyclient.client.gui.widget.ModuleRow;
import io.github.theamethyx.femboyclient.client.gui.widget.ScrollList;
import io.github.theamethyx.femboyclient.client.gui.widget.SettingRow;
import io.github.theamethyx.femboyclient.client.gui.widget.TextField;
import io.github.theamethyx.femboyclient.client.gui.widget.ToggleSwitch;
import io.github.theamethyx.femboyclient.client.gui.widget.Widget;
import io.github.theamethyx.femboyclient.core.module.Module;
import io.github.theamethyx.femboyclient.core.module.ModuleCategory;
import io.github.theamethyx.femboyclient.core.module.ModuleManager;

/**
 * Searchable module list on the left; the selected module's status, key bind and settings on the
 * right. Each sidebar category (Visuals, World, Player, HUD) is one instance restricted to its category;
 * the category filter only appears when a page shows more than one category.
 */
public class ModulesPage extends Page {
	/** Filter choice; {@code category == null} means all categories shown on this page. */
	public record CategoryChoice(ModuleCategory category) {
		String label() {
			return category == null ? "All" : category.label();
		}
	}

	private final List<ModuleCategory> categories;
	private final String title;
	private final String navLabel;
	private final String subtitle;
	private final Icon icon;
	private final ModuleManager modules;
	private final TextField search;
	private final Dropdown<CategoryChoice> filter;
	private final ScrollList list;
	private final ScrollList details;
	private String lastSelection;
	private String selectedId;
	private String searchText = "";

	public ModulesPage(UiContext ui, String title, String navLabel, String subtitle, Icon icon, ModuleCategory... categories) {
		super(ui);
		this.title = title;
		this.navLabel = navLabel;
		this.subtitle = subtitle;
		this.icon = icon;
		this.categories = Arrays.asList(categories);
		this.modules = femboy.modules();

		search = add(new TextField("Search", 40).icon(Icon.SEARCH).onChange(text -> {
			searchText = text;
			rebuildList();
		}));
		search.tooltip("Type to filter by module name, description or setting name. All words must match.");

		List<CategoryChoice> choices = new ArrayList<>();
		choices.add(new CategoryChoice(null));
		this.categories.forEach(category -> choices.add(new CategoryChoice(category)));
		filter = add(new Dropdown<>(() -> choices, () -> new CategoryChoice(effectiveFilter()), choice -> {
			GuiState.moduleFilter = choice.category();
			rebuildList();
		}, CategoryChoice::label));
		filter.tooltip("Show only modules of one category.");
		if (this.categories.size() == 1) {
			children.remove(filter);
		}

		list = add(new ScrollList(ui, 3, 0));
		details = add(new ScrollList(ui, 4, 0));
	}

	private ModuleCategory effectiveFilter() {
		return categories.contains(GuiState.moduleFilter) ? GuiState.moduleFilter : null;
	}

	@Override
	public String title() {
		return title;
	}

	@Override
	public String navLabel() {
		return navLabel;
	}

	@Override
	public String subtitle() {
		return subtitle;
	}

	@Override
	public Icon icon() {
		return icon;
	}

	/** The categories this page lists. */
	public List<ModuleCategory> categories() {
		return categories;
	}

	@Override
	public void onShow() {
		Module requested = GuiState.selectedModule == null ? null : modules.get(GuiState.selectedModule);
		if (requested != null && categories.contains(requested.category())) {
			selectedId = requested.id();
		}

		search.setText(searchText);
		rebuildList();
		rebuildDetails();
	}

	@Override
	protected void onLayout() {
		int listWidth = Math.max(120, Math.min(170, (width - Theme.GAP) * 9 / 20));
		int filterWidth = categories.size() == 1 ? 0 : Math.min(96, listWidth / 2);
		search.setBounds(x, y, listWidth - filterWidth - (filterWidth == 0 ? 0 : Theme.GAP), 15);
		filter.setBounds(x + listWidth - filterWidth, y, filterWidth, 15);
		list.setBounds(x, y + 15 + Theme.GAP, listWidth, height - 15 - Theme.GAP);
		details.setBounds(x + listWidth + Theme.PAD, y, width - listWidth - Theme.PAD, height);
		rebuildList();
		rebuildDetails();
	}

	private List<Module> visibleModules() {
		ModuleCategory chosen = effectiveFilter();
		List<Module> result = new ArrayList<>();
		for (Module module : modules.search(searchText, chosen)) {
			if (categories.contains(module.category())) {
				result.add(module);
			}
		}

		return result;
	}

	private void rebuildList() {
		List<Module> visible = visibleModules();
		List<Widget> rows = new ArrayList<>();
		for (Module module : visible) {
			rows.add(new ModuleRow(module, modules, () -> selectedId, this::select));
		}

		if (rows.isEmpty()) {
			String message = searchText.isBlank() ? "No modules in this category yet." : "No modules match \"" + searchText + "\".";
			rows.add(new Message(message));
		}

		list.setChildren(rows);
		if (selectedModule() == null && !visible.isEmpty()) {
			select(visible.getFirst());
		}
	}

	/** The module whose settings are shown, or null (used by automated UI tests). */
	public Module selected() {
		return selectedModule();
	}

	private Module selectedModule() {
		Module module = selectedId == null ? null : modules.get(selectedId);
		return module != null && categories.contains(module.category()) ? module : null;
	}

	private void select(Module module) {
		selectedId = module.id();
		GuiState.selectedModule = module.id();
		if (!module.id().equals(lastSelection)) {
			rebuildDetails();
		}
	}

	private void rebuildDetails() {
		Module module = selectedModule();
		lastSelection = module == null ? null : module.id();
		List<Widget> rows = new ArrayList<>();

		if (module == null) {
			rows.add(new Message("Select a module on the left to see its settings."));
		} else {
			rows.add(new ModuleHeader(module));
			rows.add(new ModuleControls(module));
			if (module.settings().isEmpty()) {
				rows.add(new Message("This module has no settings."));
			} else {
				rows.addAll(SettingRow.rowsFor(module.settings().all()));
			}

			if (module instanceof io.github.theamethyx.femboyclient.client.module.ClientHooks.CustomControls custom) {
				// Module-specific controls (e.g. the waypoint list); refresh rebuilds this panel.
				rows.addAll(custom.createControls(ui, () -> rebuildDetailsKeepScroll()));
			}
		}

		details.setChildren(rows);
		details.scrollToTop();
	}

	private void rebuildDetailsKeepScroll() {
		lastSelection = null;
		rebuildDetails();
	}

	/** Name, category, description and live status of the selected module. */
	private final class ModuleHeader extends Widget {
		private final Module module;

		ModuleHeader(Module module) {
			this.module = module;
		}

		@Override
		public int preferredHeight(UiContext ui, int width) {
			return 26 + ui.wrappedHeight(module.description(), width - 12) + 14;
		}

		@Override
		public void render(UiContext ui) {
			ui.roundRect(x, y, width, height, Theme.SURFACE);
			ui.icon(Icon.byName(module.category().icon()), x + 6, y + 6, 9, Theme.ACCENT);
			ui.textClipped(module.name(), x + 19, y + 7, width - 25, Theme.TEXT);
			ui.text(module.category().label() + (module.isRestricted() ? "  ·  Singleplayer and authorized servers" : ""), x + 6, y + 18, Theme.TEXT_MUTED);
			int used = ui.wrapped(module.description(), x + 6, y + 30, width - 12, Theme.TEXT_SECONDARY);

			ModuleRow.StatusView status = ModuleRow.status(module, modules);
			ui.rect(x + 6, y + 33 + used, 4, 4, status.color());
			boolean cut = ui.textClipped(status.text(), x + 14, y + 31 + used, width - 20, status.color());
			if (cut && ui.hovered(x, y + 28 + used, width, 12)) {
				ui.tooltip(this, status.text());
			}
		}
	}

	/** Enable switch, key bind and reset for the selected module. */
	private final class ModuleControls extends Container {
		private final ToggleSwitch enabled;
		private final KeybindButton key;
		private final Button reset;

		ModuleControls(Module module) {
			enabled = add(new ToggleSwitch(module::isEnabled, value -> modules.setEnabled(module, value)));
			enabled.tooltip("Turn the module on or off. It also runs only while its environment allows it (see status above).");
			key = add(new KeybindButton(module::key, code -> modules.setKey(module, code)));
			key.tooltip(() -> "Key that toggles this module while playing. Click, then press a key; Backspace removes it."
					+ conflictText(module));
			reset = add(new Button("Reset", Button.Style.SECONDARY, () -> modules.resetToDefaults(module)).icon(Icon.RESET));
			reset.tooltip("Restore this module's settings, key and on/off state to the defaults.");
		}

		private String conflictText(Module module) {
			List<Module> shared = femboy.keybinds().modulesBoundTo(module.key());
			shared.remove(module);
			return shared.isEmpty() ? "" : "\n\nAlso bound to: " + String.join(", ", shared.stream().map(Module::name).toList());
		}

		@Override
		public int preferredHeight(UiContext ui, int width) {
			return 36;
		}

		@Override
		protected void onLayout() {
			enabled.setBounds(x + width - ToggleSwitch.WIDTH - 6, y + 4, ToggleSwitch.WIDTH, ToggleSwitch.HEIGHT);
			int resetWidth = 50;
			key.setBounds(x + 6, y + 18, width - 12 - resetWidth - Theme.GAP, 14);
			reset.setBounds(x + width - 6 - resetWidth, y + 18, resetWidth, 14);
		}

		@Override
		public void render(UiContext ui) {
			ui.roundRect(x, y, width, height, Theme.SURFACE);
			ui.text("Enabled", x + 6, y + 5, Theme.TEXT);
			super.render(ui);
		}
	}

	/** A muted one-line message used for empty states. */
	static final class Message extends Widget {
		private final String text;

		Message(String text) {
			this.text = text;
		}

		@Override
		public int preferredHeight(UiContext ui, int width) {
			return ui.wrappedHeight(text, width - 12) + 10;
		}

		@Override
		public void render(UiContext ui) {
			ui.wrapped(text, x + 6, y + 5, width - 12, Theme.TEXT_MUTED);
		}
	}
}
