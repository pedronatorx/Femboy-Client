package io.github.theamethyx.femboyclient.core.setting;

import java.math.BigDecimal;
import java.math.RoundingMode;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

/**
 * A bounded number edited with a slider. Values are snapped to {@code step}; a step of 1 makes
 * this an integer setting.
 */
public final class NumberSetting extends Setting<Double> {
	private final double min;
	private final double max;
	private final double step;
	private final String unit;

	public NumberSetting(String id, String name, String description, double defaultValue, double min, double max, double step, String unit) {
		super(id, name, description, defaultValue);

		if (!(min < max) || !(step > 0) || step > max - min) {
			throw new IllegalArgumentException("Invalid range for setting '" + id + "'");
		}

		this.min = min;
		this.max = max;
		this.step = step;
		this.unit = unit == null ? "" : unit;
		requireValidDefault();
	}

	@Override
	public String validate(Double candidate) {
		if (candidate.isNaN() || candidate.isInfinite()) {
			return "must be a finite number";
		}

		if (candidate < min || candidate > max) {
			return "must be between " + format(min) + " and " + format(max);
		}

		return null;
	}

	/** Snaps an arbitrary number (e.g. a slider position) to the nearest allowed value and sets it. */
	public void setSnapped(double raw) {
		set(snap(raw));
	}

	public double snap(double raw) {
		if (Double.isNaN(raw)) {
			return get();
		}

		double clamped = Math.max(min, Math.min(max, raw));
		double steps = Math.round((clamped - min) / step);
		double snapped = min + steps * step;
		// Remove floating-point noise such as 0.30000000000000004 so saved files stay readable.
		snapped = new BigDecimal(snapped).setScale(decimals() + 2, RoundingMode.HALF_UP).doubleValue();
		return Math.max(min, Math.min(max, snapped));
	}

	/** Position of the current value on the slider track, from 0 to 1. */
	public double fraction() {
		return (get() - min) / (max - min);
	}

	@Override
	public JsonElement toJson() {
		return isInteger() ? new JsonPrimitive(intValue()) : new JsonPrimitive(get());
	}

	@Override
	protected Double parse(JsonElement json) {
		if (!json.isJsonPrimitive() || !json.getAsJsonPrimitive().isNumber()) {
			throw new IllegalArgumentException("expected a number");
		}

		double raw = json.getAsDouble();
		// In-range values written by hand may be off-step; snap those. Out-of-range values are
		// returned unchanged so validation reports them instead of silently clamping.
		return raw >= min && raw <= max ? snap(raw) : raw;
	}

	@Override
	public String displayValue() {
		return unit.isEmpty() ? format(get()) : format(get()) + " " + unit;
	}

	public String format(double number) {
		int decimals = decimals();
		return decimals == 0 ? Long.toString(Math.round(number)) : String.format(java.util.Locale.ROOT, "%." + decimals + "f", number);
	}

	private int decimals() {
		return Math.max(0, BigDecimal.valueOf(step).stripTrailingZeros().scale());
	}

	public boolean isInteger() {
		return step == Math.rint(step) && min == Math.rint(min);
	}

	public int intValue() {
		return (int) Math.round(get());
	}

	public double min() {
		return min;
	}

	public double max() {
		return max;
	}

	public double step() {
		return step;
	}

	public String unit() {
		return unit;
	}
}
