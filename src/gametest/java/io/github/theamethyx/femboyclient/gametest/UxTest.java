package io.github.theamethyx.femboyclient.gametest;

import static io.github.theamethyx.femboyclient.gametest.ClientGameTest.enable;
import static io.github.theamethyx.femboyclient.gametest.ClientGameTest.femboy;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.lwjgl.glfw.GLFW;

import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

import io.github.theamethyx.femboyclient.client.GlobalOptions;
import io.github.theamethyx.femboyclient.client.gui.ClientScreen;
import io.github.theamethyx.femboyclient.client.gui.CommandPalette;
import io.github.theamethyx.femboyclient.client.gui.GuiState;
import io.github.theamethyx.femboyclient.client.gui.HudEditorScreen;
import io.github.theamethyx.femboyclient.client.gui.ModuleStatus;
import io.github.theamethyx.femboyclient.client.gui.page.HelpPage;
import io.github.theamethyx.femboyclient.client.gui.page.HomePage;
import io.github.theamethyx.femboyclient.client.gui.page.ModulesPage;
import io.github.theamethyx.femboyclient.client.gui.page.ProfilesPage;
import io.github.theamethyx.femboyclient.client.gui.page.SettingsPage;
import io.github.theamethyx.femboyclient.client.gui.widget.Button;
import io.github.theamethyx.femboyclient.client.gui.widget.Dialog;
import io.github.theamethyx.femboyclient.client.gui.widget.Dropdown;
import io.github.theamethyx.femboyclient.client.gui.widget.EspPreview;
import io.github.theamethyx.femboyclient.client.gui.widget.ModuleRow;
import io.github.theamethyx.femboyclient.client.gui.widget.SettingRow;
import io.github.theamethyx.femboyclient.client.modules.BlockEspModule;
import io.github.theamethyx.femboyclient.client.modules.ChunkBordersModule;
import io.github.theamethyx.femboyclient.client.modules.CoordinatesHudModule;
import io.github.theamethyx.femboyclient.client.modules.EntityEspModule;
import io.github.theamethyx.femboyclient.client.modules.StorageEspModule;
import io.github.theamethyx.femboyclient.core.module.Module;
import io.github.theamethyx.femboyclient.core.module.ModuleCategory;

/**
 * The redesigned interface, driven like a player would: the nine sections and that every module has exactly one home,
 * the one search across modules, settings, profiles, HUD elements and help, help mode and the Help tab, the ESP layout
 * shared by all ESP modules with its preview, folded advanced settings, module status in words, the list filters, Home's
 * parts, profile dialogs, the HUD editor's toolbar placement and inspector, keyboard navigation, the layout at several
 * window sizes and menu scales, and reopening the menu.
 */
@SuppressWarnings("UnstableApiUsage")
final class UxTest {
	private UxTest() {
	}

	static void run(ClientGameTestContext context, TestSingleplayerContext singleplayer, UiDriver ui, TestReport report) {
		sections(context, report);
		search(context, report);
		help(context, ui, report);
		espLayout(context, ui, report);
		statusAndFilters(context, ui, report);
		home(context, report);
		profiles(context, ui, report);
		hudEditor(context, report);
		keyboard(context, report);
		layouts(context, report);
		reopen(context, report);
	}

	private static ClientScreen screen(ClientGameTestContext context) {
		return context.computeOnClient(client -> (ClientScreen) client.screen);
	}

	/** Every module appears on exactly one section page; the sections are the nine of the design. */
	private static void sections(ClientGameTestContext context, TestReport report) {
		ClientGameTest.openMenu(context);
		String result = context.computeOnClient(client -> {
			ClientScreen screen = (ClientScreen) client.screen;
			Map<String, Integer> pagesPerModule = new HashMap<>();
			for (int i = 0; i < screen.pageCount(); i++) {
				if (screen.page(i) instanceof ModulesPage page) {
					for (ModuleCategory category : page.categories()) {
						for (Module module : femboy().modules().byCategory(category)) {
							pagesPerModule.merge(module.id(), 1, Integer::sum);
						}
					}
				}
			}

			List<String> problems = new ArrayList<>();
			for (Module module : femboy().modules().all()) {
				int count = pagesPerModule.getOrDefault(module.id(), 0);
				if (count != 1) {
					problems.add(module.id() + " on " + count + " pages");
				}
			}

			return problems.isEmpty() ? "ok (" + femboy().modules().all().size() + " modules)" : String.join(", ", problems);
		});
		report.check("Every module is on exactly one section page (none lost in the regrouping)", result.startsWith("ok"), result);

		List<String> groups = context.computeOnClient(client -> {
			ClientScreen screen = (ClientScreen) client.screen;
			screen.showPage(screen.pageIndex(page -> page.title().equals("World")));
			GuiState.openSections.remove("modules-group/DEVELOPER");
			((ModulesPage) screen.currentPage()).onShow();
			return screen.currentPage().descendants(ModuleRow.class).stream().map(row -> row.module().category().name()).distinct().toList();
		});
		report.check("World groups world tools, search and trackers; developer tools start folded",
				groups.contains("WORLD") && groups.contains("TRACKERS") && !groups.contains("DEVELOPER"), groups.toString());
		context.waitTicks(2);
		context.takeScreenshot("ux-world-groups");
		ClientGameTest.closeMenu(context, new UiDriver(context));
	}

	private static List<String> searchGroups(ClientGameTestContext context, String query) {
		return context.computeOnClient(client -> {
			CommandPalette palette = ((ClientScreen) client.screen).openSearch(query);
			List<String> result = palette.entries().stream().map(entry -> entry.group() + ":" + entry.title()).toList();
			((ClientScreen) client.screen).uiContext().closeOverlay();
			return result;
		});
	}

	/** One search for everything, grouped by kind. */
	private static void search(ClientGameTestContext context, TestReport report) {
		ClientGameTest.openMenu(context);
		List<String> setting = searchGroups(context, "outline thickness");
		List<String> action = searchGroups(context, "restore point");
		List<String> hud = searchGroups(context, "coordinates hud");
		List<String> help = searchGroups(context, "target line");
		List<String> nothing = searchGroups(context, "zzqqxx");
		report.check("The search finds settings (\"outline thickness\" under Settings)", setting.stream().anyMatch(e -> e.startsWith("Settings:Outline thickness")),
				setting.toString());
		report.check("The search finds HUD elements (\"coordinates hud\")", hud.stream().anyMatch(e -> e.startsWith("HUD elements:Coordinates")), hud.toString());
		report.check("The search finds help topics (\"target line\")", help.stream().anyMatch(e -> e.startsWith("Help:Target line")), help.toString());
		report.check("The search finds actions (\"restore point\") and no modules or settings for nonsense",
				action.contains("Actions:Create a restore point") && nothing.stream().noneMatch(e -> e.startsWith("Modules:") || e.startsWith("Settings:")),
				action + " / " + nothing);

		// Choosing a setting result opens its module with the setting's folds open (here: a "More options" one).
		context.runOnClient(client -> {
			CommandPalette palette = ((ClientScreen) client.screen).openSearch("background padding");
			palette.entries().stream().filter(e -> e.group().equals("Settings") && e.detail().startsWith("Entity ESP")).findFirst().ifPresent(e -> {
				((ClientScreen) client.screen).uiContext().closeOverlay();
				e.run().run();
			});
		});
		context.waitTicks(3);
		int height = context.computeOnClient(client -> ((ClientScreen) client.screen).currentPage().descendants(SettingRow.class).stream()
				.filter(row -> row.setting().id().equals("label_padding")).findFirst().map(row -> row.height()).orElse(-1));
		report.check("Choosing a setting in the search opens its module with the setting visible (Entity ESP, Background padding)", height > 0,
				"row height " + height);
		ClientGameTest.closeMenu(context, new UiDriver(context));
	}

	/** Settings › Help: guides and glossary, searchable; help mode toggles explanations. */
	private static void help(ClientGameTestContext context, UiDriver ui, TestReport report) {
		ClientGameTest.openMenu(context);
		context.runOnClient(client -> ((ClientScreen) client.screen).openSettings("Help"));
		context.waitTicks(3);
		List<String> all = context.computeOnClient(client -> ((HelpPage) ((SettingsPage) ((ClientScreen) client.screen).currentPage()).activeTab()).shownTitles());
		context.takeScreenshot("ux-help");
		HelpPage page = context.computeOnClient(client -> (HelpPage) ((SettingsPage) ((ClientScreen) client.screen).currentPage()).activeTab());
		ui.click(page.searchField());
		ui.type("restore");
		context.waitTicks(2);
		List<String> found = context.computeOnClient(client -> page.shownTitles());
		ui.key(GLFW.GLFW_KEY_ESCAPE);
		context.runOnClient(client -> page.setQuery("zzqqxx"));
		List<String> none = context.computeOnClient(client -> page.shownTitles());
		context.runOnClient(client -> page.setQuery(""));
		report.check("Settings › Help lists guides and a glossary", all.contains("Getting started") && all.contains("ESP") && all.size() >= 20,
				all.size() + " topics");
		report.check("Typing in the help search narrows it (\"restore\" finds Backups and restore points; nonsense finds nothing)",
				found.contains("Backups and restore points") && found.size() < all.size() && none.isEmpty(), found + " / " + none);
		ClientGameTest.closeMenu(context, ui);
	}

	/** Entity, Block and Storage ESP share their sections; each has a live preview; tuning is folded away. */
	private static void espLayout(ClientGameTestContext context, UiDriver ui, TestReport report) {
		ClientGameTest.openMenu(context);
		List<String> problems = new ArrayList<>();
		for (Class<? extends Module> type : List.of(EntityEspModule.class, BlockEspModule.class, StorageEspModule.class)) {
			ClientGameTest.openModule(context, type);
			List<String> titles = context.computeOnClient(client -> ((ClientScreen) client.screen).currentPage().descendants(SettingRow.SectionHeader.class)
					.stream().map(SettingRow.SectionHeader::title).toList());
			boolean preview = context.computeOnClient(client -> !((ClientScreen) client.screen).currentPage().descendants(EspPreview.class).isEmpty());
			for (String expected : List.of("Targets", "Highlight", "Labels", "Target lines", "Advanced")) {
				if (!titles.contains(expected)) {
					problems.add(type.getSimpleName() + " lacks " + expected);
				}
			}

			if (!preview) {
				problems.add(type.getSimpleName() + " has no preview");
			}
		}

		report.check("Entity, Block and Storage ESP use the same sections (Targets, Highlight, Labels, Target lines, Advanced) and show a preview",
				problems.isEmpty(), problems.toString());

		ClientGameTest.openModule(context, EntityEspModule.class);
		GuiState.openSections.clear();
		ClientGameTest.openModule(context, EntityEspModule.class);
		context.waitTicks(2);
		int hiddenTuning = context.computeOnClient(client -> rowHeight(client, "update_interval"));
		int hiddenExtra = context.computeOnClient(client -> rowHeight(client, "label_padding"));
		int everyday = context.computeOnClient(client -> rowHeight(client, "max_distance"));
		report.check("Everyday ESP settings show at once; tuning (Advanced) and rarely used ones (More options) are folded away",
				everyday > 0 && hiddenTuning == 0 && hiddenExtra == 0, everyday + "/" + hiddenTuning + "/" + hiddenExtra);
		context.takeScreenshot("ux-entity-esp");

		ui.openSection("Performance");
		int shownTuning = context.computeOnClient(client -> rowHeight(client, "update_interval"));
		// The first "More options" is the Highlight section's: per-group outline switches.
		var more = ui.find(SettingRow.MoreOptions.class, m -> true);
		ui.click(more);
		context.waitTicks(2);
		int shownExtra = context.computeOnClient(client -> rowHeight(client, "outline_players"));
		report.check("Clicking Advanced and More options shows the folded settings", shownTuning > 0 && shownExtra > 0, shownTuning + "/" + shownExtra);

		// The preview follows the settings: line thickness changes without rebuilding anything.
		boolean follows = context.computeOnClient(client -> {
			var settings = femboy().modules().get(EntityEspModule.class).settings();
			return settings.get("tracer_width") != null && EspPreview.applies(settings);
		});
		report.check("The ESP preview reads the module's own settings (outline, fill, labels, target lines)", follows);
		ClientGameTest.closeMenu(context, ui);
	}

	private static int rowHeight(net.minecraft.client.Minecraft client, String id) {
		return ((ClientScreen) client.screen).currentPage().descendants(SettingRow.class).stream().filter(row -> row.setting().id().equals(id))
				.findFirst().map(row -> row.height()).orElse(-1);
	}

	/** Status in words; the Show filter; empty states. */
	private static void statusAndFilters(ClientGameTestContext context, UiDriver ui, TestReport report) {
		enable(context, ChunkBordersModule.class, true);
		String on = context.computeOnClient(client -> ModuleStatus.of(femboy().modules().get(ChunkBordersModule.class), femboy().modules()).label());
		String off = context.computeOnClient(client -> ModuleStatus.of(femboy().modules().get(CoordinatesHudModule.class), femboy().modules()).label());
		report.check("Module status is spelled out (On / Off), never only a color", on.equals("On") && off.equals("Off"), on + " / " + off);

		ClientGameTest.openMenu(context);
		ClientGameTest.showPage(context, "Visuals");
		List<String> shown = context.computeOnClient(client -> {
			ModulesPage page = (ModulesPage) ((ClientScreen) client.screen).currentPage();
			page.setFilter(ModulesPage.Filter.ON);
			return page.visibleModules().stream().map(Module::id).toList();
		});
		context.waitTicks(2);
		context.takeScreenshot("ux-filter-on");
		report.check("The Show menu's \"Turned on\" lists only modules that are on", shown.contains("chunk_borders")
				&& shown.stream().allMatch(id -> femboy().modules().get(id).isEnabled()), shown.toString());
		boolean empty = context.computeOnClient(client -> {
			var quick = femboy().quickAccess();
			for (Module module : quick.favorites()) quick.toggleFavorite(module);
			ModulesPage page = (ModulesPage) ((ClientScreen) client.screen).currentPage();
			page.setFilter(ModulesPage.Filter.FAVORITES);
			return !page.descendants(ModulesPage.EmptyState.class).isEmpty();
		});
		report.check("An empty filter explains what to do (no favorites yet: click the star)", empty);
		context.runOnClient(client -> ((ModulesPage) ((ClientScreen) client.screen).currentPage()).setFilter(ModulesPage.Filter.ALL));

		// A long option list can be narrowed by typing.
		List<Integer> numbers = new ArrayList<>();
		for (int i = 1; i <= 14; i++) numbers.add(i);
		int[] chosen = {0};
		List<Integer> narrowed = context.computeOnClient(client -> {
			var uiContext = ((ClientScreen) client.screen).uiContext();
			Dropdown<Integer> dropdown = new Dropdown<>(() -> numbers, () -> chosen[0], value -> chosen[0] = value, value -> "Option " + value);
			dropdown.setBounds(20, 20, 100, 14);
			dropdown.mouseClicked(uiContext, 30, 25, 0);
			var list = dropdown.optionList();
			list.charTyped(uiContext, new net.minecraft.client.input.CharacterEvent('1', 0));
			list.charTyped(uiContext, new net.minecraft.client.input.CharacterEvent('2', 0));
			List<Integer> result = list.shown();
			uiContext.closeOverlay();
			return result;
		});
		report.check("Long dropdown lists filter as you type (\"12\" leaves Option 12)", narrowed.equals(List.of(12)), narrowed.toString());
		ClientGameTest.closeMenu(context, ui);
		enable(context, ChunkBordersModule.class, false);
	}

	/** Home's parts can be hidden and shown. */
	private static void home(ClientGameTestContext context, TestReport report) {
		ClientGameTest.openMenu(context);
		ClientGameTest.showPage(context, "Home");
		String result = context.computeOnClient(client -> {
			GlobalOptions options = femboy().options();
			HomePage home = (HomePage) ((ClientScreen) client.screen).currentPage();
			home.rebuild();
			int withAll = home.children().size() > 0 ? count(home) : -1;
			options.homeShortcuts.set(false);
			options.homeStatus.set(false);
			home.rebuild();
			int fewer = count(home);
			options.homeShortcuts.set(true);
			options.homeStatus.set(true);
			home.rebuild();
			return withAll + " -> " + fewer + " -> " + count(home);
		});
		String[] parts = result.split(" -> ");
		report.check("Home's parts can be hidden and shown again (Customize Home)", Integer.parseInt(parts[1]) < Integer.parseInt(parts[0])
				&& parts[0].equals(parts[2]), result);
		context.takeScreenshot("ux-home");
		ClientGameTest.closeMenu(context, new UiDriver(context));
	}

	private static int count(HomePage home) {
		return home.descendants(io.github.theamethyx.femboyclient.client.gui.widget.Widget.class).size();
	}

	/** New profile and delete through the dialogs, like a player. */
	private static void profiles(ClientGameTestContext context, UiDriver ui, TestReport report) {
		ClientGameTest.openMenu(context);
		ClientGameTest.showPage(context, "Profiles");
		Button create = ui.find(Button.class, b -> b.label().equals("New profile"));
		ui.click(create);
		context.waitTicks(1);
		ui.type("ux_test");
		context.takeScreenshot("ux-profile-dialog");
		ui.key(GLFW.GLFW_KEY_ENTER);
		context.waitTicks(2);
		boolean created = context.computeOnClient(client -> femboy().config().profileExists("ux_test"));
		report.check("New profile asks for a name in a dialog and creates the profile", created);

		// Delete it from its ⋯ menu; the confirmation says what is lost, Escape would cancel.
		boolean cancelled = context.computeOnClient(client -> {
			ProfilesPage page = (ProfilesPage) ((ClientScreen) client.screen).currentPage();
			var uiContext = ((ClientScreen) client.screen).uiContext();
			page.menuFor("ux_test").choose(uiContext, "Delete...");
			return uiContext.overlay() instanceof Dialog;
		});
		String message = context.computeOnClient(client -> ((ClientScreen) client.screen).uiContext().overlay() instanceof Dialog dialog
				? dialog.confirmButton().label() : "none");
		context.takeScreenshot("ux-profile-delete");
		ui.key(GLFW.GLFW_KEY_ESCAPE);
		boolean stillThere = context.computeOnClient(client -> femboy().config().profileExists("ux_test"));
		context.runOnClient(client -> {
			ProfilesPage page = (ProfilesPage) ((ClientScreen) client.screen).currentPage();
			var uiContext = ((ClientScreen) client.screen).uiContext();
			page.menuFor("ux_test").choose(uiContext, "Delete...");
		});
		ui.key(GLFW.GLFW_KEY_ENTER);
		context.waitTicks(2);
		boolean deleted = context.computeOnClient(client -> !femboy().config().profileExists("ux_test"));
		report.check("Deleting a profile asks first (Escape keeps it, Enter deletes)", cancelled && message.equals("Delete profile") && stillThere && deleted,
				cancelled + " / " + message + " / " + stillThere + " / " + deleted);
		context.takeScreenshot("ux-profiles");
		ClientGameTest.closeMenu(context, ui);
	}

	/** The toolbar moves out of the way; the inspector's advanced row aligns elements. */
	private static void hudEditor(ClientGameTestContext context, TestReport report) {
		enable(context, CoordinatesHudModule.class, true);
		context.runOnClient(client -> client.setScreen(new HudEditorScreen(null, femboy().hud())));
		context.waitTicks(3);
		// Coordinates at the top center: under the toolbar's place, so the toolbar goes to the bottom.
		context.runOnClient(client -> ((HudEditorScreen) client.screen).moveTo("coordinates_hud", client.getWindow().getGuiScaledWidth() / 2.0 - 20, 2));
		context.waitTicks(3);
		boolean bottom = context.computeOnClient(client -> ((HudEditorScreen) client.screen).toolbarAtBottom());
		context.takeScreenshot("ux-hud-toolbar-bottom");
		// Moved to the middle of the left edge: the top is free again.
		context.runOnClient(client -> ((HudEditorScreen) client.screen).moveTo("coordinates_hud", 4, client.getWindow().getGuiScaledHeight() / 2.0));
		context.waitTicks(3);
		boolean top = context.computeOnClient(client -> !((HudEditorScreen) client.screen).toolbarAtBottom());
		String result = bottom + "," + top;
		report.check("The HUD editor's toolbar moves to the bottom while an element is under it at the top, and back",
				result.equals("true,true"), result);
		context.runOnClient(client -> {
			femboy().hud().layout().resetAll();
			client.setScreen(null);
		});
		enable(context, CoordinatesHudModule.class, false);
	}

	/** Ctrl+number jumps to a section; arrows move through the module list; Space switches the selected module. */
	private static void keyboard(ClientGameTestContext context, TestReport report) {
		GuiState.selectedModule = "entity_esp";
		ClientGameTest.openMenu(context);
		context.getInput().holdControl();
		context.getInput().pressKey(GLFW.GLFW_KEY_4);
		context.getInput().releaseControl();
		context.waitTicks(2);
		String page = ClientGameTest.currentTitle(context);
		String first = context.computeOnClient(client -> ((ModulesPage) ((ClientScreen) client.screen).currentPage()).selected().id());
		context.getInput().pressKey(GLFW.GLFW_KEY_DOWN);
		context.waitTicks(1);
		String second = context.computeOnClient(client -> ((ModulesPage) ((ClientScreen) client.screen).currentPage()).selected().id());
		boolean before = context.computeOnClient(client -> femboy().modules().get(second).isEnabled());
		context.getInput().pressKey(GLFW.GLFW_KEY_SPACE);
		context.waitTicks(1);
		boolean after = context.computeOnClient(client -> femboy().modules().get(second).isEnabled());
		context.runOnClient(client -> femboy().modules().setEnabled(femboy().modules().get(second), before));
		report.check("Ctrl+4 opens Visuals; Down selects the next module; Space switches it", page.equals("Visuals") && !first.equals(second) && before != after,
				page + " / " + first + " -> " + second + " / " + before + " -> " + after);
		ClientGameTest.closeMenu(context, new UiDriver(context));
	}

	/** No overlaps or clipped regions at small, medium and large windows, and at fixed menu scales. */
	private static void layouts(ClientGameTestContext context, TestReport report) {
		List<String> problems = new ArrayList<>();
		ClientGameTest.openMenu(context);
		Set<String> pages = Set.of("Home", "Visuals", "Profiles", "Settings");
		for (int[] size : new int[][] {{854, 480}, {1280, 720}, {1920, 1080}}) {
			context.getInput().resizeWindow(size[0], size[1]);
			context.waitTicks(3);
			for (String title : pages) {
				ClientGameTest.showPage(context, title);
				List<String> found = context.computeOnClient(client -> ((ClientScreen) client.screen).layoutProblems());
				String summary = context.computeOnClient(client -> ((ClientScreen) client.screen).layoutSummary());
				found.forEach(problem -> problems.add(size[0] + "x" + size[1] + " " + title + " (" + summary + "): " + problem));
			}

			context.takeScreenshot("ux-visuals-" + size[0] + "x" + size[1]);
		}

		context.getInput().resizeWindow(1280, 720);
		for (GlobalOptions.MenuScale scale : List.of(GlobalOptions.MenuScale.X1, GlobalOptions.MenuScale.X2, GlobalOptions.MenuScale.MINECRAFT)) {
			context.runOnClient(client -> femboy().options().menuScale.set(scale));
			context.waitTicks(3);
			List<String> found = context.computeOnClient(client -> ((ClientScreen) client.screen).layoutProblems());
			found.forEach(problem -> problems.add("scale " + scale.label() + ": " + problem));
		}

		context.runOnClient(client -> femboy().options().menuScale.set(GlobalOptions.MenuScale.AUTO));
		context.waitTicks(2);
		report.check("No overlapping or clipped layout at 854x480, 1280x720, 1920x1080 and menu scales 1x, 2x and Minecraft's",
				problems.isEmpty(), problems.toString());
		ClientGameTest.closeMenu(context, new UiDriver(context));
	}

	/** The menu opens cleanly again and again: no stale list, field or overlay is left open. */
	private static void reopen(ClientGameTestContext context, TestReport report) {
		List<String> stale = new ArrayList<>();
		for (int i = 0; i < 5; i++) {
			ClientGameTest.openMenu(context);
			// Leave something open (the search, or a dialog) and close the whole menu from outside, as the game can.
			int round = i;
			context.runOnClient(client -> {
				ClientScreen screen = (ClientScreen) client.screen;
				if (round % 2 == 0) {
					screen.openPalette();
				} else {
					Dialog.prompt("Test", "Left open on purpose.", "", "", 10, "OK", text -> null).open(screen.uiContext());
				}
			});
			String state = context.computeOnClient(client -> {
				ClientScreen screen = (ClientScreen) client.screen;
				return screen.uiContext().overlay() == null ? "no overlay" : "ok";
			});
			context.runOnClient(client -> client.setScreen(null));
			context.waitTicks(1);
			ClientGameTest.openMenu(context);
			String after = context.computeOnClient(client -> {
				ClientScreen screen = (ClientScreen) client.screen;
				return screen.uiContext().overlay() != null ? "overlay left open" : screen.uiContext().focused() != null ? "focus left" : "clean";
			});
			if (!state.equals("ok") || !after.equals("clean")) {
				stale.add(i + ": " + state + " / " + after);
			}

			ClientGameTest.closeMenu(context, new UiDriver(context));
		}

		report.check("Opening and closing the menu five times leaves no stale overlay or focus", stale.isEmpty(), stale.toString());
	}
}
