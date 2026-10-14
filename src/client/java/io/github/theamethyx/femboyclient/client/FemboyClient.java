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

import io.github.theamethyx.femboyclient.client.gui.ClientScreen;
import io.github.theamethyx.femboyclient.client.hud.HudManager;
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

		config =new ConfigurationManager(configDir, modules, options.settings, diagnostics, System::currentTimeMillis);
		modules.setEnvironment(new ClientEnvironment(client));

		ModuleRegistry.registerAll(modules);

		List<String> report = config.load();
		diagnostics.info("Client", "Initialized " + modules.all().size() + " modules; profile '" + config.activeProfile() + "'"
				+ (report.isEmpty() ? "" : " (" + report.size() + " configuration problem(s) recovered, see Diagnostics)"));

		ClientKeys.register();
		new HudManager(modules).register();
		worldRender = new WorldRenderDispatcher(modules, diagnostics);
		worldRender.register();
		ClientTickEvents.END_CLIENT_TICK.register(this::onEndTick);
		WorldRenderEvents.START_MAIN.register(context -> performance.markFrame(System.nanoTime()));
		ClientLifecycleEvents.CLIENT_STOPPING.register(this::onStopping);
	}

	private void onEndTick(Minecraft client) {
		while (ClientKeys.openMenu.consumeClick()) {
			if (client.screen == null) {
				client.setScreen(new ClientScreen());
			}
		}

		modules.tick();
		config.tick();
	}

	private void onStopping(Minecraft client) {
		modules.stopAll();
		config.saveNow();
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
			client.player.displayClientMessage(Component.literal(module.name() + " " + state + extra), true);
		}
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
