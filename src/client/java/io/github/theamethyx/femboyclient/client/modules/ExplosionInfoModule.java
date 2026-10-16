package io.github.theamethyx.femboyclient.client.modules;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.CombatRules;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerExplosion;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import io.github.theamethyx.femboyclient.client.combat.Targets;
import io.github.theamethyx.femboyclient.client.gui.Theme;
import io.github.theamethyx.femboyclient.client.hud.HudAnchor;
import io.github.theamethyx.femboyclient.client.hud.HudLine;
import io.github.theamethyx.femboyclient.client.module.ClientHooks;
import io.github.theamethyx.femboyclient.client.render.RenderingUtilities;
import io.github.theamethyx.femboyclient.core.module.Module;
import io.github.theamethyx.femboyclient.core.module.ModuleCategory;
import io.github.theamethyx.femboyclient.core.setting.BooleanSetting;
import io.github.theamethyx.femboyclient.core.setting.ColorSetting;
import io.github.theamethyx.femboyclient.core.setting.EnumSetting;

/**
 * Crystal and respawn anchor information: where an end crystal can be placed within reach, and the explosion
 * damage you and your target would take from the crystal, placement spot or anchor under your crosshair.
 *
 * <p>The estimate follows vanilla: {@code ExplosionDamageCalculator.getEntityDamageAmount} with exposure from
 * {@code ServerExplosion.getSeenPercent}, difficulty scaling for players, armor ({@code CombatRules}), Resistance
 * and Protection/Blast Protection levels. Server-only effects (other enchantments, plugins) are not included.
 */
public final class ExplosionInfoModule extends Module implements ClientHooks.HudBlock, ClientHooks.WorldRender {
	private static final float CRYSTAL_POWER = 6;
	private static final float ANCHOR_POWER = 5;

	private final EnumSetting<HudAnchor> anchor = settings().add(new EnumSetting<>("anchor", "Position",
			"Screen corner for the estimates.", HudAnchor.TOP_RIGHT));
	private final BooleanSetting placements = settings().add(new BooleanSetting("placements", "Placement preview",
			"Highlight obsidian and bedrock within reach where an end crystal can be placed.", true));
	private final ColorSetting placementColor = settings().add(new ColorSetting("placement_color", "Placement color",
			"Color of possible crystal spots.", 0x9D6EFF)).visibleWhen(placements::get);
	private final BooleanSetting estimates = settings().add(new BooleanSetting("estimates", "Damage estimates",
			"Estimated damage to you and to your target from the crystal, spot or anchor under your crosshair.", true));
	private final BooleanSetting anchors = settings().add(new BooleanSetting("anchors", "Respawn anchor status",
			"Charges, and whether using the anchor here explodes (it does outside the Nether).", true));

	private final List<BlockPos> spots = new ArrayList<>();
	private Focus focus;

	@Override
	public Tier tier() {
		return Tier.ASSIST;
	}

	public ExplosionInfoModule() {
		super("explosion_info", "Crystal & Anchor Info", "Crystal placement preview and estimated explosion damage from crystals and respawn anchors.",
				ModuleCategory.COMBAT, false, NO_KEY);
	}

	@Override
	public HudAnchor hudAnchor() {
		return anchor.get();
	}

	/** What the crosshair points at: a crystal, a spot a crystal could be placed on, or an anchor. */
	public record Focus(String label, Vec3 center, float power, String note, BlockPos removed) {
		public Focus(String label, Vec3 center, float power, String note) {
			this(label, center, power, note, null);
		}

	}

	public Focus focus() {
		return focus;
	}

	public List<BlockPos> spots() {
		return List.copyOf(spots);
	}

	@Override
	protected void onDisable() {
		spots.clear();
		focus = null;
	}

	@Override
	protected void onTick() {
		Minecraft client = Minecraft.getInstance();
		LocalPlayer player = client.player;
		Level level = player.level();
		spots.clear();
		if (placements.get()) {
			double reach = player.blockInteractionRange();
			Vec3 eye = player.getEyePosition();
			int r = (int) Math.ceil(reach);
			BlockPos center = player.blockPosition();
			for (BlockPos pos : BlockPos.betweenClosed(center.offset(-r, -r - 1, -r), center.offset(r, r, r))) {
				if (canPlaceCrystal(level, pos) && eye.distanceToSqr(Vec3.atCenterOf(pos)) <= reach * reach) {
					spots.add(pos.immutable());
				}
			}
		}

		focus = findFocus(client, level);
	}

	/** Vanilla's {@code EndCrystalItem.useOn} checks: obsidian or bedrock, air above, no entity in the crystal's space. */
	public static boolean canPlaceCrystal(Level level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		if (!state.is(Blocks.OBSIDIAN) && !state.is(Blocks.BEDROCK)) {
			return false;
		}

		BlockPos above = pos.above();
		if (!level.isEmptyBlock(above)) {
			return false;
		}

		return level.getEntities(null, new AABB(above.getX(), above.getY(), above.getZ(), above.getX() + 1, above.getY() + 2, above.getZ() + 1)).isEmpty();
	}

	private Focus findFocus(Minecraft client, Level level) {
		HitResult hit = client.hitResult;
		if (hit instanceof EntityHitResult entityHit && entityHit.getEntity() instanceof EndCrystal crystal) {
			return new Focus("Crystal", crystal.position(), CRYSTAL_POWER, null);
		}

		if (!(hit instanceof BlockHitResult blockHit) || hit.getType() != HitResult.Type.BLOCK) {
			return null;
		}

		BlockPos pos = blockHit.getBlockPos();
		BlockState state = level.getBlockState(pos);
		if (canPlaceCrystal(level, pos)) {
			return new Focus("Crystal here", new Vec3(pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5), CRYSTAL_POWER, null);
		}

		if (anchors.get() && state.is(Blocks.RESPAWN_ANCHOR)) {
			int charges = state.getValue(RespawnAnchorBlock.CHARGE);
			boolean nether = level.dimension() == Level.NETHER;
			String note = charges + "/4 charges, " + (nether ? "sets your spawn here" : charges > 0 ? "EXPLODES when used here" : "uncharged");
			return new Focus("Anchor", pos.getCenter(), nether || charges == 0 ? 0 : ANCHOR_POWER, note, pos.immutable());
		}

		return null;
	}

	/** Estimated explosion damage to {@code entity}; 0 when out of range. */
	public static float estimate(LivingEntity entity, Vec3 center, float power) {
		return estimate(entity, center, power, null);
	}

	/**
	 * Estimated explosion damage when the block at {@code removed} is gone at the moment of the explosion (a respawn anchor
	 * removes itself before it explodes, so it does not shield anyone); {@code null} keeps every block.
	 */
	public static float estimate(LivingEntity entity, Vec3 center, float power, BlockPos removed) {
		float diameter = power * 2;
		double distance = Math.sqrt(entity.distanceToSqr(center)) / diameter;
		if (distance > 1) {
			return 0;
		}

		float seen = removed == null ? ServerExplosion.getSeenPercent(center, entity) : seenPercent(center, entity, removed);
		double impact = (1 - distance) * seen;
		float damage = (float) ((impact * impact + impact) / 2 * 7 * diameter + 1);
		if (entity instanceof Player) {
			Difficulty difficulty = entity.level().getDifficulty();
			if (difficulty == Difficulty.PEACEFUL) damage = 0;
			if (difficulty == Difficulty.EASY) damage = Math.min(damage / 2 + 1, damage);
			if (difficulty == Difficulty.HARD) damage = damage * 3 / 2;
		}

		damage = CombatRules.getDamageAfterAbsorb(entity, damage, entity.damageSources().explosion(null, null), entity.getArmorValue(),
				(float) entity.getAttributeValue(Attributes.ARMOR_TOUGHNESS));
		if (entity.hasEffect(MobEffects.RESISTANCE)) {
			int resisted = (entity.getEffect(MobEffects.RESISTANCE).getAmplifier() + 1) * 5;
			damage = Math.max(damage * (25 - resisted) / 25f, 0);
		}

		int protection = 0;
		for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
			var stack = entity.getItemBySlot(slot);
			protection += level(entity, Enchantments.PROTECTION, stack) + 2 * level(entity, Enchantments.BLAST_PROTECTION, stack);
		}

		return protection > 0 ? CombatRules.getDamageAfterMagicAbsorb(damage, protection) : damage;
	}

	/** {@code ServerExplosion.getSeenPercent} with the block at {@code removed} treated as air. */
	public static float seenPercent(Vec3 center, net.minecraft.world.entity.Entity entity, BlockPos removed) {
		AABB box = entity.getBoundingBox();
		double stepX = 1.0 / ((box.maxX - box.minX) * 2.0 + 1.0);
		double stepY = 1.0 / ((box.maxY - box.minY) * 2.0 + 1.0);
		double stepZ = 1.0 / ((box.maxZ - box.minZ) * 2.0 + 1.0);
		double offsetX = (1.0 - Math.floor(1.0 / stepX) * stepX) / 2.0;
		double offsetZ = (1.0 - Math.floor(1.0 / stepZ) * stepZ) / 2.0;
		Level level = entity.level();
		int clear = 0;
		int total = 0;
		for (double x = 0; x <= 1; x += stepX) {
			for (double y = 0; y <= 1; y += stepY) {
				for (double z = 0; z <= 1; z += stepZ) {
					Vec3 from = new Vec3(net.minecraft.util.Mth.lerp(x, box.minX, box.maxX) + offsetX, net.minecraft.util.Mth.lerp(y, box.minY, box.maxY),
							net.minecraft.util.Mth.lerp(z, box.minZ, box.maxZ) + offsetZ);
					net.minecraft.world.level.ClipContext context = new net.minecraft.world.level.ClipContext(from, center,
							net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, entity);
					boolean blocked = net.minecraft.world.level.BlockGetter.traverseBlocks(from, center, context, (ctx, pos) -> {
						if (pos.equals(removed)) {
							return null;
						}

						BlockState state = level.getBlockState(pos);
						return ctx.getBlockShape(state, level, pos).clip(from, center, pos) != null ? Boolean.TRUE : null;
					}, ctx -> Boolean.FALSE);
					if (!blocked) {
						clear++;
					}

					total++;
				}
			}
		}

		return total == 0 ? 0 : (float) clear / total;
	}

	private static int level(LivingEntity entity, ResourceKey<Enchantment> key, net.minecraft.world.item.ItemStack stack) {
		return entity.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).get(key)
				.map(holder -> EnchantmentHelper.getItemEnchantmentLevel((Holder<Enchantment>) holder, stack)).orElse(0);
	}

	@Override
	public void buildHud(List<HudLine> lines) {
		LocalPlayer player = Minecraft.getInstance().player;
		Focus current = focus;
		if (player == null || current == null) {
			return;
		}

		if (current.note() != null) {
			lines.add(HudLine.of(current.label(), current.note(), current.power() > 0 ? Theme.WARNING : Theme.TEXT_SECONDARY));
		}

		if (!estimates.get() || current.power() <= 0) {
			return;
		}

		float self = estimate(player, current.center(), current.power(), current.removed());
		float health = player.getHealth() + player.getAbsorptionAmount();
		lines.add(HudLine.of(current.note() == null ? current.label() + ": you" : "You", String.format(Locale.ROOT, "%.1f dmg", self),
				self >= health ? Theme.ERROR : self > 0 ? Theme.WARNING : Theme.SUCCESS));
		LivingEntity target = Targets.current();
		if (target != null && target != player) {
			float other = estimate(target, current.center(), current.power(), current.removed());
			lines.add(HudLine.of("Target", String.format(Locale.ROOT, "%.1f dmg", other), Theme.ACCENT_STRONG));
		}
	}

	@Override
	public void renderWorld(RenderingUtilities render) {
		if (!placements.get()) {
			return;
		}

		int fill = placementColor.withAlpha(0.25f);
		int line = placementColor.withAlpha(0.8f);
		for (BlockPos pos : spots) {
			AABB top = new AABB(pos.getX(), pos.getY() + 1, pos.getZ(), pos.getX() + 1, pos.getY() + 1.02, pos.getZ() + 1);
			render.filledBox(top, fill, false);
			render.boxOutline(top, line, 1, false);
		}
	}
}
