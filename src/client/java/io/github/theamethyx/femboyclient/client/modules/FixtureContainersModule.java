package io.github.theamethyx.femboyclient.client.modules;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import io.github.theamethyx.femboyclient.client.FemboyClient;
import io.github.theamethyx.femboyclient.client.fixture.FixtureRegistry;
import io.github.theamethyx.femboyclient.client.gui.Theme;
import io.github.theamethyx.femboyclient.client.gui.UiContext;
import io.github.theamethyx.femboyclient.client.module.ClientHooks;
import io.github.theamethyx.femboyclient.client.render.RenderingUtilities;
import io.github.theamethyx.femboyclient.client.render.ScreenProjection;
import io.github.theamethyx.femboyclient.client.world.WorldIdentity;
import io.github.theamethyx.femboyclient.core.module.Module;
import io.github.theamethyx.femboyclient.core.module.ModuleCategory;
import io.github.theamethyx.femboyclient.core.setting.BooleanSetting;
import io.github.theamethyx.femboyclient.core.setting.NumberSetting;

/**
 * Marks containers registered as test fixtures and shows whether each one is still present.
 * Unregistered containers are never marked.
 */
public final class FixtureContainersModule extends Module implements ClientHooks.WorldRender, ClientHooks.ScreenOverlay {
	public enum Status { PRESENT, MISSING, NOT_LOADED }

	public record Observed(FixtureRegistry.ContainerFixture fixture, Status status) {
	}

	private final BooleanSetting throughWalls = settings().add(new BooleanSetting("through_walls", "Show through walls",
			"Draw the markers even when blocks are in the way.", true));
	private final BooleanSetting labels = settings().add(new BooleanSetting("labels", "Show labels",
			"Show each fixture's name and whether the container is still there.", true));
	private final NumberSetting maxDistance = settings().add(new NumberSetting("max_distance", "Maximum distance",
			"Ignore fixtures farther away than this.", 64, 8, 256, 8, "blocks"));

	private volatile List<Observed> lastObserved = List.of();

	public FixtureContainersModule() {
		super("fixture_containers", "Fixture Containers",
				"Marks containers created by Femboy Client's test scenarios: green if present, red if missing. Other containers are never shown.",
				ModuleCategory.VISUAL, false, NO_KEY);
	}

	@Override
	public boolean isRestricted() {
		return true;
	}

	/** Fixtures and their status from the last rendered frame; read by automated tests. */
	public List<Observed> lastObserved() {
		return lastObserved;
	}

	private List<Observed> observe(Vec3 from) {
		ClientLevel level = Minecraft.getInstance().level;
		String world = WorldIdentity.current();
		if (level == null || world == null) {
			return List.of();
		}

		List<Observed> result = new ArrayList<>();
		for (FixtureRegistry.ContainerFixture fixture : FemboyClient.get().fixtures().containers(world, WorldIdentity.dimension())) {
			BlockPos pos = new BlockPos(fixture.x(), fixture.y(), fixture.z());
			if (Vec3.atCenterOf(pos).distanceTo(from) > maxDistance.get()) {
				continue;
			}

			Status status;
			if (!level.isLoaded(pos)) {
				// Unavailable data is reported as such, never as "missing".
				status = Status.NOT_LOADED;
			} else {
				status = level.getBlockEntity(pos) instanceof Container ? Status.PRESENT : Status.MISSING;
			}

			result.add(new Observed(fixture, status));
		}

		return result;
	}

	@Override
	public void renderWorld(RenderingUtilities render) {
		List<Observed> observed = observe(render.camera());
		lastObserved = List.copyOf(observed);
		for (Observed entry : observed) {
			if (entry.status() == Status.NOT_LOADED) {
				continue;
			}

			FixtureRegistry.ContainerFixture f = entry.fixture();
			AABB box = new AABB(f.x(), f.y(), f.z(), f.x() + 1, f.y() + 1, f.z() + 1).inflate(0.02);
			int color = entry.status() == Status.PRESENT ? Theme.SUCCESS : Theme.ERROR;
			render.filledBox(box, Theme.withAlpha(color, 0.15f), throughWalls.get());
			render.boxOutline(box, color, 1.5f, throughWalls.get());
		}
	}

	@Override
	public void renderOverlay(UiContext ui, int screenWidth, int screenHeight, float partialTick) {
		if (!labels.get()) {
			return;
		}

		for (Observed entry : lastObserved) {
			FixtureRegistry.ContainerFixture f = entry.fixture();
			int[] at = ScreenProjection.toScreen(new Vec3(f.x() + 0.5, f.y() + 1.4, f.z() + 0.5), screenWidth, screenHeight);
			if (at == null) {
				continue;
			}

			String state = switch (entry.status()) {
				case PRESENT -> "present";
				case MISSING -> "missing";
				case NOT_LOADED -> "not loaded";
			};
			int color = switch (entry.status()) {
				case PRESENT -> Theme.SUCCESS;
				case MISSING -> Theme.ERROR;
				case NOT_LOADED -> Theme.TEXT_MUTED;
			};
			String text = f.label() + "  " + state;
			ui.worldLabel(text, at[0], at[1], color);
		}
	}
}
