package io.github.theamethyx.femboyclient.gametest;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import org.lwjgl.glfw.GLFW;

import net.minecraft.core.BlockPos;

import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

import io.github.theamethyx.femboyclient.client.FemboyClient;
import io.github.theamethyx.femboyclient.client.GlobalOptions;
import io.github.theamethyx.femboyclient.client.diag.DiagnosticReport;
import io.github.theamethyx.femboyclient.client.gui.ClientScreen;
import io.github.theamethyx.femboyclient.client.gui.CommandPalette;
import io.github.theamethyx.femboyclient.client.gui.GuiState;
import io.github.theamethyx.femboyclient.client.gui.HudEditorScreen;
import io.github.theamethyx.femboyclient.client.gui.QuickWheelScreen;
import io.github.theamethyx.femboyclient.client.gui.Theme;
import io.github.theamethyx.femboyclient.client.gui.ThemePresets;
import io.github.theamethyx.femboyclient.client.gui.page.SettingsPage;
import io.github.theamethyx.femboyclient.client.hud.HudManager;
import io.github.theamethyx.femboyclient.client.hud.Notifications;
import io.github.theamethyx.femboyclient.client.modules.ChunkBordersModule;
import io.github.theamethyx.femboyclient.client.modules.CompassModule;
import io.github.theamethyx.femboyclient.client.modules.CoordinatesHudModule;
import io.github.theamethyx.femboyclient.client.modules.ExperienceInfoModule;
import io.github.theamethyx.femboyclient.client.modules.KillAuraModule;
import io.github.theamethyx.femboyclient.client.modules.MentionAlertsModule;
import io.github.theamethyx.femboyclient.client.modules.SessionStatsModule;
import io.github.theamethyx.femboyclient.client.modules.ShapePlannerModule;
import io.github.theamethyx.femboyclient.client.smart.SessionTimeline;
import io.github.theamethyx.femboyclient.core.config.ConfigDiff;
import io.github.theamethyx.femboyclient.core.hud.HudLayouts;
import io.github.theamethyx.femboyclient.core.module.Module;
import io.github.theamethyx.femboyclient.core.module.ModuleHealth;
import io.github.theamethyx.femboyclient.core.rules.Rule;
import io.github.theamethyx.femboyclient.core.tools.ShapeGenerator;

/**
 * 1.0: command palette, favorites and the quick wheel, Theme Studio, the HUD editor and layouts, profiles (diff, history,
 * rename, per-profile HUD), restore points, rules, the timeline, the Performance Center and profiler, the health monitor,
 * notifications and the new modules.
 */
final class ProductTest {
	private ProductTest() {
	}

	private static FemboyClient femboy() {
		return FemboyClient.get();
	}

	private static void enable(ClientGameTestContext context, Class<? extends Module> type, boolean on) {
		context.runOnClient(client -> femboy().modules().setEnabled(femboy().modules().get(type), on));
	}

	static void run(ClientGameTestContext context, TestSingleplayerContext singleplayer, UiDriver ui, TestReport report) {
		singleplayer.getServer().runCommand("tp @a 0.5 -60 0.5 0 20");
		context.waitTicks(5);
		palette(context, ui, report);
		favoritesAndWheel(context, report);
		theme(context, ui, report);
		hudEditor(context, report);
		profilesAndRestore(context, report);
		rulesAndTimeline(context, report);
		performance(context, ui, report);
		notifications(context, report);
		newModules(context, report);
	}

	private static String overlayName(ClientGameTestContext context) {
		return context.computeOnClient(client -> {
			if (!(client.screen instanceof ClientScreen screen)) return "no menu";
			var overlay = screen.uiContext().overlay();
			return overlay == null ? "none" : overlay.getClass().getSimpleName();
		});
	}

	private static void palette(ClientGameTestContext context, UiDriver ui, TestReport report) {
		enable(context, CoordinatesHudModule.class, false);
		ClientGameTest.openMenu(context);
		context.waitTicks(2);
		String focus = context.computeOnClient(client -> {
			var focused = ((ClientScreen) client.screen).uiContext().focused();
			return focused == null ? "nothing" : focused.getClass().getSimpleName();
		});
		context.getInput().holdControl();
		context.getInput().pressKey(GLFW.GLFW_KEY_K);
		context.getInput().releaseControl();
		context.waitTicks(2);
		String overlay = overlayName(context);
		ui.type("coordin");
		context.waitTicks(2);
		String first = context.computeOnClient(client -> {
			if (((ClientScreen) client.screen).uiContext().overlay() instanceof CommandPalette palette && !palette.entries().isEmpty()) {
				return palette.entries().getFirst().title();
			}

			return "none";
		});
		context.takeScreenshot("palette-search");
		ui.key(GLFW.GLFW_KEY_ENTER);
		context.waitTicks(2);
		boolean toggled = context.computeOnClient(client -> femboy().modules().get(CoordinatesHudModule.class).isEnabled());
		report.check("Ctrl+K opens the command palette; typing finds Coordinates first and Enter turns it on",
				overlay.equals("CommandPalette") && first.equals("Coordinates") && toggled, overlay + " / " + first + " / " + toggled + " (focus before: " + focus + ")");

		context.runOnClient(client -> ((ClientScreen) client.screen).openPalette().setQuery("block esp"));
		context.waitTicks(1);
		ui.key(GLFW.GLFW_KEY_TAB);
		context.waitTicks(2);
		String selected = GuiState.selectedModule;
		report.check("Tab on a module in the palette opens its settings instead (Block ESP)", "block_esp".equals(selected), String.valueOf(selected));

		context.runOnClient(client -> ((ClientScreen) client.screen).openPalette().setQuery("ore esp"));
		context.waitTicks(1);
		String alias = context.computeOnClient(client -> ((CommandPalette) ((ClientScreen) client.screen).uiContext().overlay()).entries().getFirst().title());
		report.check("Aliases find renamed modules: 'ore esp' finds Block ESP", alias.equals("Block ESP"), alias);
		ui.key(GLFW.GLFW_KEY_ESCAPE);
		context.waitTicks(1);
		report.check("Escape closes the palette", overlayName(context).equals("none"), overlayName(context));

		context.runOnClient(client -> ((ClientScreen) client.screen).openPalette().setQuery("appearance"));
		context.waitTicks(1);
		ui.key(GLFW.GLFW_KEY_ENTER);
		context.waitTicks(2);
		String tab = context.computeOnClient(client -> client.screen instanceof ClientScreen screen && screen.currentPage() instanceof SettingsPage settings
				? settings.activeTab().navLabel() : "?");
		report.check("The palette jumps to a settings tab (Appearance)", tab.equals("Appearance"), tab);
		ClientGameTest.closeMenu(context, ui);
		enable(context, CoordinatesHudModule.class, false);
	}

	private static void favoritesAndWheel(ClientGameTestContext context, TestReport report) {
		context.runOnClient(client -> {
			var quick = femboy().quickAccess();
			for (Module module : quick.favorites()) quick.toggleFavorite(module);
			quick.toggleFavorite(femboy().modules().get(ChunkBordersModule.class));
		});
		List<String> labels = context.computeOnClient(client -> {
			QuickWheelScreen wheel = new QuickWheelScreen();
			client.setScreen(wheel);
			List<String> shown = wheel.labels();
			wheel.choose(0);
			return shown;
		});
		context.waitTicks(2);
		boolean on = context.computeOnClient(client -> femboy().modules().get(ChunkBordersModule.class).isEnabled());
		report.check("The quick wheel lists favorites first and toggles the chosen one (Chunk Borders)",
				!labels.isEmpty() && labels.getFirst().equals("Chunk Borders") && on, labels + " / " + on);
		boolean recent = context.computeOnClient(client -> femboy().quickAccess().recent().stream().anyMatch(m -> m.id().equals("chunk_borders")));
		report.check("Toggled modules appear under recently used", recent);
		enable(context, ChunkBordersModule.class, false);

		ClientGameTest.openMenu(context);
		ClientGameTest.showPage(context, "Home");
		context.waitTicks(3);
		context.takeScreenshot("home-favorites");
		ClientGameTest.closeMenu(context, new UiDriver(context));
	}

	private static void theme(ClientGameTestContext context, UiDriver ui, TestReport report) {
		int before = context.computeOnClient(client -> Theme.PANEL);
		List<String> problems = context.computeOnClient(client -> new ThemePresets(femboy().options(), femboy().config())
				.apply(ThemePresets.BUILT_IN.stream().filter(p -> p.name().equals("High contrast")).findFirst().orElseThrow().values()));
		boolean contrast = context.computeOnClient(client -> Theme.current() == Theme.Palette.HIGH_CONTRAST && Theme.TEXT == 0xFFFFFFFF);
		report.check("Theme Studio presets apply (High contrast: white text on black)", problems.isEmpty() && contrast, problems.toString());

		ClientGameTest.openMenu(context);
		ClientGameTest.showPage(context, "Settings");
		context.runOnClient(client -> ((SettingsPage) ((ClientScreen) client.screen).currentPage()).showTab("Appearance"));
		context.waitTicks(3);
		context.takeScreenshot("theme-studio-high-contrast");

		String saved = context.computeOnClient(client -> {
			ThemePresets presets = new ThemePresets(femboy().options(), femboy().config());
			String problem = presets.save("Test look");
			if (problem != null) return problem;
			String text = presets.export("Test look");
			ThemePresets.ImportResult imported = presets.importTheme(text);
			ThemePresets.ImportResult bad = presets.importTheme("{\"settings\": {\"theme\": \"NOPE\", \"evil\": 1}}");
			presets.delete("Test look");
			presets.delete(imported.name());
			presets.delete(bad.name());
			return imported.error() == null && bad.warnings().size() == 2 ? "ok" : imported + " / " + bad;
		});
		report.check("Themes save, export and import; invalid imported values are skipped and listed", saved.equals("ok"), saved);

		context.runOnClient(client -> {
			femboy().options().corners.set(GlobalOptions.CornerStyle.ROUND);
			femboy().options().panelOpacity.set(80.0);
		});
		float radius = context.computeOnClient(client -> Theme.RADIUS_SCALE);
		int panelAlpha = context.computeOnClient(client -> Theme.PANEL >>> 24);
		context.waitTicks(2);
		context.takeScreenshot("theme-studio-round-translucent");
		report.check("Corner style and panel opacity change the drawing", radius == GlobalOptions.CornerStyle.ROUND.scale && panelAlpha < 0xF0,
				radius + " / " + panelAlpha);
		context.runOnClient(client -> new ThemePresets(femboy().options(), femboy().config()).apply(java.util.Map.of()));
		int after = context.computeOnClient(client -> Theme.PANEL);
		report.check("Applying the defaults restores the original look", after == before, Integer.toHexString(after) + " vs " + Integer.toHexString(before));
		ClientGameTest.closeMenu(context, ui);
	}

	private static HudManager.Placed placed(ClientGameTestContext context, String id) {
		return context.computeOnClient(client -> femboy().hud().placed().stream().filter(p -> p.moduleId().equals(id)).findFirst().orElse(null));
	}

	private static void hudEditor(ClientGameTestContext context, TestReport report) {
		enable(context, CoordinatesHudModule.class, true);
		enable(context, CompassModule.class, true);
		context.runOnClient(client -> client.setScreen(new HudEditorScreen(null, femboy().hud())));
		context.waitTicks(3);
		HudManager.Placed start = placed(context, "coordinates_hud");
		context.runOnClient(client -> ((HudEditorScreen) client.screen).moveTo("coordinates_hud", 200, 120));
		context.waitTicks(2);
		HudManager.Placed moved = placed(context, "coordinates_hud");
		report.check("HUD editor: an element can be placed anywhere", moved != null && moved.custom() && Math.abs(moved.x() - 200) <= 1, String.valueOf(moved));

		context.runOnClient(client -> {
			femboy().hud().layout().checkpoint();
			femboy().hud().layout().update("coordinates_hud", e -> e.withScale(1.5f).withOpacity(0.6f));
		});
		context.waitTicks(2);
		HudManager.Placed scaled = placed(context, "coordinates_hud");
		report.check("HUD editor: scaling makes the element larger (1.5x)", scaled != null && moved != null && scaled.width() >= moved.width() * 1.4,
				moved + " -> " + scaled);
		context.takeScreenshot("hud-editor");

		context.runOnClient(client -> {
			femboy().hud().layout().checkpoint();
			femboy().hud().layout().update("compass", e -> e.withHidden(true));
		});
		context.waitTicks(2);
		boolean faintInEditor = context.computeOnClient(client -> femboy().hud().placed().stream().anyMatch(p -> p.moduleId().equals("compass") && p.hidden()));
		context.runOnClient(client -> client.setScreen(null));
		context.waitTicks(2);
		boolean goneInGame = placed(context, "compass") == null;
		report.check("Hidden elements show faintly in the editor and not in the game", faintInEditor && goneInGame, faintInEditor + " / " + goneInGame);

		boolean undone = context.computeOnClient(client -> femboy().hud().layout().undo() && !femboy().hud().layout().element("compass").hidden()
				&& femboy().hud().layout().undo() && femboy().hud().layout().element("coordinates_hud").scale() == 1);
		report.check("Undo reverts the last changes (visibility, then scale)", undone);

		context.runOnClient(client -> femboy().hud().layout().update("compass", e -> e.withCondition(HudLayouts.Condition.IN_COMBAT)));
		context.waitTicks(2);
		boolean conditional = placed(context, "compass") == null;
		context.runOnClient(client -> femboy().hud().layout().update("compass", e -> e.withCondition(HudLayouts.Condition.ALWAYS)));
		context.waitTicks(2);
		report.check("An element set to show only in combat stays hidden out of combat", conditional && placed(context, "compass") != null);

		String layouts = context.computeOnClient(client -> {
			var layout = femboy().hud().layout();
			String problem = layout.changeLayouts(l -> l.create("Test layout"));
			if (problem != null) return problem;
			layout.reset("coordinates_hud");
			boolean corner = !layout.element("coordinates_hud").placed();
			layout.changeLayouts(l -> l.setActive(HudLayouts.DEFAULT));
			boolean back = layout.element("coordinates_hud").placed();
			layout.changeLayouts(l -> l.delete("Test layout"));
			return corner && back ? "ok" : corner + "/" + back;
		});
		report.check("Layouts are independent and can be switched", layouts.equals("ok"), layouts);
		context.runOnClient(client -> femboy().hud().layout().resetAll());
		enable(context, CompassModule.class, false);
		enable(context, CoordinatesHudModule.class, false);
	}

	private static void profilesAndRestore(ClientGameTestContext context, TestReport report) {
		String result = context.computeOnClient(client -> {
			var config = femboy().config();
			String original = config.activeProfile();
			config.createProfile("diff_test");
			femboy().modules().setEnabled(femboy().modules().get(ChunkBordersModule.class), true);
			femboy().hud().layout().placeAt("coordinates_hud", 10, 10, 20, 10, 400, 300);
			List<ConfigDiff.Difference> diff = config.diffProfiles(original, "diff_test");
			boolean diffOk = diff != null && diff.stream().anyMatch(d -> d.kind() == ConfigDiff.Kind.ENABLED && d.moduleId().equals("chunk_borders"));
			config.switchProfile("diff_test");
			boolean hudPerProfile = !femboy().hud().layout().element("coordinates_hud").placed();
			boolean history = original.equals(config.previousProfile());
			String renamed = config.renameProfile("diff_test", "diff_renamed");
			boolean revert = config.revertProfile() == null && config.activeProfile().equals(original);
			boolean hudBack = femboy().hud().layout().element("coordinates_hud").placed();
			config.deleteProfile("diff_renamed");
			femboy().modules().setEnabled(femboy().modules().get(ChunkBordersModule.class), false);
			femboy().hud().layout().resetAll();
			return diffOk && hudPerProfile && history && renamed == null && revert && hudBack ? "ok"
					: diffOk + " " + hudPerProfile + " " + history + " " + renamed + " " + revert + " " + hudBack;
		});
		report.check("Profiles: diff, per-profile HUD layout, history, rename and revert", result.equals("ok"), result);

		String restored = context.computeOnClient(client -> {
			var config = femboy().config();
			var setting = femboy().modules().get(ChunkBordersModule.class).settings().get("radius");
			((io.github.theamethyx.femboyclient.core.setting.NumberSetting) setting).set(4.0);
			String problem = config.createRestorePoint("Game test");
			if (problem != null) return problem;
			String id = config.restorePoints().getFirst().id();
			((io.github.theamethyx.femboyclient.core.setting.NumberSetting) setting).set(2.0);
			problem = config.restore(id);
			double value = (Double) setting.get();
			setting.reset();
			return problem == null && value == 4 ? "ok" : problem + " / " + value;
		});
		report.check("A restore point brings back earlier settings", restored.equals("ok"), restored);
		var validation = context.computeOnClient(client -> femboy().config().validateProfiles());
		report.check("The configuration validator checks every profile", !validation.isEmpty() && validation.values().stream().allMatch(List::isEmpty),
				validation.toString());
	}

	private static void rulesAndTimeline(ClientGameTestContext context, TestReport report) {
		List<Rule> previous = context.computeOnClient(client -> femboy().rules().engine().rules());
		context.runOnClient(client -> {
			femboy().config().createProfile("rule_test");
			femboy().rules().setRules(List.of(
					new Rule("Compass when testing", true, Rule.Trigger.PROFILE_CHANGED, true,
							List.of(new Rule.Condition(Rule.ConditionType.PROFILE_IS, "rule_test")),
							List.of(new Rule.Action(Rule.ActionType.NOTIFY, "rule ran"), new Rule.Action(Rule.ActionType.ENABLE_VISUAL_MODULE, "compass")), 0, false),
					new Rule("Not allowed", true, Rule.Trigger.PROFILE_CHANGED, true, List.of(),
							List.of(new Rule.Action(Rule.ActionType.ENABLE_VISUAL_MODULE, "kill_aura")), 0, false),
					new Rule("Dry", true, Rule.Trigger.PROFILE_CHANGED, true, List.of(),
							List.of(new Rule.Action(Rule.ActionType.DISABLE_VISUAL_MODULE, "compass")), 0, true)));
			femboy().config().switchProfile("rule_test");
		});
		context.waitTicks(3);
		boolean compass = context.computeOnClient(client -> femboy().modules().get(CompassModule.class).isEnabled());
		boolean killAura = context.computeOnClient(client -> femboy().modules().get(KillAuraModule.class).isEnabled());
		boolean notified = context.computeOnClient(client -> Notifications.history().stream().anyMatch(t -> t.text().equals("rule ran")));
		boolean dry = context.computeOnClient(client -> femboy().rules().engine().history().stream().anyMatch(e -> e.dryRun() && e.rule().equals("Dry")));
		boolean refused = context.computeOnClient(client -> femboy().rules().engine().history().stream()
				.anyMatch(e -> e.rule().equals("Not allowed") && e.problem() != null));
		report.check("Rules run on their trigger: a notification and a visual module turned on", compass && notified, compass + " / " + notified);
		report.check("Rules cannot turn on automation modules, and dry runs only record", !killAura && refused && dry, killAura + " / " + refused + " / " + dry);

		boolean timeline = context.computeOnClient(client -> {
			SessionTimeline line = femboy().timeline();
			line.record(SessionTimeline.Kind.MARKER, "Test marker", "");
			return !line.events("", SessionTimeline.Kind.PROFILE, false).isEmpty() && !line.events("", SessionTimeline.Kind.RULE, false).isEmpty()
					&& line.events("test marker", null, false).size() == 1;
		});
		report.check("The timeline records profile switches, rules and markers, and search finds them", timeline);

		ClientGameTest.openMenu(context);
		ClientGameTest.showPage(context, "Settings");
		context.runOnClient(client -> ((SettingsPage) ((ClientScreen) client.screen).currentPage()).showTab("Rules"));
		context.waitTicks(3);
		context.takeScreenshot("rules");
		context.runOnClient(client -> ((SettingsPage) ((ClientScreen) client.screen).currentPage()).showTab("Timeline"));
		context.waitTicks(3);
		context.takeScreenshot("timeline");
		ClientGameTest.closeMenu(context, new UiDriver(context));

		context.runOnClient(client -> {
			femboy().rules().setRules(previous);
			femboy().config().switchProfile("default");
			femboy().config().deleteProfile("rule_test");
			femboy().modules().setEnabled(femboy().modules().get(CompassModule.class), false);
		});
	}

	private static void performance(ClientGameTestContext context, UiDriver ui, TestReport report) {
		enable(context, ChunkBordersModule.class, false);
		String started = context.computeOnClient(client -> femboy().profiler().start(femboy().modules().get(ChunkBordersModule.class), 2));
		String refused = context.computeOnClient(client -> femboy().profiler().check(femboy().modules().get(KillAuraModule.class)));
		ClientGameTest.openMenu(context);
		ClientGameTest.showPage(context, "Settings");
		context.runOnClient(client -> ((SettingsPage) ((ClientScreen) client.screen).currentPage()).showTab("Performance"));
		context.waitTicks(40);
		context.takeScreenshot("performance-center");
		context.waitTicks(120);
		var results = context.computeOnClient(client -> femboy().profiler().results());
		boolean restored = context.computeOnClient(client -> !femboy().modules().get(ChunkBordersModule.class).isEnabled());
		report.check("The on/off comparison measures both halves and leaves the module as it was",
				started == null && !results.isEmpty() && results.getFirst().framesOn() > 0 && results.getFirst().framesOff() > 0 && restored,
				started + " / " + results + " / " + restored);
		report.check("Automation modules cannot be compared (switching them on would make them act)", refused != null, String.valueOf(refused));

		context.runOnClient(client -> ((SettingsPage) ((ClientScreen) client.screen).currentPage()).showTab("Developer"));
		context.waitTicks(3);
		context.takeScreenshot("developer-health");
		ModuleHealth.State health = context.computeOnClient(client -> femboy().modules().health(femboy().modules().get(KillAuraModule.class)).state());
		report.check("The health monitor reports module states (Kill Aura off)", health == ModuleHealth.State.OFF, String.valueOf(health));
		ClientGameTest.closeMenu(context, ui);

		context.runOnClient(client -> DiagnosticReport.export(text -> { }));
		context.waitTicks(20);
		boolean report2 = context.computeOnClient(client -> {
			Path reports = femboy().config().root().resolve("reports");
			try (Stream<Path> files = Files.list(reports)) {
				return files.anyMatch(path -> path.getFileName().toString().startsWith("report-"));
			} catch (Exception e) {
				return false;
			}
		});
		report.check("A diagnostic report can be exported", report2);
	}

	private static void notifications(ClientGameTestContext context, TestReport report) {
		int count = context.computeOnClient(client -> {
			for (int i = 0; i < 3; i++) Notifications.post(Notifications.Category.GENERAL, "Repeat", "same text", Theme.INFO);
			return Notifications.history().getLast().count();
		});
		report.check("Repeated notifications are folded into one with a count", count == 3, String.valueOf(count));
	}

	private static void newModules(ClientGameTestContext context, TestReport report) {
		enable(context, CompassModule.class, true);
		enable(context, ExperienceInfoModule.class, true);
		enable(context, SessionStatsModule.class, true);
		enable(context, ShapePlannerModule.class, true);
		context.runOnClient(client -> {
			ShapePlannerModule planner = femboy().modules().get(ShapePlannerModule.class);
			planner.settings().get("shape").load(com.google.gson.JsonParser.parseString("\"SPHERE\""));
			planner.settings().get("radius").load(com.google.gson.JsonParser.parseString("5"));
			planner.setCenter(new BlockPos(0, -58, 6));
		});
		context.waitTicks(20);
		context.takeScreenshot("new-modules");
		List<String> shown = context.computeOnClient(client -> femboy().hud().placed().stream().map(HudManager.Placed::moduleId).toList());
		boolean all = shown.containsAll(List.of("compass", "xp_info", "session_stats", "shape_planner"));
		report.check("Compass, Experience, Session Stats and Shape Planner show on the HUD", all, shown.toString());
		int planned = context.computeOnClient(client -> femboy().modules().get(ShapePlannerModule.class).shapeResult().count());
		int expected = ShapeGenerator.generate(ShapeGenerator.Shape.SPHERE, 5, 5, true).count();
		String work = context.computeOnClient(client -> femboy().modules().get(ShapePlannerModule.class).workload());
		report.check("Shape Planner plans a hollow sphere and draws its outlines", planned == expected && work != null && !work.contains(" 0 outlines"),
				planned + " / " + work);

		boolean alerted = context.computeOnClient(client -> {
			MentionAlertsModule alerts = femboy().modules().get(MentionAlertsModule.class);
			femboy().modules().setEnabled(alerts, true);
			alerts.settings().get("keywords").load(new com.google.gson.JsonPrimitive("diamonds"));
			String name = client.player.getGameProfile().name();
			boolean byName = alerts.check("<Someone> hey " + name + ", come here");
			boolean byKeyword = alerts.check("<Someone> found DIAMONDS!");
			boolean notPartial = !alerts.check("<Someone> diamondsword");
			boolean notOwn = !alerts.check("<" + name + "> diamonds");
			femboy().modules().setEnabled(alerts, false);
			alerts.settings().get("keywords").reset();
			return byName && byKeyword && notPartial && notOwn;
		});
		report.check("Mention Alerts match your name and keywords as whole words, not your own messages", alerted);

		for (Class<? extends Module> type : List.of(CompassModule.class, ExperienceInfoModule.class, SessionStatsModule.class, ShapePlannerModule.class)) {
			context.runOnClient(client -> femboy().modules().resetToDefaults(femboy().modules().get(type)));
		}
	}
}
