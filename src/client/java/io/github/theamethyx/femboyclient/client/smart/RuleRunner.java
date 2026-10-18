package io.github.theamethyx.femboyclient.client.smart;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.google.gson.JsonElement;

import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import io.github.theamethyx.femboyclient.client.FemboyClient;
import io.github.theamethyx.femboyclient.client.GlobalOptions;
import io.github.theamethyx.femboyclient.client.gui.Theme;
import io.github.theamethyx.femboyclient.client.hud.Notifications;
import io.github.theamethyx.femboyclient.client.profile.AutoProfileSwitcher;
import io.github.theamethyx.femboyclient.core.config.ProfileRule;
import io.github.theamethyx.femboyclient.core.module.Module;
import io.github.theamethyx.femboyclient.core.rules.Rule;
import io.github.theamethyx.femboyclient.core.rules.RuleEngine;

/**
 * Connects the {@link RuleEngine} to the client: what the conditions see, what the actions do, and where the rules are
 * stored (global configuration, section "rules"). Suggestions are not applied: they show a notification and wait in the
 * command palette ("Apply suggestion") until you accept them or a newer one replaces them.
 */
public final class RuleRunner implements RuleEngine.Context, RuleEngine.Executor {
	static final String SECTION = "rules";

	private final FemboyClient femboy;
	private final RuleEngine engine = new RuleEngine(System::currentTimeMillis);
	private List<String> loadWarnings = List.of();
	private Suggestion suggestion;

	/** A pending suggestion: what it is and how to apply it. */
	public record Suggestion(String text, Runnable apply, long madeAt) {
	}

	public RuleRunner(FemboyClient femboy) {
		this.femboy = femboy;
		reload();
		femboy.options().rulesEnabled.onChange(value -> engine.setEnabled(value));
		engine.setEnabled(femboy.options().rulesEnabled.get());
		// A restore or a full reset replaces the stored rules.
		femboy.config().addLoadListener(this::reload);
	}

	/** Reads the rules; a new installation starts with {@link RuleEngine#defaults()}. */
	public void reload() {
		JsonElement saved = femboy.config().section(SECTION);
		List<String> warnings = new ArrayList<>();
		engine.setRules(saved == null ? RuleEngine.defaults() : RuleEngine.fromJson(saved, warnings));
		loadWarnings = List.copyOf(warnings);
		warnings.forEach(warning -> femboy.diagnostics().warn("Rules", warning));
	}

	public RuleEngine engine() {
		return engine;
	}

	public List<String> loadWarnings() {
		return loadWarnings;
	}

	public void setRules(List<Rule> rules) {
		engine.setRules(rules);
		femboy.config().setSection(SECTION, engine.toJson());
	}

	public Suggestion suggestion() {
		return suggestion;
	}

	/** Applies the pending suggestion. @return what was done, or {@code null} if there was none */
	public String applySuggestion() {
		Suggestion pending = suggestion;
		suggestion = null;
		if (pending == null) {
			return null;
		}

		pending.apply().run();
		return pending.text();
	}

	public void dismissSuggestion() {
		suggestion = null;
	}

	/** Runs the rules for an event. */
	public void fire(Rule.Trigger trigger) {
		for (RuleEngine.Execution execution : engine.fire(trigger, this, this)) {
			String what = execution.done().isEmpty() ? "" : String.join("; ", execution.done());
			femboy.timeline().record(SessionTimeline.Kind.RULE, execution.rule() + (execution.dryRun() ? " (dry run)" : ""),
					execution.problem() == null ? what : what + (what.isEmpty() ? "" : "; ") + execution.problem());
			if (execution.problem() != null) {
				Notifications.post(Notifications.Category.RULE, "Rule " + execution.rule(), execution.problem(), Theme.WARNING);
			} else if (execution.dryRun()) {
				Notifications.post(Notifications.Category.RULE, "Rule " + execution.rule() + " (dry run)", what, Theme.INFO);
			}
		}
	}

	// ----- context ---------------------------------------------------------------------------

	@Override
	public String profile() {
		return femboy.config().activeProfile();
	}

	@Override
	public String dimension() {
		Level level = Minecraft.getInstance().level;
		if (level == null) return "";
		if (level.dimension() == Level.OVERWORLD) return "overworld";
		if (level.dimension() == Level.NETHER) return "nether";
		if (level.dimension() == Level.END) return "end";
		return level.dimension().identifier().toString();
	}

	@Override
	public String environment() {
		return environmentOf(femboy);
	}

	/** "singleplayer", "private network", "public server" or "" (no address is ever stored). */
	public static String environmentOf(FemboyClient femboy) {
		Minecraft client = Minecraft.getInstance();
		if (client.level == null) return "";
		if (client.getSingleplayerServer() != null) return "singleplayer";
		return femboy.modules().environment().testEnvironment() ? "private network" : "public server";
	}

	@Override
	public String holding() {
		var player = Minecraft.getInstance().player;
		return player == null ? "other" : holdingGroup(player.getMainHandItem());
	}

	public static String holdingGroup(ItemStack stack) {
		ProfileRule.Trigger group = AutoProfileSwitcher.classify(stack);
		if (group == null) {
			return stack.has(DataComponents.FOOD) ? "food" : "other";
		}

		return switch (group) {
			case SWORD, AXE, MACE, SPEAR, TRIDENT, BOW, CROSSBOW -> "weapon";
			case PICKAXE -> "tool";
			case BLOCK, RESPAWN_ANCHOR -> "block";
			case GOLDEN_APPLE -> "food";
			case POTION -> "potion";
			case TOTEM -> "totem";
			case EMPTY_HAND -> "empty hand";
			default -> stack.has(DataComponents.FOOD) ? "food" : "other";
		};
	}

	@Override
	public String hudLayout() {
		return femboy.hud().layout().layouts().activeName();
	}

	@Override
	public int fps() {
		return Minecraft.getInstance().getFps();
	}

	@Override
	public boolean profileExists(String name) {
		return femboy.config().profileExists(name);
	}

	@Override
	public boolean layoutExists(String name) {
		return femboy.hud().layout().layouts().layout(name) != null;
	}

	@Override
	public boolean moduleExists(String id) {
		return femboy.modules().get(id) != null;
	}

	@Override
	public boolean isVisualModule(String id) {
		Module module = femboy.modules().get(id);
		return module != null && (module.tier() == Module.Tier.VISUAL || module.tier() == Module.Tier.ASSIST) && !module.testEnvironmentOnly();
	}

	// ----- actions ---------------------------------------------------------------------------

	@Override
	public String execute(Rule rule, Rule.Action action) {
		String value = action.value();
		switch (action.type()) {
			case NOTIFY -> Notifications.post(Notifications.Category.RULE, rule.name(), value, Theme.ACCENT);
			case SUGGEST_PROFILE -> {
				if (!profileExists(value)) return "there is no profile '" + value + "'";
				if (value.equals(profile())) return null;
				suggest("Switch to profile " + value, () -> femboy.config().switchProfile(value));
			}
			case SUGGEST_PERFORMANCE_MODE -> {
				if (femboy.options().performanceMode.get() == GlobalOptions.PerformanceMode.PERFORMANCE) return null;
				suggest("Use Performance mode", () -> femboy.options().performanceMode.set(GlobalOptions.PerformanceMode.PERFORMANCE));
			}
			case SWITCH_PROFILE -> {
				return femboy.config().switchProfile(value);
			}
			case SWITCH_HUD_LAYOUT -> {
				return femboy.hud().layout().changeLayouts(layouts -> layouts.setActive(value));
			}
			case SHOW_HUD_ELEMENT, HIDE_HUD_ELEMENT -> {
				if (!moduleExists(value)) return "there is no module '" + value + "'";
				boolean hide = action.type() == Rule.ActionType.HIDE_HUD_ELEMENT;
				femboy.hud().layout().update(value, element -> element.withHidden(hide));
			}
			case ENABLE_VISUAL_MODULE, DISABLE_VISUAL_MODULE -> {
				if (!isVisualModule(value)) return "'" + value + "' is not a visual module";
				femboy.modules().setEnabled(femboy.modules().get(value), action.type() == Rule.ActionType.ENABLE_VISUAL_MODULE);
			}
			case SET_PERFORMANCE_MODE -> {
				try {
					femboy.options().performanceMode.set(GlobalOptions.PerformanceMode.valueOf(value.toUpperCase(Locale.ROOT)));
				} catch (IllegalArgumentException e) {
					return "unknown mode '" + value + "'";
				}
			}
			case ADD_TIMELINE_MARKER -> femboy.timeline().record(SessionTimeline.Kind.MARKER, value.isEmpty() ? rule.name() : value, "from rule " + rule.name());
		}

		return null;
	}

	private void suggest(String text, Runnable apply) {
		suggestion = new Suggestion(text, apply, System.currentTimeMillis());
		Notifications.post(Notifications.Category.RULE, "Suggestion", text + " (Ctrl+K in the menu: Apply suggestion)", Theme.ACCENT);
	}

	/** Used by the editor to describe value choices for an action type: profile, layout or module names. */
	public List<String> valueChoices(Rule.ActionType type) {
		return switch (type) {
			case SUGGEST_PROFILE, SWITCH_PROFILE -> femboy.config().profiles();
			case SWITCH_HUD_LAYOUT -> femboy.hud().layout().layouts().names();
			case SHOW_HUD_ELEMENT, HIDE_HUD_ELEMENT -> hudModuleIds();
			case ENABLE_VISUAL_MODULE, DISABLE_VISUAL_MODULE -> femboy.modules().all().stream().filter(m -> isVisualModule(m.id())).map(Module::id).toList();
			default -> type.choices();
		};
	}

	public List<String> valueChoices(Rule.ConditionType type) {
		return switch (type) {
			case PROFILE_IS, PROFILE_IS_NOT -> femboy.config().profiles();
			case HUD_LAYOUT_IS -> femboy.hud().layout().layouts().names();
			default -> type.choices();
		};
	}

	private List<String> hudModuleIds() {
		return femboy.modules().all().stream().filter(m -> m instanceof io.github.theamethyx.femboyclient.client.module.ClientHooks.HudBlock
				|| m instanceof io.github.theamethyx.femboyclient.client.module.ClientHooks.HudWidget).map(Module::id).toList();
	}
}
