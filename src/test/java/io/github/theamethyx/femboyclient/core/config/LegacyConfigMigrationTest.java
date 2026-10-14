package io.github.theamethyx.femboyclient.core.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LegacyConfigMigrationTest {
	@TempDir
	Path dir;

	@Test
	void copiesTheLegacyFolderAndRenamesTheMainFile() throws IOException {
		Path legacy = dir.resolve("nova");
		Files.createDirectories(legacy.resolve("profiles"));
		Files.writeString(legacy.resolve("nova.json"), "{\"activeProfile\":\"pvp\"}");
		Files.writeString(legacy.resolve("nova.json.bak"), "{}");
		Files.writeString(legacy.resolve("profiles/pvp.json"), "{\"modules\":{}}");

		Path target = dir.resolve("femboyclient");
		assertEquals(3, LegacyConfigMigration.migrate(legacy, target, "femboyclient.json"));

		assertEquals("{\"activeProfile\":\"pvp\"}", Files.readString(target.resolve("femboyclient.json")));
		assertTrue(Files.exists(target.resolve("femboyclient.json.bak")));
		assertTrue(Files.exists(target.resolve("profiles/pvp.json")));
		assertTrue(Files.exists(legacy.resolve("nova.json")), "the old folder is kept");
		assertFalse(Files.exists(dir.resolve("femboyclient.migrating")));
	}

	@Test
	void doesNothingWhenTheNewFolderAlreadyExists() throws IOException {
		Path legacy = dir.resolve("nova");
		Files.createDirectories(legacy);
		Files.writeString(legacy.resolve("nova.json"), "{\"old\":true}");
		Path target = dir.resolve("femboyclient");
		Files.createDirectories(target);
		Files.writeString(target.resolve("femboyclient.json"), "{\"new\":true}");

		assertEquals(0, LegacyConfigMigration.migrate(legacy, target, "femboyclient.json"));
		assertEquals("{\"new\":true}", Files.readString(target.resolve("femboyclient.json")));
	}

	@Test
	void doesNothingWithoutALegacyFolder() throws IOException {
		assertEquals(0, LegacyConfigMigration.migrate(dir.resolve("nova"), dir.resolve("femboyclient"), "femboyclient.json"));
		assertFalse(Files.exists(dir.resolve("femboyclient")));
	}
}
