package io.github.theamethyx.femboyclient.client.modules;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import com.mojang.datafixers.util.Pair;

import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.phys.Vec3;

import io.github.theamethyx.femboyclient.client.gui.Theme;
import io.github.theamethyx.femboyclient.client.gui.UiContext;
import io.github.theamethyx.femboyclient.client.hud.HudAnchor;
import io.github.theamethyx.femboyclient.client.hud.HudLine;
import io.github.theamethyx.femboyclient.client.module.ClientHooks;
import io.github.theamethyx.femboyclient.client.render.RenderingUtilities;
import io.github.theamethyx.femboyclient.client.render.ScreenProjection;
import io.github.theamethyx.femboyclient.core.module.EnvironmentPolicy;
import io.github.theamethyx.femboyclient.core.module.Module;
import io.github.theamethyx.femboyclient.core.module.ModuleCategory;
import io.github.theamethyx.femboyclient.core.setting.IdSetSetting;
import io.github.theamethyx.femboyclient.core.setting.NumberSetting;

/**
 * Finds the nearest structures of the chosen types with the same search as {@code /locate}, run on your singleplayer
 * world. Where a structure generates depends on the world seed, which multiplayer servers never send, so this only works
 * in singleplayer. Searches run one structure type at a time, again when you have moved far enough.
 */
public final class StructureFinderModule extends Module implements ClientHooks.HudBlock, ClientHooks.WorldRender, ClientHooks.ScreenOverlay {
	public static final String NEEDS_SEED = "Needs the world seed, so it only works in singleplayer (servers never send the seed).";

	private final IdSetSetting structures = settings().add(new IdSetSetting("structures", "Structures", "Structure types to find.",
			Set.of("minecraft:village_plains", "minecraft:stronghold", "minecraft:trial_chambers", "minecraft:ancient_city"))).choices(this::choices);
	private final NumberSetting radius = settings().add(new NumberSetting("radius", "Search radius", "How far to search, like /locate (100 there).",
			100, 8, 200, 4, "chunks"));
	private final NumberSetting moveAgain = settings().add(new NumberSetting("move_again", "Search again after", "Search again once you are this far from the last search.",
			256, 32, 2048, 32, "blocks"));

	/** One found structure. */
	public record Found(String id, String label, BlockPos pos) {
	}

	private final List<Found> found = new ArrayList<>();
	private List<String> queue = List.of();
	private final List<Found> pending = new ArrayList<>();
	private CompletableFuture<Found> running;
	private String runningId;
	private BlockPos searchedFrom;
	private Set<String> searchedFor = Set.of();
	private int searches;

	public StructureFinderModule() {
		super("structure_finder", "Structure Finder", "Nearest villages, strongholds, trial chambers and other structures (singleplayer only).",
				ModuleCategory.WORLD, false, NO_KEY);
	}

	@Override
	public Tier tier() {
		return Tier.ASSIST;
	}

	@Override
	public Availability availability(EnvironmentPolicy environment) {
		Availability base = super.availability(environment);
		if (!base.available()) {
			return base;
		}

		return Minecraft.getInstance().getSingleplayerServer() == null ? Availability.environment(NEEDS_SEED) : base;
	}

	public List<Found> found() {
		return List.copyOf(found);
	}

	/** Completed search rounds (for tests). */
	public int searches() {
		return searches;
	}

	private List<IdSetSetting.Choice> choices() {
		IntegratedServer server = Minecraft.getInstance().getSingleplayerServer();
		if (server == null) {
			List<IdSetSetting.Choice> current = new ArrayList<>();
			structures.get().forEach(id -> current.add(new IdSetSetting.Choice(id, label(id), "Structures")));
			return current;
		}

		List<IdSetSetting.Choice> all = new ArrayList<>();
		server.registryAccess().lookupOrThrow(Registries.STRUCTURE).keySet().stream().map(Identifier::toString).sorted()
				.forEach(id -> all.add(new IdSetSetting.Choice(id, label(id), id.startsWith("minecraft:") ? "Vanilla" : "Mods")));
		return all;
	}

	private static String label(String id) {
		String path = id.substring(id.indexOf(':') + 1).replace('_', ' ');
		return Character.toUpperCase(path.charAt(0)) + path.substring(1);
	}

	@Override
	protected void onEnable() {
		reset();
	}

	@Override
	protected void onDisable() {
		reset();
	}

	@Override
	protected void onWorldChange() {
		// Another dimension has other structures: drop the results and any search still running for the old one.
		reset();
	}

	private void reset() {
		found.clear();
		pending.clear();
		queue = List.of();
		searchedFrom = null;
		searchedFor = Set.of();
		// A search still running on the server thread finishes there; its result is no longer read.
		running = null;
		runningId = null;
	}

	@Override
	protected void onTick() {
		Minecraft client = Minecraft.getInstance();
		IntegratedServer server = client.getSingleplayerServer();
		if (server == null) {
			return;
		}

		BlockPos here = client.player.blockPosition();
		if (running == null && queue.isEmpty()
				&& (searchedFrom == null || !searchedFor.equals(structures.get()) || !here.closerThan(searchedFrom, moveAgain.get()))) {
			queue = new ArrayList<>(structures.get().stream().sorted().toList());
			pending.clear();
			searchedFrom = here;
			searchedFor = Set.copyOf(structures.get());
		}

		if (running != null) {
			if (!running.isDone()) {
				return;
			}

			Found result = running.getNow(null);
			if (result != null) {
				pending.add(result);
			}

			running = null;
			if (queue.isEmpty()) {
				found.clear();
				found.addAll(pending);
				found.sort((a, b) -> Double.compare(a.pos().distSqr(here), b.pos().distSqr(here)));
				searches++;
			}
		}

		if (!queue.isEmpty()) {
			String id = queue.removeFirst();
			runningId = id;
			ResourceKey<net.minecraft.world.level.Level> dimension = client.level.dimension();
			BlockPos from = searchedFrom;
			int chunks = radius.intValue();
			// /locate's search, on the server thread like the command.
			running = CompletableFuture.supplyAsync(() -> locate(server, dimension, id, from, chunks), server).exceptionally(e -> null);
		}
	}

	private static Found locate(IntegratedServer server, ResourceKey<net.minecraft.world.level.Level> dimension, String id, BlockPos from, int chunks) {
		ServerLevel level = server.getLevel(dimension);
		Identifier parsed = Identifier.tryParse(id);
		if (level == null || parsed == null) {
			return null;
		}

		Registry<Structure> registry = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
		Holder<Structure> holder = registry.get(ResourceKey.create(Registries.STRUCTURE, parsed)).orElse(null);
		if (holder == null) {
			return null;
		}

		Pair<BlockPos, Holder<Structure>> pair = level.getChunkSource().getGenerator().findNearestMapStructure(level, HolderSet.direct(holder), from, chunks, false);
		return pair == null ? null : new Found(id, label(id), pair.getFirst());
	}

	private static String direction(BlockPos from, BlockPos to) {
		double angle = Math.toDegrees(Math.atan2(to.getZ() - from.getZ(), to.getX() - from.getX()));
		String[] names = {"E", "SE", "S", "SW", "W", "NW", "N", "NE"};
		return names[(int) Math.floorMod(Math.round(angle / 45), 8)];
	}

	@Override
	public HudAnchor hudAnchor() {
		return HudAnchor.TOP_LEFT;
	}

	@Override
	public void buildHud(List<HudLine> lines) {
		BlockPos here = Minecraft.getInstance().player.blockPosition();
		if (found.isEmpty()) {
			lines.add(HudLine.of("Structures", running != null ? "searching " + label(runningId) + "..." : "none within " + radius.intValue() + " chunks",
					Theme.TEXT_MUTED));
			return;
		}

		for (Found structure : found) {
			double distance = Math.sqrt(structure.pos().distSqr(new BlockPos(here.getX(), structure.pos().getY(), here.getZ())));
			lines.add(HudLine.of(structure.label(), String.format(Locale.ROOT, "%.0f m %s  (%d, %d)", distance, direction(here, structure.pos()),
					structure.pos().getX(), structure.pos().getZ()), Theme.ACCENT_STRONG));
		}
	}

	@Override
	public void renderWorld(RenderingUtilities render) {
		Vec3 camera = render.camera();
		for (Found structure : found) {
			Vec3 base = new Vec3(structure.pos().getX() + 0.5, camera.y - 32, structure.pos().getZ() + 0.5);
			render.line(base, base.add(0, 160, 0), Theme.withAlpha(Theme.ACCENT, 0.6f), 2f, true);
		}
	}

	@Override
	public void renderOverlay(UiContext ui, int screenWidth, int screenHeight, float partialTick) {
		Vec3 camera = Minecraft.getInstance().gameRenderer.getMainCamera().position();
		for (Found structure : found) {
			Vec3 point = new Vec3(structure.pos().getX() + 0.5, camera.y + 8, structure.pos().getZ() + 0.5);
			int[] at = ScreenProjection.toScreen(point, screenWidth, screenHeight);
			if (at != null) {
				double distance = Math.sqrt(point.distanceToSqr(camera.x, point.y, camera.z));
				ui.worldLabel(structure.label() + String.format(Locale.ROOT, "  %.0fm", distance), at[0], at[1], Theme.ACCENT_STRONG);
			}
		}
	}
}
