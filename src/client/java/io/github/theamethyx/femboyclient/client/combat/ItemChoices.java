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

	/** Block categories in display order; a click on a category in the picker selects or clears all of it. */
	public static final List<String> BLOCK_GROUPS = List.of("Ores", "Containers and spawners", "Redstone", "Plants and wood", "Other blocks");
	private static final java.util.regex.Pattern REDSTONE = java.util.regex.Pattern.compile(
			"redstone|repeater|comparator|piston|observer|lever|button|pressure_plate|daylight|target|tripwire|rail|note_block|sculk_sensor|lightning_rod");
	private static final java.util.regex.Pattern PLANTS = java.util.regex.Pattern.compile(
			"_log|_wood|_stem|_hyphae|leaves|planks|sapling|flower|tulip|orchid|allium|daisy|poppy|dandelion|_grass|fern|vine|mushroom|wheat|carrots|potatoes|beetroots|melon|pumpkin|cactus|sugar_cane|bamboo|kelp|azalea|roots|moss");

	/** Which picker category a block belongs to (by its class and id). */
	public static String blockGroup(net.minecraft.world.level.block.Block block, String id) {
		String path = id.substring(id.indexOf(':') + 1);
		if (path.endsWith("_ore") || path.equals("ancient_debris")) return "Ores";
		if (block instanceof net.minecraft.world.level.block.BaseEntityBlock) return "Containers and spawners";
		if (REDSTONE.matcher(path).find()) return "Redstone";
		if (PLANTS.matcher(path).find()) return "Plants and wood";
		return "Other blocks";
	}

	/** Every registered block as a choice, grouped into {@link #BLOCK_GROUPS}. */
	public static List<IdSetSetting.Choice> blocks() {
		if (blocks == null) {
			List<IdSetSetting.Choice> choices = new ArrayList<>();
			for (net.minecraft.world.level.block.Block block : BuiltInRegistries.BLOCK) {
				String id = BuiltInRegistries.BLOCK.getKey(block).toString();
				choices.add(new IdSetSetting.Choice(id, block.getName().getString(), blockGroup(block, id)));
			}

			choices.sort(Comparator.comparingInt((IdSetSetting.Choice c) -> BLOCK_GROUPS.indexOf(c.group())).thenComparing(c -> c.label().toLowerCase(Locale.ROOT)));
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
