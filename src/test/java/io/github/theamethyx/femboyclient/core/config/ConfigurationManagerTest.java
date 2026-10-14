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
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

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

class ConfigurationManagerTest {
	@TempDir
	Path dir;

	private final AtomicLong clock = new AtomicLong(1_000);
	private ModuleManager modules;
	private ModuleSettings global;
	private BooleanSetting animations;
	private TestModule module;

	static final class TestModule extends Module {
		final NumberSetting size = settings().add(new NumberSetting("size", "Size", "", 2, 1, 5, 1, ""));

		TestModule() {
			super("test_module", "Test Module", "", ModuleCategory.HUD, true, Module.NO_KEY);
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
		return new ConfigurationManager(dir, modules, global, new DiagnosticLogger(), clock::get);
	}

	private Path profile(String name) {
		return dir.resolve("profiles").resolve(name + ".json");
	}

	@Test
	void firstStartCreatesDefaultFiles() {
		List<String> report = newConfig().load();

		assertTrue(report.isEmpty(), report::toString);
		assertTrue(Files.exists(dir.resolve("femboyclient.json")));
		assertTrue(Files.exists(profile("default")));
		assertTrue(module.isEnabled(), "enabled by default");
	}

	@Test
	void changesAreAutosavedAfterTheDelayAndSurviveARestart() {
		ConfigurationManager config = newConfig();
		config.load();

		module.size.set(4.0);
		animations.set(false);
		assertTrue(config.isDirty());
		config.tick();
		assertTrue(config.isDirty(), "not saved before the delay");
		clock.addAndGet(ConfigurationManager.AUTOSAVE_DELAY_MILLIS);
		config.tick();
		assertFalse(config.isDirty());

		module.size.set(1.0);
		animations.set(true);
		ConfigurationManager restarted = newConfig();
		assertTrue(restarted.load().isEmpty());
		assertEquals(4.0, module.size.get());
		assertFalse(animations.get());
	}

	@Test
	void malformedProfileIsKeptAsideAndRestoredFromBackup() throws IOException {
		ConfigurationManager config = newConfig();
		config.load();
		module.size.set(5.0);
		config.saveNow();
		// The second save makes the .bak contain size 5.
		config.saveNow();

		Files.writeString(profile("default"), "{ \"modules\": { broken");
		List<String> report = newConfig().load();

		assertEquals(5.0, module.size.get(), "value recovered from the backup");
		assertTrue(report.stream().anyMatch(line -> line.contains("could not be read")), report::toString);
		assertTrue(report.stream().anyMatch(line -> line.contains("restored from the backup")), report::toString);
		try (Stream<Path> files = Files.list(dir.resolve("profiles"))) {
			assertTrue(files.anyMatch(path -> path.getFileName().toString().startsWith("default.json.corrupt-")), "corrupt file is kept, not deleted");
		}

		// The recovered state was written back as a valid file.
		assertTrue(newConfig().load().isEmpty());
	}

	@Test
	void malformedGlobalFileWithoutBackupFallsBackToDefaults() throws IOException {
		Files.createDirectories(dir);
		Files.writeString(dir.resolve("femboyclient.json"), "not json at all");
		animations.set(false);

		List<String> report = newConfig().load();

		assertTrue(animations.get());
		assertTrue(report.stream().anyMatch(line -> line.contains("no backup")), report::toString);
	}

	@Test
	void wrongTypesAreReportedPerValue() throws IOException {
		newConfig().load();
		Files.writeString(profile("default"), """
				{"schemaVersion": 99, "modules": {"test_module": {"enabled": "yes", "settings": {"size": 42}}}}
				""");

		List<String> report = newConfig().load();

		assertEquals(3, report.size(), report::toString);
		assertTrue(report.get(0).contains("newer version"));
		assertTrue(report.stream().anyMatch(line -> line.contains("Test Module: 'enabled' must be true or false")));
		assertTrue(report.stream().anyMatch(line -> line.contains("'Size' had an invalid value")));
		assertEquals(2.0, module.size.get());
	}

	@Test
	void profilesCanBeCreatedSwitchedAndDeleted() throws IOException {
		ConfigurationManager config = newConfig();
		config.load();
		module.size.set(3.0);

		assertNull(config.createProfile("pvp"));
		assertNotNull(config.createProfile("PVP"), "names are unique ignoring case");
		assertNotNull(config.createProfile("../escape"));
		assertNotNull(config.createProfile(""));

		module.size.set(5.0);
		assertNull(config.switchProfile("pvp"));
		assertEquals("pvp", config.activeProfile());
		assertEquals(3.0, module.size.get(), "pvp was created as a copy with size 3");

		assertNull(config.switchProfile("default"));
		assertEquals(5.0, module.size.get(), "changes to default were saved before switching");

		assertNotNull(config.deleteProfile("default"), "active profile cannot be deleted");
		assertNull(config.deleteProfile("pvp"));
		assertEquals(List.of("default"), config.profiles());
		assertNotNull(config.switchProfile("pvp"));

		// The active profile is remembered across restarts.
		assertNull(config.createProfile("second"));
		assertNull(config.switchProfile("second"));
		ConfigurationManager restarted = newConfig();
		restarted.load();
		assertEquals("second", restarted.activeProfile());
	}

	@Test
	void anOldAuthorizedServerListIsIgnoredAndDropped() throws Exception {
		// Versions up to 0.2.0 stored a list of authorized servers; it no longer has a meaning.
		newConfig().load();
		Files.writeString(dir.resolve("femboyclient.json"), """
				{ "schemaVersion": 1, "activeProfile": "default", "authorizedServers": ["localhost:25565"] }
				""");
		ConfigurationManager config = newConfig();
		List<String> report = config.load();

		assertTrue(report.isEmpty(), report::toString);
		config.saveNow();
		assertFalse(Files.readString(dir.resolve("femboyclient.json")).contains("authorizedServers"));
	}
}
