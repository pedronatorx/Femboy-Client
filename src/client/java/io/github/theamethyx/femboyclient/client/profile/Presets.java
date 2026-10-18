package io.github.theamethyx.femboyclient.client.profile;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import io.github.theamethyx.femboyclient.core.config.ProfileRule;

/**
 * Ready-made starting profiles for common PvP styles. Installing one creates an ordinary profile that can be
 * edited, copied, exported and reset like any other. They only turn on information and visual modules; automation
 * (Auto Attribute Swap, KillAura, Trigger Bot, Auto StunSlam, AutoTotem) is configured where it fits but left off.
 */
public final class Presets {
	/**
	 * A preset: the profile name it installs as, a title and description, the module data, and the item that suits it
	 * ({@code null} when no held item fits, e.g. Performance).
	 */
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
						.off("kill_aura", "range", 3.0, "timing_mode", "CHARGE", "charge", 100.0)
						.off("trigger_bot", "charge", 100.0)
						.build(), ProfileRule.Trigger.SWORD));
		list.add(new Preset("axe_pvp", "Axe PvP", "Shield state of your target, your own shield cooldown, armor and combat statistics. "
				+ "Auto Attribute Swap is set up for axe-against-shield but off.",
				new Builder()
						.on("target_hud", "shield", true)
						.on("weapon_info")
						.on("combat_info")
						.on("health_armor")
						.off("auto_attribute_swap", "axe_enabled", true, "axe_shield_only", true, "mace_enabled", false)
						.off("kill_aura", "range", 3.0, "charge", 100.0)
						.build(), ProfileRule.Trigger.AXE));
		list.add(new Preset("mace_pvp", "Mace PvP", "Fall distance, smash bonus, vertical speed and wind charges, with your target and totem count. "
				+ "Auto Attribute Swap is set up for the mace but off. Auto StunSlam is not available.",
				new Builder()
						.on("weapon_info")
						.on("movement_info", "speed", false)
						.on("target_hud")
						.on("item_counters", "items", ids("wind_charge", "totem_of_undying", "golden_apple", "ender_pearl"))
						.off("auto_attribute_swap", "mace_enabled", true, "mace_min_fall", 1.5, "axe_enabled", false)
						.off("auto_stunslam", "min_fall", 2.0, "delay", 0.0, "require_shield", true)
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
						.off("auto_stunslam", "min_fall", 2.0)
						.build(), ProfileRule.Trigger.SPEAR));
		list.add(new Preset("crystal_pvp", "Crystal PvP", "Crystal placement preview, explosion damage estimates, totems, crystals and armor durability.",
				new Builder()
						.on("explosion_info")
						.on("item_counters", "items", ids("totem_of_undying", "end_crystal", "obsidian", "golden_apple", "enchanted_golden_apple",
								"experience_bottle"), "refill_items", ids("totem_of_undying", "end_crystal", "obsidian"))
						.off("auto_totem", "delay", 0.0)
						.on("health_armor")
						.on("target_hud")
						.build(), ProfileRule.Trigger.END_CRYSTAL));
		list.add(new Preset("anchor_pvp", "Anchor PvP", "Anchor charges and whether it explodes here, explosion estimates, anchors, glowstone and totems.",
				new Builder()
						.on("explosion_info", "placements", false)
						.on("item_counters", "items", ids("respawn_anchor", "glowstone", "totem_of_undying", "golden_apple"),
								"refill_items", ids("respawn_anchor", "glowstone", "totem_of_undying"))
						.off("auto_totem", "delay", 0.0)
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
		list.add(new Preset("spear_pvp", "Spear PvP", "Spear charge windows and Lunge status, target distance, reach and the spear parts of "
				+ "Auto Attribute Swap (left off).",
				new Builder()
						.on("weapon_info", "axe", false, "mace", false, "ranged", false)
						.on("target_hud", "distance", true)
						.on("combat_info", "reach", true)
						.off("auto_attribute_swap", "spear_enabled", true, "axe_enabled", false, "mace_enabled", false)
						.build(), ProfileRule.Trigger.SPEAR));
		list.add(new Preset("survival_smp", "Survival / SMP", "Health, food and durability, coordinates, waypoints, death coordinates, "
				+ "shulker previews, Auto Tool and the inventory Sort button.",
				new Builder()
						.on("health_armor", "alerts", true)
						.on("coordinates_hud")
						.on("waypoints")
						.on("death_coordinates")
						.on("shulker_preview")
						.on("auto_tool")
						.on("inventory_sorter")
						.on("item_counters", "items", ids("golden_apple", "totem_of_undying", "ender_pearl", "torch"))
						.build(), null));
		list.add(new Preset("building", "Building", "Block highlight, coordinates with chunk position, chunk borders, light levels and zoom.",
				new Builder()
						.on("block_highlight")
						.on("coordinates_hud", "chunk", true)
						.on("chunk_borders")
						.on("light_level")
						.on("zoom")
						.build(), ProfileRule.Trigger.BLOCK));
		list.add(new Preset("mining", "Mining", "Auto Tool, light levels, a diamond and debris counter, durability and death coordinates. "
				+ "Block ESP is set up but left off (it runs only in singleplayer and on your own network).",
				new Builder()
						.on("auto_tool")
						.on("light_level")
						.on("block_counter")
						.on("health_armor", "only_low", true)
						.on("coordinates_hud")
						.on("death_coordinates")
						.off("block_esp", "radius", 4.0)
						.build(), ProfileRule.Trigger.PICKAXE));
		list.add(new Preset("exploration", "Exploration", "Coordinates, waypoints and trackers, death coordinates, movement and elytra info, zoom.",
				new Builder()
						.on("coordinates_hud")
						.on("waypoints")
						.on("trackers")
						.on("death_coordinates")
						.on("movement_info")
						.on("elytra_info")
						.on("zoom")
						.build(), null));
		list.add(new Preset("performance", "Performance", "Only the performance panel: frame rate, frame time, ping and memory.",
				new Builder()
						.on("performance_hud")
						.build(), null));
		list.add(new Preset("local_testing", "Local Testing", "For singleplayer and your test servers: hitboxes, combat info, target HUD and "
				+ "performance on; KillAura, Velocity, Reach and Flight set up but off.",
				new Builder()
						.on("hitboxes")
						.on("combat_info", "accuracy", true)
						.on("target_hud")
						.on("performance_hud")
						.off("kill_aura")
						.off("velocity")
						.off("reach")
						.off("flight")
						.build(), null));
		list.add(new Preset("bed_pvp", "Bed PvP", "Explosion damage estimates, health and armor, beds and totems counted; for the Nether and the End "
				+ "where beds explode.",
				new Builder()
						.on("explosion_info")
						.on("health_armor")
						.on("target_hud")
						.on("item_counters", "items", ids("red_bed", "white_bed", "totem_of_undying", "golden_apple"))
						.build(), null));
		list.add(new Preset("farming", "Farming", "Light levels for crops and mob spawning, coordinates, block highlight and seed and bone meal counts.",
				new Builder()
						.on("light_level")
						.on("coordinates_hud")
						.on("block_highlight")
						.on("item_counters", "items", ids("wheat_seeds", "bone_meal", "carrot", "potato"))
						.build(), null));
		list.add(new Preset("parkour", "Parkour", "Speed and height, keystrokes and exact coordinates for practising jumps.",
				new Builder()
						.on("movement_info")
						.on("keystrokes")
						.on("coordinates_hud")
						.build(), null));
		list.add(new Preset("speedrunning", "Speedrunning", "Coordinates with chunk position, movement, death coordinates, waypoints and the "
				+ "performance panel. Structure Finder needs singleplayer (it uses the world seed).",
				new Builder()
						.on("coordinates_hud")
						.on("movement_info")
						.on("death_coordinates")
						.on("waypoints")
						.on("performance_hud")
						.off("structure_finder")
						.build(), null));
		list.add(new Preset("recording", "Recording / Streaming", "A clean screen: only keystrokes, toasts for messages instead of chat lines, "
				+ "nothing else drawn.",
				new Builder()
						.on("keystrokes")
						.on("notifications")
						.build(), null));
		list.add(new Preset("developer", "Developer", "Performance panel, hitboxes, chunk borders and the module list, for testing the client itself.",
				new Builder()
						.on("performance_hud")
						.on("hitboxes")
						.on("chunk_borders")
						.on("module_list_hud")
						.build(), null));
		list.add(new Preset("authorized_testing", "Authorized Testing", "Like Local Testing, for servers in your own network (LAN or VPN). Sandbox "
				+ "modules run only there; on any other server they stay off, whatever the profile says.",
				new Builder()
						.on("hitboxes")
						.on("combat_info", "accuracy", true)
						.on("target_hud")
						.on("performance_hud")
						.on("entity_esp")
						.off("kill_aura")
						.off("trigger_bot")
						.off("velocity")
						.build(), null));
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
