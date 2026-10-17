package io.github.theamethyx.femboyclient.client.modules;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

import io.github.theamethyx.femboyclient.client.FemboyClient;
import io.github.theamethyx.femboyclient.core.module.Module;
import io.github.theamethyx.femboyclient.core.module.ModuleCategory;
import io.github.theamethyx.femboyclient.core.setting.NumberSetting;

/** Walk up full blocks (or more) without jumping, by raising your step height. Sandbox only. */
public final class StepModule extends Module {
	private static final Identifier ID = Identifier.fromNamespaceAndPath(FemboyClient.MOD_ID, "step");

	private final NumberSetting height = settings().add(new NumberSetting("height", "Step height", "Highest step you walk up (vanilla 0.6).", 1, 0.6, 2.5, 0.1, "blocks"));

	public StepModule() {
		super("step", "Step", "Walk up full blocks without jumping.", ModuleCategory.MOVEMENT, false, NO_KEY);
	}

	@Override
	public boolean testEnvironmentOnly() {
		return false;
	}

	@Override
	protected void onTick() {
		AttributeInstance instance = Minecraft.getInstance().player.getAttribute(Attributes.STEP_HEIGHT);
		double amount = height.get() - 0.6;
		AttributeModifier current = instance.getModifier(ID);
		if (current == null || current.amount() != amount) {
			instance.removeModifier(ID);
			instance.addTransientModifier(new AttributeModifier(ID, amount, AttributeModifier.Operation.ADD_VALUE));
		}
	}

	@Override
	protected void onDisable() {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player != null) {
			player.getAttribute(Attributes.STEP_HEIGHT).removeModifier(ID);
		}
	}
}
