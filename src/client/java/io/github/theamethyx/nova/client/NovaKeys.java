package io.github.theamethyx.nova.client;

import org.lwjgl.glfw.GLFW;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;

import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;

/**
 * Vanilla key mappings owned by NOVA. They appear in Options > Controls so players can rebind them
 * with the familiar UI. Module toggle keys are separate and edited in the ClickGUI.
 */
public final class NovaKeys {
	public static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(NovaClient.MOD_ID, "main"));

	public static KeyMapping openMenu;

	private NovaKeys() {
	}

	static void register() {
		openMenu = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.nova.open_menu", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_SHIFT, CATEGORY));
	}
}
