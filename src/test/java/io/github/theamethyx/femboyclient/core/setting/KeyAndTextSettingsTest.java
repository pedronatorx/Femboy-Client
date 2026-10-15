package io.github.theamethyx.femboyclient.core.setting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonPrimitive;

class KeyAndTextSettingsTest {
	@Test
	void keySettingAcceptsKeysAndNone() {
		KeySetting key = new KeySetting("key_up", "Up", "", 32);
		assertNull(key.set(KeySetting.NONE));
		assertFalse(key.isBound());
		assertNull(key.set(341));
		assertNotNull(key.set(5), "mouse buttons and garbage are not keys");
		assertEquals(341, key.get());

		KeySetting loaded = new KeySetting("key_up", "Up", "", 32);
		assertNull(loaded.load(key.toJson()));
		assertEquals(341, loaded.get());
		assertNotNull(loaded.load(new JsonPrimitive("space")));
		assertEquals(32, loaded.get(), "unreadable values fall back to the default");
	}

	@Test
	void stringSettingRejectsControlCharactersAndLongText() {
		StringSetting text = new StringSetting("java_path", "Java", "", "", 10, "auto");
		assertNull(text.set("/usr/bin/j"));
		assertNotNull(text.set("/usr/bin/java"), "too long");
		assertNotNull(text.set("a\nb"));
		assertEquals("/usr/bin/j", text.get());
		assertEquals("auto", text.placeholder());
	}

	@Test
	void sectionsAndPanelOnlySettings() {
		ModuleSettings settings = new ModuleSettings();
		BooleanSetting first = settings.add(new BooleanSetting("a", "A", "", true));
		settings.section("Labels");
		BooleanSetting second = settings.add(new BooleanSetting("b", "B", "", true));
		BooleanSetting hidden = settings.add(new BooleanSetting("c", "C", "", true)).panelOnly();

		assertNull(first.section());
		assertEquals("Labels", second.section());
		assertTrue(second.isListed());
		assertFalse(hidden.isListed());
		assertEquals(3, settings.all().size(), "panel-only settings are still saved and reset with the module");
	}
}
