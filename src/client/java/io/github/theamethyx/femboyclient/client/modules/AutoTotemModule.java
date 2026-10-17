package io.github.theamethyx.femboyclient.client.modules;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.Items;

import io.github.theamethyx.femboyclient.core.module.Module;
import io.github.theamethyx.femboyclient.core.module.ModuleCategory;
import io.github.theamethyx.femboyclient.core.setting.BooleanSetting;
import io.github.theamethyx.femboyclient.core.setting.NumberSetting;

/**
 * Keeps a Totem of Undying in your offhand by swapping one in from your inventory, the same click as pressing the
 * offhand-swap key over it in the inventory. Not while another container is open. Runs only in test environments.
 */
public final class AutoTotemModule extends Module {
	/** Button number vanilla uses for the offhand swap in inventory clicks. */
	private static final int OFFHAND_BUTTON = 40;

	public enum OffhandItem implements io.github.theamethyx.femboyclient.core.setting.EnumSetting.Labeled {
		TOTEM("Totem of Undying", Items.TOTEM_OF_UNDYING), SHIELD("Shield", Items.SHIELD), GOLDEN_APPLE("Golden apple", Items.GOLDEN_APPLE),
		END_CRYSTAL("End crystal", Items.END_CRYSTAL);

		private final String label;
		final net.minecraft.world.item.Item item;

		OffhandItem(String label, net.minecraft.world.item.Item item) {
			this.label = label;
			this.item = item;
		}

		@Override
		public String label() {
			return label;
		}
	}

	private final io.github.theamethyx.femboyclient.core.setting.EnumSetting<OffhandItem> item = settings().add(
			new io.github.theamethyx.femboyclient.core.setting.EnumSetting<>("item", "Item", "What to keep in your offhand.", OffhandItem.TOTEM));
	private final NumberSetting delay = settings().add(new NumberSetting("delay", "Delay",
			"Wait this long after the offhand empties before swapping a totem in.", 0, 0, 2000, 25, "ms"));
	private final BooleanSetting replaceItems = settings().add(new BooleanSetting("replace_items", "Replace other offhand items",
			"Also swap out a shield or other item in your offhand. Off: only fill an empty offhand.", true));
	private final NumberSetting health = settings().add(new NumberSetting("health", "Only below health",
			"Swap only at or below this health. 20 means always.", 20, 1, 20, 1, "HP"));

	private long missingSince = -1;
	private int swaps;

	public AutoTotemModule() {
		super("auto_totem", "Auto Offhand", "Keeps a totem (or a shield, golden apple or end crystal) in your offhand from your inventory.",
				ModuleCategory.AUTOMATION, false, NO_KEY);
	}

	@Override
	public boolean testEnvironmentOnly() {
		return false;
	}

	public int swaps() {
		return swaps;
	}

	@Override
	protected void onTick() {
		Minecraft client = Minecraft.getInstance();
		LocalPlayer player = client.player;
		var offhand = player.getOffhandItem();
		boolean needed = !offhand.is(item.get().item) && (offhand.isEmpty() || replaceItems.get()) && player.getHealth() <= health.get();
		if (!needed || client.gameMode == null || (client.screen != null && !(client.screen instanceof InventoryScreen))) {
			missingSince = -1;
			return;
		}

		long now = System.currentTimeMillis();
		if (missingSince < 0) {
			missingSince = now;
		}

		if (now - missingSince < delay.get()) {
			return;
		}

		Inventory inventory = player.getInventory();
		for (int i = 0; i < Inventory.INVENTORY_SIZE; i++) {
			if (inventory.getItem(i).is(item.get().item)) {
				// Inventory menu slots: 9-35 main inventory, 36-44 hotbar.
				int menuSlot = i < Inventory.getSelectionSize() ? 36 + i : i;
				client.gameMode.handleInventoryMouseClick(player.inventoryMenu.containerId, menuSlot, OFFHAND_BUTTON, ClickType.SWAP, player);
				swaps++;
				missingSince = -1;
				return;
			}
		}
	}
}
