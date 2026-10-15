package io.github.theamethyx.femboyclient.client.profile;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import io.github.theamethyx.femboyclient.core.config.ProfileRule;

/**
 * Ready-made starting profiles for common PvP styles. Installing one creates an ordinary profile that can be
 * edited, copied, exported and reset like any other. They only turn on information and visual modules; Auto
 * Attribute Swap is configured where it fits but left off, so you decide when it runs.
 */
public final class Presets {
	/** A preset: the profile name it installs as, a title and description, the module data, and the item that suits it. */
	public record Preset(String profile, String title, String description, JsonObject modules, ProfileRule.Trigger trigger) {
	}

	private Presets() {
	}

	public static List<Preset> all() {
		List<Preset> list = new ArrayList<>();
		list.add(new Preset("sword_pvp", "Sword PvP", "Target HUD, attack charge, clicks per second, reach and combo.",
				new Builder()
						.on("target_hud")
						.on("combat_info", "accuracy", true)
						.on("weapon_info")
						.on("health_armor", "only_low", true)
						.build(), ProfileRule.Trigger.SWORD));
		list.add(new Preset("axe_pvp", "Axe PvP", "Shield state of your target, your own shield cooldown, armor and combat statistics. "
				+ "Auto Attribute Swap is set up for axe-against-shield but off.",
				new Builder()
						.on("target_hud", "shield", true)
						.on("weapon_info")
						.on("combat_info")
						.on("health_armor")
						.off("auto_attribute_swap", "axe_enabled", true, "axe_shield_only", true, "mace_enabled", false)
						.build(), ProfileRule.Trigger.AXE));
		list.add(new Preset("mace_pvp", "Mace PvP", "Fall distance, smash bonus, vertical speed and wind charges, with your target and totem count. "
				+ "Auto Attribute Swap is set up for the mace but off. Auto StunSlam is not available.",
				new Builder()
						.on("weapon_info")
						.on("movement_info", "speed", false)
						.on("target_hud")
						.on("item_counters", "items", ids("wind_charge", "totem_of_undying", "golden_apple", "ender_pearl"))
						.off("auto_attribute_swap", "mace_enabled", true, "mace_min_fall", 1.5, "axe_enabled", false)
						.build(), ProfileRule.Trigger.MACE));
		list.add(new Preset("spear_mace_pvp", "Spear + Mace PvP", "Spear charge windows and Lunge status, mace timing and target distance. "
				+ "Auto Attribute Swap is set up with separate spear and mace cooldowns but off.",
				new Builder()
						.on("weapon_info")
						.on("target_hud", "distance", true)
						.on("combat_info", "cps", false)
						.on("item_counters", "items", ids("wind_charge", "totem_of_undying", "golden_apple"))
						.off("auto_attribute_swap", "spear_enabled", true, "spear_cooldown", 500.0, "mace_enabled", true, "mace_cooldown", 0.0,
								"axe_enabled", false)
						.build(), ProfileRule.Trigger.SPEAR));
		list.add(new Preset("crystal_pvp", "Crystal PvP", "Crystal placement preview, explosion damage estimates, totems, crystals and armor durability.",
				new Builder()
						.on("explosion_info")
						.on("item_counters", "items", ids("totem_of_undying", "end_crystal", "obsidian", "golden_apple", "enchanted_golden_apple",
								"experience_bottle"), "refill_items", ids("totem_of_undying", "end_crystal", "obsidian"))
						.on("health_armor")
						.on("target_hud")
						.build(), ProfileRule.Trigger.END_CRYSTAL));
		list.add(new Preset("anchor_pvp", "Anchor PvP", "Anchor charges and whether it explodes here, explosion estimates, anchors, glowstone and totems.",
				new Builder()
						.on("explosion_info", "placements", false)
						.on("item_counters", "items", ids("respawn_anchor", "glowstone", "totem_of_undying", "golden_apple"),
								"refill_items", ids("respawn_anchor", "glowstone", "totem_of_undying"))
						.on("health_armor")
						.on("target_hud")
						.build(), ProfileRule.Trigger.RESPAWN_ANCHOR));
		list.add(new Preset("pot_pvp", "Pot PvP", "Effect timers, potions counted by type with hotbar refill reminders, and splash potion paths.",
				new Builder()
						.on("effects")
						.on("item_counters", "items", ids("splash_potion", "lingering_potion", "ender_pearl", "golden_apple"),
								"split_potions", true, "refill_items", ids("splash_potion"))
						.on("trajectories", "throwables", true)
						.on("health_armor")
						.on("target_hud")
						.build(), ProfileRule.Trigger.POTION));
		list.add(new Preset("uhc_pvp", "UHC / Survival", "Health warning, golden apples, arrows, bow paths, fall warnings and equipment durability alerts.",
				new Builder()
						.on("health_armor", "low_health", 5.0, "alerts", true)
						.on("item_counters", "items", ids("golden_apple", "enchanted_golden_apple", "arrow", "ender_pearl", "water_bucket"))
						.on("trajectories")
						.on("weapon_info")
						.on("movement_info", "speed", false, "vertical", false)
						.on("target_hud")
						.build(), ProfileRule.Trigger.GOLDEN_APPLE));
		list.add(new Preset("elytra_pvp", "Elytra PvP", "Flight speed, height, elytra durability, rockets and obstacle warnings.",
				new Builder()
						.on("elytra_info", "lookahead", 2.0)
						.on("item_counters", "items", ids("firework_rocket", "totem_of_undying", "golden_apple"))
						.on("target_hud")
						.on("health_armor", "only_low", true)
						.build(), ProfileRule.Trigger.GLIDING));
		list.add(new Preset("bow_pvp", "Bow / Crossbow", "Arrow paths, draw and load progress, ammunition and target distance.",
				new Builder()
						.on("trajectories")
						.on("weapon_info")
						.on("target_hud", "distance", true, "max_distance", 96.0)
						.on("item_counters", "items", ids("arrow", "spectral_arrow", "tipped_arrow", "firework_rocket"))
						.build(), ProfileRule.Trigger.BOW));
		return list;
	}

	private static JsonArray ids(String... names) {
		JsonArray array = new JsonArray();
		for (String name : names) {
			array.add("minecraft:" + name);
		}

		return array;
	}

	/** Builds profile module data: listed modules get the given on/off state and settings; all others stay at defaults (off). */
	private static final class Builder {
		private final JsonObject modules = new JsonObject();

		Builder on(String module, Object... settings) {
			return add(module, true, settings);
		}

		Builder off(String module, Object... settings) {
			return add(module, false, settings);
		}

		private Builder add(String module, boolean enabled, Object... settings) {
			JsonObject json = new JsonObject();
			json.addProperty("enabled", enabled);
			JsonObject values = new JsonObject();
			for (int i = 0; i + 1 < settings.length; i += 2) {
				String key = (String) settings[i];
				Object value = settings[i + 1];
				if (value instanceof Boolean b) values.addProperty(key, b);
				else if (value instanceof Number n) values.addProperty(key, n);
				else if (value instanceof JsonArray array) values.add(key, array);
				else values.addProperty(key, value.toString());
			}

			json.add("settings", values);
			modules.add(module, json);
			return this;
		}

		JsonObject build() {
			return modules;
		}
	}
}
