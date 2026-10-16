package io.github.theamethyx.femboyclient.client.combat;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.equipment.Equippable;

/** Numbers from an item's own components, for comparing weapons and armor. */
public final class ItemStats {
	private ItemStats() {
	}

	/** Attack damage the item gives in the main hand (player base 1 included). */
	public static double attackDamage(ItemStack stack) {
		return stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY).compute(Attributes.ATTACK_DAMAGE, 1, EquipmentSlot.MAINHAND);
	}

	/** The armor slot the item goes in, or null. */
	public static EquipmentSlot armorSlot(ItemStack stack) {
		Equippable equippable = stack.get(DataComponents.EQUIPPABLE);
		return equippable == null || !equippable.slot().isArmor() ? null : equippable.slot();
	}

	/** Armor points plus a share of toughness for its slot; 0 for non-armor. */
	public static double armorScore(ItemStack stack) {
		EquipmentSlot slot = armorSlot(stack);
		if (slot == null) {
			return 0;
		}

		ItemAttributeModifiers modifiers = stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
		return modifiers.compute(Attributes.ARMOR, 0, slot) + modifiers.compute(Attributes.ARMOR_TOUGHNESS, 0, slot) * 0.5;
	}
}
