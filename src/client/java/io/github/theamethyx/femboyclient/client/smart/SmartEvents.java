package io.github.theamethyx.femboyclient.client.smart;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;

import io.github.theamethyx.femboyclient.client.FemboyClient;
import io.github.theamethyx.femboyclient.client.gui.Theme;
import io.github.theamethyx.femboyclient.client.hud.HudManager;
import io.github.theamethyx.femboyclient.client.hud.Notifications;
import io.github.theamethyx.femboyclient.core.diag.PerformanceMonitor;
import io.github.theamethyx.femboyclient.core.module.Module;
import io.github.theamethyx.femboyclient.core.rules.Rule;

/**
 * Notices what happens in the client once per tick (profile, world, dimension, GUI scale, frame rate, combat, held item,
 * deaths, module switches) and passes it on: to the rules as triggers and to the session timeline as events. It also
 * warns, once per module and session, when a module's ticks take more than {@link #HEAVY_TICK_MICROS} on average.
 * Everything here compares a few values per tick; nothing is scanned.
 */
public final class SmartEvents {
	/** Average tick cost above which a module gets one performance warning per session. */
	public static final double HEAVY_TICK_MICROS = 2000;
	private static final long LOW_FPS_MILLIS = 5000;

	private final FemboyClient femboy;
	private String profile;
	private ClientLevel level;
	private String dimension;
	private int guiScale = -1;
	private int windowWidth;
	private int windowHeight;
	private long lowFpsSince;
	private boolean lowFpsFired;
	private boolean inCombat;
	private String holding;
	private boolean dead;
	private int tick;
	private final Map<Module, Boolean> enabled = new HashMap<>();
	private final Set<String> warned = new HashSet<>();

	public SmartEvents(FemboyClient femboy) {
		this.femboy = femboy;
		femboy.modules().addListener(this::moduleChanged);
		femboy.modules().addFailureListener((module, phase, reason, turnedOff) -> femboy.timeline().record(SessionTimeline.Kind.ERROR,
				module.name() + (turnedOff ? " turned off" : " paused"), reason));
		femboy.modules().addConflictListener((on, off) -> femboy.timeline().record(SessionTimeline.Kind.MODULE, off.name() + " turned off",
				"conflicts with " + on.name()));
	}

	private void moduleChanged(Module module) {
		Boolean before = enabled.put(module, module.isEnabled());
		if (before != null && before != module.isEnabled()) {
			femboy.quickAccess().touch(module);
			femboy.timeline().record(SessionTimeline.Kind.MODULE, module.name() + (module.isEnabled() ? " on" : " off"), "");
		}
	}

	/** Call once per client tick, after modules ticked. */
	public void tick(Minecraft client) {
		RuleRunner rules = femboy.rules();
		tick++;

		String currentProfile = femboy.config().activeProfile();
		if (profile != null && !profile.equals(currentProfile)) {
			femboy.timeline().record(SessionTimeline.Kind.PROFILE, "Profile " + currentProfile, "from " + profile);
			profile = currentProfile;
			rules.fire(Rule.Trigger.PROFILE_CHANGED);
		}

		profile = currentProfile;

		if (client.level != level) {
			ClientLevel previous = level;
			level = client.level;
			if (level != null) {
				String newDimension = rules.dimension();
				if (previous == null) {
					femboy.timeline().record(SessionTimeline.Kind.WORLD, "Joined a world", RuleRunner.environmentOf(femboy) + ", " + pretty(newDimension));
					dimension = newDimension;
					rules.fire(Rule.Trigger.WORLD_JOINED);
				} else if (!newDimension.equals(dimension)) {
					femboy.timeline().record(SessionTimeline.Kind.WORLD, "Entered " + pretty(newDimension), "");
					dimension = newDimension;
					rules.fire(Rule.Trigger.DIMENSION_CHANGED);
				}
			} else if (previous != null) {
				femboy.timeline().record(SessionTimeline.Kind.WORLD, "Left the world", "");
				dimension = null;
			}
		}

		int scale = client.getWindow().getGuiScale();
		int width = client.getWindow().getWidth();
		int height = client.getWindow().getHeight();
		if (guiScale != -1 && (scale != guiScale || width != windowWidth || height != windowHeight)) {
			rules.fire(Rule.Trigger.GUI_SCALE_CHANGED);
		}

		guiScale = scale;
		windowWidth = width;
		windowHeight = height;

		if (client.player == null) {
			return;
		}

		lowFps(client);

		boolean combat = femboy.combat().inCombat(HudManager.COMBAT_WINDOW_MILLIS);
		if (combat != inCombat) {
			inCombat = combat;
			rules.fire(combat ? Rule.Trigger.COMBAT_STARTED : Rule.Trigger.COMBAT_ENDED);
		}

		String held = RuleRunner.holdingGroup(client.player.getMainHandItem());
		if (holding != null && !held.equals(holding)) {
			holding = held;
			rules.fire(Rule.Trigger.HELD_ITEM_CHANGED);
		}

		holding = held;

		boolean deadNow = client.player.isDeadOrDying();
		if (deadNow && !dead) {
			var pos = client.player.blockPosition();
			femboy.timeline().record(SessionTimeline.Kind.DEATH, "Died", String.format(Locale.ROOT, "at %d %d %d in %s", pos.getX(), pos.getY(), pos.getZ(),
					pretty(dimension == null ? "" : dimension)));
		}

		dead = deadNow;

		if (tick % 100 == 0) {
			watchPerformance();
		}
	}

	/** Fires LOW_FPS once when the frame rate stays under the lowest rule limit (30 by default) for 5 seconds. */
	private void lowFps(Minecraft client) {
		int limit = 30;
		for (Rule rule : femboy.rules().engine().rules()) {
			if (rule.enabled() && rule.trigger() == Rule.Trigger.LOW_FPS) {
				for (Rule.Condition condition : rule.conditions()) {
					if (condition.type() == Rule.ConditionType.FPS_BELOW) {
						try {
							limit = Math.max(limit, Integer.parseInt(condition.value()));
						} catch (NumberFormatException ignored) {
							// Reported by the rule validator.
						}
					}
				}
			}
		}

		long now = System.currentTimeMillis();
		if (client.getFps() > 0 && client.getFps() < limit && client.screen == null) {
			if (lowFpsSince == 0) {
				lowFpsSince = now;
			} else if (!lowFpsFired && now - lowFpsSince >= LOW_FPS_MILLIS) {
				lowFpsFired = true;
				femboy.timeline().record(SessionTimeline.Kind.PERFORMANCE, "Low frame rate", client.getFps() + " FPS for 5 s");
				femboy.rules().fire(Rule.Trigger.LOW_FPS);
			}
		} else {
			lowFpsSince = 0;
			lowFpsFired = false;
		}
	}

	private void watchPerformance() {
		PerformanceMonitor performance = femboy.performance();
		for (Module module : femboy.modules().all()) {
			if (!module.isRunning() || warned.contains(module.id())) {
				continue;
			}

			PerformanceMonitor.Timing timing = performance.timing(module.id() + "/tick");
			if (timing != null && timing.calls() > 100 && timing.averageMicros() > HEAVY_TICK_MICROS) {
				warned.add(module.id());
				String text = String.format(Locale.ROOT, "%.1f ms per tick; see Settings, Performance", timing.averageMicros() / 1000);
				Notifications.post(Notifications.Category.PERFORMANCE, module.name() + " is slow", text, Theme.WARNING);
				femboy.timeline().record(SessionTimeline.Kind.PERFORMANCE, module.name() + " is slow", text);
			}
		}
	}

	private static String pretty(String dimension) {
		return switch (dimension) {
			case "overworld" -> "the Overworld";
			case "nether" -> "the Nether";
			case "end" -> "the End";
			default -> dimension;
		};
	}
}
