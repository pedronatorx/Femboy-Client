package io.github.theamethyx.femboyclient.core.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import io.github.theamethyx.femboyclient.core.setting.EnumSetting;

/**
 * One rule for automatic profile switching: when the held item belongs to {@link #item()} and the player is in
 * {@link #dimension()}, the client switches to {@link #profile()}. Rules are checked in order; the first match
 * wins. Item groups are matched by the client using registry data, never by translated item names.
 */
public record ProfileRule(Trigger item, Dimension dimension, String profile) {
	public ProfileRule {
		Objects.requireNonNull(item);
		Objects.requireNonNull(dimension);
		if (!ConfigurationManager.isValidProfileName(profile)) {
			throw new IllegalArgumentException("Invalid profile name: " + profile);
		}
	}

	/** What the player holds in the main hand, or a movement context. */
	public enum Trigger implements EnumSetting.Labeled {
		SWORD("Sword"),
		AXE("Axe"),
		MACE("Mace"),
		SPEAR("Spear"),
		TRIDENT("Trident"),
		BOW("Bow"),
		CROSSBOW("Crossbow"),
		END_CRYSTAL("End crystal"),
		RESPAWN_ANCHOR("Respawn anchor"),
		POTION("Splash or lingering potion"),
		TOTEM("Totem of Undying"),
		GOLDEN_APPLE("Golden apple"),
		FIREWORK("Firework rocket"),
		GLIDING("Gliding with an elytra"),
		EMPTY_HAND("Empty hand");

		private final String label;

		Trigger(String label) {
			this.label = label;
		}

		@Override
		public String label() {
			return label;
		}
	}

	public enum Dimension implements EnumSetting.Labeled {
		ANY("Any dimension"), OVERWORLD("Overworld"), NETHER("Nether"), END("The End");

		private final String label;

		Dimension(String label) {
			this.label = label;
		}

		@Override
		public String label() {
			return label;
		}
	}

	public boolean matches(Trigger current, boolean gliding, Dimension currentDimension) {
		if (dimension != Dimension.ANY && dimension != currentDimension) {
			return false;
		}

		return item == Trigger.GLIDING ? gliding : item == current;
	}

	/**
	 * The profile of the first matching rule, or {@code null} if none matches.
	 *
	 * @param held what is in the main hand (never {@link Trigger#GLIDING})
	 */
	public static String select(List<ProfileRule> rules, Trigger held, boolean gliding, Dimension dimension) {
		for (ProfileRule rule : rules) {
			if (rule.matches(held, gliding, dimension)) {
				return rule.profile;
			}
		}

		return null;
	}

	public JsonObject toJson() {
		JsonObject json = new JsonObject();
		json.addProperty("item", item.name().toLowerCase(Locale.ROOT));
		json.addProperty("dimension", dimension.name().toLowerCase(Locale.ROOT));
		json.addProperty("profile", profile);
		return json;
	}

	public static JsonArray toJson(List<ProfileRule> rules) {
		JsonArray array = new JsonArray();
		rules.forEach(rule -> array.add(rule.toJson()));
		return array;
	}

	/** Parses saved rules, skipping invalid entries with a readable warning each. */
	public static List<ProfileRule> fromJson(JsonElement json, List<String> warnings) {
		List<ProfileRule> rules = new ArrayList<>();
		if (json == null) {
			return rules;
		}

		if (!(json instanceof JsonArray array)) {
			warnings.add("Profile switching rules must be a list; no rules were loaded.");
			return rules;
		}

		for (int i = 0; i < array.size(); i++) {
			try {
				JsonObject object = array.get(i).getAsJsonObject();
				rules.add(new ProfileRule(parse(Trigger.class, object.get("item")), parse(Dimension.class, object.get("dimension")),
						string(object.get("profile"))));
			} catch (RuntimeException e) {
				warnings.add("Profile switching rule " + (i + 1) + " was skipped (" + e.getMessage() + ").");
			}
		}

		return rules;
	}

	private static String string(JsonElement json) {
		if (json instanceof JsonPrimitive primitive && primitive.isString()) {
			return primitive.getAsString();
		}

		throw new IllegalArgumentException("expected text");
	}

	private static <E extends Enum<E>> E parse(Class<E> type, JsonElement json) {
		String name = string(json).toUpperCase(Locale.ROOT);
		try {
			return Enum.valueOf(type, name);
		} catch (IllegalArgumentException e) {
			throw new IllegalArgumentException("unknown value '" + name.toLowerCase(Locale.ROOT) + "'");
		}
	}
}
