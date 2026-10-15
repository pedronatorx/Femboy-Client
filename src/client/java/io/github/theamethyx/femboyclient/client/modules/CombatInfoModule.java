package io.github.theamethyx.femboyclient.client.modules;

import java.util.List;
import java.util.Locale;

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
import io.github.theamethyx.femboyclient.core.setting.NumberSetting;

/** Clicks per second, combo, reach and hit accuracy on the HUD. Information only; nothing is sent to the server. */
public final class CombatInfoModule extends Module implements ClientHooks.HudBlock {
	private final EnumSetting<HudAnchor> anchor = settings().add(new EnumSetting<>("anchor", "Position",
			"Screen corner for this block.", HudAnchor.TOP_RIGHT));
	private final BooleanSetting cps = settings().add(new BooleanSetting("cps", "Clicks per second",
			"Left and right mouse clicks during the last second, counted while no menu is open.", true));
	private final BooleanSetting rightCps = settings().add(new BooleanSetting("cps_right", "Include right clicks",
			"Also show right clicks per second.", true)).visibleWhen(cps::get);
	private final BooleanSetting combo = settings().add(new BooleanSetting("combo", "Combo",
			"Hits that landed in a row. Ends when you take damage or after the timeout.", true));
	private final NumberSetting comboTimeout = settings().add(new NumberSetting("combo_timeout", "Combo timeout",
			"A combo ends when no hit lands for this long.", 3, 1, 10, 0.5, "s")).visibleWhen(combo::get);
	private final BooleanSetting reach = settings().add(new BooleanSetting("reach", "Reach",
			"Distance from your eyes to the target's hitbox for your last hit that landed.", true));
	private final BooleanSetting accuracy = settings().add(new BooleanSetting("accuracy", "Accuracy",
			"Hits that landed out of attacks sent this session.", false));

	public CombatInfoModule() {
		super("combat_info", "Combat Info", "Clicks per second, combo, reach and accuracy on the HUD.", ModuleCategory.COMBAT, false, NO_KEY);
	}

	public long comboTimeoutMillis() {
		return Math.round(comboTimeout.get() * 1000);
	}

	@Override
	public HudAnchor hudAnchor() {
		return anchor.get();
	}

	@Override
	protected void onEnable() {
		FemboyClient.get().combat().reset();
	}

	@Override
	public void buildHud(List<HudLine> lines) {
		CombatTracker tracker = FemboyClient.get().combat();
		if (cps.get()) {
			lines.add(HudLine.of("CPS", rightCps.get() ? tracker.leftCps() + " | " + tracker.rightCps() : String.valueOf(tracker.leftCps())));
		}

		if (combo.get()) {
			int current = tracker.combo();
			lines.add(HudLine.of("Combo", current + "  (best " + tracker.bestCombo() + ")", current >= 3 ? Theme.ACCENT_STRONG : Theme.TEXT));
		}

		if (reach.get()) {
			double last = tracker.lastReach();
			lines.add(HudLine.of("Reach", Double.isNaN(last) ? "-" : String.format(Locale.ROOT, "%.2f m", last)));
		}

		if (accuracy.get()) {
			int attacks = tracker.attacks();
			lines.add(HudLine.of("Accuracy", attacks == 0 ? "-"
					: String.format(Locale.ROOT, "%d%%  (%d/%d)", Math.round(100.0 * tracker.landed() / attacks), tracker.landed(), attacks)));
		}
	}
}
