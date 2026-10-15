package io.github.theamethyx.femboyclient.client.modules;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;

import io.github.theamethyx.femboyclient.client.FemboyClient;
import io.github.theamethyx.femboyclient.client.gui.UiContext;
import io.github.theamethyx.femboyclient.client.gui.widget.RunnerPanel;
import io.github.theamethyx.femboyclient.client.gui.widget.Widget;
import io.github.theamethyx.femboyclient.client.module.ClientHooks;
import io.github.theamethyx.femboyclient.core.diag.DiagnosticLogger;
import io.github.theamethyx.femboyclient.core.module.Module;
import io.github.theamethyx.femboyclient.core.module.ModuleCategory;
import io.github.theamethyx.femboyclient.core.runner.JavaRunner;
import io.github.theamethyx.femboyclient.core.runner.RiskScanner;
import io.github.theamethyx.femboyclient.core.setting.NumberSetting;
import io.github.theamethyx.femboyclient.core.setting.Setting;
import io.github.theamethyx.femboyclient.core.setting.StringSetting;

/**
 * Write and run small Java programs from the menu.
 *
 * <p>Code never runs inside Minecraft. Each run starts a separate Java process (see {@link JavaRunner}) with
 * a memory cap, a time limit and an output limit; Stop kills it. The program can read a snapshot of the
 * client taken when you press Run (versions, frame rate, module states and settings, diagnostics) through
 * the {@code Femboy} class, but it has no access to the game itself, your world or other players.
 *
 * <p>The process runs with your normal user permissions, so it is not a security sandbox. Code that uses
 * APIs able to change files, start programs or use the network needs an extra confirmation, and nothing
 * ever runs without you pressing Run.
 */
public final class JavaRunnerModule extends Module implements ClientHooks.CustomControls {
	public record Example(String title, String code) {
	}

	public static final List<Example> EXAMPLES = List.of(
			new Example("Versions", """
					System.out.println("Femboy Client " + Femboy.clientVersion());
					System.out.println("Minecraft " + Femboy.minecraftVersion());
					System.out.println("Game Java " + Femboy.gameJavaVersion() + ", runner Java " + System.getProperty("java.version"));
					System.out.println("Connection: " + Femboy.connection() + ", " + Femboy.fps() + " fps");"""),
			new Example("Module overview", """
					List<List<?>> rows = new ArrayList<>();
					for (String id : Femboy.modules()) {
					    rows.add(List.of(Femboy.moduleName(id), Femboy.enabled(id) ? "on" : "off", Femboy.running(id) ? "running" : "-"));
					}
					Femboy.printTable(List.of("Module", "Enabled", "Running"), rows);"""),
			new Example("Settings of a module", """
					// Settings exactly as shown in the menu, by setting id.
					Femboy.settings("entity_esp").forEach((id, value) -> System.out.println(id + " = " + value));"""),
			new Example("Diagnostics", """
					List<String> messages = Femboy.diagnostics();
					System.out.println(messages.size() + " recent messages");
					messages.forEach(System.out::println);"""),
			new Example("Nether coordinates", """
					int x = 1200, z = -340;
					System.out.printf("Overworld %d, %d -> Nether %d, %d%n", x, z, Math.floorDiv(x, 8), Math.floorDiv(z, 8));
					long ticks = 24000 * 3;
					System.out.println(ticks + " ticks = " + ticks / 20 / 60 + " minutes of game time");"""),
			new Example("Error handling", """
					try {
					    Integer.parseInt("not a number");
					} catch (NumberFormatException e) {
					    System.out.println("Caught: " + e.getMessage());
					}
					throw new IllegalStateException("Uncaught exceptions show the line they came from");"""));

	{ settings().section("Limits"); }
	private final NumberSetting timeout = settings().add(new NumberSetting("timeout", "Time limit",
			"A run is stopped after this long, including the time to start Java and compile.", 15, 2, 120, 1, "s"));
	private final NumberSetting memory = settings().add(new NumberSetting("memory", "Memory limit",
			"Maximum heap of the runner process.", 256, 64, 1024, 64, "MB"));
	private final NumberSetting maxOutput = settings().add(new NumberSetting("max_output", "Output limit",
			"A run that prints more than this is stopped.", 256, 16, 2048, 16, "KB"));

	{ settings().section("Java"); }
	private final StringSetting javaPath = settings().add(new StringSetting("java_path", "Java for the runner",
			"Path to the java program used for runs. Empty uses the Java Minecraft runs on; if that has no compiler (the launcher's "
					+ "Java often doesn't), enter a JDK 21+, e.g. /usr/lib/jvm/java-21/bin/java.", "", 512, "auto: the game's Java"));

	private String source = EXAMPLES.getFirst().code();
	private JavaRunner.Execution execution;
	private List<String> pendingRisks = List.of();

	public JavaRunnerModule() {
		super("java_runner", "Java Runner", "Write and run small Java programs in a separate process, with output, errors and limits.",
				ModuleCategory.DEVELOPER, false, NO_KEY);
	}

	@Override
	public boolean requiresWorld() {
		return false;
	}

	public String source() {
		return source;
	}

	public void setSource(String text) {
		if (!text.equals(source)) {
			source = text;
			pendingRisks = List.of();
			FemboyClient femboy = FemboyClient.get();
			if (femboy != null) {
				femboy.config().markDirty();
			}
		}
	}

	public JavaRunner.Execution execution() {
		return execution;
	}

	/** Risky APIs found in the code waiting for confirmation; empty when nothing waits. */
	public List<String> pendingRisks() {
		return pendingRisks;
	}

	/**
	 * Runs the current source, unless it uses risky APIs: then those are returned and nothing runs until
	 * {@link #confirmRun()}.
	 */
	public List<String> run() {
		if (!isRunning() || execution != null && execution.status().active()) {
			return List.of();
		}

		List<String> risks = RiskScanner.scan(source);
		if (!risks.isEmpty()) {
			pendingRisks = risks;
			return risks;
		}

		start();
		return List.of();
	}

	public void confirmRun() {
		if (!pendingRisks.isEmpty() && isRunning()) {
			pendingRisks = List.of();
			start();
		}
	}

	public void cancelRun() {
		pendingRisks = List.of();
	}

	public void stop() {
		if (execution != null) {
			execution.stop();
		}
	}

	public void clearOutput() {
		if (execution == null || !execution.status().active()) {
			execution = null;
		}
	}

	private void start() {
		execution = JavaRunner.start(source, snapshot(), config());
	}

	@Override
	protected void onDisable() {
		stop();
		pendingRisks = List.of();
	}

	// ----- process configuration -------------------------------------------------------------

	public JavaRunner.Config config() {
		return new JavaRunner.Config(javaExecutable(), classpath(), FabricLoader.getInstance().getConfigDir().resolve(FemboyClient.MOD_ID).resolve("runner"),
				timeout.intValue(), memory.intValue(), maxOutput.intValue() * 1024);
	}

	private Path javaExecutable() {
		if (!javaPath.get().isBlank()) {
			return Path.of(javaPath.get().trim());
		}

		Path game = ProcessHandle.current().info().command().map(Path::of).orElse(null);
		if (game != null && Files.isExecutable(game) && game.getFileName().toString().startsWith("java")) {
			return game;
		}

		return Path.of(System.getProperty("java.home"), "bin", "java");
	}

	/** Only Femboy Client's own files: the runner never gets Minecraft or other mods on its classpath. */
	private static List<Path> classpath() {
		return FabricLoader.getInstance().getModContainer(FemboyClient.MOD_ID).map(ModContainer::getOrigin)
				.map(origin -> origin.getPaths()).orElse(List.of());
	}

	/** The read-only view of the client that the runner's {@code Femboy} class exposes. */
	public Map<String, String> snapshot() {
		Map<String, String> snapshot = new LinkedHashMap<>();
		FemboyClient femboy = FemboyClient.get();
		Minecraft client = Minecraft.getInstance();
		snapshot.put("client.version", FabricLoader.getInstance().getModContainer(FemboyClient.MOD_ID)
				.map(mod -> mod.getMetadata().getVersion().getFriendlyString()).orElse("unknown"));
		snapshot.put("minecraft.version", SharedConstants.getCurrentVersion().name());
		snapshot.put("java.version", System.getProperty("java.version"));
		snapshot.put("fps", String.valueOf(client.getFps()));
		snapshot.put("connection", femboy.modules().environment().describe());
		for (Module module : femboy.modules().all()) {
			snapshot.put("module." + module.id() + ".name", module.name());
			snapshot.put("module." + module.id() + ".enabled", String.valueOf(module.isEnabled()));
			snapshot.put("module." + module.id() + ".running", String.valueOf(module.isRunning()));
			for (Setting<?> setting : module.settings().all()) {
				snapshot.put("setting." + module.id() + "." + setting.id(), setting.displayValue());
			}
		}

		List<DiagnosticLogger.Entry> entries = new ArrayList<>(femboy.diagnostics().snapshot());
		int from = Math.max(0, entries.size() - 50);
		for (int i = from; i < entries.size(); i++) {
			DiagnosticLogger.Entry entry = entries.get(i);
			snapshot.put("diag." + (i - from), entry.level() + " " + entry.source() + ": " + entry.message());
		}

		return snapshot;
	}

	// ----- persistence and GUI ---------------------------------------------------------------

	@Override
	protected void saveExtra(JsonObject json) {
		json.addProperty("source", source);
	}

	@Override
	protected void loadExtra(JsonObject json, List<String> warnings) {
		if (json.get("source") instanceof JsonPrimitive text && text.isString()) {
			source = text.getAsString();
		}
	}

	@Override
	protected void resetExtra() {
		source = EXAMPLES.getFirst().code();
	}

	@Override
	public List<Widget> createControls(UiContext ui, Runnable refresh) {
		return List.of(new RunnerPanel(this));
	}
}
