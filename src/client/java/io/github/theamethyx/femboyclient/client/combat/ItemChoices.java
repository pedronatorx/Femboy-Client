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
}
