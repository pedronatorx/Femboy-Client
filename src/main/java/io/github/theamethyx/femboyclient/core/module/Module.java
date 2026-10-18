package io.github.theamethyx.femboyclient.core.module;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import io.github.theamethyx.femboyclient.core.setting.ModuleSettings;

/**
 * Base class for every Femboy Client module.
 *
 * <p>Lifecycle (all calls are made by {@link ModuleManager}, which catches failures):
 * <ol>
 *   <li>{@link #onRegister()} once, when the module is added to the manager;</li>
 *   <li>{@link #onEnable()} when the module starts running, i.e. it is enabled by the user
 *       <em>and</em> currently available (see {@link #availability(EnvironmentPolicy)});</li>
 *   <li>{@link #onTick()} every client tick while running; render hooks are provided by
 *       client-side interfaces;</li>
 *   <li>{@link #onWorldChange()} while running, when the client switches to another world or dimension, so
 *       cached positions from the old one are dropped;</li>
 *   <li>{@link #onDisable()} when it stops running (turned off, world unloaded, error);</li>
 *   <li>{@link #saveExtra(JsonObject)} / {@link #loadExtra(JsonObject, List)} for data beyond
 *       plain settings.</li>
 * </ol>
 */
public abstract class Module {
	public static final int NO_KEY = -1;
	private static final Pattern ID_PATTERN = Pattern.compile("[a-z0-9_]{1,48}");

	private final String id;
	private final String name;
	private final String description;
	private final ModuleCategory category;
	private final ModuleSettings settings = new ModuleSettings();
	private final boolean enabledByDefault;
	private final int defaultKey;

	private boolean enabled;
	private boolean running;
	private int key;
	private boolean holdKey;
	private String lastError;
	private long lastErrorMillis;
	private int errorCount;
	/** {@link System#nanoTime()} at the start of the last tick or render call that completed, or 0. */
	long lastSuccessNanos;
	/** Times (clock millis) of the most recent failures, newest last; used to tell a one-off error from a broken module. */
	final long[] recentFailures = new long[ModuleManager.FAILURES_BEFORE_OFF];
	int recentFailureCount;
	/** While the clock is before this, the module is suspended after a failure and will be restarted. */
	long suspendedUntil;
	/** Who turned the module off when it was not the user (a conflict or repeated failures); cleared when it is turned on. */
	String offReason;
	/** "id/phase" keys for the performance monitor, built once instead of on every call. */
	final String[] timingKeys;

	protected Module(String id, String name, String description, ModuleCategory category, boolean enabledByDefault, int defaultKey) {
		if (!ID_PATTERN.matcher(id).matches()) {
			throw new IllegalArgumentException("Module id must match " + ID_PATTERN + ": " + id);
		}

		this.id = id;
		this.name = Objects.requireNonNull(name);
		this.description = Objects.requireNonNull(description);
		this.category = Objects.requireNonNull(category);
		this.enabledByDefault = enabledByDefault;
		this.defaultKey = defaultKey;
		this.enabled = enabledByDefault;
		this.key = defaultKey;
		ModuleManager.Phase[] phases = ModuleManager.Phase.values();
		this.timingKeys = new String[phases.length];
		for (ModuleManager.Phase phase : phases) {
			timingKeys[phase.ordinal()] = id + "/" + phase.name().toLowerCase(java.util.Locale.ROOT);
		}
	}

	protected Module(String id, String name, String description, ModuleCategory category) {
		this(id, name, description, category, false, NO_KEY);
	}

	// ----- lifecycle hooks -------------------------------------------------------------------

	protected void onRegister() {
	}

	protected void onEnable() {
	}

	protected void onDisable() {
	}

	protected void onTick() {
	}

	/**
	 * Called while running when the client switches to another world or dimension (the module keeps running). Drop
	 * anything that describes the old world here: cached positions, scan results, targets.
	 */
	protected void onWorldChange() {
	}

	/** Writes data that is not a plain setting (e.g. a waypoint list). */
	protected void saveExtra(JsonObject json) {
	}

	/** Reads data written by {@link #saveExtra(JsonObject)}; add readable warnings for bad data. */
	protected void loadExtra(JsonObject json, List<String> warnings) {
	}

	/** Restores non-setting data to its defaults (used by "reset module" and profile defaults). */
	protected void resetExtra() {
	}

	// ----- availability ----------------------------------------------------------------------

	public static final String TEST_ENVIRONMENT_ONLY = "Only in singleplayer and on servers in your own network (LAN or VPN).";

	/**
	 * Whether the module automates combat or movement and therefore runs only in a test environment
	 * (see {@link EnvironmentPolicy#testEnvironment()}). Elsewhere it stays off and says why.
	 */
	public boolean testEnvironmentOnly() {
		return false;
	}

	/** What kind of module this is, shown as a badge and usable as a filter. */
	public enum Tier {
		VISUAL("Visual", "Shows information the client already has."),
		ASSIST("Assist", "Calculates and reminds; never acts for you."),
		AUTOMATION("Automation", "Acts for you within vanilla rules, on any server."),
		SANDBOX("Sandbox", TEST_ENVIRONMENT_ONLY);

		private final String label;
		private final String meaning;

		Tier(String label, String meaning) {
			this.label = label;
			this.meaning = meaning;
		}

		public String label() {
			return label;
		}

		public String meaning() {
			return meaning;
		}
	}

	/** The module's tier; every test-environment-only module is {@link Tier#SANDBOX}. */
	public Tier tier() {
		return testEnvironmentOnly() ? Tier.SANDBOX : Tier.VISUAL;
	}

	/** Whether the module needs a loaded world to do anything useful. */
	public boolean requiresWorld() {
		return true;
	}

	/**
	 * Why this module does not run in Low-Impact mode, or {@code null} when it runs there (possibly at a slower rate).
	 * Modules that repeat actions many times a second return a reason; the GUI shows it instead of pretending the module
	 * runs.
	 */
	public String lowImpactLimitation() {
		return null;
	}

	/**
	 * Ids of modules that cannot run together with this one because both control the same thing (for example your view
	 * or your motion). Turning this module on turns those off, with a message. The relation is checked both ways, so
	 * declaring it on one side is enough.
	 */
	public java.util.Set<String> conflicts() {
		return java.util.Set.of();
	}

	/**
	 * Other names people may search for (for example "ore esp" for Block ESP or "xray" for nothing at all: aliases only
	 * help find a module, they never describe features it does not have). Lower case.
	 */
	public List<String> aliases() {
		return List.of();
	}

	/**
	 * A short description of the module's current work, such as "1,240 blocks found, 64 shown", for the health monitor and
	 * the Performance Center, or {@code null}. Called only while the menu shows it, from the client thread; keep it cheap.
	 */
	public String workload() {
		return null;
	}

	/** Another module this one uses, and what for. Shown in the module's info; nothing is switched on automatically. */
	public record Requirement(String moduleId, String purpose) {
	}

	/** Modules this one uses for some of its features (for example Trackers' last-seen positions need Player Finder). */
	public List<Requirement> requirements() {
		return List.of();
	}

	public Availability availability(EnvironmentPolicy environment) {
		if (requiresWorld() && !environment.inWorld()) {
			return Availability.waiting("Waiting for a world to load.");
		}

		if (testEnvironmentOnly() && !environment.testEnvironment()) {
			return Availability.environment(TEST_ENVIRONMENT_ONLY);
		}

		String limitation = environment.lowImpactMode() ? lowImpactLimitation() : null;
		if (limitation != null) {
			return Availability.reduced("Paused in Low-Impact mode: " + limitation);
		}

		return Availability.AVAILABLE;
	}

	// ----- persistence -----------------------------------------------------------------------

	public final JsonObject toJson() {
		JsonObject json = new JsonObject();
		json.addProperty("enabled", enabled);
		json.addProperty("key", key);
		if (holdKey) {
			json.addProperty("keyMode", "hold");
		}
		json.add("settings", settings.toJson());
		JsonObject extra = new JsonObject();
		saveExtra(extra);
		if (!extra.isEmpty()) {
			json.add("extra", extra);
		}

		return json;
	}

	/**
	 * Applies saved state. The enabled flag is returned rather than applied so the manager can run
	 * the lifecycle hooks.
	 */
	final boolean loadFromJson(JsonObject json, List<String> warnings) {
		List<String> local = new ArrayList<>();

		boolean wantEnabled = enabledByDefault;
		JsonElement enabledJson = json.get("enabled");
		if (enabledJson != null) {
			if (enabledJson instanceof JsonPrimitive primitive && primitive.isBoolean()) {
				wantEnabled = primitive.getAsBoolean();
			} else {
				local.add("'enabled' must be true or false; using the default.");
			}
		}

		JsonElement keyJson = json.get("key");
		key = defaultKey;
		if (keyJson != null) {
			if (keyJson instanceof JsonPrimitive primitive && primitive.isNumber() && isValidKey(primitive.getAsInt())) {
				key = primitive.getAsInt();
			} else {
				local.add("the key bind was not a valid key code; using the default.");
			}
		}

		holdKey = false;
		JsonElement modeJson = json.get("keyMode");
		if (modeJson != null) {
			if (modeJson instanceof JsonPrimitive primitive && primitive.isString()
					&& (primitive.getAsString().equals("hold") || primitive.getAsString().equals("toggle"))) {
				holdKey = primitive.getAsString().equals("hold");
			} else {
				local.add("the key mode must be 'hold' or 'toggle'; using 'toggle'.");
			}
		}

		JsonElement settingsJson = json.get("settings");
		if (settingsJson instanceof JsonObject object) {
			local.addAll(settings.load(object));
		} else {
			settings.resetAll();
			if (settingsJson != null) {
				local.add("'settings' must be an object; using defaults.");
			}
		}

		resetExtra();
		if (json.get("extra") instanceof JsonObject extra) {
			loadExtra(extra, local);
		}

		local.forEach(warning -> warnings.add(name + ": " + warning));
		return wantEnabled;
	}

	/**
	 * Copies saved module state, keeping only values {@link #loadFromJson} would accept, without changing
	 * this module. Used to validate imported profiles before they are written.
	 */
	final JsonObject sanitize(JsonObject json, List<String> warnings) {
		JsonObject clean = new JsonObject();
		List<String> local = new ArrayList<>();
		if (json.get("enabled") instanceof JsonPrimitive primitive && primitive.isBoolean()) {
			clean.add("enabled", primitive);
		} else if (json.has("enabled")) {
			local.add("'enabled' must be true or false; it will be off");
		}

		if (json.get("key") instanceof JsonPrimitive primitive && primitive.isNumber() && isValidKey(primitive.getAsInt())) {
			clean.addProperty("key", primitive.getAsInt());
		} else if (json.has("key")) {
			local.add("the key bind is not a valid key code; it will be unbound");
		}

		if (json.get("keyMode") instanceof JsonPrimitive primitive && primitive.isString()
				&& (primitive.getAsString().equals("hold") || primitive.getAsString().equals("toggle"))) {
			clean.add("keyMode", primitive);
		} else if (json.has("keyMode")) {
			local.add("the key mode must be 'hold' or 'toggle'");
		}

		if (json.get("settings") instanceof JsonObject object) {
			clean.add("settings", settings.sanitize(object, local));
		} else if (json.has("settings")) {
			local.add("'settings' must be an object; defaults will be used");
		}

		if (json.get("extra") instanceof JsonObject extra) {
			// Checked by loadExtra when the profile is loaded; invalid entries are reported then.
			clean.add("extra", extra.deepCopy());
		}

		local.forEach(warning -> warnings.add(name + ": " + warning + "."));
		return clean;
	}

	public static boolean isValidKey(int key) {
		// GLFW key codes are in [32, 348]; NO_KEY means "unbound".
		return key == NO_KEY || (key >= 32 && key <= 348);
	}

	// ----- state -----------------------------------------------------------------------------

	public final String id() {
		return id;
	}

	public final String name() {
		return name;
	}

	public final String description() {
		return description;
	}

	public final ModuleCategory category() {
		return category;
	}

	public final ModuleSettings settings() {
		return settings;
	}

	/** The user's choice. A module can be enabled but not running (see {@link #isRunning()}). */
	public final boolean isEnabled() {
		return enabled;
	}

	/** Whether the lifecycle is currently active (enabled and available, without errors). */
	public final boolean isRunning() {
		return running;
	}

	public final boolean isEnabledByDefault() {
		return enabledByDefault;
	}

	public final int key() {
		return key;
	}

	public final int defaultKey() {
		return defaultKey;
	}

	/** Whether the key turns the module on only while it is held, instead of toggling it. */
	public final boolean isHoldKey() {
		return holdKey;
	}

	public final String lastError() {
		return lastError;
	}

	public final long lastErrorMillis() {
		return lastErrorMillis;
	}

	public final int errorCount() {
		return errorCount;
	}

	final void setEnabledFlag(boolean enabled) {
		this.enabled = enabled;
	}

	final void setRunning(boolean running) {
		this.running = running;
	}

	final void setKeyInternal(int key) {
		this.key = key;
	}

	final void setHoldKeyInternal(boolean holdKey) {
		this.holdKey = holdKey;
	}

	final void recordError(String message) {
		lastError = message;
		lastErrorMillis = System.currentTimeMillis();
		errorCount++;
	}

	final void clearError() {
		lastError = null;
		recentFailureCount = 0;
		suspendedUntil = 0;
	}

	/** Why the module was turned off by the client rather than by you (a conflict, repeated failures), or {@code null}. */
	public final String offReason() {
		return offReason;
	}

	/** Failures within the recent window that the manager still counts (see {@link ModuleManager#FAILURE_WINDOW_MILLIS}). */
	public final int recentFailures() {
		return recentFailureCount;
	}

	@Override
	public String toString() {
		return "Module[" + id + "]";
	}

	/**
	 * Whether a module can run here and now. {@code kind} says why not, so the GUI and the health monitor can tell "waiting
	 * for a world" from "not allowed here" without parsing the text.
	 */
	public record Availability(boolean available, String reason, Kind kind) {
		public enum Kind {
			/** It can run. */
			AVAILABLE,
			/** It needs something that is not there yet, such as a loaded world. */
			WAITING,
			/** It is not allowed or not possible in this environment (for example a public server). */
			ENVIRONMENT,
			/** The performance mode pauses it. */
			REDUCED,
			/** Any other reason given by the module. */
			OTHER
		}

		public static final Availability AVAILABLE = new Availability(true, "", Kind.AVAILABLE);

		public Availability(boolean available, String reason) {
			this(available, reason, available ? Kind.AVAILABLE : Kind.OTHER);
		}

		public static Availability unavailable(String reason) {
			return new Availability(false, reason, Kind.OTHER);
		}

		public static Availability waiting(String reason) {
			return new Availability(false, reason, Kind.WAITING);
		}

		public static Availability environment(String reason) {
			return new Availability(false, reason, Kind.ENVIRONMENT);
		}

		public static Availability reduced(String reason) {
			return new Availability(false, reason, Kind.REDUCED);
		}
	}
}
