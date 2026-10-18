package io.github.theamethyx.femboyclient.client.modules;

import java.util.List;
import java.util.Locale;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

import net.fabricmc.loader.api.FabricLoader;

import io.github.theamethyx.femboyclient.client.gui.Theme;
import io.github.theamethyx.femboyclient.client.hud.HudAnchor;
import io.github.theamethyx.femboyclient.client.hud.HudLine;
import io.github.theamethyx.femboyclient.client.module.ClientHooks;
import io.github.theamethyx.femboyclient.core.module.Module;
import io.github.theamethyx.femboyclient.core.module.ModuleCategory;
import io.github.theamethyx.femboyclient.core.setting.BooleanSetting;
import io.github.theamethyx.femboyclient.core.setting.EnumSetting;

/**
 * "What am I looking at": the block or creature under the crosshair, which mod adds it, and what you can tell about it
 * from the client: growth of crops, redstone power, whether your held tool can harvest it and how long breaking takes,
 * a creature's health, armor, owner and villager job. Inspired by Jade/WTHIT, written from scratch; it only uses what the
 * client already knows (no server queries), so container contents and other server-side data are not shown.
 */
public final class LookInfoModule extends Module implements ClientHooks.HudBlock {
	private final EnumSetting<HudAnchor> anchor = settings().add(new EnumSetting<>("anchor", "Position",
			"Screen corner when not placed with the HUD editor.", HudAnchor.TOP_RIGHT));
	private final BooleanSetting modName = settings().add(new BooleanSetting("mod_name", "Which mod",
			"Show which mod (or Minecraft) adds the block or creature.", true));
	private final BooleanSetting harvest = settings().add(new BooleanSetting("harvest", "Harvest and break time",
			"Whether the item in your hand can harvest the block, and how long breaking it takes with it.", true));
	private final BooleanSetting states = settings().add(new BooleanSetting("states", "Growth and power",
			"Growth of crops and saplings, redstone power and similar block details.", true));
	private final BooleanSetting creatures = settings().add(new BooleanSetting("creatures", "Creature details",
			"Health, armor, owner of pets and the job of villagers.", true));
	private final BooleanSetting position = settings().add(new BooleanSetting("position", "Position",
			"The coordinates of the block or creature.", false)).advanced();

	public LookInfoModule() {
		super("look_info", "Look At", "What you are looking at: block or creature, its mod, growth, power, harvest tool, break time and health.",
				ModuleCategory.HUD, false, NO_KEY);
	}

	@Override
	public Tier tier() {
		return Tier.VISUAL;
	}

	@Override
	public List<String> aliases() {
		return List.of("jade", "waila", "wthit", "tooltip", "block info", "what am i looking at");
	}

	@Override
	public HudAnchor hudAnchor() {
		return anchor.get();
	}

	@Override
	public void buildHud(List<HudLine> lines) {
		Minecraft client = Minecraft.getInstance();
		LocalPlayer player = client.player;
		HitResult hit = client.hitResult;
		if (player == null || client.level == null || hit == null || hit.getType() == HitResult.Type.MISS) {
			return;
		}

		if (hit instanceof BlockHitResult blockHit) {
			block(client, player, blockHit.getBlockPos(), lines);
		} else if (hit instanceof EntityHitResult entityHit) {
			entity(player, entityHit.getEntity(), lines);
		}
	}

	private void block(Minecraft client, LocalPlayer player, BlockPos pos, List<HudLine> lines) {
		BlockState state = client.level.getBlockState(pos);
		if (state.isAir()) {
			return;
		}

		lines.add(HudLine.text(state.getBlock().getName().getString(), Theme.TEXT));
		if (modName.get()) {
			lines.add(HudLine.text(modOf(BuiltInRegistries.BLOCK.getKey(state.getBlock()).getNamespace()), Theme.ACCENT_2));
		}

		if (states.get()) {
			for (Property<?> property : state.getProperties()) {
				if (property instanceof IntegerProperty integer && property.getName().equals("age")) {
					int age = state.getValue(integer);
					int max = integer.getPossibleValues().stream().mapToInt(Integer::intValue).max().orElse(age);
					lines.add(age >= max ? HudLine.of("Growth", "Mature", Theme.SUCCESS)
							: HudLine.of("Growth", growth(age, max), Theme.TEXT));
				} else if (property instanceof IntegerProperty integer && property.getName().equals("power")) {
					lines.add(HudLine.of("Power", String.valueOf(state.getValue(integer)), Theme.ERROR));
				} else if (property instanceof IntegerProperty integer && property.getName().equals("level") && !state.getFluidState().isEmpty()) {
					lines.add(HudLine.of("Fluid level", String.valueOf(state.getValue(integer))));
				}
			}
		}

		if (harvest.get()) {
			float hardness = state.getDestroySpeed(client.level, pos);
			if (hardness < 0) {
				lines.add(HudLine.of("Breaking", "Unbreakable", Theme.TEXT_MUTED));
			} else {
				boolean canHarvest = !state.requiresCorrectToolForDrops() || player.hasCorrectToolForDrops(state);
				lines.add(HudLine.of("Harvest", canHarvest ? "Drops with this tool" : "Needs a better tool", canHarvest ? Theme.SUCCESS : Theme.WARNING));
				float perTick = state.getDestroyProgress(player, client.level, pos);
				lines.add(HudLine.of("Break time", breakTime(perTick)));
			}
		}

		if (position.get()) {
			lines.add(HudLine.of("At", pos.getX() + " " + pos.getY() + " " + pos.getZ()));
		}
	}

	private void entity(LocalPlayer player, Entity entity, List<HudLine> lines) {
		lines.add(HudLine.text(entity.getDisplayName().getString(), Theme.TEXT));
		if (modName.get()) {
			lines.add(HudLine.text(modOf(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).getNamespace()), Theme.ACCENT_2));
		}

		if (creatures.get() && entity instanceof LivingEntity living) {
			lines.add(HudLine.of("Health", String.format(Locale.ROOT, "%.1f / %.0f", living.getHealth() + living.getAbsorptionAmount(), living.getMaxHealth()),
					living.getHealth() <= living.getMaxHealth() / 4 ? Theme.ERROR : Theme.SUCCESS));
			if (living.getArmorValue() > 0) {
				lines.add(HudLine.of("Armor", String.valueOf(living.getArmorValue())));
			}

			if (living.isBaby()) {
				lines.add(HudLine.text("Baby", Theme.TEXT_SECONDARY));
			}

			if (entity instanceof OwnableEntity ownable && ownable.getOwnerReference() != null) {
				LivingEntity owner = ownable.getOwner();
				lines.add(HudLine.of("Owner", owner != null ? owner.getName().getString() : "someone not nearby"));
			}

			if (entity instanceof Villager villager) {
				var data = villager.getVillagerData();
				String job = data.profession().unwrapKey().map(key -> label(key.identifier().getPath())).orElse("?");
				lines.add(HudLine.of("Job", job + ", level " + data.level()));
			}
		}

		if (position.get()) {
			lines.add(HudLine.of("At", String.format(Locale.ROOT, "%.1f %.1f %.1f, %.1f m", entity.getX(), entity.getY(), entity.getZ(), player.distanceTo(entity))));
		}
	}

	/** "57%" for a crop at age 4 of 7. */
	static String growth(int age, int max) {
		return max <= 0 ? "Mature" : Math.round(100.0 * age / max) + "%";
	}

	/** Seconds to break a block from the game's per-tick progress; "Instant" and "Never" at the ends. */
	static String breakTime(float progressPerTick) {
		if (progressPerTick >= 1) {
			return "Instant";
		}

		if (progressPerTick <= 0) {
			return "Never";
		}

		int ticks = (int) Math.ceil(1 / progressPerTick);
		return String.format(Locale.ROOT, "%.2f s", ticks / 20.0);
	}

	private static String modOf(String namespace) {
		if (namespace.equals("minecraft")) {
			return "Minecraft";
		}

		return FabricLoader.getInstance().getModContainer(namespace).map(mod -> mod.getMetadata().getName()).orElse(namespace);
	}

	private static String label(String id) {
		String text = id.replace('_', ' ');
		return text.isEmpty() ? text : Character.toUpperCase(text.charAt(0)) + text.substring(1);
	}
}
