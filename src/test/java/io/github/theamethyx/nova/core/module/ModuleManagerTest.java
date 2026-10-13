package io.github.theamethyx.nova.core.module;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.github.theamethyx.nova.core.diag.DiagnosticLogger;
import io.github.theamethyx.nova.core.diag.PerformanceMonitor;
import io.github.theamethyx.nova.core.setting.BooleanSetting;
import io.github.theamethyx.nova.core.setting.NumberSetting;

class ModuleManagerTest {
	private final MutableEnvironment environment = new MutableEnvironment();
	private DiagnosticLogger diagnostics;
	private ModuleManager manager;

	static final class MutableEnvironment implements EnvironmentPolicy {
		boolean inWorld = true;
		boolean authorized = true;

		@Override
		public boolean inWorld() {
			return inWorld;
		}

		@Override
		public boolean isAuthorizedEnvironment() {
			return authorized;
		}

		@Override
		public String describe() {
			return "test";
		}
	}

	static class RecordingModule extends Module {
		final List<String> events = new ArrayList<>();
		final NumberSetting size;
		final BooleanSetting flag;
		boolean restricted;
		RuntimeException failOnTick;
		RuntimeException failOnEnable;

		RecordingModule(String id, String name, int key) {
			super(id, name, "A module used by tests.", ModuleCategory.HUD, false, key);
			size = settings().add(new NumberSetting("size", "Size", "", 2, 1, 5, 1, ""));
			flag = settings().add(new BooleanSetting("flag", "Show details", "", false));
		}

		@Override
		protected void onRegister() {
			events.add("register");
		}

		@Override
		protected void onEnable() {
			events.add("enable");
			if (failOnEnable != null) throw failOnEnable;
		}

		@Override
		protected void onDisable() {
			events.add("disable");
		}

		@Override
		protected void onTick() {
			events.add("tick");
			if (failOnTick != null) throw failOnTick;
		}

		@Override
		public boolean isRestricted() {
			return restricted;
		}
	}

	@BeforeEach
	void setUp() {
		diagnostics = new DiagnosticLogger();
		manager = new ModuleManager(diagnostics, new PerformanceMonitor(), environment);
	}

	@Test
	void lifecycleRunsInOrder() {
		RecordingModule module = manager.register(new RecordingModule("a", "Alpha", Module.NO_KEY));
		manager.setEnabled(module, true);
		manager.tick();
		manager.setEnabled(module, false);
		manager.tick();

		assertEquals(List.of("register", "enable", "tick", "disable"), module.events);
		assertFalse(module.isRunning());
	}

	@Test
	void duplicateIdsAreRejected() {
		manager.register(new RecordingModule("a", "Alpha", Module.NO_KEY));
		assertThrows(IllegalArgumentException.class, () -> manager.register(new RecordingModule("a", "Again", Module.NO_KEY)));
	}

	@Test
	void unavailableModulesPauseAndResumeWithTheWorld() {
		RecordingModule module = manager.register(new RecordingModule("a", "Alpha", Module.NO_KEY));
		environment.inWorld = false;
		manager.setEnabled(module, true);

		assertTrue(module.isEnabled());
		assertFalse(module.isRunning(), "no world: enabled but paused");
		manager.tick();
		assertFalse(module.events.contains("tick"));

		environment.inWorld = true;
		manager.tick();
		assertTrue(module.isRunning());

		environment.inWorld = false;
		manager.tick();
		assertFalse(module.isRunning());
		assertTrue(module.isEnabled(), "the user's choice survives world transitions");
		assertEquals(List.of("register", "enable", "tick", "disable"), module.events);
	}

	@Test
	void restrictedModulesOnlyRunInAuthorizedEnvironments() {
		RecordingModule module = manager.register(new RecordingModule("a", "Alpha", Module.NO_KEY));
		module.restricted = true;
		environment.authorized = false;
		manager.setEnabled(module, true);

		assertFalse(module.isRunning());
		assertFalse(manager.availability(module).available());
		assertTrue(manager.availability(module).reason().contains("authorized"));

		environment.authorized = true;
		manager.tick();
		assertTrue(module.isRunning());
	}

	@Test
	void aFailingModuleIsDisabledWithoutAffectingOthers() {
		RecordingModule broken = manager.register(new RecordingModule("broken", "Broken", Module.NO_KEY));
		RecordingModule healthy = manager.register(new RecordingModule("healthy", "Healthy", Module.NO_KEY));
		broken.failOnTick = new IllegalStateException("boom");
		manager.setEnabled(broken, true);
		manager.setEnabled(healthy, true);

		manager.tick();
		manager.tick();

		assertFalse(broken.isEnabled());
		assertFalse(broken.isRunning());
		assertNotNull(broken.lastError());
		assertTrue(broken.lastError().contains("boom"));
		assertEquals(List.of("register", "enable", "tick", "disable"), broken.events);
		assertEquals(2, healthy.events.stream().filter("tick"::equals).count());
		assertEquals(1, diagnostics.errorCount());

		broken.failOnTick = null;
		manager.setEnabled(broken, true);
		assertNull(broken.lastError(), "re-enabling clears the error");
		assertTrue(broken.isRunning());
	}

	@Test
	void failureDuringEnableDoesNotLeaveModuleRunning() {
		RecordingModule module = manager.register(new RecordingModule("a", "Alpha", Module.NO_KEY));
		module.failOnEnable = new RuntimeException("cannot start");
		manager.setEnabled(module, true);

		assertFalse(module.isRunning());
		assertFalse(module.isEnabled());
		assertFalse(module.events.contains("disable"), "onDisable is not called for a module that never started");
	}

	@Test
	void keybindsToggleAndConflictsAreReported() {
		RecordingModule a = manager.register(new RecordingModule("a", "Alpha", 71));
		RecordingModule b = manager.register(new RecordingModule("b", "Beta", 72));
		KeybindManager keys = new KeybindManager(manager);

		assertEquals(List.of(a), keys.onKeyPressed(71));
		assertTrue(a.isEnabled());
		keys.onKeyPressed(71);
		assertFalse(a.isEnabled());
		assertTrue(keys.onKeyPressed(Module.NO_KEY).isEmpty());

		assertTrue(keys.conflicts().isEmpty());
		manager.setKey(b, 71);
		assertEquals(List.of(a, b), keys.conflicts().get(71));
		assertThrows(IllegalArgumentException.class, () -> manager.setKey(b, 5000));
	}

	@Test
	void searchMatchesNamesFirstThenDescriptionsAndSettings() {
		RecordingModule coords = manager.register(new RecordingModule("coords", "Coordinates", Module.NO_KEY));
		RecordingModule other = manager.register(new RecordingModule("other", "Other", Module.NO_KEY));

		assertEquals(List.of(coords), manager.search("coord", null));
		assertEquals(List.of(coords, other), manager.search("", null));
		assertEquals(List.of(coords, other), manager.search("details", null), "matches a setting name");
		assertTrue(manager.search("coord", ModuleCategory.VISUAL).isEmpty());
		assertTrue(manager.search("coord zzz", null).isEmpty(), "all terms must match");
	}

	@Test
	void saveAndLoadRoundTripsStateAndReportsBadValues() {
		RecordingModule module = manager.register(new RecordingModule("a", "Alpha", Module.NO_KEY));
		manager.setEnabled(module, true);
		manager.setKey(module, 80);
		module.size.set(4.0);
		JsonObject saved = manager.saveModules();

		manager.resetToDefaults(module);
		assertFalse(module.isEnabled());
		assertEquals(2.0, module.size.get());

		assertTrue(manager.loadModules(saved).isEmpty());
		assertTrue(module.isEnabled());
		assertTrue(module.isRunning());
		assertEquals(80, module.key());
		assertEquals(4.0, module.size.get());

		JsonObject bad = saved.deepCopy();
		bad.getAsJsonObject("a").addProperty("key", 9999);
		bad.getAsJsonObject("a").getAsJsonObject("settings").addProperty("size", 50);
		List<String> warnings = manager.loadModules(bad);
		assertEquals(2, warnings.size(), warnings::toString);
		assertTrue(warnings.get(0).startsWith("Alpha: "));
		assertEquals(Module.NO_KEY, module.key());
		assertEquals(2.0, module.size.get());
	}
}
