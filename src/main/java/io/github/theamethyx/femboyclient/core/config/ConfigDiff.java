package io.github.theamethyx.femboyclient.core.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import io.github.theamethyx.femboyclient.core.module.Module;
import io.github.theamethyx.femboyclient.core.module.ModuleManager;
import io.github.theamethyx.femboyclient.core.setting.ModuleSettings;
import io.github.theamethyx.femboyclient.core.setting.Setting;

/**
 * Compares two configurations (profile module data plus the client options a profile stores) and lists exactly what
 * differs: modules turned on or off, key binds, setting values and other module data. A value missing on one side counts
 * as its default, so a profile that never touched a setting and one that set it to the default are equal.
 */
public final class ConfigDiff {
	private ConfigDiff() {
	}

	/** What kind of value differs. */
	public enum Kind {
		/** On in one configuration, off in the other. */
		ENABLED,
		/** A different key bind; {@code left}/{@code right} hold GLFW key codes (-1 = none) for the GUI to name. */
		KEY,
		/** Toggle or hold. */
		KEY_MODE,
		/** A module setting. */
		SETTING,
		/** Data that is not a plain setting (waypoints, lists); only "differs" is reported. */
		DATA,
		/** A client option stored in profiles (theme, performance mode, ...). */
		OPTION
	}

	/**
	 * One difference. {@code moduleId}/{@code moduleName} are empty for {@link Kind#OPTION}; {@code what} names the setting
	 * or option; {@code left}/{@code right} are readable values.
	 */
	public record Difference(Kind kind, String moduleId, String moduleName, String what, String left, String right) {
	}

	/**
	 * Lists the differences between two module data objects (as stored under "modules" in a profile) and two "options"
	 * objects (either may be {@code null}), in module registration order. Modules this version does not have are ignored.
	 */
	public static List<Difference> compare(ModuleManager modules, JsonObject leftModules, JsonObject rightModules, ModuleSettings options,
			JsonObject leftOptions, JsonObject rightOptions) {
		List<Difference> result = new ArrayList<>();
		for (Module module : modules.all()) {
			JsonObject left = leftModules.get(module.id()) instanceof JsonObject object ? object : new JsonObject();
			JsonObject right = rightModules.get(module.id()) instanceof JsonObject object ? object : new JsonObject();
			compareModule(module, left, right, result);
		}

		if (options != null) {
			for (Setting<?> setting : options.all()) {
				JsonElement left = leftOptions == null ? null : leftOptions.get(setting.id());
				JsonElement right = rightOptions == null ? null : rightOptions.get(setting.id());
				if (left == null || right == null) {
					// Only options both sides store are compared; a profile without them keeps whatever is current.
					continue;
				}

				if (!left.equals(right)) {
					result.add(new Difference(Kind.OPTION, "", "", setting.name(), setting.describe(left), setting.describe(right)));
				}
			}
		}

		return result;
	}

	private static void compareModule(Module module, JsonObject left, JsonObject right, List<Difference> result) {
		boolean leftOn = bool(left.get("enabled"), module.isEnabledByDefault());
		boolean rightOn = bool(right.get("enabled"), module.isEnabledByDefault());
		if (leftOn != rightOn) {
			result.add(new Difference(Kind.ENABLED, module.id(), module.name(), "Enabled", leftOn ? "on" : "off", rightOn ? "on" : "off"));
		}

		int leftKey = integer(left.get("key"), module.defaultKey());
		int rightKey = integer(right.get("key"), module.defaultKey());
		if (leftKey != rightKey) {
			result.add(new Difference(Kind.KEY, module.id(), module.name(), "Key bind", String.valueOf(leftKey), String.valueOf(rightKey)));
		}

		String leftMode = string(left.get("keyMode"), "toggle");
		String rightMode = string(right.get("keyMode"), "toggle");
		if (!leftMode.equals(rightMode)) {
			result.add(new Difference(Kind.KEY_MODE, module.id(), module.name(), "Key mode", leftMode, rightMode));
		}

		JsonObject leftSettings = left.get("settings") instanceof JsonObject object ? object : new JsonObject();
		JsonObject rightSettings = right.get("settings") instanceof JsonObject object ? object : new JsonObject();
		for (Setting<?> setting : module.settings().all()) {
			JsonElement defaultValue = setting.defaultJson();
			JsonElement a = Objects.requireNonNullElse(leftSettings.get(setting.id()), defaultValue);
			JsonElement b = Objects.requireNonNullElse(rightSettings.get(setting.id()), defaultValue);
			if (!a.equals(b)) {
				String what = setting.section() == null ? setting.name() : setting.section() + " › " + setting.name();
				result.add(new Difference(Kind.SETTING, module.id(), module.name(), what, setting.describe(a), setting.describe(b)));
			}
		}

		JsonElement leftExtra = left.get("extra") == null ? new JsonObject() : left.get("extra");
		JsonElement rightExtra = right.get("extra") == null ? new JsonObject() : right.get("extra");
		if (!leftExtra.equals(rightExtra)) {
			result.add(new Difference(Kind.DATA, module.id(), module.name(), "Other data (lists, positions)", "differs", "differs"));
		}
	}

	private static boolean bool(JsonElement json, boolean fallback) {
		return json instanceof JsonPrimitive primitive && primitive.isBoolean() ? primitive.getAsBoolean() : fallback;
	}

	private static int integer(JsonElement json, int fallback) {
		return json instanceof JsonPrimitive primitive && primitive.isNumber() && Module.isValidKey(primitive.getAsInt()) ? primitive.getAsInt() : fallback;
	}

	private static String string(JsonElement json, String fallback) {
		return json instanceof JsonPrimitive primitive && primitive.isString() ? primitive.getAsString() : fallback;
	}
}
