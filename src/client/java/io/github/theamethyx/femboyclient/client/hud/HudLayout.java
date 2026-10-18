package io.github.theamethyx.femboyclient.client.hud;

import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;

import io.github.theamethyx.femboyclient.core.config.ConfigurationManager;
import io.github.theamethyx.femboyclient.core.hud.HudLayouts;
import io.github.theamethyx.femboyclient.core.hud.UndoHistory;

/**
 * The HUD layouts (see {@link HudLayouts}) stored in the configuration: element positions, scale, opacity, visibility and
 * visibility conditions, in several named layouts. Each profile stores its own copy, so switching profiles switches the
 * layout; this reloads whenever a profile is loaded. Changes made in the HUD editor can be undone.
 */
public final class HudLayout {
	public static final String SECTION = "hud_layout";

	private final ConfigurationManager config;
	private final UndoHistory<HudLayouts.Layout> history = new UndoHistory<>(50);
	private HudLayouts layouts = new HudLayouts();
	private List<String> lastWarnings = List.of();
	private int version;

	public HudLayout(ConfigurationManager config) {
		this.config = config;
		reload();
		config.addLoadListener(this::reload);
	}

	/** Reads the saved layouts; invalid entries are skipped and listed in {@link #lastWarnings()}. */
	public void reload() {
		List<String> warnings = new ArrayList<>();
		layouts = HudLayouts.fromJson(config.section(SECTION), warnings);
		lastWarnings = List.copyOf(warnings);
		history.clear();
		version++;
	}

	public List<String> lastWarnings() {
		return lastWarnings;
	}

	/** Increases whenever anything changes, so caches can tell. */
	public int version() {
		return version;
	}

	public HudLayouts layouts() {
		return layouts;
	}

	/** The active layout's entry for an element (defaults when it has none). */
	public HudLayouts.Element element(String moduleId) {
		return layouts.active().get(moduleId);
	}

	/** Records the current state for undo; call once before a change (or the start of a drag). */
	public void checkpoint() {
		history.record(layouts.snapshot());
	}

	/** Changes one element of the active layout and saves. Call {@link #checkpoint()} first to make it undoable. */
	public void update(String moduleId, UnaryOperator<HudLayouts.Element> change) {
		layouts.active().put(moduleId, change.apply(element(moduleId)));
		save();
	}

	/** Places an element's top-left corner at GUI coordinates, anchored to the nearest screen third. */
	public void placeAt(String moduleId, int x, int y, int width, int height, int screenWidth, int screenHeight) {
		update(moduleId, element -> element.movedTo(x, y, width, height, screenWidth, screenHeight));
	}

	/** Puts an element back in its corner (scale, opacity and visibility stay). */
	public void reset(String moduleId) {
		update(moduleId, HudLayouts.Element::unplaced);
	}

	/** Clears the active layout: every element back in its corner, unscaled, visible, always shown. */
	public void resetAll() {
		layouts.replaceActive(new HudLayouts.Layout());
		save();
	}

	public boolean undo() {
		HudLayouts.Layout previous = history.undo(layouts.snapshot());
		if (previous == null) {
			return false;
		}

		layouts.replaceActive(previous);
		save();
		return true;
	}

	public boolean redo() {
		HudLayouts.Layout next = history.redo(layouts.snapshot());
		if (next == null) {
			return false;
		}

		layouts.replaceActive(next);
		save();
		return true;
	}

	public boolean canUndo() {
		return history.canUndo();
	}

	public boolean canRedo() {
		return history.canRedo();
	}

	/** Switching, creating, renaming or deleting layouts: the undo history belongs to one layout, so it starts over. */
	public String changeLayouts(java.util.function.Function<HudLayouts, String> change) {
		String problem = change.apply(layouts);
		if (problem == null) {
			history.clear();
			save();
		}

		return problem;
	}

	/** Number of elements in the active layout with a position of their own. */
	public int size() {
		return layouts.active().placedCount();
	}

	public void save() {
		version++;
		config.setSection(SECTION, layouts.toJson());
	}
}
