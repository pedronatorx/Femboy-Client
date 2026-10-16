package io.github.theamethyx.femboyclient.client.profile;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

import io.github.theamethyx.femboyclient.client.FemboyClient;
import io.github.theamethyx.femboyclient.client.modules.AutoAttributeSwapModule;
import io.github.theamethyx.femboyclient.core.config.ProfileRule;

/**
 * Switches profiles from the held item, gliding state and dimension, following the user's rules in order.
 *
 * <p>A switch happens only when the rule result changes, so choosing a profile by hand sticks until the held
 * item (or dimension) changes to something a different rule matches. Nothing happens while Auto Attribute Swap
 * has a weapon swapped in for a single hit, so that brief swap never changes the profile.
 */
public final class AutoProfileSwitcher {
	private String lastDesired;
	private String candidate;
	private int stableTicks;
	private String lastProblem;
	private int switches;

	/** Call once per client tick. */
	public void tick(Minecraft client, FemboyClient femboy) {
		LocalPlayer player = client.player;
		if (!femboy.options().autoProfile.get() || player == null || client.level == null) {
			lastDesired = null;
			return;
		}

		AutoAttributeSwapModule swap = femboy.modules().get(AutoAttributeSwapModule.class);
		if (swap != null && swap.swapInProgress()) {
			return;
		}

		String desired = ProfileRule.select(femboy.config().profileRules(), classify(player.getMainHandItem()), player.isFallFlying(),
				dimension(client.level));
		// Debounce: the same rule result must hold for the configured delay, so scrolling past items does not flicker.
		if (desired == null || !desired.equals(candidate)) {
			candidate = desired;
			stableTicks = 0;
		}

		if (desired == null || desired.equals(lastDesired) || ++stableTicks < femboy.options().profileDelay.intValue()) {
			return;
		}

		lastDesired = desired;
		if (desired.equals(femboy.config().activeProfile())) {
			lastProblem = null;
			return;
		}

		String problem = femboy.config().switchProfile(desired);
		lastProblem = problem;
		if (problem != null) {
			femboy.diagnostics().warn("Profiles", "Automatic switch to '" + desired + "' failed: " + problem);
			return;
		}

		switches++;
		if (femboy.options().profileMessages.get()) {
			io.github.theamethyx.femboyclient.client.hud.Notifications.show("Profile", desired, io.github.theamethyx.femboyclient.client.gui.Theme.ACCENT);
		}
	}

	/** Why the last automatic switch failed, or {@code null}. */
	public String lastProblem() {
		return lastProblem;
	}

	/** Number of automatic switches this session (for the GUI and tests). */
	public int switches() {
		return switches;
	}

	/** Forgets the last rule result, so the rules are applied again on the next tick. */
	public void reset() {
		lastDesired = null;
	}

	/** Item group of a stack by registry data (tags and item ids), or {@code null} if no group applies. */
	public static ProfileRule.Trigger classify(ItemStack stack) {
		if (stack.isEmpty()) return ProfileRule.Trigger.EMPTY_HAND;
		if (stack.is(ItemTags.SWORDS)) return ProfileRule.Trigger.SWORD;
		if (stack.is(ItemTags.AXES)) return ProfileRule.Trigger.AXE;
		if (stack.is(ItemTags.SPEARS)) return ProfileRule.Trigger.SPEAR;
		if (stack.is(Items.MACE)) return ProfileRule.Trigger.MACE;
		if (stack.is(Items.TRIDENT)) return ProfileRule.Trigger.TRIDENT;
		if (stack.is(Items.BOW)) return ProfileRule.Trigger.BOW;
		if (stack.is(Items.CROSSBOW)) return ProfileRule.Trigger.CROSSBOW;
		if (stack.is(Items.END_CRYSTAL)) return ProfileRule.Trigger.END_CRYSTAL;
		if (stack.is(Items.RESPAWN_ANCHOR)) return ProfileRule.Trigger.RESPAWN_ANCHOR;
		if (stack.is(Items.SPLASH_POTION) || stack.is(Items.LINGERING_POTION)) return ProfileRule.Trigger.POTION;
		if (stack.is(Items.TOTEM_OF_UNDYING)) return ProfileRule.Trigger.TOTEM;
		if (stack.is(Items.GOLDEN_APPLE) || stack.is(Items.ENCHANTED_GOLDEN_APPLE)) return ProfileRule.Trigger.GOLDEN_APPLE;
		if (stack.is(Items.FIREWORK_ROCKET)) return ProfileRule.Trigger.FIREWORK;
		if (stack.is(ItemTags.PICKAXES) || stack.is(ItemTags.SHOVELS)) return ProfileRule.Trigger.PICKAXE;
		if (stack.getItem() instanceof net.minecraft.world.item.BlockItem) return ProfileRule.Trigger.BLOCK;
		return null;
	}

	/** The vanilla dimension, or {@code null} for custom ones (which only match "Any dimension" rules). */
	public static ProfileRule.Dimension dimension(Level level) {
		if (level.dimension() == Level.OVERWORLD) return ProfileRule.Dimension.OVERWORLD;
		if (level.dimension() == Level.NETHER) return ProfileRule.Dimension.NETHER;
		if (level.dimension() == Level.END) return ProfileRule.Dimension.END;
		return null;
	}
}
