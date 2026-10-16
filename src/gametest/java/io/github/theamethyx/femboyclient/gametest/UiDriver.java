package io.github.theamethyx.femboyclient.gametest;

import java.util.List;
import java.util.function.Predicate;

import org.lwjgl.glfw.GLFW;

import com.mojang.blaze3d.platform.Window;

import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;

import io.github.theamethyx.femboyclient.client.gui.ClientScreen;
import io.github.theamethyx.femboyclient.client.gui.widget.Widget;

/**
 * Drives the Femboy Client screen through real (simulated) mouse and keyboard input, the same path a player
 * uses: cursor position in window pixels, GLFW button presses and typed characters.
 */
@SuppressWarnings("UnstableApiUsage")
final class UiDriver {
	private final ClientGameTestContext context;

	UiDriver(ClientGameTestContext context) {
		this.context = context;
	}

	ClientScreen screen() {
		return context.computeOnClient(client -> client.screen instanceof ClientScreen screen ? screen : null);
	}

	/** Finds the first widget of a type on the current page matching a condition. */
	<W extends Widget> W find(Class<W> type, Predicate<W> condition) {
		return context.computeOnClient(client -> {
			if (!(client.screen instanceof ClientScreen screen)) {
				return null;
			}

			List<W> all = screen.currentPage().descendants(type);
			return all.stream().filter(condition).findFirst().orElse(null);
		});
	}

	/** Clicks the center of a widget with the left mouse button, scrolling it into view first. */
	void click(Widget widget) {
		context.runOnClient(client -> {
			if (client.screen instanceof ClientScreen screen) {
				for (io.github.theamethyx.femboyclient.client.gui.widget.ScrollList list : screen.currentPage().descendants(io.github.theamethyx.femboyclient.client.gui.widget.ScrollList.class)) {
					list.revealDescendant(widget);
				}

				// Children of dynamic panels are positioned while rendering; let one frame pass.
			}
		});
		context.waitTick();
		clickVirtual(widget.x() + widget.width() / 2.0, widget.y() + widget.height() / 2.0);
	}

	/** Moves the mouse to a position in the menu's coordinate space without clicking. */
	void hoverVirtual(double vx, double vy) {
		double[] windowPos = toWindow(vx, vy);
		context.getInput().setCursorPos(windowPos[0], windowPos[1]);
		context.waitTick();
	}

	private double[] toWindow(double vx, double vy) {
		return context.computeOnClient(client -> {
			ClientScreen screen = (ClientScreen) client.screen;
			Window window = client.getWindow();
			return new double[] {screen.toGui(vx) * window.getScreenWidth() / window.getGuiScaledWidth(),
					screen.toGui(vy) * window.getScreenHeight() / window.getGuiScaledHeight()};
		});
	}

	/** Clicks at a position in the menu's own coordinate space. */
	void clickVirtual(double vx, double vy) {
		double[] windowPos = context.computeOnClient(client -> {
			ClientScreen screen = (ClientScreen) client.screen;
			Window window = client.getWindow();
			double gx = screen.toGui(vx);
			double gy = screen.toGui(vy);
			return new double[] {gx * window.getScreenWidth() / window.getGuiScaledWidth(), gy * window.getScreenHeight() / window.getGuiScaledHeight()};
		});
		context.getInput().setCursorPos(windowPos[0], windowPos[1]);
		context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
		context.waitTick();
	}

	/** Right-clicks the middle of a widget (after letting one frame pass, like {@link #click}). */
	void rightClick(Widget widget) {
		context.waitTick();
		double[] windowPos = toWindow(widget.x() + widget.width() / 2.0, widget.y() + widget.height() / 2.0);
		context.getInput().setCursorPos(windowPos[0], windowPos[1]);
		context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
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
