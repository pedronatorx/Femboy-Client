package io.github.theamethyx.femboyclient.client.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The built-in help: short guides and a glossary, written for someone who has never used a client. Shown on Settings ›
 * Help and found by the search. Plain text only, so it can be searched and wrapped anywhere.
 */
public final class HelpTopics {
	private HelpTopics() {
	}

	/** A guide ({@code term == false}) or a glossary entry. */
	public record Topic(String title, String body, boolean term) {
		/** Whether every word of {@code query} appears in the title or the text. */
		public boolean matches(String query) {
			String haystack = (title + " " + body).toLowerCase(Locale.ROOT);
			for (String word : query.toLowerCase(Locale.ROOT).trim().split("\\s+")) {
				if (!word.isEmpty() && !haystack.contains(word)) {
					return false;
				}
			}

			return true;
		}
	}

	public static final List<Topic> GUIDES = List.of(
			new Topic("Getting started",
					"Open this menu with Right Shift. The sections on the left group the modules: Combat, Movement, Visuals, Inventory, "
							+ "World and Automation. Click a module to see what it does and change its settings; the switch next to it "
							+ "turns it on or off. Everything you change is saved right away to the active profile.", false),
			new Topic("Finding things",
					"Press Ctrl+K (or click Search) to search modules, settings, profiles, HUD elements, help and actions. Enter opens "
							+ "the result, Tab turns a module on or off. On a section page, the Show menu narrows the list to modules "
							+ "that are on, your favorites, modules with a key, or modules that need attention.", false),
			new Topic("Module status",
					"Every module shows its state in words: On; Off; Waiting (it needs something, such as a loaded world); Not "
							+ "available here (for example singleplayer only); Reduced (slowed down by the performance mode); On, limited "
							+ "(a feature needs another module); Restarting (it hit an error and restarts by itself); Error (it was "
							+ "turned off after repeated errors). The line at the bottom of the sidebar says when a module needs a look.", false),
			new Topic("Keys",
					"Each module can have a key that turns it on or off while you play: open the module, click its key field and "
							+ "press a key (Backspace removes it). Toggle switches it with each press; Hold keeps it on only while you hold "
							+ "the key. The menu key, the search key, the quick toggle wheel and the emergency off key are in Minecraft's "
							+ "Options › Controls › Key Binds under Femboy Client.", false),
			new Topic("Favorites and the quick wheel",
					"Click the star next to a module's name to make it a favorite. Favorites appear on Home, first in the search and on "
							+ "the quick toggle wheel (bind its key in Minecraft's controls; hold the key, point, release).", false),
			new Topic("Profiles",
					"A profile is a complete set of module choices: which modules are on, every setting, every key, the HUD layout, and "
							+ "the look and performance mode. Switching profiles saves the current one first. Use profiles for different "
							+ "game modes or servers. Ready-made presets create a new profile you can change. Before switching, Preview "
							+ "lists exactly what would change.", false),
			new Topic("Backups and restore points",
					"A restore point is a copy of all settings and profiles. One is made automatically before risky changes (a restore, "
							+ "a full reset, replacing a profile with a preset, a format update); you can make one yourself in Settings › "
							+ "General. Restoring one first makes a new restore point, so a restore can itself be undone.", false),
			new Topic("The HUD editor",
					"Open it from Home (Edit HUD) or Settings › General. Drag an element to move it; it snaps to edges, the center and "
							+ "other elements (hold Shift to place freely). Select one to change its size and opacity, hide it, or, under "
							+ "More, choose when it shows and align it. Ctrl+Z undoes. Several layouts can be kept.", false),
			new Topic("ESP and target lines",
					"ESP modules highlight things through walls or at a distance: Entity ESP (players and mobs), Block ESP (chosen "
							+ "blocks, such as ores), Storage ESP (chests and other containers). All of them are organized the same way: "
							+ "Targets (what to highlight and how far), Highlight (outline, fill), Colors, Labels, Target lines, and "
							+ "Advanced (update rates and limits). A preview at the top shows how your choices look.", false),
			new Topic("Performance modes",
					"Performance does less work per frame for the highest frame rate. Balanced uses your settings as they are. Low "
							+ "impact slows updates down further and pauses modules that repeat actions many times a second. Settings › "
							+ "Performance shows what each module costs.", false),
			new Topic("Help mode",
					"With help mode on, every setting shows its explanation, the allowed range and the default under its name. Turn it "
							+ "off for a compact list (explanations then appear when you hover). Toggle it with the i next to a module's "
							+ "name or in Settings › General.", false),
			new Topic("Where automation may be used",
					"Modules of the kind \"acts for you\" (the Automation section) are meant for singleplayer and servers where you are "
							+ "allowed to use them. Many servers forbid them; using them there can get you banned. Femboy Client does not "
							+ "hide them from anti-cheat systems.", false),
			new Topic("Something is not working",
					"Open the module: its status line says what is wrong and what to do. Settings › Developer lists every module's "
							+ "health and its last error. A module that fails is paused and retried by itself; after three failures in a "
							+ "minute it is turned off. Settings › Performance can export a diagnostic report; Settings › General restores a backup.", false));

	public static final List<Topic> GLOSSARY = List.of(
			new Topic("Module", "One feature of the client that you can turn on or off, such as Coordinates or Entity ESP.", true),
			new Topic("ESP", "Highlights that show things you would not see otherwise, such as entities behind walls. Short for "
					+ "\"extra-sensory perception\".", true),
			new Topic("HUD", "Heads-up display: the information drawn over the game while you play, such as coordinates.", true),
			new Topic("Target line", "A line from your crosshair to a highlighted target, so you can see which way it is.", true),
			new Topic("Tracer", "Another name for a target line.", true),
			new Topic("Profile", "A saved set of module choices, settings, keys, HUD layout and look. See the Profiles guide.", true),
			new Topic("Preset", "A ready-made profile for a common play style. Installing one creates a normal profile.", true),
			new Topic("Restore point", "A copy of all settings and profiles that you can go back to.", true),
			new Topic("Toggle / Hold", "Key modes: Toggle switches a module with each press, Hold keeps it on only while the key is held.", true),
			new Topic("Kind: shows information", "The module only shows information the game already gave you.", true),
			new Topic("Kind: assists", "The module calculates and reminds, but never acts for you.", true),
			new Topic("Kind: acts for you", "The module performs actions for you, within vanilla rules. Only use it where allowed.", true),
			new Topic("Sandbox", "A module that only works in test environments and singleplayer.", true),
			new Topic("Low impact", "The performance mode that does the least work; some modules pause in it.", true),
			new Topic("Environment", "Where you are playing: singleplayer, a server, or no world. Some modules only work in some places.", true),
			new Topic("Snapping", "In the HUD editor: elements line up with screen edges, the center and each other while you drag.", true),
			new Topic("Opacity", "How solid something is drawn: 100% is fully solid, lower values are see-through.", true),
			new Topic("Through walls", "Highlights are drawn even when blocks are in the way.", true),
			new Topic("Line of sight", "Only targets with nothing between them and your eyes.", true),
			new Topic("Rule", "An automatic \"when this happens, do that\" you set up in Settings › Rules.", true),
			new Topic("Timeline", "A list of what happened this session (profile changes, deaths, markers), in Settings › Timeline.", true));

	/** Every topic: guides first, then the glossary. */
	public static List<Topic> all() {
		List<Topic> all = new ArrayList<>(GUIDES);
		all.addAll(GLOSSARY);
		return all;
	}

	/** Topics matching a search, guides first; all of them for an empty query. */
	public static List<Topic> search(String query) {
		if (query == null || query.isBlank()) {
			return all();
		}

		return all().stream().filter(topic -> topic.matches(query)).toList();
	}
}
