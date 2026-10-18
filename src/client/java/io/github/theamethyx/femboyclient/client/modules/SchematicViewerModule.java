package io.github.theamethyx.femboyclient.client.modules;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

import com.google.gson.JsonObject;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Util;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;

import net.fabricmc.loader.api.FabricLoader;

import io.github.theamethyx.femboyclient.client.FemboyClient;
import io.github.theamethyx.femboyclient.client.gui.Icon;
import io.github.theamethyx.femboyclient.client.gui.Theme;
import io.github.theamethyx.femboyclient.client.gui.UiContext;
import io.github.theamethyx.femboyclient.client.gui.widget.Button;
import io.github.theamethyx.femboyclient.client.gui.widget.Container;
import io.github.theamethyx.femboyclient.client.gui.widget.Widget;
import io.github.theamethyx.femboyclient.client.hud.HudAnchor;
import io.github.theamethyx.femboyclient.client.hud.HudLine;
import io.github.theamethyx.femboyclient.client.module.ClientHooks;
import io.github.theamethyx.femboyclient.client.render.RenderingUtilities;
import io.github.theamethyx.femboyclient.client.schematic.Schematic;
import io.github.theamethyx.femboyclient.core.module.Module;
import io.github.theamethyx.femboyclient.core.module.ModuleCategory;
import io.github.theamethyx.femboyclient.core.setting.BooleanSetting;
import io.github.theamethyx.femboyclient.core.setting.ColorSetting;
import io.github.theamethyx.femboyclient.core.setting.EnumSetting;
import io.github.theamethyx.femboyclient.core.setting.NumberSetting;

/**
 * Shows a schematic in the world as outlines: blocks still missing and blocks that are wrong. Load a {@code .nbt},
 * {@code .schem} or {@code .litematic} file from {@code config/femboyclient/schematics/}, then place it where you stand.
 * The world is compared with the schematic a little each tick; the result also feeds the Material List.
 */
public final class SchematicViewerModule extends Module implements ClientHooks.WorldRender, ClientHooks.HudBlock, ClientHooks.CustomControls {
	public enum Turn implements EnumSetting.Labeled {
		NONE("None", Rotation.NONE), CLOCKWISE_90("90° clockwise", Rotation.CLOCKWISE_90), CLOCKWISE_180("180°", Rotation.CLOCKWISE_180),
		COUNTERCLOCKWISE_90("90° counterclockwise", Rotation.COUNTERCLOCKWISE_90);

		private final String label;
		final Rotation rotation;

		Turn(String label, Rotation rotation) {
			this.label = label;
			this.rotation = rotation;
		}

		@Override
		public String label() {
			return label;
		}
	}

	private final EnumSetting<Turn> turn = settings().add(new EnumSetting<>("rotation", "Rotation", "Turn the schematic around its placement corner.", Turn.NONE));
	private final BooleanSetting exactStates = settings().add(new BooleanSetting("exact_states", "Compare block states",
			"Also count a block as wrong when its state differs (facing, half, ...). Off compares only the block type.", false));
	private final NumberSetting layer = settings().add(new NumberSetting("layer", "Show layer", "Only outline this layer (0 is the bottom); -1 shows all.",
			-1, -1, 383, 1, ""));
	private final NumberSetting range = settings().add(new NumberSetting("range", "Outline range", "Outline blocks this close to you.", 32, 8, 128, 4, "blocks"));
	private final NumberSetting maxBoxes = settings().add(new NumberSetting("max_boxes", "Maximum outlines", "Clutter and performance limit.", 2000, 100, 10000, 100, ""));
	private final BooleanSetting throughWalls = settings().add(new BooleanSetting("through_walls", "Through walls", "Draw outlines even behind blocks.", false));
	private final ColorSetting missingColor = settings().add(new ColorSetting("missing_color", "Missing", "Color of blocks still to place.", 0x6EA8FF));
	private final ColorSetting wrongColor = settings().add(new ColorSetting("wrong_color", "Wrong", "Color of blocks that differ from the schematic.", 0xF27272));

	/** The comparison of one full pass over the schematic. */
	public record Progress(int placed, int missing, int wrong, int unloaded, List<BlockPos> missingNear, List<BlockPos> wrongNear,
			Map<Item, int[]> materials) {
		public int total() {
			return placed + missing + wrong + unloaded;
		}
	}

	private Schematic schematic;
	private String file;
	private BlockPos origin;
	private String loadError;
	private Progress progress;

	// Pass in progress.
	private int cursor;
	private int placed;
	private int missing;
	private int wrong;
	private int unloaded;
	private List<BlockPos> missingNear = new ArrayList<>();
	private List<BlockPos> wrongNear = new ArrayList<>();
	private Map<Item, int[]> materials = new LinkedHashMap<>();

	public SchematicViewerModule() {
		super("schematic_viewer", "Schematic Viewer", "Load a .nbt, .schem or .litematic build and see missing and wrong blocks in the world.",
				ModuleCategory.WORLD, false, NO_KEY);
	}

	@Override
	public Tier tier() {
		return Tier.ASSIST;
	}

	public static Path folder() {
		return FabricLoader.getInstance().getConfigDir().resolve(FemboyClient.MOD_ID).resolve("schematics");
	}

	public Schematic schematic() {
		return schematic;
	}

	public BlockPos origin() {
		return origin;
	}

	public Progress progress() {
		return progress;
	}

	public String loadError() {
		return loadError;
	}

	/** Schematic files in the folder, by name. */
	public List<String> files() {
		try {
			Files.createDirectories(folder());
			try (Stream<Path> list = Files.list(folder())) {
				return list.map(p -> p.getFileName().toString())
						.filter(name -> name.matches("(?i).+\\.(nbt|schem|schematic|litematic)")).sorted().toList();
			}
		} catch (IOException e) {
			return List.of();
		}
	}

	/** Loads {@code name} from the folder. @return {@code null} on success, otherwise why not */
	public String load(String name) {
		Path path = folder().resolve(name).normalize();
		if (!path.startsWith(folder())) {
			return "Invalid file name.";
		}

		try {
			schematic = Schematic.load(path);
			file = name;
			loadError = null;
			restartPass();
			progress = null;
			markDirty();
			return null;
		} catch (IOException | RuntimeException e) {
			schematic = null;
			loadError = name + ": " + e.getMessage();
			return loadError;
		}
	}

	/** Puts the schematic's lowest corner at {@code pos}. */
	public void placeAt(BlockPos pos) {
		origin = pos.immutable();
		restartPass();
		progress = null;
		markDirty();
	}

	private void markDirty() {
		FemboyClient femboy = FemboyClient.get();
		if (femboy != null && femboy.config() != null) {
			femboy.config().markDirty();
		}
	}

	private void restartPass() {
		cursor = 0;
		placed = 0;
		missing = 0;
		wrong = 0;
		unloaded = 0;
		missingNear = new ArrayList<>();
		wrongNear = new ArrayList<>();
		materials = new LinkedHashMap<>();
	}

	@Override
	protected void onTick() {
		Minecraft client = Minecraft.getInstance();
		if (schematic == null && file != null && loadError == null) {
			load(file);
		}

		if (schematic == null || origin == null) {
			return;
		}

		ClientLevel level = client.level;
		LocalPlayer player = client.player;
		Rotation rotation = turn.get().rotation;
		double rangeSq = range.get() * range.get();
		int cap = maxBoxes.intValue();
		int shownLayer = layer.intValue();
		long deadline = System.nanoTime() + 2_000_000;
		int count = schematic.blockCount();
		while (cursor < count) {
			// About 2 ms per tick; the clock is read every 256 blocks.
			if ((cursor & 255) == 0 && System.nanoTime() > deadline) {
				break;
			}

			int index = cursor++;
			BlockPos local = schematic.position(index);
			BlockPos world = origin.offset(local.rotate(rotation));
			BlockState expected = schematic.state(index).rotate(rotation);
			Item item = itemFor(expected);
			int amount = amountFor(expected);
			if (!level.isLoaded(world)) {
				unloaded++;
				continue;
			}

			BlockState actual = level.getBlockState(world);
			boolean correct = exactStates.get() ? actual == expected : actual.is(expected.getBlock());
			if (item != null) {
				int[] entry = materials.computeIfAbsent(item, k -> new int[2]);
				entry[0] += amount;
				if (correct) {
					entry[1] += amount;
				}
			}

			if (correct) {
				placed++;
				continue;
			}

			boolean isMissing = actual.canBeReplaced();
			if (isMissing) {
				missing++;
			} else {
				wrong++;
			}

			if ((shownLayer < 0 || local.getY() == shownLayer) && world.distToCenterSqr(player.position()) <= rangeSq) {
				List<BlockPos> list = isMissing ? missingNear : wrongNear;
				if (missingNear.size() + wrongNear.size() < cap) {
					list.add(world);
				}
			}
		}

		if (cursor >= count) {
			progress = new Progress(placed, missing, wrong, unloaded, List.copyOf(missingNear), List.copyOf(wrongNear), Map.copyOf(materials));
			restartPass();
		}
	}

	/** The item that places {@code state}, or {@code null} when no item does (fire, portals...). */
	public static Item itemFor(BlockState state) {
		if (state.getFluidState().is(Fluids.WATER) && state.getBlock().asItem() == Items.AIR) return Items.WATER_BUCKET;
		if (state.getFluidState().is(Fluids.LAVA) && state.getBlock().asItem() == Items.AIR) return Items.LAVA_BUCKET;
		if (state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF) && state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER) return null;
		if (state.hasProperty(BlockStateProperties.BED_PART) && state.getValue(BlockStateProperties.BED_PART) == BedPart.HEAD) return null;
		Item item = state.getBlock().asItem();
		return item == Items.AIR ? null : item;
	}

	private static int amountFor(BlockState state) {
		return state.hasProperty(BlockStateProperties.SLAB_TYPE) && state.getValue(BlockStateProperties.SLAB_TYPE) == SlabType.DOUBLE ? 2 : 1;
	}

	// ----- persistence -----------------------------------------------------------------------

	@Override
	protected void saveExtra(JsonObject json) {
		if (file != null) {
			json.addProperty("file", file);
		}

		if (origin != null) {
			json.addProperty("x", origin.getX());
			json.addProperty("y", origin.getY());
			json.addProperty("z", origin.getZ());
		}
	}

	@Override
	protected void loadExtra(JsonObject json, List<String> warnings) {
		try {
			if (json.has("file")) {
				file = json.get("file").getAsString();
			}

			if (json.has("x")) {
				origin = new BlockPos(json.get("x").getAsInt(), json.get("y").getAsInt(), json.get("z").getAsInt());
			}
		} catch (RuntimeException e) {
			warnings.add("the saved schematic placement was unreadable and was cleared.");
			file = null;
			origin = null;
		}
	}

	@Override
	protected void resetExtra() {
		file = null;
		origin = null;
		schematic = null;
		progress = null;
		loadError = null;
	}

	// ----- rendering -------------------------------------------------------------------------

	@Override
	public void renderWorld(RenderingUtilities render) {
		Progress current = progress;
		if (current == null) {
			return;
		}

		int miss = missingColor.get();
		int bad = wrongColor.get();
		for (BlockPos pos : current.missingNear()) {
			AABB box = new AABB(pos).deflate(0.05);
			if (!io.github.theamethyx.femboyclient.client.render.FrameProjection.inView(box)) {
				continue;
			}

			render.filledBox(box, (0x30 << 24) | (miss & 0xFFFFFF), throughWalls.get());
			render.boxOutline(box, 0xC0000000 | (miss & 0xFFFFFF), 1f, throughWalls.get());
		}

		for (BlockPos pos : current.wrongNear()) {
			AABB box = new AABB(pos).inflate(0.02);
			if (!io.github.theamethyx.femboyclient.client.render.FrameProjection.inView(box)) {
				continue;
			}

			render.filledBox(box, (0x40 << 24) | (bad & 0xFFFFFF), throughWalls.get());
			render.boxOutline(box, 0xFF000000 | (bad & 0xFFFFFF), 1.5f, throughWalls.get());
		}
	}

	@Override
	public HudAnchor hudAnchor() {
		return HudAnchor.TOP_LEFT;
	}

	@Override
	public void buildHud(List<HudLine> lines) {
		if (schematic == null) {
			lines.add(HudLine.of("Schematic", loadError != null ? loadError : "none loaded", loadError != null ? Theme.ERROR : Theme.TEXT_MUTED));
			return;
		}

		if (origin == null) {
			lines.add(HudLine.of("Schematic", schematic.name() + " - press Place here", Theme.WARNING));
			return;
		}

		Progress current = progress;
		if (current == null) {
			lines.add(HudLine.of("Schematic", schematic.name() + " - comparing...", Theme.TEXT_SECONDARY));
			return;
		}

		int known = current.placed() + current.missing() + current.wrong();
		String percent = known == 0 ? "-" : Math.round(100.0 * current.placed() / known) + "%";
		lines.add(HudLine.of("Schematic", String.format(Locale.ROOT, "%s  %s built (%d/%d)", schematic.name(), percent, current.placed(), known),
				Theme.ACCENT_STRONG));
		lines.add(HudLine.of("Left", current.missing() + " missing, " + current.wrong() + " wrong"
				+ (current.unloaded() > 0 ? ", " + current.unloaded() + " not loaded" : ""), current.wrong() > 0 ? Theme.WARNING : Theme.TEXT_SECONDARY));
	}

	// ----- GUI -------------------------------------------------------------------------------

	@Override
	public List<Widget> createControls(UiContext ui, Runnable refresh) {
		List<Widget> widgets = new ArrayList<>();
		widgets.add(new Actions(refresh));
		String status = schematic == null ? (loadError != null ? loadError : "No schematic loaded.")
				: schematic.name() + ": " + schematic.sizeX() + " x " + schematic.sizeY() + " x " + schematic.sizeZ() + ", " + schematic.blockCount() + " blocks"
						+ (origin == null ? ", not placed yet" : ", corner at " + origin.toShortString())
						+ (schematic.warnings().isEmpty() ? "" : ". " + String.join(" ", schematic.warnings()));
		widgets.add(new Note(status, loadError != null && schematic == null ? Theme.ERROR : Theme.TEXT_SECONDARY));
		List<String> files = files();
		if (files.isEmpty()) {
			widgets.add(new Note("Put .nbt, .schem or .litematic files into config/femboyclient/schematics/ and press Refresh.", Theme.TEXT_MUTED));
		}

		for (String name : files) {
			widgets.add(new FileRow(name, refresh));
		}

		return widgets;
	}

	private final class Actions extends Container {
		private final Button place;
		private final Button open;
		private final Button reload;

		Actions(Runnable refresh) {
			place = add(new Button("Place here", Button.Style.PRIMARY, () -> {
				LocalPlayer player = Minecraft.getInstance().player;
				if (player != null) {
					placeAt(player.blockPosition());
					refresh.run();
				}
			}).icon(Icon.PLUS));
			place.enabledWhen(() -> schematic != null && Minecraft.getInstance().player != null);
			place.tooltip("Put the schematic's lowest corner (north-west, bottom) at the block you stand on.");
			open = add(new Button("Open folder", Button.Style.SECONDARY, () -> Util.getPlatform().openPath(folder())));
			reload = add(new Button("Refresh", Button.Style.GHOST, refresh));
		}

		@Override
		public int preferredHeight(UiContext ui, int width) {
			return 15;
		}

		@Override
		protected void onLayout() {
			int w = (width - 8) / 3;
			place.setBounds(x, y, w, 15);
			open.setBounds(x + w + 4, y, w, 15);
			reload.setBounds(x + 2 * (w + 4), y, w, 15);
		}
	}

	private final class FileRow extends Container {
		private final String name;
		private final Button loadButton;

		FileRow(String name, Runnable refresh) {
			this.name = name;
			loadButton = add(new Button(() -> name.equals(file) && schematic != null ? "Loaded" : "Load", Button.Style.SECONDARY, () -> {
				load(name);
				refresh.run();
			}));
		}

		@Override
		public int preferredHeight(UiContext ui, int width) {
			return 20;
		}

		@Override
		protected void onLayout() {
			loadButton.setBounds(x + width - 50, y + 3, 46, 14);
		}

		@Override
		public void render(UiContext ui) {
			ui.roundRect(x, y, width, height, name.equals(file) ? Theme.SURFACE_ACTIVE : Theme.SURFACE);
			ui.textClipped(name, x + 6, y + 6, width - 62, Theme.TEXT);
			super.render(ui);
		}
	}

	static final class Note extends Widget {
		private final String text;
		private final int color;

		Note(String text, int color) {
			this.text = text;
			this.color = color;
		}

		@Override
		public int preferredHeight(UiContext ui, int width) {
			return ui.wrappedHeight(text, width - 8) + 4;
		}

		@Override
		public void render(UiContext ui) {
			ui.wrapped(text, x + 4, y + 2, width - 8, color);
		}
	}
}
