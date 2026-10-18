package io.github.theamethyx.femboyclient.client.hud;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.Identifier;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;

import io.github.theamethyx.femboyclient.client.ClientTicks;
import io.github.theamethyx.femboyclient.client.FemboyClient;
import io.github.theamethyx.femboyclient.client.Performance;
import io.github.theamethyx.femboyclient.client.gui.Theme;
import io.github.theamethyx.femboyclient.client.gui.UiContext;
import io.github.theamethyx.femboyclient.client.gui.ClientScreen;
import io.github.theamethyx.femboyclient.client.module.ClientHooks;
import io.github.theamethyx.femboyclient.core.module.Module;
import io.github.theamethyx.femboyclient.core.module.ModuleManager;

/**
 * Draws the HUD contributions of all running modules in one Fabric HUD layer (placed below the
 * chat). Text blocks are stacked per screen corner so modules never overlap each other.
 *
 * <p>Text blocks only change with game state, which changes once per tick, so their lines are built once per tick
 * (every {@link Performance#hudInterval()} ticks) and reused for the frames in between, and at once when a module or
 * its settings change. Overlays and widgets still draw every frame: they follow the camera or animate.
 */
public final class HudManager {
	private static final int MARGIN = 4;
	private static final int GAP = 3;
	private static final int LINE = 10;

	private final ModuleManager modules;
	private final HudLayout layout;
	private final List<Placed> placed = new ArrayList<>();
	private UiContext ui;
	/** Text blocks of the last rebuild, by module; a module without an entry showed nothing. */
	private final Map<Module, Block> blocks = new IdentityHashMap<>();
	private long builtTick = Long.MIN_VALUE;
	private boolean dirty = true;
	private int rebuilds;

	/**
	 * Where a block was drawn in the last frame (GUI coordinates, scaled size), for the HUD editor and tests. {@code hidden}
	 * marks an element the layout hides (or whose condition does not hold), drawn faintly only while the editor is open.
	 */
	public record Placed(String moduleId, String name, int x, int y, int width, int height, boolean custom, boolean hidden, float scale) {
	}

	/** Whether the HUD editor is open: hidden elements are then drawn faintly so they can be found and shown again. */
	private static boolean editing() {
		return Minecraft.getInstance().screen instanceof io.github.theamethyx.femboyclient.client.gui.HudEditorScreen;
	}

	/** Which visibility conditions hold this frame, by ordinal. */
	private final boolean[] conditions = new boolean[io.github.theamethyx.femboyclient.core.hud.HudLayouts.Condition.values().length];

	private void evaluateConditions(Minecraft client) {
		var player = client.player;
		boolean combat = FemboyClient.get().combat().inCombat(COMBAT_WINDOW_MILLIS);
		conditions[io.github.theamethyx.femboyclient.core.hud.HudLayouts.Condition.ALWAYS.ordinal()] = true;
		conditions[io.github.theamethyx.femboyclient.core.hud.HudLayouts.Condition.IN_COMBAT.ordinal()] = combat;
		conditions[io.github.theamethyx.femboyclient.core.hud.HudLayouts.Condition.OUT_OF_COMBAT.ordinal()] = !combat;
		conditions[io.github.theamethyx.femboyclient.core.hud.HudLayouts.Condition.HOLDING_WEAPON.ordinal()] = player != null && isWeapon(player.getMainHandItem());
		conditions[io.github.theamethyx.femboyclient.core.hud.HudLayouts.Condition.GLIDING.ordinal()] = player != null && player.isFallFlying();
		conditions[io.github.theamethyx.femboyclient.core.hud.HudLayouts.Condition.SNEAKING.ordinal()] = player != null && player.isShiftKeyDown();
	}

	/** Seconds after your last attack or damage during which "In combat" holds. */
	public static final long COMBAT_WINDOW_MILLIS = 8_000;

	private static boolean isWeapon(net.minecraft.world.item.ItemStack stack) {
		var group = io.github.theamethyx.femboyclient.client.profile.AutoProfileSwitcher.classify(stack);
		return group != null && switch (group) {
			case SWORD, AXE, MACE, SPEAR, TRIDENT, BOW, CROSSBOW -> true;
			default -> false;
		};
	}

	public HudManager(ModuleManager modules, HudLayout layout) {
		this.modules = modules;
		this.layout = layout;
		modules.addListener(module -> dirty = true);
	}

	/** How often the text blocks were rebuilt (for tests). */
	public int rebuilds() {
		return rebuilds;
	}

	private void rebuildBlocks() {
		blocks.clear();
		rebuilds++;
		for (Module module : modules.all()) {
			if (module.isRunning() && module instanceof ClientHooks.HudBlock block) {
				List<HudLine> lines = new ArrayList<>();
				if (modules.runGuarded(module, ModuleManager.Phase.RENDER_HUD, () -> block.buildHud(lines)) && !lines.isEmpty()) {
					blocks.put(module, new Block(module.id(), module.name(), List.copyOf(lines), block.panelStyle(), block.hudAnchor().right(),
							Block.measure(ui, lines, block.panelStyle())));
				}
			}
		}
	}

	public HudLayout layout() {
		return layout;
	}

	public List<Placed> placed() {
		return List.copyOf(placed);
	}

	public void register() {
		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, Identifier.fromNamespaceAndPath(FemboyClient.MOD_ID, "hud"), this::render);
	}

	private void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
		Minecraft client = Minecraft.getInstance();
		if (client.options.hideGui || client.level == null) {
			return;
		}

		var performance = FemboyClient.get().performance();
		long bytes = performance.allocatedBytes();
		long start = System.nanoTime();
		try {
			renderAll(client, graphics, deltaTracker);
		} finally {
			performance.record("client/hud", System.nanoTime() - start, performance.allocatedBytes() - bytes);
		}
	}

	private void renderAll(Minecraft client, GuiGraphics graphics, DeltaTracker deltaTracker) {
		if (ui == null) {
			ui = new UiContext(client.font);
			// Drawn every frame while playing: plain shapes batch better than textured corners.
			ui.setCheapShapes(true);
		}

		int width = graphics.guiWidth();
		int height = graphics.guiHeight();
		ui.setScreenSize(width, height);
		ui.beginFrame(graphics, -1000, -1000, 0, false);
		float partialTick = deltaTracker.getGameTimeDeltaPartialTick(false);
		boolean editing = editing();
		evaluateConditions(client);

		if (dirty || ClientTicks.count() - builtTick >= Performance.hudInterval()) {
			dirty = false;
			builtTick = ClientTicks.count();
			rebuildBlocks();
		}

		Map<HudAnchor, List<Item>> byCorner = new EnumMap<>(HudAnchor.class);
		List<Item> custom = new ArrayList<>();
		placed.clear();
		for (Module module : modules.all()) {
			if (!module.isRunning()) {
				continue;
			}

			if (module instanceof ClientHooks.ScreenOverlay overlay) {
				modules.runGuarded(module, ModuleManager.Phase.RENDER_HUD, () -> overlay.renderOverlay(ui, width, height, partialTick));
			}

			io.github.theamethyx.femboyclient.core.hud.HudLayouts.Element element = layout.element(module.id());
			boolean hidden = element.hidden() || !conditions[element.when().ordinal()];
			if (hidden && !editing) {
				continue;
			}

			Block text = blocks.get(module);
			if (text != null && module instanceof ClientHooks.HudBlock block) {
				Item item = new Styled(text, element, hidden);
				if (element.placed()) {
					custom.add(item);
				} else {
					byCorner.computeIfAbsent(block.hudAnchor(), anchor -> new ArrayList<>()).add(item);
				}
			}

			if (module instanceof ClientHooks.HudWidget widget) {
				int[] size = new int[2];
				if (modules.runGuarded(module, ModuleManager.Phase.RENDER_HUD, () -> {
					size[0] = widget.hudWidth(ui);
					size[1] = widget.hudHeight(ui);
				}) && size[0] > 0 && size[1] > 0) {
					Item entry = new Styled(new WidgetItem(module, widget, size[0], size[1]), element, hidden);
					if (element.placed()) {
						custom.add(entry);
					} else {
						byCorner.computeIfAbsent(widget.hudAnchor(), anchor -> new ArrayList<>()).add(entry);
					}
				}
			}
		}

		// The F3 screen covers both top corners; the Femboy Client menu covers the middle and is fine.
		boolean debugVisible = client.debugEntries.isOverlayVisible();
		boolean menuOpen = client.screen instanceof ClientScreen;
		for (Map.Entry<HudAnchor, List<Item>> entry : byCorner.entrySet()) {
			HudAnchor anchor = entry.getKey();
			if (debugVisible && !anchor.bottom()) {
				continue;
			}

			drawCorner(anchor, entry.getValue(), width, height, menuOpen);
		}

		for (Item block : custom) {
			int blockWidth = block.width(ui);
			int blockHeight = block.height(ui);
			int[] position = layout.element(block.moduleId()).topLeft(width, height, blockWidth, blockHeight);
			block.draw(ui, position[0], position[1], blockWidth, block.alignRight(), menuOpen);
			placed.add(new Placed(block.moduleId(), block.name(), position[0], position[1], blockWidth, blockHeight, true, block.hidden(), block.scale()));
		}
	}

	private void drawCorner(HudAnchor anchor, List<Item> blocks, int screenWidth, int screenHeight, boolean dimmed) {
		int cursor = anchor.bottom() ? screenHeight - MARGIN : MARGIN;
		if (anchor == HudAnchor.BOTTOM_LEFT || anchor == HudAnchor.BOTTOM_RIGHT) {
			// Stay above the chat input line and the hotbar area on the left/right edges.
			cursor -= 12;
		}

		if (anchor == HudAnchor.TOP_RIGHT) {
			cursor += vanillaEffectIconsHeight();
		}

		for (Item block : blocks) {
			int blockWidth = block.width(ui);
			int blockHeight = block.height(ui);
			int x = anchor.right() ? screenWidth - MARGIN - blockWidth : MARGIN;
			int y = anchor.bottom() ? cursor - blockHeight : cursor;
			block.draw(ui, x, y, blockWidth, anchor.right(), dimmed);
			placed.add(new Placed(block.moduleId(), block.name(), x, y, blockWidth, blockHeight, false, block.hidden(), block.scale()));
			cursor = anchor.bottom() ? y - GAP : y + blockHeight + GAP;
		}
	}

	/** Vanilla draws status effect icons in the top-right corner: beneficial ones in a first row, harmful ones in a second. */
	private static int vanillaEffectIconsHeight() {
		var player = net.minecraft.client.Minecraft.getInstance().player;
		if (player == null) {
			return 0;
		}

		boolean beneficial = false;
		boolean harmful = false;
		for (var effect : player.getActiveEffects()) {
			if (!effect.showIcon()) continue;
			if (effect.getEffect().value().isBeneficial()) beneficial = true;
			else harmful = true;
		}

		return harmful ? 52 : beneficial ? 26 : 0;
	}

	/** Something placed on the HUD: a text block or a widget, possibly scaled and faded by the layout. */
	private sealed interface Item permits Block, WidgetItem, Styled {
		String moduleId();

		String name();

		boolean alignRight();

		int width(UiContext ui);

		int height(UiContext ui);

		void draw(UiContext ui, int x, int y, int width, boolean alignRight, boolean dimmed);

		default boolean hidden() {
			return false;
		}

		default float scale() {
			return 1;
		}
	}

	/**
	 * An item with its layout entry applied: drawn scaled around its top-left corner and with the entry's opacity (a third
	 * of it when hidden, which only happens in the editor). Sizes are the scaled ones.
	 */
	private record Styled(Item inner, io.github.theamethyx.femboyclient.core.hud.HudLayouts.Element element, boolean hidden) implements Item {
		@Override
		public String moduleId() {
			return inner.moduleId();
		}

		@Override
		public String name() {
			return inner.name();
		}

		@Override
		public boolean alignRight() {
			return inner.alignRight();
		}

		@Override
		public float scale() {
			return element.scale();
		}

		@Override
		public int width(UiContext ui) {
			return Math.round(inner.width(ui) * element.scale());
		}

		@Override
		public int height(UiContext ui) {
			return Math.round(inner.height(ui) * element.scale());
		}

		@Override
		public void draw(UiContext ui, int x, int y, int width, boolean alignRight, boolean dimmed) {
			float opacity = element.opacity() * (hidden ? 0.35f : 1);
			if (element.scale() == 1 && opacity >= 1) {
				inner.draw(ui, x, y, width, alignRight, dimmed);
				return;
			}

			var pose = ui.graphics().pose();
			pose.pushMatrix();
			pose.translate(x, y);
			pose.scale(element.scale(), element.scale());
			ui.setAlpha(opacity);
			try {
				inner.draw(ui, 0, 0, inner.width(ui), alignRight, dimmed);
			} finally {
				ui.setAlpha(1);
				pose.popMatrix();
			}
		}
	}

	private final class WidgetItem implements Item {
		private final Module module;
		private final ClientHooks.HudWidget widget;
		private final int width;
		private final int height;

		WidgetItem(Module module, ClientHooks.HudWidget widget, int width, int height) {
			this.module = module;
			this.widget = widget;
			this.width = width;
			this.height = height;
		}

		@Override
		public String moduleId() {
			return module.id();
		}

		@Override
		public String name() {
			return module.name();
		}

		@Override
		public boolean alignRight() {
			return widget.hudAnchor().right();
		}

		@Override
		public int width(UiContext ui) {
			return width;
		}

		@Override
		public int height(UiContext ui) {
			return height;
		}

		@Override
		public void draw(UiContext ui, int x, int y, int ignoredWidth, boolean alignRight, boolean dimmed) {
			modules.runGuarded(module, ModuleManager.Phase.RENDER_HUD, () -> widget.drawHud(ui, x, y));
		}
	}

	private record Block(String moduleId, String name, List<HudLine> lines, boolean panel, boolean alignRight, int measuredWidth) implements Item {
		int pad() {
			return panel ? 4 : 0;
		}

		/** Width of the widest line plus padding, measured once when the block is built. */
		static int measure(UiContext ui, List<HudLine> lines, boolean panel) {
			int widest = 0;
			for (HudLine line : lines) {
				int w = ui.textWidth(line.value()) + (line.label().isEmpty() ? 0 : ui.textWidth(line.label()) + 6) + (line.accent() != 0 ? 4 : 0);
				widest = Math.max(widest, w);
			}

			return widest + (panel ? 8 : 0);
		}

		@Override
		public int width(UiContext ui) {
			return measuredWidth;
		}

		int height() {
			return lines.size() * LINE + pad() * 2 - 1;
		}

		@Override
		public int height(UiContext ui) {
			return height();
		}

		@Override
		public void draw(UiContext ui, int x, int y, int width, boolean alignRight, boolean dimmed) {
			if (panel) {
				ui.roundRect(x, y, width, height(), Theme.withAlpha(Theme.SURFACE, dimmed ? 0.45f : Theme.HUD_BACKGROUND));
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
					ui.rect(rowX, rowY - 1, rowWidth, LINE, Theme.withAlpha(Theme.SURFACE, dimmed ? 0.4f : Theme.HUD_BACKGROUND * 0.92f));
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
				ui.text(line.label(), x, y, Theme.TEXT_SECONDARY);
			}

			ui.text(line.value(), x + labelWidth, y, line.valueColor());
		}
	}
}
