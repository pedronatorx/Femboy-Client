package io.github.theamethyx.femboyclient.client.modules;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MaceItem;

import io.github.theamethyx.femboyclient.client.combat.Targets;
import io.github.theamethyx.femboyclient.client.combat.TargetSettings;
import io.github.theamethyx.femboyclient.client.gui.Theme;
import io.github.theamethyx.femboyclient.client.hud.HudAnchor;
import io.github.theamethyx.femboyclient.client.hud.HudLine;
import io.github.theamethyx.femboyclient.client.mixin.MultiPlayerGameModeAccessor;
import io.github.theamethyx.femboyclient.client.module.ClientHooks;
import io.github.theamethyx.femboyclient.core.module.Module;
import io.github.theamethyx.femboyclient.core.module.ModuleCategory;
import io.github.theamethyx.femboyclient.core.setting.BooleanSetting;
import io.github.theamethyx.femboyclient.core.setting.NumberSetting;

/**
 * StunSlam: while you fall onto a target holding up a shield, hit it with an axe (which disables the shield) and
 * then with a mace (smash attack), then switch back to the slot you had.
 *
 * <p>States: {@code READY} → (falling, target in reach with a raised shield, axe and mace in the hotbar, cooldown
 * over) → axe hit → {@code WAITING} for the configured delay (0 = same tick) → mace hit → back to your slot →
 * {@code READY}. If the target leaves reach, dies or you land while waiting, the sequence is cancelled and your slot
 * restored. In vanilla the mace's smash bonus is added after the attack-charge scaling, so the second hit keeps it.
 * Runs only in test environments.
 */
public final class AutoStunSlamModule extends Module implements ClientHooks.HudBlock {
	/** True while a sequence picks weapons itself; Auto Attribute Swap stays out of it. */
	public static boolean sequenceActive;

	private final NumberSetting minFall = settings().add(new NumberSetting("min_fall", "Minimum fall",
			"Start only after falling this far. Vanilla's smash attack needs more than 1.5 blocks.", 2, 1.6, 20, 0.1, "blocks"));
	private final NumberSetting delay = settings().add(new NumberSetting("delay", "Delay between hits",
			"Ticks between the axe hit and the mace hit. 0 hits with both in the same tick.", 0, 0, 5, 1, "ticks"));
	private final NumberSetting cooldown = settings().add(new NumberSetting("cooldown", "Cooldown",
			"Wait after a sequence before the next one.", 500, 0, 5000, 50, "ms"));
	private final BooleanSetting requireShield = settings().add(new BooleanSetting("require_shield", "Only against a raised shield",
			"Run only when the target is blocking. Turn off to also open with the axe against targets without a shield.", true));
	private final BooleanSetting holding = settings().add(new BooleanSetting("only_holding", "Only while holding the axe or mace",
			"Run only while your hand holds an axe or a mace.", true));
	private final BooleanSetting useTarget = settings().add(new BooleanSetting("use_target", "Use Target HUD's target",
			"Besides the entity under your crosshair, also use the target from Target HUD (or the one you hit last) when it is in reach.", true));
	private final BooleanSetting hud = settings().add(new BooleanSetting("hud", "Show on HUD", "Show the state and the last result.", true));

	private int axeSlot = -1;
	private int maceSlot = -1;
	private int originalSlot = -1;
	private LivingEntity target;
	private int waitTicks;
	private boolean waiting;
	private long readyAt;
	private String lastResult = "Ready";
	private int completed;

	public AutoStunSlamModule() {
		super("auto_stunslam", "Auto StunSlam", "While falling onto a shielding target: axe hit to disable the shield, then a mace smash, then back.",
				ModuleCategory.AUTOMATION, false, NO_KEY);
	}

	@Override
	public boolean testEnvironmentOnly() {
		return true;
	}

	public String lastResult() {
		return lastResult;
	}

	public int completed() {
		return completed;
	}

	@Override
	protected void onEnable() {
		lastResult = "Ready";
		completed = 0;
		readyAt = 0;
	}

	@Override
	protected void onDisable() {
		if (waiting) {
			finish(Minecraft.getInstance().player, "Cancelled: module turned off");
		}
	}

	@Override
	protected void onTick() {
		Minecraft client = Minecraft.getInstance();
		LocalPlayer player = client.player;
		if (client.gameMode == null) {
			return;
		}

		if (waiting) {
			if (!inReach(player, target) || player.onGround()) {
				finish(player, player.onGround() ? "Cancelled: landed before the mace hit" : "Cancelled: target left reach");
			} else if (--waitTicks <= 0) {
				maceHit(client, player);
			}

			return;
		}

		if (System.currentTimeMillis() < readyAt || client.screen != null || player.onGround() || player.isFallFlying()
				|| player.fallDistance < minFall.get() || player.getDeltaMovement().y >= 0) {
			return;
		}

		ItemStack held = player.getMainHandItem();
		if (holding.get() && !held.is(ItemTags.AXES) && !held.is(Items.MACE)) {
			return;
		}

		LivingEntity candidate = pickTarget(client, player);
		if (candidate == null || (requireShield.get() && !candidate.isBlocking())) {
			return;
		}

		axeSlot = findHotbar(player.getInventory(), stack -> stack.is(ItemTags.AXES));
		maceSlot = findHotbar(player.getInventory(), stack -> stack.is(Items.MACE));
		if (axeSlot < 0 || maceSlot < 0) {
			lastResult = "Needs an axe and a mace in the hotbar";
			return;
		}

		target = candidate;
		originalSlot = player.getInventory().getSelectedSlot();
		sequenceActive = true;
		try {
			player.getInventory().setSelectedSlot(axeSlot);
			client.gameMode.attack(player, target);
			player.swing(InteractionHand.MAIN_HAND);
		} finally {
			sequenceActive = false;
		}

		waiting = true;
		waitTicks = delay.intValue();
		if (waitTicks <= 0) {
			maceHit(client, player);
		}
	}

	private void maceHit(Minecraft client, LocalPlayer player) {
		if (!MaceItem.canSmashAttack(player)) {
			finish(player, "Axe hit; mace skipped (fall too short for a smash)");
			return;
		}

		sequenceActive = true;
		try {
			player.getInventory().setSelectedSlot(maceSlot);
			client.gameMode.attack(player, target);
			player.swing(InteractionHand.MAIN_HAND);
		} finally {
			sequenceActive = false;
		}

		completed++;
		finish(player, "Axe and mace hit " + target.getDisplayName().getString());
	}

	private void finish(LocalPlayer player, String result) {
		waiting = false;
		lastResult = result;
		readyAt = System.currentTimeMillis() + cooldown.get().longValue();
		if (player != null && originalSlot >= 0) {
			player.getInventory().setSelectedSlot(originalSlot);
			((MultiPlayerGameModeAccessor) Minecraft.getInstance().gameMode).femboyclient$ensureHasSentCarriedItem();
		}

		originalSlot = -1;
		target = null;
	}

	private LivingEntity pickTarget(Minecraft client, LocalPlayer player) {
		Entity looked = client.crosshairPickEntity;
		if (looked instanceof LivingEntity living && inReach(player, living)) {
			return living;
		}

		if (useTarget.get()) {
			LivingEntity other = Targets.current();
			return inReach(player, other) ? other : null;
		}

		return null;
	}

	private static boolean inReach(LocalPlayer player, LivingEntity entity) {
		return entity != null && entity.isAlive() && entity.level() == player.level()
				&& TargetSettings.distance(player, entity) <= player.entityInteractionRange() && player.hasLineOfSight(entity);
	}

	private static int findHotbar(Inventory inventory, java.util.function.Predicate<ItemStack> matches) {
		for (int i = 0; i < Inventory.getSelectionSize(); i++) {
			if (matches.test(inventory.getItem(i))) {
				return i;
			}
		}

		return -1;
	}

	@Override
	public HudAnchor hudAnchor() {
		return HudAnchor.BOTTOM_RIGHT;
	}

	@Override
	public void buildHud(List<HudLine> lines) {
		if (hud.get()) {
			lines.add(HudLine.of("StunSlam", waiting ? "Axe hit, mace next" : lastResult, waiting ? Theme.WARNING : Theme.TEXT_SECONDARY));
		}
	}
}
