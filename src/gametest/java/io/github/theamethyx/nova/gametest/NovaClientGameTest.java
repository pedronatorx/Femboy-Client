package io.github.theamethyx.nova.gametest;

import org.lwjgl.glfw.GLFW;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

import io.github.theamethyx.nova.client.NovaClient;
import io.github.theamethyx.nova.client.gui.NovaScreen;

/**
 * End-to-end tests that run inside a real Minecraft client. Screenshots are written to
 * {@code build/run/clientGameTest/screenshots}; a summary report goes to {@code nova-test-report.txt}
 * in the same run directory.
 */
@SuppressWarnings("UnstableApiUsage")
public class NovaClientGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		TestReport report = new TestReport(context);

		report.check("NOVA entrypoint ran", context.computeOnClient(client -> NovaClient.get() != null && NovaClient.get().modules() != null));
		// Deterministic screenshots: no easing between frames.
		context.runOnClient(client -> NovaClient.get().options().animations.set(false));

		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			singleplayer.getClientWorld().waitForChunksRender();
			context.takeScreenshot("nova-world");

			GuiWalkthrough.run(context, report);
		}

		report.finish();
	}

	/** Opens the ClickGUI with the real key binding and visits every page. */
	static final class GuiWalkthrough {
		static void run(ClientGameTestContext context, TestReport report) {
			context.getInput().pressKey(GLFW.GLFW_KEY_RIGHT_SHIFT);
			context.waitForScreen(NovaScreen.class);
			report.check("Right Shift opens the NOVA screen", true);

			for (int[] size : new int[][] {{854, 480}, {1280, 720}}) {
				context.getInput().resizeWindow(size[0], size[1]);
				context.waitTicks(2);
				int pages = context.computeOnClient(client -> ((NovaScreen) client.screen).pageCount());
				for (int page = 0; page < pages; page++) {
					int index = page;
					context.runOnClient(client -> ((NovaScreen) client.screen).showPage(index));
					context.waitTicks(3);
					String title = context.computeOnClient(client -> ((NovaScreen) client.screen).currentPage().title());
					context.takeScreenshot("gui-" + size[0] + "x" + size[1] + "-" + index + "-" + title.toLowerCase().replaceAll("[^a-z]+", "-"));
				}
			}

			context.runOnClient(client -> ((NovaScreen) client.screen).showPage(0));
			context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
			context.waitForScreen(null);
			report.check("Escape closes the NOVA screen", true);
		}
	}
}
