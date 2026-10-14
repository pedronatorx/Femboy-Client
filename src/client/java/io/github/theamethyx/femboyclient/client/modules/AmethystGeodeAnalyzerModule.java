package io.github.theamethyx.femboyclient.client.modules;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.AABB;

import io.github.theamethyx.femboyclient.client.FemboyClient;
import io.github.theamethyx.femboyclient.client.analysis.WorldScanner;
import io.github.theamethyx.femboyclient.client.fixture.FixtureRegistry;
import io.github.theamethyx.femboyclient.client.gui.Theme;
import io.github.theamethyx.femboyclient.client.hud.HudAnchor;
import io.github.theamethyx.femboyclient.client.hud.HudLine;
import io.github.theamethyx.femboyclient.client.module.ClientHooks;
import io.github.theamethyx.femboyclient.client.render.RenderingUtilities;
import io.github.theamethyx.femboyclient.client.world.WorldIdentity;
import io.github.theamethyx.femboyclient.core.module.Module;
import io.github.theamethyx.femboyclient.core.module.ModuleCategory;
import io.github.theamethyx.femboyclient.core.research.GrowthStage;
import io.github.theamethyx.femboyclient.core.research.geode.GeodeObservation;
import io.github.theamethyx.femboyclient.core.setting.BooleanSetting;
import io.github.theamethyx.femboyclient.core.setting.EnumSetting;
import io.github.theamethyx.femboyclient.core.setting.NumberSetting;

/**
 * Finds amethyst geodes in the chunks the client has loaded and reports their growth-stage
 * distribution, completeness and data quality. A research tool for controlled test worlds:
 * restricted to singleplayer and authorized test servers.
 */
public final class AmethystGeodeAnalyzerModule extends Module implements ClientHooks.WorldRender, ClientHooks.HudBlock {
	public enum FixtureFilter implements EnumSetting.Labeled {
		ALL("All geodes"), FIXTURES_ONLY("Registered fixtures only"), NATURAL_ONLY("Unregistered geodes only");

		private final String label;

		FixtureFilter(String label) {
			this.label = label;
		}

		@Override
		public String label() {
			return label;
		}
	}

	/**
	 * Ordinal single-hue ramp for the four stages (more growth = brighter). Validated with the dataviz
	 * palette checker against the panel surface #1A1A20: monotone lightness, visible steps, one hue.
	 */
	public static int stageColor(GrowthStage stage) {
		return switch (stage) {
			case SMALL_BUD -> 0xFF6A55B0;
			case MEDIUM_BUD -> 0xFF8E6FE3;
			case LARGE_BUD -> 0xFFB49AF7;
			case CLUSTER -> 0xFFE0D4FF;
		};
	}

	private final NumberSetting radius = settings().add(new NumberSetting("radius", "Analysis radius",
			"How many chunks around you the analyzer reads, as a square. This is the analyzer's own limit: it can only read chunks "
					+ "your client already has loaded (your render distance, capped by the server's view distance), and a larger value "
					+ "never loads more chunks or makes the server simulate anything. Chunks it cannot read are reported as unavailable.",
			4, 1, 16, 1, "chunks"));
	private final NumberSetting minimumSample = settings().add(new NumberSetting("minimum_sample", "Minimum sample size",
			"Geodes with fewer growth objects than this are rated 'Insufficient' evidence: with so few buds the percentages "
					+ "mostly reflect chance. Twice this number is needed for an 'Adequate' rating.", 20, 1, 200, 1, "growth objects"));
	private final BooleanSetting requireComplete = settings().add(new BooleanSetting("require_complete", "Require complete observations",
			"Only complete geodes (the whole geode and a 2-block margin were readable) count towards totals. Incomplete ones are "
					+ "still listed, clearly marked.", true));
	private final BooleanSetting stageOverlay = settings().add(new BooleanSetting("stage_overlay", "Growth-stage display",
			"Outline every bud and cluster in the world, colored by stage (darkest = small bud, brightest = cluster).", true));
	private final BooleanSetting percentages = settings().add(new BooleanSetting("percentages", "Show percentages",
			"Show each stage's share of all growth objects next to its count.", true));
	private final BooleanSetting debugOverlay = settings().add(new BooleanSetting("debug_overlay", "Debug overlays",
			"Draw each detected geode's bounding box: green when complete, amber when incomplete.", true));
	private final EnumSetting<FixtureFilter> fixtureFilter = settings().add(new EnumSetting<>("fixture_filter", "Test-fixture filter",
			"Which geodes to list: all, only those inside regions registered by Femboy Client's test scenarios, or only unregistered ones.",
			FixtureFilter.ALL));
	private final NumberSetting budget = settings().add(new NumberSetting("budget_ms", "Scan time per tick",
			"Performance limit: milliseconds of each 50 ms game tick the scanner may use. Lower values scan more slowly but never "
					+ "cause stutter.", 2, 0.5, 10, 0.5, "ms"));
	private final NumberSetting autoRescan = settings().add(new NumberSetting("auto_rescan", "Automatic rescan",
			"Rescan automatically this many seconds after the previous scan finished. 0 means only when you press Scan.", 0, 0, 300, 5, "s"));

	private final WorldScanner scanner = new WorldScanner();
	private List<GeodeObservation> results = List.of();
	private String resultsWorld;
	private String resultsDimension;
	private String selectedId;

	public AmethystGeodeAnalyzerModule() {
		super("geode_analyzer", "Amethyst Geode Analyzer",
				"Finds amethyst geodes in loaded chunks and reports exact growth-stage counts, completeness and evidence quality.",
				ModuleCategory.WORLD_ANALYSIS, false, NO_KEY);
	}

	@Override
	public boolean isRestricted() {
		return true;
	}

	// ----- scanning --------------------------------------------------------------------------

	public void startScan() {
		resultsWorld = WorldIdentity.current();
		resultsDimension = WorldIdentity.dimension();
		scanner.start(radius.intValue(), this::onScanDone);
	}

	public void cancelScan() {
		scanner.cancel();
	}

	private void onScanDone(List<GeodeObservation> geodes) {
		FixtureRegistry registry = FemboyClient.get().fixtures();
		String world = WorldIdentity.current();
		List<GeodeObservation> linked = new ArrayList<>();
		for (GeodeObservation geode : geodes) {
			FixtureRegistry.RegionFixture region = world == null ? null
					: registry.regionAt(world, geode.dimension(), geode.centerX(), geode.centerY(), geode.centerZ());
			linked.add(region == null ? geode : geode.withScenario(region.runId()));
		}

		results = List.copyOf(linked);
		if (selectedId == null || results.stream().noneMatch(g -> g.id().equals(selectedId))) {
			selectedId = visibleResults().isEmpty() ? null : visibleResults().getFirst().id();
		}
	}

	@Override
	protected void onTick() {
		String world = WorldIdentity.current();
		boolean otherDimension = resultsDimension != null && !resultsDimension.equals(WorldIdentity.dimension());
		if (resultsWorld != null && (!resultsWorld.equals(world) || otherDimension)) {
			// Results belong to another world or dimension: never draw or list them here.
			scanner.clear();
			results = List.of();
			selectedId = null;
			resultsWorld = null;
			resultsDimension = null;
		}

		scanner.tick((long) (budget.get() * 1_000_000));
		int every = autoRescan.intValue();
		if (every > 0 && !scanner.busy() && (scanner.finishedAt() == 0 || System.currentTimeMillis() - scanner.finishedAt() > every * 1000L)) {
			startScan();
		}
	}

	@Override
	protected void onDisable() {
		scanner.cancel();
	}

	// ----- results ---------------------------------------------------------------------------

	public WorldScanner scanner() {
		return scanner;
	}

	/** All geodes from the last scan, linked to fixture runs where applicable. */
	public List<GeodeObservation> results() {
		return results;
	}

	/** Results after the fixture filter. Incomplete geodes are included and marked. */
	public List<GeodeObservation> visibleResults() {
		return results.stream().filter(geode -> switch (fixtureFilter.get()) {
			case ALL -> true;
			case FIXTURES_ONLY -> geode.scenarioId() != null;
			case NATURAL_ONLY -> geode.scenarioId() == null;
		}).toList();
	}

	public GeodeObservation selected() {
		return results.stream().filter(g -> g.id().equals(selectedId)).findFirst().orElse(null);
	}

	public void select(String id) {
		selectedId = id;
	}

	public int minimumSample() {
		return minimumSample.intValue();
	}

	public boolean requireComplete() {
		return requireComplete.get();
	}

	public boolean showPercentages() {
		return percentages.get();
	}

	public int radiusChunks() {
		return radius.intValue();
	}

	/** Chunks the client can actually provide, for explaining the radius setting. */
	public int clientChunkRadius() {
		return Minecraft.getInstance().options.getEffectiveRenderDistance();
	}

	// ----- rendering -------------------------------------------------------------------------

	@Override
	public void renderWorld(RenderingUtilities render) {
		if (!debugOverlay.get() && !stageOverlay.get()) {
			return;
		}

		for (GeodeObservation geode : visibleResults()) {
			if (debugOverlay.get()) {
				AABB box = new AABB(geode.minX(), geode.minY(), geode.minZ(), geode.maxX() + 1, geode.maxY() + 1, geode.maxZ() + 1);
				boolean selected = geode.id().equals(selectedId);
				int color = geode.complete() ? Theme.SUCCESS : Theme.WARNING;
				render.boxOutline(box, Theme.withAlpha(color, selected ? 1f : 0.6f), selected ? 2f : 1f, true);
			}

			if (stageOverlay.get()) {
				for (GeodeObservation.PositionedStage growth : geode.growth()) {
					AABB box = new AABB(growth.x() + 0.2, growth.y() + 0.2, growth.z() + 0.2, growth.x() + 0.8, growth.y() + 0.8, growth.z() + 0.8);
					render.boxOutline(box, stageColor(growth.stage()), 1f, false);
				}
			}
		}
	}

	@Override
	public HudAnchor hudAnchor() {
		return HudAnchor.BOTTOM_RIGHT;
	}

	@Override
	public void buildHud(List<HudLine> lines) {
		if (scanner.busy()) {
			lines.add(HudLine.of("Geode scan", String.format(Locale.ROOT, "%.0f%%", scanner.progress() * 100), Theme.ACCENT_STRONG));
		}
	}
}
