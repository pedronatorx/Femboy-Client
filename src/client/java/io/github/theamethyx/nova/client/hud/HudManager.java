package io.github.theamethyx.nova.client.hud;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.Identifier;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;

import io.github.theamethyx.nova.client.NovaClient;
import io.github.theamethyx.nova.client.gui.Theme;
import io.github.theamethyx.nova.client.gui.UiContext;
import io.github.theamethyx.nova.client.gui.NovaScreen;
import io.github.theamethyx.nova.client.module.ClientHooks;
import io.github.theamethyx.nova.core.module.Module;
import io.github.theamethyx.nova.core.module.ModuleManager;

/**
 * Draws the HUD contributions of all running modules in one Fabric HUD layer (placed below the
 * chat). Text blocks are stacked per screen corner so modules never overlap each other.
 */
public final class HudManager {
	private static final int MARGIN = 4;
	private static final int GAP = 3;
	private static final int LINE = 10;

	private final ModuleManager modules;
	private UiContext ui;

	public HudManager(ModuleManager modules) {
		this.modules = modules;
	}

	public void register() {
		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, Identifier.fromNamespaceAndPath(NovaClient.MOD_ID, "hud"), this::render);
	}

	private void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
		Minecraft client = Minecraft.getInstance();
		if (client.options.hideGui || client.level == null) {
			return;
		}

		if (ui == null) {
			ui = new UiContext(client.font);
		}

		int width = graphics.guiWidth();
		int height = graphics.guiHeight();
		ui.setScreenSize(width, height);
		ui.beginFrame(graphics, -1000, -1000, 0, false);
		float partialTick = deltaTracker.getGameTimeDeltaPartialTick(false);

		Map<HudAnchor, List<Block>> byCorner = new EnumMap<>(HudAnchor.class);
		for (Module module : modules.all()) {
			if (!module.isRunning()) {
				continue;
			}

			if (module instanceof ClientHooks.ScreenOverlay overlay) {
				modules.runGuarded(module, ModuleManager.Phase.RENDER_HUD, () -> overlay.renderOverlay(ui, width, height, partialTick));
			}

			if (module instanceof ClientHooks.HudBlock block) {
				List<HudLine> lines = new ArrayList<>();
				if (modules.runGuarded(module, ModuleManager.Phase.RENDER_HUD, () -> block.buildHud(lines)) && !lines.isEmpty()) {
					byCorner.computeIfAbsent(block.hudAnchor(), anchor -> new ArrayList<>()).add(new Block(lines, block.panelStyle()));
				}
			}
		}

		// The F3 screen covers both top corners; the NOVA menu covers the middle and is fine.
		boolean debugVisible = client.debugEntries.isOverlayVisible();
		boolean menuOpen = client.screen instanceof NovaScreen;
		for (Map.Entry<HudAnchor, List<Block>> entry : byCorner.entrySet()) {
			HudAnchor anchor = entry.getKey();
			if (debugVisible && !anchor.bottom()) {
				continue;
			}

			drawCorner(anchor, entry.getValue(), width, height, menuOpen);
		}
	}

	private void drawCorner(HudAnchor anchor, List<Block> blocks, int screenWidth, int screenHeight, boolean dimmed) {
		int cursor = anchor.bottom() ? screenHeight - MARGIN : MARGIN;
		if (anchor == HudAnchor.BOTTOM_LEFT || anchor == HudAnchor.BOTTOM_RIGHT) {
			// Stay above the chat input line and the hotbar area on the left/right edges.
			cursor -= 12;
		}

		for (Block block : blocks) {
			int blockWidth = block.width(ui);
			int blockHeight = block.height();
			int x = anchor.right() ? screenWidth - MARGIN - blockWidth : MARGIN;
			int y = anchor.bottom() ? cursor - blockHeight : cursor;
			block.draw(ui, x, y, blockWidth, anchor.right(), dimmed);
			cursor = anchor.bottom() ? y - GAP : y + blockHeight + GAP;
		}
	}

	private record Block(List<HudLine> lines, boolean panel) {
		int pad() {
			return panel ? 4 : 0;
		}

		int width(UiContext ui) {
			int widest = 0;
			for (HudLine line : lines) {
				int w = ui.textWidth(line.value()) + (line.label().isEmpty() ? 0 : ui.textWidth(line.label()) + 6) + (line.accent() != 0 ? 4 : 0);
				widest = Math.max(widest, w);
			}

			return widest + pad() * 2;
		}

		int height() {
			return lines.size() * LINE + pad() * 2 - 1;
		}

		void draw(UiContext ui, int x, int y, int width, boolean alignRight, boolean dimmed) {
			if (panel) {
				ui.roundRect(x, y, width, height(), dimmed ? 0x60101014 : 0xA8101014);
				ui.rect(alignRight ? x + width - 1 : x, y + 2, 1, height() - 4, Theme.ACCENT_DIM);
			}

			for (int i = 0; i < lines.size(); i++) {
				HudLine line = lines.get(i);
				int rowY = y + pad() + i * LINE;
				int labelWidth = line.label().isEmpty() ? 0 : ui.textWidth(line.label()) + 6;
				int valueWidth = ui.textWidth(line.value());

				if (!panel) {
					// List style: each row gets its own tight background, aligned to the edge.
					int rowWidth = valueWidth + labelWidth + 6 + (line.accent() != 0 ? 2 : 0);
					int rowX = alignRight ? x + width - rowWidth : x;
					ui.rect(rowX, rowY - 1, rowWidth, LINE, dimmed ? 0x50101014 : 0x90101014);
					if (line.accent() != 0) {
						ui.rect(alignRight ? rowX + rowWidth - 2 : rowX, rowY - 1, 2, LINE, line.accent());
					}

					int textX = alignRight ? rowX + 3 : rowX + 3 + (line.accent() != 0 ? 2 : 0);
					drawRow(ui, line, textX, rowY, labelWidth);
				} else {
					int textX = alignRight ? x + width - pad() - labelWidth - valueWidth : x + pad() + 1;
					drawRow(ui, line, textX, rowY, labelWidth);
				}
			}
		}

		private static void drawRow(UiContext ui, HudLine line, int x, int y, int labelWidth) {
			if (labelWidth > 0) {
				ui.text(line.label(), x, y, Theme.TEXT_MUTED);
			}

			ui.text(line.value(), x + labelWidth, y, line.valueColor());
		}
	}
}
