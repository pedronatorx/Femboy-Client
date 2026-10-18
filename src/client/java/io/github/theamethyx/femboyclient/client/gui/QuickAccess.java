package io.github.theamethyx.femboyclient.client.gui;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import io.github.theamethyx.femboyclient.core.config.ConfigurationManager;
import io.github.theamethyx.femboyclient.core.module.Module;
import io.github.theamethyx.femboyclient.core.module.ModuleManager;

/**
 * Favorite and recently used modules, for Home, the command palette and the quick toggle wheel. Stored in the global
 * configuration (section "quick_access"), so they are the same in every profile.
 */
public final class QuickAccess {
	static final String SECTION = "quick_access";
	public static final int MAX_FAVORITES = 16;
	public static final int MAX_RECENT = 8;

	private final ConfigurationManager config;
	private final ModuleManager modules;
	private final List<String> favorites = new ArrayList<>();
	private final List<String> recent = new ArrayList<>();

	public QuickAccess(ConfigurationManager config, ModuleManager modules) {
		this.config = config;
		this.modules = modules;
		reload();
		config.addLoadListener(this::reload);
	}

	public void reload() {
		favorites.clear();
		recent.clear();
		if (config.section(SECTION) instanceof JsonObject json) {
			read(json.get("favorites"), favorites, MAX_FAVORITES);
			read(json.get("recent"), recent, MAX_RECENT);
		}
	}

	private void read(JsonElement json, List<String> into, int max) {
		if (json instanceof JsonArray array) {
			for (JsonElement element : array) {
				// Ids of modules this version does not have are kept, so they come back with a version that has them.
				if (element instanceof JsonPrimitive primitive && primitive.isString() && !into.contains(primitive.getAsString()) && into.size() < max) {
					into.add(primitive.getAsString());
				}
			}
		}
	}

	private void save() {
		JsonObject json = new JsonObject();
		JsonArray fav = new JsonArray();
		favorites.forEach(fav::add);
		JsonArray rec = new JsonArray();
		recent.forEach(rec::add);
		json.add("favorites", fav);
		json.add("recent", rec);
		config.setSection(SECTION, json);
	}

	public boolean isFavorite(Module module) {
		return favorites.contains(module.id());
	}

	/** @return whether it is a favorite now */
	public boolean toggleFavorite(Module module) {
		if (!favorites.remove(module.id())) {
			if (favorites.size() >= MAX_FAVORITES) {
				return false;
			}

			favorites.add(module.id());
		}

		save();
		return isFavorite(module);
	}

	/** Marks a module as just used (turned on or off, or its settings opened). */
	public void touch(Module module) {
		if (!recent.isEmpty() && recent.getFirst().equals(module.id())) {
			return;
		}

		recent.remove(module.id());
		recent.addFirst(module.id());
		while (recent.size() > MAX_RECENT) {
			recent.removeLast();
		}

		save();
	}

	public List<Module> favorites() {
		return resolve(favorites);
	}

	public List<Module> recent() {
		return resolve(recent);
	}

	private List<Module> resolve(List<String> ids) {
		List<Module> list = new ArrayList<>();
		for (String id : ids) {
			Module module = modules.get(id);
			if (module != null) {
				list.add(module);
			}
		}

		return list;
	}
}
