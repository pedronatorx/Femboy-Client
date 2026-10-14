package io.github.theamethyx.femboyclient.core.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

/**
 * Carries settings over from the folder used before the rename to Femboy Client ({@code config/nova/}).
 *
 * <p>The old folder is copied, never moved or deleted, so going back to an older build still finds its
 * settings. Nothing happens once the new folder exists.
 */
public final class LegacyConfigMigration {
	public static final String LEGACY_MAIN_FILE = "nova.json";

	private LegacyConfigMigration() {
	}

	/**
	 * Copies {@code legacy} to {@code target} when only the legacy folder exists, renaming the main file
	 * to {@code mainFile}. Returns the number of files copied (0 when there was nothing to migrate).
	 */
	public static int migrate(Path legacy, Path target, String mainFile) throws IOException {
		if (Files.exists(target) || !Files.isDirectory(legacy)) {
			return 0;
		}

		// Copy into a temporary sibling first so a crash halfway never leaves a partial target behind.
		Path staging = target.resolveSibling(target.getFileName() + ".migrating");
		deleteTree(staging);
		List<Path> sources;
		try (Stream<Path> walk = Files.walk(legacy)) {
			sources = walk.toList();
		}

		int copied = 0;
		for (Path source : sources) {
			Path relative = legacy.relativize(source);
			String name = relative.toString();
			if (name.equals(LEGACY_MAIN_FILE)) {
				relative = Path.of(mainFile);
			} else if (name.equals(LEGACY_MAIN_FILE + ".bak")) {
				relative = Path.of(mainFile + ".bak");
			}

			Path destination = staging.resolve(relative);
			if (Files.isDirectory(source)) {
				Files.createDirectories(destination);
			} else {
				Files.createDirectories(destination.getParent());
				Files.copy(source, destination);
				copied++;
			}
		}

		Files.move(staging, target);
		return copied;
	}

	private static void deleteTree(Path path) throws IOException {
		if (!Files.exists(path)) {
			return;
		}

		try (Stream<Path> walk = Files.walk(path)) {
			for (Path p : walk.sorted((a, b) -> b.getNameCount() - a.getNameCount()).toList()) {
				Files.delete(p);
			}
		}
	}
}
