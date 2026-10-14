package io.github.theamethyx.femboyclient.core.setting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import org.junit.jupiter.api.Test;

class SettingsTest {
	enum Mode { FAST, SAFE_MODE }

	private static NumberSetting radius() {
		return new NumberSetting("radius", "Radius", "test", 4, 1, 16, 1, "chunks");
	}

	@Test
	void numberRejectsOutOfRangeAndKeepsValue() {
		NumberSetting setting = radius();
		assertNotNull(setting.set(17.0));
		assertNotNull(setting.set(Double.NaN));
		assertEquals(4.0, setting.get());
		assertNull(setting.set(8.0));
		assertEquals(8, setting.intValue());
		assertEquals("8 chunks", setting.displayValue());
	}

	@Test
	void numberSnapsToStepWithoutFloatingPointNoise() {
		NumberSetting setting = new NumberSetting("alpha", "Alpha", "test", 0.5, 0, 1, 0.1, "");
		setting.setSnapped(0.29999);
		assertEquals(0.3, setting.get());
		setting.setSnapped(5);
		assertEquals(1.0, setting.get());
		assertEquals("1.0", setting.displayValue());
	}

	@Test
	void invalidDefaultOrRangeIsAProgrammingError() {
		assertThrows(IllegalArgumentException.class, () -> new NumberSetting("x", "X", "", 20, 1, 16, 1, ""));
		assertThrows(IllegalArgumentException.class, () -> new NumberSetting("x", "X", "", 1, 5, 1, 1, ""));
		assertThrows(IllegalArgumentException.class, () -> new BooleanSetting("Bad Id", "X", "", true));
	}

	@Test
	void loadingBadJsonFallsBackToDefaultWithReadableWarning() {
		NumberSetting setting = radius();
		setting.set(10.0);

		String warning = setting.load(new JsonPrimitive("ten"));
		assertNotNull(warning);
		assertTrue(warning.contains("Radius"), warning);
		assertEquals(4.0, setting.get());

		assertNotNull(setting.load(new JsonPrimitive(99)));
		assertEquals(4.0, setting.get());

		assertNull(setting.load(new JsonPrimitive(6.4)));
		assertEquals(6.0, setting.get(), "in-range values are snapped to the step");
	}

	@Test
	void enumParsesCaseInsensitivelyAndRejectsUnknown() {
		EnumSetting<Mode> setting = new EnumSetting<>("mode", "Mode", "", Mode.FAST);
		assertNull(setting.load(new JsonPrimitive("safe_mode")));
		assertEquals(Mode.SAFE_MODE, setting.get());
		assertEquals("Safe mode", setting.displayValue());
		assertNotNull(setting.load(new JsonPrimitive("turbo")));
		assertEquals(Mode.FAST, setting.get());
		setting.cycle(-1);
		assertEquals(Mode.SAFE_MODE, setting.get());
	}

	@Test
	void booleanRequiresRealBoolean() {
		BooleanSetting setting = new BooleanSetting("flag", "Flag", "", true);
		assertNotNull(setting.load(new JsonPrimitive("false")));
		assertTrue(setting.get());
		assertNull(setting.load(new JsonPrimitive(false)));
		assertFalse(setting.get());
	}

	@Test
	void listenersFireOnlyOnRealChanges() {
		BooleanSetting setting = new BooleanSetting("flag", "Flag", "", false);
		List<Boolean> seen = new ArrayList<>();
		setting.onChange(seen::add);
		setting.set(false);
		setting.set(true);
		setting.set(true);
		assertEquals(List.of(true), seen);
	}

	@Test
	void groupIgnoresUnknownKeysAndResetsMissingOnes() {
		ModuleSettings group = new ModuleSettings();
		NumberSetting radius = group.add(radius());
		BooleanSetting flag = group.add(new BooleanSetting("flag", "Flag", "", true));
		radius.set(9.0);
		flag.set(false);

		JsonObject json = JsonParser.parseString("{\"radius\": 12, \"unknown\": 1}").getAsJsonObject();
		List<String> warnings = group.load(json);

		assertTrue(warnings.isEmpty(), warnings::toString);
		assertEquals(12.0, radius.get());
		assertTrue(flag.get(), "missing key resets to default");
		assertThrows(IllegalArgumentException.class, () -> group.add(new BooleanSetting("flag", "Again", "", false)));
	}
}
