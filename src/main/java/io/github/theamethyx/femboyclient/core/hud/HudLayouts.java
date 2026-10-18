package io.github.theamethyx.femboyclient.core.hud;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

import io.github.theamethyx.femboyclient.core.setting.EnumSetting;

/**
 * Named HUD layouts: for each HUD element (by module id) where it sits, its scale and opacity, whether it is hidden and
 * when it shows. One layout is active.
 *
 * <p>Positions are anchored, so they survive window resizes and GUI scale changes: an element is attached to the left,
 * center or right of the screen (and top, middle or bottom) with a pixel offset, and the matching point of the element is
 * placed there. A block on the right edge therefore stays on the right edge when it grows or the window widens. Layouts
 * saved before anchoring (fractions of the screen) are read as such and become anchored the next time the element is
 * moved.
 */
public final class HudLayouts {
	public static final int VERSION = 2;
	public static final String DEFAULT = "Default";
	public static final float MIN_SCALE = 0.5f;
	public static final float MAX_SCALE = 2.5f;
	public static final float MIN_OPACITY = 0.2f;
	private static final Pattern NAME = Pattern.compile("[A-Za-z0-9 _-]{1,24}");

	/** When an element is shown. */
	public enum Condition implements EnumSetting.Labeled {
		ALWAYS("Always"),
		IN_COMBAT("In combat"),
		OUT_OF_COMBAT("Out of combat"),
		HOLDING_WEAPON("Holding a weapon"),
		GLIDING("Gliding"),
		SNEAKING("Sneaking");

		private final String label;

		Condition(String label) {
			this.label = label;
		}

		@Override
		public String label() {
			return label;
		}
	}

	/**
	 * One element's placement. {@code ax}/{@code ay} are anchors (0 = left/top, 0.5 = center, 1 = right/bottom) or
	 * {@code NaN} when the element stays stacked in its corner; {@code dx}/{@code dy} the offset in GUI pixels.
	 * {@code legacyX}/{@code legacyY} hold a pre-anchoring position (top-left as a fraction of the screen), else {@code NaN}.
	 */
	public record Element(float ax, float ay, int dx, int dy, float legacyX, float legacyY, float scale, float opacity, boolean hidden,
			Condition when) {
		public static final Element DEFAULT = new Element(Float.NaN, Float.NaN, 0, 0, Float.NaN, Float.NaN, 1, 1, false, Condition.ALWAYS);

		public Element {
			scale = Math.clamp(Float.isNaN(scale) ? 1 : scale, MIN_SCALE, MAX_SCALE);
			opacity = Math.clamp(Float.isNaN(opacity) ? 1 : opacity, MIN_OPACITY, 1);
			when = when == null ? Condition.ALWAYS : when;
		}

		/** Whether the element has a position of its own (otherwise it is stacked in its corner). */
		public boolean placed() {
			return !Float.isNaN(ax) || !Float.isNaN(legacyX);
		}

		/** Whether nothing differs from the default, so the entry need not be stored. */
		public boolean isDefault() {
			return !placed() && scale == 1 && opacity == 1 && !hidden && when == Condition.ALWAYS;
		}

		/**
		 * Top-left corner on a screen of {@code screenWidth}x{@code screenHeight} for an element of {@code width}x{@code height}
		 * (already scaled), kept on screen.
		 */
		public int[] topLeft(int screenWidth, int screenHeight, int width, int height) {
			int x;
			int y;
			if (!Float.isNaN(ax)) {
				x = Math.round(ax * screenWidth + dx - ax * width);
				y = Math.round(ay * screenHeight + dy - ay * height);
			} else {
				x = Math.round(legacyX * screenWidth);
				y = Math.round(legacyY * screenHeight);
			}

			return new int[] {Math.clamp(x, 0, Math.max(0, screenWidth - width)), Math.clamp(y, 0, Math.max(0, screenHeight - height))};
		}

		/**
		 * This element moved so its top-left corner is at ({@code x}, {@code y}): anchored to the nearest third of the screen
		 * in each direction, so it keeps its place relative to that edge or the center.
		 */
		public Element movedTo(int x, int y, int width, int height, int screenWidth, int screenHeight) {
			float newAx = anchor(x + width / 2f, screenWidth);
			float newAy = anchor(y + height / 2f, screenHeight);
			int newDx = Math.round(x + newAx * width - newAx * screenWidth);
			int newDy = Math.round(y + newAy * height - newAy * screenHeight);
			return new Element(newAx, newAy, newDx, newDy, Float.NaN, Float.NaN, scale, opacity, hidden, when);
		}

		private static float anchor(float center, int size) {
			return center < size / 3f ? 0 : center > size * 2 / 3f ? 1 : 0.5f;
		}

		/** Back to its corner, keeping scale, opacity, visibility and condition. */
		public Element unplaced() {
			return new Element(Float.NaN, Float.NaN, 0, 0, Float.NaN, Float.NaN, scale, opacity, hidden, when);
		}

		public Element withScale(float newScale) {
			return new Element(ax, ay, dx, dy, legacyX, legacyY, newScale, opacity, hidden, when);
		}

		public Element withOpacity(float newOpacity) {
			return new Element(ax, ay, dx, dy, legacyX, legacyY, scale, newOpacity, hidden, when);
		}

		public Element withHidden(boolean newHidden) {
			return new Element(ax, ay, dx, dy, legacyX, legacyY, scale, opacity, newHidden, when);
		}

		public Element withCondition(Condition newWhen) {
			return new Element(ax, ay, dx, dy, legacyX, legacyY, scale, opacity, hidden, newWhen);
		}

		JsonObject toJson() {
			JsonObject json = new JsonObject();
			if (!Float.isNaN(ax)) {
				json.addProperty("ax", ax);
				json.addProperty("ay", ay);
				json.addProperty("dx", dx);
				json.addProperty("dy", dy);
			} else if (!Float.isNaN(legacyX)) {
				json.addProperty("x", legacyX);
				json.addProperty("y", legacyY);
			}

			if (scale != 1) json.addProperty("scale", scale);
			if (opacity != 1) json.addProperty("opacity", opacity);
			if (hidden) json.addProperty("hidden", true);
			if (when != Condition.ALWAYS) json.addProperty("when", when.name());
			return json;
		}

		static Element fromJson(JsonElement json, String id, List<String> warnings) {
			// Format 1 (0.6-0.9): [x, y] as fractions of the screen.
			if (json instanceof JsonArray array) {
				if (array.size() == 2 && array.get(0) instanceof JsonPrimitive x && x.isNumber() && array.get(1) instanceof JsonPrimitive y && y.isNumber()
						&& fraction(x.getAsFloat()) && fraction(y.getAsFloat())) {
					return new Element(Float.NaN, Float.NaN, 0, 0, x.getAsFloat(), y.getAsFloat(), 1, 1, false, Condition.ALWAYS);
				}

				warnings.add("HUD element '" + id + "': the position was not two numbers between 0 and 1; it goes back to its corner.");
				return null;
			}

			if (!(json instanceof JsonObject object)) {
				warnings.add("HUD element '" + id + "' was not an object; it goes back to its corner.");
				return null;
			}

			float ax = Float.NaN, ay = Float.NaN, legacyX = Float.NaN, legacyY = Float.NaN;
			int dx = 0, dy = 0;
			if (number(object, "ax") && number(object, "ay") && anchorValue(object.get("ax").getAsFloat()) && anchorValue(object.get("ay").getAsFloat())) {
				ax = object.get("ax").getAsFloat();
				ay = object.get("ay").getAsFloat();
				dx = number(object, "dx") ? Math.clamp(object.get("dx").getAsInt(), -10_000, 10_000) : 0;
				dy = number(object, "dy") ? Math.clamp(object.get("dy").getAsInt(), -10_000, 10_000) : 0;
			} else if (number(object, "x") && number(object, "y") && fraction(object.get("x").getAsFloat()) && fraction(object.get("y").getAsFloat())) {
				legacyX = object.get("x").getAsFloat();
				legacyY = object.get("y").getAsFloat();
			} else if (object.has("ax") || object.has("x")) {
				warnings.add("HUD element '" + id + "': the position is invalid; it goes back to its corner.");
			}

			float scale = number(object, "scale") ? object.get("scale").getAsFloat() : 1;
			float opacity = number(object, "opacity") ? object.get("opacity").getAsFloat() : 1;
			boolean hidden = object.get("hidden") instanceof JsonPrimitive primitive && primitive.isBoolean() && primitive.getAsBoolean();
			Condition when = Condition.ALWAYS;
			if (object.get("when") instanceof JsonPrimitive primitive && primitive.isString()) {
				try {
					when = Condition.valueOf(primitive.getAsString());
				} catch (IllegalArgumentException e) {
					warnings.add("HUD element '" + id + "': unknown condition '" + primitive.getAsString() + "'; it shows always.");
				}
			}

			return new Element(ax, ay, dx, dy, legacyX, legacyY, scale, opacity, hidden, when);
		}

		private static boolean number(JsonObject object, String key) {
			return object.get(key) instanceof JsonPrimitive primitive && primitive.isNumber() && Float.isFinite(primitive.getAsFloat());
		}

		private static boolean fraction(float value) {
			return value >= 0 && value <= 1;
		}

		private static boolean anchorValue(float value) {
			return value == 0 || value == 0.5f || value == 1;
		}
	}

	/** A named set of element placements. */
	public static final class Layout {
		private final Map<String, Element> elements = new LinkedHashMap<>();

		public Element get(String id) {
			return elements.getOrDefault(id, Element.DEFAULT);
		}

		public void put(String id, Element element) {
			if (element.isDefault()) {
				elements.remove(id);
			} else {
				elements.put(id, element);
			}
		}

		public Map<String, Element> elements() {
			return java.util.Collections.unmodifiableMap(elements);
		}

		/** Number of elements with a position of their own. */
		public int placedCount() {
			return (int) elements.values().stream().filter(Element::placed).count();
		}

		Layout copy() {
			Layout copy = new Layout();
			copy.elements.putAll(elements);
			return copy;
		}

		public JsonObject toJson() {
			JsonObject json = new JsonObject();
			JsonObject items = new JsonObject();
			elements.forEach((id, element) -> items.add(id, element.toJson()));
			json.add("elements", items);
			return json;
		}

		static Layout fromJson(JsonObject json, List<String> warnings) {
			Layout layout = new Layout();
			JsonObject items = json.get("elements") instanceof JsonObject object ? object : new JsonObject();
			for (Map.Entry<String, JsonElement> entry : items.entrySet()) {
				Element element = Element.fromJson(entry.getValue(), entry.getKey(), warnings);
				if (element != null) {
					layout.put(entry.getKey(), element);
				}
			}

			return layout;
		}
	}

	private final Map<String, Layout> layouts = new LinkedHashMap<>();
	private String active = DEFAULT;

	public HudLayouts() {
		layouts.put(DEFAULT, new Layout());
	}

	public static boolean isValidName(String name) {
		return name != null && NAME.matcher(name).matches() && !name.isBlank();
	}

	public Layout active() {
		return layouts.get(active);
	}

	public String activeName() {
		return active;
	}

	public List<String> names() {
		return List.copyOf(layouts.keySet());
	}

	public Layout layout(String name) {
		return layouts.get(name);
	}

	/** @return {@code null} on success, otherwise a readable reason */
	public String setActive(String name) {
		if (!layouts.containsKey(name)) {
			return "There is no layout named '" + name + "'.";
		}

		active = name;
		return null;
	}

	/** Creates a layout as a copy of the active one and makes it active. @return {@code null} or a readable reason */
	public String create(String name) {
		String problem = checkNewName(name);
		if (problem != null) {
			return problem;
		}

		layouts.put(name, active().copy());
		active = name;
		return null;
	}

	public String rename(String from, String to) {
		if (!layouts.containsKey(from)) {
			return "There is no layout named '" + from + "'.";
		}

		if (from.equals(to)) {
			return null;
		}

		String problem = checkNewName(to);
		if (problem != null) {
			return problem;
		}

		Map<String, Layout> copy = new LinkedHashMap<>();
		layouts.forEach((name, layout) -> copy.put(name.equals(from) ? to : name, layout));
		layouts.clear();
		layouts.putAll(copy);
		if (active.equals(from)) {
			active = to;
		}

		return null;
	}

	public String delete(String name) {
		if (!layouts.containsKey(name)) {
			return "There is no layout named '" + name + "'.";
		}

		if (layouts.size() == 1) {
			return "The last layout cannot be deleted.";
		}

		layouts.remove(name);
		if (active.equals(name)) {
			active = layouts.keySet().iterator().next();
		}

		return null;
	}

	private String checkNewName(String name) {
		if (!isValidName(name)) {
			return "Use 1-24 letters, digits, spaces, '-' or '_'.";
		}

		for (String existing : layouts.keySet()) {
			if (existing.equalsIgnoreCase(name)) {
				return "A layout named '" + existing + "' already exists.";
			}
		}

		return null;
	}

	/** Replaces the active layout's elements (undo/redo). */
	public void replaceActive(Layout layout) {
		layouts.put(active, layout.copy());
	}

	/** A copy of the active layout (for undo/redo snapshots). */
	public Layout snapshot() {
		return active().copy();
	}

	public JsonObject toJson() {
		JsonObject json = new JsonObject();
		json.addProperty("version", VERSION);
		json.addProperty("active", active);
		JsonObject all = new JsonObject();
		layouts.forEach((name, layout) -> all.add(name, layout.toJson()));
		json.add("layouts", all);
		return json;
	}

	/**
	 * Reads saved layouts. Accepts the current format and the 0.6-0.9 format (an object of module id to {@code [x, y]}),
	 * which becomes the "Default" layout. Invalid entries are skipped and described in {@code warnings}.
	 */
	public static HudLayouts fromJson(JsonElement json, List<String> warnings) {
		HudLayouts result = new HudLayouts();
		if (!(json instanceof JsonObject object)) {
			return result;
		}

		if (!(object.get("layouts") instanceof JsonObject all)) {
			// The old format: the whole object is one layout of [x, y] pairs.
			JsonObject wrapped = new JsonObject();
			wrapped.add("elements", object);
			result.layouts.put(DEFAULT, Layout.fromJson(wrapped, warnings));
			return result;
		}

		result.layouts.clear();
		for (Map.Entry<String, JsonElement> entry : all.entrySet()) {
			if (!isValidName(entry.getKey()) || !(entry.getValue() instanceof JsonObject layoutJson)) {
				warnings.add("HUD layout '" + entry.getKey() + "' was skipped (invalid name or data).");
				continue;
			}

			result.layouts.put(entry.getKey(), Layout.fromJson(layoutJson, warnings));
		}

		if (result.layouts.isEmpty()) {
			result.layouts.put(DEFAULT, new Layout());
		}

		String wanted = object.get("active") instanceof JsonPrimitive primitive && primitive.isString() ? primitive.getAsString() : DEFAULT;
		result.active = result.layouts.containsKey(wanted) ? wanted : result.layouts.keySet().iterator().next();
		return result;
	}

	/** One layout as shareable JSON text. */
	public String export(String name) {
		Layout layout = layouts.get(name);
		if (layout == null) {
			return null;
		}

		JsonObject json = layout.toJson();
		json.addProperty("femboyClientHudLayout", name);
		json.addProperty("version", VERSION);
		return json.toString();
	}

	/** Result of {@link #importLayout}: {@code error} is null on success. */
	public record ImportResult(String error, String name, List<String> warnings) {
	}

	/**
	 * Adds a layout from exported text under a free name based on the exported one (or "Imported"), and makes it active.
	 * Invalid entries are skipped and listed.
	 */
	public ImportResult importLayout(String text) {
		JsonObject json;
		try {
			JsonElement parsed = JsonParser.parseString(text == null ? "" : text.strip());
			if (!(parsed instanceof JsonObject object) || !(object.get("elements") instanceof JsonObject)) {
				return new ImportResult("This is not a Femboy Client HUD layout (no 'elements').", null, List.of());
			}

			json = object;
		} catch (JsonParseException e) {
			return new ImportResult("This is not valid JSON: " + e.getMessage(), null, List.of());
		}

		List<String> warnings = new ArrayList<>();
		Layout layout = Layout.fromJson(json, warnings);
		String base = json.get("femboyClientHudLayout") instanceof JsonPrimitive primitive && primitive.isString() && isValidName(primitive.getAsString())
				? primitive.getAsString() : "Imported";
		String name = base;
		for (int i = 2; checkNewName(name) != null; i++) {
			String suffix = " " + i;
			name = base.substring(0, Math.min(base.length(), 24 - suffix.length())) + suffix;
		}

		layouts.put(name, layout);
		active = name;
		return new ImportResult(null, name, List.copyOf(warnings));
	}
}
