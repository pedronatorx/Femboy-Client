package io.github.theamethyx.femboyclient.client.modules;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import io.github.theamethyx.femboyclient.client.Performance;
import io.github.theamethyx.femboyclient.client.combat.ItemChoices;
import io.github.theamethyx.femboyclient.client.gui.UiContext;
import io.github.theamethyx.femboyclient.client.module.ClientHooks;
import io.github.theamethyx.femboyclient.client.render.EspRenderer;
import io.github.theamethyx.femboyclient.client.render.FrameProjection;
import io.github.theamethyx.femboyclient.client.render.RenderingUtilities;
import io.github.theamethyx.femboyclient.client.render.TargetLines;
import io.github.theamethyx.femboyclient.client.world.BlockScanner;
import io.github.theamethyx.femboyclient.core.module.Module;
import io.github.theamethyx.femboyclient.core.module.ModuleCategory;
import io.github.theamethyx.femboyclient.core.setting.BooleanSetting;
import io.github.theamethyx.femboyclient.core.setting.ColorSetting;
import io.github.theamethyx.femboyclient.core.setting.EnumSetting;
import io.github.theamethyx.femboyclient.core.setting.IdSetSetting;
import io.github.theamethyx.femboyclient.core.setting.NumberSetting;

/**
 * Highlights chosen blocks (ores by default) in chunks your client has loaded, with labels and optional target lines
 * from the crosshair. Formerly Ore ESP; saved Ore ESP settings are moved here.
 *
 * <p>It reads every block of the loaded chunks, which reveals what is hidden behind other blocks, so it runs only in
 * singleplayer and on servers in your own network (LAN or VPN). It shows what the server sent: anti-xray servers send
 * fake or hidden ores, and those appear as sent.
 *
 * <p>Work: the scanner reads chunks once and then follows block changes as they happen; the list of shown blocks is
 * rebuilt only when the scanner's hits change or you move a few blocks; drawing skips boxes outside the view.
 */
public final class BlockEspModule extends Module implements ClientHooks.WorldRender, ClientHooks.ScreenOverlay {
	static final Set<String> DEFAULT_ORES = Set.of("minecraft:diamond_ore", "minecraft:deepslate_diamond_ore", "minecraft:emerald_ore",
			"minecraft:deepslate_emerald_ore", "minecraft:ancient_debris", "minecraft:gold_ore", "minecraft:deepslate_gold_ore",
			"minecraft:iron_ore", "minecraft:deepslate_iron_ore", "minecraft:redstone_ore", "minecraft:deepslate_redstone_ore",
			"minecraft:lapis_ore", "minecraft:deepslate_lapis_ore");

	private static final Map<String, Integer> COLORS = Map.ofEntries(Map.entry("diamond", 0x4FE3E3), Map.entry("emerald", 0x3DDC6E),
			Map.entry("ancient_debris", 0x9C6B4E), Map.entry("gold", 0xF2C14E), Map.entry("iron", 0xD8AF93), Map.entry("redstone", 0xF24A4A),
			Map.entry("lapis", 0x3E64F2), Map.entry("coal", 0x5A5A5A), Map.entry("copper", 0xE07B4B), Map.entry("quartz", 0xEDE7DF),
			Map.entry("amethyst", 0xB58FFF), Map.entry("spawner", 0xF27272), Map.entry("chest", 0xF29E4C));

	public enum ColorMode implements EnumSetting.Labeled {
		BY_BLOCK("By block (ore colors)"), CUSTOM("One custom color");

		private final String label;

		ColorMode(String label) {
			this.label = label;
		}

		@Override
		public String label() {
			return label;
		}
	}

	{ settings().section("Blocks"); }
	private final IdSetSetting blocks = settings().add(new IdSetSetting("blocks", "Blocks",
			"Blocks to highlight. Click a category (ores, containers, redstone, plants...) to select or clear all of it.", DEFAULT_ORES));
	private final NumberSetting radius = settings().add(new NumberSetting("radius", "Radius", "Chunks around you to search.", 4, 1, 12, 1, "chunks"));
	private final BooleanSetting exposedOnly = settings().add(new BooleanSetting("exposed_only", "Only exposed blocks",
			"Show only blocks touching air, water or lava (what you could see from a cave).", false));

	{ settings().section("Highlight"); }
	private final BooleanSetting outline = settings().add(new BooleanSetting("outline", "Outline", "Draw the edges of each block.", true));
	private final BooleanSetting fill = settings().add(new BooleanSetting("fill", "Fill", "Fill each block with a faint tint.", true));
	private final BooleanSetting throughWalls = settings().add(new BooleanSetting("through_walls", "Through walls", "Draw through blocks.", true));
	private final EnumSetting<ColorMode> colorMode = settings().add(new EnumSetting<>("color_mode", "Colors",
			"A color per kind of block (diamond cyan, gold yellow...), or one color for all.", ColorMode.BY_BLOCK));
	private final ColorSetting customColor = settings().add(new ColorSetting("color", "Custom color", "Color for every block.", 0xB58FFF))
			.visibleWhen(() -> colorMode.get() == ColorMode.CUSTOM);
	private final NumberSetting opacity = settings().add(new NumberSetting("opacity", "Opacity", "How solid outlines are; fills are fainter.",
			0.8, 0.1, 1, 0.05, ""));

	{ settings().section("Labels"); }
	private final BooleanSetting labels = settings().add(new BooleanSetting("labels", "Name and distance",
			"Show the block's name and distance above the nearest highlighted blocks.", false));
	private final NumberSetting maxLabels = settings().add(new NumberSetting("max_labels", "Most labels", "Labels for the nearest blocks only.",
			12, 1, 64, 1, "")).visibleWhen(labels::get);

	private final TargetLines.Settings lines = new TargetLines.Settings(settings(), false, TargetLines.Visibility.ALWAYS, 64, 192, 16);

	{ settings().section("Performance"); }
	private final NumberSetting limit = settings().add(new NumberSetting("limit", "Maximum shown", "Closest blocks drawn at most.", 400, 50, 2000, 50, ""));
	private final NumberSetting recheck = settings().add(new NumberSetting("recheck", "Full re-read every",
			"Changes are followed as they happen; this re-reads every chunk now and then as a safety net.", 60, 10, 600, 10, "s"));

	private final BlockScanner scanner = new BlockScanner();
	private Set<Block> chosen = Set.of();
	private final Map<Block, Integer> blockColors = new HashMap<>();
	private final Map<Block, String> blockNames = new HashMap<>();
	private List<Shown> shown = List.of();
	private long shownVersion = -1;
	private BlockPos shownFrom;
	private boolean selectionDirty = true;
	private int ticksUntilSightCheck;
	private int rebuilds;

	/** One highlighted block. {@code box} and colors are computed once per rebuild, not per frame. */
	public static final class Shown implements TargetLines.Target {
		private final BlockPos pos;
		private final BlockState state;
		private final AABB box;
		private final Vec3 center;
		private final int rgb;
		private final String name;
		private boolean sight = true;

		Shown(BlockPos pos, BlockState state, int rgb, String name) {
			this.pos = pos;
			this.state = state;
			this.box = new AABB(pos);
			this.center = Vec3.atCenterOf(pos);
			this.rgb = rgb;
			this.name = name;
		}

		public BlockPos pos() {
			return pos;
		}

		public BlockState state() {
			return state;
		}

		@Override
		public Vec3 linePoint(float partialTick) {
			return center;
		}

		@Override
		public int lineColor() {
			return rgb;
		}

		@Override
		public boolean lineOfSight() {
			return sight;
		}
	}

	public BlockEspModule() {
		super("block_esp", "Block ESP",
				"Highlights chosen blocks (ores by default) in loaded chunks, with labels and lines from your crosshair. Singleplayer and your own network only.",
				ModuleCategory.VISUALS, false, NO_KEY);
		blocks.choices(ItemChoices::blocks);
		blocks.onChange(ids -> rebuildFilter());
		Runnable reselect = () -> selectionDirty = true;
		exposedOnly.onChange(value -> reselect.run());
		limit.onChange(value -> reselect.run());
		colorMode.onChange(value -> reselect.run());
		customColor.onChange(value -> reselect.run());
	}

	@Override
	public boolean testEnvironmentOnly() {
		// Reading every loaded block shows what other blocks hide.
		return true;
	}

	private void rebuildFilter() {
		chosen = ItemChoices.resolveBlocks(blocks.get());
		Set<Block> current = chosen;
		scanner.setFilter(state -> current.contains(state.getBlock()));
		selectionDirty = true;
	}

	@Override
	protected void onEnable() {
		rebuildFilter();
		scanner.start();
	}

	@Override
	protected void onDisable() {
		scanner.stop();
		shown = List.of();
		shownVersion = -1;
	}

	@Override
	protected void onWorldChange() {
		scanner.clear();
		shown = List.of();
		shownVersion = -1;
	}

	/** Blocks highlighted after the last update, nearest first; read by automated tests. */
	public List<Shown> shown() {
		return shown;
	}

	public BlockScanner scanner() {
		return scanner;
	}

	/** Times the shown list was rebuilt (for tests: only when something changed). */
	public int rebuilds() {
		return rebuilds;
	}

	public TargetLines.Settings lines() {
		return lines;
	}

	@Override
	protected void onTick() {
		Minecraft client = Minecraft.getInstance();
		LocalPlayer player = client.player;
		BlockPos at = player.blockPosition();
		scanner.tick(client.level, at, radius.intValue(), Performance.periodMillis(recheck.intValue() * 1000L), Performance.scanBudgetNanos());

		boolean moved = shownFrom == null || shownFrom.distManhattan(at) >= 3;
		if (selectionDirty || moved || scanner.version() != shownVersion) {
			select(client, at);
		}

		if (lines.enabled.get() && lines.visibility.get() == TargetLines.Visibility.LINE_OF_SIGHT && --ticksUntilSightCheck <= 0) {
			ticksUntilSightCheck = Performance.interval(5);
			int checks = Math.min(shown.size(), lines.maxCount.intValue());
			for (int i = 0; i < checks; i++) {
				Shown target = shown.get(i);
				target.sight = TargetLines.lineOfSight(target.center, target.pos);
			}
		}
	}

	private void select(Minecraft client, BlockPos at) {
		selectionDirty = false;
		shownVersion = scanner.version();
		shownFrom = at;
		rebuilds++;
		List<BlockScanner.Hit> hits = scanner.hits();
		List<BlockScanner.Hit> candidates = new ArrayList<>(hits.size());
		for (BlockScanner.Hit hit : hits) {
			if (!exposedOnly.get() || exposed(client, hit.pos())) {
				candidates.add(hit);
			}
		}

		candidates.sort(Comparator.comparingDouble(hit -> hit.pos().distSqr(at)));
		int max = Performance.cap(limit.intValue(), 150);
		List<Shown> next = new ArrayList<>(Math.min(max, candidates.size()));
		for (int i = 0; i < candidates.size() && i < max; i++) {
			BlockScanner.Hit hit = candidates.get(i);
			Block block = hit.state().getBlock();
			next.add(new Shown(hit.pos(), hit.state(), colorOf(block), blockNames.computeIfAbsent(block, b -> b.getName().getString())));
		}

		shown = List.copyOf(next);
		ticksUntilSightCheck = 0;
	}

	private static boolean exposed(Minecraft client, BlockPos pos) {
		BlockPos.MutableBlockPos neighbor = new BlockPos.MutableBlockPos();
		for (Direction direction : Direction.values()) {
			BlockState state = client.level.getBlockState(neighbor.setWithOffset(pos, direction));
			if (state.isAir() || !state.getFluidState().isEmpty()) {
				return true;
			}
		}

		return false;
	}

	private int colorOf(Block block) {
		if (colorMode.get() == ColorMode.CUSTOM) {
			return customColor.get();
		}

		return blockColors.computeIfAbsent(block, BlockEspModule::defaultColor);
	}

	static int defaultColor(Block block) {
		String id = BuiltInRegistries.BLOCK.getKey(block).getPath();
		for (Map.Entry<String, Integer> entry : COLORS.entrySet()) {
			if (id.contains(entry.getKey())) {
				return entry.getValue();
			}
		}

		return 0xB58FFF;
	}

	@Override
	public void renderWorld(RenderingUtilities render) {
		float alpha = opacity.get().floatValue();
		boolean walls = throughWalls.get();
		for (Shown block : shown) {
			if (!FrameProjection.inView(block.box)) {
				continue;
			}

			if (fill.get()) {
				render.filledBox(block.box, EspRenderer.withAlpha(block.rgb, alpha * 0.25f), walls);
			}

			if (outline.get()) {
				render.boxOutline(block.box, EspRenderer.withAlpha(block.rgb, alpha), 1.5f, walls);
			}
		}

		lines.draw(render, shown);
	}

	@Override
	public void renderOverlay(UiContext ui, int screenWidth, int screenHeight, float partialTick) {
		if (!labels.get()) {
			return;
		}

		Vec3 camera = render().position();
		int drawn = 0;
		for (Shown block : shown) {
			if (drawn >= maxLabels.intValue()) {
				break;
			}

			int[] at = io.github.theamethyx.femboyclient.client.render.ScreenProjection.toScreen(block.center.add(0, 0.7, 0), screenWidth, screenHeight);
			if (at == null) {
				continue;
			}

			ui.worldLabel(block.name + "  " + Math.round(block.center.distanceTo(camera)) + "m", at[0], at[1], block.rgb);
			drawn++;
		}
	}

	private static net.minecraft.client.Camera render() {
		return Minecraft.getInstance().gameRenderer.getMainCamera();
	}

	/** For the HUD and tests: e.g. "412 found, 81/81 chunks". */
	public String status() {
		return String.format(Locale.ROOT, "%d found, %d/%d chunks", scanner.count(), Math.min(scanner.scannedChunks(), scanner.chunksInRange()),
				scanner.chunksInRange());
	}

	@Override
	public java.util.List<String> aliases() {
		return java.util.List.of("ore esp", "ores", "block finder");
	}

	@Override
	public String workload() {
		return String.format(java.util.Locale.ROOT, "%,d blocks found in %d/%d chunks, %d shown, %d lines", scanner().count(), scanner().scannedChunks(),
				scanner().chunksInRange(), shown().size(), lines().drawn());
	}
}
