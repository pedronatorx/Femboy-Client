package io.github.theamethyx.femboyclient.client.modules;

import java.util.List;
import java.util.Locale;

import com.mojang.blaze3d.platform.NativeImage;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;

import io.github.theamethyx.femboyclient.client.FemboyClient;
import io.github.theamethyx.femboyclient.client.Performance;
import io.github.theamethyx.femboyclient.client.gui.Theme;
import io.github.theamethyx.femboyclient.client.gui.UiContext;
import io.github.theamethyx.femboyclient.client.hud.HudAnchor;
import io.github.theamethyx.femboyclient.client.module.ClientHooks;
import io.github.theamethyx.femboyclient.client.world.WorldIdentity;
import io.github.theamethyx.femboyclient.core.module.Module;
import io.github.theamethyx.femboyclient.core.module.ModuleCategory;
import io.github.theamethyx.femboyclient.core.setting.BooleanSetting;
import io.github.theamethyx.femboyclient.core.setting.EnumSetting;
import io.github.theamethyx.femboyclient.core.setting.NumberSetting;

/**
 * A minimap of the chunks your client has loaded, drawn with the same colors as vanilla maps, with other players,
 * your waypoints and your last death. Under a roof (caves, the Nether) it shows the floor around your height instead
 * of the surface. Written from scratch, in the spirit of Xaero's Minimap; it has no world map and keeps nothing on disk.
 *
 * <p>Work: the map is redrawn into a second image a few rows per tick (a fixed budget) and swapped in when complete,
 * so it never tears; between redraws it only moves. While you stand still it is redrawn at most every 5 seconds. It
 * reads only blocks the client has loaded.
 */
public final class MinimapModule extends Module implements ClientHooks.HudWidget {
	private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(FemboyClient.MOD_ID, "dynamic/minimap");

	public enum Layer implements EnumSetting.Labeled {
		AUTO("Automatic (caves under a roof)"), SURFACE("Always the surface"), CAVES("Always caves");

		private final String label;

		Layer(String label) {
			this.label = label;
		}

		@Override
		public String label() {
			return label;
		}
	}

	private final EnumSetting<HudAnchor> anchor = settings().add(new EnumSetting<>("anchor", "Position",
			"Screen corner when not placed with the HUD editor.", HudAnchor.TOP_RIGHT));
	private final NumberSetting size = settings().add(new NumberSetting("size", "Size", "Width and height of the map.", 96, 48, 200, 4, "px"));
	private final NumberSetting range = settings().add(new NumberSetting("range", "Blocks shown",
			"Blocks from the center to the edge of the map.", 48, 16, 128, 8, "blocks"));
	private final BooleanSetting rotate = settings().add(new BooleanSetting("rotate", "Rotate with view",
			"Turn the map so that the direction you look is up; off keeps north up.", false));
	private final EnumSetting<Layer> layer = settings().add(new EnumSetting<>("layer", "Layer",
			"The surface, or the floor around your height (caves and the Nether).", Layer.AUTO));
	private final BooleanSetting players = settings().add(new BooleanSetting("players", "Players", "Show other players your client can see.", true));
	private final BooleanSetting waypoints = settings().add(new BooleanSetting("waypoints", "Waypoints and death",
			"Show your waypoints in this world and where you last died.", true));
	private final BooleanSetting coordinates = settings().add(new BooleanSetting("coordinates", "Coordinates", "Show your position under the map.", true));
	private final NumberSetting interval = settings().add(new NumberSetting("interval", "Redraw every",
			"How often the map is drawn again (it moves smoothly in between).", 10, 2, 60, 1, "ticks")).advanced();

	private DynamicTexture texture;
	private NativeImage back;
	private int textureRadius;
	private int shownOriginX;
	private int shownOriginZ;
	private boolean hasShown;
	private int passOriginX;
	private int passOriginZ;
	private int passRow = -1;
	private boolean passCaves;
	private int passY;
	private int[] previousHeights;
	private int ticksUntilPass;
	private int ticksSincePass;
	private int passes;

	public MinimapModule() {
		super("minimap", "Minimap", "A map of the loaded area around you with players, waypoints and your last death; shows caves under a roof.",
				ModuleCategory.HUD, false, NO_KEY);
	}

	@Override
	public Tier tier() {
		return Tier.VISUAL;
	}

	@Override
	public List<String> aliases() {
		return List.of("map", "radar", "xaero", "journeymap");
	}

	@Override
	public HudAnchor hudAnchor() {
		return anchor.get();
	}

	@Override
	public int hudWidth(UiContext ui) {
		return size.intValue();
	}

	@Override
	public int hudHeight(UiContext ui) {
		return size.intValue() + (coordinates.get() ? 11 : 0);
	}

	/** Completed redraws (for tests). */
	public int passes() {
		return passes;
	}

	@Override
	protected void onDisable() {
		release();
	}

	@Override
	protected void onWorldChange() {
		release();
	}

	private void release() {
		if (texture != null) {
			Minecraft.getInstance().getTextureManager().release(TEXTURE);
			texture = null;
		}

		if (back != null) {
			back.close();
			back = null;
		}

		hasShown = false;
		passRow = -1;
	}

	/** Radius of the image around its center: room for the corners when rotated and for moving between redraws. */
	private int wantedRadius() {
		return (int) Math.ceil(range.intValue() * 1.45) + 16;
	}

	@Override
	protected void onTick() {
		Minecraft client = Minecraft.getInstance();
		LocalPlayer player = client.player;
		ClientLevel level = client.level;
		if (player == null || level == null) {
			return;
		}

		if (passRow < 0) {
			ticksSincePass++;
			if (--ticksUntilPass > 0) {
				return;
			}

			// Standing still, the map only changes when blocks do: redraw then at most every 5 seconds.
			if (hasShown && ticksSincePass < 100 && player.getBlockX() - textureRadius == shownOriginX && player.getBlockZ() - textureRadius == shownOriginZ
					&& player.getBlockY() == passY) {
				ticksUntilPass = Performance.interval(interval.intValue());
				return;
			}

			startPass(player, level);
		}

		int n = textureRadius * 2;
		// A full redraw is spread over about 12 ticks (24 outside Balanced), so a tick reads at most a few thousand columns.
		int rows = Math.max(4, n / (Performance.mode() == io.github.theamethyx.femboyclient.client.GlobalOptions.PerformanceMode.BALANCED ? 12 : 24));
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int i = 0; i < rows && passRow < n; i++, passRow++) {
			drawRow(level, passRow, pos);
		}

		if (passRow >= n) {
			finishPass();
		}
	}

	private void startPass(LocalPlayer player, ClientLevel level) {
		int radius = wantedRadius();
		if (back == null || radius != textureRadius) {
			if (back != null) {
				back.close();
			}

			textureRadius = radius;
			back = new NativeImage(radius * 2, radius * 2, false);
			if (texture != null && texture.getPixels() != null && texture.getPixels().getWidth() != radius * 2) {
				Minecraft.getInstance().getTextureManager().release(TEXTURE);
				texture = null;
				hasShown = false;
			}
		}

		passOriginX = player.getBlockX() - textureRadius;
		passOriginZ = player.getBlockZ() - textureRadius;
		passY = player.getBlockY();
		passCaves = switch (layer.get()) {
			case SURFACE -> false;
			case CAVES -> true;
			case AUTO -> level.dimensionType().hasCeiling() || !level.canSeeSky(player.blockPosition().above());
		};
		previousHeights = new int[textureRadius * 2];
		for (int i = 0; i < previousHeights.length; i++) {
			previousHeights[i] = Integer.MIN_VALUE;
		}

		passRow = 0;
	}

	private void drawRow(ClientLevel level, int row, BlockPos.MutableBlockPos pos) {
		int z = passOriginZ + row;
		int n = textureRadius * 2;
		for (int column = 0; column < n; column++) {
			int x = passOriginX + column;
			if (!level.hasChunk(x >> 4, z >> 4)) {
				back.setPixel(column, row, 0xFF0E0B14);
				previousHeights[column] = Integer.MIN_VALUE;
				continue;
			}

			int height = passCaves ? caveFloor(level, x, z, pos) : level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
			if (height == Integer.MIN_VALUE) {
				back.setPixel(column, row, 0xFF1A1622);
				previousHeights[column] = Integer.MIN_VALUE;
				continue;
			}

			back.setPixel(column, row, color(level, x, height, z, previousHeights[column], pos));
			previousHeights[column] = height;
		}
	}

	/** The highest floor (a solid block with room above it) from just above your head down to 32 blocks below. */
	private int caveFloor(ClientLevel level, int x, int z, BlockPos.MutableBlockPos pos) {
		boolean airAbove = false;
		for (int y = passY + 2; y >= passY - 32 && y >= level.getMinY(); y--) {
			BlockState state = level.getBlockState(pos.set(x, y, z));
			boolean empty = state.isAir();
			if (!empty && airAbove) {
				return y;
			}

			airAbove = empty || !state.getFluidState().isEmpty();
		}

		return Integer.MIN_VALUE;
	}

	/** Vanilla map shading: brighter where the ground rises toward the south, darker where it falls; water by depth. */
	private int color(ClientLevel level, int x, int y, int z, int northHeight, BlockPos.MutableBlockPos pos) {
		BlockState state = level.getBlockState(pos.set(x, y, z));
		int depth = 0;
		while (state.getFluidState().is(FluidTags.WATER) && depth < 12 && y - depth > level.getMinY()) {
			depth++;
			state = level.getBlockState(pos.set(x, y - depth, z));
		}

		MapColor mapColor = state.getMapColor(level, pos);
		if (depth > 0) {
			MapColor.Brightness shade = depth < 3 ? MapColor.Brightness.HIGH : depth < 7 ? MapColor.Brightness.NORMAL : MapColor.Brightness.LOW;
			return MapColor.WATER.calculateARGBColor(shade);
		}

		if (mapColor == MapColor.NONE) {
			return 0xFF1A1622;
		}

		MapColor.Brightness shade = northHeight == Integer.MIN_VALUE || northHeight == y ? MapColor.Brightness.NORMAL
				: y > northHeight ? MapColor.Brightness.HIGH : MapColor.Brightness.LOW;
		int argb = mapColor.calculateARGBColor(shade);
		if (passCaves) {
			// Deeper floors are darker, so drops read at a glance.
			float light = Math.max(0.45f, 1 - Math.max(0, passY - y) / 40f);
			argb = 0xFF000000 | ((int) (((argb >> 16) & 0xFF) * light) << 16) | ((int) (((argb >> 8) & 0xFF) * light) << 8) | (int) ((argb & 0xFF) * light);
		}

		return argb;
	}

	private void finishPass() {
		Minecraft client = Minecraft.getInstance();
		if (texture == null) {
			texture = new DynamicTexture(() -> "Femboy Client minimap", back);
			client.getTextureManager().register(TEXTURE, texture);
		} else {
			texture.setPixels(back);
			texture.upload();
		}

		back = new NativeImage(textureRadius * 2, textureRadius * 2, false);
		shownOriginX = passOriginX;
		shownOriginZ = passOriginZ;
		hasShown = true;
		passRow = -1;
		ticksUntilPass = Performance.interval(interval.intValue());
		ticksSincePass = 0;
		passes++;
	}

	@Override
	public void drawHud(UiContext ui, int x, int y) {
		Minecraft client = Minecraft.getInstance();
		LocalPlayer player = client.player;
		if (player == null) {
			return;
		}

		int s = size.intValue();
		float partial = client.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		double px = net.minecraft.util.Mth.lerp(partial, player.xo, player.getX());
		double pz = net.minecraft.util.Mth.lerp(partial, player.zo, player.getZ());
		float yaw = player.getViewYRot(partial);
		double scale = s / (2.0 * range.intValue());
		float angle = rotate.get() ? (float) Math.toRadians(-(yaw + 180)) : 0;

		ui.roundRect(x - 2, y - 2, s + 4, s + 4, Theme.withAlpha(Theme.SURFACE, Theme.HUD_BACKGROUND), 4);
		ui.pushClip(x, y, s, s);
		if (hasShown) {
			var pose = ui.graphics().pose();
			pose.pushMatrix();
			pose.translate(x + s / 2f, y + s / 2f);
			pose.rotate(angle);
			pose.scale((float) scale, (float) scale);
			pose.translate((float) (shownOriginX - px), (float) (shownOriginZ - pz));
			int n = textureRadius * 2;
			ui.graphics().blit(RenderPipelines.GUI_TEXTURED, TEXTURE, 0, 0, 0, 0, n, n, n, n);
			pose.popMatrix();
		} else {
			ui.textCentered("Drawing map...", x + s / 2, y + s / 2 - 4, Theme.TEXT_MUTED);
		}

		if (waypoints.get()) {
			WaypointsModule module = FemboyClient.get().modules().get(WaypointsModule.class);
			if (module != null) {
				for (WaypointsModule.Waypoint waypoint : module.visibleHere()) {
					marker(ui, x, y, s, scale, angle, waypoint.x() + 0.5 - px, waypoint.z() + 0.5 - pz, Theme.ACCENT_2, true);
				}
			}

			DeathCoordinatesModule deaths = FemboyClient.get().modules().get(DeathCoordinatesModule.class);
			DeathCoordinatesModule.Death death = deaths == null ? null : deaths.last();
			if (death != null && death.dimension().equals(WorldIdentity.dimension())) {
				marker(ui, x, y, s, scale, angle, death.x() + 0.5 - px, death.z() + 0.5 - pz, Theme.ERROR, true);
			}
		}

		if (players.get() && client.level != null) {
			for (Player other : client.level.players()) {
				if (other != player) {
					marker(ui, x, y, s, scale, angle, other.getX() - px, other.getZ() - pz, 0xFFFFFFFF, false);
				}
			}
		}

		arrow(ui, x + s / 2, y + s / 2, rotate.get() ? 0 : (float) Math.toRadians(yaw + 180));
		// "N" at the edge in the direction of north (world -Z, turned like the map).
		double rx = Math.sin(angle);
		double ry = -Math.cos(angle);
		ui.textCentered("N", x + s / 2 + (int) Math.round(rx * (s / 2.0 - 6)), y + s / 2 + (int) Math.round(ry * (s / 2.0 - 6)) - 4, Theme.ACCENT_STRONG);
		ui.popClip();
		ui.outline(x - 2, y - 2, s + 4, s + 4, Theme.BORDER_STRONG, 4);
		if (coordinates.get()) {
			ui.textCentered(String.format(Locale.ROOT, "%d, %d, %d", player.getBlockX(), player.getBlockY(), player.getBlockZ()), x + s / 2, y + s + 3,
					Theme.TEXT_SECONDARY);
		}
	}

	/** A dot for something {@code dx}/{@code dz} blocks from you; things beyond the edge stay at the edge when {@code clamp}. */
	private static void marker(UiContext ui, int x, int y, int s, double scale, float angle, double dx, double dz, int color, boolean clamp) {
		double mx = (dx * Math.cos(angle) - dz * Math.sin(angle)) * scale;
		double my = (dx * Math.sin(angle) + dz * Math.cos(angle)) * scale;
		double half = s / 2.0 - 3;
		if (Math.abs(mx) > half || Math.abs(my) > half) {
			if (!clamp) {
				return;
			}

			double k = half / Math.max(Math.abs(mx), Math.abs(my));
			mx *= k;
			my *= k;
		}

		int cx = x + s / 2 + (int) Math.round(mx);
		int cy = y + s / 2 + (int) Math.round(my);
		ui.rect(cx - 2, cy - 2, 5, 5, 0xFF000000);
		ui.rect(cx - 1, cy - 1, 3, 3, color);
	}

	/** Your position and facing: a small arrow drawn from rotated rows. */
	private static void arrow(UiContext ui, int cx, int cy, float angle) {
		var pose = ui.graphics().pose();
		pose.pushMatrix();
		pose.translate(cx, cy);
		pose.rotate(angle);
		ui.rect(-1, -4, 2, 2, Theme.ACCENT_STRONG);
		ui.rect(-2, -2, 4, 2, Theme.ACCENT_STRONG);
		ui.rect(-3, 0, 6, 2, Theme.ACCENT_STRONG);
		ui.rect(-3, 2, 2, 2, Theme.ACCENT_STRONG);
		ui.rect(1, 2, 2, 2, Theme.ACCENT_STRONG);
		pose.popMatrix();
	}

	@Override
	public String workload() {
		return String.format(Locale.ROOT, "%d×%d map image, %d redraws%s", textureRadius * 2, textureRadius * 2, passes, passCaves ? ", cave layer" : "");
	}
}
