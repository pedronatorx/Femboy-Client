package io.github.theamethyx.femboyclient.client.modules;

import java.util.List;
import java.util.Locale;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;

import io.github.theamethyx.femboyclient.client.FemboyClient;
import io.github.theamethyx.femboyclient.client.combat.CombatTracker;
import io.github.theamethyx.femboyclient.client.gui.Theme;
import io.github.theamethyx.femboyclient.client.hud.HudAnchor;
import io.github.theamethyx.femboyclient.client.hud.HudLine;
import io.github.theamethyx.femboyclient.client.module.ClientHooks;
import io.github.theamethyx.femboyclient.core.module.Module;
import io.github.theamethyx.femboyclient.core.module.ModuleCategory;
import io.github.theamethyx.femboyclient.core.setting.BooleanSetting;
import io.github.theamethyx.femboyclient.core.setting.EnumSetting;

/**
 * Statistics of this session, counted from when the module is turned on (or the world is joined): play time, distance
 * moved, deaths, and hits landed with the best combo from the combat tracker. Jumps over 10 blocks in one tick
 * (teleports, respawns) are not counted as distance.
 */
public final class SessionStatsModule extends Module implements ClientHooks.HudBlock {
	private final EnumSetting<HudAnchor> anchor = settings().add(new EnumSetting<>("anchor", "Position", "Screen corner for this block.", HudAnchor.TOP_LEFT));
	private final BooleanSetting showCombat = settings().add(new BooleanSetting("combat", "Combat statistics",
			"Show hits landed, accuracy and the best combo (counted in this world).", true));

	private long startedAt;
	private double distance;
	private Vec3 last;
	private int deaths;
	private boolean dead;

	public SessionStatsModule() {
		super("session_stats", "Session Stats", "Play time, distance moved, deaths and combat statistics of this session.",
				ModuleCategory.HUD, false, NO_KEY);
	}

	@Override
	public Tier tier() {
		return Tier.ASSIST;
	}

	@Override
	public List<String> aliases() {
		return List.of("statistics", "play time", "distance");
	}

	@Override
	protected void onEnable() {
		startedAt = System.currentTimeMillis();
		distance = 0;
		deaths = 0;
		last = null;
		dead = false;
	}

	@Override
	protected void onWorldChange() {
		// Another dimension: positions are not comparable, so the next tick starts a new segment.
		last = null;
	}

	@Override
	protected void onTick() {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null) {
			return;
		}

		Vec3 now = player.position();
		if (last != null) {
			double moved = now.distanceTo(last);
			if (moved < 10) {
				distance += moved;
			}
		}

		last = now;
		boolean deadNow = player.isDeadOrDying();
		if (deadNow && !dead) {
			deaths++;
		}

		dead = deadNow;
	}

	@Override
	public HudAnchor hudAnchor() {
		return anchor.get();
	}

	@Override
	public void buildHud(List<HudLine> lines) {
		long seconds = (System.currentTimeMillis() - startedAt) / 1000;
		lines.add(HudLine.of("Session", seconds >= 3600 ? String.format(Locale.ROOT, "%d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60)
				: String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60)));
		lines.add(HudLine.of("Distance", distance >= 1000 ? String.format(Locale.ROOT, "%.2f km", distance / 1000) : String.format(Locale.ROOT, "%.0f m", distance)));
		lines.add(HudLine.of("Deaths", String.valueOf(deaths), deaths > 0 ? Theme.WARNING : Theme.TEXT));
		if (showCombat.get()) {
			CombatTracker combat = FemboyClient.get().combat();
			if (combat.attacks() > 0) {
				lines.add(HudLine.of("Hits", String.format(Locale.ROOT, "%d / %d (%.0f%%)", combat.landed(), combat.attacks(), 100.0 * combat.landed() / combat.attacks())));
				lines.add(HudLine.of("Best combo", String.valueOf(combat.bestCombo())));
			}
		}
	}

	/** Distance moved this session in blocks (for tests). */
	public double distance() {
		return distance;
	}
}
