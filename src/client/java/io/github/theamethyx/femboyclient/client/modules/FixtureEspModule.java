package io.github.theamethyx.femboyclient.client.modules;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import io.github.theamethyx.femboyclient.client.FemboyClient;
import io.github.theamethyx.femboyclient.client.fixture.FixtureRegistry;
import io.github.theamethyx.femboyclient.client.gui.Theme;
import io.github.theamethyx.femboyclient.client.gui.UiContext;
import io.github.theamethyx.femboyclient.client.module.ClientHooks;
import io.github.theamethyx.femboyclient.client.render.OverlayColor;
import io.github.theamethyx.femboyclient.client.render.RenderingUtilities;
import io.github.theamethyx.femboyclient.client.render.ScreenProjection;
import io.github.theamethyx.femboyclient.client.scenario.TestScenarioManager;
import io.github.theamethyx.femboyclient.client.world.WorldIdentity;
import io.github.theamethyx.femboyclient.core.module.Module;
import io.github.theamethyx.femboyclient.core.module.ModuleCategory;
import io.github.theamethyx.femboyclient.core.setting.BooleanSetting;
import io.github.theamethyx.femboyclient.core.setting.EnumSetting;
import io.github.theamethyx.femboyclient.core.setting.NumberSetting;

/**
 * Outlines test entities that Femboy Client's scenarios registered as fixtures. Entities that are not in the
 * fixture list are never highlighted, so this cannot reveal other players or mobs.
 */
public final class FixtureEspModule extends Module implements ClientHooks.WorldRender, ClientHooks.ScreenOverlay {
	private final EnumSetting<OverlayColor> color = settings().add(new EnumSetting<>("color", "Color", "Outline color.", OverlayColor.PURPLE));
	private final BooleanSetting throughWalls = settings().add(new BooleanSetting("through_walls", "Show through walls",
			"Draw outlines even when blocks are in the way.", true));
	private final BooleanSetting labels = settings().add(new BooleanSetting("labels", "Show labels",
			"Show the fixture's registered name above it.", true));
	private final NumberSetting maxDistance = settings().add(new NumberSetting("max_distance", "Maximum distance",
			"Ignore fixtures farther away than this.", 64, 8, 256, 8, "blocks"));

	/** UUIDs outlined in the last rendered frame; read by automated tests. */
	private volatile List<UUID> lastHighlighted = List.of();

	public FixtureEspModule() {
		super("fixture_esp", "Fixture ESP", "Outlines test entities created by Femboy Client's test scenarios. Other entities are never shown.",
				ModuleCategory.VISUAL, false, NO_KEY);
	}

	@Override
	public boolean isRestricted() {
		return true;
	}

	public List<UUID> lastHighlighted() {
		return lastHighlighted;
	}

	private List<Entity> fixturesInRange(Vec3 from) {
		Minecraft client = Minecraft.getInstance();
		ClientLevel level = client.level;
		String world = WorldIdentity.current();
		if (level == null || world == null) {
			return List.of();
		}

		Set<UUID> registered = TestScenarioManager.fixtureUuids(FemboyClient.get().fixtures(), world);
		List<Entity> result = new ArrayList<>();
		if (registered.isEmpty()) {
			return result;
		}

		for (Entity entity : level.entitiesForRendering()) {
			if (registered.contains(entity.getUUID()) && entity.position().distanceTo(from) <= maxDistance.get()) {
				result.add(entity);
			}
		}

		return result;
	}

	@Override
	public void renderWorld(RenderingUtilities render) {
		List<UUID> highlighted = new ArrayList<>();
		for (Entity entity : fixturesInRange(render.camera())) {
			Vec3 interpolated = entity.getPosition(render.partialTick());
			AABB box = entity.getBoundingBox().move(interpolated.subtract(entity.position())).inflate(0.05);
			render.filledBox(box, color.get().withAlpha(0.12f), throughWalls.get());
			render.boxOutline(box, color.get().argb, 1.5f, throughWalls.get());
			highlighted.add(entity.getUUID());
		}

		lastHighlighted = List.copyOf(highlighted);
	}

	@Override
	public void renderOverlay(UiContext ui, int screenWidth, int screenHeight, float partialTick) {
		if (!labels.get()) {
			return;
		}

		Vec3 camera = Minecraft.getInstance().gameRenderer.getMainCamera().position();
		FixtureRegistry registry = FemboyClient.get().fixtures();
		String world = WorldIdentity.current();
		for (Entity entity : fixturesInRange(camera)) {
			FixtureRegistry.EntityFixture fixture = registry.entity(world, entity.getUUID());
			Vec3 top = entity.getPosition(partialTick).add(0, entity.getBbHeight() + 0.6, 0);
			int[] at = ScreenProjection.toScreen(top, screenWidth, screenHeight);
			if (at == null || fixture == null) {
				continue;
			}

			String text = fixture.label() + String.format(Locale.ROOT, "  %.0fm", entity.position().distanceTo(camera));
			ui.worldLabel(text, at[0], at[1], color.get().argb);
		}
	}
}
