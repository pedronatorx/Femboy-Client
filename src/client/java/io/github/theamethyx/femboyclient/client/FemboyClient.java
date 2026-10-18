package io.github.theamethyx.femboyclient.client;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.fabricmc.loader.api.FabricLoader;

import io.github.theamethyx.femboyclient.client.combat.CombatTracker;
import io.github.theamethyx.femboyclient.client.gui.ClientScreen;
import io.github.theamethyx.femboyclient.client.hud.HudManager;
import io.github.theamethyx.femboyclient.client.profile.AutoProfileSwitcher;
import io.github.theamethyx.femboyclient.client.render.WorldRenderDispatcher;
import io.github.theamethyx.femboyclient.core.config.ConfigurationManager;
import io.github.theamethyx.femboyclient.core.config.LegacyConfigMigration;
import io.github.theamethyx.femboyclient.core.diag.DiagnosticLogger;
import io.github.theamethyx.femboyclient.core.diag.PerformanceMonitor;
import io.github.theamethyx.femboyclient.core.module.KeybindManager;
import io.github.theamethyx.femboyclient.core.module.Module;
import io.github.theamethyx.femboyclient.core.module.ModuleManager;

/** Client entrypoint: creates the shared services and connects them to Fabric events. */
public final class FemboyClient implements ClientModInitializer {
	public static final String MOD_ID = "femboyclient";
	private static FemboyClient instance;

	private DiagnosticLogger diagnostics;
	private PerformanceMonitor performance;
	private ModuleManager modules;
	private KeybindManager keybinds;
	private ConfigurationManager config;
	private GlobalOptions options;
	private WorldRenderDispatcher worldRender;
	private HudManager hud;
	private final AutoProfileSwitcher profileSwitcher = new AutoProfileSwitcher();
	private final CombatTracker combat = new CombatTracker();
	/** Writes configuration files off the client thread, one at a time. */
	private final java.util.concurrent.ExecutorService configWriter = java.util.concurrent.Executors.newSingleThreadExecutor(runnable -> {
		Thread thread = new Thread(runnable, "Femboy Client config writer");
		thread.setDaemon(true);
		return thread;
	});
	private net.minecraft.client.multiplayer.ClientLevel lastLevel;

	public static FemboyClient get() {
		return instance;
	}

	@Override
	public void onInitializeClient() {
		instance = this;
		Minecraft client = Minecraft.getInstance();

		diagnostics = new DiagnosticLogger();
		performance = new PerformanceMonitor();
		options = new GlobalOptions();
		Performance.bind(options);
		modules = new ModuleManager(diagnostics, performance, new NoWorldYet());
		keybinds = new KeybindManager(modules);

		Path configDir = FabricLoader.getInstance().getConfigDir().resolve(MOD_ID);
		try {
			int copied = LegacyConfigMigration.migrate(configDir.resolveSibling("nova"), configDir, MOD_ID + ".json");
			if (copied > 0) {
				diagnostics.info("Config", "Copied " + copied + " file(s) from config/nova/ (the folder used before the rename); the old folder was kept.");
			}
		} catch (IOException e) {
			diagnostics.error("Config", "Could not copy settings from config/nova/; starting with defaults. The old folder is untouched.", e);
		}

		config = new ConfigurationManager(configDir, modules, options.settings, diagnostics, System::currentTimeMillis, configWriter);
		config.setProfileOptions(GlobalOptions.PROFILE_OPTIONS);
		modules.setEnvironment(new ClientEnvironment(client));
		modules.addFailureListener((module, phase, reason) -> io.github.theamethyx.femboyclient.client.hud.Notifications.show(module.name() + " stopped",
				reason + " (details in Settings, Diagnostics)", io.github.theamethyx.femboyclient.client.gui.Theme.ERROR));
		modules.addConflictListener((enabled, turnedOff) -> io.github.theamethyx.femboyclient.client.hud.Notifications.show(turnedOff.name() + " turned off",
				"it cannot run together with " + enabled.name(), io.github.theamethyx.femboyclient.client.gui.Theme.WARNING));

		ModuleRegistry.registerAll(modules);

		List<String> report = config.load();
		diagnostics.info("Client", "Initialized " + modules.all().size() + " modules; profile '" + config.activeProfile() + "'"
				+ (report.isEmpty() ? "" : " (" + report.size() + " configuration problem(s) recovered, see Diagnostics)"));

		ClientKeys.register();
		hud = new HudManager(modules, new io.github.theamethyx.femboyclient.client.hud.HudLayout(config));
		hud.register();
		worldRender = new WorldRenderDispatcher(modules, diagnostics);
		worldRender.register();
		ClientTickEvents.END_CLIENT_TICK.register(this::onEndTick);
		io.github.theamethyx.femboyclient.client.modules.AutoReconnectModule.registerEvents();
		io.github.theamethyx.femboyclient.client.modules.InventorySorterModule.registerEvents();
		io.github.theamethyx.femboyclient.client.modules.ChestOrganizerModule.registerEvents();
		io.github.theamethyx.femboyclient.client.modules.ShulkerPreviewModule.registerTooltip();
		WorldRenderEvents.START_MAIN.register(context -> performance.markFrame(System.nanoTime()));
		WorldRenderEvents.END_EXTRACTION.register(context -> io.github.theamethyx.femboyclient.client.render.FrameProjection.captureFrustum(context.frustum()));
		io.github.theamethyx.femboyclient.client.world.BlockChanges.register();
		ClientLifecycleEvents.CLIENT_STOPPING.register(this::onStopping);
	}

	private void onEndTick(Minecraft client) {
		while (ClientKeys.openMenu.consumeClick()) {
			if (client.screen == null) {
				client.setScreen(new ClientScreen());
			}
		}

		while (ClientKeys.disableAll.consumeClick()) {
			disableEverything();
		}

		keybinds.releaseUnheld(key -> com.mojang.blaze3d.platform.InputConstants.isKeyDown(client.getWindow(), key));
		ClientTicks.advance();
		if (client.level != lastLevel) {
			// A new world or dimension: shared caches start over, running modules drop what described the old one.
			io.github.theamethyx.femboyclient.client.world.EntityIndex.invalidate();
			if (lastLevel != null && client.level != null) {
				modules.worldChanged();
			}

			lastLevel = client.level;
		}

		profileSwitcher.tick(client, this);
		combat.tick(client, comboTimeoutMillis());
		modules.tick();
		config.tick();
	}

	/**
	 * Emergency stop: turns every module off (each restores what it changed, e.g. Free Camera returns the view
	 * and Fullbright the lighting) and stops automatic profile switching so no profile turns them back on.
	 *
	 * @return how many modules were turned off
	 */
	public int disableEverything() {
		options.autoProfile.set(false);
		keybinds.clearHeld();
		int count = modules.disableAll().size();
		Minecraft client = Minecraft.getInstance();
		if (client.player != null) {
			client.player.displayClientMessage(Component.literal("Femboy Client: all modules off (" + count + ")"
					+ (count == 0 ? "" : ", automatic profile switching paused")), true);
		}

		return count;
	}

	private void onStopping(Minecraft client) {
		modules.stopAll();
		config.saveNow();
		configWriter.shutdown();
		worldRender.close();
	}

	/** Called by the keyboard mixin for key presses while no screen is open. */
	public void onKeyPressedInWorld(int key) {
		List<Module> toggled = keybinds.onKeyPressed(key);
		Minecraft client = Minecraft.getInstance();

		if (!toggled.isEmpty() && options.toggleMessages.get() && client.player != null) {
			Module module = toggled.getFirst();
			String state = module.isEnabled() ? "enabled" : "disabled";
			String extra = toggled.size() > 1 ? " (+" + (toggled.size() - 1) + " more on this key)" : "";
			io.github.theamethyx.femboyclient.client.hud.Notifications.show(module.name(), state + extra,
					module.isEnabled() ? io.github.theamethyx.femboyclient.client.gui.Theme.SUCCESS : io.github.theamethyx.femboyclient.client.gui.Theme.TEXT_MUTED);
		}
	}

	/** Called by the keyboard mixin for every key release, also while a screen is open. */
	public void onKeyReleased(int key) {
		keybinds.onKeyReleased(key);
	}

	/** Combo timeout from Combat Info's setting (3 s when that module is not configured). */
	private long comboTimeoutMillis() {
		var info = modules.get(io.github.theamethyx.femboyclient.client.modules.CombatInfoModule.class);
		return info == null ? 3000 : info.comboTimeoutMillis();
	}

	public HudManager hud() {
		return hud;
	}

	public CombatTracker combat() {
		return combat;
	}

	public AutoProfileSwitcher profileSwitcher() {
		return profileSwitcher;
	}

	public DiagnosticLogger diagnostics() {
		return diagnostics;
	}

	public PerformanceMonitor performance() {
		return performance;
	}

	public ModuleManager modules() {
		return modules;
	}

	public KeybindManager keybinds() {
		return keybinds;
	}

	public ConfigurationManager config() {
		return config;
	}

	public GlobalOptions options() {
		return options;
	}

	/** Placeholder policy used only until the modules are set up. */
	private static final class NoWorldYet implements io.github.theamethyx.femboyclient.core.module.EnvironmentPolicy {
		@Override
		public boolean inWorld() {
			return false;
		}

		@Override
		public String describe() {
			return "Starting";
		}
	}
}
