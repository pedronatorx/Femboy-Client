package io.github.theamethyx.femboyclient.client.modules;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;

import io.github.theamethyx.femboyclient.client.combat.InventoryActions;
import io.github.theamethyx.femboyclient.client.gui.Theme;
import io.github.theamethyx.femboyclient.client.hud.HudAnchor;
import io.github.theamethyx.femboyclient.client.hud.HudLine;
import io.github.theamethyx.femboyclient.client.module.ClientHooks;
import io.github.theamethyx.femboyclient.core.module.Module;
import io.github.theamethyx.femboyclient.core.module.ModuleCategory;
import io.github.theamethyx.femboyclient.core.setting.BooleanSetting;
import io.github.theamethyx.femboyclient.core.setting.NumberSetting;

/**
 * Throws splash potions from your hotbar at your feet: healing or regeneration when your health is low, and Strength,
 * Speed or Fire Resistance when the effect is missing. Your view visibly turns down for the throw (the potion flies where
 * you look) and back afterwards. Sandbox only.
 */
public final class AutoPotModule extends Module implements ClientHooks.HudBlock {
	{
		settings().section("When to throw");
	}

	private final NumberSetting health = settings().add(new NumberSetting("health", "Heal below", "Throw healing or regeneration at or below this health.",
			10, 1, 20, 1, "HP"));
	private final BooleanSetting healing = settings().add(new BooleanSetting("healing", "Healing", "Use splash potions of Healing when low.", true));
	private final BooleanSetting regeneration = settings().add(new BooleanSetting("regeneration", "Regeneration",
			"Use splash potions of Regeneration when low and not already regenerating.", true));
	private final BooleanSetting strength = settings().add(new BooleanSetting("strength", "Keep Strength", "Throw Strength when you don't have it.", false));
	private final BooleanSetting speed = settings().add(new BooleanSetting("speed", "Keep Speed", "Throw Speed when you don't have it.", false));
	private final BooleanSetting fireResistance = settings().add(new BooleanSetting("fire_resistance", "Fire Resistance when burning",
			"Throw Fire Resistance while you are on fire and don't have it.", false));

	{
		settings().section("Timing");
	}

	private final NumberSetting delay = settings().add(new NumberSetting("delay", "Delay", "Least time between two throws.", 500, 100, 5000, 50, "ms"));
	private final BooleanSetting restore = settings().add(new BooleanSetting("restore", "Look back up",
			"Turn your view back and reselect your slot after the throw.", true));

	private enum Step { IDLE, AIM, THROW, RESTORE }

	private Step step = Step.IDLE;
	private int potionSlot;
	private int previousSlot;
	private float previousPitch;
	private long readyAt;
	private int throwsMade;
	private String lastThrown = "";

	public AutoPotModule() {
		super("auto_pot", "Auto Pot", "Throws splash potions at your feet: healing when low, buffs when they run out.", ModuleCategory.AUTOMATION,
				false, NO_KEY);
	}

	@Override
	public boolean testEnvironmentOnly() {
		return false;
	}

	public int throwsMade() {
		return throwsMade;
	}

	@Override
	protected void onEnable() {
		step = Step.IDLE;
		throwsMade = 0;
	}

	@Override
	protected void onDisable() {
		if (step == Step.THROW || step == Step.RESTORE) {
			restoreView(Minecraft.getInstance().player);
		}

		step = Step.IDLE;
	}

	@Override
	protected void onTick() {
		Minecraft client = Minecraft.getInstance();
		LocalPlayer player = client.player;
		if (client.gameMode == null) {
			return;
		}

		switch (step) {
			case IDLE -> {
				if (client.screen != null || player.isUsingItem() || System.currentTimeMillis() < readyAt) {
					return;
				}

				int slot = InventoryActions.findHotbar(player, this::wanted);
				if (slot < 0) {
					return;
				}

				potionSlot = slot;
				previousSlot = player.getInventory().getSelectedSlot();
				previousPitch = player.getXRot();
				player.getInventory().setSelectedSlot(slot);
				player.setXRot(90);
				step = Step.AIM;
			}
			case AIM -> {
				// One tick looking down, so the movement packet with the new pitch reaches the server before the throw.
				player.setXRot(90);
				step = Step.THROW;
			}
			case THROW -> {
				ItemStack stack = player.getInventory().getItem(potionSlot);
				if (player.getInventory().getSelectedSlot() == potionSlot && wanted(stack)) {
					lastThrown = stack.getHoverName().getString();
					client.gameMode.useItem(player, InteractionHand.MAIN_HAND);
					player.swing(InteractionHand.MAIN_HAND);
					throwsMade++;
				}

				readyAt = System.currentTimeMillis() + delay.get().longValue();
				step = Step.RESTORE;
			}
			case RESTORE -> {
				restoreView(player);
				step = Step.IDLE;
			}
		}
	}

	private void restoreView(LocalPlayer player) {
		if (player != null && restore.get()) {
			player.getInventory().setSelectedSlot(previousSlot);
			player.setXRot(previousPitch);
		}
	}

	/** Whether a stack is a splash potion that should be thrown now. */
	private boolean wanted(ItemStack stack) {
		LocalPlayer player = Minecraft.getInstance().player;
		if (!stack.is(Items.SPLASH_POTION)) {
			return false;
		}

		PotionContents contents = stack.get(DataComponents.POTION_CONTENTS);
		if (contents == null) {
			return false;
		}

		boolean low = player.getHealth() <= health.get();
		for (MobEffectInstance effect : contents.getAllEffects()) {
			if (low && healing.get() && effect.is(MobEffects.INSTANT_HEALTH)) return true;
			if (low && regeneration.get() && effect.is(MobEffects.REGENERATION) && !player.hasEffect(MobEffects.REGENERATION)) return true;
			if (strength.get() && missing(player, effect, MobEffects.STRENGTH)) return true;
			if (speed.get() && missing(player, effect, MobEffects.SPEED)) return true;
			if (fireResistance.get() && player.isOnFire() && missing(player, effect, MobEffects.FIRE_RESISTANCE)) return true;
		}

		return false;
	}

	private static boolean missing(LocalPlayer player, MobEffectInstance effect, Holder<MobEffect> type) {
		return effect.is(type) && !player.hasEffect(type);
	}

	@Override
	public HudAnchor hudAnchor() {
		return HudAnchor.BOTTOM_RIGHT;
	}

	@Override
	public void buildHud(List<HudLine> lines) {
		if (throwsMade > 0) {
			lines.add(HudLine.of("Auto Pot", throwsMade + " thrown, last " + lastThrown, Theme.TEXT_SECONDARY));
		}
	}
}
