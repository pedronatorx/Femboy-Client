package io.github.theamethyx.femboyclient.client.modules;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import net.fabricmc.loader.api.FabricLoader;

import io.github.theamethyx.femboyclient.client.FemboyClient;
import io.github.theamethyx.femboyclient.client.Performance;
import io.github.theamethyx.femboyclient.client.gui.Icon;
import io.github.theamethyx.femboyclient.client.gui.Theme;
import io.github.theamethyx.femboyclient.client.gui.UiContext;
import io.github.theamethyx.femboyclient.client.gui.widget.Button;
import io.github.theamethyx.femboyclient.client.gui.widget.Container;
import io.github.theamethyx.femboyclient.client.gui.widget.Widget;
import io.github.theamethyx.femboyclient.client.hud.Notifications;
import io.github.theamethyx.femboyclient.client.module.ClientHooks;
import io.github.theamethyx.femboyclient.client.render.EspRenderer;
import io.github.theamethyx.femboyclient.client.render.FrameProjection;
import io.github.theamethyx.femboyclient.client.render.RenderingUtilities;
import io.github.theamethyx.femboyclient.client.render.ScreenProjection;
import io.github.theamethyx.femboyclient.client.render.TargetLines;
import io.github.theamethyx.femboyclient.core.module.EnvironmentPolicy;
import io.github.theamethyx.femboyclient.core.module.Module;
import io.github.theamethyx.femboyclient.core.module.ModuleCategory;
import io.github.theamethyx.femboyclient.core.setting.BooleanSetting;
import io.github.theamethyx.femboyclient.core.setting.EnumSetting;
import io.github.theamethyx.femboyclient.core.setting.IdSetSetting;
import io.github.theamethyx.femboyclient.core.setting.NumberSetting;
import io.github.theamethyx.femboyclient.world.oresim.OreComparison;
import io.github.theamethyx.femboyclient.world.oresim.OreKind;
import io.github.theamethyx.femboyclient.world.oresim.OreSimulator;

/**
 * Ore Simulation: predicts ore positions around you from your singleplayer world's seed with the world generator's own
 * code ({@link OreSimulator}), shows them like Block ESP, and keeps checking them against the real world wherever its
 * chunks are loaded, so every highlight says whether it is an estimate or verified.
 *
 * <p>It only runs in singleplayer: it needs the seed, the generator and its random state, which a server never sends.
 * For your own test server, operators use {@code /femboyclient oresim compare} on the server instead (off until the
 * server's config enables it). There is no way to enter another server's seed here, so it cannot be used to get round
 * a server's anti-xray.
 */
public final class OreSimulationModule extends Module implements ClientHooks.WorldRender, ClientHooks.ScreenOverlay, ClientHooks.CustomControls {
	public static final String NEEDS_SINGLEPLAYER = "Needs your own world's seed and generator, so it only works in singleplayer. "
			+ "On your own test server, an operator can run /femboyclient oresim compare there.";
	private static final Set<String> DEFAULT_ORES = Set.of("minecraft:diamond_ore", "minecraft:gold_ore", "minecraft:iron_ore", "minecraft:redstone_ore",
			"minecraft:lapis_ore", "minecraft:emerald_ore", "minecraft:ancient_debris");

	public enum Show implements EnumSetting.Labeled {
		ALL("Estimated and verified"), VERIFIED("Only verified"), ESTIMATED("Only not yet checked");

		private final String label;

		Show(String label) {
			this.label = label;
		}

		@Override
		public String label() {
			return label;
		}
	}

	{ settings().section("Ores"); }
	private final IdSetSetting ores = settings().add(new IdSetSetting("ores", "Ores", "Which ores to predict and show.", DEFAULT_ORES));
	private final NumberSetting radius = settings().add(new NumberSetting("radius", "Radius",
			"Chunks around you to simulate. Each step adds a ring of chunks; 3 is 7×7 chunks.", 2, 1, 6, 1, "chunks"));
	private final BooleanSetting follow = settings().add(new BooleanSetting("follow", "Follow me",
			"Simulate again when you walk out of the simulated area.", true));

	{ settings().section("Highlight"); }
	private final EnumSetting<Show> show = settings().add(new EnumSetting<>("show", "Show", "Which predictions to draw.", Show.ALL));
	private final BooleanSetting hideContradicted = settings().add(new BooleanSetting("hide_contradicted", "Hide predictions the world disproves",
			"Predictions whose block turned out to be something else (or was mined) are not drawn.", true));
	private final BooleanSetting outline = settings().add(new BooleanSetting("outline", "Outline", "Draw the edges of each block.", true));
	private final BooleanSetting fill = settings().add(new BooleanSetting("fill", "Fill verified", "Fill verified blocks with a faint tint; estimates are outlines only.", true));
	private final BooleanSetting throughWalls = settings().add(new BooleanSetting("through_walls", "Through walls", "Draw through blocks.", true));
	private final NumberSetting opacity = settings().add(new NumberSetting("opacity", "Opacity", "How solid outlines are; estimates are drawn fainter.",
			0.8, 0.1, 1, 0.05, ""));

	{ settings().section("Labels"); }
	private final BooleanSetting labels = settings().add(new BooleanSetting("labels", "Name, distance and state",
			"Show the ore, its distance and whether it is verified above the nearest highlights.", true));
	private final NumberSetting maxLabels = settings().add(new NumberSetting("max_labels", "Most labels", "Labels for the nearest blocks only.",
			8, 1, 64, 1, "")).visibleWhen(labels::get).advanced();

	private final TargetLines.Settings lines = new TargetLines.Settings(settings(), false, TargetLines.Visibility.ALWAYS, 48, 160, 8);

	{ settings().section("Performance"); }
	private final NumberSetting limit = settings().add(new NumberSetting("limit", "Maximum shown", "Closest predictions drawn at most.", 300, 50, 2000, 50, ""));
	private final NumberSetting recheck = settings().add(new NumberSetting("recheck", "Check against the world every",
			"How often predictions are compared with the loaded chunks (newly loaded chunks become verified).", 2, 1, 30, 1, "s")).advanced();
	private final NumberSetting cacheSize = settings().add(new NumberSetting("cache", "Areas kept",
			"Simulated areas kept in memory, so walking back does not simulate again. Cleared when you leave the world.", 4, 1, 16, 1, "")).advanced();

	private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(runnable -> {
		Thread thread = new Thread(runnable, "Femboy Client ore simulation");
		thread.setDaemon(true);
		thread.setPriority(Thread.MIN_PRIORITY);
		return thread;
	});

	private final Map<String, OreSimulator.Result> cache = new LinkedHashMap<>(16, 0.75f, true);
	private CompletableFuture<OreSimulator.Result> running;
	private AtomicBoolean cancel = new AtomicBoolean();
	private volatile int progressDone;
	private volatile int progressTotal;
	private String runningKey;
	private OreSimulator.Result result;
	private OreComparison.Outcome outcome;
	private List<Shown> shown = List.of();
	private BlockPos shownFrom;
	private long nextCheck;
	private boolean dirty = true;
	private String status = "Waiting for the first simulation.";
	private String problem;
	private int simulations;
	private int ticksUntilSightCheck;

	/** One drawn prediction. */
	public static final class Shown implements TargetLines.Target {
		private final OreSimulator.Prediction prediction;
		private final OreComparison.Check check;
		private final AABB box;
		private final Vec3 center;
		private boolean sight = true;

		Shown(OreSimulator.Prediction prediction, OreComparison.Check check) {
			this.prediction = prediction;
			this.check = check;
			this.box = new AABB(prediction.pos());
			this.center = Vec3.atCenterOf(prediction.pos());
		}

		public OreSimulator.Prediction prediction() {
			return prediction;
		}

		public OreComparison.Check check() {
			return check;
		}

		@Override
		public Vec3 linePoint(float partialTick) {
			return center;
		}

		@Override
		public int lineColor() {
			return prediction.ore().color();
		}

		@Override
		public boolean lineOfSight() {
			return sight;
		}
	}

	public OreSimulationModule() {
		super("ore_simulation", "Ore Simulation",
				"Predicts ores around you from your singleplayer world's seed and checks each prediction against the real world as chunks load.",
				ModuleCategory.WORLD, false, NO_KEY);
		ores.choices(() -> {
			List<IdSetSetting.Choice> choices = new ArrayList<>();
			for (OreKind kind : OreKind.values()) {
				boolean nether = kind == OreKind.QUARTZ || kind == OreKind.ANCIENT_DEBRIS;
				choices.add(new IdSetSetting.Choice(id(kind), kind.label(), nether ? "Nether" : "Overworld"));
			}

			return choices;
		});
		Runnable reselect = () -> dirty = true;
		show.onChange(value -> reselect.run());
		hideContradicted.onChange(value -> reselect.run());
		limit.onChange(value -> reselect.run());
		ores.onChange(value -> {
			nextCheck = 0;
			reselect.run();
		});
	}

	/** The setting id of an ore kind: its stone ore block (deepslate and nether variants count with it). */
	static String id(OreKind kind) {
		return switch (kind) {
			case QUARTZ -> "minecraft:nether_quartz_ore";
			case ANCIENT_DEBRIS -> "minecraft:ancient_debris";
			default -> "minecraft:" + kind.name().toLowerCase(Locale.ROOT) + "_ore";
		};
	}

	@Override
	public boolean testEnvironmentOnly() {
		// It shows ore positions hidden in the ground.
		return true;
	}

	@Override
	public Availability availability(EnvironmentPolicy environment) {
		Availability base = super.availability(environment);
		if (!base.available()) {
			return base;
		}

		return Minecraft.getInstance().getSingleplayerServer() == null ? Availability.environment(NEEDS_SINGLEPLAYER) : base;
	}

	@Override
	public List<String> aliases() {
		return List.of("oresim", "ore sim", "seed ores", "seed analysis", "ore prediction");
	}

	/** The ore kinds chosen in the settings. */
	public Set<OreKind> kinds() {
		Set<OreKind> kinds = EnumSet.noneOf(OreKind.class);
		for (OreKind kind : OreKind.values()) {
			if (ores.get().contains(id(kind))) {
				kinds.add(kind);
			}
		}

		return kinds;
	}

	@Override
	protected void onEnable() {
		dirty = true;
		problem = null;
	}

	@Override
	protected void onDisable() {
		cancelRun();
		shown = List.of();
	}

	@Override
	protected void onWorldChange() {
		cancelRun();
		cache.clear();
		result = null;
		outcome = null;
		shown = List.of();
		problem = null;
		status = "Waiting for the first simulation.";
	}

	/** Stops a simulation still running; its result is thrown away. */
	public void cancelRun() {
		cancel.set(true);
		running = null;
		runningKey = null;
	}

	/** Forgets every simulated area (the current one is simulated again). */
	public void clearCache() {
		cancelRun();
		cache.clear();
		result = null;
		outcome = null;
		shown = List.of();
		status = "Cache cleared.";
	}

	/** Simulates the area around you now, even if it is cached. */
	public void runNow() {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null) {
			return;
		}

		// The current result stays shown until the new one is ready (or the run is cancelled).
		cache.remove(key(client.level.dimension().identifier().toString(), client.player.chunkPosition()));
		problem = null;
		start(client, client.player.chunkPosition());
	}

	private String key(String dimension, ChunkPos center) {
		return dimension + "|" + center.x + "|" + center.z + "|" + radius.intValue();
	}

	@Override
	protected void onTick() {
		Minecraft client = Minecraft.getInstance();
		IntegratedServer server = client.getSingleplayerServer();
		if (server == null || client.player == null) {
			return;
		}

		ChunkPos here = client.player.chunkPosition();
		if (running != null) {
			if (running.isDone()) {
				finish(client);
			}
		} else if (problem == null && needsRun(here)) {
			String key = key(client.level.dimension().identifier().toString(), here);
			OreSimulator.Result cached = cache.get(key);
			if (cached != null) {
				use(cached);
			} else {
				start(client, here);
			}
		}

		long now = System.currentTimeMillis();
		if (result != null && now >= nextCheck) {
			nextCheck = now + Performance.periodMillis(recheck.intValue() * 1000L);
			outcome = OreComparison.compare(result, kinds(), (x, z) -> client.level.getChunkSource().getChunk(x, z, ChunkStatus.FULL, false));
			dirty = true;
		}

		BlockPos at = client.player.blockPosition();
		if (dirty || shownFrom == null || shownFrom.distManhattan(at) >= 3) {
			select(at);
		}

		if (lines.enabled.get() && lines.visibility.get() == TargetLines.Visibility.LINE_OF_SIGHT && --ticksUntilSightCheck <= 0) {
			ticksUntilSightCheck = Performance.interval(5);
			int checks = Math.min(shown.size(), lines.maxCount.intValue());
			for (int i = 0; i < checks; i++) {
				Shown target = shown.get(i);
				target.sight = TargetLines.lineOfSight(target.center, target.prediction.pos());
			}
		}
	}

	private boolean needsRun(ChunkPos here) {
		if (result == null) {
			return true;
		}

		if (!follow.get()) {
			return false;
		}

		int r = result.radius();
		return Math.abs(here.x - result.center().x) > Math.max(0, r - 1) || Math.abs(here.z - result.center().z) > Math.max(0, r - 1)
				|| r != radius.intValue();
	}

	private void start(Minecraft client, ChunkPos center) {
		IntegratedServer server = client.getSingleplayerServer();
		ServerLevel level = server == null ? null : server.getLevel(client.level.dimension());
		if (level == null) {
			return;
		}

		if (!OreSimulator.supports(level)) {
			problem = OreSimulator.UNSUPPORTED_GENERATOR;
			status = problem;
			return;
		}

		cancel.set(true);
		AtomicBoolean stop = new AtomicBoolean();
		cancel = stop;
		int chunks = radius.intValue();
		progressDone = 0;
		progressTotal = (2 * chunks + 3) * (2 * chunks + 3);
		runningKey = key(level.dimension().identifier().toString(), center);
		status = "Simulating " + (2 * chunks + 1) + "×" + (2 * chunks + 1) + " chunks around chunk " + center.x + " " + center.z + "...";
		OreSimulator.Progress progress = new OreSimulator.Progress() {
			@Override
			public boolean cancelled() {
				return stop.get();
			}

			@Override
			public void progress(int done, int total, String stage) {
				progressDone = done;
				progressTotal = total;
			}
		};
		// Every ore kind is simulated, so changing the ore filter only changes what is drawn.
		running = CompletableFuture.supplyAsync(() -> OreSimulator.simulate(level, center, chunks, EnumSet.allOf(OreKind.class), progress), WORKER);
	}

	private void finish(Minecraft client) {
		CompletableFuture<OreSimulator.Result> done = running;
		String key = runningKey;
		running = null;
		runningKey = null;
		try {
			OreSimulator.Result next = done.join();
			cache.put(key, next);
			while (cache.size() > cacheSize.intValue()) {
				cache.remove(cache.keySet().iterator().next());
			}

			simulations++;
			use(next);
		} catch (Exception e) {
			Throwable cause = e.getCause() != null ? e.getCause() : e;
			if (cause instanceof OreSimulator.CancelledException) {
				status = "Cancelled.";
				return;
			}

			problem = "The simulation failed: " + cause.getMessage();
			status = problem;
			Notifications.post(Notifications.Category.ERROR, "Ore Simulation", problem, Theme.ERROR);
		}
	}

	private void use(OreSimulator.Result next) {
		result = next;
		nextCheck = 0;
		dirty = true;
		status = String.format(Locale.ROOT, "%,d ores predicted in %d×%d chunks around chunk %d %d (%,d ms).", next.predictions().size(),
				2 * next.radius() + 1, 2 * next.radius() + 1, next.center().x, next.center().z, next.millis());
	}

	private void select(BlockPos at) {
		dirty = false;
		shownFrom = at;
		if (result == null) {
			shown = List.of();
			return;
		}

		Set<OreKind> kinds = kinds();
		List<Shown> candidates = new ArrayList<>();
		Map<BlockPos, OreComparison.Check> checks = outcome == null ? Map.of() : outcome.checks();
		for (OreSimulator.Prediction prediction : result.predictions()) {
			if (!kinds.contains(prediction.ore())) {
				continue;
			}

			OreComparison.Check check = checks.getOrDefault(prediction.pos(), OreComparison.Check.ESTIMATED);
			boolean wanted = switch (show.get()) {
				case ALL -> check != OreComparison.Check.CONTRADICTED || !hideContradicted.get();
				case VERIFIED -> check == OreComparison.Check.VERIFIED;
				case ESTIMATED -> check == OreComparison.Check.ESTIMATED;
			};
			if (wanted) {
				candidates.add(new Shown(prediction, check));
			}
		}

		candidates.sort(Comparator.comparingDouble(s -> s.prediction.pos().distSqr(at)));
		int max = Performance.cap(limit.intValue(), 150);
		shown = List.copyOf(candidates.subList(0, Math.min(max, candidates.size())));
		ticksUntilSightCheck = 0;
	}

	// ----- state for the GUI, HUD and tests --------------------------------------------------

	public OreSimulator.Result result() {
		return result;
	}

	public OreComparison.Outcome outcome() {
		return outcome;
	}

	public List<Shown> shown() {
		return shown;
	}

	public boolean simulating() {
		return running != null;
	}

	/** Completed simulations (not counting areas taken from the cache). */
	public int simulations() {
		return simulations;
	}

	public String status() {
		return status;
	}

	public TargetLines.Settings lines() {
		return lines;
	}

	public NumberSetting radiusSetting() {
		return radius;
	}

	/** 0..1 while a simulation runs. */
	public float progress() {
		int total = progressTotal;
		return total <= 0 ? 0 : Math.min(1, progressDone / (float) total);
	}

	/** Where the seed comes from, for the panel's seed section. */
	public static String seedSource() {
		IntegratedServer server = Minecraft.getInstance().getSingleplayerServer();
		if (server == null) {
			return "No seed: you are not in singleplayer. Servers never send their seed, and this module does not take one.";
		}

		return "Seed " + server.getWorldData().worldGenOptions().seed() + ", read from your singleplayer world (exact).";
	}

	/** Whether SeedcrackerX, an independent seed-cracking mod, is installed alongside (it is not bundled or controlled). */
	public static boolean seedcrackerInstalled() {
		return FabricLoader.getInstance().isModLoaded("seedcrackerx");
	}

	/** Writes the current predictions and checks to a CSV file in the config folder; returns the file or {@code null}. */
	public Path export() {
		if (result == null) {
			return null;
		}

		Path file = FabricLoader.getInstance().getConfigDir().resolve(FemboyClient.MOD_ID).resolve("oresim")
				.resolve("oresim-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".csv");
		try {
			OreComparison.exportCsv(result, outcome == null ? Map.of() : outcome.checks(), file);
			Notifications.post(Notifications.Category.GENERAL, "Ore Simulation", "Exported to " + file.getFileName(), Theme.SUCCESS);
			return file;
		} catch (Exception e) {
			Notifications.post(Notifications.Category.ERROR, "Ore Simulation", "Export failed: " + e.getMessage(), Theme.ERROR);
			return null;
		}
	}

	@Override
	public String workload() {
		return String.format(Locale.ROOT, "%d areas cached, %d predictions, %d shown, %d lines%s", cache.size(),
				result == null ? 0 : result.predictions().size(), shown.size(), lines.drawn(), simulating() ? ", simulating" : "");
	}

	// ----- rendering -------------------------------------------------------------------------

	@Override
	public void renderWorld(RenderingUtilities render) {
		float alpha = opacity.get().floatValue();
		boolean walls = throughWalls.get();
		for (Shown block : shown) {
			if (!FrameProjection.inView(block.box)) {
				continue;
			}

			int rgb = block.prediction.ore().color();
			boolean verified = block.check == OreComparison.Check.VERIFIED;
			if (fill.get() && verified) {
				render.filledBox(block.box, EspRenderer.withAlpha(rgb, alpha * 0.25f), walls);
			}

			if (outline.get()) {
				AABB box = verified ? block.box : block.box.deflate(0.12);
				render.boxOutline(box, EspRenderer.withAlpha(block.check == OreComparison.Check.CONTRADICTED ? Theme.ERROR : rgb,
						verified ? alpha : alpha * 0.55f), verified ? 1.5f : 1f, walls);
			}
		}

		lines.draw(render, shown);
	}

	@Override
	public void renderOverlay(UiContext ui, int screenWidth, int screenHeight, float partialTick) {
		if (!labels.get()) {
			return;
		}

		Vec3 camera = Minecraft.getInstance().gameRenderer.getMainCamera().position();
		int drawn = 0;
		for (Shown block : shown) {
			if (drawn >= maxLabels.intValue()) {
				break;
			}

			int[] at = ScreenProjection.toScreen(block.center.add(0, 0.7, 0), screenWidth, screenHeight);
			if (at == null) {
				continue;
			}

			ui.worldLabel(block.prediction.ore().label() + "  " + Math.round(block.center.distanceTo(camera)) + "m  " + block.check.label().toLowerCase(Locale.ROOT),
					at[0], at[1], block.prediction.ore().color());
			drawn++;
		}
	}

	// ----- panel -----------------------------------------------------------------------------

	@Override
	public List<Widget> createControls(UiContext ui, Runnable refresh) {
		return List.of(new StatusCard(), new Actions(), new SeedCard());
	}

	/** Progress while simulating, the last result, and how well it matched the world. */
	private final class StatusCard extends Widget {
		@Override
		public int preferredHeight(UiContext ui, int width) {
			return 18 + ui.wrappedHeight(status, width - 16) + ui.wrappedHeight(summary(), width - 16) + 12;
		}

		private String summary() {
			if (outcome == null) {
				return "Each highlight is an estimate until its chunk is loaded and compared with the real block.";
			}

			return outcome.summary().describe();
		}

		@Override
		public void render(UiContext ui) {
			ui.roundRect(x, y, width, height, Theme.SURFACE);
			ui.icon(simulating() ? Icon.CLOCK : Icon.ANALYSIS, x + 6, y + 6, 9, Theme.ACCENT);
			ui.text(simulating() ? "Simulating" : "Ore simulation", x + 19, y + 7, Theme.TEXT);
			int cy = y + 19;
			if (simulating()) {
				ui.roundRect(x + 90, y + 8, width - 98, 5, Theme.SURFACE_HOVER, 2);
				ui.roundRect(x + 90, y + 8, Math.max(2, (int) ((width - 98) * progress())), 5, Theme.ACCENT, 2);
			}

			ui.wrapped(status, x + 8, cy, width - 16, problem != null ? Theme.ERROR : Theme.TEXT_SECONDARY);
			cy += ui.wrappedHeight(status, width - 16) + 4;
			ui.wrapped(summary(), x + 8, cy, width - 16, Theme.TEXT_MUTED);
		}
	}

	private final class Actions extends Container {
		private final Button run;
		private final Button stop;
		private final Button exportButton;
		private final Button clear;

		Actions() {
			run = add(new Button("Simulate now", Button.Style.PRIMARY, OreSimulationModule.this::runNow).icon(Icon.PLAY));
			run.enabledWhen(() -> isRunning() && !simulating());
			run.tooltip("Simulate the area around you again, even if it was simulated before.");
			stop = add(new Button("Cancel", Button.Style.SECONDARY, OreSimulationModule.this::cancelRun).icon(Icon.STOP));
			stop.enabledWhen(OreSimulationModule.this::simulating);
			stop.tooltip("Stop the simulation that is running.");
			exportButton = add(new Button("Export CSV", Button.Style.SECONDARY, OreSimulationModule.this::export).icon(Icon.SAVE));
			exportButton.enabledWhen(() -> result != null);
			exportButton.tooltip("Write every prediction with its check (verified, not there, estimated) to config/femboyclient/oresim.");
			clear = add(new Button("Clear cache", Button.Style.SECONDARY, OreSimulationModule.this::clearCache).icon(Icon.TRASH));
			clear.tooltip("Forget the simulated areas kept in memory.");
		}

		@Override
		public int preferredHeight(UiContext ui, int width) {
			return width < 300 ? 15 * 2 + Theme.GAP : 15;
		}

		@Override
		protected void onLayout() {
			Button[] buttons = {run, stop, exportButton, clear};
			int perRow = width < 300 ? 2 : 4;
			int w = (width - (perRow - 1) * Theme.GAP) / perRow;
			for (int i = 0; i < buttons.length; i++) {
				int row = i / perRow;
				int col = i % perRow;
				buttons[i].setBounds(x + col * (w + Theme.GAP), y + row * (15 + Theme.GAP), w, 15);
			}
		}
	}

	/** Where the seed comes from and what this can and cannot tell. */
	private static final class SeedCard extends Widget {
		private static String text() {
			return "Seed analysis. " + seedSource() + " Predictions use the generator's own terrain, surface, cave and ore code; "
					+ "lakes, geodes, dungeons and structures are not simulated, so ores near them can be wrong. "
					+ (seedcrackerInstalled() ? "SeedcrackerX is installed as a separate mod; Femboy Client does not read or control it."
							: "Recovering an unknown seed is not part of Femboy Client.");
		}

		@Override
		public int preferredHeight(UiContext ui, int width) {
			return ui.wrappedHeight(text(), width - 16) + 10;
		}

		@Override
		public void render(UiContext ui) {
			ui.wrapped(text(), x + 8, y + 5, width - 16, Theme.TEXT_MUTED);
		}
	}
}
