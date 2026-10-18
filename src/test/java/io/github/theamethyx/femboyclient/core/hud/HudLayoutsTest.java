package io.github.theamethyx.femboyclient.core.hud;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

class HudLayoutsTest {
	@Test
	void anElementOnTheRightStaysOnTheRightWhenTheScreenOrElementGrows() {
		HudLayouts.Element placed = HudLayouts.Element.DEFAULT.movedTo(380, 10, 16, 20, 400, 300);

		assertEquals(1f, placed.ax());
		assertEquals(0f, placed.ay());
		assertArrayEquals(new int[] {380, 10}, placed.topLeft(400, 300, 16, 20), "the same place on the same screen");
		assertArrayEquals(new int[] {780, 10}, placed.topLeft(800, 300, 16, 20), "4 px from the right edge on a wider screen");
		assertArrayEquals(new int[] {364, 10}, placed.topLeft(400, 300, 32, 20), "a wider element grows to the left");
	}

	@Test
	void centeredElementsStayCenteredAndPositionsStayOnScreen() {
		HudLayouts.Element middle = HudLayouts.Element.DEFAULT.movedTo(190, 140, 20, 20, 400, 300);
		assertEquals(0.5f, middle.ax());
		assertEquals(0.5f, middle.ay());
		assertArrayEquals(new int[] {390, 290}, middle.topLeft(800, 600, 20, 20));
		assertArrayEquals(new int[] {0, 0}, middle.topLeft(10, 10, 20, 20), "clamped to a tiny screen");
	}

	@Test
	void theOldFormatIsReadAsTheDefaultLayout() {
		List<String> warnings = new ArrayList<>();
		HudLayouts layouts = HudLayouts.fromJson(JsonParser.parseString("{\"coordinates\": [0.5, 0.25], \"broken\": [3, 1], \"bad\": 1}"), warnings);

		assertEquals(List.of(HudLayouts.DEFAULT), layouts.names());
		HudLayouts.Element coordinates = layouts.active().get("coordinates");
		assertTrue(coordinates.placed());
		assertArrayEquals(new int[] {200, 75}, coordinates.topLeft(400, 300, 10, 10), "fractions of the screen, as before");
		assertFalse(layouts.active().get("broken").placed());
		assertEquals(2, warnings.size(), warnings::toString);
	}

	@Test
	void layoutsRoundTripWithScaleOpacityVisibilityAndConditions() {
		HudLayouts layouts = new HudLayouts();
		layouts.active().put("fps", HudLayouts.Element.DEFAULT.movedTo(4, 4, 30, 10, 400, 300).withScale(1.5f).withOpacity(0.6f)
				.withCondition(HudLayouts.Condition.IN_COMBAT));
		layouts.active().put("hidden_one", HudLayouts.Element.DEFAULT.withHidden(true));
		assertNull(layouts.create("PvP"));
		layouts.active().put("fps", layouts.active().get("fps").withScale(9f));

		List<String> warnings = new ArrayList<>();
		HudLayouts read = HudLayouts.fromJson(layouts.toJson(), warnings);

		assertTrue(warnings.isEmpty(), warnings::toString);
		assertEquals(List.of("Default", "PvP"), read.names());
		assertEquals("PvP", read.activeName());
		assertEquals(HudLayouts.MAX_SCALE, read.active().get("fps").scale(), "scale is clamped");
		HudLayouts.Element fps = read.layout("Default").get("fps");
		assertEquals(1.5f, fps.scale());
		assertEquals(0.6f, fps.opacity());
		assertEquals(HudLayouts.Condition.IN_COMBAT, fps.when());
		assertTrue(read.layout("Default").get("hidden_one").hidden());
		assertFalse(read.layout("Default").get("hidden_one").placed());
	}

	@Test
	void defaultEntriesAreNotStoredAndUnplacingKeepsTheRest() {
		HudLayouts.Layout layout = new HudLayouts.Layout();
		layout.put("a", HudLayouts.Element.DEFAULT);
		assertTrue(layout.elements().isEmpty());

		HudLayouts.Element element = HudLayouts.Element.DEFAULT.movedTo(10, 10, 5, 5, 100, 100).withOpacity(0.5f);
		layout.put("a", element.unplaced());
		assertFalse(layout.get("a").placed());
		assertEquals(0.5f, layout.get("a").opacity());
	}

	@Test
	void namesAreCheckedAndTheLastLayoutIsKept() {
		HudLayouts layouts = new HudLayouts();
		assertNotNull(layouts.create("default"), "names are unique ignoring case");
		assertNotNull(layouts.create("bad/name"));
		assertNull(layouts.create("Mining"));
		assertNull(layouts.rename("Mining", "Building"));
		assertEquals("Building", layouts.activeName());
		assertNull(layouts.delete("Building"));
		assertEquals("Default", layouts.activeName());
		assertNotNull(layouts.delete("Default"));
	}

	@Test
	void exportedLayoutsImportUnderAFreeName() {
		HudLayouts layouts = new HudLayouts();
		layouts.active().put("fps", HudLayouts.Element.DEFAULT.withOpacity(0.5f));
		String text = layouts.export("Default");

		HudLayouts.ImportResult result = layouts.importLayout(text);
		assertNull(result.error());
		assertEquals("Default 2", result.name());
		assertEquals(0.5f, layouts.active().get("fps").opacity());
		assertNotNull(layouts.importLayout("not json").error());
		assertNotNull(layouts.importLayout("{\"x\": 1}").error());
	}

	@Test
	void snappingFindsEdgesCentersAndOtherElements() {
		Snapping.Result toEdge = Snapping.snap(new Snapping.Box(6, 50, 20, 10), 400, 300, List.of(), 4, 4, 0);
		assertEquals(4, toEdge.x());
		assertEquals(List.of(4), toEdge.guidesX());

		Snapping.Result toCenter = Snapping.snap(new Snapping.Box(188, 100, 20, 10), 400, 300, List.of(), 4, 4, 0);
		assertEquals(190, toCenter.x(), "centered on the screen");

		Snapping.Result toOther = Snapping.snap(new Snapping.Box(100, 62, 20, 10), 400, 300, List.of(new Snapping.Box(100, 40, 50, 20)), 4, 4, 0);
		assertEquals(60, toOther.y(), "below the other element");
		assertEquals(List.of(60), toOther.guidesY());

		Snapping.Result free = Snapping.snap(new Snapping.Box(103, 77, 20, 10), 400, 300, List.of(), 2, 4, 8);
		assertEquals(104, free.x(), "on the grid");
		assertEquals(80, free.y());
		assertTrue(Snapping.overlap(new Snapping.Box(0, 0, 10, 10), new Snapping.Box(5, 5, 10, 10)));
		assertFalse(Snapping.overlap(new Snapping.Box(0, 0, 10, 10), new Snapping.Box(10, 0, 10, 10)));
	}

	@Test
	void undoAndRedoWalkThroughSnapshots() {
		UndoHistory<String> history = new UndoHistory<>(2);
		history.record("a");
		history.record("b");
		history.record("c");

		assertEquals("c", history.undo("d"));
		assertEquals("b", history.undo("c"));
		assertNull(history.undo("b"), "only the newest 2 are kept");
		assertEquals("c", history.redo("b"));
		history.record("x");
		assertFalse(history.canRedo(), "a new change clears redo");
	}
}
