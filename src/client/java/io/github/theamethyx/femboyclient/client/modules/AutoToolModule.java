package io.github.theamethyx.femboyclient.client.modules;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import io.github.theamethyx.femboyclient.client.FemboyClient;
import io.github.theamethyx.femboyclient.core.module.Module;
import io.github.theamethyx.femboyclient.core.module.ModuleCategory;
import io.github.theamethyx.femboyclient.core.setting.BooleanSetting;
import io.github.theamethyx.femboyclient.core.setting.NumberSetting;

/** When you start mining, selects the fastest tool for the block from your hotbar (and optionally switches back). */
public final class AutoToolModule extends Module {
	private final BooleanSetting switchBack = settings().add(new BooleanSetting("switch_back", "Switch back",
			"Go back to the slot you had when you stop mining.", true));
	private final NumberSetting minDurability = settings().add(new NumberSetting("min_durability", "Spare tools below",
			"Skip tools with this much durability or less, so they do not break.", 5, 0, 100, 1, ""));

	private int originalSlot = -1;
	private int idleTicks;
	private int switches;

	public AutoToolModule() {
		super("auto_tool", "Auto Tool", "Selects the fastest hotbar tool when you start mining a block.", ModuleCategory.INVENTORY, false, NO_KEY);
	}

	@Override
	public Tier tier() {
		return Tier.AUTOMATION;
	}

	public int switches() {
		return switches;
	}

	/** Called by the mining mixin when a block is hit. */
	public static void onMining(BlockPos pos) {
		FemboyClient femboy = FemboyClient.get();
		AutoToolModule module = femboy == null ? null : femboy.modules().get(AutoToolModule.class);
		if (module != null && module.isRunning()) {
			femboy.modules().runGuarded(module, io.github.theamethyx.femboyclient.core.module.ModuleManager.Phase.TICK, () -> module.select(pos));
		}
	}

	private void select(BlockPos pos) {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null || player.isCreative()) {
			return;
		}

		idleTicks = 0;
		BlockState state = player.level().getBlockState(pos);
		Inventory inventory = player.getInventory();
		int current = inventory.getSelectedSlot();
		int best = current;
		float bestSpeed = speed(inventory.getItem(current), state);
		for (int slot = 0; slot < Inventory.getSelectionSize(); slot++) {
			ItemStack stack = inventory.getItem(slot);
			float speed = speed(stack, state);
			if (speed > bestSpeed + 0.01f) {
				best = slot;
				bestSpeed = speed;
			}
		}

		if (best != current) {
			if (originalSlot < 0) {
				originalSlot = current;
			}

			inventory.setSelectedSlot(best);
			switches++;
		}
	}

	private float speed(ItemStack stack, BlockState state) {
		if (stack.isDamageableItem() && stack.getMaxDamage() - stack.getDamageValue() <= minDurability.intValue()) {
			return 0;
		}

		float speed = stack.getDestroySpeed(state);
		// Prefer a tool that actually gets drops when the block needs one.
		return state.requiresCorrectToolForDrops() && !stack.isCorrectToolForDrops(state) ? speed * 0.01f : speed;
	}

	@Override
	protected void onTick() {
		Minecraft client = Minecraft.getInstance();
		if (originalSlot >= 0 && !client.options.keyAttack.isDown() && ++idleTicks > 4) {
			if (switchBack.get()) {
				client.player.getInventory().setSelectedSlot(originalSlot);
			}

			originalSlot = -1;
		}
	}
}
