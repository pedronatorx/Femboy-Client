package io.github.theamethyx.femboyclient.client.modules;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import io.github.theamethyx.femboyclient.client.Performance;
import io.github.theamethyx.femboyclient.client.gui.UiContext;
import io.github.theamethyx.femboyclient.client.module.ClientHooks;
import io.github.theamethyx.femboyclient.client.render.EspRenderer;
import io.github.theamethyx.femboyclient.client.render.FrameProjection;
import io.github.theamethyx.femboyclient.client.render.RenderingUtilities;
import io.github.theamethyx.femboyclient.client.render.TargetLines;
import io.github.theamethyx.femboyclient.client.world.BlockChanges;
import io.github.theamethyx.femboyclient.core.module.Module;
import io.github.theamethyx.femboyclient.core.module.ModuleCategory;
import io.github.theamethyx.femboyclient.core.setting.BooleanSetting;
import io.github.theamethyx.femboyclient.core.setting.ColorSetting;
import io.github.theamethyx.femboyclient.core.setting.IdSetSetting;
import io.github.theamethyx.femboyclient.core.setting.NumberSetting;

/**
 * Highlights storage blocks (chests, barrels, shulker boxes, furnaces...) in the chunks the client has
 * loaded. Blocks are chosen by block id, not by block-entity type: copper chests, for example, share the
 * chest block entity but are separate choices here.
 *
 * <p>Scans run at the scan interval, never per frame, and at once when a storage block is placed or broken nearby. It
 * reads only block entities the client already has and never asks the server. Boxes, colors and names are worked out
 * per scan; each frame only draws what is in view, labels the nearest few and draws target lines.
 */
public final class StorageEspModule extends Module implements ClientHooks.WorldRender, ClientHooks.ScreenOverlay, BlockChanges.Listener {
	/** Storage groups in display order, each with its built-in color. */
	public enum Group {
		CHESTS("Chests", 0xF29E4C), TRAPPED("Trapped chests", 0xF27272), ENDER("Ender chests", 0x9D6EFF),
		BARRELS("Barrels", 0xC9A27A), SHULKERS("Shulker boxes", 0xE070C8), HOPPERS("Hoppers", 0x8A8A99),
		DISPENSERS("Dispensers & droppers", 0x6EA8FF), FURNACES("Furnaces", 0xF2C14E), OTHER("Other storage", 0x4CC9C0);

		public final String label;
		public final int rgb;

		Group(String label, int rgb) {
			this.label = label;
			this.rgb = rgb;
		}
	}

	/** Classifies a block id; {@code null} for blocks that are not storage. */
	public static Group groupOf(String blockId) {
		if (!blockId.startsWith("minecraft:")) {
			return null;
		}

		String path = blockId.substring("minecraft:".length());
		return switch (path) {
			case "chest" -> Group.CHESTS;
			case "trapped_chest" -> Group.TRAPPED;
			case "ender_chest" -> Group.ENDER;
			case "barrel" -> Group.BARRELS;
			case "hopper" -> Group.HOPPERS;
			case "dispenser", "dropper", "crafter" -> Group.DISPENSERS;
			case "furnace", "blast_furnace", "smoker" -> Group.FURNACES;
			case "brewing_stand", "chiseled_bookshelf", "decorated_pot" -> Group.OTHER;
			default -> path.endsWith("copper_chest") ? Group.CHESTS
					: path.endsWith("shulker_box") ? Group.SHULKERS
					: path.endsWith("_shelf") ? Group.OTHER : null;
		};
	}

	private final IdSetSetting blocks = settings().add(new IdSetSetting("blocks", "Storage blocks",
			"Which storage blocks to highlight. Click a group to select or clear all of it.", defaultBlocks()));
	private final BooleanSetting outline = settings().add(new BooleanSetting("outline", "Outline", "Draw the edges of each block.", true));
	private final BooleanSetting fill = settings().add(new BooleanSetting("fill", "Fill", "Fill each block with a faint tint.", true));
	private final BooleanSetting throughWalls = settings().add(new BooleanSetting("through_walls", "Show through walls",
			"Draw highlights even when other blocks are in the way.", true));
	private final BooleanSetting labels = settings().add(new BooleanSetting("labels", "Labels", "Show the block's name and distance above it.", false));
	private final NumberSetting maxLabels = settings().add(new NumberSetting("max_labels", "Most labels", "Labels for the nearest storage blocks only.",
			24, 1, 64, 1, "")).visibleWhen(labels::get);
	private final BooleanSetting customColors = settings().add(new BooleanSetting("custom_colors", "Custom colors",
			"Choose your own color for each group instead of the built-in palette.", false));
	private final Map<Group, ColorSetting> colors = groupColors();
	private final NumberSetting maxDistance = settings().add(new NumberSetting("max_distance", "Maximum distance",
			"Storage blocks farther away than this are not highlighted.", 64, 16, 256, 16, "blocks"));
	private final NumberSetting interval = settings().add(new NumberSetting("scan_interval", "Scan interval",
			"Performance option: how often the loaded chunks are checked for storage blocks.", 1, 0.25, 10, 0.25, "s"));
	private final NumberSetting maxTargets = settings().add(new NumberSetting("max_targets", "Maximum highlights",
			"Performance limit: only the nearest storage blocks up to this number are drawn.", 256, 16, 2048, 16, ""));

	private final TargetLines.Settings lines = new TargetLines.Settings(settings(), false, TargetLines.Visibility.ALWAYS, 64, 256, 16);
	private final IdSetSetting lineGroups = settings().add(new IdSetSetting("line_groups", "Lines to",
			"Which kinds of storage get a target line.", allGroupIds())).visibleWhen(lines.enabled::get);

	/** One highlighted storage block, worked out at scan time. */
	private static final class Found implements TargetLines.Target {
		final BlockPos pos;
		final AABB box;
		final Vec3 center;
		final Group group;
		final String name;
		final int rgb;
		final boolean line;
		boolean sight = true;

		Found(BlockPos pos, AABB box, Group group, String name, int rgb, boolean line) {
			this.pos = pos;
			this.box = box;
			this.center = box.getCenter();
			this.group = group;
			this.name = name;
			this.rgb = rgb;
			this.line = line;
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

		@Override
		public boolean wantsLine() {
			return line;
		}
	}

	private Set<String> selected = Set.of();
	private List<Found> found = List.of();
	private int ticksUntilScan;
	private int ticksUntilSightCheck;
	private int scans;
	private List<IdSetSetting.Choice> choices;

	public StorageEspModule() {
		super("storage_esp", "Storage ESP", "Highlights chests, barrels, shulker boxes and other storage blocks you choose.",
				ModuleCategory.VISUALS, false, NO_KEY);
		blocks.choices(this::choices);
		blocks.onChange(ids -> {
			selected = Set.copyOf(ids);
			ticksUntilScan = 0;
		});
		selected = Set.copyOf(blocks.get());
		lineGroups.choices(() -> {
			List<IdSetSetting.Choice> list = new ArrayList<>();
			for (Group group : Group.values()) {
				list.add(new IdSetSetting.Choice(groupId(group), group.label, "Storage"));
			}

			return list;
		});
		// Colors and line choices are baked into the scan results: rescan when they change.
		Runnable rescan = () -> ticksUntilScan = 0;
		customColors.onChange(value -> rescan.run());
		colors.values().forEach(color -> color.onChange(value -> rescan.run()));
		lineGroups.onChange(value -> rescan.run());
	}

	/** Id of a group in the "Lines to" choice, shaped like the registry ids the setting stores. */
	static String groupId(Group group) {
		return "storage:" + group.name().toLowerCase(Locale.ROOT);
	}

	private static Set<String> allGroupIds() {
		Set<String> ids = new HashSet<>();
		for (Group group : Group.values()) {
			ids.add(groupId(group));
		}

		return ids;
	}

	/** One color per group, listed right after the "Custom colors" switch and shown only while it is on. */
	private Map<Group, ColorSetting> groupColors() {
		Map<Group, ColorSetting> map = new HashMap<>();
		for (Group group : Group.values()) {
			ColorSetting color = settings().add(new ColorSetting("color_" + group.name().toLowerCase(Locale.ROOT), group.label + " color",
					"Highlight color for " + group.label.toLowerCase(Locale.ROOT) + ".", group.rgb));
			color.visibleWhen(customColors::get);
			map.put(group, color);
		}

		return map;
	}

	private static Set<String> defaultBlocks() {
		Set<String> ids = new HashSet<>();
		for (Block block : BuiltInRegistries.BLOCK) {
			String id = BuiltInRegistries.BLOCK.getKey(block).toString();
			Group group = groupOf(id);
			if (group == Group.CHESTS || group == Group.TRAPPED || group == Group.BARRELS || group == Group.SHULKERS) {
				ids.add(id);
			}
		}

		return ids;
	}

	private List<IdSetSetting.Choice> choices() {
		if (choices == null) {
			List<IdSetSetting.Choice> all = new ArrayList<>();
			for (Block block : BuiltInRegistries.BLOCK) {
				String id = BuiltInRegistries.BLOCK.getKey(block).toString();
				Group group = groupOf(id);
				if (group != null) {
					all.add(new IdSetSetting.Choice(id, block.getName().getString(), group.label));
				}
			}

			all.sort(Comparator.<IdSetSetting.Choice>comparingInt(c -> groupIndex(c.group())).thenComparing(c -> c.label().toLowerCase(Locale.ROOT)));
			choices = List.copyOf(all);
		}

		return choices;
	}

	private static int groupIndex(String label) {
		for (Group group : Group.values()) {
			if (group.label.equals(label)) {
				return group.ordinal();
			}
		}

		return Group.values().length;
	}

	private int colorOf(Group group) {
		return customColors.get() ? colors.get(group).get() : group.rgb;
	}

	// ----- scanning --------------------------------------------------------------------------

	@Override
	protected void onEnable() {
		BlockChanges.add(this);
		ticksUntilScan = 0;
	}

	@Override
	protected void onTick() {
		Minecraft client = Minecraft.getInstance();
		ClientLevel level = client.level;
		if (level == null || client.player == null) {
			found = List.of();
			return;
		}

		if (--ticksUntilScan <= 0) {
			ticksUntilScan = Performance.interval(Math.max(1, (int) Math.round(interval.get() * 20)));
			scan(level, client.player.position());
		}

		if (lines.enabled.get() && lines.visibility.get() == TargetLines.Visibility.LINE_OF_SIGHT && --ticksUntilSightCheck <= 0) {
			ticksUntilSightCheck = Performance.interval(5);
			int checks = Math.min(found.size(), lines.maxCount.intValue());
			for (int i = 0; i < checks; i++) {
				Found f = found.get(i);
				f.sight = TargetLines.lineOfSight(f.center, f.pos);
			}
		}
	}

	@Override
	protected void onWorldChange() {
		// New world or dimension: positions from the old one must never be drawn here.
		found = List.of();
		ticksUntilScan = 0;
	}

	// A storage block placed or broken: scan again right away instead of waiting for the interval.
	@Override
	public void blockChanged(ClientLevel level, BlockPos pos, BlockState oldState, BlockState newState) {
		if (oldState.getBlock() != newState.getBlock() && (oldState.hasBlockEntity() || newState.hasBlockEntity())) {
			ticksUntilScan = Math.min(ticksUntilScan, 2);
		}
	}

	@Override
	public void chunkLoaded(ClientLevel level, LevelChunk chunk) {
	}

	@Override
	public void chunkUnloaded(ClientLevel level, LevelChunk chunk) {
		if (!found.isEmpty()) {
			ticksUntilScan = Math.min(ticksUntilScan, 2);
		}
	}

	private void scan(ClientLevel level, Vec3 center) {
		if (selected.isEmpty()) {
			found = List.of();
			return;
		}

		double max = maxDistance.get();
		int radius = (int) Math.ceil(max / 16);
		int cx = (int) Math.floor(center.x) >> 4;
		int cz = (int) Math.floor(center.z) >> 4;
		List<Found> result = new ArrayList<>();
		for (int dx = -radius; dx <= radius; dx++) {
			for (int dz = -radius; dz <= radius; dz++) {
				LevelChunk chunk = level.getChunkSource().getChunk(cx + dx, cz + dz, ChunkStatus.FULL, false);
				if (chunk == null) {
					continue; // not loaded on this client: nothing to show, nothing requested
				}

				for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
					BlockPos pos = blockEntity.getBlockPos();
					if (Vec3.atCenterOf(pos).distanceToSqr(center) > max * max) {
						continue;
					}

					BlockState state = blockEntity.getBlockState();
					String id = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
					Group group = groupOf(id);
					if (group == null || !selected.contains(id)) {
						continue;
					}

					result.add(new Found(pos.immutable(), boxOf(level, pos, state), group, state.getBlock().getName().getString(), colorOf(group),
							lineGroups.get().contains(groupId(group))));
				}
			}
		}

		result.sort(Comparator.comparingDouble(f -> f.center.distanceToSqr(center)));
		int keep = Performance.cap(maxTargets.intValue(), 96);
		found = result.size() > keep ? List.copyOf(result.subList(0, keep)) : List.copyOf(result);
		ticksUntilSightCheck = 0;
		scans++;
	}

	private static AABB boxOf(ClientLevel level, BlockPos pos, BlockState state) {
		VoxelShape shape = state.getShape(level, pos);
		AABB local = shape.isEmpty() ? new AABB(0, 0, 0, 1, 1, 1) : shape.bounds();
		return local.move(pos).inflate(0.01);
	}

	@Override
	protected void onDisable() {
		BlockChanges.remove(this);
		found = List.of();
	}

	/** Positions highlighted after the last scan, nearest first; read by automated tests. */
	public List<BlockPos> highlighted() {
		List<BlockPos> positions = new ArrayList<>(found.size());
		found.forEach(f -> positions.add(f.pos));
		return positions;
	}

	/** Scans made so far (for tests). */
	public int scans() {
		return scans;
	}

	public TargetLines.Settings lines() {
		return lines;
	}

	// ----- rendering -------------------------------------------------------------------------

	@Override
	public void renderWorld(RenderingUtilities render) {
		List<EspRenderer.Target> targets = new ArrayList<>(found.size());
		for (Found f : found) {
			if (FrameProjection.inView(f.box)) {
				targets.add(new EspRenderer.Target(f.box, f.rgb, null));
			}
		}

		EspRenderer.drawBoxes(render, targets, EspRenderer.Style.simple(outline.get(), fill.get(), throughWalls.get(), 1.5f));
		lines.draw(render, found);
	}

	@Override
	public void renderOverlay(UiContext ui, int screenWidth, int screenHeight, float partialTick) {
		if (!labels.get()) {
			return;
		}

		Vec3 camera = Minecraft.getInstance().gameRenderer.getMainCamera().position();
		int limit = Performance.cap(maxLabels.intValue(), 12);
		List<EspRenderer.Target> targets = new ArrayList<>(Math.min(limit, found.size()));
		for (Found f : found) {
			if (targets.size() >= limit) {
				break;
			}

			targets.add(new EspRenderer.Target(f.box, f.rgb, EspRenderer.Label.of(f.name, EntityEspModule.meters(f.center.distanceTo(camera)))));
		}

		EspRenderer.drawLabels(ui, targets, screenWidth, screenHeight, limit, EspRenderer.LabelStyle.DEFAULT);
	}
}
