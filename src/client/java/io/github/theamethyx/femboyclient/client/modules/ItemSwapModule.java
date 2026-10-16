package io.github.theamethyx.femboyclient.client.modules;

import java.util.Set;

import org.lwjgl.glfw.GLFW;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.Item;

import io.github.theamethyx.femboyclient.client.combat.InventoryActions;
import io.github.theamethyx.femboyclient.client.combat.ItemChoices;
import io.github.theamethyx.femboyclient.client.gui.Theme;
import io.github.theamethyx.femboyclient.client.hud.Notifications;
import io.github.theamethyx.femboyclient.core.module.Module;
import io.github.theamethyx.femboyclient.core.module.ModuleCategory;
import io.github.theamethyx.femboyclient.core.setting.BooleanSetting;
import io.github.theamethyx.femboyclient.core.setting.IdSetSetting;
import io.github.theamethyx.femboyclient.core.setting.KeySetting;

/**
 * Up to three keys that each bring a chosen item to hand. If the item is in your hotbar, its slot is selected; if it is
 * only in your inventory, it is swapped into the slot you hold (the number-key swap players do by hand). Pressing the
 * same key again goes back. Works on any server; it makes the same clicks you would.
 */
public final class ItemSwapModule extends Module {
	private static final int SLOTS = 3;

	private final KeySetting[] keys = new KeySetting[SLOTS];
	private final IdSetSetting[] items = new IdSetSetting[SLOTS];

	{
		int[] defaults = {GLFW.GLFW_KEY_Z, KeySetting.NONE, KeySetting.NONE};
		String[] defaultItems = {"minecraft:totem_of_undying", "minecraft:golden_apple", "minecraft:ender_pearl"};
		for (int i = 0; i < SLOTS; i++) {
			settings().section("Swap " + (i + 1));
			keys[i] = settings().add(new KeySetting("key_" + (i + 1), "Key", "Press to bring one of the items below to hand.", defaults[i]));
			items[i] = settings().add(new IdSetSetting("items_" + (i + 1), "Items", "Items this key looks for, first match wins.",
					Set.of(defaultItems[i]))).choices(ItemChoices::all);
		}

		settings().section("Behavior");
	}

	private final BooleanSetting pressAgainToReturn = settings().add(new BooleanSetting("return", "Press again to go back",
			"A second press of the same key returns to the slot or item you had before.", true));
	private final BooleanSetting fromInventory = settings().add(new BooleanSetting("from_inventory", "Take from inventory",
			"If the item is not in your hotbar, swap it from your inventory into the slot you hold.", true));

	private final boolean[] wasDown = new boolean[SLOTS];
	/** The swap that is active, or -1. */
	private int active = -1;
	private int previousSlot;
	/** Menu slot the item came from when it was swapped in from the inventory, or -1 when only the slot changed. */
	private int pulledFrom = -1;
	private int pulledInto;
	private int swaps;

	public ItemSwapModule() {
		super("item_swap", "Item Swap", "Keys that bring a chosen item (totem, gapple, pearl...) to hand from your hotbar or inventory.",
				ModuleCategory.INVENTORY, false, NO_KEY);
	}

	@Override
	public Tier tier() {
		return Tier.AUTOMATION;
	}

	public int swaps() {
		return swaps;
	}

	@Override
	protected void onEnable() {
		active = -1;
	}

	@Override
	protected void onTick() {
		Minecraft client = Minecraft.getInstance();
		for (int i = 0; i < SLOTS; i++) {
			boolean down = keys[i].isBound() && client.screen == null && InputConstants.isKeyDown(client.getWindow(), keys[i].get());
			if (down && !wasDown[i]) {
				press(i);
			}

			wasDown[i] = down;
		}
	}

	/** Acts as if key {@code index} (0-2) was pressed; used by the key handling and by tests. */
	public void press(int index) {
		Minecraft client = Minecraft.getInstance();
		LocalPlayer player = client.player;
		if (player == null || client.gameMode == null || player.containerMenu != player.inventoryMenu) {
			return;
		}

		if (active == index && pressAgainToReturn.get()) {
			goBack(player);
			return;
		}

		if (active >= 0) {
			goBack(player);
		}

		Set<Item> wanted = ItemChoices.resolve(items[index].get());
		int hotbar = InventoryActions.findHotbar(player, stack -> wanted.contains(stack.getItem()));
		int selected = player.getInventory().getSelectedSlot();
		if (hotbar >= 0) {
			previousSlot = selected;
			pulledFrom = -1;
			player.getInventory().setSelectedSlot(hotbar);
		} else {
			int main = fromInventory.get() ? InventoryActions.findMain(player, stack -> wanted.contains(stack.getItem())) : -1;
			if (main < 0) {
				Notifications.show("Item Swap", "None of the items for swap " + (index + 1) + " are in your inventory.", Theme.WARNING);
				return;
			}

			previousSlot = selected;
			pulledFrom = main;
			pulledInto = selected;
			InventoryActions.swapWithHotbar(player.inventoryMenu, main, selected);
		}

		active = index;
		swaps++;
	}

	private void goBack(LocalPlayer player) {
		if (pulledFrom >= 0) {
			InventoryActions.swapWithHotbar(player.inventoryMenu, pulledFrom, pulledInto);
		}

		player.getInventory().setSelectedSlot(previousSlot);
		active = -1;
		pulledFrom = -1;
	}
}
