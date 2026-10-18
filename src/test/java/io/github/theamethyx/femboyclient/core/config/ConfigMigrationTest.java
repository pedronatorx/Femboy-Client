package io.github.theamethyx.femboyclient.core.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import io.github.theamethyx.femboyclient.core.diag.DiagnosticLogger;
import io.github.theamethyx.femboyclient.core.diag.PerformanceMonitor;
import io.github.theamethyx.femboyclient.core.module.EnvironmentPolicy;
import io.github.theamethyx.femboyclient.core.module.Module;
import io.github.theamethyx.femboyclient.core.module.ModuleCategory;
import io.github.theamethyx.femboyclient.core.module.ModuleManager;
import io.github.theamethyx.femboyclient.core.setting.BooleanSetting;
import io.github.theamethyx.femboyclient.core.setting.EnumSetting;
import io.github.theamethyx.femboyclient.core.setting.ModuleSettings;
import io.github.theamethyx.femboyclient.core.setting.NumberSetting;

/** The 0.9.0 configuration changes: Ore ESP's data moving to Block ESP, background saving, options stored per profile. */
class ConfigMigrationTest {
	@TempDir
	Path dir;

	private final AtomicLong clock = new AtomicLong(1_000);
	private ModuleManager modules;
	private BlockEsp blockEsp;
	private ModuleSettings global;
	private EnumSetting<Mode> mode;

	enum Mode { BALANCED, PERFORMANCE }

	/** Stands in for Block ESP: same id and setting ids as the real module. */
	static final class BlockEsp extends Module {
		final NumberSetting radius = settings().add(new NumberSetting("radius", "Radius", "", 4, 1, 12, 1, "chunks"));
		final BooleanSetting exposed = settings().add(new BooleanSetting("exposed_only", "Only exposed", "", false));

		BlockEsp() {
			super("block_esp", "Block ESP", "", ModuleCategory.VISUALS, false, Module.NO_KEY);
		}
	}

	@BeforeEach
	void setUp() {
		modules = new ModuleManager(new DiagnosticLogger(), new PerformanceMonitor(), EnvironmentPolicy.IN_WORLD);
		blockEsp = modules.register(new BlockEsp());
		global = new ModuleSettings();
		mode = global.add(new EnumSetting<>("performance_mode", "Mode", "", Mode.BALANCED));
	}

	private Path profile(String name) {
		return dir.resolve("profiles").resolve(name + ".json");
	}

	@Test
	void oreEspSettingsMoveToBlockEsp() throws Exception {
		Files.createDirectories(dir.resolve("profiles"));
		Files.writeString(profile("default"), """
				{"schemaVersion": 1, "modules": {"ore_esp": {"enabled": true, "settings": {"radius": 7, "exposed_only": true}}}}
				""");
		ConfigurationManager config = new ConfigurationManager(dir, modules, global, new DiagnosticLogger(), clock::get);

		List<String> report = config.load();

		assertTrue(report.isEmpty(), "migration is not a problem: " + report);
		assertTrue(blockEsp.isEnabled(), "enabled state carried over");
		assertEquals(7.0, blockEsp.radius.get());
		assertTrue(blockEsp.exposed.get());
		JsonObject saved = JsonParser.parseString(Files.readString(profile("default"))).getAsJsonObject();
		assertEquals(ConfigurationManager.SCHEMA_VERSION, saved.get("schemaVersion").getAsInt());
		assertTrue(saved.getAsJsonObject("modules").has("block_esp"));
		assertFalse(saved.getAsJsonObject("modules").has("ore_esp"), "the old id is not written back");
		assertTrue(Files.exists(dir.resolve("profiles").resolve("default.json.bak")), "the old file is kept as a backup");
	}

	@Test
	void newDataWinsWhenBothIdsArePresent() {
		JsonObject modulesJson = JsonParser.parseString("""
				{"ore_esp": {"settings": {"radius": 2}}, "block_esp": {"settings": {"radius": 9}}}
				""").getAsJsonObject();

		List<String> notes = ConfigMigrations.migrateModules(modulesJson);

		assertEquals(9, modulesJson.getAsJsonObject("block_esp").getAsJsonObject("settings").get("radius").getAsInt());
		assertFalse(modulesJson.has("ore_esp"));
		assertEquals(1, notes.size());
	}

	@Test
	void importedOldProfilesAreMigratedToo() {
		ConfigurationManager config = new ConfigurationManager(dir, modules, global, new DiagnosticLogger(), clock::get);
		config.load();

		var result = config.importProfile("shared", """
				{"femboyClientProfile": "old", "schemaVersion": 1, "modules": {"ore_esp": {"enabled": true, "settings": {"radius": 3}}}}
				""");

		assertEquals(null, result.error());
		assertTrue(result.warnings().isEmpty(), "nothing dropped as unknown: " + result.warnings());
		assertEquals(null, config.switchProfile("shared"));
		assertEquals(3.0, blockEsp.radius.get());
	}

	@Test
	void automaticSavesRunOnTheWriterAndSaveNowWaitsForThem() throws Exception {
		Queue<Runnable> queued = new ArrayDeque<>();
		ConfigurationManager config = new ConfigurationManager(dir, modules, global, new DiagnosticLogger(), clock::get, queued::add);
		config.load();
		long before = Files.getLastModifiedTime(profile("default")).toMillis();
		String written = Files.readString(profile("default"));

		blockEsp.radius.set(11.0);
		clock.addAndGet(ConfigurationManager.AUTOSAVE_DELAY_MILLIS);
		config.tick();

		assertEquals(1, queued.size(), "the write was handed to the writer");
		assertEquals(written, Files.readString(profile("default")), "nothing written on the calling thread");
		assertFalse(config.isDirty(), "the state was captured");
		queued.poll().run();
		assertTrue(Files.readString(profile("default")).contains("\"radius\": 11"), "the writer wrote the captured state");
		assertTrue(Files.getLastModifiedTime(profile("default")).toMillis() >= before);
	}

	@Test
	void profilesCarryTheirOwnOptions() {
		ConfigurationManager config = new ConfigurationManager(dir, modules, global, new DiagnosticLogger(), clock::get);
		config.setProfileOptions(Set.of("performance_mode"));
		config.load();
		assertEquals(null, config.createProfile("fast"));
		assertEquals(null, config.switchProfile("fast"));
		mode.set(Mode.PERFORMANCE);
		config.saveNow();

		assertEquals(null, config.switchProfile("default"));
		assertEquals(Mode.BALANCED, mode.get(), "the default profile keeps its own mode");
		assertEquals(null, config.switchProfile("fast"));
		assertEquals(Mode.PERFORMANCE, mode.get(), "switching back brings the mode back");
	}
}
