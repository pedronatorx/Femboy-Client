package io.github.theamethyx.femboyclient.client.modules;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import org.joml.Vector3f;
import org.joml.Vector3fc;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import io.github.theamethyx.femboyclient.client.FemboyClient;
import io.github.theamethyx.femboyclient.client.gui.Icon;
import io.github.theamethyx.femboyclient.client.gui.UiContext;
import io.github.theamethyx.femboyclient.client.module.ClientHooks;
import io.github.theamethyx.femboyclient.client.render.EspRenderer;
import io.github.theamethyx.femboyclient.core.module.Module;
import io.github.theamethyx.femboyclient.core.module.ModuleCategory;
import io.github.theamethyx.femboyclient.core.setting.BooleanSetting;
import io.github.theamethyx.femboyclient.core.setting.ColorSetting;
import io.github.theamethyx.femboyclient.core.setting.NumberSetting;

/**
 * Screen-edge arrows pointing at targets you are interested in, with a small marker when the target is
 * on screen. Deliberately different from ESP boxes: trackers say <em>where to look</em>, not what is there.
 *
 * <p>Sources, each labelled so you know how fresh the position is:
 * <ul>
 *   <li>Entities your client has loaded (players, hostile, passive mobs, villagers): live positions.</li>
 *   <li>Your waypoints in this world and dimension: fixed positions you saved.</li>
 *   <li>Player Finder's "last seen" positions: where a player was when they left your range, with the age.
 *       Only while Player Finder is on; nothing is guessed for players that were never in range.</li>
 * </ul>
 * The target list is rebuilt every few ticks; entities that despawn or leave range drop out at the next
 * rebuild and are skipped immediately if already removed.
 */
public final class TrackersModule extends Module implements ClientHooks.ScreenOverlay {
	public enum Source { PLAYER, HOSTILE, PASSIVE, VILLAGER, WAYPOINT, LAST_SEEN }

	/** One indicator as drawn in the last frame; read by automated tests. */
	public record Indicator(String name, Source source, boolean onScreen, int x, int y, String label) {
	}

	private record Target(String name, Source source, Entity entity, Vec3 fixed, long seenAt) {
		Vec3 position(float partialTick) {
			return entity != null ? entity.getPosition(partialTick).add(0, entity.getBbHeight() / 2, 0) : fixed;
		}
	}

	{ settings().section("Targets"); }
	private final BooleanSetting players = settings().add(new BooleanSetting("players", "Players", "Track other players in your range.", true));
	private final BooleanSetting hostile = settings().add(new BooleanSetting("hostile", "Hostile mobs", "Track hostile mobs.", false));
	private final BooleanSetting passive = settings().add(new BooleanSetting("passive", "Passive mobs", "Track passive mobs and animals.", false));
	private final BooleanSetting villagers = settings().add(new BooleanSetting("villagers", "Villagers", "Track villagers and wandering traders.", false));
	private final BooleanSetting waypoints = settings().add(new BooleanSetting("waypoints", "Waypoints",
			"Track your saved waypoints in this world and dimension.", true));
	private final BooleanSetting lastSeen = settings().add(new BooleanSetting("last_seen", "Last seen players",
			"Track where players were when they left your range (needs Player Finder on). Labelled with the age.", true));
	private final NumberSetting maxDistance = settings().add(new NumberSetting("max_distance", "Maximum distance",
			"Targets farther away are not tracked. Waypoints and last-seen positions count too.", 256, 16, 2048, 16, "blocks"));
	private final NumberSetting maxIndicators = settings().add(new NumberSetting("max_indicators", "Maximum indicators",
			"Only the nearest targets up to this number get an indicator.", 12, 1, 64, 1, ""));
	private final NumberSetting updateInterval = settings().add(new NumberSetting("update_interval", "Update interval",
			"How often the target list is rebuilt. Arrows still follow moving targets every frame.", 4, 1, 40, 1, "ticks"));

	{ settings().section("Style"); }
	private final NumberSetting size = settings().add(new NumberSetting("size", "Indicator size", "Size of the arrows and markers.", 10, 6, 24, 1, "px"));
	private final NumberSetting edgeMargin = settings().add(new NumberSetting("edge_margin", "Distance from screen edge",
			"How far from the screen edge the arrows sit.", 28, 8, 120, 2, "px"));
	private final BooleanSetting onScreenMarkers = settings().add(new BooleanSetting("on_screen_markers", "Markers on screen",
			"Show a small marker on targets that are on screen. Off: only off-screen targets get an arrow.", true));
	private final BooleanSetting labels = settings().add(new BooleanSetting("labels", "Labels", "Show the name next to each indicator.", true));
	private final BooleanSetting showDistance = settings().add(new BooleanSetting("show_distance", "Distance", "Add the distance to the label.", true));
	private final NumberSetting labelScale = settings().add(new NumberSetting("label_scale", "Label size", "Size of the label text.", 0.7, 0.5, 1.5, 0.05, "x"));
	private final NumberSetting opacity = settings().add(new NumberSetting("opacity", "Opacity", "How solid the indicators are.", 0.9, 0.2, 1, 0.05, ""));

	{ settings().section("Colors"); }
	private final ColorSetting playerColor = settings().add(new ColorSetting("color_players", "Players", "Color for players.", 0x9D6EFF));
	private final ColorSetting hostileColor = settings().add(new ColorSetting("color_hostile", "Hostile mobs", "Color for hostile mobs.", 0xF27272));
	private final ColorSetting passiveColor = settings().add(new ColorSetting("color_passive", "Passive mobs", "Color for passive mobs.", 0x5BD68A));
	private final ColorSetting villagerColor = settings().add(new ColorSetting("color_villagers", "Villagers", "Color for villagers.", 0xF2C14E));
	private final ColorSetting waypointColor = settings().add(new ColorSetting("color_waypoints", "Waypoints", "Color for waypoints.", 0x6EA8FF));
	private final ColorSetting lastSeenColor = settings().add(new ColorSetting("color_last_seen", "Last seen", "Color for last-seen positions.", 0xF29E4C));

	private List<Target> targets = List.of();
	private List<Indicator> lastIndicators = List.of();
	private int ticksUntilUpdate;

	public TrackersModule() {
		super("trackers", "Trackers", "Arrows at the screen edge pointing at players, mobs, waypoints and last-seen positions.",
				ModuleCategory.TRACKERS, false, NO_KEY);
		showDistance.visibleWhen(labels::get);
		labelScale.visibleWhen(labels::get);
	}

	@Override
	protected void onTick() {
		if (--ticksUntilUpdate > 0) {
			return;
		}

		ticksUntilUpdate = updateInterval.intValue();
		Minecraft client = Minecraft.getInstance();
		if (client.level == null || client.player == null) {
			targets = List.of();
			return;
		}

		Vec3 me = client.player.position();
		double max = maxDistance.get();
		List<Target> found = new ArrayList<>();
		for (Entity entity : client.level.entitiesForRendering()) {
			if (entity == client.player || !entity.isAlive() || entity.isInvisibleTo(client.player) || entity.distanceToSqr(me) > max * max) {
				continue;
			}

			Source source = sourceOf(entity);
			if (source != null && wanted(source)) {
				found.add(new Target(entity.getDisplayName().getString(), source, entity, null, 0));
			}
		}

		FemboyClient femboy = FemboyClient.get();
		if (waypoints.get()) {
			WaypointsModule module = femboy.modules().get(WaypointsModule.class);
			if (module != null) {
				for (WaypointsModule.Waypoint waypoint : module.visibleHere()) {
					if (waypoint.center().distanceTo(me) <= max) {
						found.add(new Target(waypoint.name(), Source.WAYPOINT, null, waypoint.center(), 0));
					}
				}
			}
		}

		PlayerFinderModule finder = femboy.modules().get(PlayerFinderModule.class);
		if (lastSeen.get() && finder != null && finder.isRunning()) {
			for (PlayerFinderModule.Entry entry : finder.entries()) {
				if (entry.knowledge() == PlayerFinderModule.Knowledge.LAST_SEEN && entry.position().distanceTo(me) <= max) {
					found.add(new Target(entry.name(), Source.LAST_SEEN, null, entry.position(), entry.seenAt()));
				}
			}
		}

		found.sort(Comparator.comparingDouble(t -> t.position(1).distanceToSqr(me)));
		targets = found.size() > maxIndicators.intValue() ? List.copyOf(found.subList(0, maxIndicators.intValue())) : List.copyOf(found);
	}

	private static Source sourceOf(Entity entity) {
		if (entity instanceof Player) {
			return Source.PLAYER;
		}

		return switch (EntityEspModule.groupOf(entity.getType())) {
			case EntityEspModule.HOSTILE -> Source.HOSTILE;
			case EntityEspModule.PASSIVE -> Source.PASSIVE;
			case EntityEspModule.VILLAGERS -> Source.VILLAGER;
			default -> null;
		};
	}

	private boolean wanted(Source source) {
		return switch (source) {
			case PLAYER -> players.get();
			case HOSTILE -> hostile.get();
			case PASSIVE -> passive.get();
			case VILLAGER -> villagers.get();
			case WAYPOINT -> waypoints.get();
			case LAST_SEEN -> lastSeen.get();
		};
	}

	private int colorOf(Source source) {
		return switch (source) {
			case PLAYER -> playerColor.get();
			case HOSTILE -> hostileColor.get();
			case PASSIVE -> passiveColor.get();
			case VILLAGER -> villagerColor.get();
			case WAYPOINT -> waypointColor.get();
			case LAST_SEEN -> lastSeenColor.get();
		};
	}

	@Override
	protected void onDisable() {
		targets = List.of();
		lastIndicators = List.of();
		ticksUntilUpdate = 0;
	}

	/** Indicators drawn in the last frame. */
	public List<Indicator> lastIndicators() {
		return lastIndicators;
	}

	@Override
	public void renderOverlay(UiContext ui, int screenWidth, int screenHeight, float partialTick) {
		Minecraft client = Minecraft.getInstance();
		Camera camera = client.gameRenderer.getMainCamera();
		Vec3 eye = camera.position();
		Vector3fc forward = camera.forwardVector();
		Vector3fc left = camera.leftVector();
		Vector3fc up = camera.upVector();
		int s = size.intValue();
		float alpha = opacity.get().floatValue();
		List<Indicator> drawn = new ArrayList<>();

		for (Target target : targets) {
			if (target.entity() != null && target.entity().isRemoved()) {
				continue;
			}

			Vec3 position = target.position(partialTick);
			Vec3 rel = position.subtract(eye);
			Vector3f r = new Vector3f((float) rel.x, (float) rel.y, (float) rel.z);
			int color = EspRenderer.withAlpha(colorOf(target.source()), alpha);
			int[] at = r.dot(forward) > 0.05f ? onScreen(client, position, screenWidth, screenHeight) : null;
			String label = label(target, rel.length());

			if (at != null) {
				if (!onScreenMarkers.get()) {
					continue;
				}

				ui.icon(Icon.TARGET, at[0] - s / 2, at[1] - s / 2, s, color);
				if (labels.get()) {
					drawLabel(ui, label, at[0], at[1] + s / 2 + 2, color, screenWidth, screenHeight);
				}

				drawn.add(new Indicator(target.name(), target.source(), true, at[0], at[1], label));
				continue;
			}

			// Off screen (or behind): direction on the screen plane, then pushed out to the edge rectangle.
			float dx = -r.dot(left);
			float dy = -r.dot(up);
			if (Math.abs(dx) < 1e-4 && Math.abs(dy) < 1e-4) {
				dy = 1;
			}

			float halfW = screenWidth / 2f - edgeMargin.get().floatValue();
			float halfH = screenHeight / 2f - edgeMargin.get().floatValue();
			float scale = Math.min(halfW / Math.max(1e-4f, Math.abs(dx)), halfH / Math.max(1e-4f, Math.abs(dy)));
			int ax = Math.round(screenWidth / 2f + dx * scale);
			int ay = Math.round(screenHeight / 2f + dy * scale);
			var pose = ui.graphics().pose();
			pose.pushMatrix();
			pose.translate(ax, ay);
			pose.rotate((float) Math.atan2(dx, -dy));
			ui.icon(Icon.ARROW, -s / 2, -s / 2, s, color);
			pose.popMatrix();
			if (labels.get()) {
				// Labels sit on the inner side of the arrow so they stay on screen.
				int lx = ax - Math.round(Math.signum(dx) * (s + 4));
				int ly = ay - Math.round(Math.signum(dy) * (s / 2f + 6)) - 3;
				drawLabel(ui, label, lx, ly, color, screenWidth, screenHeight);
			}

			drawn.add(new Indicator(target.name(), target.source(), false, ax, ay, label));
		}

		lastIndicators = List.copyOf(drawn);
	}

	private String label(Target target, double distance) {
		String text = target.name();
		if (showDistance.get()) {
			text += String.format(Locale.ROOT, " · %.0fm", distance);
		}

		return switch (target.source()) {
			case WAYPOINT -> text + " · waypoint";
			case LAST_SEEN -> text + " · last seen " + PlayerFinderModule.ago(target.seenAt());
			default -> text;
		};
	}

	private void drawLabel(UiContext ui, String text, int centerX, int topY, int color, int screenWidth, int screenHeight) {
		float scale = labelScale.get().floatValue();
		float w = ui.textWidth(text) * scale;
		int boxW = Math.round(w) + 4;
		int boxH = Math.round(8 * scale) + 3;
		int bx = Math.round(Math.max(2, Math.min(screenWidth - w - 2, centerX - w / 2))) - 2;
		int by = Math.round(Math.max(2, Math.min(screenHeight - 10, topY))) - 1;
		// Labels near each other (e.g. several arrows at the same edge) stack instead of overlapping.
		by = Math.max(2, ui.placeLabel(bx, by, boxW, boxH));
		ui.rect(bx, by, boxW, boxH, EspRenderer.withAlpha(0x101014, 0.45f));
		ui.textScaled(text, bx + 2, by + 1, scale, color | 0xFF000000);
	}

	private static int[] onScreen(Minecraft client, Vec3 world, int screenWidth, int screenHeight) {
		Vec3 ndc = client.gameRenderer.projectPointToScreen(world);
		if (Math.abs(ndc.x) > 1 || Math.abs(ndc.y) > 1) {
			return null;
		}

		return new int[] {(int) Math.round((ndc.x + 1) / 2 * screenWidth), (int) Math.round((1 - ndc.y) / 2 * screenHeight)};
	}
}
