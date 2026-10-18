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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import io.github.theamethyx.femboyclient.client.Performance;
import io.github.theamethyx.femboyclient.client.gui.UiContext;
import io.github.theamethyx.femboyclient.client.module.ClientHooks;
import io.github.theamethyx.femboyclient.client.render.EspRenderer;
import io.github.theamethyx.femboyclient.client.render.FrameProjection;
import io.github.theamethyx.femboyclient.client.render.RenderingUtilities;
import io.github.theamethyx.femboyclient.client.render.TargetLines;
import io.github.theamethyx.femboyclient.client.world.EntityIndex;
import io.github.theamethyx.femboyclient.core.module.Module;
import io.github.theamethyx.femboyclient.core.module.ModuleCategory;
import io.github.theamethyx.femboyclient.core.setting.BooleanSetting;
import io.github.theamethyx.femboyclient.core.setting.ColorSetting;
import io.github.theamethyx.femboyclient.core.setting.EnumSetting;
import io.github.theamethyx.femboyclient.core.setting.IdSetSetting;
import io.github.theamethyx.femboyclient.core.setting.NumberSetting;

/**
 * Highlights entities of the chosen types, with labels and target lines from the crosshair. Types are selected by
 * registry id and grouped into players, hostile, passive, villagers, items, projectiles and other; tamed animals can be
 * handled separately as pets. Outlines, health and lines can be switched per group.
 *
 * <p>It only uses entities the client already knows about (the shared {@link EntityIndex}) and skips entities that are
 * invisible to you unless "Include invisible" is on. The tracked list is rebuilt at the update interval; boxes and lines
 * follow the entities every frame.
 */
public final class EntityEspModule extends Module implements ClientHooks.WorldRender, ClientHooks.ScreenOverlay {
	public static final String PLAYERS = "Players";
	public static final String HOSTILE = "Hostile mobs";
	public static final String PASSIVE = "Passive mobs";
	public static final String VILLAGERS = "Villagers";
	public static final String ITEMS = "Items";
	public static final String PROJECTILES = "Projectiles";
	public static final String OTHER = "Other";
	private static final List<String> GROUP_ORDER = List.of(PLAYERS, HOSTILE, PASSIVE, VILLAGERS, ITEMS, PROJECTILES, OTHER);

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

	{ settings().section("Targets"); }
	private final IdSetSetting types = settings().add(new IdSetSetting("types", "Entity types",
			"Which entity types to highlight. Click a group to select or clear all of it.", defaultTypes()));
	private final EnumSetting<PetMode> pets = settings().add(new EnumSetting<>("pets", "Pets",
			"How tamed animals (wolves, cats, horses, parrots...) are treated, regardless of their type.", PetMode.LIKE_TYPE));
	private final BooleanSetting showInvisible = settings().add(new BooleanSetting("show_invisible", "Include invisible entities",
			"Also highlight entities that are invisible to you (for example under an invisibility effect).", false));
	private final NumberSetting maxDistance = settings().add(new NumberSetting("max_distance", "Maximum distance",
			"Entities farther away than this are not highlighted.", 64, 8, 256, 8, "blocks"));

	{ settings().section("Highlight"); }
	private final BooleanSetting outline = settings().add(new BooleanSetting("outline", "Outline", "Draw the edges of each entity's box.", true));
	private final NumberSetting outlineWidth = settings().add(new NumberSetting("outline_width", "Outline thickness",
			"Line thickness, as a multiple of the normal line width.", 1.5, 0.5, 6, 0.5, "x"));
	private final NumberSetting outlineOpacity = settings().add(new NumberSetting("outline_opacity", "Outline opacity",
			"How solid the outline is.", 1, 0.1, 1, 0.05, ""));
	private final BooleanSetting outlinePlayers = settings().add(new BooleanSetting("outline_players", "Outline players", "Draw outlines around players.", true)).advanced();
	private final BooleanSetting outlineHostile = settings().add(new BooleanSetting("outline_hostile", "Outline hostile mobs", "Draw outlines around hostile mobs.", true)).advanced();
	private final BooleanSetting outlinePassive = settings().add(new BooleanSetting("outline_passive", "Outline passive mobs and pets",
			"Draw outlines around passive mobs and tamed animals.", true)).advanced();
	private final BooleanSetting outlineVillagers = settings().add(new BooleanSetting("outline_villagers", "Outline villagers", "Draw outlines around villagers and traders.", true)).advanced();
	private final BooleanSetting outlineItems = settings().add(new BooleanSetting("outline_items", "Outline items", "Draw outlines around dropped items.", true)).advanced();
	private final BooleanSetting outlineProjectiles = settings().add(new BooleanSetting("outline_projectiles", "Outline projectiles",
			"Draw outlines around arrows, tridents, pearls and other projectiles.", true)).advanced();
	private final BooleanSetting outlineOther = settings().add(new BooleanSetting("outline_other", "Outline other entities", "Draw outlines around everything else.", true)).advanced();
	private final BooleanSetting fill = settings().add(new BooleanSetting("fill", "Fill", "Fill each box with a faint tint.", false));
	private final NumberSetting fillOpacity = settings().add(new NumberSetting("fill_opacity", "Fill opacity",
			"How strong the fill tint is.", 0.15, 0.05, 0.6, 0.05, ""));
	private final BooleanSetting throughWalls = settings().add(new BooleanSetting("through_walls", "Show through walls",
			"Draw boxes even when blocks are in the way.", true));

	{ settings().section("Colors"); }
	private final ColorSetting playerColor = settings().add(new ColorSetting("color_players", "Player color", "Highlight color for players.", 0x9D6EFF));
	private final ColorSetting hostileColor = settings().add(new ColorSetting("color_hostile", "Hostile color", "Highlight color for hostile mobs.", 0xF27272));
	private final ColorSetting passiveColor = settings().add(new ColorSetting("color_passive", "Passive color", "Highlight color for passive mobs.", 0x5BD68A));
	private final ColorSetting villagerColor = settings().add(new ColorSetting("color_villagers", "Villager color", "Highlight color for villagers and traders.", 0xF2C14E));
	private final ColorSetting petColor = settings().add(new ColorSetting("color_pets", "Pet color", "Highlight color for tamed animals.", 0xE070C8));
	private final ColorSetting itemColor = settings().add(new ColorSetting("color_items", "Item color", "Highlight color for dropped items.", 0xF2D16B));
	private final ColorSetting projectileColor = settings().add(new ColorSetting("color_projectiles", "Projectile color",
			"Highlight color for projectiles.", 0x4CC9C0));
	private final ColorSetting otherColor = settings().add(new ColorSetting("color_other", "Other color", "Highlight color for everything else.", 0x6EA8FF));
	private final IdSetSetting overrideTypes = settings().add(new IdSetSetting("override_types", "Color override: entity types",
			"Entity types drawn in the override color instead of their group color, e.g. to make one mob stand out.", Set.of())).advanced();
	private final ColorSetting overrideColor = settings().add(new ColorSetting("override_color", "Override color",
			"Color for the entity types chosen above.", 0xF29E4C)).advanced();

	{ settings().section("Labels"); }
	private final BooleanSetting names = settings().add(new BooleanSetting("names", "Names", "Show the entity's name above it.", true));
	private final NumberSetting nameScale = settings().add(new NumberSetting("name_scale", "Name size",
			"Size of the name text, relative to the normal GUI font.", 0.75, 0.5, 2, 0.05, "x")).advanced();
	private final BooleanSetting coloredNames = settings().add(new BooleanSetting("colored_names", "Names in group color",
			"Draw names in the entity's highlight color. When off, names use the text color below.", true));
	private final ColorSetting textColor = settings().add(new ColorSetting("text_color", "Text color",
			"Color of the distance text, and of names when 'Names in group color' is off.", 0xFFFFFF)).advanced();
	private final BooleanSetting distance = settings().add(new BooleanSetting("distance", "Distance", "Show the distance in blocks.", true));
	private final NumberSetting distanceScale = settings().add(new NumberSetting("distance_scale", "Distance size",
			"Size of the distance text.", 0.65, 0.5, 2, 0.05, "x")).advanced();
	private final NumberSetting labelBackground = settings().add(new NumberSetting("label_background", "Background opacity",
			"How dark the box behind each label is. 0 removes it.", 0.5, 0, 1, 0.05, "")).advanced();
	private final NumberSetting labelPadding = settings().add(new NumberSetting("label_padding", "Background padding",
			"Space between the text and the edge of its box.", 2, 0, 6, 1, "px")).advanced();
	private final NumberSetting textSpacing = settings().add(new NumberSetting("text_spacing", "Text spacing",
			"Space between the lines of a label.", 1, 0, 4, 1, "px")).advanced();
	private final NumberSetting maxNameWidth = settings().add(new NumberSetting("max_name_width", "Maximum name width",
			"Longer names are shortened with '...'.", 110, 40, 240, 10, "px")).advanced();
	private final BooleanSetting equipment = settings().add(new BooleanSetting("equipment", "Armor and held items",
			"Show the armor and held items of players (and mobs that wear some) as icons above their label, with durability bars.", false));
	private final NumberSetting equipmentScale = settings().add(new NumberSetting("equipment_scale", "Item icon size",
			"Size of the equipment icons; 1 is the size of an inventory slot.", 0.75, 0.4, 1.5, 0.05, "x")).visibleWhen(equipment::get).advanced();
	private final NumberSetting maxLabels = settings().add(new NumberSetting("max_labels", "Maximum labels",
			"Clutter limit: labels are drawn for the nearest entities only.", 16, 0, 64, 1, "")).advanced();

	{ settings().section("Health"); }
	private final BooleanSetting health = settings().add(new BooleanSetting("health", "Health indicator",
			"Show the health of living entities in their label, for the groups chosen below.", false));
	private final EnumSetting<EspRenderer.HealthStyle> healthStyle = settings().add(new EnumSetting<>("health_style", "Health style",
			"A compact bar, the number of health points, or both.", EspRenderer.HealthStyle.BAR));
	private final NumberSetting healthBarScale = settings().add(new NumberSetting("health_bar_scale", "Health bar size",
			"Width and thickness of the health bar.", 1, 0.5, 3, 0.25, "x")).advanced();
	private final NumberSetting healthTextScale = settings().add(new NumberSetting("health_text_scale", "Health text size",
			"Size of the health number.", 0.65, 0.5, 2, 0.05, "x")).advanced();
	private final NumberSetting healthOpacity = settings().add(new NumberSetting("health_opacity", "Health opacity",
			"How solid the health bar and number are.", 0.9, 0.2, 1, 0.05, "")).advanced();
	private final BooleanSetting healthPlayers = settings().add(new BooleanSetting("health_players", "Health for players", "Show health on players.", true)).advanced();
	private final BooleanSetting healthHostile = settings().add(new BooleanSetting("health_hostile", "Health for hostile mobs", "Show health on hostile mobs.", true)).advanced();
	private final BooleanSetting healthPassive = settings().add(new BooleanSetting("health_passive", "Health for passive mobs and pets",
			"Show health on passive mobs and tamed animals.", false)).advanced();
	private final BooleanSetting healthVillagers = settings().add(new BooleanSetting("health_villagers", "Health for villagers", "Show health on villagers and traders.", false)).advanced();
	private final BooleanSetting healthOther = settings().add(new BooleanSetting("health_other", "Health for other entities", "Show health on other living entities.", false)).advanced();

	private final TargetLines.Settings lines = new TargetLines.Settings(settings(), false, TargetLines.Visibility.ALWAYS, 128, 256, 24);
	private final BooleanSetting linesPlayers = settings().add(new BooleanSetting("lines_players", "Lines to players", "Players get a line.", true)).advanced();
	private final BooleanSetting linesHostile = settings().add(new BooleanSetting("lines_hostile", "Lines to hostile mobs", "Hostile mobs get a line.", true)).advanced();
	private final BooleanSetting linesPassive = settings().add(new BooleanSetting("lines_passive", "Lines to passive mobs and pets",
			"Passive mobs and tamed animals get a line.", true)).advanced();
	private final BooleanSetting linesVillagers = settings().add(new BooleanSetting("lines_villagers", "Lines to villagers", "Villagers and traders get a line.", true)).advanced();
	private final BooleanSetting linesItems = settings().add(new BooleanSetting("lines_items", "Lines to items", "Dropped items get a line.", true)).advanced();
	private final BooleanSetting linesProjectiles = settings().add(new BooleanSetting("lines_projectiles", "Lines to projectiles",
			"Projectiles get a line.", true)).advanced();
	private final BooleanSetting linesOther = settings().add(new BooleanSetting("lines_other", "Lines to other entities", "Everything else gets a line.", true)).advanced();

	{ settings().section("Performance"); }
	private final NumberSetting maxEntities = settings().add(new NumberSetting("max_entities", "Maximum highlights",
			"Only the nearest entities up to this number are drawn.", 64, 8, 512, 8, ""));
	private final NumberSetting updateInterval = settings().add(new NumberSetting("update_interval", "Update interval",
			"How often the list of highlighted entities is rebuilt. Positions still move smoothly every frame.", 1, 1, 20, 1, "ticks"));

	private Set<EntityType<?>> selected = Set.of();
	private Set<EntityType<?>> overridden = Set.of();
	private List<Tracked> tracked = List.of();
	private int ticksUntilSightCheck;
	private List<EspRenderer.DrawnLabel> lastLabels = List.of();
	private int ticksUntilUpdate;
	private String choicesLanguage;
	private List<IdSetSetting.Choice> choices = List.of();

	public EntityEspModule() {
		super("entity_esp", "Entity ESP", "Highlights the entity types you choose, with labels, health, colors and target lines per group.",
				ModuleCategory.VISUALS, false, NO_KEY);
		types.choices(this::choices);
		types.onChange(ids -> selected = resolve(ids));
		selected = resolve(types.get());
		overrideTypes.choices(this::choices);
		overrideTypes.onChange(ids -> overridden = resolve(ids));
		overridden = resolve(overrideTypes.get());

		for (var sub : List.of(outlineWidth, outlineOpacity, outlinePlayers, outlineHostile, outlinePassive, outlineVillagers, outlineItems, outlineProjectiles,
				outlineOther)) {
			sub.visibleWhen(outline::get);
		}

		fillOpacity.visibleWhen(fill::get);
		nameScale.visibleWhen(names::get);
		coloredNames.visibleWhen(names::get);
		maxNameWidth.visibleWhen(names::get);
		distanceScale.visibleWhen(distance::get);
		for (var sub : List.of(healthStyle, healthOpacity, healthPlayers, healthHostile, healthPassive, healthVillagers, healthOther)) {
			sub.visibleWhen(health::get);
		}

		healthBarScale.visibleWhen(() -> health.get() && healthStyle.get() != EspRenderer.HealthStyle.TEXT);
		healthTextScale.visibleWhen(() -> health.get() && healthStyle.get() != EspRenderer.HealthStyle.BAR);
		for (var sub : List.of(linesPlayers, linesHostile, linesPassive, linesVillagers, linesItems, linesProjectiles, linesOther)) {
			sub.visibleWhen(lines.enabled::get);
		}

		overrideColor.visibleWhen(() -> !overrideTypes.get().isEmpty());
	}

	// ----- type registry ---------------------------------------------------------------------

	/** Which group an entity type belongs to, by its registry category and entity class. */
	public static String groupOf(EntityType<?> type) {
		if (type == EntityType.PLAYER) return PLAYERS;
		if (type == EntityType.VILLAGER || type == EntityType.WANDERING_TRADER) return VILLAGERS;
		if (type == EntityType.ITEM) return ITEMS;
		if (net.minecraft.world.entity.projectile.Projectile.class.isAssignableFrom(type.getBaseClass())) return PROJECTILES;
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

			all.sort(Comparator.<IdSetSetting.Choice>comparingInt(c -> GROUP_ORDER.indexOf(c.group())).thenComparing(c -> c.label().toLowerCase(Locale.ROOT)));
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
		if (overridden.contains(entity.getType())) {
			return overrideColor.get();
		}

		if (isPet(entity) && pets.get() != PetMode.LIKE_TYPE) {
			return petColor.get();
		}

		return switch (groupOf(entity.getType())) {
			case PLAYERS -> playerColor.get();
			case HOSTILE -> hostileColor.get();
			case VILLAGERS -> villagerColor.get();
			case ITEMS -> itemColor.get();
			case PROJECTILES -> projectileColor.get();
			case OTHER -> otherColor.get();
			default -> isPet(entity) ? petColor.get() : passiveColor.get();
		};
	}

	private boolean lineWanted(Entity entity) {
		if (isPet(entity)) {
			return linesPassive.get();
		}

		return switch (groupOf(entity.getType())) {
			case PLAYERS -> linesPlayers.get();
			case HOSTILE -> linesHostile.get();
			case VILLAGERS -> linesVillagers.get();
			case ITEMS -> linesItems.get();
			case PROJECTILES -> linesProjectiles.get();
			case OTHER -> linesOther.get();
			default -> linesPassive.get();
		};
	}

	/**
	 * One highlighted entity, with what was decided at the last update: color, outline and line. The position is read
	 * from the entity every frame.
	 */
	public static final class Tracked implements TargetLines.Target {
		private final Entity entity;
		private final int rgb;
		private final boolean outline;
		private final boolean line;
		private boolean sight = true;

		Tracked(Entity entity, int rgb, boolean outline, boolean line) {
			this.entity = entity;
			this.rgb = rgb;
			this.outline = outline;
			this.line = line;
		}

		public Entity entity() {
			return entity;
		}

		@Override
		public Vec3 linePoint(float partialTick) {
			return entity.getPosition(partialTick).add(0, entity.getBbHeight() / 2, 0);
		}

		@Override
		public int lineColor() {
			return rgb;
		}

		@Override
		public boolean lineOfSight() {
			return sight;
		}

		@Override
		public boolean wantsLine() {
			return line && !entity.isRemoved();
		}
	}

	/** Chooses the entities to highlight at the update interval; rendering only interpolates their positions. */
	@Override
	protected void onTick() {
		Minecraft client = Minecraft.getInstance();
		ClientLevel level = client.level;
		if (level == null || client.player == null || selected.isEmpty() && pets.get() != PetMode.ALWAYS) {
			tracked = List.of();
			return;
		}

		if (--ticksUntilUpdate <= 0) {
			ticksUntilUpdate = Performance.interval(updateInterval.intValue());
			update(client);
		}

		if (lines.enabled.get() && lines.visibility.get() == TargetLines.Visibility.LINE_OF_SIGHT && --ticksUntilSightCheck <= 0) {
			ticksUntilSightCheck = Performance.interval(4);
			int checks = Math.min(tracked.size(), lines.maxCount.intValue());
			for (int i = 0; i < checks; i++) {
				Tracked target = tracked.get(i);
				target.sight = client.player.hasLineOfSight(target.entity);
			}
		}
	}

	private void update(Minecraft client) {
		int max = Performance.cap(maxEntities.intValue(), 48);
		List<Tracked> found = new ArrayList<>(Math.min(max, 64));
		// Nearest first already: the first matches are the ones to keep.
		for (Entity entity : EntityIndex.within(maxDistance.get())) {
			if (found.size() >= max) {
				break;
			}

			if (!entity.isAlive() || entity.isSpectator() || !showInvisible.get() && entity.isInvisibleTo(client.player) || !wanted(entity)) {
				continue;
			}

			found.add(new Tracked(entity, colorOf(entity), outlined(entity), lineWanted(entity)));
		}

		tracked = List.copyOf(found);
		ticksUntilSightCheck = 0;
	}

	@Override
	protected void onDisable() {
		tracked = List.of();
		lastLabels = List.of();
		ticksUntilUpdate = 0;
	}

	@Override
	protected void onWorldChange() {
		tracked = List.of();
		lastLabels = List.of();
		ticksUntilUpdate = 0;
	}

	public TargetLines.Settings lines() {
		return lines;
	}

	/** Labels as drawn in the last frame; read by automated tests. */
	public List<EspRenderer.DrawnLabel> lastLabels() {
		return lastLabels;
	}

	private boolean outlined(Entity entity) {
		if (isPet(entity)) {
			return outlinePassive.get();
		}

		return switch (groupOf(entity.getType())) {
			case PLAYERS -> outlinePlayers.get();
			case HOSTILE -> outlineHostile.get();
			case VILLAGERS -> outlineVillagers.get();
			case ITEMS -> outlineItems.get();
			case PROJECTILES -> outlineProjectiles.get();
			case OTHER -> outlineOther.get();
			default -> outlinePassive.get();
		};
	}

	private boolean healthShown(Entity entity) {
		if (!health.get() || !(entity instanceof LivingEntity)) {
			return false;
		}

		if (isPet(entity)) {
			return healthPassive.get();
		}

		return switch (groupOf(entity.getType())) {
			case PLAYERS -> healthPlayers.get();
			case HOSTILE -> healthHostile.get();
			case VILLAGERS -> healthVillagers.get();
			case OTHER -> healthOther.get();
			default -> healthPassive.get();
		};
	}

	/** Entities highlighted after the last update, nearest first; read by automated tests. */
	public List<Entity> tracked() {
		List<Entity> entities = new ArrayList<>(tracked.size());
		tracked.forEach(t -> entities.add(t.entity));
		return entities;
	}

	/** Highlighted entities with their decided color, outline and line (for tests). */
	public List<Tracked> trackedTargets() {
		return tracked;
	}

	/**
	 * Boxes (and, with {@code labelLimit} above 0, labels for the nearest that many) for this frame. Labels are only built
	 * for entities that can get one, so names and distances are not formatted for every highlighted entity.
	 */
	private List<EspRenderer.Target> targets(Vec3 camera, float partialTick, int labelLimit, boolean cull) {
		List<EspRenderer.Target> targets = new ArrayList<>(tracked.size());
		for (Tracked t : tracked) {
			Entity entity = t.entity;
			if (entity.isRemoved()) {
				continue;
			}

			Vec3 interpolated = entity.getPosition(partialTick);
			AABB box = entity.getBoundingBox().move(interpolated.subtract(entity.position())).inflate(0.05);
			if (cull && !FrameProjection.inView(box)) {
				continue;
			}

			EspRenderer.Label label = null;
			if (targets.size() < labelLimit) {
				String name = names.get() ? entity.getDisplayName().getString() : null;
				String meters = distance.get() ? meters(interpolated.distanceTo(camera)) : null;
				boolean withHealth = healthShown(entity);
				LivingEntity living = withHealth ? (LivingEntity) entity : null;
				label = new EspRenderer.Label(name, meters, withHealth ? living.getHealth() : Float.NaN, withHealth ? living.getMaxHealth() : Float.NaN,
						equipment.get() ? equipmentOf(entity) : List.of());
			}

			targets.add(new EspRenderer.Target(box, t.rgb, t.outline, label));
		}

		return targets;
	}

	private static final String[] METERS = new String[257];

	/** "12m", from a small cache: labels are drawn every frame and the numbers repeat. */
	static String meters(double distance) {
		int m = (int) Math.round(distance);
		if (m < 0 || m >= METERS.length) {
			return m + "m";
		}

		String text = METERS[m];
		if (text == null) {
			text = m + "m";
			METERS[m] = text;
		}

		return text;
	}

	/** Main hand, helmet to boots, offhand: the non-empty ones, in the order a nametag shows them. */
	private static List<net.minecraft.world.item.ItemStack> equipmentOf(Entity entity) {
		if (!(entity instanceof LivingEntity living)) {
			return List.of();
		}

		List<net.minecraft.world.item.ItemStack> stacks = new ArrayList<>();
		for (net.minecraft.world.entity.EquipmentSlot slot : List.of(net.minecraft.world.entity.EquipmentSlot.MAINHAND, net.minecraft.world.entity.EquipmentSlot.HEAD,
				net.minecraft.world.entity.EquipmentSlot.CHEST, net.minecraft.world.entity.EquipmentSlot.LEGS, net.minecraft.world.entity.EquipmentSlot.FEET,
				net.minecraft.world.entity.EquipmentSlot.OFFHAND)) {
			net.minecraft.world.item.ItemStack stack = living.getItemBySlot(slot);
			if (!stack.isEmpty()) {
				stacks.add(stack);
			}
		}

		return stacks;
	}

	private EspRenderer.LabelStyle labelStyle() {
		return new EspRenderer.LabelStyle(nameScale.get().floatValue(), distanceScale.get().floatValue(), healthTextScale.get().floatValue(),
				healthBarScale.get().floatValue(), coloredNames.get(), textColor.get(), labelBackground.get().floatValue(), labelPadding.intValue(),
				textSpacing.intValue(), maxNameWidth.intValue(), healthStyle.get(), healthOpacity.get().floatValue(), equipmentScale.get().floatValue());
	}

	@Override
	public void renderWorld(RenderingUtilities render) {
		List<EspRenderer.Target> targets = targets(render.camera(), render.partialTick(), 0, true);
		EspRenderer.drawBoxes(render, targets, new EspRenderer.Style(outline.get(), outlineWidth.get().floatValue(), outlineOpacity.get().floatValue(),
				fill.get(), fillOpacity.get().floatValue(), throughWalls.get()));
		lines.draw(render, tracked);
	}

	@Override
	public void renderOverlay(UiContext ui, int screenWidth, int screenHeight, float partialTick) {
		int limit = Performance.cap(maxLabels.intValue(), 12);
		if (limit == 0 || !names.get() && !distance.get() && !health.get() && !equipment.get()) {
			lastLabels = List.of();
			return;
		}

		Vec3 camera = Minecraft.getInstance().gameRenderer.getMainCamera().position();
		lastLabels = EspRenderer.drawLabels(ui, targets(camera, partialTick, limit, true), screenWidth, screenHeight, limit, labelStyle());
	}

	@Override
	public java.util.List<String> aliases() {
		return java.util.List.of("player esp", "mob esp", "item esp", "projectile esp", "tracers");
	}

	@Override
	public String workload() {
		return trackedTargets().size() + " targets, " + lines().drawn() + " lines, " + lastLabels().size() + " labels";
	}
}
