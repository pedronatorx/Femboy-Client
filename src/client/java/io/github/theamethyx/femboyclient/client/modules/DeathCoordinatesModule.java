package io.github.theamethyx.femboyclient.client.modules;

import java.util.List;
import java.util.Locale;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;

import io.github.theamethyx.femboyclient.client.FemboyClient;
import io.github.theamethyx.femboyclient.client.gui.Theme;
import io.github.theamethyx.femboyclient.client.hud.HudAnchor;
import io.github.theamethyx.femboyclient.client.hud.HudLine;
import io.github.theamethyx.femboyclient.client.module.ClientHooks;
import io.github.theamethyx.femboyclient.client.world.WorldIdentity;
import io.github.theamethyx.femboyclient.core.module.Module;
import io.github.theamethyx.femboyclient.core.module.ModuleCategory;
import io.github.theamethyx.femboyclient.core.setting.BooleanSetting;

/** Remembers where you died: a chat line only you see, a HUD line, and optionally a waypoint. */
public final class DeathCoordinatesModule extends Module implements ClientHooks.HudBlock {
	private final BooleanSetting chat = settings().add(new BooleanSetting("chat", "Chat message", "Print the coordinates in your chat (only you see it).", true));
	private final BooleanSetting waypoint = settings().add(new BooleanSetting("waypoint", "Add a waypoint",
			"Save a waypoint named after the time of death (needs the Waypoints module for markers).", true));
	private final BooleanSetting hud = settings().add(new BooleanSetting("hud", "Show on HUD", "Show your last death position.", true));

	public record Death(int x, int y, int z, String dimension, long at) {
	}

	private boolean wasDead;
	private Death last;

	public DeathCoordinatesModule() {
		super("death_coordinates", "Death Coordinates", "Remembers where you died: chat line, HUD line and an optional waypoint.",
				ModuleCategory.WORLD, false, NO_KEY);
	}

	public Death last() {
		return last;
	}

	@Override
	protected void onTick() {
		LocalPlayer player = Minecraft.getInstance().player;
		boolean dead = player.isDeadOrDying();
		if (dead && !wasDead) {
			record(player);
		}

		wasDead = dead;
	}

	private void record(LocalPlayer player) {
		last = new Death(player.getBlockX(), player.getBlockY(), player.getBlockZ(), WorldIdentity.dimension(), System.currentTimeMillis());
		String text = String.format(Locale.ROOT, "You died at %d %d %d (%s)", last.x(), last.y(), last.z(), last.dimension());
		if (chat.get()) {
			player.displayClientMessage(Component.literal(text), false);
		}

		if (waypoint.get()) {
			WaypointsModule waypoints = FemboyClient.get().modules().get(WaypointsModule.class);
			if (waypoints != null) {
				waypoints.addAtPlayer("Death " + java.time.LocalTime.now().withNano(0));
			}
		}
	}

	@Override
	public HudAnchor hudAnchor() {
		return HudAnchor.TOP_LEFT;
	}

	@Override
	public void buildHud(List<HudLine> lines) {
		if (hud.get() && last != null) {
			long minutes = (System.currentTimeMillis() - last.at()) / 60000;
			lines.add(HudLine.of("Last death", String.format(Locale.ROOT, "%d %d %d  %s, %d min ago", last.x(), last.y(), last.z(),
					last.dimension().replace("minecraft:", ""), minutes), Theme.WARNING));
		}
	}
}
