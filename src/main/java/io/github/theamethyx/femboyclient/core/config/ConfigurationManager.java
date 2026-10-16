package io.github.theamethyx.femboyclient.core.config;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.LongSupplier;
import java.util.regex.Pattern;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

import io.github.theamethyx.femboyclient.core.diag.DiagnosticLogger;
import io.github.theamethyx.femboyclient.core.module.ModuleManager;
import io.github.theamethyx.femboyclient.core.setting.ModuleSettings;

/**
 * Persists global options and per-profile module state.
 *
 * <pre>
 * config/femboyclient/
 *   femboyclient.json               global options, active profile, profile switching rules
 *   profiles/&lt;name&gt;.json    module state for one profile
 *   *.bak                   previous version of each file (written before every save)
 *   *.corrupt-&lt;time&gt;       unreadable files moved aside, never deleted
 * </pre>
 *
 * Changes are saved shortly after they happen ({@link #tick()}) and on shutdown.
 */
public final class ConfigurationManager {
	public static final int SCHEMA_VERSION = 1;
	public static final String DEFAULT_PROFILE = "default";
	public static final long AUTOSAVE_DELAY_MILLIS = 1500;
	private static final Pattern PROFILE_NAME = Pattern.compile("[A-Za-z0-9_-]{1,32}");
	private static final String SOURCE = "Config";

	private final Path root;
	private final ModuleManager modules;
	private final ModuleSettings globalSettings;
	private final DiagnosticLogger diagnostics;
	private final LongSupplier clock;
	private final List<String> lastReport = new ArrayList<>();
	private final List<ProfileRule> profileRules = new ArrayList<>();
	/** Client-wide data that is not a plain setting (e.g. the HUD layout), stored under "sections" in the global file. */
	private final java.util.Map<String, JsonElement> sections = new java.util.LinkedHashMap<>();

	private String activeProfile = DEFAULT_PROFILE;
	private boolean dirty;
	private boolean applying;
	private long dirtySince;

	public ConfigurationManager(Path root, ModuleManager modules, ModuleSettings globalSettings, DiagnosticLogger diagnostics, LongSupplier clock) {
		this.root = Objects.requireNonNull(root);
		this.modules = Objects.requireNonNull(modules);
		this.globalSettings = Objects.requireNonNull(globalSettings);
		this.diagnostics = Objects.requireNonNull(diagnostics);
		this.clock = Objects.requireNonNull(clock);

		modules.addListener(module -> markDirty());
		globalSettings.all().forEach(setting -> setting.onChange(value -> markDirty()));
	}

	// ----- startup ---------------------------------------------------------------------------

	/**
	 * Loads the global file and the active profile, recovering from missing or malformed files.
	 *
	 * @return readable messages describing every problem that was recovered from
	 */
	public List<String> load() {
		List<String> report = new ArrayList<>();
		applying = true;

		try {
			JsonObject global = readWithRecovery(globalFile(), "Global settings", report);
			activeProfile = DEFAULT_PROFILE;
			globalSettings.resetAll();
			profileRules.clear();
			sections.clear();

			if (global != null) {
				checkSchema(global, "Global settings", report);
				if (global.get("settings") instanceof JsonObject settings) {
					globalSettings.load(settings).forEach(warning -> report.add("Global settings: " + warning));
				}

				profileRules.addAll(ProfileRule.fromJson(global.get("profileRules"), report));
				if (global.get("sections") instanceof JsonObject saved) {
					saved.entrySet().forEach(entry -> sections.put(entry.getKey(), entry.getValue().deepCopy()));
				}

				if (global.get("activeProfile") instanceof JsonPrimitive primitive && primitive.isString()) {
					String name = primitive.getAsString();
					if (isValidProfileName(name) && Files.exists(profileFile(name))) {
						activeProfile = name;
					} else {
						report.add("The active profile '" + name + "' was not found; switched to '" + DEFAULT_PROFILE + "'.");
					}
				}
			}

			loadProfileInto(activeProfile, report);
		} finally {
			applying = false;
		}

		// Write back immediately so recovered or newly created files exist on disk.
		saveNow();
		finishReport(report);
		return report;
	}

	private void loadProfileInto(String name, List<String> report) {
		JsonObject profile = Files.exists(profileFile(name)) || Files.exists(JsonFiles.backupOf(profileFile(name)))
				? readWithRecovery(profileFile(name), "Profile '" + name + "'", report)
				: null;

		if (profile == null) {
			modules.loadModules(new JsonObject());
			return;
		}

		checkSchema(profile, "Profile '" + name + "'", report);
		JsonObject moduleJson = profile.get("modules") instanceof JsonObject object ? object : new JsonObject();
		modules.loadModules(moduleJson).forEach(warning -> report.add("Profile '" + name + "': " + warning));
	}

	// ----- saving ----------------------------------------------------------------------------

	public void markDirty() {
		if (applying) {
			return;
		}

		if (!dirty) {
			dirtySince = clock.getAsLong();
		}

		dirty = true;
	}

	/** Saves pending changes once they have been stable for {@link #AUTOSAVE_DELAY_MILLIS}. */
	public void tick() {
		if (dirty && clock.getAsLong() - dirtySince >= AUTOSAVE_DELAY_MILLIS) {
			saveNow();
		}
	}

	public boolean isDirty() {
		return dirty;
	}

	/** @return {@code true} if both files were written */
	public boolean saveNow() {
		boolean ok = true;

		try {
			JsonFiles.writeAtomically(profileFile(activeProfile), profileJson());
		} catch (IOException e) {
			ok = false;
			diagnostics.error(SOURCE, "Could not save profile '" + activeProfile + "' (" + e.getMessage() + "). Changes are kept in memory and saving will be retried.", e);
		}

		try {
			JsonFiles.writeAtomically(globalFile(), globalJson());
		} catch (IOException e) {
			ok = false;
			diagnostics.error(SOURCE, "Could not save global settings (" + e.getMessage() + "). Changes are kept in memory and saving will be retried.", e);
		}

		if (ok) {
			dirty = false;
		} else {
			// Retry after another delay instead of every tick.
			dirtySince = clock.getAsLong();
		}

		return ok;
	}

	private JsonObject profileJson() {
		JsonObject json = new JsonObject();
		json.addProperty("schemaVersion", SCHEMA_VERSION);
		json.add("modules", modules.saveModules());
		return json;
	}

	private JsonObject globalJson() {
		JsonObject json = new JsonObject();
		json.addProperty("schemaVersion", SCHEMA_VERSION);
		json.addProperty("activeProfile", activeProfile);
		json.add("settings", globalSettings.toJson());
		json.add("profileRules", ProfileRule.toJson(profileRules));
		JsonObject savedSections = new JsonObject();
		sections.forEach((key, value) -> savedSections.add(key, value.deepCopy()));
		json.add("sections", savedSections);
		return json;
	}

	// ----- profiles --------------------------------------------------------------------------

	public String activeProfile() {
		return activeProfile;
	}

	/** Profile names found on disk, sorted case-insensitively. Always contains the active one. */
	public List<String> profiles() {
		List<String> names = new ArrayList<>();
		Path dir = root.resolve("profiles");

		if (Files.isDirectory(dir)) {
			try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.json")) {
				for (Path path : stream) {
					String file = path.getFileName().toString();
					String name = file.substring(0, file.length() - ".json".length());
					if (isValidProfileName(name)) {
						names.add(name);
					}
				}
			} catch (IOException e) {
				diagnostics.warn(SOURCE, "Could not list profiles: " + e.getMessage());
			}
		}

		if (!names.contains(activeProfile)) {
			names.add(activeProfile);
		}

		names.sort(String.CASE_INSENSITIVE_ORDER);
		return names;
	}

	public static boolean isValidProfileName(String name) {
		return name != null && PROFILE_NAME.matcher(name).matches();
	}

	/**
	 * Creates a profile containing a copy of the current module state.
	 *
	 * @return {@code null} on success, otherwise a readable reason
	 */
	public String createProfile(String name) {
		String problem = checkNewProfileName(name);
		if (problem != null) {
			return problem;
		}

		try {
			JsonFiles.writeAtomically(profileFile(name), profileJson());
		} catch (IOException e) {
			return "Could not write the profile file: " + e.getMessage();
		}

		diagnostics.info(SOURCE, "Created profile '" + name + "' from '" + activeProfile + "'.");
		return null;
	}

	/** Saves the current profile, then loads {@code name}. */
	public String switchProfile(String name) {
		if (!isValidProfileName(name) || !Files.exists(profileFile(name))) {
			return "Profile '" + name + "' does not exist.";
		}

		if (name.equals(activeProfile)) {
			return null;
		}

		saveNow();
		List<String> report = new ArrayList<>();
		applying = true;

		try {
			activeProfile = name;
			loadProfileInto(name, report);
		} finally {
			applying = false;
		}

		saveNow();
		diagnostics.info(SOURCE, "Switched to profile '" + name + "'.");
		finishReport(report);
		return null;
	}

	/** Deletes a profile that is not active. The file is moved aside as a backup, not erased. */
	public String deleteProfile(String name) {
		if (name.equals(activeProfile)) {
			return "The active profile cannot be deleted. Switch to another profile first.";
		}

		Path file = profileFile(name);
		if (!isValidProfileName(name) || !Files.exists(file)) {
			return "Profile '" + name + "' does not exist.";
		}

		try {
			Files.move(file, file.resolveSibling(name + ".json.deleted-" + timestamp()), StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException e) {
			return "Could not delete the profile: " + e.getMessage();
		}

		diagnostics.info(SOURCE, "Deleted profile '" + name + "' (a copy was kept in the profiles folder).");
		return null;
	}

	/**
	 * Creates {@code target} as a copy of {@code source}, without switching.
	 *
	 * @return {@code null} on success, otherwise a readable reason
	 */
	public String duplicateProfile(String source, String target) {
		JsonObject modulesJson = profileModules(source);
		if (modulesJson == null) {
			return "Profile '" + source + "' could not be read.";
		}

		return writeNewProfile(target, modulesJson, "Duplicated profile '" + source + "' as '" + target + "'.");
	}

	/**
	 * The profile as shareable JSON text (module state only; no global options or file paths).
	 *
	 * @return the text, or {@code null} if the profile could not be read
	 */
	public String exportProfile(String name) {
		JsonObject modulesJson = profileModules(name);
		if (modulesJson == null) {
			return null;
		}

		JsonObject json = new JsonObject();
		json.addProperty("femboyClientProfile", name);
		json.addProperty("schemaVersion", SCHEMA_VERSION);
		json.add("modules", modulesJson);
		return JsonFiles.GSON.toJson(json);
	}

	/**
	 * Writes an exported profile to {@code exports/<name>-<time>.json}.
	 *
	 * @return the written file, or {@code null} if it failed (reported to diagnostics)
	 */
	public Path exportProfileToFile(String name) {
		String text = exportProfile(name);
		if (text == null) {
			return null;
		}

		Path file = root.resolve("exports").resolve(name + "-" + timestamp() + ".json");
		try {
			JsonFiles.writeAtomically(file, JsonParser.parseString(text));
			return file;
		} catch (IOException e) {
			diagnostics.error(SOURCE, "Could not export profile '" + name + "': " + e.getMessage(), e);
			return null;
		}
	}

	/** Result of {@link #importProfile}: {@code error} is null on success; warnings list values that were dropped. */
	public record ImportResult(String error, List<String> warnings) {
	}

	/**
	 * Creates profile {@code name} from exported text. Every value is validated first; unknown modules and
	 * invalid values are dropped (their defaults apply) and listed in the warnings. Nothing is switched.
	 */
	public ImportResult importProfile(String name, String text) {
		JsonObject json;
		try {
			JsonElement parsed = JsonParser.parseString(text == null ? "" : text.strip());
			if (!(parsed instanceof JsonObject object)) {
				return new ImportResult("This is not a Femboy Client profile (expected a JSON object).", List.of());
			}

			json = object;
		} catch (JsonParseException e) {
			return new ImportResult("This is not valid JSON: " + e.getMessage(), List.of());
		}

		if (!(json.get("modules") instanceof JsonObject modulesJson)) {
			return new ImportResult("This is not a Femboy Client profile (no 'modules' object).", List.of());
		}

		List<String> warnings = new ArrayList<>();
		checkSchema(json, "The imported profile", warnings);
		JsonObject clean = modules.sanitizeModules(modulesJson, warnings);
		String problem = writeNewProfile(name, clean, "Imported profile '" + name + "'" + (warnings.isEmpty() ? "." : " (" + warnings.size() + " value(s) dropped)."));
		return new ImportResult(problem, problem == null ? List.copyOf(warnings) : List.of());
	}

	/**
	 * Creates a profile from module data that came from this build (e.g. a built-in preset).
	 *
	 * @return {@code null} on success, otherwise a readable reason
	 */
	public String createProfileFrom(String name, JsonObject modulesJson) {
		List<String> warnings = new ArrayList<>();
		JsonObject clean = modules.sanitizeModules(modulesJson, warnings);
		warnings.forEach(warning -> diagnostics.warn(SOURCE, "Profile '" + name + "': " + warning));
		return writeNewProfile(name, clean, "Created profile '" + name + "'.");
	}

	/**
	 * Replaces a profile's module data (e.g. "reset to preset"), creating the profile if needed. Values are
	 * validated first. If it is the active profile, the new state is applied immediately.
	 *
	 * @return {@code null} on success, otherwise a readable reason
	 */
	public String overwriteProfile(String name, JsonObject modulesJson) {
		if (!isValidProfileName(name)) {
			return "Use 1-32 letters, digits, '-' or '_'.";
		}

		List<String> warnings = new ArrayList<>();
		JsonObject clean = modules.sanitizeModules(modulesJson, warnings);
		warnings.forEach(warning -> diagnostics.warn(SOURCE, "Profile '" + name + "': " + warning));
		JsonObject json = new JsonObject();
		json.addProperty("schemaVersion", SCHEMA_VERSION);
		json.add("modules", clean);
		try {
			JsonFiles.writeAtomically(profileFile(name), json);
		} catch (IOException e) {
			return "Could not write the profile file: " + e.getMessage();
		}

		if (name.equals(activeProfile)) {
			List<String> report = new ArrayList<>();
			applying = true;
			try {
				loadProfileInto(name, report);
			} finally {
				applying = false;
			}

			finishReport(report);
		}

		diagnostics.info(SOURCE, "Wrote profile '" + name + "'.");
		return null;
	}

	/** Resets every module of the active profile to its defaults (all modules off) and saves. */
	public void resetActiveProfile() {
		for (var module : modules.all()) {
			modules.resetToDefaults(module);
		}

		saveNow();
		diagnostics.info(SOURCE, "Reset profile '" + activeProfile + "' to defaults.");
	}

	private String writeNewProfile(String name, JsonObject modulesJson, String logMessage) {
		String problem = checkNewProfileName(name);
		if (problem != null) {
			return problem;
		}

		JsonObject json = new JsonObject();
		json.addProperty("schemaVersion", SCHEMA_VERSION);
		json.add("modules", modulesJson);
		try {
			JsonFiles.writeAtomically(profileFile(name), json);
		} catch (IOException e) {
			return "Could not write the profile file: " + e.getMessage();
		}

		diagnostics.info(SOURCE, logMessage);
		return null;
	}

	/** Module data of a profile: the live state for the active one, otherwise read from disk. */
	private JsonObject profileModules(String name) {
		if (name.equals(activeProfile)) {
			return modules.saveModules();
		}

		if (!isValidProfileName(name) || !Files.exists(profileFile(name))) {
			return null;
		}

		try {
			return JsonFiles.readObject(profileFile(name)).get("modules") instanceof JsonObject object ? object : new JsonObject();
		} catch (IOException e) {
			diagnostics.warn(SOURCE, "Could not read profile '" + name + "': " + e.getMessage());
			return null;
		}
	}

	public boolean profileExists(String name) {
		return name.equals(activeProfile) || (isValidProfileName(name) && Files.exists(profileFile(name)));
	}

	// ----- client-wide sections --------------------------------------------------------------

	/** A copy of a stored section, or {@code null}. The caller validates the content. */
	public JsonElement section(String key) {
		JsonElement value = sections.get(key);
		return value == null ? null : value.deepCopy();
	}

	public void setSection(String key, JsonElement value) {
		if (value == null) {
			sections.remove(key);
		} else {
			sections.put(key, value.deepCopy());
		}

		markDirty();
	}

	// ----- automatic switching rules ---------------------------------------------------------

	/** Rules for automatic profile switching, in priority order. */
	public List<ProfileRule> profileRules() {
		return List.copyOf(profileRules);
	}

	public void setProfileRules(List<ProfileRule> rules) {
		profileRules.clear();
		profileRules.addAll(rules);
		markDirty();
	}

	private String checkNewProfileName(String name) {
		if (!isValidProfileName(name)) {
			return "Use 1-32 letters, digits, '-' or '_'.";
		}

		for (String existing : profiles()) {
			if (existing.equalsIgnoreCase(name)) {
				return "A profile named '" + existing + "' already exists.";
			}
		}

		return null;
	}

	// ----- recovery helpers ------------------------------------------------------------------

	/**
	 * Reads a file; if it is malformed it is moved aside and the backup is tried.
	 *
	 * @return the parsed object, or {@code null} if defaults must be used
	 */
	private JsonObject readWithRecovery(Path file, String what, List<String> report) {
		boolean primaryFailed = false;

		if (Files.exists(file)) {
			try {
				return JsonFiles.readObject(file);
			} catch (JsonFiles.MalformedConfigException e) {
				primaryFailed = true;
				Path aside = file.resolveSibling(file.getFileName() + ".corrupt-" + timestamp());
				try {
					Files.move(file, aside, StandardCopyOption.REPLACE_EXISTING);
					report.add(what + " could not be read (" + e.getMessage() + "). The file was kept as " + aside.getFileName() + ".");
				} catch (IOException moveFailure) {
					report.add(what + " could not be read (" + e.getMessage() + ") and could not be moved aside: " + moveFailure.getMessage());
				}
			} catch (IOException e) {
				report.add(what + " could not be opened (" + e.getMessage() + "); defaults are used for this session.");
				return null;
			}
		}

		Path backup = JsonFiles.backupOf(file);
		if (Files.exists(backup)) {
			try {
				JsonObject restored = JsonFiles.readObject(backup);
				report.add(what + " was restored from the backup copy.");
				return restored;
			} catch (IOException e) {
				report.add(what + " backup could not be read either (" + e.getMessage() + "); using defaults.");
			}
		} else if (primaryFailed) {
			report.add(what + ": no backup was available; using defaults.");
		}

		return null;
	}

	private static void checkSchema(JsonObject json, String what, List<String> report) {
		if (json.get("schemaVersion") instanceof JsonPrimitive primitive && primitive.isNumber() && primitive.getAsInt() > SCHEMA_VERSION) {
			report.add(what + " was written by a newer version of Femboy Client; settings this version does not know were ignored.");
		}
	}

	private void finishReport(List<String> report) {
		report.forEach(message -> diagnostics.warn(SOURCE, message));
		lastReport.clear();
		lastReport.addAll(report);
	}

	/** Problems found during the last load or profile switch. */
	public List<String> lastReport() {
		return List.copyOf(lastReport);
	}

	private Path globalFile() {
		return root.resolve("femboyclient.json");
	}

	private Path profileFile(String name) {
		return root.resolve("profiles").resolve(name + ".json");
	}

	public Path root() {
		return root;
	}

	private static String timestamp() {
		return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS"));
	}
}
