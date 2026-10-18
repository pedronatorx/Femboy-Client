package io.github.theamethyx.femboyclient.client.modules;

import java.util.List;
import java.util.Locale;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

import io.github.theamethyx.femboyclient.client.gui.Theme;
import io.github.theamethyx.femboyclient.client.gui.UiContext;
import io.github.theamethyx.femboyclient.client.hud.HudAnchor;
import io.github.theamethyx.femboyclient.client.module.ClientHooks;
import io.github.theamethyx.femboyclient.core.module.Module;
import io.github.theamethyx.femboyclient.core.module.ModuleCategory;
import io.github.theamethyx.femboyclient.core.setting.BooleanSetting;
import io.github.theamethyx.femboyclient.core.setting.EnumSetting;
import io.github.theamethyx.femboyclient.core.setting.NumberSetting;

/**
 * A compass strip: the directions around where you look, with tick marks every 15 degrees and your exact heading. Place it
 * anywhere with the HUD editor (top center works well).
 */
public final class CompassModule extends Module implements ClientHooks.HudWidget {
	private static final String[] POINTS = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};

	private final EnumSetting<HudAnchor> anchor = settings().add(new EnumSetting<>("anchor", "Position",
			"Screen corner when not placed with the HUD editor.", HudAnchor.TOP_LEFT));
	private final NumberSetting width = settings().add(new NumberSetting("width", "Width", "Width of the strip.", 140, 80, 260, 10, "px"));
	private final NumberSetting view = settings().add(new NumberSetting("view", "Field of view",
			"How many degrees the strip spans.", 120, 60, 240, 10, "°"));
	private final BooleanSetting degrees = settings().add(new BooleanSetting("degrees", "Show heading",
			"Show the exact heading in degrees (0 = north, 90 = east).", true));

	public CompassModule() {
		super("compass", "Compass", "A compass strip showing the directions around where you look and your heading in degrees.",
				ModuleCategory.HUD, false, NO_KEY);
	}

	@Override
	public List<String> aliases() {
		return List.of("direction", "heading", "bearing");
	}

	@Override
	public HudAnchor hudAnchor() {
		return anchor.get();
	}

	@Override
	public int hudWidth(UiContext ui) {
		return width.intValue();
	}

	@Override
	public int hudHeight(UiContext ui) {
		return degrees.get() ? 24 : 14;
	}

	/** Compass bearing of a Minecraft yaw: 0 north, 90 east, 180 south, 270 west. */
	public static double bearing(float yaw) {
		return ((yaw + 180) % 360 + 360) % 360;
	}

	@Override
	public void drawHud(UiContext ui, int x, int y) {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null) {
			return;
		}

		int w = width.intValue();
		double heading = bearing(player.getYRot());
		double span = view.get();
		double scale = w / span;
		ui.roundRect(x, y, w, 14, Theme.withAlpha(Theme.SURFACE, Theme.HUD_BACKGROUND), 3);
		ui.pushClip(x, y, w, 14);
		int start = (int) Math.floor((heading - span / 2) / 15) * 15;
		for (int angle = start; angle <= heading + span / 2 + 15; angle += 15) {
			int px = x + w / 2 + (int) Math.round((angle - heading) * scale);
			int normalized = ((angle % 360) + 360) % 360;
			if (normalized % 45 == 0) {
				String label = POINTS[normalized / 45];
				int color = normalized == 0 ? Theme.ACCENT_2 : normalized % 90 == 0 ? Theme.TEXT : Theme.TEXT_SECONDARY;
				ui.textCentered(label, px, y + 3, color);
			} else {
				ui.rect(px, y + 9, 1, 3, Theme.TEXT_MUTED);
			}
		}

		ui.popClip();
		ui.rect(x + w / 2, y, 1, 2, Theme.ACCENT);
		ui.rect(x + w / 2, y + 12, 1, 2, Theme.ACCENT);
		if (degrees.get()) {
			ui.textCentered(String.format(Locale.ROOT, "%.0f°", heading), x + w / 2, y + 15, Theme.ACCENT_STRONG);
		}
	}
}
