package io.github.theamethyx.femboyclient;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

/** Checks the mod's metadata and resource files; Gradle passes the project directory as femboyclient.projectDir. */
class ResourceFilesTest {
	private static final Path ROOT = Path.of(System.getProperty("femboyclient.projectDir", "")).toAbsolutePath();

	private static JsonObject json(String path) throws IOException {
		return JsonParser.parseString(Files.readString(ROOT.resolve(path))).getAsJsonObject();
	}

	private static Path sourceOf(String className) {
		String file = className.replace('.', '/') + ".java";
		for (String set : new String[] {"src/main/java/", "src/client/java/", "src/gametest/java/"}) {
			if (Files.exists(ROOT.resolve(set + file))) return ROOT.resolve(set + file);
		}

		return null;
	}

	@Test
	void fabricModJsonIsValidAndPointsAtExistingClasses() throws IOException {
		JsonObject mod = json("src/main/resources/fabric.mod.json");
		assertEquals(1, mod.get("schemaVersion").getAsInt());
		assertEquals("femboyclient", mod.get("id").getAsString());
		assertEquals("~1.21.11", mod.getAsJsonObject("depends").get("minecraft").getAsString());
		for (JsonElement entry : mod.getAsJsonObject("entrypoints").getAsJsonArray("client")) {
			assertTrue(sourceOf(entry.getAsString()) != null, "entrypoint class exists: " + entry);
		}

		assertTrue(Files.exists(ROOT.resolve("src/main/resources/" + mod.get("icon").getAsString())), "icon exists");
		for (JsonElement mixin : mod.getAsJsonArray("mixins")) {
			assertTrue(Files.exists(ROOT.resolve("src/client/resources/" + mixin.getAsJsonObject().get("config").getAsString())), "mixin config exists");
		}
	}

	@Test
	void everyListedMixinClassExists() throws IOException {
		JsonObject mixins = json("src/client/resources/femboyclient.client.mixins.json");
		String pkg = mixins.get("package").getAsString();
		assertEquals("JAVA_21", mixins.get("compatibilityLevel").getAsString());
		for (JsonElement name : mixins.getAsJsonArray("client")) {
			assertTrue(sourceOf(pkg + "." + name.getAsString()) != null, "mixin class exists: " + name);
		}
	}

	@Test
	void gametestModJsonEntrypointsExist() throws IOException {
		JsonObject mod = json("src/gametest/resources/fabric.mod.json");
		for (var entry : mod.getAsJsonObject("entrypoints").entrySet()) {
			for (JsonElement cls : entry.getValue().getAsJsonArray()) {
				assertTrue(sourceOf(cls.getAsString()) != null, entry.getKey() + " class exists: " + cls);
			}
		}
	}

	@Test
	void languageFileCoversTheKeyMappings() throws IOException {
		JsonObject lang = json("src/client/resources/assets/femboyclient/lang/en_us.json");
		assertTrue(lang.has("key.femboyclient.open_menu"));
		assertTrue(lang.has("key.category.femboyclient.main"));
	}

	@Test
	void texturesArePngsWithTheExpectedSize() throws IOException {
		int[] icon = pngSize("src/main/resources/assets/femboyclient/icon.png");
		assertArrayEquals(new int[] {128, 128}, icon);
		// The icon atlas holds 8 x 6 cells of 32 px; Icon.java uses the same layout.
		String gui = "src/client/resources/assets/femboyclient/textures/gui/";
		assertArrayEquals(new int[] {256, 224}, pngSize(gui + "icons.png"));
		// The smooth-shape textures UiContext draws with (tools/make_gui_art.py).
		assertArrayEquals(new int[] {64, 64}, pngSize(gui + "round.png"));
		assertArrayEquals(new int[] {64, 64}, pngSize(gui + "shadow.png"));
		assertArrayEquals(new int[] {128, 128}, pngSize(gui + "glow.png"));
		assertArrayEquals(new int[] {64, 4}, pngSize(gui + "ramp.png"));
		assertArrayEquals(new int[] {128, 128}, pngSize(gui + "logo.png"));
		for (String name : new String[] {"icons", "round", "shadow", "glow", "ramp", "logo"}) {
			JsonObject meta = json(gui + name + ".png.mcmeta");
			assertTrue(meta.getAsJsonObject("texture").get("blur").getAsBoolean(), name + " is filtered smoothly");
		}
	}

	private static int[] pngSize(String path) throws IOException {
		byte[] bytes = Files.readAllBytes(ROOT.resolve(path));
		byte[] signature = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n'};
		for (int i = 0; i < signature.length; i++) {
			assertEquals(signature[i], bytes[i], "PNG signature of " + path);
		}

		ByteBuffer header = ByteBuffer.wrap(bytes, 16, 8);
		return new int[] {header.getInt(), header.getInt()};
	}
}
