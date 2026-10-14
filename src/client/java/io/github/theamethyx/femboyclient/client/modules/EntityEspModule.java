package io.github.theamethyx.femboyclient.client.modules;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import io.github.theamethyx.femboyclient.client.gui.UiContext;
import io.github.theamethyx.femboyclient.client.module.ClientHooks;
import io.github.theamethyx.femboyclient.client.render.EspRenderer;
import io.github.theamethyx.femboyclient.client.render.RenderingUtilities;
import io.github.theamethyx.femboyclient.core.module.Module;
import io.github.theamethyx.femboyclient.core.module.ModuleCategory;
import io.github.theamethyx.femboyclient.core.setting.BooleanSetting;
import io.github.theamethyx.femboyclient.core.setting.ColorSetting;
import io.github.theamethyx.femboyclient.core.setting.EnumSetting;
import io.github.theamethyx.femboyclient.core.setting.IdSetSetting;
import io.github.theamethyx.femboyclient.core.setting.NumberSetting;

/**
 * Highlights entities of the chosen types. Types are selected by registry id and grouped into players,
 * hostile, passive, villagers and other; tamed animals can be handled separately as pets.
 *
 * <p>Restricted to singleplayer and authorized servers: on any other server the module pauses.
 * It only uses entities the client already knows about and skips entities that are invisible to you.
 */
public final class EntityEspModule extends Module implements ClientHooks.WorldRender, ClientHooks.ScreenOverlay {
	public static final String PLAYERS = "Players";
	public static final String HOSTILE = "Hostile mobs";
	public static final String PASSIVE = "Passive mobs";
	public static final String VILLAGERS = "Villagers";
	public static final String OTHER = "Other";

	public enum PetMode implements EnumSetting.Labeled {
		LIKE_TYPE("Follow the type selection"), ALWAYS("Always show pets"), NEVER("Never show pets");

		private final String label;

		PetMode(String label) {
			this.label = label;
		}

		@Override
		public String label() {
			return label;
		}
	}

	private final IdSetSetting types = settings().add(new IdSetSetting("types", "Entity types",
			"Which entity types to highlight. Click a group to select or clear all of it.", defaultTypes()));
	private final EnumSetting<PetMode> pets = settings().add(new EnumSetting<>("pets", "Pets",
			"How tamed animals (wolves, cats, horses, parrots...) are treated, regardless of their type.", PetMode.LIKE_TYPE));
	private final BooleanSetting outline = settings().add(new BooleanSetting("outline", "Outline", "Draw the edges of each entity's box.", true));
	private final BooleanSetting fill = settings().add(new BooleanSetting("fill", "Fill", "Fill each box with a faint tint.", false));
	private final BooleanSetting throughWalls = settings().add(new BooleanSetting("through_walls", "Show through walls",
			"Draw highlights even when blocks are in the way.", true));
	private final BooleanSetting names = settings().add(new BooleanSetting("names", "Names", "Show the entity's name above it.", true));
	private final BooleanSetting distance = settings().add(new BooleanSetting("distance", "Distance", "Show the distance in blocks next to the name.", true));
	private final BooleanSetting showInvisible = settings().add(new BooleanSetting("show_invisible", "Include invisible entities",
			"Also highlight entities that are invisible to you (for example under an invisibility effect).", false));
	private final ColorSetting playerColor = settings().add(new ColorSetting("color_players", "Player color", "Highlight color for players.", 0x9D6EFF));
	private final ColorSetting hostileColor = settings().add(new ColorSetting("color_hostile", "Hostile color", "Highlight color for hostile mobs.", 0xF27272));
	private final ColorSetting passiveColor = settings().add(new ColorSetting("color_passive", "Passive color", "Highlight color for passive mobs.", 0x5BD68A));
	private final ColorSetting villagerColor = settings().add(new ColorSetting("color_villagers", "Villager color", "Highlight color for villagers and traders.", 0xF2C14E));
	private final ColorSetting petColor = settings().add(new ColorSetting("color_pets", "Pet color", "Highlight color for tamed animals.", 0xE070C8));
	private final ColorSetting otherColor = settings().add(new ColorSetting("color_other", "Other color", "Highlight color for everything else.", 0x6EA8FF));
	private final NumberSetting maxDistance = settings().add(new NumberSetting("max_distance", "Maximum distance",
			"Entities farther away than this are not highlighted.", 64, 8, 256, 8, "blocks"));
	private final NumberSetting maxEntities = settings().add(new NumberSetting("max_entities", "Maximum highlights",
			"Performance limit: only the nearest entities up to this number are drawn.", 64, 8, 512, 8, ""));
	private final NumberSetting maxLabels = settings().add(new NumberSetting("max_labels", "Maximum labels",
			"Performance and clutter limit: labels are drawn for the nearest entities only.", 16, 0, 64, 1, ""));

	private Set<EntityType<?>> selected = Set.of();
	private List<Entity> tracked = List.of();
	private String choicesLanguage;
	private List<IdSetSetting.Choice> choices = List.of();

	public EntityEspModule() {
		super("entity_esp", "Entity ESP", "Highlights the entity types you choose, with names, distances and colors per group.",
				ModuleCategory.VISUALS, false, NO_KEY);
		types.choices(this::choices);
		types.onChange(ids -> selected = resolve(ids));
		selected = resolve(types.get());
		distance.visibleWhen(names::get);
	}

	@Override
	public boolean isRestricted() {
		return true;
	}

	// ----- type registry ---------------------------------------------------------------------

	/** Which group an entity type belongs to, by its registry category. */
	public static String groupOf(EntityType<?> type) {
		if (type == EntityType.PLAYER) return PLAYERS;
		if (type == EntityType.VILLAGER || type == EntityType.WANDERING_TRADER) return VILLAGERS;
		MobCategory category = type.getCategory();
		if (category == MobCategory.MONSTER) return HOSTILE;
		if (category == MobCategory.MISC) return OTHER;
		return PASSIVE;
	}

	private static Set<String> defaultTypes() {
		Set<String> ids = new HashSet<>();
		for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
			String group = groupOf(type);
			if (group.equals(PLAYERS) || group.equals(HOSTILE)) {
				ids.add(BuiltInRegistries.ENTITY_TYPE.getKey(type).toString());
			}
		}

		return ids;
	}

	private static Set<EntityType<?>> resolve(Set<String> ids) {
		Set<EntityType<?>> resolved = new HashSet<>();
		for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
			if (ids.contains(BuiltInRegistries.ENTITY_TYPE.getKey(type).toString())) {
				resolved.add(type);
			}
		}

		return resolved;
	}

	/** All registered entity types with translated names; rebuilt when the language changes. */
	private List<IdSetSetting.Choice> choices() {
		String language = Minecraft.getInstance().options.languageCode;
		if (!language.equals(choicesLanguage)) {
			List<IdSetSetting.Choice> all = new ArrayList<>();
			for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
				all.add(new IdSetSetting.Choice(BuiltInRegistries.ENTITY_TYPE.getKey(type).toString(), type.getDescription().getString(), groupOf(type)));
			}

			List<String> order = List.of(PLAYERS, HOSTILE, PASSIVE, VILLAGERS, OTHER);
			all.sort(Comparator.<IdSetSetting.Choice>comparingInt(c -> order.indexOf(c.group())).thenComparing(c -> c.label().toLowerCase(Locale.ROOT)));
			choices = List.copyOf(all);
			choicesLanguage = language;
		}

		return choices;
	}

	// ----- selection -------------------------------------------------------------------------

	private static boolean isPet(Entity entity) {
		return entity instanceof OwnableEntity ownable && ownable.getOwnerReference() != null;
	}

	private boolean wanted(Entity entity) {
		if (isPet(entity)) {
			return switch (pets.get()) {
				case ALWAYS -> true;
				case NEVER -> false;
				case LIKE_TYPE -> selected.contains(entity.getType());
			};
		}

		return selected.contains(entity.getType());
	}

	private int colorOf(Entity entity) {
		if (isPet(entity) && pets.get() != PetMode.LIKE_TYPE) {
			return petColor.get();
		}

		return switch (groupOf(entity.getType())) {
			case PLAYERS -> playerColor.get();
			case HOSTILE -> hostileColor.get();
			case VILLAGERS -> villagerColor.get();
			case OTHER -> otherColor.get();
			default -> isPet(entity) ? petColor.get() : passiveColor.get();
		};
	}

	/** Chooses the entities to highlight once per tick; rendering only interpolates their positions. */
	@Override
	protected void onTick() {
		Minecraft client = Minecraft.getInstance();
		ClientLevel level = client.level;
		Entity self = client.getCameraEntity();
		if (level == null || client.player == null || selected.isEmpty() && pets.get() != PetMode.ALWAYS) {
			tracked = List.of();
			return;
		}

		Vec3 eye = client.player.position();
		double max = maxDistance.get();
		List<Entity> found = new ArrayList<>();
		for (Entity entity : level.entitiesForRendering()) {
			if (entity == client.player || entity == self || !entity.isAlive() || entity.isSpectator()) {
				continue;
			}

			if (!showInvisible.get() && entity.isInvisibleTo(client.player)) {
				continue;
			}

			if (entity.position().distanceToSqr(eye) <= max * max && wanted(entity)) {
				found.add(entity);
			}
		}

		found.sort(Comparator.comparingDouble(e -> e.position().distanceToSqr(eye)));
		tracked = found.size() > maxEntities.intValue() ? List.copyOf(found.subList(0, maxEntities.intValue())) : List.copyOf(found);
	}

	@Override
	protected void onDisable() {
		tracked = List.of();
	}

	/** Entities highlighted after the last tick, nearest first; read by automated tests. */
	public List<Entity> tracked() {
		return tracked;
	}

	private List<EspRenderer.Target> targets(Vec3 camera, float partialTick, boolean withLabels) {
		List<EspRenderer.Target> targets = new ArrayList<>(tracked.size());
		for (Entity entity : tracked) {
			if (entity.isRemoved()) {
				continue;
			}

			Vec3 interpolated = entity.getPosition(partialTick);
			AABB box = entity.getBoundingBox().move(interpolated.subtract(entity.position())).inflate(0.05);
			String label = null;
			if (withLabels && names.get()) {
				label = entity.getDisplayName().getString();
				if (distance.get()) {
					label += String.format(Locale.ROOT, "  %.0fm", interpolated.distanceTo(camera));
				}
			}

			targets.add(new EspRenderer.Target(box, colorOf(entity), label));
		}

		return targets;
	}

	@Override
	public void renderWorld(RenderingUtilities render) {
		EspRenderer.drawBoxes(render, targets(render.camera(), render.partialTick(), false),
				new EspRenderer.Style(outline.get(), fill.get(), throughWalls.get(), 1.5f));
	}

	@Override
	public void renderOverlay(UiContext ui, int screenWidth, int screenHeight, float partialTick) {
		if (!names.get() || maxLabels.intValue() == 0) {
			return;
		}

		Vec3 camera = Minecraft.getInstance().gameRenderer.getMainCamera().position();
		EspRenderer.drawLabels(ui, targets(camera, partialTick, true), screenWidth, screenHeight, maxLabels.intValue());
	}
}
