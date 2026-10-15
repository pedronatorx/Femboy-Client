package io.github.theamethyx.femboyclient.client.modules;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

import io.github.theamethyx.femboyclient.client.FemboyClient;
import io.github.theamethyx.femboyclient.core.module.Module;
import io.github.theamethyx.femboyclient.core.module.ModuleCategory;
import io.github.theamethyx.femboyclient.core.setting.NumberSetting;

/**
 * Extends your client's entity and block reach with attribute modifiers on your own player. The server still
 * decides: vanilla accepts attacks up to your interaction range plus 3 blocks and block actions plus 1 block, so
 * more than that is rejected. Runs only in test environments.
 */
public final class ReachModule extends Module {
	private static final Identifier ID = Identifier.fromNamespaceAndPath(FemboyClient.MOD_ID, "reach");

	private final NumberSetting entity = settings().add(new NumberSetting("entity", "Extra entity reach",
			"Added to your attack range (3 blocks in survival). Vanilla servers allow up to +3.", 1, 0, 3, 0.1, "blocks"));
	private final NumberSetting block = settings().add(new NumberSetting("block", "Extra block reach",
			"Added to your block range (4.5 in survival). Vanilla servers allow up to +1.", 0.5, 0, 1, 0.1, "blocks"));

	public ReachModule() {
		super("reach", "Reach", "Longer attack and block reach, within what the server accepts.", ModuleCategory.AUTOMATION, false, NO_KEY);
	}

	@Override
	public boolean testEnvironmentOnly() {
		return false;
	}

	@Override
	protected void onTick() {
		LocalPlayer player = Minecraft.getInstance().player;
		apply(player, Attributes.ENTITY_INTERACTION_RANGE, entity.get());
		apply(player, Attributes.BLOCK_INTERACTION_RANGE, block.get());
	}

	@Override
	protected void onDisable() {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player != null) {
			remove(player, Attributes.ENTITY_INTERACTION_RANGE);
			remove(player, Attributes.BLOCK_INTERACTION_RANGE);
		}
	}

	/** Re-applied every tick: attribute updates from the server replace all modifiers. */
	private static void apply(LocalPlayer player, Holder<Attribute> attribute, double amount) {
		AttributeInstance instance = player.getAttribute(attribute);
		if (instance == null) {
			return;
		}

		AttributeModifier current = instance.getModifier(ID);
		if (current != null && current.amount() == amount) {
			return;
		}

		instance.removeModifier(ID);
		if (amount > 0) {
			instance.addTransientModifier(new AttributeModifier(ID, amount, AttributeModifier.Operation.ADD_VALUE));
		}
	}

	private static void remove(LocalPlayer player, Holder<Attribute> attribute) {
		AttributeInstance instance = player.getAttribute(attribute);
		if (instance != null) {
			instance.removeModifier(ID);
		}
	}
}
