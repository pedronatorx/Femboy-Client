package io.github.theamethyx.femboyclient.client.modules;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.chat.Component;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;

import io.github.theamethyx.femboyclient.client.FemboyClient;
import io.github.theamethyx.femboyclient.core.module.Module;
import io.github.theamethyx.femboyclient.core.module.ModuleCategory;
import io.github.theamethyx.femboyclient.core.setting.NumberSetting;

/** On the "disconnected" screen: a reconnect button with a countdown that rejoins the last server automatically. */
public final class AutoReconnectModule extends Module {
	private final NumberSetting delay = settings().add(new NumberSetting("delay", "Delay", "Seconds before reconnecting.", 5, 1, 60, 1, "s"));
	private final NumberSetting attempts = settings().add(new NumberSetting("attempts", "Attempts",
			"Give up after this many automatic tries in a row (the button still works).", 5, 1, 50, 1, ""));

	private static ServerData lastServer;
	private int tries;
	/** The disconnect screen the countdown belongs to; a resize re-runs init and must not start a second countdown. */
	private Screen attachedTo;
	private long reconnectAt;

	public AutoReconnectModule() {
		super("auto_reconnect", "Auto Reconnect", "Rejoins the last server after a countdown when you get disconnected.", ModuleCategory.HUD, false, NO_KEY);
	}

	@Override
	public boolean requiresWorld() {
		return false;
	}

	/** Registered once at startup; does nothing while the module is off. */
	public static void registerEvents() {
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
			ServerData server = client.getCurrentServer();
			if (server != null && !client.hasSingleplayerServer()) {
				lastServer = server;
			}

			AutoReconnectModule module = FemboyClient.get().modules().get(AutoReconnectModule.class);
			if (module != null) {
				module.tries = 0;
			}
		});
		ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
			AutoReconnectModule module = FemboyClient.get().modules().get(AutoReconnectModule.class);
			if (screen instanceof DisconnectedScreen && module != null && module.isRunning() && lastServer != null) {
				module.attach(client, screen, width, height);
			}
		});
	}

	/** Test hook: the server a reconnect would join (normally recorded when you join one). */
	public static void rememberServer(ServerData server) {
		lastServer = server;
	}

	private void attach(Minecraft client, Screen screen, int width, int height) {
		boolean automatic = tries < attempts.intValue();
		Button button = Button.builder(Component.literal("Reconnect"), b -> reconnect(client)).bounds(width / 2 - 100, height - 30, 200, 20).build();
		Screens.getButtons(screen).add(button);
		if (attachedTo != screen) {
			attachedTo = screen;
			reconnectAt = System.currentTimeMillis() + delay.intValue() * 1000L;
		}

		// Re-initialized (window resized): the running countdown continues instead of starting over. Whichever listener
		// reconnects first changes the screen, so the other one cannot reconnect a second time.
		ScreenEvents.afterTick(screen).register(s -> {
			updateLabel(button, automatic);
			if (automatic && reconnectAt - System.currentTimeMillis() <= 0 && client.screen == s) {
				tries++;
				attachedTo = null;
				reconnect(client);
			}
		});
	}

	private void updateLabel(Button button, boolean automatic) {
		long left = (reconnectAt - System.currentTimeMillis() + 999) / 1000;
		if (!automatic) {
			button.setMessage(Component.literal("Reconnect (stopped after " + attempts.intValue() + " tries)"));
		} else if (left > 0) {
			button.setMessage(Component.literal("Reconnecting in " + left + " s"));
		}
	}

	private static void reconnect(Minecraft client) {
		ServerData server = lastServer;
		if (server != null) {
			ConnectScreen.startConnecting(new JoinMultiplayerScreen(new TitleScreen()), client, ServerAddress.parseString(server.ip), server, false, null);
		}
	}
}
