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
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
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
 *   profiles/*.before-repair-&lt;time&gt;  a profile as it was before invalid values in it were replaced by defaults
 *   restore-points/&lt;time&gt;-&lt;label&gt;/  copies of all files, made by you or before risky changes (newest 20 kept)
 * </pre>
 *
 * <p>Nothing you saved is dropped silently: values for modules or settings this version does not know are written back
 * unchanged, and a profile whose values had to be replaced is copied first.
 *
 * Changes are saved shortly after they happen ({@link #tick()}) and on shutdown. Automatic saves serialize the state
 * on the calling (client) thread and write the files on the {@code writer} executor, so the game never waits for the
 * disk; writes run one at a time in order, and {@link #saveNow()} waits for them.
 *
 * <p>A profile also stores a few client options ({@link #setProfileOptions}): the look and the performance mode. They
 * are applied when the profile is loaded, so switching profiles switches them too.
 */
public final class ConfigurationManager {
	/** 3: profiles may hold "sections" (the HUD layout) and the global file a profile history. */
	public static final int SCHEMA_VERSION = 3;
	public static final int MAX_RESTORE_POINTS = 20;
	public static final int MAX_HISTORY = 20;
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
	private final Executor writer;
	/** The last write handed to {@link #writer}; later writes and {@link #saveNow()} run after it. */
	private CompletableFuture<Void> lastWrite = CompletableFuture.completedFuture(null);
	/** Failures from background writes, reported on the client thread at the next tick. */
	private final ConcurrentLinkedQueue<String> writeFailures = new ConcurrentLinkedQueue<>();
	private Set<String> profileOptionIds = Set.of();
	private Set<String> profileSectionKeys = Set.of();
	/** Profiles switched away from, newest last. */
	private final List<HistoryEntry> history = new ArrayList<>();
	private final List<Runnable> loadListeners = new java.util.concurrent.CopyOnWriteArrayList<>();

	/** A profile you switched away from, and when. */
	public record HistoryEntry(String profile, long timeMillis) {
	}

	/** A saved copy of all configuration files. {@code profiles} are the profile names it contains. */
	public record RestorePoint(String id, String label, long createdMillis, List<String> profiles) {
	}

	/** Writes files synchronously on the calling thread (used by tests and tools). */
	public ConfigurationManager(Path root, ModuleManager modules, ModuleSettings globalSettings, DiagnosticLogger diagnostics, LongSupplier clock) {
		this(root, modules, globalSettings, diagnostics, clock, Runnable::run);
	}

	/** {@code writer} runs file writes for automatic saves; it must run tasks one at a time, in order. */
	public ConfigurationManager(Path root, ModuleManager modules, ModuleSettings globalSettings, DiagnosticLogger diagnostics, LongSupplier clock,
			Executor writer) {
		this.root = Objects.requireNonNull(root);
		this.modules = Objects.requireNonNull(modules);
		this.globalSettings = Objects.requireNonNull(globalSettings);
		this.diagnostics = Objects.requireNonNull(diagnostics);
		this.clock = Objects.requireNonNull(clock);
		this.writer = Objects.requireNonNull(writer);

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
			history.clear();

			if (global != null) {
				checkSchema(global, "Global settings", report);
				if (global.get("settings") instanceof JsonObject settings) {
					globalSettings.load(settings).forEach(warning -> report.add("Global settings: " + warning));
				}

				profileRules.addAll(ProfileRule.fromJson(global.get("profileRules"), report));
				readHistory(global.get("profileHistory"));
				if (global.get("schemaVersion") instanceof JsonPrimitive version && version.isNumber() && version.getAsInt() < SCHEMA_VERSION) {
					// The files are about to be rewritten in the new format: keep the old ones.
					String problem = snapshotFiles("Before updating the configuration format");
					if (problem != null) {
						report.add("Could not keep a copy of the old configuration: " + problem);
					}
				}

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
		fireLoaded();
		return report;
	}

	/** Called on the calling thread after a profile was loaded (startup, switch, restore, reset), for state outside modules. */
	public void addLoadListener(Runnable listener) {
		loadListeners.add(listener);
	}

	private void fireLoaded() {
		for (Runnable listener : loadListeners) {
			try {
				listener.run();
			} catch (RuntimeException e) {
				diagnostics.error(SOURCE, "Applying the loaded profile failed in part: " + e.getMessage(), e);
			}
		}
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
		migrate(moduleJson, "Profile '" + name + "'");
		List<String> warnings = modules.loadModules(moduleJson);
		warnings.forEach(warning -> report.add("Profile '" + name + "': " + warning));
		if (!warnings.isEmpty()) {
			keepOriginal(name, report);
		}

		if (profile.get("options") instanceof JsonObject options) {
			applyProfileOptions(options, name, report);
		}

		if (profile.get("sections") instanceof JsonObject saved) {
			for (String key : profileSectionKeys) {
				if (saved.get(key) != null) {
					sections.put(key, saved.get(key).deepCopy());
				}
			}
		}
	}

	/** Copies a profile whose values were just replaced by defaults, before the next save overwrites them. */
	private void keepOriginal(String name, List<String> report) {
		Path file = profileFile(name);
		if (!Files.exists(file)) {
			return;
		}

		Path copy = file.resolveSibling(name + ".json.before-repair-" + timestamp());
		try {
			Files.copy(file, copy, StandardCopyOption.REPLACE_EXISTING);
			report.add("Profile '" + name + "': the file as it was is kept as " + copy.getFileName() + ".");
		} catch (IOException e) {
			report.add("Profile '" + name + "': could not keep a copy of the original file (" + e.getMessage() + ").");
		}
	}

	/**
	 * Which client-wide sections (by key, e.g. the HUD layout) each profile stores as well: when a profile with that
	 * section is loaded, its copy replaces the current one. A profile without it keeps the current one.
	 */
	public void setProfileSections(Set<String> keys) {
		profileSectionKeys = Set.copyOf(keys);
	}

	private void migrate(JsonObject moduleJson, String what) {
		ConfigMigrations.migrateModules(moduleJson).forEach(note -> diagnostics.info(SOURCE, what + ": " + note));
	}

	// ----- per-profile client options --------------------------------------------------------

	/**
	 * Which global settings (by id) are stored in each profile as well: when a profile is loaded, its stored values
	 * replace the current ones. Settings not listed stay client-wide.
	 */
	public void setProfileOptions(Set<String> ids) {
		profileOptionIds = Set.copyOf(ids);
	}

	private void applyProfileOptions(JsonObject options, String profile, List<String> report) {
		for (String id : profileOptionIds) {
			var setting = globalSettings.get(id);
			JsonElement value = options.get(id);
			if (setting != null && value != null) {
				String problem = setting.load(value);
				if (problem != null) {
					report.add("Profile '" + profile + "': option '" + setting.name() + "': " + problem);
				}
			}
		}
	}

	private JsonObject profileOptionsJson() {
		JsonObject json = new JsonObject();
		for (String id : profileOptionIds) {
			var setting = globalSettings.get(id);
			if (setting != null) {
				json.add(id, setting.toJson());
			}
		}

		return json;
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

	/**
	 * Saves pending changes once they have been stable for {@link #AUTOSAVE_DELAY_MILLIS}. The state is captured now; the
	 * files are written on the writer executor.
	 */
	public void tick() {
		String failure;
		while ((failure = writeFailures.poll()) != null) {
			diagnostics.error(SOURCE, failure, null);
			// Retry after another delay instead of every tick.
			markDirtyAgain();
		}

		if (dirty && clock.getAsLong() - dirtySince >= AUTOSAVE_DELAY_MILLIS) {
			saveInBackground();
		}
	}

	private void markDirtyAgain() {
		dirty = true;
		dirtySince = clock.getAsLong();
	}

	private void saveInBackground() {
		Path profileFile = profileFile(activeProfile);
		String profileName = activeProfile;
		JsonObject profile = profileJson();
		JsonObject global = globalJson();
		dirty = false;
		lastWrite = lastWrite.thenRunAsync(() -> {
			try {
				JsonFiles.writeAtomically(profileFile, profile);
			} catch (IOException e) {
				writeFailures.add("Could not save profile '" + profileName + "' (" + e.getMessage() + "). Changes are kept in memory and saving will be retried.");
			}

			try {
				JsonFiles.writeAtomically(globalFile(), global);
			} catch (IOException e) {
				writeFailures.add("Could not save global settings (" + e.getMessage() + "). Changes are kept in memory and saving will be retried.");
			}
		}, writer);
	}

	/** Waits until background writes handed out so far are on disk. */
	public void flushWrites() {
		try {
			lastWrite.join();
		} catch (RuntimeException e) {
			diagnostics.error(SOURCE, "A background save failed: " + e.getMessage(), e);
		}
	}

	public boolean isDirty() {
		return dirty;
	}

	/** Writes both files now, after any pending background write. @return {@code true} if both files were written */
	public boolean saveNow() {
		flushWrites();
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
		if (!profileOptionIds.isEmpty()) {
			json.add("options", profileOptionsJson());
		}

		JsonObject saved = new JsonObject();
		for (String key : profileSectionKeys) {
			if (sections.get(key) != null) {
				saved.add(key, sections.get(key).deepCopy());
			}
		}

		if (!saved.isEmpty()) {
			json.add("sections", saved);
		}

		return json;
	}

	private JsonObject globalJson() {
		JsonObject json = new JsonObject();
		json.addProperty("schemaVersion", SCHEMA_VERSION);
		json.addProperty("activeProfile", activeProfile);
		json.add("settings", globalSettings.toJson());
		json.add("profileRules", ProfileRule.toJson(profileRules));
		com.google.gson.JsonArray historyJson = new com.google.gson.JsonArray();
		for (HistoryEntry entry : history) {
			JsonObject item = new JsonObject();
			item.addProperty("profile", entry.profile());
			item.addProperty("time", entry.timeMillis());
			historyJson.add(item);
		}

		json.add("profileHistory", historyJson);
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

		flushWrites();
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
		String previous = activeProfile;

		try {
			activeProfile = name;
			loadProfileInto(name, report);
		} finally {
			applying = false;
		}

		remember(previous);
		saveNow();
		diagnostics.info(SOURCE, "Switched to profile '" + name + "'.");
		finishReport(report);
		fireLoaded();
		return null;
	}

	private void remember(String profile) {
		history.removeIf(entry -> entry.profile().equals(profile));
		history.add(new HistoryEntry(profile, clock.getAsLong()));
		while (history.size() > MAX_HISTORY) {
			history.removeFirst();
		}
	}

	private void readHistory(JsonElement json) {
		if (json instanceof com.google.gson.JsonArray array) {
			for (JsonElement element : array) {
				if (element instanceof JsonObject item && item.get("profile") instanceof JsonPrimitive profile && profile.isString()
						&& isValidProfileName(profile.getAsString()) && item.get("time") instanceof JsonPrimitive time && time.isNumber()) {
					history.add(new HistoryEntry(profile.getAsString(), time.getAsLong()));
				}
			}

			while (history.size() > MAX_HISTORY) {
				history.removeFirst();
			}
		}
	}

	/** Profiles you switched away from, newest first (only those that still exist). */
	public List<HistoryEntry> history() {
		List<HistoryEntry> result = new ArrayList<>();
		for (int i = history.size() - 1; i >= 0; i--) {
			HistoryEntry entry = history.get(i);
			if (!entry.profile().equals(activeProfile) && profileExists(entry.profile())) {
				result.add(entry);
			}
		}

		return result;
	}

	/** The profile used before the current one, or {@code null}. */
	public String previousProfile() {
		List<HistoryEntry> entries = history();
		return entries.isEmpty() ? null : entries.getFirst().profile();
	}

	/** Switches back to {@link #previousProfile()}. @return {@code null} on success, otherwise a readable reason */
	public String revertProfile() {
		String previous = previousProfile();
		return previous == null ? "There is no earlier profile to go back to." : switchProfile(previous);
	}

	/**
	 * Renames a profile (also the active one). Switching rules and the history follow the new name.
	 *
	 * @return {@code null} on success, otherwise a readable reason
	 */
	public String renameProfile(String from, String to) {
		if (!profileExists(from)) {
			return "Profile '" + from + "' does not exist.";
		}

		if (!isValidProfileName(to)) {
			return "Use 1-32 letters, digits, '-' or '_'.";
		}

		if (from.equals(to)) {
			return null;
		}

		if (!from.equalsIgnoreCase(to)) {
			String problem = checkNewProfileName(to);
			if (problem != null) {
				return problem;
			}
		}

		boolean active = from.equals(activeProfile);
		if (active) {
			saveNow();
		} else {
			flushWrites();
		}

		try {
			Files.move(profileFile(from), profileFile(to), StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException e) {
			return "Could not rename the profile file: " + e.getMessage();
		}

		if (active) {
			activeProfile = to;
		}

		profileRules.replaceAll(rule -> rule.profile().equals(from) ? new ProfileRule(rule.item(), rule.dimension(), to) : rule);
		history.replaceAll(entry -> entry.profile().equals(from) ? new HistoryEntry(to, entry.timeMillis()) : entry);
		String note = profileNote(from);
		if (!note.isEmpty()) {
			setProfileNote(from, null);
			setProfileNote(to, note);
		}

		saveNow();
		diagnostics.info(SOURCE, "Renamed profile '" + from + "' to '" + to + "'.");
		return null;
	}

	/**
	 * What differs between two profiles: modules, keys, settings and stored client options. Either may be the active
	 * profile (its live state is used). {@code null} if one of them could not be read.
	 */
	public List<ConfigDiff.Difference> diffProfiles(String left, String right) {
		JsonObject leftModules = profileModules(left);
		JsonObject rightModules = profileModules(right);
		if (leftModules == null || rightModules == null) {
			return null;
		}

		return ConfigDiff.compare(modules, leftModules, rightModules, globalSettings, profileOptions(left), profileOptions(right));
	}

	/** The client options a profile stores, or {@code null} when it stores none or cannot be read. */
	private JsonObject profileOptions(String name) {
		if (name.equals(activeProfile)) {
			return profileOptionIds.isEmpty() ? null : profileOptionsJson();
		}

		try {
			return Files.exists(profileFile(name)) && JsonFiles.readObject(profileFile(name)).get("options") instanceof JsonObject options ? options : null;
		} catch (IOException e) {
			return null;
		}
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

		flushWrites();
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
		ConfigMigrations.migrateModules(modulesJson);
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
		ConfigMigrations.migrateModules(modulesJson);
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

		ConfigMigrations.migrateModules(modulesJson);
		List<String> warnings = new ArrayList<>();
		JsonObject clean = modules.sanitizeModules(modulesJson, warnings);
		warnings.forEach(warning -> diagnostics.warn(SOURCE, "Profile '" + name + "': " + warning));
		if (profileExists(name)) {
			String problem = createRestorePoint("Before replacing profile " + name);
			if (problem != null) {
				return problem;
			}
		}

		JsonObject json = new JsonObject();
		json.addProperty("schemaVersion", SCHEMA_VERSION);
		json.add("modules", clean);
		flushWrites();
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
			fireLoaded();
		}

		diagnostics.info(SOURCE, "Wrote profile '" + name + "'.");
		return null;
	}

	/**
	 * Resets everything to a fresh installation's state: client options, switching rules, the HUD layout and other client
	 * data, and every module of the active profile (all off). Other profiles are kept. A restore point is made first.
	 *
	 * @return {@code null} on success, otherwise why the restore point could not be made (nothing was reset then)
	 */
	public String resetEverything() {
		String problem = createRestorePoint("Before resetting everything");
		if (problem != null) {
			return problem;
		}

		applying = true;
		try {
			globalSettings.resetAll();
			profileRules.clear();
			sections.clear();
			for (var module : modules.all()) {
				modules.resetToDefaults(module);
			}
		} finally {
			applying = false;
		}

		saveNow();
		diagnostics.info(SOURCE, "Reset all settings to defaults (a restore point was made first).");
		fireLoaded();
		return null;
	}

	// ----- restore points --------------------------------------------------------------------

	/**
	 * Saves the current state and copies all configuration files into a new restore point.
	 *
	 * @return {@code null} on success, otherwise a readable reason
	 */
	public String createRestorePoint(String label) {
		saveNow();
		return snapshotFiles(label);
	}

	/** Copies the files as they are on disk now (without saving first). */
	private String snapshotFiles(String label) {
		flushWrites();
		String slug = label.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
		Path dir = root.resolve("restore-points").resolve(timestamp() + (slug.isEmpty() ? "" : "-" + slug.substring(0, Math.min(40, slug.length()))));
		try {
			Files.createDirectories(dir.resolve("profiles"));
			if (Files.exists(globalFile())) {
				Files.copy(globalFile(), dir.resolve("femboyclient.json"), StandardCopyOption.REPLACE_EXISTING);
			}

			Path profiles = root.resolve("profiles");
			if (Files.isDirectory(profiles)) {
				try (DirectoryStream<Path> stream = Files.newDirectoryStream(profiles, "*.json")) {
					for (Path file : stream) {
						Files.copy(file, dir.resolve("profiles").resolve(file.getFileName()), StandardCopyOption.REPLACE_EXISTING);
					}
				}
			}

			JsonObject info = new JsonObject();
			info.addProperty("label", label);
			info.addProperty("created", clock.getAsLong());
			info.addProperty("schemaVersion", SCHEMA_VERSION);
			JsonFiles.writeAtomically(dir.resolve("info.json"), info);
		} catch (IOException e) {
			diagnostics.error(SOURCE, "Could not create a restore point: " + e.getMessage(), e);
			return "Could not create the restore point: " + e.getMessage();
		}

		diagnostics.info(SOURCE, "Created restore point '" + label + "'.");
		pruneRestorePoints();
		return null;
	}

	private void pruneRestorePoints() {
		List<RestorePoint> points = restorePoints();
		for (int i = MAX_RESTORE_POINTS; i < points.size(); i++) {
			deleteRestorePoint(points.get(i).id());
		}
	}

	/** Restore points, newest first. */
	public List<RestorePoint> restorePoints() {
		List<RestorePoint> points = new ArrayList<>();
		Path dir = root.resolve("restore-points");
		if (!Files.isDirectory(dir)) {
			return points;
		}

		try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, Files::isDirectory)) {
			for (Path point : stream) {
				String label = point.getFileName().toString();
				long created = 0;
				try {
					JsonObject info = JsonFiles.readObject(point.resolve("info.json"));
					if (info.get("label") instanceof JsonPrimitive primitive && primitive.isString()) label = primitive.getAsString();
					if (info.get("created") instanceof JsonPrimitive primitive && primitive.isNumber()) created = primitive.getAsLong();
				} catch (IOException e) {
					// A point without readable info is still listed under its folder name.
				}

				List<String> names = new ArrayList<>();
				if (Files.isDirectory(point.resolve("profiles"))) {
					try (DirectoryStream<Path> files = Files.newDirectoryStream(point.resolve("profiles"), "*.json")) {
						for (Path file : files) {
							String fileName = file.getFileName().toString();
							names.add(fileName.substring(0, fileName.length() - ".json".length()));
						}
					}
				}

				names.sort(String.CASE_INSENSITIVE_ORDER);
				points.add(new RestorePoint(point.getFileName().toString(), label, created, List.copyOf(names)));
			}
		} catch (IOException e) {
			diagnostics.warn(SOURCE, "Could not list restore points: " + e.getMessage());
		}

		points.sort(java.util.Comparator.comparing(RestorePoint::id).reversed());
		return points;
	}

	/**
	 * Puts the files of a restore point back and loads them. A restore point of the current state is made first, so this
	 * can be undone. Profiles created after the point are kept.
	 *
	 * @return {@code null} on success, otherwise a readable reason
	 */
	public String restore(String id) {
		Path point = restorePointDir(id);
		if (point == null) {
			return "That restore point does not exist.";
		}

		// Read the point first: making the new restore point may prune old ones, this one included.
		JsonObject global = null;
		java.util.Map<Path, JsonObject> profileFiles = new java.util.LinkedHashMap<>();
		try {
			if (Files.exists(point.resolve("femboyclient.json"))) {
				global = JsonFiles.readObject(point.resolve("femboyclient.json"));
			}

			if (Files.isDirectory(point.resolve("profiles"))) {
				try (DirectoryStream<Path> files = Files.newDirectoryStream(point.resolve("profiles"), "*.json")) {
					for (Path file : files) {
						profileFiles.put(root.resolve("profiles").resolve(file.getFileName()), JsonFiles.readObject(file));
					}
				}
			}
		} catch (IOException e) {
			return "The restore point could not be read (" + e.getMessage() + "); nothing was changed.";
		}

		String problem = createRestorePoint("Before restoring " + id);
		if (problem != null) {
			return problem;
		}

		try {
			if (global != null) {
				JsonFiles.writeAtomically(globalFile(), global);
			}

			for (var entry : profileFiles.entrySet()) {
				JsonFiles.writeAtomically(entry.getKey(), entry.getValue());
			}
		} catch (IOException e) {
			diagnostics.error(SOURCE, "Restoring failed: " + e.getMessage(), e);
			return "Restoring failed (" + e.getMessage() + "). Your previous state is in the newest restore point.";
		}

		load();
		diagnostics.info(SOURCE, "Restored the configuration from restore point " + id + ".");
		return null;
	}

	/** Deletes a restore point folder. @return {@code null} on success, otherwise a readable reason */
	public String deleteRestorePoint(String id) {
		Path point = restorePointDir(id);
		if (point == null) {
			return "That restore point does not exist.";
		}

		try (var walk = Files.walk(point)) {
			for (Path path : walk.sorted(java.util.Comparator.reverseOrder()).toList()) {
				Files.delete(path);
			}
		} catch (IOException e) {
			return "Could not delete the restore point: " + e.getMessage();
		}

		return null;
	}

	private Path restorePointDir(String id) {
		if (id == null || !id.matches("[A-Za-z0-9-]{1,80}")) {
			return null;
		}

		Path dir = root.resolve("restore-points").resolve(id);
		return Files.isDirectory(dir) ? dir : null;
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
		flushWrites();
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
			JsonObject modulesJson = JsonFiles.readObject(profileFile(name)).get("modules") instanceof JsonObject object ? object : new JsonObject();
			ConfigMigrations.migrateModules(modulesJson);
			return modulesJson;
		} catch (IOException e) {
			diagnostics.warn(SOURCE, "Could not read profile '" + name + "': " + e.getMessage());
			return null;
		}
	}

	/**
	 * Ids of the modules a profile turns on, in the order they are stored, without applying anything.
	 *
	 * @return the ids, or {@code null} if the profile could not be read
	 */
	public List<String> enabledModules(String name) {
		JsonObject modulesJson = profileModules(name);
		if (modulesJson == null) {
			return null;
		}

		List<String> enabled = new ArrayList<>();
		for (var entry : modulesJson.entrySet()) {
			if (entry.getValue() instanceof JsonObject module && module.get("enabled") instanceof com.google.gson.JsonPrimitive on && on.isBoolean()
					&& on.getAsBoolean()) {
				enabled.add(entry.getKey());
			}
		}

		return enabled;
	}

	/** Key of the client section holding the profiles' notes (a short description per profile, written by the user). */
	public static final String NOTES_SECTION = "profile_notes";

	/** The user's note for a profile, or an empty string. */
	public String profileNote(String name) {
		return section(NOTES_SECTION) instanceof JsonObject notes && notes.get(name) instanceof com.google.gson.JsonPrimitive note ? note.getAsString() : "";
	}

	/** Sets (or, when blank, removes) the note for a profile. */
	public void setProfileNote(String name, String note) {
		JsonObject notes = section(NOTES_SECTION) instanceof JsonObject existing ? existing.deepCopy() : new JsonObject();
		if (note == null || note.isBlank()) {
			notes.remove(name);
		} else {
			notes.addProperty(name, note.strip());
		}

		setSection(NOTES_SECTION, notes);
	}

	/**
	 * Checks every profile without applying anything: the values each one holds that this version would replace by defaults,
	 * and files that cannot be read. Unknown modules are listed as notes (they are kept, not dropped).
	 *
	 * @return problems by profile name; profiles without problems map to an empty list
	 */
	public java.util.Map<String, List<String>> validateProfiles() {
		java.util.Map<String, List<String>> result = new java.util.LinkedHashMap<>();
		for (String name : profiles()) {
			List<String> problems = new ArrayList<>();
			JsonObject modulesJson;
			if (name.equals(activeProfile)) {
				modulesJson = modules.saveModules();
			} else {
				try {
					JsonObject file = JsonFiles.readObject(profileFile(name));
					checkSchema(file, "The file", problems);
					modulesJson = file.get("modules") instanceof JsonObject object ? object : new JsonObject();
					ConfigMigrations.migrateModules(modulesJson);
				} catch (IOException e) {
					problems.add("The file cannot be read: " + e.getMessage());
					result.put(name, problems);
					continue;
				}
			}

			List<String> warnings = new ArrayList<>();
			modules.sanitizeModules(modulesJson, warnings);
			for (String warning : warnings) {
				problems.add(warning.startsWith("Unknown module") ? warning.replace("was skipped.", "is kept unchanged (not part of this version).") : warning);
			}

			result.put(name, problems);
		}

		return result;
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
