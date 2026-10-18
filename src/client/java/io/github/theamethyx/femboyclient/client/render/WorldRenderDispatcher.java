package io.github.theamethyx.femboyclient.client.render;

import net.minecraft.client.Minecraft;

import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;

import io.github.theamethyx.femboyclient.client.module.ClientHooks;
import io.github.theamethyx.femboyclient.core.diag.DiagnosticLogger;
import io.github.theamethyx.femboyclient.core.module.Module;
import io.github.theamethyx.femboyclient.core.module.ModuleManager;

/**
 * Calls {@link ClientHooks.WorldRender} modules once per frame at the end of the main world pass
 * and then draws the collected geometry.
 */
public final class WorldRenderDispatcher {
	private final ModuleManager modules;
	private final DiagnosticLogger diagnostics;
	private final RenderingUtilities rendering = new RenderingUtilities();
	private boolean drawFailureReported;

	public WorldRenderDispatcher(ModuleManager modules, DiagnosticLogger diagnostics) {
		this.modules = modules;
		this.diagnostics = diagnostics;
	}

	public void register() {
		WorldRenderEvents.END_MAIN.register(this::render);
	}

	private void render(WorldRenderContext context) {
		Minecraft client = Minecraft.getInstance();
		if (client.level == null || client.player == null) {
			return;
		}

		boolean any = false;
		for (Module module : modules.all()) {
			if (module.isRunning() && module instanceof ClientHooks.WorldRender) {
				any = true;
				break;
			}
		}

		if (!any) {
			return;
		}

		var performance = io.github.theamethyx.femboyclient.client.FemboyClient.get().performance();
		long bytes = performance.allocatedBytes();
		long start = System.nanoTime();
		rendering.beginFrame(context, client.getDeltaTracker().getGameTimeDeltaPartialTick(false));
		try {
			for (Module module : modules.all()) {
				if (module.isRunning() && module instanceof ClientHooks.WorldRender world) {
					modules.runGuarded(module, ModuleManager.Phase.RENDER_WORLD, () -> world.renderWorld(rendering));
				}
			}
		} finally {
			try {
				rendering.endFrame();
			} catch (RuntimeException e) {
				// A failed draw call is not attributable to one module; report once and keep the game running.
				if (!drawFailureReported) {
					drawFailureReported = true;
					diagnostics.error("Rendering", "Drawing world overlays failed; overlays may be missing", e);
				}
			}

			performance.record("client/world", System.nanoTime() - start, performance.allocatedBytes() - bytes);
		}
	}

	public void close() {
		rendering.close();
	}
}
