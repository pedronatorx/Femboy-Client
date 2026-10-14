package io.github.theamethyx.femboyclient.core.setting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

/** Color and id-set settings: validation, JSON round trips and recovery from bad config values. */
class CollectionSettingsTest {
	@Test
	void colorRoundTripsAsHex() {
		ColorSetting color = new ColorSetting("color", "Color", "test", 0x9D6EFF);
		assertEquals(0xFF9D6EFF, color.get());
		assertEquals("\"#9D6EFF\"", color.toJson().toString());

		assertNull(color.load(JsonParser.parseString("\"#5bd68a\"")));
		assertEquals(0xFF5BD68A, color.get());
		assertEquals(0x405BD68A, color.withAlpha(0.25f));
	}

	@Test
	void malformedColorFallsBackToDefault() {
		ColorSetting color = new ColorSetting("color", "Color", "test", 0x9D6EFF);
		color.set(0xFF123456);
		assertNotNull(color.load(JsonParser.parseString("\"purple\"")));
		assertEquals(0xFF9D6EFF, color.get());
		assertNotNull(color.load(JsonParser.parseString("12")));
		assertNotNull(color.set(0x00FFFFFF), "transparent colors are rejected");
	}

	@Test
	void idSetStoresSortedIdsAndKeepsUnknownOnes() {
		IdSetSetting set = new IdSetSetting("types", "Types", "test", Set.of("minecraft:zombie"));
		assertNull(set.load(JsonParser.parseString("[\"minecraft:pig\", \"othermod:robot\", \"minecraft:cow\"]")));
		assertEquals(List.of("minecraft:cow", "minecraft:pig", "othermod:robot"), List.copyOf(set.get()));
		assertEquals("[\"minecraft:cow\",\"minecraft:pig\",\"othermod:robot\"]", set.toJson().toString());
		assertEquals("3 selected", set.displayValue());
	}

	@Test
	void idSetRejectsBadIdsAndMalformedJson() {
		IdSetSetting set = new IdSetSetting("types", "Types", "test", Set.of("minecraft:zombie"));
		assertNotNull(set.load(JsonParser.parseString("[\"Zombie\"]")));
		assertEquals(Set.of("minecraft:zombie"), set.get());
		assertNotNull(set.load(JsonParser.parseString("\"minecraft:zombie\"")));
		assertNotNull(set.load(JsonParser.parseString("[1, 2]")));
		assertThrows(IllegalArgumentException.class, () -> new IdSetSetting("types", "Types", "test", Set.of("no colon")));
	}

	@Test
	void idSetToggleAndBulkChangesNotifyOnce() {
		IdSetSetting set = new IdSetSetting("types", "Types", "test", Set.of());
		int[] changes = {0};
		set.onChange(value -> changes[0]++);

		set.toggle("minecraft:pig");
		assertTrue(set.contains("minecraft:pig"));
		set.setAll(List.of("minecraft:cow", "minecraft:sheep"), true);
		assertEquals(3, set.get().size());
		set.setAll(List.of("minecraft:pig", "minecraft:cow", "minecraft:sheep"), false);
		assertTrue(set.get().isEmpty());
		assertEquals(3, changes[0]);
		assertFalse(set.isDefault() && changes[0] == 0);
	}

	@Test
	void idSetGroupsFollowChoiceOrder() {
		IdSetSetting set = new IdSetSetting("types", "Types", "test", Set.of()).choices(() -> List.of(
				new IdSetSetting.Choice("minecraft:zombie", "Zombie", "Hostile"),
				new IdSetSetting.Choice("minecraft:pig", "Pig", "Passive"),
				new IdSetSetting.Choice("minecraft:skeleton", "Skeleton", "Hostile")));
		assertEquals(List.of("Hostile", "Passive"), set.groups());
	}
}
