package io.github.theamethyx.femboyclient.client.gui.widget;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import io.github.theamethyx.femboyclient.client.gui.Icon;
import io.github.theamethyx.femboyclient.client.gui.Theme;
import io.github.theamethyx.femboyclient.client.gui.UiContext;
import io.github.theamethyx.femboyclient.client.modules.PlayerFinderModule;
import io.github.theamethyx.femboyclient.client.modules.WaypointsModule;

/**
 * Player Finder's panel: current navigation target, a searchable and sortable player list with what is
 * known about each position, and the waypoints of this world as navigation targets.
 */
public class PlayerPanel extends Container {
	private static final int MAX_ROWS = 60;

	private final PlayerFinderModule finder;
	private final Toolbar toolbar;
	private final NavigationCard navigation;
	private String query = "";
	private String shownSignature;

	public PlayerPanel(PlayerFinderModule finder) {
		this.finder = finder;
		// Created after 'finder' is set: both read from it.
		this.toolbar = new Toolbar();
		this.navigation = new NavigationCard();
	}

	private List<PlayerFinderModule.Entry> filtered() {
		List<PlayerFinderModule.Entry> all = finder.entries();
		if (query.isBlank()) {
			return all;
		}

		String q = query.trim().toLowerCase(Locale.ROOT);
		return all.stream().filter(e -> e.name().toLowerCase(Locale.ROOT).contains(q)).toList();
	}

	private void rebuildIfNeeded() {
		List<PlayerFinderModule.Entry> entries = filtered();
		List<WaypointsModule.Waypoint> waypoints = finder.waypointsHere();
		StringBuilder signature = new StringBuilder(query).append('|').append(finder.isRunning()).append('|');
		entries.forEach(e -> signature.append(e.id()).append(','));
		signature.append('|').append(waypoints.size());
		if (signature.toString().equals(shownSignature)) {
			return;
		}

		shownSignature = signature.toString();
		children.clear();
		children.add(navigation);
		children.add(toolbar);
		if (!finder.isRunning()) {
			children.add(new Note("Turn Player Finder on to see the player list."));
		} else if (entries.isEmpty()) {
			children.add(new Note(query.isBlank() ? "No other players online." : "No players match \"" + query + "\"."));
		}

		for (int i = 0; i < Math.min(MAX_ROWS, entries.size()); i++) {
			children.add(new PlayerRow(entries.get(i).id()));
		}

		if (entries.size() > MAX_ROWS) {
			children.add(new Note((entries.size() - MAX_ROWS) + " more players; search to narrow the list."));
		}

		children.add(new Heading("Waypoints in this world"));
		if (waypoints.isEmpty()) {
			children.add(new Note("No waypoints here. Add them in World > Waypoints."));
		}

		for (WaypointsModule.Waypoint waypoint : waypoints) {
			children.add(new WaypointRow(waypoint));
		}
	}

	@Override
	public int preferredHeight(UiContext ui, int width) {
		rebuildIfNeeded();
		int total = 0;
		for (Widget child : children) {
			total += child.preferredHeight(ui, width) + Theme.GAP;
		}

		return Math.max(0, total - Theme.GAP);
	}

	@Override
	protected void onLayout() {
		stack(null);
	}

	private void stack(UiContext ui) {
		int cy = y;
		for (Widget child : children) {
			int h = child.preferredHeight(ui, width);
			child.setBounds(x, cy, width, h);
			cy += h + Theme.GAP;
		}
	}

	@Override
	public void render(UiContext ui) {
		rebuildIfNeeded();
		stack(ui);
		super.render(ui);
	}

	private PlayerFinderModule.Entry entry(java.util.UUID id) {
		for (PlayerFinderModule.Entry entry : finder.entries()) {
			if (entry.id().equals(id)) {
				return entry;
			}
		}

		return null;
	}

	// ----- pieces ----------------------------------------------------------------------------

	private final class Toolbar extends Container {
		private final TextField search;
		private final Dropdown<PlayerFinderModule.Sort> sort;

		Toolbar() {
			search = add(new TextField("Search players", 32).icon(Icon.SEARCH).onChange(text -> query = text));
			search.tooltip("Filter the list by name.");
			sort = add(new Dropdown<>(() -> List.of(PlayerFinderModule.Sort.values()), finder::sort, finder::setSort, s -> "Sort: " + s.label()));
			sort.tooltip("Sort by name, by distance (players with a known position first) or by ping.");
		}

		@Override
		public int preferredHeight(UiContext ui, int width) {
			return 15;
		}

		@Override
		protected void onLayout() {
			int sortWidth = Math.min(100, width / 2 - Theme.GAP);
			search.setBounds(x, y, width - sortWidth - Theme.GAP, 15);
			sort.setBounds(x + width - sortWidth, y, sortWidth, 15);
		}
	}

	/** The current target, or how to pick one. */
	private final class NavigationCard extends Container {
		private final Button stop;

		NavigationCard() {
			stop = add(new Button("Stop", Button.Style.SECONDARY, finder::stopNavigation).icon(Icon.STOP));
			stop.enabledWhen(() -> finder.target() != null);
			stop.tooltip("Stop navigating.");
		}

		@Override
		public int preferredHeight(UiContext ui, int width) {
			return 34;
		}

		@Override
		protected void onLayout() {
			stop.setBounds(x + width - 56, y + 9, 50, 16);
		}

		@Override
		public void render(UiContext ui) {
			ui.roundRect(x, y, width, height, Theme.SURFACE);
			ui.icon(Icon.NAVIGATE, x + 6, y + 6, 9, Theme.ACCENT);
			PlayerFinderModule.Target target = finder.target();
			if (target == null) {
				ui.text("Navigation", x + 19, y + 7, Theme.TEXT);
				ui.textClipped("Pick a visible player or a waypoint below.", x + 6, y + 20, width - 68, Theme.TEXT_MUTED);
			} else {
				ui.textClipped("Navigating to " + target.name(), x + 19, y + 7, width - 81, Theme.TEXT);
				String detail = target.position() == null ? target.note()
						: PlayerFinderModule.distanceAndArrow(target.position()) + "  ·  " + target.note();
				ui.textClipped(detail, x + 6, y + 20, width - 68, target.color());
			}

			super.render(ui);
		}
	}

	private final class PlayerRow extends Widget {
		private final java.util.UUID id;

		PlayerRow(java.util.UUID id) {
			this.id = id;
		}

		@Override
		public int preferredHeight(UiContext ui, int width) {
			return 26;
		}

		@Override
		public void render(UiContext ui) {
			PlayerFinderModule.Entry entry = entry(id);
			if (entry == null) {
				return; // left the server; the next rebuild removes the row
			}

			boolean tracked = id.equals(finder.trackedPlayer());
			boolean navigable = entry.position() != null;
			boolean hot = ui.hovered(x, y, width, height);
			ui.roundRect(x, y, width, height, tracked ? Theme.SURFACE_ACTIVE : hot && navigable ? Theme.SURFACE_HOVER : Theme.SURFACE);
			if (tracked) {
				ui.rect(x, y + 4, 2, height - 8, Theme.ACCENT);
			}

			String meta = entry.gameMode() + "  ·  " + entry.ping() + " ms";
			ui.textRight(meta, x + width - 6, y + 4, Theme.TEXT_MUTED);
			ui.textClipped(entry.name(), x + 6, y + 4, width - 18 - ui.textWidth(meta), Theme.TEXT);

			String status;
			int color;
			switch (entry.knowledge()) {
				case IN_RANGE -> {
					status = "In range  ·  " + PlayerFinderModule.distanceAndArrow(entry.position());
					color = Theme.SUCCESS;
				}
				case LAST_SEEN -> {
					status = "Last seen " + PlayerFinderModule.ago(entry.seenAt()) + "  ·  " + PlayerFinderModule.distanceAndArrow(entry.position());
					color = Theme.WARNING;
				}
				default -> {
					status = "Position unknown";
					color = Theme.TEXT_MUTED;
				}
			}

			ui.textClipped(status, x + 6, y + 15, width - 12, color);
			if (hot) {
				if (navigable) {
					ui.pointer();
				}

				ui.tooltip(this, switch (entry.knowledge()) {
					case IN_RANGE -> "Within your range: the position is live. Click to " + (tracked ? "stop navigating." : "navigate to them.");
					case LAST_SEEN -> "Out of range now. The position is where you last saw them " + PlayerFinderModule.ago(entry.seenAt())
							+ " and may be out of date. Click to navigate there.";
					case UNKNOWN -> "Online, but this player has not been within your range in this world, so their position is unknown.";
				});
			}
		}

		@Override
		public boolean mouseClicked(UiContext ui, double mouseX, double mouseY, int button) {
			if (button != 0 || !contains(mouseX, mouseY)) {
				return false;
			}

			PlayerFinderModule.Entry entry = entry(id);
			if (entry != null && entry.position() != null) {
				if (id.equals(finder.trackedPlayer())) {
					finder.stopNavigation();
				} else {
					finder.navigateTo(id);
				}
			}

			return true;
		}
	}

	private final class WaypointRow extends Widget {
		private final WaypointsModule.Waypoint waypoint;

		WaypointRow(WaypointsModule.Waypoint waypoint) {
			this.waypoint = waypoint;
		}

		@Override
		public int preferredHeight(UiContext ui, int width) {
			return 16;
		}

		@Override
		public void render(UiContext ui) {
			boolean tracked = Objects.equals(waypoint, finder.trackedWaypoint());
			boolean hot = ui.hovered(x, y, width, height);
			ui.roundRect(x, y, width, height, tracked ? Theme.SURFACE_ACTIVE : hot ? Theme.SURFACE_HOVER : Theme.SURFACE);
			if (tracked) {
				ui.rect(x, y + 3, 2, height - 6, Theme.ACCENT);
			}

			String where = waypoint.x() + ", " + waypoint.y() + ", " + waypoint.z();
			ui.textRight(where, x + width - 6, y + 4, Theme.TEXT_MUTED);
			ui.textClipped(waypoint.name(), x + 6, y + 4, width - 18 - ui.textWidth(where), Theme.TEXT);
			if (hot) {
				ui.pointer();
				ui.tooltip(this, tracked ? "Click to stop navigating." : "Click to navigate to this waypoint.");
			}
		}

		@Override
		public boolean mouseClicked(UiContext ui, double mouseX, double mouseY, int button) {
			if (button != 0 || !contains(mouseX, mouseY)) {
				return false;
			}

			if (Objects.equals(waypoint, finder.trackedWaypoint())) {
				finder.stopNavigation();
			} else {
				finder.navigateTo(waypoint);
			}

			return true;
		}
	}

	private static final class Heading extends Widget {
		private final String text;

		Heading(String text) {
			this.text = text;
		}

		@Override
		public int preferredHeight(UiContext ui, int width) {
			return 14;
		}

		@Override
		public void render(UiContext ui) {
			ui.text(text, x + 2, y + 4, Theme.ACCENT_STRONG);
		}
	}

	private static final class Note extends Widget {
		private final String text;

		Note(String text) {
			this.text = text;
		}

		@Override
		public int preferredHeight(UiContext ui, int width) {
			return (ui == null ? 10 : ui.wrappedHeight(text, width - 12)) + 10;
		}

		@Override
		public void render(UiContext ui) {
			ui.wrapped(text, x + 6, y + 5, width - 12, Theme.TEXT_MUTED);
		}
	}
}
