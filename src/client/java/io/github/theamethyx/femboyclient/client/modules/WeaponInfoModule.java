package io.github.theamethyx.femboyclient.client.modules;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MaceItem;
import net.minecraft.world.item.component.KineticWeapon;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;

import io.github.theamethyx.femboyclient.client.combat.InventoryCounts;
import io.github.theamethyx.femboyclient.client.combat.Targets;
import io.github.theamethyx.femboyclient.client.gui.Theme;
import io.github.theamethyx.femboyclient.client.hud.HudAnchor;
import io.github.theamethyx.femboyclient.client.hud.HudLine;
import io.github.theamethyx.femboyclient.client.module.ClientHooks;
import io.github.theamethyx.femboyclient.core.module.Module;
import io.github.theamethyx.femboyclient.core.module.ModuleCategory;
import io.github.theamethyx.femboyclient.core.setting.BooleanSetting;
import io.github.theamethyx.femboyclient.core.setting.EnumSetting;

/**
 * Timing and status for the weapon in your hand: attack charge, shield state against axes, mace fall and smash
 * bonus, spear charge windows and Lunge, bow draw, crossbow load and trident charge. Values are read from the
 * item's own components and vanilla formulas; enchantment bonuses applied only by the server are not included.
 */
public final class WeaponInfoModule extends Module implements ClientHooks.HudBlock {
	private final EnumSetting<HudAnchor> anchor = settings().add(new EnumSetting<>("anchor", "Position",
			"Screen corner for this block.", HudAnchor.BOTTOM_RIGHT));
	private final BooleanSetting attackCharge = settings().add(new BooleanSetting("attack_charge", "Attack charge",
			"Your attack cooldown: hits before 100% deal less damage.", true));
	private final BooleanSetting axe = settings().add(new BooleanSetting("axe", "Axe and shields",
			"With an axe: whether the target's shield is raised, and your own shield's cooldown.", true));
	private final BooleanSetting mace = settings().add(new BooleanSetting("mace", "Mace",
			"With a mace: fall distance, whether a smash attack is possible, its base bonus damage, vertical speed and wind charges.", true));
	private final BooleanSetting spear = settings().add(new BooleanSetting("spear", "Spear",
			"With a spear: Lunge level and whether it can trigger, and the charge attack's windows while you hold use.", true));
	private final BooleanSetting ranged = settings().add(new BooleanSetting("ranged", "Bow, crossbow and trident",
			"Draw progress, crossbow load state, trident charge and Riptide conditions, and ammunition.", true));
	private final BooleanSetting onlyWhenRelevant = settings().add(new BooleanSetting("only_relevant", "Hide when not holding a weapon",
			"Show nothing while you hold something that is not a weapon.", true));

	@Override
	public Tier tier() {
		return Tier.ASSIST;
	}

	public WeaponInfoModule() {
		super("weapon_info", "Weapon Info", "Timing and status for the weapon in your hand: axe, mace, spear, bow, crossbow and trident.",
				ModuleCategory.COMBAT, false, NO_KEY);
	}

	@Override
	public HudAnchor hudAnchor() {
		return anchor.get();
	}

	@Override
	public void buildHud(List<HudLine> lines) {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null) {
			return;
		}

		ItemStack held = player.getMainHandItem();
		boolean isAxe = held.is(ItemTags.AXES);
		boolean isMace = held.is(Items.MACE);
		boolean isSpear = held.is(ItemTags.SPEARS);
		boolean isSword = held.is(ItemTags.SWORDS);
		boolean isRanged = held.is(Items.BOW) || held.is(Items.CROSSBOW) || held.is(Items.TRIDENT);
		if (onlyWhenRelevant.get() && !(isAxe || isMace || isSpear || isSword || isRanged)) {
			return;
		}

		if (attackCharge.get() && !held.is(Items.BOW) && !held.is(Items.CROSSBOW)) {
			float charge = player.getAttackStrengthScale(0);
			lines.add(HudLine.of("Attack", charge >= 1 ? "Ready" : Math.round(charge * 100) + "%", charge >= 1 ? Theme.SUCCESS : Theme.TEXT_SECONDARY));
		}

		if (isAxe && axe.get()) {
			axeLines(player, lines);
		}

		if (isMace && mace.get()) {
			maceLines(player, lines);
		}

		if (isSpear && spear.get()) {
			spearLines(player, held, lines);
		}

		if (isRanged && ranged.get()) {
			rangedLines(player, held, lines);
		}
	}

	private static void axeLines(LocalPlayer player, List<HudLine> lines) {
		LivingEntity target = Targets.current();
		if (target != null) {
			boolean blocking = target.isBlocking();
			lines.add(HudLine.of("Target shield", blocking ? "Raised - axe disables it" : "Down", blocking ? Theme.WARNING : Theme.TEXT_SECONDARY));
		}

		ItemStack shield = player.getOffhandItem().is(Items.SHIELD) ? player.getOffhandItem() : new ItemStack(Items.SHIELD);
		if (player.getCooldowns().isOnCooldown(shield)) {
			lines.add(HudLine.of("Your shield", "Disabled " + Math.round(player.getCooldowns().getCooldownPercent(shield, 0) * 100) + "%", Theme.ERROR));
		}
	}

	/** Mace smash bonus before Density and other enchantments, from {@code MaceItem.getAttackDamageBonus}. */
	public static double smashBonus(double fallDistance) {
		if (fallDistance <= 3) return 4 * fallDistance;
		if (fallDistance <= 8) return 12 + 2 * (fallDistance - 3);
		return 22 + fallDistance - 8;
	}

	private static void maceLines(LocalPlayer player, List<HudLine> lines) {
		boolean smash = MaceItem.canSmashAttack(player);
		lines.add(HudLine.of("Fall", String.format(Locale.ROOT, "%.1f m", player.fallDistance), smash ? Theme.SUCCESS : Theme.TEXT_SECONDARY));
		lines.add(HudLine.of("Smash", smash ? String.format(Locale.ROOT, "Ready  +%.1f dmg", smashBonus(player.fallDistance))
				: player.isFallFlying() ? "Not while gliding" : "Fall more than 1.5 m", smash ? Theme.SUCCESS : Theme.TEXT_MUTED));
		lines.add(HudLine.of("Vertical", String.format(Locale.ROOT, "%+.1f m/s", player.getDeltaMovement().y * 20)));
		int charges = InventoryCounts.count(player, stack -> stack.is(Items.WIND_CHARGE));
		ItemStack windCharge = new ItemStack(Items.WIND_CHARGE);
		boolean cooling = player.getCooldowns().isOnCooldown(windCharge);
		lines.add(HudLine.of("Wind charges", charges + (cooling ? "  (cooldown)" : ""), charges == 0 ? Theme.ERROR : Theme.TEXT));
	}

	private static void spearLines(LocalPlayer player, ItemStack held, List<HudLine> lines) {
		int lunge = level(player, Enchantments.LUNGE, held);
		if (lunge > 0) {
			String blocked = player.isPassenger() ? "not while riding" : player.isFallFlying() ? "not while gliding"
					: player.isInWater() ? "not in water" : null;
			lines.add(HudLine.of("Lunge " + lunge, blocked == null ? "Ready on your next jab" : blocked, blocked == null ? Theme.SUCCESS : Theme.WARNING));
		}

		KineticWeapon kinetic = held.get(DataComponents.KINETIC_WEAPON);
		if (kinetic == null || !player.isUsingItem() || player.getUseItem() != held) {
			return;
		}

		int used = player.getTicksUsingItem();
		if (used < kinetic.delayTicks()) {
			lines.add(HudLine.of("Charge", "Winding up " + (kinetic.delayTicks() - used) + " ticks", Theme.TEXT_SECONDARY));
			return;
		}

		int into = used - kinetic.delayTicks();
		double speed = player.getLookAngle().dot(KineticWeapon.getMotion(player));
		window(lines, "Damage", kinetic.damageConditions(), into, speed);
		window(lines, "Knockback", kinetic.knockbackConditions(), into, speed);
		window(lines, "Dismount", kinetic.dismountConditions(), into, speed);
	}

	private static void window(List<HudLine> lines, String name, Optional<KineticWeapon.Condition> condition, int into, double speed) {
		if (condition.isEmpty()) {
			return;
		}

		KineticWeapon.Condition c = condition.get();
		if (into > c.maxDurationTicks()) {
			lines.add(HudLine.of(name, "Window over", Theme.TEXT_MUTED));
			return;
		}

		boolean fastEnough = speed >= c.minSpeed();
		lines.add(HudLine.of(name, String.format(Locale.ROOT, "%d ticks left, speed %.1f/%.1f", c.maxDurationTicks() - into, speed, c.minSpeed()),
				fastEnough ? Theme.SUCCESS : Theme.WARNING));
	}

	private static void rangedLines(LocalPlayer player, ItemStack held, List<HudLine> lines) {
		boolean using = player.isUsingItem() && player.getUseItem() == held;
		if (held.is(Items.BOW)) {
			if (using) {
				float power = BowItem.getPowerForTime(player.getTicksUsingItem());
				lines.add(HudLine.of("Draw", power >= 1 ? "Full" : Math.round(power * 100) + "%", power >= 1 ? Theme.SUCCESS : Theme.TEXT_SECONDARY));
			}

			lines.add(HudLine.of("Arrows", String.valueOf(InventoryCounts.count(player, stack -> stack.is(ItemTags.ARROWS)))));
		} else if (held.is(Items.CROSSBOW)) {
			if (CrossbowItem.isCharged(held)) {
				lines.add(HudLine.of("Crossbow", "Loaded", Theme.SUCCESS));
			} else if (using) {
				int duration = Math.max(1, CrossbowItem.getChargeDuration(held, player));
				lines.add(HudLine.of("Crossbow", "Loading " + Math.min(100, Math.round(100f * player.getTicksUsingItem() / duration)) + "%", Theme.TEXT_SECONDARY));
			} else {
				lines.add(HudLine.of("Crossbow", "Empty", Theme.TEXT_MUTED));
			}

			lines.add(HudLine.of("Ammo", InventoryCounts.count(player, stack -> stack.is(ItemTags.ARROWS)) + " arrows, "
					+ InventoryCounts.count(player, stack -> stack.is(Items.FIREWORK_ROCKET)) + " rockets"));
		} else {
			if (using) {
				boolean ready = player.getTicksUsingItem() >= 10;
				lines.add(HudLine.of("Trident", ready ? "Ready to throw" : "Charging", ready ? Theme.SUCCESS : Theme.TEXT_SECONDARY));
			}

			if (EnchantmentHelper.getTridentSpinAttackStrength(held, player) > 0) {
				boolean wet = player.isInWaterOrRain();
				lines.add(HudLine.of("Riptide", wet ? "Usable" : "Needs water or rain", wet ? Theme.SUCCESS : Theme.WARNING));
			}
		}
	}

	private static int level(LocalPlayer player, ResourceKey<Enchantment> key, ItemStack stack) {
		Optional<Holder.Reference<Enchantment>> holder = player.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).get(key);
		return holder.map(h -> EnchantmentHelper.getItemEnchantmentLevel(h, stack)).orElse(0);
	}
}
