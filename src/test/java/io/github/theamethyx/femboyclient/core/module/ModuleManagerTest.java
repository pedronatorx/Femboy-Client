package io.github.theamethyx.femboyclient.core.module;

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

import io.github.theamethyx.femboyclient.core.diag.DiagnosticLogger;
import io.github.theamethyx.femboyclient.core.diag.PerformanceMonitor;
import io.github.theamethyx.femboyclient.core.setting.BooleanSetting;
import io.github.theamethyx.femboyclient.core.setting.NumberSetting;

class ModuleManagerTest {
	private final MutableEnvironment environment = new MutableEnvironment();
	private DiagnosticLogger diagnostics;
	private ModuleManager manager;
	private long now = 1_000_000;

	static final class MutableEnvironment implements EnvironmentPolicy {
		boolean inWorld = true;
		boolean lowImpact;

		@Override
		public boolean lowImpactMode() {
			return lowImpact;
		}

		@Override
		public boolean inWorld() {
			return inWorld;
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
	}

	@BeforeEach
	void setUp() {
		diagnostics = new DiagnosticLogger();
		manager = new ModuleManager(diagnostics, new PerformanceMonitor(), environment, () -> now);
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
	void aFailingModuleIsSuspendedWithoutAffectingOthersAndRestarts() {
		RecordingModule broken = manager.register(new RecordingModule("broken", "Broken", Module.NO_KEY));
		RecordingModule healthy = manager.register(new RecordingModule("healthy", "Healthy", Module.NO_KEY));
		List<Boolean> reported = new ArrayList<>();
		manager.addFailureListener((module, phase, reason, turnedOff) -> reported.add(turnedOff));
		broken.failOnTick = new IllegalStateException("boom");
		manager.setEnabled(broken, true);
		manager.setEnabled(healthy, true);

		manager.tick();
		manager.tick();

		assertTrue(broken.isEnabled(), "one failure only suspends the module");
		assertFalse(broken.isRunning());
		assertNotNull(broken.lastError());
		assertTrue(broken.lastError().contains("boom"));
		assertEquals(List.of("register", "enable", "tick", "disable"), broken.events);
		assertEquals(2, healthy.events.stream().filter("tick"::equals).count());
		assertEquals(1, diagnostics.errorCount());
		assertEquals(List.of(false), reported);
		assertEquals(ModuleHealth.State.SUSPENDED, manager.health(broken).state());
		assertTrue(manager.health(broken).reason().contains("Restarts in 5 s"), manager.health(broken).reason());

		// The suspension ends and the module starts again by itself.
		broken.failOnTick = null;
		now += ModuleManager.SUSPEND_MILLIS;
		manager.tick();
		assertTrue(broken.isRunning());
		assertEquals(List.of("register", "enable", "tick", "disable", "enable", "tick"), broken.events);
		assertEquals(ModuleHealth.State.ACTIVE, manager.health(broken).state());
		assertTrue(manager.health(broken).lastSuccessMillis() >= 0);
	}

	@Test
	void repeatedFailuresTurnTheModuleOffUntilTheUserRetries() {
		RecordingModule broken = manager.register(new RecordingModule("broken", "Broken", Module.NO_KEY));
		List<Boolean> reported = new ArrayList<>();
		manager.addFailureListener((module, phase, reason, turnedOff) -> reported.add(turnedOff));
		broken.failOnTick = new IllegalStateException("boom");
		manager.setEnabled(broken, true);

		for (int i = 0; i < 6; i++) {
			manager.tick();
			now += 11_000;
		}

		assertEquals(List.of(false, false, true), reported, "suspended twice, then turned off");
		assertFalse(broken.isEnabled());
		assertFalse(broken.isRunning());
		ModuleHealth health = manager.health(broken);
		assertEquals(ModuleHealth.State.ERROR, health.state());
		assertTrue(health.reason().contains("3 errors within a minute"), health.reason());
		assertEquals(3, broken.errorCount());

		broken.failOnTick = null;
		manager.setEnabled(broken, true);
		assertNull(broken.lastError(), "re-enabling clears the error");
		assertNull(broken.offReason());
		assertTrue(broken.isRunning());
	}

	@Test
	void failuresFarApartNeverTurnAModuleOff() {
		RecordingModule flaky = manager.register(new RecordingModule("flaky", "Flaky", Module.NO_KEY));
		flaky.failOnTick = new IllegalStateException("rare");
		manager.setEnabled(flaky, true);
		for (int i = 0; i < 5; i++) {
			manager.tick();
			now += ModuleManager.FAILURE_WINDOW_MILLIS + 1;
		}

		assertTrue(flaky.isEnabled(), "one failure a minute is suspended each time but never turned off");
		assertEquals(5, flaky.errorCount());
	}

	@Test
	void healthTellsWaitingUnsupportedAndOffApart() {
		RecordingModule plain = manager.register(new RecordingModule("plain", "Plain", Module.NO_KEY));
		RecordingModule sandbox = manager.register(new RecordingModule("sandbox", "Sandbox", Module.NO_KEY) {
			@Override
			public boolean testEnvironmentOnly() {
				return true;
			}

			@Override
			public String workload() {
				return "12 targets";
			}
		});

		assertEquals(ModuleHealth.State.OFF, manager.health(plain).state());
		environment.inWorld = false;
		manager.setEnabled(plain, true);
		assertEquals(ModuleHealth.State.WAITING, manager.health(plain).state());
		environment.inWorld = true;
		manager.tick();
		assertEquals(ModuleHealth.State.ACTIVE, manager.health(plain).state());
		assertNull(manager.health(plain).workload());

		manager.setEnabled(sandbox, true);
		assertEquals(ModuleHealth.State.UNSUPPORTED, manager.health(sandbox).state());
		assertEquals(Module.TEST_ENVIRONMENT_ONLY, manager.health(sandbox).reason());
		assertNull(manager.health(sandbox).workload(), "only running modules describe their work");
	}

	@Test
	void aliasesHelpSearchWithoutRenamingAnything() {
		RecordingModule blocks = manager.register(new RecordingModule("block_esp", "Block ESP", Module.NO_KEY) {
			@Override
			public List<String> aliases() {
				return List.of("ore esp");
			}
		});
		manager.register(new RecordingModule("other", "Other", Module.NO_KEY));

		assertEquals(List.of(blocks), manager.search("ore", null));
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
		assertTrue(manager.search("coord", ModuleCategory.VISUALS).isEmpty());
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

	@Test
	void holdModeKeysTurnModulesOnOnlyWhileHeld() {
		KeybindManager keys = new KeybindManager(manager);
		RecordingModule hold = manager.register(new RecordingModule("hold", "Hold", 70));
		RecordingModule toggle = manager.register(new RecordingModule("toggle", "Toggle", 70));
		manager.setHoldKey(hold, true);

		keys.onKeyPressed(70);
		assertTrue(hold.isEnabled());
		assertTrue(toggle.isEnabled());

		keys.onKeyReleased(70);
		assertFalse(hold.isEnabled(), "released hold module turns off");
		assertTrue(toggle.isEnabled(), "toggle module ignores the release");

		// A missed release (e.g. focus lost) is caught by the per-tick check.
		keys.onKeyPressed(70);
		assertTrue(hold.isEnabled());
		keys.releaseUnheld(key -> false);
		assertFalse(hold.isEnabled());

		// A hold module the user switched on in the menu is not turned off by releasing its key.
		manager.setEnabled(hold, true);
		keys.onKeyReleased(70);
		keys.releaseUnheld(key -> false);
		assertTrue(hold.isEnabled());
	}

	@Test
	void holdModeIsSavedAndReset() {
		RecordingModule module = manager.register(new RecordingModule("a", "Alpha", 70));
		manager.setHoldKey(module, true);
		JsonObject saved = manager.saveModules();
		manager.setHoldKey(module, false);
		manager.loadModules(saved);
		assertTrue(module.isHoldKey());

		manager.resetToDefaults(module);
		assertFalse(module.isHoldKey());
	}

	@Test
	void disableAllTurnsEveryModuleOffAndRunsTheirCleanup() {
		RecordingModule a = manager.register(new RecordingModule("a", "Alpha", Module.NO_KEY));
		RecordingModule b = manager.register(new RecordingModule("b", "Beta", Module.NO_KEY));
		RecordingModule c = manager.register(new RecordingModule("c", "Gamma", Module.NO_KEY));
		manager.setEnabled(a, true);
		manager.setEnabled(c, true);

		List<Module> off = manager.disableAll();

		assertEquals(List.of(a, c), off);
		assertFalse(a.isEnabled() || b.isEnabled() || c.isEnabled());
		assertFalse(a.isRunning() || c.isRunning());
		assertTrue(a.events.contains("disable") && c.events.contains("disable"));
	}

	@Test
	void sanitizeKeepsOnlyValidValuesWithoutApplyingThem() {
		RecordingModule module = manager.register(new RecordingModule("a", "Alpha", Module.NO_KEY));
		JsonObject input = com.google.gson.JsonParser.parseString("""
				{"a": {"enabled": "yes", "key": 9999, "keyMode": "hold",
				       "settings": {"size": 99, "flag": true, "unknown": 1}},
				 "missing_module": {"enabled": true},
				 "b": 5}""").getAsJsonObject();
		List<String> warnings = new ArrayList<>();

		JsonObject clean = manager.sanitizeModules(input, warnings);

		JsonObject a = clean.getAsJsonObject("a");
		assertFalse(a.has("enabled"));
		assertFalse(a.has("key"));
		assertEquals("hold", a.get("keyMode").getAsString());
		assertFalse(a.getAsJsonObject("settings").has("size"), "out-of-range value dropped");
		assertTrue(a.getAsJsonObject("settings").get("flag").getAsBoolean());
		assertFalse(a.getAsJsonObject("settings").has("unknown"));
		assertFalse(clean.has("missing_module"));
		assertEquals(5, warnings.size(), warnings::toString);
		assertEquals(2.0, module.size.get(), "nothing applied");
		assertFalse(module.flag.get(), "nothing applied");
	}

	// ----- 0.9.0: conflicts, world changes, Low-Impact mode, lookups, category reset ----------------

	static final class ConflictingModule extends RecordingModule {
		private final java.util.Set<String> conflicts;
		String limitation;
		int worldChanges;

		ConflictingModule(String id, java.util.Set<String> conflicts) {
			super(id, id, Module.NO_KEY);
			this.conflicts = conflicts;
		}

		@Override
		public java.util.Set<String> conflicts() {
			return conflicts;
		}

		@Override
		public String lowImpactLimitation() {
			return limitation;
		}

		@Override
		protected void onWorldChange() {
			worldChanges++;
		}
	}

	@Test
	void enablingAModuleTurnsOffOnesThatConflictWithIt() {
		ConflictingModule aura = manager.register(new ConflictingModule("aura", java.util.Set.of("aim")));
		ConflictingModule aim = manager.register(new ConflictingModule("aim", java.util.Set.of()));
		List<String> resolved = new ArrayList<>();
		manager.addConflictListener((enabled, off) -> resolved.add(enabled.id() + " turned off " + off.id()));

		manager.setEnabled(aim, true);
		manager.setEnabled(aura, true);

		assertTrue(aura.isRunning());
		assertFalse(aim.isEnabled(), "declared on one side, applied both ways");
		assertTrue(aim.events.contains("disable"), "the other module ran its cleanup");
		assertEquals(List.of("aura turned off aim"), resolved);

		manager.setEnabled(aim, true);
		assertFalse(aura.isEnabled(), "and the other way round");
	}

	@Test
	void worldChangesReachOnlyRunningModules() {
		ConflictingModule running = manager.register(new ConflictingModule("running", java.util.Set.of()));
		ConflictingModule off = manager.register(new ConflictingModule("off", java.util.Set.of()));
		manager.setEnabled(running, true);

		manager.worldChanged();

		assertEquals(1, running.worldChanges);
		assertEquals(0, off.worldChanges);
	}

	@Test
	void lowImpactModePausesModulesThatSaySoAndExplainsWhy() {
		ConflictingModule rapid = manager.register(new ConflictingModule("rapid", java.util.Set.of()));
		rapid.limitation = "it attacks for you several times a second.";
		ConflictingModule calm = manager.register(new ConflictingModule("calm", java.util.Set.of()));
		manager.setEnabled(rapid, true);
		manager.setEnabled(calm, true);
		assertTrue(rapid.isRunning());

		environment.lowImpact = true;
		manager.tick();

		assertFalse(rapid.isRunning(), "paused");
		assertTrue(rapid.isEnabled(), "still your choice: it resumes when the mode changes");
		assertTrue(manager.availability(rapid).reason().contains("Low-Impact"), manager.availability(rapid).reason());
		assertTrue(calm.isRunning());

		environment.lowImpact = false;
		manager.tick();
		assertTrue(rapid.isRunning());
	}

	@Test
	void lookupByClassIsCachedAndAllIsShared() {
		RecordingModule a = manager.register(new RecordingModule("a", "Alpha", Module.NO_KEY));
		assertEquals(a, manager.get(RecordingModule.class));
		assertEquals(a, manager.get(RecordingModule.class));
		assertEquals(null, manager.get(ConflictingModule.class), "a miss is answered too");
		assertTrue(manager.all() == manager.all(), "no copy per call");
	}

	@Test
	void resettingACategoryResetsOnlyThatCategory() {
		RecordingModule hud = manager.register(new RecordingModule("hud", "HUD", Module.NO_KEY));
		hud.size.set(5.0);
		manager.setEnabled(hud, true);

		manager.resetCategory(ModuleCategory.HUD);
		assertEquals(2.0, hud.size.get());
		assertFalse(hud.isEnabled());
		manager.resetCategory(ModuleCategory.COMBAT); // no modules there: nothing happens
	}
}
