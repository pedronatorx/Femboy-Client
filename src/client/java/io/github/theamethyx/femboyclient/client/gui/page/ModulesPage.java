package io.github.theamethyx.femboyclient.client.gui.page;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.lwjgl.glfw.GLFW;

import net.minecraft.client.input.KeyEvent;

import io.github.theamethyx.femboyclient.client.gui.GuiState;
import io.github.theamethyx.femboyclient.client.gui.Icon;
import io.github.theamethyx.femboyclient.client.gui.ModuleStatus;
import io.github.theamethyx.femboyclient.client.gui.Theme;
import io.github.theamethyx.femboyclient.client.gui.UiContext;
import io.github.theamethyx.femboyclient.client.gui.widget.Dropdown;
import io.github.theamethyx.femboyclient.client.gui.widget.ModuleRow;
import io.github.theamethyx.femboyclient.client.gui.widget.ModuleSettingsPanel;
import io.github.theamethyx.femboyclient.client.gui.widget.ScrollList;
import io.github.theamethyx.femboyclient.client.gui.widget.Widget;
import io.github.theamethyx.femboyclient.core.module.Module;
import io.github.theamethyx.femboyclient.core.module.ModuleCategory;
import io.github.theamethyx.femboyclient.core.module.ModuleManager;

/**
 * One sidebar section: its modules on the left, the selected module's settings on the right.
 *
 * <p>The list shows every module of the section's categories. A section that combines categories (Visuals with the HUD
 * and utility modules, World with search, trackers and developer tools) shows each as a collapsible group. The "Show"
 * menu narrows the list: modules that are on, favorites, modules with a key, modules that need attention, or one kind of
 * module. Searching is the header's job (one search for everything), so this page has no second search field.
 *
 * <p>Keyboard: Up and Down move through the list, Space turns the selected module on or off.
 */
public class ModulesPage extends Page {
	/** What the list shows. */
	public enum Filter {
		ALL("All modules"), ON("Turned on"), FAVORITES("Favorites"), KEYED("With a key"), ATTENTION("Needs attention"),
		VISUAL("Kind: shows information"), ASSIST("Kind: assists"), AUTOMATION("Kind: acts for you");

		private final String label;

		Filter(String label) {
			this.label = label;
		}

		public String label() {
			return label;
		}
	}

	private final List<ModuleCategory> categories;
	private final String title;
	private final String navLabel;
	private final String subtitle;
	private final Icon icon;
	private final ModuleManager modules;
	private final Dropdown<Filter> filter;
	private final ScrollList list;
	private final ModuleSettingsPanel details;
	private final io.github.theamethyx.femboyclient.client.gui.widget.ConfirmButton resetAll;
	private Filter shown = Filter.ALL;
	private String selectedId;
	private int shownCount;

	public ModulesPage(UiContext ui, String title, String navLabel, String subtitle, Icon icon, ModuleCategory... categories) {
		super(ui);
		this.title = title;
		this.navLabel = navLabel;
		this.subtitle = subtitle;
		this.icon = icon;
		this.categories = Arrays.asList(categories);
		this.modules = femboy.modules();

		filter = add(new Dropdown<>(() -> Arrays.asList(Filter.values()), () -> shown, choice -> {
			shown = choice;
			rebuildList();
		}, Filter::label));
		filter.tooltip("Show only some modules: those that are on, your favorites, those with a key, those that need attention, or one kind.");
		list = add(new ScrollList(ui, 2, 0));
		details = add(new ModuleSettingsPanel(ui, modules));
		resetAll = add(new io.github.theamethyx.femboyclient.client.gui.widget.ConfirmButton("Reset all " + title + " modules",
				"Click again to reset them all", io.github.theamethyx.femboyclient.client.gui.widget.Button.Style.GHOST, this::resetAll));
		resetAll.icon(Icon.RESET);
		resetAll.tooltip("Put every module of this section back to its defaults: settings, keys and on/off. Asks for a second click.");
	}

	private void resetAll() {
		categories.forEach(modules::resetCategory);
		io.github.theamethyx.femboyclient.client.hud.Notifications.post(io.github.theamethyx.femboyclient.client.hud.Notifications.Category.CONFIG,
				"Reset", title + " modules are back to their defaults", Theme.SUCCESS);
		rebuildList();
		if (details.module() != null) {
			details.rebuild();
		}
	}

	/** Label of the group a category forms on a combined page. */
	public static String groupLabel(ModuleCategory category) {
		return switch (category) {
			case HUD -> "HUD and utilities";
			case TRACKERS -> "Search and trackers";
			case DEVELOPER -> "Developer tools";
			default -> category.label();
		};
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

	public Filter filter() {
		return shown;
	}

	public void setFilter(Filter filter) {
		shown = filter;
		rebuildList();
	}

	@Override
	public void onShow() {
		Module requested = GuiState.selectedModule == null ? null : modules.get(GuiState.selectedModule);
		if (requested != null && categories.contains(requested.category())) {
			selectedId = requested.id();
			if (!matches(requested)) {
				shown = Filter.ALL;
			}

			// A module opened from elsewhere is always visible, even in a closed group.
			GuiState.openSections.put(groupKey(requested.category()), true);
		}

		rebuildList();
		Module module = selectedModule();
		if (module != null) {
			details.show(module);
		}
	}

	@Override
	protected void onLayout() {
		int listWidth = Math.max(130, Math.min(190, width * 2 / 5));
		filter.setBounds(x, y, listWidth, 15);
		list.setBounds(x, y + 15 + Theme.SPACE_S + 12, listWidth, height - 15 - Theme.SPACE_S - 12 - 18);
		resetAll.setBounds(x, y + height - 14, listWidth, 14);
		details.setBounds(x + listWidth + Theme.SPACE_M, y, width - listWidth - Theme.SPACE_M, height);
		rebuildList();
	}

	private boolean matches(Module module) {
		return switch (shown) {
			case ALL -> true;
			case ON -> module.isEnabled();
			case FAVORITES -> femboy.quickAccess().isFavorite(module);
			case KEYED -> module.key() != Module.NO_KEY;
			case ATTENTION -> ModuleStatus.of(module, modules).needsAttention();
			case VISUAL -> module.tier() == Module.Tier.VISUAL;
			case ASSIST -> module.tier() == Module.Tier.ASSIST;
			case AUTOMATION -> module.tier() == Module.Tier.AUTOMATION || module.tier() == Module.Tier.SANDBOX;
		};
	}

	/** Modules shown, in list order (closed groups excluded). */
	public List<Module> visibleModules() {
		List<Module> result = new ArrayList<>();
		for (ModuleCategory category : categories) {
			if (!groupOpen(category)) {
				continue;
			}

			for (Module module : modules.byCategory(category)) {
				if (matches(module)) {
					result.add(module);
				}
			}
		}

		return result;
	}

	private static String groupKey(ModuleCategory category) {
		return "modules-group/" + category.name();
	}

	private boolean grouped() {
		return categories.size() > 1;
	}

	private boolean groupOpen(ModuleCategory category) {
		Boolean open = GuiState.openSections.get(groupKey(category));
		// Developer tools are for few people: their group starts closed.
		return !grouped() || (open == null ? category != ModuleCategory.DEVELOPER : open);
	}

	private void rebuildList() {
		List<Widget> rows = new ArrayList<>();
		int total = 0;
		for (ModuleCategory category : categories) {
			List<Module> members = modules.byCategory(category).stream().filter(this::matches).toList();
			total += members.size();
			if (grouped()) {
				rows.add(new GroupHeader(category, members.size()));
			}

			if (groupOpen(category)) {
				for (Module module : members) {
					rows.add(new ModuleRow(module, modules, () -> selectedId, this::select));
				}
			}
		}

		shownCount = total;
		if (total == 0) {
			rows.clear();
			rows.add(new EmptyState(emptyMessage()));
		}

		list.setChildren(rows);
		if (selectedModule() == null) {
			List<Module> visible = visibleModules();
			if (!visible.isEmpty()) {
				select(visible.getFirst());
			}
		}
	}

	private String emptyMessage() {
		return switch (shown) {
			case ON -> "Nothing here is turned on. Use the switch next to a module to turn it on.";
			case FAVORITES -> "No favorites here yet. Open a module and click the star next to its name.";
			case KEYED -> "No module here has a key. Open a module and click its key field to choose one.";
			case ATTENTION -> "All good: no module here has a problem.";
			default -> "No modules of this kind here.";
		};
	}

	/** The module whose settings are shown, or null (used by automated UI tests). */
	public Module selected() {
		return selectedModule();
	}

	public ModuleSettingsPanel details() {
		return details;
	}

	private Module selectedModule() {
		Module module = selectedId == null ? null : modules.get(selectedId);
		return module != null && categories.contains(module.category()) ? module : null;
	}

	private void select(Module module) {
		boolean changed = !module.id().equals(selectedId) || details.module() != module;
		selectedId = module.id();
		GuiState.selectedModule = module.id();
		if (changed) {
			femboy.quickAccess().touch(module);
			details.show(module);
		}
	}

	@Override
	public void render(UiContext ui) {
		filter.render(ui);
		int count = shownCount;
		String caption = shown == Filter.ALL ? count + (count == 1 ? " module" : " modules") : count + " shown, filter: " + shown.label();
		ui.textScaled(ui.ellipsize(caption, Math.round(list.width() / ui.small())), x + 2, y + 15 + Theme.SPACE_S + 2, ui.small(), Theme.TEXT_MUTED);
		ui.pushClip(list.x(), list.y(), list.width(), list.height());
		list.render(ui);
		ui.popClip();
		resetAll.render(ui);
		if (details.module() != null) {
			details.render(ui);
		} else {
			ui.textCentered("Choose a module on the left.", details.x() + details.width() / 2, details.y() + 30, Theme.TEXT_MUTED);
		}
	}

	@Override
	public boolean keyPressed(UiContext ui, KeyEvent event) {
		List<Module> visible = visibleModules();
		if (visible.isEmpty()) {
			return false;
		}

		Module current = selectedModule();
		int index = current == null ? -1 : visible.indexOf(current);
		switch (event.key()) {
			case GLFW.GLFW_KEY_DOWN -> select(visible.get(Math.min(visible.size() - 1, index + 1)));
			case GLFW.GLFW_KEY_UP -> select(visible.get(Math.max(0, index - 1)));
			case GLFW.GLFW_KEY_SPACE -> {
				if (current != null) {
					modules.toggle(current);
				}
			}
			default -> {
				return false;
			}
		}

		Module now = selectedModule();
		list.children().stream().filter(row -> row instanceof ModuleRow r && r.module() == now).findFirst().ifPresent(list::reveal);
		return true;
	}

	/** Heading of a category group on a combined page; a click opens or closes the group. */
	private final class GroupHeader extends Widget {
		private final ModuleCategory category;
		private final int count;

		GroupHeader(ModuleCategory category, int count) {
			this.category = category;
			this.count = count;
		}

		@Override
		public int preferredHeight(UiContext ui, int width) {
			return 16;
		}

		@Override
		public void render(UiContext ui) {
			boolean hot = hovered(ui);
			boolean open = groupOpen(category);
			ui.icon(open ? Icon.CHEVRON_DOWN : Icon.CHEVRON_RIGHT, x + 2, y + 5, 7, hot ? Theme.TEXT : Theme.TEXT_MUTED);
			ui.text(groupLabel(category), x + 12, y + 5, hot ? Theme.ACCENT_STRONG : Theme.TEXT_SECONDARY);
			ui.textRight(String.valueOf(count), x + width - 4, y + 5, Theme.TEXT_MUTED);
			if (hot) {
				ui.pointer();
				ui.tooltip(this, category.description() + (open ? "\n\nClick to hide this group." : "\n\nClick to show this group."));
			}
		}

		@Override
		public boolean mouseClicked(UiContext ui, double mouseX, double mouseY, int button) {
			if (button != 0 || !contains(mouseX, mouseY)) {
				return false;
			}

			GuiState.openSections.put(groupKey(category), !groupOpen(category));
			rebuildList();
			return true;
		}
	}

	/** A friendly message where a list would be empty. */
	public static final class EmptyState extends Widget {
		private final String text;

		public EmptyState(String text) {
			this.text = text;
		}

		@Override
		public int preferredHeight(UiContext ui, int width) {
			return ui.wrappedHeight(text, width - 16) + 30;
		}

		@Override
		public void render(UiContext ui) {
			ui.icon(Icon.INFO, x + width / 2 - 5, y + 6, 10, Theme.TEXT_MUTED);
			int lineY = y + 20;
			for (String line : List.of(text)) {
				lineY += ui.wrapped(line, x + 8, lineY, width - 16, Theme.TEXT_SECONDARY);
			}
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
