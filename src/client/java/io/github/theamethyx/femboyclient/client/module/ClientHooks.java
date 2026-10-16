package io.github.theamethyx.femboyclient.client.module;

import java.util.List;

import io.github.theamethyx.femboyclient.client.gui.UiContext;
import io.github.theamethyx.femboyclient.client.gui.widget.Widget;
import io.github.theamethyx.femboyclient.client.hud.HudAnchor;
import io.github.theamethyx.femboyclient.client.hud.HudLine;
import io.github.theamethyx.femboyclient.client.render.RenderingUtilities;

/**
 * Optional client-side capabilities a module can implement. The managers only call these while the
 * module is running, and always through {@code ModuleManager.runGuarded}, so an exception turns
 * the module off instead of crashing the game.
 */
public final class ClientHooks {
	private ClientHooks() {
	}

	/** Contributes a block of text lines to one corner of the HUD. */
	public interface HudBlock {
		HudAnchor hudAnchor();

		/** Adds this frame's lines. Adding nothing hides the block. */
		void buildHud(List<HudLine> lines);

		/** Panel blocks get a background box; list blocks (e.g. the module list) are drawn bare. */
		default boolean panelStyle() {
			return true;
		}
	}

	/**
	 * A graphical HUD element with a fixed size (e.g. Keystrokes). It is stacked in its corner like text blocks and can be
	 * dragged in the HUD editor. Returning a width of 0 hides it this frame.
	 */
	public interface HudWidget {
		HudAnchor hudAnchor();

		int hudWidth(UiContext ui);

		int hudHeight(UiContext ui);

		void drawHud(UiContext ui, int x, int y);
	}

	/** Draws freely on the HUD layer, e.g. labels projected from world positions. */
	public interface ScreenOverlay {
		void renderOverlay(UiContext ui, int screenWidth, int screenHeight, float partialTick);
	}

	/** Draws lines and boxes in the world. */
	public interface WorldRender {
		void renderWorld(RenderingUtilities render);
	}

	/** Adds custom controls below the module's settings in the ClickGUI (e.g. a waypoint list). */
	public interface CustomControls {
		List<Widget> createControls(UiContext ui, Runnable refresh);
	}
}
