package io.github.theamethyx.femboyclient.core.runner.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * What a Java Runner snippet can read about the client.
 *
 * <p>Snippets run in a separate Java process, not inside Minecraft, so they cannot touch the game.
 * Instead the client sends a read-only snapshot when you press Run: versions, frame rate, where you are
 * connected, every module's state and settings, and recent diagnostic messages. Everything here comes
 * from that snapshot; nothing is live and nothing can be changed.
 */
public final class Femboy {
	private static Map<String, String> values = Map.of();

	private Femboy() {
	}

	/** Called by the runner worker before the snippet starts. */
	public static void load(Map<String, String> snapshot) {
		values = Collections.unmodifiableMap(new LinkedHashMap<>(snapshot));
	}

	/** The raw snapshot, key by key. */
	public static Map<String, String> snapshot() {
		return values;
	}

	public static String clientVersion() {
		return values.getOrDefault("client.version", "unknown");
	}

	public static String minecraftVersion() {
		return values.getOrDefault("minecraft.version", "unknown");
	}

	/** Java version of the game (the runner itself may use a different Java). */
	public static String gameJavaVersion() {
		return values.getOrDefault("java.version", "unknown");
	}

	public static int fps() {
		return Integer.parseInt(values.getOrDefault("fps", "0"));
	}

	/** "Singleplayer", "Server example.org:25565" or "No world loaded". */
	public static String connection() {
		return values.getOrDefault("connection", "unknown");
	}

	/** Module ids, in menu order. */
	public static List<String> modules() {
		List<String> ids = new ArrayList<>();
		for (String key : values.keySet()) {
			if (key.startsWith("module.") && key.endsWith(".name")) {
				ids.add(key.substring("module.".length(), key.length() - ".name".length()));
			}
		}

		return ids;
	}

	public static String moduleName(String module) {
		return require("module." + module + ".name", module);
	}

	public static boolean enabled(String module) {
		return Boolean.parseBoolean(require("module." + module + ".enabled", module));
	}

	public static boolean running(String module) {
		return Boolean.parseBoolean(require("module." + module + ".running", module));
	}

	/** A module's settings as shown in the menu (e.g. "64 blocks"), by setting id. */
	public static Map<String, String> settings(String module) {
		require("module." + module + ".name", module);
		String prefix = "setting." + module + ".";
		Map<String, String> settings = new TreeMap<>();
		values.forEach((key, value) -> {
			if (key.startsWith(prefix)) {
				settings.put(key.substring(prefix.length()), value);
			}
		});
		return settings;
	}

	public static String setting(String module, String setting) {
		String value = settings(module).get(setting);
		if (value == null) {
			throw new IllegalArgumentException("Module '" + module + "' has no setting '" + setting + "'. Its settings: " + settings(module).keySet());
		}

		return value;
	}

	/** Recent diagnostic messages, oldest first. */
	public static List<String> diagnostics() {
		List<String> messages = new ArrayList<>();
		for (int i = 0; values.containsKey("diag." + i); i++) {
			messages.add(values.get("diag." + i));
		}

		return messages;
	}

	/** Prints rows as an aligned text table. */
	public static void printTable(List<String> headers, List<? extends List<?>> rows) {
		int[] widths = new int[headers.size()];
		for (int c = 0; c < headers.size(); c++) {
			widths[c] = headers.get(c).length();
		}

		for (List<?> row : rows) {
			for (int c = 0; c < Math.min(row.size(), widths.length); c++) {
				widths[c] = Math.max(widths[c], String.valueOf(row.get(c)).length());
			}
		}

		System.out.println(format(headers, widths));
		StringBuilder rule = new StringBuilder();
		for (int width : widths) {
			rule.append("-".repeat(width)).append("  ");
		}

		System.out.println(rule.toString().stripTrailing());
		for (List<?> row : rows) {
			System.out.println(format(row, widths));
		}
	}

	private static String format(List<?> cells, int[] widths) {
		StringBuilder line = new StringBuilder();
		for (int c = 0; c < widths.length; c++) {
			String cell = c < cells.size() ? String.valueOf(cells.get(c)) : "";
			line.append(cell).append(" ".repeat(widths[c] - cell.length() + 2));
		}

		return line.toString().stripTrailing();
	}

	private static String require(String key, String module) {
		String value = values.get(key);
		if (value == null) {
			throw new IllegalArgumentException("No module '" + module + "'. Known modules: " + modules());
		}

		return value;
	}
}
