package io.github.theamethyx.femboyclient.client.modules;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import io.github.theamethyx.femboyclient.client.analysis.GeodeScanner;
import io.github.theamethyx.femboyclient.client.gui.Theme;
import io.github.theamethyx.femboyclient.client.gui.UiContext;
import io.github.theamethyx.femboyclient.client.gui.widget.GeodePanel;
import io.github.theamethyx.femboyclient.client.gui.widget.Widget;
import io.github.theamethyx.femboyclient.client.hud.HudAnchor;
import io.github.theamethyx.femboyclient.client.hud.HudLine;
import io.github.theamethyx.femboyclient.client.module.ClientHooks;
import io.github.theamethyx.femboyclient.client.render.RenderingUtilities;
import io.github.theamethyx.femboyclient.core.module.Module;
import io.github.theamethyx.femboyclient.core.module.ModuleCategory;
import io.github.theamethyx.femboyclient.core.research.GrowthStage;
import io.github.theamethyx.femboyclient.core.research.geode.GeodeObservation;
import io.github.theamethyx.femboyclient.core.setting.BooleanSetting;
import io.github.theamethyx.femboyclient.core.setting.NumberSetting;

/**
 * Finds amethyst geodes in the chunks the client has loaded and reports exact growth-stage counts,
 * completeness and evidence quality, updating automatically as chunks load and buds grow.
 *
 * <p>Observed block states are kept separate from interpretation: the panel shows counts and
 * denominators, and only quotes documented results from controlled tests as reference. Restricted to
 * singleplayer and authorized servers.
 */
public final class GeodeAnalyzerModule extends Module implements ClientHooks.WorldRender, ClientHooks.HudBlock, ClientHooks.CustomControls {
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
			"How many chunks around you are analyzed, as a square. The analyzer can only read chunks your client already has "
					+ "loaded (your render distance, capped by the server's view distance); a larger value never loads more chunks. "
					+ "Chunks in range that are not loaded are reported as unavailable.", 4, 1, 16, 1, "chunks"));
	private final NumberSetting interval = settings().add(new NumberSetting("scan_interval", "Scan interval",
			"How often an already-read chunk is read again to pick up growth. New chunks are read as soon as they load. "
					+ "Unchanged data is never analyzed twice.", 10, 2, 120, 1, "s"));
	private final NumberSetting minimumSample = settings().add(new NumberSetting("minimum_sample", "Minimum observations",
			"Geodes with fewer growth objects than this are rated 'Insufficient' evidence: with so few buds the percentages "
					+ "mostly reflect chance. Twice this number is needed for 'Adequate'.", 20, 1, 200, 1, "growth objects"));
	private final BooleanSetting percentages = settings().add(new BooleanSetting("percentages", "Percentage display",
			"Show each stage's share of all growth objects next to its count. The denominator is always the geode's total "
					+ "number of buds and clusters.", true));
	private final BooleanSetting showIncomplete = settings().add(new BooleanSetting("show_incomplete", "List incomplete geodes",
			"Also list geodes that touch unloaded chunks. They are clearly marked: their counts are a lower bound.", true));
	private final BooleanSetting stageOverlay = settings().add(new BooleanSetting("stage_overlay", "Growth-stage display",
			"Outline every bud and cluster in the world, colored by stage (darkest = small bud, brightest = cluster).", false));
	private final BooleanSetting hudStatus = settings().add(new BooleanSetting("hud_status", "Progress on HUD",
			"Show reading progress in the bottom-right corner while chunks are still being read.", true));
	private final BooleanSetting debug = settings().add(new BooleanSetting("debug_overlay", "Debug information",
			"Draw each geode's bounding box (green complete, amber incomplete) and show cache and timing details in this panel.", false));
	private final NumberSetting budget = settings().add(new NumberSetting("budget_ms", "Time per tick",
			"Performance limit: milliseconds of each 50 ms game tick the analyzer may spend reading chunks. Lower values "
					+ "read more slowly but never cause stutter.", 2, 0.5, 10, 0.5, "ms"));

	private final GeodeScanner scanner = new GeodeScanner();
	private boolean paused;
	private String selectedId;

	public GeodeAnalyzerModule() {
		super("geode_analyzer", "Geode Analyzer",
				"Finds amethyst geodes in loaded chunks and reports exact growth-stage counts, completeness and evidence quality.",
				ModuleCategory.WORLD, false, NO_KEY);
	}

	@Override
	public boolean isRestricted() {
		return true;
	}

	// ----- operation -------------------------------------------------------------------------

	@Override
	protected void onTick() {
		scanner.checkWorld();
		if (!paused) {
			scanner.tick(radius.intValue(), interval.intValue() * 1000L, (long) (budget.get() * 1_000_000));
		}
	}

	@Override
	protected void onDisable() {
		scanner.clear();
		paused = false;
		selectedId = null;
	}

	public boolean paused() {
		return paused;
	}

	public void setPaused(boolean paused) {
		this.paused = paused;
	}

	/** Reads every loaded chunk in range again, keeping the current results until new ones are ready. */
	public void rescanNow() {
		scanner.rescanAll();
	}

	public GeodeScanner scanner() {
		return scanner;
	}

	// ----- results ---------------------------------------------------------------------------

	/** Geodes from the latest analysis, nearest first; incomplete ones only if that setting is on. */
	public List<GeodeObservation> geodes() {
		Vec3 at = playerPosition();
		return scanner.results().stream()
				.filter(geode -> geode.complete() || showIncomplete.get())
				.sorted(Comparator.comparingDouble(geode -> distanceSq(geode, at)))
				.toList();
	}

	/** All geodes of the latest analysis, including incomplete ones (for tests). */
	public List<GeodeObservation> allGeodes() {
		return scanner.results();
	}

	private static Vec3 playerPosition() {
		Minecraft client = Minecraft.getInstance();
		return client.player == null ? Vec3.ZERO : client.player.position();
	}

	private static double distanceSq(GeodeObservation geode, Vec3 at) {
		double dx = geode.centerX() - at.x;
		double dy = geode.centerY() - at.y;
		double dz = geode.centerZ() - at.z;
		return dx * dx + dy * dy + dz * dz;
	}

	/** Distance in blocks and compass direction from the player, e.g. "23 m NE". */
	public static String distanceText(GeodeObservation geode) {
		Vec3 at = playerPosition();
		double dx = geode.centerX() - at.x;
		double dz = geode.centerZ() - at.z;
		String[] points = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};
		int index = Math.floorMod((int) Math.round(Math.toDegrees(Math.atan2(dx, -dz)) / 45), 8);
		return String.format(Locale.ROOT, "%.0f m %s", Math.sqrt(distanceSq(geode, at)), points[index]);
	}

	public GeodeObservation selected() {
		return scanner.results().stream().filter(g -> g.id().equals(selectedId)).findFirst().orElse(null);
	}

	public void select(String id) {
		selectedId = id == null || id.equals(selectedId) ? null : id;
	}

	public int minimumSample() {
		return minimumSample.intValue();
	}

	public boolean showPercentages() {
		return percentages.get();
	}

	public boolean debug() {
		return debug.get();
	}

	public int radiusChunks() {
		return radius.intValue();
	}

	// ----- GUI, HUD and world rendering ------------------------------------------------------

	@Override
	public List<Widget> createControls(UiContext ui, Runnable refresh) {
		return List.of(new GeodePanel(this));
	}

	@Override
	public void renderWorld(RenderingUtilities render) {
		for (GeodeObservation geode : geodes()) {
			if (debug.get()) {
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
		if (!hudStatus.get()) {
			return;
		}

		if (paused) {
			lines.add(HudLine.of("Geode analysis", "paused", Theme.TEXT_SECONDARY));
		} else if (scanner.loadedColumns() > 0 && scanner.readColumns() < scanner.loadedColumns()) {
			lines.add(HudLine.of("Geode analysis", scanner.readColumns() * 100 / scanner.loadedColumns() + "%", Theme.ACCENT_STRONG));
		}
	}
}
