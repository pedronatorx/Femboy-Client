package io.github.theamethyx.femboyclient.core.rules;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import io.github.theamethyx.femboyclient.core.setting.EnumSetting;

/**
 * A user rule: <b>when</b> something happens, <b>if</b> its conditions hold, <b>then</b> do its actions. Rules recommend by
 * default (a notification); actions that change something are explicit choices of the user. No action can switch on a
 * module that acts in the game: module actions are limited to modules the client marks as visual (see
 * {@link RuleEngine.Context#isVisualModule}).
 *
 * @param name             shown in the editor and the history
 * @param enabled          off rules are kept but never run
 * @param trigger          the event that starts the check
 * @param matchAll         all conditions must hold (true) or any one (false); no conditions always holds
 * @param conditions       checked when the trigger fires
 * @param actions          run in order when the conditions hold
 * @param cooldownSeconds  the rule runs at most once in this many seconds
 * @param dryRun           only record what would happen, without doing it
 */
public record Rule(String name, boolean enabled, Trigger trigger, boolean matchAll, List<Condition> conditions, List<Action> actions,
		int cooldownSeconds, boolean dryRun) {
	public static final int MAX_CONDITIONS = 6;
	public static final int MAX_ACTIONS = 6;

	public Rule {
		Objects.requireNonNull(trigger);
		name = name == null || name.isBlank() ? "Rule" : name.strip();
		if (name.length() > 40) name = name.substring(0, 40);
		conditions = List.copyOf(conditions);
		actions = List.copyOf(actions);
		cooldownSeconds = Math.clamp(cooldownSeconds, 0, 3600);
	}

	public Rule withEnabled(boolean value) {
		return new Rule(name, value, trigger, matchAll, conditions, actions, cooldownSeconds, value && dryRun);
	}

	public Rule withName(String value) {
		return new Rule(value, enabled, trigger, matchAll, conditions, actions, cooldownSeconds, dryRun);
	}

	public Rule withTrigger(Trigger value) {
		return new Rule(name, enabled, value, matchAll, conditions, actions, cooldownSeconds, dryRun);
	}

	public Rule withMatchAll(boolean value) {
		return new Rule(name, enabled, trigger, value, conditions, actions, cooldownSeconds, dryRun);
	}

	public Rule withConditions(List<Condition> value) {
		return new Rule(name, enabled, trigger, matchAll, value, actions, cooldownSeconds, dryRun);
	}

	public Rule withActions(List<Action> value) {
		return new Rule(name, enabled, trigger, matchAll, conditions, value, cooldownSeconds, dryRun);
	}

	public Rule withCooldown(int value) {
		return new Rule(name, enabled, trigger, matchAll, conditions, actions, value, dryRun);
	}

	public Rule withDryRun(boolean value) {
		return new Rule(name, enabled, trigger, matchAll, conditions, actions, cooldownSeconds, value);
	}

	/** What starts a rule. */
	public enum Trigger implements EnumSetting.Labeled {
		PROFILE_CHANGED("The profile changes"),
		WORLD_JOINED("A world is joined"),
		DIMENSION_CHANGED("The dimension changes"),
		GUI_SCALE_CHANGED("The GUI scale or window size changes"),
		LOW_FPS("FPS stays below the limit for 5 s"),
		COMBAT_STARTED("Combat starts"),
		COMBAT_ENDED("Combat ends"),
		HELD_ITEM_CHANGED("The held item type changes");

		private final String label;

		Trigger(String label) {
			this.label = label;
		}

		@Override
		public String label() {
			return label;
		}
	}

	/** A condition: a type and its value (a profile name, a dimension, an environment, an item group or a number). */
	public record Condition(ConditionType type, String value) {
		public Condition {
			Objects.requireNonNull(type);
			value = value == null ? "" : value.strip();
		}
	}

	public enum ConditionType implements EnumSetting.Labeled {
		PROFILE_IS("Profile is"),
		PROFILE_IS_NOT("Profile is not"),
		DIMENSION_IS("Dimension is"),
		ENVIRONMENT_IS("Environment is"),
		HOLDING("Holding"),
		HUD_LAYOUT_IS("HUD layout is"),
		FPS_BELOW("FPS is below");

		private final String label;

		ConditionType(String label) {
			this.label = label;
		}

		@Override
		public String label() {
			return label;
		}

		/** The accepted values, or an empty list when the value is free text (a name) or a number. */
		public List<String> choices() {
			return switch (this) {
				case DIMENSION_IS -> List.of("overworld", "nether", "end");
				case ENVIRONMENT_IS -> List.of("singleplayer", "private network", "public server");
				case HOLDING -> List.of("weapon", "tool", "block", "food", "potion", "totem", "empty hand");
				default -> List.of();
			};
		}
	}

	/** An action and its value (a profile or layout name, a module id, a mode or a message). */
	public record Action(ActionType type, String value) {
		public Action {
			Objects.requireNonNull(type);
			value = value == null ? "" : value.strip();
		}
	}

	public enum ActionType implements EnumSetting.Labeled {
		NOTIFY("Show a notification", false),
		SUGGEST_PROFILE("Suggest a profile", false),
		SUGGEST_PERFORMANCE_MODE("Suggest Performance mode", false),
		SWITCH_PROFILE("Switch to a profile", true),
		SWITCH_HUD_LAYOUT("Use a HUD layout", true),
		SHOW_HUD_ELEMENT("Show a HUD element", true),
		HIDE_HUD_ELEMENT("Hide a HUD element", true),
		ENABLE_VISUAL_MODULE("Turn a visual module on", true),
		DISABLE_VISUAL_MODULE("Turn a visual module off", true),
		SET_PERFORMANCE_MODE("Set the performance mode", true),
		ADD_TIMELINE_MARKER("Add a timeline marker", false);

		private final String label;
		private final boolean changesSomething;

		ActionType(String label, boolean changesSomething) {
			this.label = label;
			this.changesSomething = changesSomething;
		}

		@Override
		public String label() {
			return label;
		}

		/** Whether the action changes settings (otherwise it only informs). */
		public boolean changesSomething() {
			return changesSomething;
		}

		public List<String> choices() {
			return this == SET_PERFORMANCE_MODE ? List.of("performance", "balanced", "low_impact") : List.of();
		}

		/** Whether the value is required (a name, an id or a mode). */
		public boolean needsValue() {
			return this != SUGGEST_PERFORMANCE_MODE;
		}
	}

	// ----- JSON ------------------------------------------------------------------------------

	public JsonObject toJson() {
		JsonObject json = new JsonObject();
		json.addProperty("name", name);
		json.addProperty("enabled", enabled);
		json.addProperty("when", trigger.name());
		json.addProperty("match", matchAll ? "all" : "any");
		JsonArray conditionsJson = new JsonArray();
		for (Condition condition : conditions) {
			JsonObject item = new JsonObject();
			item.addProperty("type", condition.type().name());
			item.addProperty("value", condition.value());
			conditionsJson.add(item);
		}

		json.add("if", conditionsJson);
		JsonArray actionsJson = new JsonArray();
		for (Action action : actions) {
			JsonObject item = new JsonObject();
			item.addProperty("type", action.type().name());
			item.addProperty("value", action.value());
			actionsJson.add(item);
		}

		json.add("then", actionsJson);
		json.addProperty("cooldown", cooldownSeconds);
		if (dryRun) {
			json.addProperty("dryRun", true);
		}

		return json;
	}

	/** Reads a rule; returns {@code null} (with a warning) if it cannot be used. Unknown conditions or actions are skipped. */
	public static Rule fromJson(JsonElement element, List<String> warnings) {
		if (!(element instanceof JsonObject json)) {
			warnings.add("A rule was not an object and was skipped.");
			return null;
		}

		String name = string(json, "name", "Rule");
		Trigger trigger = parse(Trigger.class, string(json, "when", ""));
		if (trigger == null) {
			warnings.add("Rule '" + name + "': unknown trigger '" + string(json, "when", "") + "'; the rule was skipped.");
			return null;
		}

		List<Condition> conditions = new ArrayList<>();
		if (json.get("if") instanceof JsonArray array) {
			for (JsonElement item : array) {
				ConditionType type = item instanceof JsonObject object ? parse(ConditionType.class, string(object, "type", "")) : null;
				if (type == null) {
					warnings.add("Rule '" + name + "': an unknown condition was skipped.");
				} else if (conditions.size() < MAX_CONDITIONS) {
					conditions.add(new Condition(type, string((JsonObject) item, "value", "")));
				}
			}
		}

		List<Action> actions = new ArrayList<>();
		if (json.get("then") instanceof JsonArray array) {
			for (JsonElement item : array) {
				ActionType type = item instanceof JsonObject object ? parse(ActionType.class, string(object, "type", "")) : null;
				if (type == null) {
					warnings.add("Rule '" + name + "': an unknown action was skipped.");
				} else if (actions.size() < MAX_ACTIONS) {
					actions.add(new Action(type, string((JsonObject) item, "value", "")));
				}
			}
		}

		boolean enabled = !(json.get("enabled") instanceof JsonPrimitive primitive && primitive.isBoolean()) || primitive.getAsBoolean();
		boolean all = !"any".equals(string(json, "match", "all"));
		int cooldown = json.get("cooldown") instanceof JsonPrimitive primitive && primitive.isNumber() ? primitive.getAsInt() : 30;
		boolean dryRun = json.get("dryRun") instanceof JsonPrimitive primitive && primitive.isBoolean() && primitive.getAsBoolean();
		return new Rule(name, enabled, trigger, all, conditions, actions, cooldown, dryRun);
	}

	private static String string(JsonObject json, String key, String fallback) {
		return json.get(key) instanceof JsonPrimitive primitive && primitive.isString() ? primitive.getAsString() : fallback;
	}

	private static <E extends Enum<E>> E parse(Class<E> type, String name) {
		try {
			return Enum.valueOf(type, name);
		} catch (IllegalArgumentException e) {
			return null;
		}
	}
}
