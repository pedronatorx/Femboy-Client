package io.github.theamethyx.nova.client;

import java.nio.file.Path;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;

import io.github.theamethyx.nova.core.config.ConfigurationManager;
import io.github.theamethyx.nova.core.diag.DiagnosticLogger;
import io.github.theamethyx.nova.core.diag.PerformanceMonitor;
import io.github.theamethyx.nova.core.module.KeybindManager;
import io.github.theamethyx.nova.core.module.Module;
import io.github.theamethyx.nova.core.module.ModuleManager;

/** Client entrypoint: creates the shared services and connects them to Fabric events. */
public final class NovaClient implements ClientModInitializer {
	public static final String MOD_ID = "nova";
	private static NovaClient instance;

	private DiagnosticLogger diagnostics;
	private PerformanceMonitor performance;
	private ModuleManager modules;
	private KeybindManager keybinds;
	private ConfigurationManager config;
	private GlobalOptions options;

	public static NovaClient get() {
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
		config = new ConfigurationManager(configDir, modules, options.settings, diagnostics, System::currentTimeMillis);
		modules.setEnvironment(new ClientEnvironment(client, config));

		ModuleRegistry.registerAll(modules);

		List<String> report = config.load();
		diagnostics.info("NOVA", "Initialized " + modules.all().size() + " modules; profile '" + config.activeProfile() + "'"
				+ (report.isEmpty() ? "" : " (" + report.size() + " configuration problem(s) recovered, see Diagnostics)"));

		ClientTickEvents.END_CLIENT_TICK.register(this::onEndTick);
		ClientLifecycleEvents.CLIENT_STOPPING.register(this::onStopping);
	}

	private void onEndTick(Minecraft client) {
		modules.tick();
		config.tick();
	}

	private void onStopping(Minecraft client) {
		modules.stopAll();
		config.saveNow();
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

	/** Placeholder policy used only until the config (and therefore the server list) exists. */
	private static final class NoWorldYet implements io.github.theamethyx.nova.core.module.EnvironmentPolicy {
		@Override
		public boolean inWorld() {
			return false;
		}

		@Override
		public boolean isAuthorizedEnvironment() {
			return false;
		}

		@Override
		public String describe() {
			return "Starting";
		}
	}
}
