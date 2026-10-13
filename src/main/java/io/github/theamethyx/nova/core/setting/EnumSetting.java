package io.github.theamethyx.nova.core.setting;

import java.util.List;
import java.util.Locale;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

/** A choice from a fixed list, shown as a dropdown. Values are stored by enum constant name. */
public final class EnumSetting<E extends Enum<E>> extends Setting<E> {
	private final Class<E> type;
	private final List<E> options;

	public EnumSetting(String id, String name, String description, E defaultValue) {
		super(id, name, description, defaultValue);
		this.type = defaultValue.getDeclaringClass();
		this.options = List.of(type.getEnumConstants());
	}

	@Override
	public String validate(E candidate) {
		return type.isInstance(candidate) ? null : "is not one of the allowed options";
	}

	@Override
	public JsonElement toJson() {
		return new JsonPrimitive(get().name());
	}

	@Override
	protected E parse(JsonElement json) {
		if (!json.isJsonPrimitive() || !json.getAsJsonPrimitive().isString()) {
			throw new IllegalArgumentException("expected the name of an option");
		}

		String raw = json.getAsString().trim().toUpperCase(Locale.ROOT);
		for (E option : options) {
			if (option.name().equals(raw)) {
				return option;
			}
		}

		throw new IllegalArgumentException("'" + json.getAsString() + "' is not an option");
	}

	@Override
	public String displayValue() {
		return labelOf(get());
	}

	public List<E> options() {
		return options;
	}

	public void cycle(int direction) {
		int index = Math.floorMod(options.indexOf(get()) + direction, options.size());
		set(options.get(index));
	}

	public static String labelOf(Enum<?> value) {
		if (value instanceof Labeled labeled) {
			return labeled.label();
		}

		String lower = value.name().toLowerCase(Locale.ROOT).replace('_', ' ');
		return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
	}

	/** Implemented by enums that want a nicer dropdown label than their constant name. */
	public interface Labeled {
		String label();
	}
}
