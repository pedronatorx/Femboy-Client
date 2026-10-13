package io.github.theamethyx.nova.client.modules;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import io.github.theamethyx.nova.client.gui.Icon;
import io.github.theamethyx.nova.client.gui.Theme;
import io.github.theamethyx.nova.client.gui.UiContext;
import io.github.theamethyx.nova.client.gui.widget.Button;
import io.github.theamethyx.nova.client.gui.widget.Container;
import io.github.theamethyx.nova.client.gui.widget.TextField;
import io.github.theamethyx.nova.client.gui.widget.Widget;
import io.github.theamethyx.nova.client.module.ClientHooks;
import io.github.theamethyx.nova.client.render.OverlayColor;
import io.github.theamethyx.nova.client.render.RenderingUtilities;
import io.github.theamethyx.nova.client.render.ScreenProjection;
import io.github.theamethyx.nova.client.world.WorldIdentity;
import io.github.theamethyx.nova.core.module.Module;
import io.github.theamethyx.nova.core.module.ModuleCategory;
import io.github.theamethyx.nova.core.setting.BooleanSetting;
import io.github.theamethyx.nova.core.setting.EnumSetting;
import io.github.theamethyx.nova.core.setting.NumberSetting;

/**
 * Saved positions you placed yourself, shown as markers with labels. Waypoints are stored per world
 * and dimension in the active profile.
 */
public final class WaypointsModule extends Module implements ClientHooks.WorldRender, ClientHooks.ScreenOverlay, ClientHooks.CustomControls {
	public static final int MAX_WAYPOINTS = 200;
	private static final Pattern NAME = Pattern.compile("[^\\p{Cntrl}]{1,32}");

	public record Waypoint(String name, int x, int y, int z, String dimension, String world) {
		Vec3 center() {
			return new Vec3(x + 0.5, y + 0.5, z + 0.5);
		}
	}

	private final List<Waypoint> waypoints = new ArrayList<>();

	private final EnumSetting<OverlayColor> color = settings().add(new EnumSetting<>("color", "Color", "Marker and label color.", OverlayColor.CYAN));
	private final BooleanSetting throughWalls = settings().add(new BooleanSetting("through_walls", "Show through walls",
			"Draw markers even when blocks are in the way. Your own saved positions only.", true));
	private final BooleanSetting beam = settings().add(new BooleanSetting("beam", "Show beam", "Draw a vertical line above each waypoint.", true));
	private final BooleanSetting labels = settings().add(new BooleanSetting("labels", "Show labels", "Show the name above each waypoint.", true));
	private final BooleanSetting distance = settings().add(new BooleanSetting("distance", "Show distance",
			"Add the distance in blocks to the label.", true)).visibleWhen(this::labelsOn);
	private final NumberSetting maxDistance = settings().add(new NumberSetting("max_distance", "Maximum distance",
			"Hide waypoints farther away than this.", 1000, 16, 10000, 16, "blocks"));

	public WaypointsModule() {
		super("waypoints", "Waypoints", "Save positions and see them as markers with names and distances.",
				ModuleCategory.VISUAL, false, NO_KEY);
	}

	private boolean labelsOn() {
		return labels.get();
	}

	// ----- data ------------------------------------------------------------------------------

	public List<Waypoint> all() {
		return List.copyOf(waypoints);
	}

	/** Waypoints of the world and dimension the player is in. */
	public List<Waypoint> visibleHere() {
		String world = WorldIdentity.current();
		String dimension = WorldIdentity.dimension();
		List<Waypoint> result = new ArrayList<>();
		for (Waypoint waypoint : waypoints) {
			if (waypoint.world().equals(world) && waypoint.dimension().equals(dimension)) {
				result.add(waypoint);
			}
		}

		return result;
	}

	/** @return {@code null} on success, otherwise a readable reason */
	public String addAtPlayer(String rawName) {
		LocalPlayer player = Minecraft.getInstance().player;
		String world = WorldIdentity.current();
		if (player == null || world == null) {
			return "Load a world first.";
		}

		String name = rawName.trim();
		if (!NAME.matcher(name).matches()) {
			return "Names need 1-32 visible characters.";
		}

		if (waypoints.size() >= MAX_WAYPOINTS) {
			return "You can store at most " + MAX_WAYPOINTS + " waypoints.";
		}

		waypoints.add(new Waypoint(name, player.getBlockX(), player.getBlockY(), player.getBlockZ(), WorldIdentity.dimension(), world));
		settingsChanged();
		return null;
	}

	public void remove(Waypoint waypoint) {
		if (waypoints.remove(waypoint)) {
			settingsChanged();
		}
	}

	/** Waypoints are not settings, so tell the config system explicitly that something changed. */
	private void settingsChanged() {
		io.github.theamethyx.nova.client.NovaClient nova = io.github.theamethyx.nova.client.NovaClient.get();
		if (nova != null && nova.config() != null) {
			nova.config().markDirty();
		}
	}

	@Override
	protected void saveExtra(JsonObject json) {
		JsonArray array = new JsonArray();
		for (Waypoint waypoint : waypoints) {
			JsonObject entry = new JsonObject();
			entry.addProperty("name", waypoint.name());
			entry.addProperty("x", waypoint.x());
			entry.addProperty("y", waypoint.y());
			entry.addProperty("z", waypoint.z());
			entry.addProperty("dimension", waypoint.dimension());
			entry.addProperty("world", waypoint.world());
			array.add(entry);
		}

		json.add("waypoints", array);
	}

	@Override
	protected void loadExtra(JsonObject json, List<String> warnings) {
		if (!(json.get("waypoints") instanceof JsonArray array)) {
			return;
		}

		int skipped = 0;
		for (JsonElement element : array) {
			try {
				JsonObject entry = element.getAsJsonObject();
				String name = entry.get("name").getAsString();
				if (!NAME.matcher(name).matches() || waypoints.size() >= MAX_WAYPOINTS) {
					skipped++;
					continue;
				}

				waypoints.add(new Waypoint(name, entry.get("x").getAsInt(), entry.get("y").getAsInt(), entry.get("z").getAsInt(),
						entry.get("dimension").getAsString(), entry.get("world").getAsString()));
			} catch (RuntimeException e) {
				skipped++;
			}
		}

		if (skipped > 0) {
			warnings.add(skipped + " waypoint(s) were unreadable and were skipped.");
		}
	}

	@Override
	protected void resetExtra() {
		waypoints.clear();
	}

	// ----- rendering -------------------------------------------------------------------------

	private boolean inRange(Waypoint waypoint, Vec3 from) {
		return waypoint.center().distanceTo(from) <= maxDistance.get();
	}

	@Override
	public void renderWorld(RenderingUtilities render) {
		Vec3 camera = render.camera();
		int argb = color.get().argb;
		for (Waypoint waypoint : visibleHere()) {
			if (!inRange(waypoint, camera)) {
				continue;
			}

			AABB box = new AABB(waypoint.x(), waypoint.y(), waypoint.z(), waypoint.x() + 1, waypoint.y() + 1, waypoint.z() + 1);
			render.filledBox(box.deflate(0.2), color.get().withAlpha(0.25f), throughWalls.get());
			render.boxOutline(box.deflate(0.2), argb, 1.5f, throughWalls.get());
			if (beam.get()) {
				Vec3 base = new Vec3(waypoint.x() + 0.5, waypoint.y() + 1, waypoint.z() + 0.5);
				render.line(base, base.add(0, 48, 0), color.get().withAlpha(0.5f), 1f, throughWalls.get());
			}
		}
	}

	@Override
	public void renderOverlay(UiContext ui, int screenWidth, int screenHeight, float partialTick) {
		if (!labels.get()) {
			return;
		}

		Vec3 camera = Minecraft.getInstance().gameRenderer.getMainCamera().position();
		for (Waypoint waypoint : visibleHere()) {
			if (!inRange(waypoint, camera)) {
				continue;
			}

			int[] at = ScreenProjection.toScreen(waypoint.center().add(0, 0.9, 0), screenWidth, screenHeight);
			if (at == null) {
				continue;
			}

			String text = waypoint.name() + (distance.get() ? String.format(Locale.ROOT, "  %.0fm", waypoint.center().distanceTo(camera)) : "");
			ui.worldLabel(text, at[0], at[1] - 1, color.get().argb);
		}
	}

	// ----- GUI controls ----------------------------------------------------------------------

	@Override
	public List<Widget> createControls(UiContext ui, Runnable refresh) {
		List<Widget> widgets = new ArrayList<>();
		widgets.add(new AddRow(refresh));
		List<Waypoint> here = visibleHere();
		if (WorldIdentity.current() == null) {
			widgets.add(new Note("Load a world to add waypoints. Saved waypoints for other worlds are kept."));
		} else if (here.isEmpty()) {
			widgets.add(new Note("No waypoints in this world and dimension yet (" + waypoints.size() + " stored in total)."));
		}

		for (Waypoint waypoint : here) {
			widgets.add(new WaypointRow(waypoint, refresh));
		}

		return widgets;
	}

	private final class AddRow extends Container {
		private final TextField name = add(new TextField("Waypoint name", 32));
		private final Button addButton;
		private String error;

		AddRow(Runnable refresh) {
			Runnable submit = () -> {
				error = addAtPlayer(name.text());
				if (error == null) {
					name.setText("");
					refresh.run();
				}
			};
			name.onSubmit(text -> submit.run());
			addButton = add(new Button("Add here", Button.Style.PRIMARY, submit).icon(Icon.PLUS));
			addButton.enabledWhen(() -> !name.text().isBlank() && WorldIdentity.current() != null);
			addButton.tooltip("Save your current block position under this name.");
		}

		@Override
		public int preferredHeight(UiContext ui, int width) {
			return error == null ? 15 : 27;
		}

		@Override
		protected void onLayout() {
			name.setBounds(x, y, width - 66, 15);
			addButton.setBounds(x + width - 62, y, 62, 15);
		}

		@Override
		public void render(UiContext ui) {
			super.render(ui);
			if (error != null) {
				ui.textClipped(error, x + 2, y + 18, width - 4, Theme.ERROR);
			}
		}
	}

	private final class WaypointRow extends Container {
		private final Waypoint waypoint;
		private final Button delete;

		WaypointRow(Waypoint waypoint, Runnable refresh) {
			this.waypoint = waypoint;
			delete = add(new Button("", Button.Style.DANGER, () -> {
				remove(waypoint);
				refresh.run();
			}).icon(Icon.TRASH));
			delete.tooltip("Delete this waypoint.");
		}

		@Override
		public int preferredHeight(UiContext ui, int width) {
			return 20;
		}

		@Override
		protected void onLayout() {
			delete.setBounds(x + width - 21, y + 3, 18, 14);
		}

		@Override
		public void render(UiContext ui) {
			ui.roundRect(x, y, width, height, Theme.SURFACE);
			ui.rect(x, y + 3, 2, height - 6, color.get().argb);
			ui.textClipped(waypoint.name(), x + 7, y + 6, width / 2 - 10, Theme.TEXT);
			ui.textRight(waypoint.x() + ", " + waypoint.y() + ", " + waypoint.z(), x + width - 26, y + 6, Theme.TEXT_SECONDARY);
			super.render(ui);
		}
	}

	private static final class Note extends Widget {
		private final String text;

		Note(String text) {
			this.text = text;
		}

		@Override
		public int preferredHeight(UiContext ui, int width) {
			return ui.wrappedHeight(text, width - 8) + 4;
		}

		@Override
		public void render(UiContext ui) {
			ui.wrapped(text, x + 4, y + 2, width - 8, Theme.TEXT_MUTED);
		}
	}
}
