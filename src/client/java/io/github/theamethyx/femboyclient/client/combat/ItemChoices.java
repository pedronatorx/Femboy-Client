package io.github.theamethyx.femboyclient.client.combat;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import io.github.theamethyx.femboyclient.core.setting.IdSetSetting;

/** Every registered item as a choice for item-list settings, and resolving saved ids back to items. */
public final class ItemChoices {
	private static List<IdSetSetting.Choice> all;

	private ItemChoices() {
	}

	public static List<IdSetSetting.Choice> all() {
		if (all == null) {
			List<IdSetSetting.Choice> choices = new ArrayList<>();
			for (Item item : BuiltInRegistries.ITEM) {
				choices.add(new IdSetSetting.Choice(BuiltInRegistries.ITEM.getKey(item).toString(), item.getName(new ItemStack(item)).getString(), "Items"));
			}

			choices.sort(Comparator.comparing(c -> c.label().toLowerCase(Locale.ROOT)));
			all = List.copyOf(choices);
		}

		return all;
	}

	public static Set<Item> resolve(Set<String> ids) {
		Set<Item> items = new HashSet<>();
		for (String id : ids) {
			BuiltInRegistries.ITEM.getOptional(Identifier.parse(id)).ifPresent(items::add);
		}

		return Set.copyOf(items);
	}

	private static List<IdSetSetting.Choice> blocks;

	/** Every registered block as a choice. */
	public static List<IdSetSetting.Choice> blocks() {
		if (blocks == null) {
			List<IdSetSetting.Choice> choices = new ArrayList<>();
			for (net.minecraft.world.level.block.Block block : BuiltInRegistries.BLOCK) {
				String id = BuiltInRegistries.BLOCK.getKey(block).toString();
				choices.add(new IdSetSetting.Choice(id, block.getName().getString(), id.endsWith("_ore") || id.contains("debris") ? "Ores" : "Blocks"));
			}

			choices.sort(Comparator.comparing((IdSetSetting.Choice c) -> c.group().equals("Ores") ? 0 : 1).thenComparing(c -> c.label().toLowerCase(Locale.ROOT)));
			blocks = List.copyOf(choices);
		}

		return blocks;
	}

	public static Set<net.minecraft.world.level.block.Block> resolveBlocks(Set<String> ids) {
		Set<net.minecraft.world.level.block.Block> result = new HashSet<>();
		for (String id : ids) {
			BuiltInRegistries.BLOCK.getOptional(Identifier.parse(id)).ifPresent(result::add);
		}

		return Set.copyOf(result);
	}
}
