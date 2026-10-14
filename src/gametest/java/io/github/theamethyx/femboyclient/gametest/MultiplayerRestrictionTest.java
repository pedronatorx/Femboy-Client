package io.github.theamethyx.femboyclient.gametest;

import java.util.List;

import org.lwjgl.glfw.GLFW;

import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerConnection;

import io.github.theamethyx.femboyclient.client.FemboyClient;
import io.github.theamethyx.femboyclient.client.gui.ClientScreen;
import io.github.theamethyx.femboyclient.client.gui.widget.Button;
import io.github.theamethyx.femboyclient.client.gui.widget.TextField;
import io.github.theamethyx.femboyclient.client.modules.AmethystGeodeAnalyzerModule;
import io.github.theamethyx.femboyclient.client.modules.FixtureEspModule;
import io.github.theamethyx.femboyclient.client.modules.FreeCameraModule;
import io.github.theamethyx.femboyclient.core.module.Module;

/**
 * On a real dedicated server, restricted modules must stay paused until the user lists the server as
 * an authorized test environment, and pause again when it is removed.
 */
@SuppressWarnings("UnstableApiUsage")
final class MultiplayerRestrictionTest {
	private static final List<Class<? extends Module>> RESTRICTED = List.of(AmethystGeodeAnalyzerModule.class, FixtureEspModule.class, FreeCameraModule.class);

	private MultiplayerRestrictionTest() {
	}

	static void run(ClientGameTestContext context, UiDriver ui, TestReport report) {
		try (TestDedicatedServerContext server = context.worldBuilder().createServer();
				TestServerConnection connection = server.connect()) {
			connection.getClientWorld().waitForChunksRender();
			String address = context.computeOnClient(client -> client.getCurrentServer().ip);
			report.note("Connected to dedicated test server at " + address);

			report.check("A dedicated server is not an authorized environment by default",
					!context.computeOnClient(client -> FemboyClient.get().modules().environment().isAuthorizedEnvironment()));
			context.runOnClient(client -> RESTRICTED.forEach(type -> FemboyClient.get().modules().setEnabled(FemboyClient.get().modules().get(type), true)));
			context.waitTicks(3);
			report.check("Restricted modules stay paused on an unauthorized server", context.computeOnClient(client -> RESTRICTED.stream()
					.map(type -> FemboyClient.get().modules().get(type)).allMatch(m -> m.isEnabled() && !m.isRunning())));
			String reason = context.computeOnClient(client -> FemboyClient.get().modules().availability(FemboyClient.get().modules().get(FixtureEspModule.class)).reason());
			report.check("The pause reason explains the authorization rule", reason.contains("authorized"), reason);
			report.check("Unrestricted HUD modules still run on any server",
					context.computeOnClient(client -> FemboyClient.get().modules().get("coordinates_hud").isRunning()));

			// Authorize the server through the Settings page, as a user would.
			context.getInput().pressKey(GLFW.GLFW_KEY_RIGHT_SHIFT);
			context.waitForScreen(ClientScreen.class);
			context.runOnClient(client -> {
				ClientScreen screen = (ClientScreen) client.screen;
				for (int i = 0; i < screen.pageCount(); i++) {
					screen.showPage(i);
					if (screen.currentPage().title().equals("Configuration")) break;
				}
			});
			context.waitTicks(3);
			context.takeScreenshot("multiplayer-unauthorized-settings");
			// The last text field on the page is the server address field (the first is the profile name).
			List<TextField> fields = context.computeOnClient(client -> ((ClientScreen) client.screen).currentPage().descendants(TextField.class));
			TextField field = fields.get(fields.size() - 1);
			ui.click(field);
			ui.type(address);
			ui.click(ui.find(Button.class, b -> b.label().equals("Add")));
			context.waitTicks(3);
			report.check("The address typed into Settings is stored as an authorized server",
					context.computeOnClient(client -> FemboyClient.get().config().isAuthorizedServer(address)), address);
			ui.key(GLFW.GLFW_KEY_ESCAPE);
			context.waitForScreen(null);
			context.waitTicks(3);
			report.check("After authorizing the server, the restricted modules run", context.computeOnClient(client -> RESTRICTED.stream()
					.map(type -> FemboyClient.get().modules().get(type)).allMatch(Module::isRunning)));

			context.runOnClient(client -> FemboyClient.get().config().removeAuthorizedServer(address));
			context.waitTicks(3);
			report.check("Removing the server pauses the restricted modules again", context.computeOnClient(client -> RESTRICTED.stream()
					.map(type -> FemboyClient.get().modules().get(type)).noneMatch(Module::isRunning)));
			report.check("Leaving the free camera paused returned the view to the player",
					context.computeOnClient(client -> client.getCameraEntity() == client.player));
			context.runOnClient(client -> RESTRICTED.forEach(type -> FemboyClient.get().modules().setEnabled(FemboyClient.get().modules().get(type), false)));
		}
	}
}
