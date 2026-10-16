package io.github.theamethyx.femboyclient.client.combat;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import io.github.theamethyx.femboyclient.client.modules.ExplosionInfoModule;
import io.github.theamethyx.femboyclient.core.setting.BooleanSetting;
import io.github.theamethyx.femboyclient.core.setting.ModuleSettings;
import io.github.theamethyx.femboyclient.core.setting.NumberSetting;

/**
 * The damage limits AutoCrystal and AutoAnchor share: least damage to the target, most damage to yourself, and never an
 * explosion that would kill you. Damage is the Crystal &amp; Anchor Info estimate (vanilla's formula with exposure,
 * armor, Resistance and protection enchantments).
 */
public final class ExplosionSafety {
	public final NumberSetting minTarget;
	public final NumberSetting maxSelf;
	public final BooleanSetting antiSuicide;

	public ExplosionSafety(ModuleSettings settings, double defaultMinTarget, double defaultMaxSelf) {
		settings.section("Damage");
		minTarget = settings.add(new NumberSetting("min_target_damage", "Least target damage", "Only explode where the target takes at least this much.",
				defaultMinTarget, 0, 36, 0.5, "HP"));
		maxSelf = settings.add(new NumberSetting("max_self_damage", "Most self damage", "Never explode where you would take more than this.",
				defaultMaxSelf, 0, 36, 0.5, "HP"));
		antiSuicide = settings.add(new BooleanSetting("anti_suicide", "Never kill yourself",
				"Also skip any explosion that would take your health and absorption to 0.", true));
	}

	/** One evaluated explosion spot. */
	public record Spot(Vec3 center, float targetDamage, float selfDamage) {
	}

	/** Estimates the explosion at {@code center} and returns it when it passes the limits, otherwise {@code null}. */
	public Spot evaluate(LocalPlayer self, LivingEntity target, Vec3 center, float power) {
		return evaluate(self, target, center, power, null);
	}

	/** Like {@link #evaluate(LocalPlayer, LivingEntity, Vec3, float)}, with the block at {@code removed} gone when it explodes. */
	public Spot evaluate(LocalPlayer self, LivingEntity target, Vec3 center, float power, net.minecraft.core.BlockPos removed) {
		float targetDamage = ExplosionInfoModule.estimate(target, center, power, removed);
		if (targetDamage < minTarget.get()) {
			return null;
		}

		float selfDamage = self.isCreative() ? 0 : ExplosionInfoModule.estimate(self, center, power, removed);
		if (selfDamage > maxSelf.get() || antiSuicide.get() && selfDamage >= self.getHealth() + self.getAbsorptionAmount()) {
			return null;
		}

		return new Spot(center, targetDamage, selfDamage);
	}
}
