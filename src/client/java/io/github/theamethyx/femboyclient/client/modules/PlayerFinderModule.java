package io.github.theamethyx.femboyclient.client.modules;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import io.github.theamethyx.femboyclient.client.FemboyClient;
import io.github.theamethyx.femboyclient.client.gui.Theme;
import io.github.theamethyx.femboyclient.client.gui.UiContext;
import io.github.theamethyx.femboyclient.client.gui.widget.PlayerPanel;
import io.github.theamethyx.femboyclient.client.gui.widget.Widget;
import io.github.theamethyx.femboyclient.client.hud.HudAnchor;
import io.github.theamethyx.femboyclient.client.hud.HudLine;
import io.github.theamethyx.femboyclient.client.module.ClientHooks;
import io.github.theamethyx.femboyclient.client.world.WorldIdentity;
import io.github.theamethyx.femboyclient.core.module.Module;
import io.github.theamethyx.femboyclient.core.module.ModuleCategory;
import io.github.theamethyx.femboyclient.core.setting.BooleanSetting;
import io.github.theamethyx.femboyclient.core.setting.EnumSetting;

/**
 * Lists online players from the server's player list and helps you navigate to players in range and to
 * your own waypoints.
 *
 * <p>A position is known for every player the client has loaded, i.e. within the server's entity
 * tracking range (roughly your render distance). Players further away are shown as "position unknown",
 * or with their last seen position and its age. Nothing is inferred or invented.
 */
public final class PlayerFinderModule extends Module implements ClientHooks.HudBlock, ClientHooks.CustomControls {
	public static final int MAX_REMEMBERED = 200;

	public enum Sort implements EnumSetting.Labeled {
		NAME("Name"), DISTANCE("Distance"), PING("Ping");

		private final String label;

		Sort(String label) {
			this.label = label;
		}

		@Override
		public String label() {
			return label;
		}
	}

	/** How much is known about a player's position right now. */
	public enum Knowledge { IN_RANGE, LAST_SEEN, UNKNOWN }

	/** One row of the list. {@code position} is null when unknown; {@code seenAt} is when it was observed. */
	public record Entry(UUID id, String name, int ping, String gameMode, Knowledge knowledge, Vec3 position, long seenAt) {
	}

	private record Sighting(Vec3 position, long at, String world) {
	}

	private final EnumSetting<Sort> sort = settings().add(new EnumSetting<>("sort", "Sort by", "Order of the player list.", Sort.NAME));
	private final BooleanSetting rememberLastSeen = settings().add(new BooleanSetting("remember_last_seen", "Remember last seen positions",
			"When a player leaves your range, keep the position where you last saw them, shown with its age. Cleared when you change "
					+ "world or dimension.", true));
	private final BooleanSetting includeSelf = settings().add(new BooleanSetting("include_self", "Include yourself", "Show your own name in the list.", false));
	private final BooleanSetting hud = settings().add(new BooleanSetting("hud", "Navigation on HUD",
			"While navigating, show the target, distance and a direction arrow in the top-right corner.", true));

	private final Map<UUID, Sighting> lastSeen = new LinkedHashMap<>();
	private String sightingsWorld;
	private UUID trackedPlayer;
	private List<Entry> cachedEntries = List.of();
	private long cachedAt;
	private WaypointsModule.Waypoint trackedWaypoint;

	public PlayerFinderModule() {
		super("player_finder", "Player Finder", "Online player list with search and sorting, plus navigation to players in range and to your waypoints.",
				ModuleCategory.TRACKERS, false, NO_KEY);
		// Edited with the dropdown above the player list; one control is enough.
		sort.visibleWhen(() -> false);
		includeSelf.onChange(value -> cachedAt = 0);
		rememberLastSeen.onChange(value -> cachedAt = 0);
		sort.onChange(value -> cachedAt = 0);
	}

	// ----- knowledge -------------------------------------------------------------------------

	@Override
	protected void onTick() {
		Minecraft client = Minecraft.getInstance();
		String here = WorldIdentity.current() + "|" + WorldIdentity.dimension();
		if (!here.equals(sightingsWorld)) {
			lastSeen.clear();
			trackedPlayer = null;
			if (trackedWaypoint != null && !waypointIsHere(trackedWaypoint)) {
				trackedWaypoint = null;
			}

			sightingsWorld = here;
		}

		if (client.level == null || client.player == null || !rememberLastSeen.get()) {
			return;
		}

		long now = System.currentTimeMillis();
		for (AbstractClientPlayer other : client.level.players()) {
			if (other != client.player) {
				lastSeen.remove(other.getUUID());
				lastSeen.put(other.getUUID(), new Sighting(other.position(), now, here));
			}
		}

		while (lastSeen.size() > MAX_REMEMBERED) {
			lastSeen.remove(lastSeen.keySet().iterator().next());
		}
	}

	@Override
	protected void onDisable() {
		lastSeen.clear();
		trackedPlayer = null;
		trackedWaypoint = null;
		cachedEntries = List.of();
		cachedAt = 0;
	}

	/**
	 * The player list with what is known about each position, sorted by the chosen order. Cached for 100 ms:
	 * the panel asks once per row per frame.
	 */
	public List<Entry> entries() {
		long now = System.currentTimeMillis();
		if (now - cachedAt > 100) {
			cachedEntries = computeEntries();
			cachedAt = now;
		}

		return cachedEntries;
	}

	private List<Entry> computeEntries() {
		Minecraft client = Minecraft.getInstance();
		ClientPacketListener connection = client.getConnection();
		LocalPlayer self = client.player;
		if (connection == null || self == null || client.level == null) {
			return List.of();
		}

		List<Entry> result = new ArrayList<>();
		for (PlayerInfo info : connection.getOnlinePlayers()) {
			UUID id = info.getProfile().id();
			if (id.equals(self.getUUID()) && !includeSelf.get()) {
				continue;
			}

			String mode = info.getGameMode() == null ? "?" : info.getGameMode().getShortDisplayName().getString();
			AbstractClientPlayer entity = id.equals(self.getUUID()) ? self : findPlayer(id);
			if (entity != null) {
				result.add(new Entry(id, info.getProfile().name(), info.getLatency(), mode, Knowledge.IN_RANGE, entity.position(), System.currentTimeMillis()));
				continue;
			}

			Sighting sighting = rememberLastSeen.get() ? lastSeen.get(id) : null;
			if (sighting != null) {
				result.add(new Entry(id, info.getProfile().name(), info.getLatency(), mode, Knowledge.LAST_SEEN, sighting.position(), sighting.at()));
			} else {
				result.add(new Entry(id, info.getProfile().name(), info.getLatency(), mode, Knowledge.UNKNOWN, null, 0));
			}
		}

		Vec3 at = self.position();
		Comparator<Entry> byName = Comparator.comparing(e -> e.name().toLowerCase(Locale.ROOT));
		result.sort(switch (sort.get()) {
			case NAME -> byName;
			case PING -> Comparator.comparingInt(Entry::ping).thenComparing(byName);
			case DISTANCE -> Comparator.<Entry>comparingDouble(e -> e.position() == null ? Double.MAX_VALUE : e.position().distanceToSqr(at)).thenComparing(byName);
		});
		return result;
	}

	private static AbstractClientPlayer findPlayer(UUID id) {
		Minecraft client = Minecraft.getInstance();
		for (AbstractClientPlayer player : client.level.players()) {
			if (player.getUUID().equals(id)) {
				return player;
			}
		}

		return null;
	}

	public Sort sort() {
		return sort.get();
	}

	public void setSort(Sort value) {
		sort.set(value);
	}

	// ----- navigation ------------------------------------------------------------------------

	public void navigateTo(UUID player) {
		trackedPlayer = player;
		trackedWaypoint = null;
	}

	public void navigateTo(WaypointsModule.Waypoint waypoint) {
		trackedWaypoint = waypoint;
		trackedPlayer = null;
	}

	public void stopNavigation() {
		trackedPlayer = null;
		trackedWaypoint = null;
	}

	public UUID trackedPlayer() {
		return trackedPlayer;
	}

	public WaypointsModule.Waypoint trackedWaypoint() {
		return trackedWaypoint;
	}

	private static boolean waypointIsHere(WaypointsModule.Waypoint waypoint) {
		return waypoint.world().equals(WorldIdentity.current()) && waypoint.dimension().equals(WorldIdentity.dimension());
	}

	/** Waypoints of the current world and dimension, from the Waypoints module. */
	public List<WaypointsModule.Waypoint> waypointsHere() {
		WaypointsModule waypoints = FemboyClient.get().modules().get(WaypointsModule.class);
		return waypoints == null ? List.of() : waypoints.visibleHere();
	}

	/** The current navigation target, or null. */
	public record Target(String name, Vec3 position, String note, int color) {
	}

	public Target target() {
		if (trackedWaypoint != null) {
			return new Target(trackedWaypoint.name(), trackedWaypoint.center(), "waypoint", Theme.INFO);
		}

		if (trackedPlayer == null) {
			return null;
		}

		for (Entry entry : entries()) {
			if (entry.id().equals(trackedPlayer)) {
				return switch (entry.knowledge()) {
					case IN_RANGE -> new Target(entry.name(), entry.position(), "in range", Theme.SUCCESS);
					case LAST_SEEN -> new Target(entry.name(), entry.position(), "last seen " + ago(entry.seenAt()), Theme.WARNING);
					case UNKNOWN -> new Target(entry.name(), null, "position unknown", Theme.TEXT_MUTED);
				};
			}
		}

		return new Target("Player", null, "offline, position unknown", Theme.TEXT_MUTED);
	}

	public static String ago(long millis) {
		long seconds = Math.max(0, (System.currentTimeMillis() - millis) / 1000);
		return seconds < 60 ? seconds + " s ago" : seconds < 3600 ? seconds / 60 + " min ago" : seconds / 3600 + " h ago";
	}

	/** "120 m" plus an arrow relative to where you are looking. */
	public static String distanceAndArrow(Vec3 target) {
		LocalPlayer self = Minecraft.getInstance().player;
		if (self == null || target == null) {
			return "?";
		}

		double dx = target.x - self.getX();
		double dz = target.z - self.getZ();
		float bearing = (float) Math.toDegrees(Math.atan2(-dx, dz));
		float relative = Mth.wrapDegrees(bearing - self.getYRot());
		String[] arrows = {"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"};
		String arrow = arrows[Math.floorMod(Math.round(relative / 45), 8)];
		return String.format(Locale.ROOT, "%.0f m %s", target.distanceTo(self.position()), arrow);
	}

	// ----- GUI and HUD -----------------------------------------------------------------------

	@Override
	public List<Widget> createControls(UiContext ui, Runnable refresh) {
		return List.of(new PlayerPanel(this));
	}

	@Override
	public HudAnchor hudAnchor() {
		return HudAnchor.TOP_RIGHT;
	}

	@Override
	public void buildHud(List<HudLine> lines) {
		Target target = hud.get() ? target() : null;
		if (target == null) {
			return;
		}

		String value = target.position() == null ? target.note() : distanceAndArrow(target.position())
				+ (target.note().equals("in range") || target.note().equals("waypoint") ? "" : "  (" + target.note() + ")");
		lines.add(HudLine.of(target.name(), value, target.color()));
	}
}
