package io.github.theamethyx.femboyclient.core.rules;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.LongSupplier;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

/**
 * Runs {@link Rule}s. Rules are checked in priority order (their order in the list). Safety:
 * <ul>
 *   <li>each rule runs at most once per its cooldown, and at most {@link #MAX_PER_SECOND} rules run per second overall;</li>
 *   <li>an action may cause another event (switching a profile fires "the profile changes"); such follow-up events are
 *       handled, but a rule never runs twice in one chain and chains stop after {@link #MAX_DEPTH} levels, so rules cannot
 *       loop;</li>
 *   <li>dry-run rules only record what they would do.</li>
 * </ul>
 */
public final class RuleEngine {
	public static final int MAX_DEPTH = 2;
	public static final int MAX_PER_SECOND = 5;
	public static final int MAX_RULES = 50;
	/** Seconds a rule that changes settings waits before it can run again, whatever its own cooldown. */
	public static final int MIN_CHANGE_COOLDOWN = 3;
	private static final int HISTORY = 100;

	/** What the client knows right now, for conditions and validation. Values are lower case. */
	public interface Context {
		String profile();

		/** "overworld", "nether", "end", another id, or "" without a world. */
		String dimension();

		/** "singleplayer", "private network", "public server" or "" without a world. */
		String environment();

		/** "weapon", "tool", "block", "food", "potion", "totem", "empty hand" or "other". */
		String holding();

		String hudLayout();

		int fps();

		boolean profileExists(String name);

		boolean layoutExists(String name);

		boolean moduleExists(String id);

		/** Whether a module only shows information (switching it never makes the client act in the game). */
		boolean isVisualModule(String id);
	}

	/** Carries out one action; returns {@code null} on success, otherwise a readable reason. */
	public interface Executor {
		String execute(Rule rule, Rule.Action action);
	}

	/** One run of a rule (or a skipped one), for the history. */
	public record Execution(long timeMillis, String rule, Rule.Trigger trigger, List<String> done, boolean dryRun, String problem) {
	}

	private final LongSupplier clock;
	private final List<Rule> rules = new ArrayList<>();
	private final Map<String, Long> lastRun = new HashMap<>();
	private final List<Execution> history = new ArrayList<>();
	private final long[] recentRuns = new long[MAX_PER_SECOND];
	private int recentCursor;
	private int depth;
	private final Set<Rule> firedInChain = new HashSet<>();
	private boolean enabled = true;

	public RuleEngine(LongSupplier clock) {
		this.clock = Objects.requireNonNull(clock);
		java.util.Arrays.fill(recentRuns, Long.MIN_VALUE / 2);
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public boolean enabled() {
		return enabled;
	}

	public List<Rule> rules() {
		return List.copyOf(rules);
	}

	public void setRules(List<Rule> newRules) {
		rules.clear();
		rules.addAll(newRules.subList(0, Math.min(MAX_RULES, newRules.size())));
		lastRun.keySet().retainAll(rules.stream().map(this::key).toList());
	}

	/** Executions, newest first. */
	public List<Execution> history() {
		return List.copyOf(history.reversed());
	}

	public void clearHistory() {
		history.clear();
	}

	private String key(Rule rule) {
		return rule.name() + "/" + rule.trigger();
	}

	/** Whether a rule's conditions hold now. */
	public static boolean matches(Rule rule, Context context) {
		if (rule.conditions().isEmpty()) {
			return true;
		}

		for (Rule.Condition condition : rule.conditions()) {
			boolean holds = holds(condition, context);
			if (rule.matchAll() && !holds) return false;
			if (!rule.matchAll() && holds) return true;
		}

		return rule.matchAll();
	}

	static boolean holds(Rule.Condition condition, Context context) {
		String value = condition.value().toLowerCase(Locale.ROOT);
		return switch (condition.type()) {
			case PROFILE_IS -> context.profile().equalsIgnoreCase(condition.value());
			case PROFILE_IS_NOT -> !context.profile().equalsIgnoreCase(condition.value());
			case DIMENSION_IS -> context.dimension().equals(value);
			case ENVIRONMENT_IS -> context.environment().equals(value);
			case HOLDING -> context.holding().equals(value);
			case HUD_LAYOUT_IS -> context.hudLayout().equalsIgnoreCase(condition.value());
			case FPS_BELOW -> {
				try {
					yield context.fps() < Integer.parseInt(condition.value());
				} catch (NumberFormatException e) {
					yield false;
				}
			}
		};
	}

	/**
	 * Handles an event: every enabled rule for this trigger whose conditions hold and whose cooldown has passed runs (or,
	 * in dry-run mode, is recorded). May be called again from inside an action (see the class description).
	 *
	 * @return what ran, in order
	 */
	public List<Execution> fire(Rule.Trigger trigger, Context context, Executor executor) {
		List<Execution> runs = new ArrayList<>();
		if (!enabled) {
			return runs;
		}

		if (depth >= MAX_DEPTH) {
			return runs;
		}

		boolean outermost = depth == 0;
		depth++;
		try {
			for (Rule rule : List.copyOf(rules)) {
				if (!rule.enabled() || rule.trigger() != trigger || firedInChain.contains(rule) || !matches(rule, context)) {
					continue;
				}

				long now = clock.getAsLong();
				Long last = lastRun.get(key(rule));
				// Rules that change something wait at least MIN_CHANGE_COOLDOWN seconds, so two rules cannot flip a setting every tick.
				boolean changes = rule.actions().stream().anyMatch(action -> action.type().changesSomething());
				long cooldown = Math.max(rule.cooldownSeconds(), changes ? MIN_CHANGE_COOLDOWN : 0) * 1000L;
				if (last != null && now - last < cooldown) {
					continue;
				}

				if (now - recentRuns[recentCursor] < 1000) {
					record(runs, new Execution(now, rule.name(), trigger, List.of(), rule.dryRun(), "Skipped: too many rules ran in the last second."));
					continue;
				}

				recentRuns[recentCursor] = now;
				recentCursor = (recentCursor + 1) % MAX_PER_SECOND;
				lastRun.put(key(rule), now);
				firedInChain.add(rule);
				List<String> done = new ArrayList<>();
				String problem = null;
				for (Rule.Action action : rule.actions()) {
					String description = action.type().label() + (action.value().isEmpty() ? "" : ": " + action.value());
					if (rule.dryRun()) {
						done.add("would " + lowerFirst(description));
						continue;
					}

					String failure;
					try {
						failure = executor.execute(rule, action);
					} catch (RuntimeException e) {
						failure = "failed: " + e.getMessage();
					}

					if (failure == null) {
						done.add(description);
					} else {
						problem = (problem == null ? "" : problem + " ") + description + " - " + failure;
					}
				}

				record(runs, new Execution(now, rule.name(), trigger, List.copyOf(done), rule.dryRun(), problem));
			}
		} finally {
			depth--;
			if (outermost) {
				firedInChain.clear();
			}
		}

		return runs;
	}

	private static String lowerFirst(String text) {
		return text.isEmpty() ? text : Character.toLowerCase(text.charAt(0)) + text.substring(1);
	}

	private void record(List<Execution> runs, Execution execution) {
		runs.add(execution);
		history.add(execution);
		while (history.size() > HISTORY) {
			history.removeFirst();
		}
	}

	/**
	 * Problems in the rules: missing or unknown values, modules that are not visual, and rules on the same trigger whose
	 * actions contradict each other.
	 */
	public List<String> validate(Context context) {
		List<String> problems = new ArrayList<>();
		for (Rule rule : rules) {
			if (rule.actions().isEmpty()) {
				problems.add("'" + rule.name() + "' has no actions.");
			}

			for (Rule.Condition condition : rule.conditions()) {
				String problem = checkCondition(condition, context);
				if (problem != null) problems.add("'" + rule.name() + "': " + problem);
			}

			for (Rule.Action action : rule.actions()) {
				String problem = checkAction(action, context);
				if (problem != null) problems.add("'" + rule.name() + "': " + problem);
			}

			if (rule.trigger() == Rule.Trigger.PROFILE_CHANGED && rule.actions().stream().anyMatch(a -> a.type() == Rule.ActionType.SWITCH_PROFILE)
					&& rule.conditions().isEmpty()) {
				problems.add("'" + rule.name() + "' switches the profile whenever the profile changes; add a condition (it will not loop, "
						+ "but it undoes every switch you make).");
			}
		}

		for (int i = 0; i < rules.size(); i++) {
			for (int j = i + 1; j < rules.size(); j++) {
				Rule a = rules.get(i);
				Rule b = rules.get(j);
				if (a.enabled() && b.enabled() && a.trigger() == b.trigger() && a.conditions().equals(b.conditions())) {
					String conflict = conflict(a, b);
					if (conflict != null) {
						problems.add("'" + a.name() + "' and '" + b.name() + "' conflict: " + conflict + ". The one higher in the list runs first.");
					}
				}
			}
		}

		return problems;
	}

	static String checkCondition(Rule.Condition condition, Context context) {
		Rule.ConditionType type = condition.type();
		if (condition.value().isEmpty()) {
			return "'" + type.label() + "' needs a value.";
		}

		if (!type.choices().isEmpty() && !type.choices().contains(condition.value().toLowerCase(Locale.ROOT))) {
			return "'" + type.label() + "' must be one of " + String.join(", ", type.choices()) + ".";
		}

		if ((type == Rule.ConditionType.PROFILE_IS || type == Rule.ConditionType.PROFILE_IS_NOT) && !context.profileExists(condition.value())) {
			return "there is no profile '" + condition.value() + "'.";
		}

		if (type == Rule.ConditionType.HUD_LAYOUT_IS && !context.layoutExists(condition.value())) {
			return "there is no HUD layout '" + condition.value() + "'.";
		}

		if (type == Rule.ConditionType.FPS_BELOW) {
			try {
				int fps = Integer.parseInt(condition.value());
				if (fps < 1 || fps > 1000) return "the FPS limit must be between 1 and 1000.";
			} catch (NumberFormatException e) {
				return "the FPS limit must be a whole number.";
			}
		}

		return null;
	}

	static String checkAction(Rule.Action action, Context context) {
		Rule.ActionType type = action.type();
		if (type.needsValue() && action.value().isEmpty()) {
			return "'" + type.label() + "' needs a value.";
		}

		return switch (type) {
			case SUGGEST_PROFILE, SWITCH_PROFILE -> context.profileExists(action.value()) ? null : "there is no profile '" + action.value() + "'.";
			case SWITCH_HUD_LAYOUT -> context.layoutExists(action.value()) ? null : "there is no HUD layout '" + action.value() + "'.";
			case SHOW_HUD_ELEMENT, HIDE_HUD_ELEMENT -> context.moduleExists(action.value()) ? null : "there is no module '" + action.value() + "'.";
			case ENABLE_VISUAL_MODULE, DISABLE_VISUAL_MODULE -> !context.moduleExists(action.value()) ? "there is no module '" + action.value() + "'."
					: context.isVisualModule(action.value()) ? null
					: "'" + action.value() + "' is not a visual module; rules only switch modules that show information.";
			case SET_PERFORMANCE_MODE -> type.choices().contains(action.value().toLowerCase(Locale.ROOT)) ? null
					: "the mode must be one of " + String.join(", ", type.choices()) + ".";
			default -> null;
		};
	}

	private static String conflict(Rule a, Rule b) {
		for (Rule.Action x : a.actions()) {
			for (Rule.Action y : b.actions()) {
				if (x.type() == y.type() && !x.value().equalsIgnoreCase(y.value())
						&& (x.type() == Rule.ActionType.SWITCH_PROFILE || x.type() == Rule.ActionType.SWITCH_HUD_LAYOUT
						|| x.type() == Rule.ActionType.SET_PERFORMANCE_MODE)) {
					return "one sets '" + x.value() + "', the other '" + y.value() + "'";
				}

				if (x.value().equalsIgnoreCase(y.value()) && (opposite(x.type(), y.type(), Rule.ActionType.ENABLE_VISUAL_MODULE, Rule.ActionType.DISABLE_VISUAL_MODULE)
						|| opposite(x.type(), y.type(), Rule.ActionType.SHOW_HUD_ELEMENT, Rule.ActionType.HIDE_HUD_ELEMENT))) {
					return "one turns '" + x.value() + "' on, the other off";
				}
			}
		}

		return null;
	}

	private static boolean opposite(Rule.ActionType x, Rule.ActionType y, Rule.ActionType on, Rule.ActionType off) {
		return x == on && y == off || x == off && y == on;
	}

	// ----- JSON ------------------------------------------------------------------------------

	public JsonArray toJson() {
		JsonArray array = new JsonArray();
		rules.forEach(rule -> array.add(rule.toJson()));
		return array;
	}

	public static List<Rule> fromJson(JsonElement json, List<String> warnings) {
		List<Rule> result = new ArrayList<>();
		if (json instanceof JsonArray array) {
			for (JsonElement element : array) {
				Rule rule = Rule.fromJson(element, warnings);
				if (rule != null && result.size() < MAX_RULES) {
					result.add(rule);
				}
			}
		}

		return result;
	}

	/** Rules as shareable text. */
	public String export() {
		JsonObject json = new JsonObject();
		json.addProperty("femboyClientRules", 1);
		json.add("rules", toJson());
		return json.toString();
	}

	/** Parses exported rules (not applied). @return the rules, or {@code null} with the reason in {@code warnings} */
	public static List<Rule> parseExport(String text, List<String> warnings) {
		try {
			JsonElement parsed = JsonParser.parseString(text == null ? "" : text.strip());
			if (parsed instanceof JsonObject object && object.get("rules") instanceof JsonArray array) {
				return fromJson(array, warnings);
			}

			warnings.add("This is not a Femboy Client rule export (no 'rules').");
		} catch (JsonParseException e) {
			warnings.add("This is not valid JSON: " + e.getMessage());
		}

		return null;
	}

	/** Rules a new installation starts with: they only suggest, nothing changes by itself. */
	public static List<Rule> defaults() {
		return List.of(
				new Rule("Suggest Performance mode when FPS is low", true, Rule.Trigger.LOW_FPS, true, List.of(new Rule.Condition(Rule.ConditionType.FPS_BELOW, "30")),
						List.of(new Rule.Action(Rule.ActionType.SUGGEST_PERFORMANCE_MODE, "")), 600, false),
				new Rule("Remind me of sandbox modules in local worlds", false, Rule.Trigger.WORLD_JOINED, true,
						List.of(new Rule.Condition(Rule.ConditionType.ENVIRONMENT_IS, "singleplayer")),
						List.of(new Rule.Action(Rule.ActionType.NOTIFY, "Local world: sandbox modules can run here.")), 60, false));
	}
}
