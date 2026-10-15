package io.github.theamethyx.femboyclient.client.combat;

import java.util.concurrent.ThreadLocalRandom;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import io.github.theamethyx.femboyclient.core.setting.BooleanSetting;
import io.github.theamethyx.femboyclient.core.setting.EnumSetting;
import io.github.theamethyx.femboyclient.core.setting.ModuleSettings;
import io.github.theamethyx.femboyclient.core.setting.NumberSetting;

/** The "Timing" settings shared by KillAura and Trigger Bot: when the next automatic attack may happen. */
public final class AttackTiming {
	public enum Mode implements EnumSetting.Labeled {
		CHARGE("Attack charge"), CPS("Clicks per second");

		private final String label;

		Mode(String label) {
			this.label = label;
		}

		@Override
		public String label() {
			return label;
		}
	}

	public final EnumSetting<Mode> mode;
	public final NumberSetting charge;
	public final NumberSetting minCps;
	public final NumberSetting maxCps;
	public final NumberSetting cooldown;
	public final BooleanSetting weaponOnly;
	public final BooleanSetting skipWhileUsing;

	private long nextAllowed;

	public AttackTiming(ModuleSettings settings) {
		settings.section("Timing");
		mode = settings.add(new EnumSetting<>("timing_mode", "Timing", "Attack when your attack charge reaches the threshold "
				+ "(full damage), or at a click rate.", Mode.CHARGE));
		charge = settings.add(new NumberSetting("charge", "Attack charge", "Attack once the charge reaches this. 100% deals full damage.",
				100, 10, 100, 5, "%")).visibleWhen(() -> mode.get() == Mode.CHARGE);
		minCps = settings.add(new NumberSetting("min_cps", "Minimum CPS", "Slowest click rate.", 8, 1, 20, 1, "")).visibleWhen(() -> mode.get() == Mode.CPS);
		maxCps = settings.add(new NumberSetting("max_cps", "Maximum CPS", "Fastest click rate; each delay is picked between the two.",
				12, 1, 20, 1, "")).visibleWhen(() -> mode.get() == Mode.CPS);
		cooldown = settings.add(new NumberSetting("cooldown", "Extra cooldown", "Additional wait after each attack.", 0, 0, 2000, 25, "ms"));
		weaponOnly = settings.add(new BooleanSetting("weapon_only", "Only with a weapon",
				"Only while holding a sword, axe, mace, spear or trident.", true));
		skipWhileUsing = settings.add(new BooleanSetting("skip_while_using", "Not while using an item",
				"Wait while you eat, drink, block or draw a bow.", true));
	}

	public static boolean isWeapon(ItemStack stack) {
		return stack.is(ItemTags.SWORDS) || stack.is(ItemTags.AXES) || stack.is(ItemTags.SPEARS) || stack.is(Items.MACE) || stack.is(Items.TRIDENT);
	}

	/** Whether the hand and item state allow an automatic attack at all. */
	public boolean handReady(LocalPlayer player) {
		if (weaponOnly.get() && !isWeapon(player.getMainHandItem())) return false;
		return !(skipWhileUsing.get() && player.isUsingItem());
	}

	public boolean ready(LocalPlayer player) {
		long now = System.currentTimeMillis();
		if (now < nextAllowed) {
			return false;
		}

		return mode.get() == Mode.CPS || player.getAttackStrengthScale(0.5f) * 100 >= charge.get() - 1.0E-3;
	}

	/** Call right after an attack was sent. */
	public void attacked() {
		long delay = cooldown.get().longValue();
		if (mode.get() == Mode.CPS) {
			double low = Math.min(minCps.get(), maxCps.get());
			double high = Math.max(minCps.get(), maxCps.get());
			double cps = low == high ? low : ThreadLocalRandom.current().nextDouble(low, high);
			delay += Math.round(1000 / cps);
		}

		nextAllowed = System.currentTimeMillis() + delay;
	}

	public void reset() {
		nextAllowed = 0;
	}
}
