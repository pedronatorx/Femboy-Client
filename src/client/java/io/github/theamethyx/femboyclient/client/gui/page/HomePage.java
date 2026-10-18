package io.github.theamethyx.femboyclient.client.gui.page;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.lwjgl.glfw.GLFW;

import net.minecraft.client.input.KeyEvent;

import io.github.theamethyx.femboyclient.client.gui.Anim;
import io.github.theamethyx.femboyclient.client.gui.GuiState;
import io.github.theamethyx.femboyclient.client.gui.Icon;
import io.github.theamethyx.femboyclient.client.gui.Theme;
import io.github.theamethyx.femboyclient.client.gui.UiContext;
import io.github.theamethyx.femboyclient.client.gui.widget.Container;
import io.github.theamethyx.femboyclient.client.gui.widget.ModuleSettingsPanel;
import io.github.theamethyx.femboyclient.client.gui.widget.ModuleTile;
import io.github.theamethyx.femboyclient.core.module.Module;
import io.github.theamethyx.femboyclient.core.module.ModuleCategory;

/**
 * Every module at a glance: one card per category, laid out in columns. Left-click a module to turn it on or off,
 * right-click it to open its settings in a panel that slides in from the right. Category chips and the search field in
 * the header narrow the grid; the strip at the bottom lists what is on.
 */
public class HomePage extends Page {
	private static final int CHIPS = 15;
	private static final int STRIP = 16;
	private static final int MIN_COLUMN = 138;

	private final List<CategoryCard> cards = new ArrayList<>();
	private final ModuleSettingsPanel panel;
	private final Anim panelOpen = new Anim(0, 16);
	private final Anim scroll = new Anim(0, 20);
	private final Anim appear = new Anim(0, 10);
	private final Anim chipLine = new Anim(0, 18);
	private final List<ModuleCategory> categories = new ArrayList<>();

	/** A chip: one sidebar section, which may hold several categories (Visuals includes the HUD panels, for example). */
	private record Group(String label, List<ModuleCategory> members) {
	}

	private static final List<Group> GROUPS = List.of(new Group("Combat", List.of(ModuleCategory.COMBAT)),
			new Group("Automation", List.of(ModuleCategory.AUTOMATION)), new Group("Movement", List.of(ModuleCategory.MOVEMENT)),
			new Group("Visuals", List.of(ModuleCategory.VISUALS, ModuleCategory.HUD)), new Group("Inventory", List.of(ModuleCategory.INVENTORY)),
			new Group("World", List.of(ModuleCategory.WORLD, ModuleCategory.DEVELOPER)), new Group("Trackers", List.of(ModuleCategory.TRACKERS)));
	private Group group;
	private float scrollTarget;
	private int contentHeight;
	private String query = "";
	private int laidOutFor = -1;

	public HomePage(UiContext ui, java.util.function.Consumer<Module> openModule) {
		super(ui);
		for (ModuleCategory c : ModuleCategory.values()) {
			if (!femboy.modules().byCategory(c).isEmpty()) {
				categories.add(c);
			}
		}

		query = GuiState.search;
		panel = add(new ModuleSettingsPanel(ui, femboy.modules(), this::closeSettings));
	}

	@Override
	public String title() {
		return "Home";
	}

	@Override
	public String subtitle() {
		return query.isBlank() ? "Left-click a module to turn it on or off, right-click it for its settings."
				: "Modules matching \"" + query + "\". Left-click: on/off, right-click: settings.";
	}

	/** Filters the grid (the header's search field calls this). */
	public void setQuery(String text) {
		if (!text.equals(query)) {
			query = text;
			scrollTarget = 0;
			rebuild();
		}
	}

	public String query() {
		return query;
	}

	@Override
	public Icon icon() {
		return Icon.HOME;
	}

	/** The module whose settings panel is open, or {@code null} (used by automated UI tests). */
	public Module settingsModule() {
		return settingsOpen() ? panel.module() : null;
	}

	public ModuleSettingsPanel settingsPanel() {
		return panel;
	}

	/** Opens the settings panel for {@code module} (what a right-click does). */
	public void openSettings(Module module) {
		GuiState.homeSettings = module.id();
		panel.show(module);
		layoutAll();
	}

	public void closeSettings() {
		GuiState.homeSettings = null;
		ui.focus(null);
	}

	private boolean settingsOpen() {
		return GuiState.homeSettings != null && panel.module() != null;
	}

	@Override
	public void onShow() {
		appear.snap(0);
		if (GuiState.homeSettings != null) {
			Module module = femboy.modules().get(GuiState.homeSettings);
			if (module != null) {
				panel.show(module);
			}
		}

		rebuild();
	}

	private void rebuild() {
		children.removeAll(cards);
		cards.clear();
		Map<ModuleCategory, List<Module>> grouped = new LinkedHashMap<>();
		for (Module module : femboy.modules().search(query, null)) {
			if (group != null && !group.members().contains(module.category())) {
				continue;
			}

			grouped.computeIfAbsent(module.category(), c -> new ArrayList<>()).add(module);
		}

		// Favorites and recently used modules first, when nothing is searched or filtered.
		if (query.isBlank() && group == null) {
			List<Module> favorites = femboy.quickAccess().favorites();
			if (!favorites.isEmpty()) {
				CategoryCard card = new CategoryCard("Favorites", Icon.STAR, favorites);
				cards.add(card);
				children.add(0, card);
			}

			List<Module> recent = femboy.quickAccess().recent().stream().filter(module -> !favorites.contains(module)).limit(6).toList();
			if (!recent.isEmpty()) {
				CategoryCard card = new CategoryCard("Recently used", Icon.CLOCK, recent);
				cards.add(card);
				children.add(0, card);
			}
		}

		// Cards in sidebar order: a section's categories next to each other (Visuals, then the HUD panels).
		for (Group g : GROUPS) {
			for (ModuleCategory c : g.members()) {
				List<Module> modules = grouped.get(c);
				if (modules != null && categories.contains(c)) {
					CategoryCard card = new CategoryCard(c, modules);
					cards.add(card);
					children.add(0, card);
				}
			}
		}

		laidOutFor = -1;
		layoutAll();
	}

	@Override
	protected void onLayout() {
		layoutAll();
	}

	private int panelWidth() {
		return Math.min(230, Math.max(180, width * 2 / 5));
	}

	private int gridTop() {
		return y + chipsHeight() + Theme.GAP + 2;
	}

	private int gridHeight() {
		return height - (gridTop() - y) - STRIP - Theme.GAP;
	}

	private void layoutAll() {
		float open = panelOpen.get();
		int panelW = panelWidth();
		panel.setBounds(x + width - Math.round(panelW * open), gridTop(), panelW, height - (gridTop() - y));
		layoutGrid();
	}

	/** Places the cards in columns, each card going to the currently shortest column. */
	private void layoutGrid() {
		int gridWidth = width - Math.round((panelWidth() + Theme.PAD) * panelOpen.get());
		int columns = Math.max(1, Math.min(4, (gridWidth + Theme.PAD) / (MIN_COLUMN + Theme.PAD)));
		int columnWidth = (gridWidth - (columns - 1) * Theme.PAD) / columns;
		int[] heights = new int[columns];
		int offset = Math.round(scroll.get());
		for (CategoryCard card : cards) {
			int column = 0;
			for (int i = 1; i < columns; i++) {
				if (heights[i] < heights[column]) {
					column = i;
				}
			}

			int cardHeight = card.preferredHeight(ui, columnWidth);
			card.setBounds(x + column * (columnWidth + Theme.PAD), gridTop() + heights[column] - offset, columnWidth, cardHeight);
			heights[column] += cardHeight + Theme.PAD;
		}

		contentHeight = 0;
		for (int h : heights) {
			contentHeight = Math.max(contentHeight, h);
		}

		laidOutFor = offset * 10_000 + gridWidth;
	}

	private int maxScroll() {
		return Math.max(0, contentHeight - gridHeight());
	}

	@Override
	public void render(UiContext ui) {
		float open = panelOpen.update(settingsOpen() ? 1 : 0, ui.frameSeconds(), ui.motion());
		scrollTarget = Math.max(0, Math.min(maxScroll(), scrollTarget));
		float offset = scroll.update(scrollTarget, ui.frameSeconds(), ui.animate());
		int gridWidth = width - Math.round((panelWidth() + Theme.PAD) * open);
		if (laidOutFor != Math.round(offset) * 10_000 + gridWidth || open > 0 && open < 1) {
			layoutAll();
		}

		renderChips(ui);

		// Cards rise into place when the page opens (only fade with reduced motion).
		float rise = appear.update(1, ui.frameSeconds(), ui.animate());
		ui.pushClip(x, gridTop(), gridWidth, gridHeight());
		var pose = ui.graphics().pose();
		pose.pushMatrix();
		if (ui.motion()) {
			pose.translate(0, Math.round((1 - rise) * 10));
		}
		for (CategoryCard card : cards) {
			if (card.y() + card.height() >= gridTop() && card.y() <= gridTop() + gridHeight()) {
				card.render(ui);
			}
		}

		pose.popMatrix();
		ui.popClip();
		if (rise < 1) {
			ui.rect(x, gridTop(), gridWidth, gridHeight(), Theme.withAlpha(Theme.PANEL, (1 - rise) * 0.8f));
		}

		if (cards.isEmpty()) {
			ui.textCentered("No modules match \"" + query + "\".", x + gridWidth / 2, gridTop() + 20, Theme.TEXT_MUTED);
		}

		if (maxScroll() > 0) {
			int barHeight = Math.max(16, gridHeight() * gridHeight() / Math.max(1, contentHeight));
			int barY = gridTop() + Math.round((gridHeight() - barHeight) * (offset / Math.max(1, maxScroll())));
			ui.roundRect(x + gridWidth - 2, barY, 2, barHeight, Theme.withAlpha(Theme.ACCENT, 0.5f));
		}

		renderStrip(ui, gridWidth);
		if (open > 0.01f) {
			ui.graphics().nextStratum();
			panel.render(ui);
		}
	}

	/** Chip boxes {x, y, width}; they wrap to further rows, the first row leaving room for the search field. */
	private List<int[]> chipBoxes() {
		List<int[]> boxes = new ArrayList<>();
		int cx = x;
		int cy = y;
		int limit = x + width;
		for (int i = 0; i <= GROUPS.size(); i++) {
			String label = i == 0 ? "All" : GROUPS.get(i - 1).label();
			int w = ui.textWidth(label) + 12;
			if (cx + w > limit && cx > x) {
				cx = x;
				cy += CHIPS + 3;
				limit = x + width;
			}

			boxes.add(new int[] {cx, cy, w});
			cx += w + 3;
		}

		return boxes;
	}

	private int chipsHeight() {
		List<int[]> boxes = chipBoxes();
		return boxes.isEmpty() ? CHIPS : boxes.getLast()[1] - y + CHIPS;
	}

	private void renderChips(UiContext ui) {
		List<int[]> boxes = chipBoxes();
		int selectedIndex = group == null ? 0 : GROUPS.indexOf(group) + 1;
		// The highlight glides from the previous chip to the selected one.
		float line = chipLine.update(selectedIndex, ui.frameSeconds(), ui.motion());
		int from = Math.max(0, Math.min(boxes.size() - 1, (int) Math.floor(line)));
		int to = Math.min(boxes.size() - 1, from + 1);
		float t = line - from;
		int[] a = boxes.get(from);
		int[] b = boxes.get(to);
		if (a[1] == b[1]) {
			ui.gradientRect(Math.round(a[0] + (b[0] - a[0]) * t), a[1], Math.round(a[2] + (b[2] - a[2]) * t), CHIPS, Theme.ACCENT, Theme.ACCENT_2, 7);
		} else {
			// Across a row break the highlight jumps instead of sliding through the gap.
			int[] at = t < 0.5f ? a : b;
			ui.gradientRect(at[0], at[1], at[2], CHIPS, Theme.ACCENT, Theme.ACCENT_2, 7);
		}

		for (int i = 0; i < boxes.size(); i++) {
			int[] box = boxes.get(i);
			String label = i == 0 ? "All" : GROUPS.get(i - 1).label();
			boolean selected = i == selectedIndex;
			boolean hot = ui.hovered(box[0], box[1], box[2], CHIPS);
			if (!selected) {
				ui.roundRect(box[0], box[1], box[2], CHIPS, hot ? Theme.SURFACE_HOVER : Theme.SURFACE, 7);
				if (hot) {
					ui.pointer();
				}
			}

			ui.text(label, box[0] + 6, box[1] + 4, selected ? 0xFFFFFFFF : hot ? Theme.TEXT : Theme.TEXT_SECONDARY);
		}
	}

	private int chipAt(double mouseX, double mouseY) {
		List<int[]> boxes = chipBoxes();
		for (int i = 0; i < boxes.size(); i++) {
			int[] box = boxes.get(i);
			if (UiContext.inside(mouseX, mouseY, box[0], box[1], box[2], CHIPS)) {
				return i;
			}
		}

		return -1;
	}

	private void renderStrip(UiContext ui, int gridWidth) {
		int sy = y + height - STRIP;
		ui.roundRect(x, sy, gridWidth, STRIP, Theme.SURFACE, 6);
		List<String> on = femboy.modules().all().stream().filter(Module::isEnabled).map(Module::name).toList();
		ui.icon(Icon.PERFORMANCE, x + 5, sy + 4, 8, on.isEmpty() ? Theme.TEXT_MUTED : Theme.ACCENT);
		String text = on.isEmpty() ? "No modules enabled" : on.size() + " enabled: " + String.join(", ", on);
		boolean cut = ui.textClipped(text, x + 17, sy + 4, gridWidth - 22, on.isEmpty() ? Theme.TEXT_MUTED : Theme.TEXT_SECONDARY);
		if (cut && ui.hovered(x, sy, gridWidth, STRIP)) {
			ui.tooltip("enabled-strip", String.join("\n", on));
		}
	}

	@Override
	public boolean mouseClicked(UiContext ui, double mouseX, double mouseY, int button) {
		if (settingsOpen() && panel.mouseClicked(ui, mouseX, mouseY, button)) {
			return true;
		}

		int chip = chipAt(mouseX, mouseY);
		if (chip >= 0) {
			group = chip == 0 ? null : GROUPS.get(chip - 1);
			scrollTarget = 0;
			rebuild();
			return true;
		}

		if (mouseY >= gridTop() && mouseY < gridTop() + gridHeight()) {
			for (CategoryCard card : cards) {
				if (card.mouseClicked(ui, mouseX, mouseY, button)) {
					return true;
				}
			}
		}

		return false;
	}

	@Override
	public boolean mouseScrolled(UiContext ui, double mouseX, double mouseY, double amount) {
		if (settingsOpen() && panel.contains(mouseX, mouseY)) {
			return panel.mouseScrolled(ui, mouseX, mouseY, amount);
		}

		scrollTarget = (float) Math.max(0, Math.min(maxScroll(), scrollTarget - amount * 28));
		return true;
	}

	@Override
	public boolean keyPressed(UiContext ui, KeyEvent event) {
		if (event.key() == GLFW.GLFW_KEY_ESCAPE && settingsOpen()) {
			closeSettings();
			return true;
		}

		return super.keyPressed(ui, event);
	}

	/** One category: a header with icon, name and count, then its modules. */
	private final class CategoryCard extends Container {
		private final String title;
		private final Icon icon;
		private final int total;
		private final List<ModuleTile> tiles = new ArrayList<>();

		CategoryCard(ModuleCategory category, List<Module> modules) {
			this(category.label(), Icon.byName(category.icon()), modules, femboy.modules().byCategory(category).size());
		}

		CategoryCard(String title, Icon icon, List<Module> modules) {
			this(title, icon, modules, modules.size());
		}

		private CategoryCard(String title, Icon icon, List<Module> modules, int total) {
			this.title = title;
			this.icon = icon;
			this.total = total;
			for (Module module : modules) {
				tiles.add(add(new ModuleTile(module, femboy.modules(), () -> settingsOpen() ? panel.module().id() : null, HomePage.this::openSettings)));
			}
		}

		@Override
		public int preferredHeight(UiContext ui, int width) {
			return 22 + tiles.size() * (ModuleTile.HEIGHT + 2) + 3;
		}

		@Override
		protected void onLayout() {
			int ty = y + 22;
			for (ModuleTile tile : tiles) {
				tile.setBounds(x + 3, ty, width - 6, ModuleTile.HEIGHT);
				ty += ModuleTile.HEIGHT + 2;
			}
		}

		@Override
		public void render(UiContext ui) {
			ui.roundRect(x, y, width, height, Theme.SIDEBAR, 7);
			ui.outline(x, y, width, height, Theme.BORDER, 7);
			ui.gradientRect(x + 6, y + 5, 13, 13, Theme.ACCENT, Theme.ACCENT_2, 4);
			ui.icon(icon, x + 8, y + 7, 9, 0xFFFFFFFF);
			ui.textScaled(title, x + 24, y + 7, 1.1f, Theme.TEXT);
			String count = String.valueOf(total);
			int countWidth = ui.textWidth(count) + 8;
			ui.roundRect(x + width - countWidth - 6, y + 5, countWidth, 11, Theme.SURFACE_ACTIVE, 5);
			ui.text(count, x + width - countWidth - 2, y + 7, Theme.TEXT_SECONDARY);
			super.render(ui);
		}
	}

	/** Tiles in the grid, in display order (used by automated UI tests). */
	public List<ModuleTile> tiles() {
		List<ModuleTile> all = new ArrayList<>();
		cards.forEach(card -> all.addAll(card.tiles));
		return all;
	}
}
