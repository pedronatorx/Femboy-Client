package io.github.theamethyx.femboyclient.client.modules;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionContents;

import io.github.theamethyx.femboyclient.client.combat.InventoryCounts;
import io.github.theamethyx.femboyclient.client.combat.ItemChoices;
import io.github.theamethyx.femboyclient.client.gui.Theme;
import io.github.theamethyx.femboyclient.client.hud.HudAnchor;
import io.github.theamethyx.femboyclient.client.hud.HudLine;
import io.github.theamethyx.femboyclient.client.module.ClientHooks;
import io.github.theamethyx.femboyclient.core.module.Module;
import io.github.theamethyx.femboyclient.core.module.ModuleCategory;
import io.github.theamethyx.femboyclient.core.setting.BooleanSetting;
import io.github.theamethyx.femboyclient.core.setting.EnumSetting;
import io.github.theamethyx.femboyclient.core.setting.IdSetSetting;
import io.github.theamethyx.femboyclient.core.setting.NumberSetting;

/**
 * Counts of the items you choose (totems, gapples, crystals, pearls, potions...) and reminders when an item is
 * still in your inventory but no longer in your hotbar or offhand. Only reads your own inventory.
 */
public final class ItemCountersModule extends Module implements ClientHooks.HudBlock {
	static final Set<String> DEFAULT_ITEMS = Set.of("minecraft:totem_of_undying", "minecraft:golden_apple", "minecraft:enchanted_golden_apple",
			"minecraft:end_crystal", "minecraft:obsidian", "minecraft:respawn_anchor", "minecraft:glowstone", "minecraft:ender_pearl",
			"minecraft:wind_charge", "minecraft:firework_rocket", "minecraft:experience_bottle", "minecraft:splash_potion", "minecraft:arrow");
	static final Set<String> DEFAULT_REFILL = Set.of("minecraft:totem_of_undying", "minecraft:golden_apple", "minecraft:splash_potion",
			"minecraft:ender_pearl", "minecraft:end_crystal", "minecraft:respawn_anchor", "minecraft:glowstone");

	private final EnumSetting<HudAnchor> anchor = settings().add(new EnumSetting<>("anchor", "Position",
			"Screen corner for this block.", HudAnchor.TOP_LEFT));
	private final IdSetSetting items = settings().add(new IdSetSetting("items", "Counted items", "Items whose total you want to see.", DEFAULT_ITEMS));
	private final BooleanSetting showZero = settings().add(new BooleanSetting("show_zero", "Show items you have none of",
			"Also list counted items with a count of 0.", false));
	private final NumberSetting lowAt = settings().add(new NumberSetting("low_at", "Low at",
			"Counts at or below this are shown in red.", 1, 0, 16, 1, ""));
	private final BooleanSetting splitPotions = settings().add(new BooleanSetting("split_potions", "Count potions by effect",
			"Count each kind of potion separately (e.g. Healing and Swiftness) instead of all splash potions together.", true));
	private final BooleanSetting refill = settings().add(new BooleanSetting("refill", "Refill reminders",
			"Remind you when one of the refill items is in your inventory but not in your hotbar or offhand.", true));
	private final IdSetSetting refillItems = settings().add(new IdSetSetting("refill_items", "Refill items",
			"Items that should always be ready in your hotbar or offhand.", DEFAULT_REFILL)).visibleWhen(refill::get);

	private Set<Item> counted = Set.of();
	private Set<Item> refilled = Set.of();

	@Override
	public Tier tier() {
		return Tier.ASSIST;
	}

	public ItemCountersModule() {
		super("item_counters", "Item Counters", "Counts of totems, gapples, crystals, pearls, potions and other items, with refill reminders.",
				ModuleCategory.INVENTORY, false, NO_KEY);
		items.choices(ItemChoices::all);
		refillItems.choices(ItemChoices::all);
		items.onChange(ids -> counted = ItemChoices.resolve(ids));
		refillItems.onChange(ids -> refilled = ItemChoices.resolve(ids));
	}

	@Override
	protected void onEnable() {
		counted = ItemChoices.resolve(items.get());
		refilled = ItemChoices.resolve(refillItems.get());
	}

	@Override
	public HudAnchor hudAnchor() {
		return anchor.get();
	}

	/** Totals per item, or per potion kind; in registry order of the first stack found. */
	public Map<String, Integer> counts() {
		LocalPlayer player = Minecraft.getInstance().player;
		Map<String, Integer> totals = new LinkedHashMap<>();
		if (player == null) {
			return totals;
		}

		Inventory inventory = player.getInventory();
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			ItemStack stack = inventory.getItem(i);
			if (!stack.isEmpty() && counted.contains(stack.getItem())) {
				totals.merge(label(stack), stack.getCount(), Integer::sum);
			}
		}

		if (showZero.get()) {
			for (Item item : counted) {
				totals.putIfAbsent(item.getName(new ItemStack(item)).getString(), 0);
			}
		}

		return totals;
	}

	private String label(ItemStack stack) {
		PotionContents potion = stack.get(DataComponents.POTION_CONTENTS);
		return splitPotions.get() && potion != null && potion.potion().isPresent() ? stack.getHoverName().getString() : stack.getItem().getName(stack).getString();
	}

	@Override
	public void buildHud(List<HudLine> lines) {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null) {
			return;
		}

		int low = lowAt.intValue();
		counts().forEach((name, count) -> lines.add(HudLine.of(name, String.valueOf(count), count <= low ? Theme.ERROR : Theme.TEXT)));
		if (!refill.get()) {
			return;
		}

		for (Item item : refilled) {
			int ready = InventoryCounts.countReady(player, stack -> stack.is(item));
			int total = InventoryCounts.count(player, stack -> stack.is(item));
			if (ready == 0 && total > 0) {
				lines.add(HudLine.of("Refill", item.getName(new ItemStack(item)).getString() + " (" + total + " in inventory)", Theme.WARNING));
			}
		}
	}
}
