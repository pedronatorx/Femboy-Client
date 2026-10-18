package io.github.theamethyx.femboyclient.client.modules;

import java.util.List;
import java.util.Locale;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

import io.github.theamethyx.femboyclient.client.gui.Theme;
import io.github.theamethyx.femboyclient.client.hud.HudAnchor;
import io.github.theamethyx.femboyclient.client.hud.HudLine;
import io.github.theamethyx.femboyclient.client.module.ClientHooks;
import io.github.theamethyx.femboyclient.core.module.Module;
import io.github.theamethyx.femboyclient.core.module.ModuleCategory;
import io.github.theamethyx.femboyclient.core.setting.EnumSetting;
import io.github.theamethyx.femboyclient.core.setting.NumberSetting;
import io.github.theamethyx.femboyclient.core.tools.Experience;

/** Experience calculator: your level, the points to the next level and to a target level, with Minecraft's exact formulas. */
public final class ExperienceInfoModule extends Module implements ClientHooks.HudBlock {
	private final EnumSetting<HudAnchor> anchor = settings().add(new EnumSetting<>("anchor", "Position", "Screen corner for this block.", HudAnchor.BOTTOM_RIGHT));
	private final NumberSetting target = settings().add(new NumberSetting("target", "Target level",
			"Show the points still needed for this level (30 is the most an enchanting table uses).", 30, 1, 100, 1, ""));

	public ExperienceInfoModule() {
		super("xp_info", "Experience", "Your level and exactly how many experience points the next level and a target level still need.",
				ModuleCategory.HUD, false, NO_KEY);
	}

	@Override
	public Tier tier() {
		return Tier.ASSIST;
	}

	@Override
	public List<String> aliases() {
		return List.of("xp calculator", "experience calculator", "levels");
	}

	@Override
	public HudAnchor hudAnchor() {
		return anchor.get();
	}

	@Override
	public void buildHud(List<HudLine> lines) {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null) {
			return;
		}

		int level = player.experienceLevel;
		float progress = player.experienceProgress;
		int next = Experience.pointsForNextLevel(level);
		lines.add(HudLine.of("Level", String.format(Locale.ROOT, "%d (%.0f%%)", level, progress * 100)));
		lines.add(HudLine.of("Next level", String.format(Locale.ROOT, "%d points", Math.max(0, Math.round(next * (1 - progress))))));
		int goal = target.intValue();
		if (goal > level) {
			lines.add(HudLine.of("Level " + goal, String.format(Locale.ROOT, "%,d points", Experience.pointsToReach(level, progress, goal)), Theme.ACCENT_STRONG));
		}

		lines.add(HudLine.of("Total", String.format(Locale.ROOT, "%,d points", Experience.totalPoints(level, progress)), Theme.TEXT_SECONDARY));
	}
}
