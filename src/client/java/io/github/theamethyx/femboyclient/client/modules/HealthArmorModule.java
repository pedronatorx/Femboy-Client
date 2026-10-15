package io.github.theamethyx.femboyclient.client.modules;

import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

import io.github.theamethyx.femboyclient.client.gui.Theme;
import io.github.theamethyx.femboyclient.client.hud.HudAnchor;
import io.github.theamethyx.femboyclient.client.hud.HudLine;
import io.github.theamethyx.femboyclient.client.module.ClientHooks;
import io.github.theamethyx.femboyclient.core.module.Module;
import io.github.theamethyx.femboyclient.core.module.ModuleCategory;
import io.github.theamethyx.femboyclient.core.setting.BooleanSetting;
import io.github.theamethyx.femboyclient.core.setting.EnumSetting;
import io.github.theamethyx.femboyclient.core.setting.NumberSetting;

/** Your health, food, armor and held-item durability, with low-health and low-durability warnings. */
public final class HealthArmorModule extends Module implements ClientHooks.HudBlock {
	private static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET,
			EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND};

	private final EnumSetting<HudAnchor> anchor = settings().add(new EnumSetting<>("anchor", "Position",
			"Screen corner for this block.", HudAnchor.BOTTOM_RIGHT));
	private final BooleanSetting health = settings().add(new BooleanSetting("health", "Health and food",
			"Health with absorption, food and saturation.", true));
	private final NumberSetting lowHealth = settings().add(new NumberSetting("low_health", "Low health warning",
			"Show a warning at or below this many hearts. 0 turns it off.", 4, 0, 10, 0.5, "hearts"));
	private final BooleanSetting durability = settings().add(new BooleanSetting("durability", "Equipment durability",
			"Durability of your armor and the items in your hands.", true));
	private final BooleanSetting onlyLow = settings().add(new BooleanSetting("only_low", "Only low durability",
			"List only items at or below the warning level.", false)).visibleWhen(durability::get);
	private final NumberSetting lowDurability = settings().add(new NumberSetting("low_durability", "Low durability warning",
			"Durability at or below this percentage is shown in red.", 15, 1, 50, 1, "%"));
	private final BooleanSetting alerts = settings().add(new BooleanSetting("alerts", "Durability alerts",
			"Show a message above the hotbar once when an item drops to the warning level.", true));

	private final Map<EquipmentSlot, Boolean> wasLow = new EnumMap<>(EquipmentSlot.class);

	public HealthArmorModule() {
		super("health_armor", "Health & Armor", "Health, food, armor and held-item durability with low-health and durability warnings.",
				ModuleCategory.INVENTORY, false, NO_KEY);
	}

	@Override
	public HudAnchor hudAnchor() {
		return anchor.get();
	}

	@Override
	protected void onDisable() {
		wasLow.clear();
	}

	/** Remaining durability in percent, or -1 for items without durability. */
	static double percentLeft(ItemStack stack) {
		if (stack.isEmpty() || !stack.isDamageableItem()) {
			return -1;
		}

		return 100.0 * (stack.getMaxDamage() - stack.getDamageValue()) / stack.getMaxDamage();
	}

	@Override
	protected void onTick() {
		LocalPlayer player = Minecraft.getInstance().player;
		for (EquipmentSlot slot : SLOTS) {
			double left = percentLeft(player.getItemBySlot(slot));
			boolean low = left >= 0 && left <= lowDurability.get();
			if (low && !wasLow.getOrDefault(slot, false) && alerts.get()) {
				player.displayClientMessage(Component.literal(player.getItemBySlot(slot).getHoverName().getString() + " is at "
						+ Math.round(left) + "% durability"), true);
			}

			wasLow.put(slot, low);
		}
	}

	@Override
	public void buildHud(List<HudLine> lines) {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null) {
			return;
		}

		if (health.get()) {
			float hp = player.getHealth();
			float absorption = player.getAbsorptionAmount();
			boolean low = lowHealth.get() > 0 && hp <= lowHealth.get() * 2;
			lines.add(HudLine.of("Health", String.format(Locale.ROOT, "%.1f", hp) + (absorption > 0 ? String.format(Locale.ROOT, " +%.0f", absorption) : "")
					+ (low ? "  LOW" : ""), low ? Theme.ERROR : Theme.TEXT));
			lines.add(HudLine.of("Food", player.getFoodData().getFoodLevel() + String.format(Locale.ROOT, "  sat %.1f", player.getFoodData().getSaturationLevel())));
		}

		if (!durability.get()) {
			return;
		}

		for (EquipmentSlot slot : SLOTS) {
			ItemStack stack = player.getItemBySlot(slot);
			double left = percentLeft(stack);
			if (left < 0) {
				continue;
			}

			boolean low = left <= lowDurability.get();
			if (onlyLow.get() && !low) {
				continue;
			}

			lines.add(HudLine.of(stack.getHoverName().getString(), (stack.getMaxDamage() - stack.getDamageValue()) + "  " + Math.round(left) + "%",
					low ? Theme.ERROR : left <= 40 ? Theme.WARNING : Theme.TEXT));
		}
	}
}
