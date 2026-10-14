package io.github.theamethyx.femboyclient.core.setting;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;

/**
 * A set of registry identifiers such as {@code minecraft:zombie}, edited as a grouped checklist.
 *
 * <p>Values are stable registry ids, never translated names. Ids that are not currently offered (for
 * example from a mod that is not installed) are kept, so switching mods on and off never loses a choice.
 * The available choices come from the client at runtime through {@link #choices(Supplier)}.
 */
public final class IdSetSetting extends Setting<Set<String>> {
	private static final Pattern ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");

	/** One selectable id with a readable label, shown under {@code group}. */
	public record Choice(String id, String label, String group) {
	}

	private Supplier<List<Choice>> choices = List::of;

	public IdSetSetting(String id, String name, String description, Set<String> defaultIds) {
		super(id, name, description, Collections.unmodifiableSet(new TreeSet<>(defaultIds)));
		requireValidDefault();
	}

	/** Sets where the GUI gets its choices; called by the client module that owns this setting. */
	public IdSetSetting choices(Supplier<List<Choice>> choices) {
		this.choices = choices;
		return this;
	}

	public List<Choice> choices() {
		return choices.get();
	}

	@Override
	public String validate(Set<String> candidate) {
		for (String id : candidate) {
			if (id == null || !ID.matcher(id).matches()) {
				return "'" + id + "' is not a registry id like minecraft:zombie";
			}
		}

		return null;
	}

	@Override
	public JsonElement toJson() {
		JsonArray array = new JsonArray();
		get().forEach(array::add);
		return array;
	}

	@Override
	protected Set<String> parse(JsonElement json) {
		if (!json.isJsonArray()) {
			throw new IllegalArgumentException("expected a list of ids");
		}

		Set<String> ids = new TreeSet<>();
		for (JsonElement element : json.getAsJsonArray()) {
			if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
				throw new IllegalArgumentException("expected only id strings");
			}

			ids.add(element.getAsString().trim());
		}

		return Collections.unmodifiableSet(ids);
	}

	@Override
	public String displayValue() {
		return get().size() + " selected";
	}

	public boolean contains(String id) {
		return get().contains(id);
	}

	/** Adds or removes one id. */
	public void toggle(String id) {
		setAll(List.of(id), !contains(id));
	}

	/** Adds or removes several ids at once (one change notification). */
	public void setAll(List<String> ids, boolean selected) {
		Set<String> next = new TreeSet<>(get());
		if (selected) {
			next.addAll(ids);
		} else {
			next.removeAll(ids);
		}

		set(Collections.unmodifiableSet(next));
	}

	/** Group names in the order they first appear in the choices. */
	public List<String> groups() {
		Set<String> groups = new LinkedHashSet<>();
		choices().forEach(choice -> groups.add(choice.group()));
		return new ArrayList<>(groups);
	}
}
