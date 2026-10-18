package io.github.theamethyx.femboyclient.client.modules;

import java.util.List;
import java.util.Locale;

import org.lwjgl.glfw.GLFW;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;

import io.github.theamethyx.femboyclient.client.FemboyClient;
import io.github.theamethyx.femboyclient.client.mixin.AbstractContainerScreenAccessor;
import io.github.theamethyx.femboyclient.core.module.Module;
import io.github.theamethyx.femboyclient.core.module.ModuleCategory;
import io.github.theamethyx.femboyclient.core.setting.BooleanSetting;

/**
 * A search box next to every container screen (inventory, chests, shulker boxes, furnaces...): slots whose item
 * matches are outlined and the rest are dimmed. It matches the item's name and id, its enchantments, and the items
 * inside shulker boxes and bundles. Only looks; it never moves items.
 */
public final class InventorySearchModule extends Module {
	private final BooleanSetting contents = settings().add(new BooleanSetting("contents", "Search inside containers",
			"Also match shulker boxes and bundles by the items in them.", true));
	private final BooleanSetting enchantments = settings().add(new BooleanSetting("enchantments", "Search enchantments",
			"Also match items by their enchantments (\"mending\", \"sharpness\").", true));
	private final BooleanSetting remember = settings().add(new BooleanSetting("remember", "Keep the search",
			"Keep what you typed when you close and open containers; off clears it each time.", false));

	private String query = "";

	public InventorySearchModule() {
		super("inventory_search", "Inventory Search", "A search box next to containers that highlights matching items, including inside shulker boxes.",
				ModuleCategory.INVENTORY, false, NO_KEY);
	}

	@Override
	public Tier tier() {
		return Tier.VISUAL;
	}

	@Override
	public List<String> aliases() {
		return List.of("item search", "find item", "chest search");
	}

	public String query() {
		return query;
	}

	public void setQuery(String text) {
		query = text == null ? "" : text;
	}

	/** Whether the stack matches the search; an empty search matches nothing (nothing is marked). */
	public boolean matches(ItemStack stack) {
		String q = query.trim().toLowerCase(Locale.ROOT);
		return !q.isEmpty() && !stack.isEmpty() && matches(stack, q, contents.get(), enchantments.get(), 0);
	}

	static boolean matches(ItemStack stack, String q, boolean searchContents, boolean searchEnchantments, int depth) {
		if (stack.getHoverName().getString().toLowerCase(Locale.ROOT).contains(q)
				|| BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath().replace('_', ' ').contains(q)) {
			return true;
		}

		if (searchEnchantments && (enchanted(stack.getEnchantments(), q) || enchanted(stack.get(DataComponents.STORED_ENCHANTMENTS), q))) {
			return true;
		}

		if (searchContents && depth == 0) {
			ItemContainerContents container = stack.get(DataComponents.CONTAINER);
			if (container != null) {
				for (ItemStack inner : container.nonEmptyItems()) {
					if (matches(inner, q, false, searchEnchantments, 1)) {
						return true;
					}
				}
			}

			BundleContents bundle = stack.get(DataComponents.BUNDLE_CONTENTS);
			if (bundle != null) {
				for (ItemStack inner : bundle.items()) {
					if (matches(inner, q, false, searchEnchantments, 1)) {
						return true;
					}
				}
			}
		}

		return false;
	}

	private static boolean enchanted(ItemEnchantments enchantments, String q) {
		if (enchantments == null || enchantments.isEmpty()) {
			return false;
		}

		for (Holder<Enchantment> enchantment : enchantments.keySet()) {
			if (enchantment.value().description().getString().toLowerCase(Locale.ROOT).contains(q)) {
				return true;
			}
		}

		return false;
	}

	/** Called for every slot as a container screen draws it (see {@code AbstractContainerScreenMixin}). */
	public static void drawSlot(AbstractContainerScreen<?> screen, GuiGraphics graphics, Slot slot) {
		InventorySearchModule module = instance();
		// Empty slots stay as they are: dimming them too would darken the whole screen.
		if (module == null || module.query.isBlank() || screen instanceof CreativeModeInventoryScreen || slot.getItem().isEmpty()) {
			return;
		}

		if (module.matches(slot.getItem())) {
			int color = 0xFFB58FFF;
			graphics.fill(slot.x - 1, slot.y - 1, slot.x + 17, slot.y, color);
			graphics.fill(slot.x - 1, slot.y + 16, slot.x + 17, slot.y + 17, color);
			graphics.fill(slot.x - 1, slot.y, slot.x, slot.y + 16, color);
			graphics.fill(slot.x + 16, slot.y, slot.x + 17, slot.y + 16, color);
		} else {
			graphics.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, 0xB0101018);
		}
	}

	private static InventorySearchModule instance() {
		FemboyClient femboy = FemboyClient.get();
		InventorySearchModule module = femboy == null ? null : femboy.modules().get(InventorySearchModule.class);
		return module != null && module.isRunning() ? module : null;
	}

	/** Adds the search box to container screens; registered once at startup. */
	public static void registerEvents() {
		ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
			InventorySearchModule module = instance();
			if (module == null || !(screen instanceof AbstractContainerScreen<?> container) || screen instanceof CreativeModeInventoryScreen) {
				return;
			}

			if (!module.remember.get()) {
				module.query = "";
			}

			AbstractContainerScreenAccessor at = (AbstractContainerScreenAccessor) container;
			int boxWidth = 80;
			int x = at.femboyclient$leftPos() - boxWidth - 4;
			int y = at.femboyclient$topPos();
			if (x < 2) {
				// No room to the left: above the container's left corner instead.
				x = at.femboyclient$leftPos();
				y = at.femboyclient$topPos() - 14;
			}

			EditBox box = new EditBox(Minecraft.getInstance().font, x, y, boxWidth, 12, Component.literal("Search items"));
			box.setHint(Component.literal("Search items"));
			box.setMaxLength(50);
			box.setValue(module.query);
			box.setResponder(module::setQuery);
			Screens.getButtons(screen).add(box);
			// While typing, keys go to the box, so letters such as E (inventory) do not close the screen.
			ScreenKeyboardEvents.allowKeyPress(screen).register((current, event) -> {
				if (!box.isFocused() || event.key() == GLFW.GLFW_KEY_ESCAPE) {
					return true;
				}

				box.keyPressed(event);
				return false;
			});
		});
	}
}
