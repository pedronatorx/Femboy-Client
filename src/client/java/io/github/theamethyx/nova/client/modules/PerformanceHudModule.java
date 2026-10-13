package io.github.theamethyx.nova.client.modules;

import java.util.List;
import java.util.Locale;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.server.IntegratedServer;

import io.github.theamethyx.nova.client.NovaClient;
import io.github.theamethyx.nova.client.gui.Theme;
import io.github.theamethyx.nova.client.hud.HudAnchor;
import io.github.theamethyx.nova.client.hud.HudLine;
import io.github.theamethyx.nova.client.module.ClientHooks;
import io.github.theamethyx.nova.core.diag.PerformanceMonitor;
import io.github.theamethyx.nova.core.module.Module;
import io.github.theamethyx.nova.core.module.ModuleCategory;
import io.github.theamethyx.nova.core.setting.BooleanSetting;
import io.github.theamethyx.nova.core.setting.EnumSetting;

/** FPS, frame time, ping, memory and (in singleplayer) server tick time. */
public final class PerformanceHudModule extends Module implements ClientHooks.HudBlock {
	private final EnumSetting<HudAnchor> anchor = settings().add(new EnumSetting<>("anchor", "Position",
			"Screen corner for this block.", HudAnchor.TOP_LEFT));
	private final BooleanSetting frameTime = settings().add(new BooleanSetting("frame_time", "Show frame time",
			"Average and slowest frame time over the last few seconds.", true));
	private final BooleanSetting ping = settings().add(new BooleanSetting("ping", "Show ping",
			"Round-trip latency reported by the server for your connection (multiplayer only).", true));
	private final BooleanSetting memory = settings().add(new BooleanSetting("memory", "Show memory",
			"Java heap memory in use and the maximum the game may use.", false));
	private final BooleanSetting serverTick = settings().add(new BooleanSetting("server_tick", "Show server tick time",
			"Singleplayer only: how long the built-in server needs per tick. Above 50 ms the world slows down.", true));
	private final BooleanSetting colors = settings().add(new BooleanSetting("colors", "Color-code values",
			"Green, amber or red depending on how good the value is.", true));

	public PerformanceHudModule() {
		super("performance_hud", "Performance", "Shows FPS, frame time, ping, memory and singleplayer server tick time.",
				ModuleCategory.HUD, true, NO_KEY);
	}

	@Override
	public HudAnchor hudAnchor() {
		return anchor.get();
	}

	private int grade(double value, double good, double bad, boolean higherIsBetter) {
		if (!colors.get()) {
			return Theme.TEXT;
		}

		boolean isGood = higherIsBetter ? value >= good : value <= good;
		boolean isBad = higherIsBetter ? value < bad : value > bad;
		return isGood ? Theme.SUCCESS : isBad ? Theme.ERROR : Theme.WARNING;
	}

	@Override
	public void buildHud(List<HudLine> lines) {
		Minecraft client = Minecraft.getInstance();
		int fps = client.getFps();
		lines.add(HudLine.of("FPS", String.valueOf(fps), grade(fps, 60, 30, true)));

		if (frameTime.get()) {
			PerformanceMonitor.FrameStats stats = NovaClient.get().performance().frameStats();
			if (stats.samples() > 0) {
				lines.add(HudLine.of("Frame", String.format(Locale.ROOT, "%.1f ms (max %.0f)", stats.averageFrameMs(), stats.worstFrameMs()),
						grade(stats.averageFrameMs(), 16.7, 33.4, false)));
			}
		}

		if (ping.get() && !client.hasSingleplayerServer()) {
			ClientPacketListener connection = client.getConnection();
			PlayerInfo info = connection == null || client.player == null ? null : connection.getPlayerInfo(client.player.getUUID());
			if (info != null) {
				lines.add(HudLine.of("Ping", info.getLatency() + " ms", grade(info.getLatency(), 80, 200, false)));
			}
		}

		if (serverTick.get()) {
			IntegratedServer server = client.getSingleplayerServer();
			if (server != null) {
				double ms = server.getCurrentSmoothedTickTime();
				lines.add(HudLine.of("Server tick", String.format(Locale.ROOT, "%.1f ms", ms), grade(ms, 25, 50, false)));
			}
		}

		if (memory.get()) {
			Runtime runtime = Runtime.getRuntime();
			long used = (runtime.totalMemory() - runtime.freeMemory()) >> 20;
			long max = runtime.maxMemory() >> 20;
			lines.add(HudLine.of("Memory", used + " / " + max + " MB", grade(used / (double) max, 0.7, 0.9, false)));
		}
	}
}
