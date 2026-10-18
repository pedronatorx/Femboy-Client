package io.github.theamethyx.femboyclient.core.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.theamethyx.femboyclient.core.diag.DiagnosticLogger;
import io.github.theamethyx.femboyclient.core.diag.PerformanceMonitor;
import io.github.theamethyx.femboyclient.core.module.EnvironmentPolicy;
import io.github.theamethyx.femboyclient.core.module.Module;
import io.github.theamethyx.femboyclient.core.module.ModuleCategory;
import io.github.theamethyx.femboyclient.core.module.ModuleManager;
import io.github.theamethyx.femboyclient.core.setting.BooleanSetting;
import io.github.theamethyx.femboyclient.core.setting.ModuleSettings;
import io.github.theamethyx.femboyclient.core.setting.NumberSetting;

/** Data preservation, restore points, diffs, history, renaming and the full reset (1.0). */
class ConfigToolsTest {
	@TempDir
	Path dir;

	private final AtomicLong clock = new AtomicLong(1_000);
	private ModuleManager modules;
	private ModuleSettings global;
	private BooleanSetting animations;
	private TestModule module;

	static final class TestModule extends Module {
		final NumberSetting size = settings().add(new NumberSetting("size", "Size", "", 2, 1, 5, 1, ""));
		final BooleanSetting flag = settings().add(new BooleanSetting("flag", "Flag", "", false));

		TestModule() {
			super("test_module", "Test Module", "", ModuleCategory.HUD, false, Module.NO_KEY);
		}
	}

	@BeforeEach
	void setUp() {
		modules = new ModuleManager(new DiagnosticLogger(), new PerformanceMonitor(), EnvironmentPolicy.IN_WORLD);
		module = modules.register(new TestModule());
		global = new ModuleSettings();
		animations = global.add(new BooleanSetting("animations", "Animations", "", true));
	}

	private ConfigurationManager newConfig() {
		ConfigurationManager config = new ConfigurationManager(dir, modules, global, new DiagnosticLogger(), clock::get);
		config.setProfileOptions(Set.of("animations"));
		config.setProfileSections(Set.of("hud_layout"));
		return config;
	}

	private Path profile(String name) {
		return dir.resolve("profiles").resolve(name + ".json");
	}

	@Test
	void dataForUnknownModulesAndSettingsIsKeptWhenSaving() throws IOException {
		newConfig().load();
		Files.writeString(profile("default"), """
				{ "schemaVersion": 3, "modules": {
				    "test_module": { "enabled": true, "settings": { "size": 4, "future_setting": [1, 2] } },
				    "module_from_a_newer_version": { "enabled": true, "settings": { "radius": 9 } } } }
				""");
		ConfigurationManager config = newConfig();
		List<String> report = config.load();

		assertTrue(report.isEmpty(), "unknown data is not a problem: " + report);
		assertEquals(4.0, module.size.get());
		assertEquals(Set.of("module_from_a_newer_version"), modules.retainedModules());
		module.size.set(3.0);
		config.saveNow();

		JsonObject saved = JsonParser.parseString(Files.readString(profile("default"))).getAsJsonObject().getAsJsonObject("modules");
		assertEquals(9, saved.getAsJsonObject("module_from_a_newer_version").getAsJsonObject("settings").get("radius").getAsInt());
		JsonObject settings = saved.getAsJsonObject("test_module").getAsJsonObject("settings");
		assertEquals(3, settings.get("size").getAsInt());
		assertEquals("[1,2]", settings.get("future_setting").toString());
	}

	@Test
	void aProfileWhoseValuesAreReplacedIsCopiedFirst() throws IOException {
		newConfig().load();
		Files.writeString(profile("default"), """
				{ "schemaVersion": 3, "modules": { "test_module": { "enabled": true, "settings": { "size": 50 } } } }
				""");
		List<String> report = newConfig().load();

		assertTrue(report.stream().anyMatch(line -> line.contains("before-repair")), report::toString);
		try (Stream<Path> files = Files.list(dir.resolve("profiles"))) {
			Path copy = files.filter(path -> path.getFileName().toString().startsWith("default.json.before-repair-")).findFirst().orElseThrow();
			assertTrue(Files.readString(copy).contains("\"size\": 50"), "the original value survives in the copy");
		}
	}

	@Test
	void restorePointsBringBackEarlierStateAndCanBeUndone() {
		ConfigurationManager config = newConfig();
		config.load();
		module.size.set(4.0);
		assertNull(config.createRestorePoint("Size four"));
		clock.addAndGet(10);

		module.size.set(1.0);
		modules.setEnabled(module, true);
		config.saveNow();

		List<ConfigurationManager.RestorePoint> points = config.restorePoints();
		assertEquals(1, points.size());
		assertEquals("Size four", points.getFirst().label());
		assertEquals(List.of("default"), points.getFirst().profiles());

		assertNull(config.restore(points.getFirst().id()));
		assertEquals(4.0, module.size.get());
		assertFalse(module.isEnabled());

		List<ConfigurationManager.RestorePoint> after = config.restorePoints();
		assertEquals(2, after.size(), "restoring made a restore point of the state it replaced");
		assertTrue(after.getFirst().label().startsWith("Before restoring"), after.getFirst().label());
		assertNull(config.restore(after.getFirst().id()));
		assertEquals(1.0, module.size.get(), "the restore was undone");

		assertNotNull(config.restore("../../etc"), "ids cannot leave the folder");
		assertNull(config.deleteRestorePoint(points.getFirst().id()));
	}

	@Test
	void onlyTheNewestRestorePointsAreKept() {
		ConfigurationManager config = newConfig();
		config.load();
		for (int i = 0; i < ConfigurationManager.MAX_RESTORE_POINTS + 3; i++) {
			assertNull(config.createRestorePoint("Point " + i));
		}

		List<ConfigurationManager.RestorePoint> points = config.restorePoints();
		assertEquals(ConfigurationManager.MAX_RESTORE_POINTS, points.size());
		assertEquals("Point " + (ConfigurationManager.MAX_RESTORE_POINTS + 2), points.getFirst().label());
	}

	@Test
	void diffListsExactlyWhatDiffers() {
		ConfigurationManager config = newConfig();
		config.load();
		assertNull(config.createProfile("other"));
		module.size.set(2.0); // the default: equal to a profile that never set it
		modules.setEnabled(module, true);
		modules.setKey(module, 71);
		module.flag.set(true);
		animations.set(false);

		List<ConfigDiff.Difference> diff = config.diffProfiles("default", "other");

		assertNotNull(diff);
		assertEquals(List.of(ConfigDiff.Kind.ENABLED, ConfigDiff.Kind.KEY, ConfigDiff.Kind.SETTING, ConfigDiff.Kind.OPTION),
				diff.stream().map(ConfigDiff.Difference::kind).toList(), diff::toString);
		ConfigDiff.Difference flag = diff.get(2);
		assertEquals("Flag", flag.what());
		assertEquals("On", flag.left());
		assertEquals("Off", flag.right());
		assertEquals("Animations", diff.get(3).what());
		assertTrue(config.diffProfiles("default", "default").isEmpty());
		assertNull(config.diffProfiles("default", "missing"));
	}

	@Test
	void historyRemembersEarlierProfilesAndRevertGoesBack() {
		ConfigurationManager config = newConfig();
		config.load();
		assertNull(config.createProfile("pvp"));
		assertNull(config.revertProfile() == null ? "expected a reason" : null);

		assertNull(config.switchProfile("pvp"));
		clock.addAndGet(5);
		assertEquals("default", config.previousProfile());
		assertNull(config.revertProfile());
		assertEquals("default", config.activeProfile());
		assertEquals("pvp", config.previousProfile());

		ConfigurationManager restarted = newConfig();
		restarted.load();
		assertEquals("pvp", restarted.previousProfile(), "the history is saved");
	}

	@Test
	void renamingKeepsRulesHistoryAndTheActiveProfile() throws IOException {
		ConfigurationManager config = newConfig();
		config.load();
		assertNull(config.createProfile("sword"));
		config.setProfileRules(List.of(new ProfileRule(ProfileRule.Trigger.SWORD, ProfileRule.Dimension.ANY, "sword")));
		assertNull(config.switchProfile("sword"));
		module.size.set(5.0);

		assertNull(config.renameProfile("sword", "blade"));
		assertEquals("blade", config.activeProfile());
		assertFalse(Files.exists(profile("sword")));
		assertTrue(Files.exists(profile("blade")));
		assertEquals("blade", config.profileRules().getFirst().profile());
		assertNotNull(config.renameProfile("blade", "default"), "names stay unique");
		assertNotNull(config.renameProfile("blade", "bad name"));

		assertNull(config.switchProfile("default"));
		assertEquals("blade", config.previousProfile());
		assertNull(config.switchProfile("blade"));
		assertEquals(5.0, module.size.get(), "the renamed profile kept its values");
	}

	@Test
	void theHudLayoutIsStoredPerProfileAndLoadListenersRun() {
		ConfigurationManager config = newConfig();
		AtomicInteger loads = new AtomicInteger();
		config.addLoadListener(loads::incrementAndGet);
		config.load();
		assertEquals(1, loads.get());

		config.setSection("hud_layout", new JsonPrimitive("layout A"));
		assertNull(config.createProfile("other"));
		config.setSection("hud_layout", new JsonPrimitive("layout B"));
		assertNull(config.switchProfile("other"));
		assertEquals("layout A", config.section("hud_layout").getAsString());
		assertEquals(2, loads.get());
		assertNull(config.switchProfile("default"));
		assertEquals("layout B", config.section("hud_layout").getAsString());
	}

	@Test
	void resetEverythingMakesARestorePointFirst() {
		ConfigurationManager config = newConfig();
		config.load();
		animations.set(false);
		modules.setEnabled(module, true);
		config.setSection("hud_layout", new JsonPrimitive("moved"));

		assertNull(config.resetEverything());

		assertTrue(animations.get());
		assertFalse(module.isEnabled());
		assertNull(config.section("hud_layout"));
		assertEquals("Before resetting everything", config.restorePoints().getFirst().label());
	}

	@Test
	void anOlderConfigurationFormatIsCopiedBeforeItIsRewritten() throws IOException {
		Files.createDirectories(dir.resolve("profiles"));
		Files.writeString(dir.resolve("femboyclient.json"), "{ \"schemaVersion\": 2, \"activeProfile\": \"default\" }");
		Files.writeString(profile("default"), "{ \"schemaVersion\": 2, \"modules\": { \"test_module\": { \"enabled\": true } } }");

		newConfig().load();

		List<ConfigurationManager.RestorePoint> points = newConfig().restorePoints();
		assertEquals(1, points.size());
		assertEquals("Before updating the configuration format", points.getFirst().label());
		assertTrue(module.isEnabled());
	}

	@Test
	void profilesListTheirEnabledModulesAndKeepNotesThroughARename() {
		ConfigurationManager config = newConfig();
		config.load();
		modules.setEnabled(module, true);
		assertNull(config.createProfile("second"));
		modules.setEnabled(module, false);

		assertEquals(List.of(), config.enabledModules("default"), "the active profile is read live");
		assertEquals(List.of("test_module"), config.enabledModules("second"), "another profile is read from its file");
		assertNull(config.enabledModules("missing"));

		config.setProfileNote("second", "  Bedwars  ");
		assertEquals("Bedwars", config.profileNote("second"));
		assertEquals("", config.profileNote("default"));
		assertNull(config.renameProfile("second", "bedwars"));
		assertEquals("Bedwars", config.profileNote("bedwars"), "the note follows the rename");
		assertEquals("", config.profileNote("second"));
		config.setProfileNote("bedwars", " ");
		assertEquals("", config.profileNote("bedwars"), "a blank note removes it");

		config.setProfileNote("bedwars", "kept");
		config.saveNow();
		ConfigurationManager reloaded = newConfig();
		reloaded.load();
		assertEquals("kept", reloaded.profileNote("bedwars"), "notes are saved with the client settings");
	}
}
