package io.github.theamethyx.femboyclient.core.setting;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.regex.Pattern;

import com.google.gson.JsonElement;

/**
 * A single validated, persistable, user-editable value.
 *
 * <p>Every write goes through {@link #validate(Object)}, so a setting can never hold a value
 * that its UI control or a hand-edited config file could not legitimately produce.
 */
public abstract class Setting<T> {
	private static final Pattern ID_PATTERN = Pattern.compile("[a-z0-9_]{1,48}");

	private final String id;
	private final String name;
	private final String description;
	private final T defaultValue;
	private final List<Consumer<T>> listeners = new ArrayList<>();
	private BooleanSupplier visibleWhen = () -> true;
	private String section;
	private boolean listed = true;
	private T value;

	protected Setting(String id, String name, String description, T defaultValue) {
		if (!ID_PATTERN.matcher(id).matches()) {
			throw new IllegalArgumentException("Setting id must match " + ID_PATTERN + ": " + id);
		}

		this.id = id;
		this.name = Objects.requireNonNull(name);
		this.description = Objects.requireNonNull(description);
		this.defaultValue = Objects.requireNonNull(defaultValue);
		this.value = defaultValue;
	}

	/**
	 * Checks a candidate value.
	 *
	 * @return {@code null} if the value is acceptable, otherwise a human-readable reason
	 */
	public abstract String validate(T candidate);

	/** Converts the current value to JSON for the config file. */
	public abstract JsonElement toJson();

	/**
	 * Parses a JSON value. Implementations throw {@link IllegalArgumentException} with a readable
	 * message when the JSON has the wrong shape.
	 */
	protected abstract T parse(JsonElement json);

	/** Subclasses call this once their own constraints are initialized. */
	protected final void requireValidDefault() {
		String problem = validate(defaultValue);
		if (problem != null) {
			throw new IllegalArgumentException("Default value of setting '" + id + "' is invalid: " + problem);
		}
	}

	/** Short text used by summaries and diagnostics, e.g. {@code "12.5 blocks"}. */
	public String displayValue() {
		return String.valueOf(value);
	}

	/**
	 * Sets the value if it is valid.
	 *
	 * @return {@code null} on success, otherwise the reason the value was rejected
	 */
	public final String set(T candidate) {
		if (candidate == null) {
			return "A value is required.";
		}

		String problem = validate(candidate);
		if (problem != null) {
			return problem;
		}

		if (!candidate.equals(value)) {
			value = candidate;
			for (Consumer<T> listener : List.copyOf(listeners)) {
				listener.accept(candidate);
			}
		}

		return null;
	}

	/**
	 * Loads a value from config JSON. Invalid or malformed input leaves the default in place.
	 *
	 * @return {@code null} on success, otherwise a warning suitable for the config report
	 */
	public final String load(JsonElement json) {
		T parsed;

		try {
			parsed = parse(json);
		} catch (RuntimeException e) {
			reset();
			return "'" + name + "' had an unreadable value (" + e.getMessage() + "); using the default " + displayValue() + ".";
		}

		String problem = set(parsed);
		if (problem != null) {
			reset();
			return "'" + name + "' had an invalid value (" + problem + "); using the default " + displayValue() + ".";
		}

		return null;
	}

	/**
	 * Checks a config value without applying it (used to validate imported profiles).
	 *
	 * @return {@code null} if {@link #load(JsonElement)} would accept it, otherwise the reason
	 */
	public final String check(JsonElement json) {
		T parsed;
		try {
			parsed = parse(json);
		} catch (RuntimeException e) {
			return "'" + name + "' has an unreadable value (" + e.getMessage() + ")";
		}

		String problem = parsed == null ? "A value is required." : validate(parsed);
		return problem == null ? null : "'" + name + "' has an invalid value (" + problem + ")";
	}

	public final void reset() {
		set(defaultValue);
	}

	public final boolean isDefault() {
		return value.equals(defaultValue);
	}

	public final void onChange(Consumer<T> listener) {
		listeners.add(listener);
	}

	/** Hides the control in the GUI unless the condition holds (e.g. a sub-option of a toggle). */
	@SuppressWarnings("unchecked")
	public final <S extends Setting<T>> S visibleWhen(BooleanSupplier condition) {
		this.visibleWhen = Objects.requireNonNull(condition);
		return (S) this;
	}

	public final boolean isVisible() {
		return visibleWhen.getAsBoolean();
	}

	/** Leaves the setting out of the generic settings list; for settings a module shows in its own panel. */
	@SuppressWarnings("unchecked")
	public final <S extends Setting<T>> S panelOnly() {
		this.listed = false;
		return (S) this;
	}

	public final boolean isListed() {
		return listed;
	}

	/** Heading this setting is listed under in the GUI, or {@code null}. Set by {@link ModuleSettings#section}. */
	public final String section() {
		return section;
	}

	final void setSection(String section) {
		this.section = section;
	}

	public final String id() {
		return id;
	}

	public final String name() {
		return name;
	}

	public final String description() {
		return description;
	}

	public final T defaultValue() {
		return defaultValue;
	}

	public final T get() {
		return value;
	}
}
