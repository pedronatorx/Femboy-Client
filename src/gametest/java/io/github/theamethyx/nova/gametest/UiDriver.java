package io.github.theamethyx.nova.gametest;

import java.util.List;
import java.util.function.Predicate;

import org.lwjgl.glfw.GLFW;

import com.mojang.blaze3d.platform.Window;

import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;

import io.github.theamethyx.nova.client.gui.NovaScreen;
import io.github.theamethyx.nova.client.gui.widget.Widget;

/**
 * Drives the NOVA screen through real (simulated) mouse and keyboard input, the same path a player
 * uses: cursor position in window pixels, GLFW button presses and typed characters.
 */
@SuppressWarnings("UnstableApiUsage")
final class UiDriver {
	private final ClientGameTestContext context;

	UiDriver(ClientGameTestContext context) {
		this.context = context;
	}

	NovaScreen screen() {
		return context.computeOnClient(client -> client.screen instanceof NovaScreen screen ? screen : null);
	}

	/** Finds the first widget of a type on the current page matching a condition. */
	<W extends Widget> W find(Class<W> type, Predicate<W> condition) {
		return context.computeOnClient(client -> {
			if (!(client.screen instanceof NovaScreen screen)) {
				return null;
			}

			List<W> all = screen.currentPage().descendants(type);
			return all.stream().filter(condition).findFirst().orElse(null);
		});
	}

	/** Clicks the center of a widget with the left mouse button. */
	void click(Widget widget) {
		clickVirtual(widget.x() + widget.width() / 2.0, widget.y() + widget.height() / 2.0);
	}

	/** Clicks at a position in the menu's own coordinate space. */
	void clickVirtual(double vx, double vy) {
		double[] windowPos = context.computeOnClient(client -> {
			NovaScreen screen = (NovaScreen) client.screen;
			Window window = client.getWindow();
			double gx = screen.toGui(vx);
			double gy = screen.toGui(vy);
			return new double[] {gx * window.getScreenWidth() / window.getGuiScaledWidth(), gy * window.getScreenHeight() / window.getGuiScaledHeight()};
		});
		context.getInput().setCursorPos(windowPos[0], windowPos[1]);
		context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
		context.waitTick();
	}

	void type(String text) {
		context.getInput().typeChars(text);
		context.waitTick();
	}

	void key(int glfwKey) {
		context.getInput().pressKey(glfwKey);
		context.waitTick();
	}
}
