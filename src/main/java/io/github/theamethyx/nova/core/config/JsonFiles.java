package io.github.theamethyx.nova.core.config;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.Strictness;
import com.google.gson.stream.JsonReader;

/** Crash-safe JSON file helpers. */
public final class JsonFiles {
	public static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

	private JsonFiles() {
	}

	/**
	 * Reads a JSON object.
	 *
	 * @throws MalformedConfigException if the file is not a readable JSON object
	 * @throws IOException if the file cannot be read at all
	 */
	public static JsonObject readObject(Path path) throws IOException {
		try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
			JsonReader jsonReader = new JsonReader(reader);
			jsonReader.setStrictness(Strictness.STRICT);
			JsonElement element = JsonParser.parseReader(jsonReader);
			if (!element.isJsonObject()) {
				throw new MalformedConfigException("the file does not contain a JSON object");
			}

			return element.getAsJsonObject();
		} catch (JsonParseException | IllegalStateException e) {
			Throwable cause = e.getCause() != null ? e.getCause() : e;
			throw new MalformedConfigException(cause.getMessage() == null ? "invalid JSON" : cause.getMessage());
		}
	}

	/**
	 * Writes atomically: the data goes to a temporary file first, the previous version is kept as
	 * {@code <name>.bak}, then the temporary file replaces the target. A crash mid-write therefore
	 * never leaves a half-written config behind.
	 */
	public static void writeAtomically(Path path, JsonElement json) throws IOException {
		Files.createDirectories(path.getParent());
		Path temp = path.resolveSibling(path.getFileName() + ".tmp");

		try (Writer writer = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) {
			GSON.toJson(json, writer);
			writer.write('\n');
		}

		if (Files.exists(path)) {
			Files.copy(path, backupOf(path), StandardCopyOption.REPLACE_EXISTING);
		}

		try {
			Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (AtomicMoveNotSupportedException e) {
			Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING);
		}
	}

	public static Path backupOf(Path path) {
		return path.resolveSibling(path.getFileName() + ".bak");
	}

	/** Thrown when a config file exists but its content cannot be used. */
	public static final class MalformedConfigException extends IOException {
		public MalformedConfigException(String message) {
			super(message);
		}
	}
}
