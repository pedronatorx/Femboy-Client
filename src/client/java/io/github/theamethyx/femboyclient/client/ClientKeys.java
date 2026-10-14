package io.github.theamethyx.femboyclient.client;

import org.lwjgl.glfw.GLFW;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;

import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;

/**
 * Vanilla key mappings owned by Femboy Client. They appear in Options > Controls so players can rebind them
 * with the familiar UI. Module toggle keys are separate and edited in the ClickGUI.
 */
public final class ClientKeys {
	public static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(FemboyClient.MOD_ID, "main"));

	public static KeyMapping openMenu;

	private ClientKeys() {
	}

	static void register() {
		openMenu = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.femboyclient.open_menu", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_SHIFT, CATEGORY));
	}
}
