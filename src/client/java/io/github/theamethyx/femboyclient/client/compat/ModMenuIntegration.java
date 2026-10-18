package io.github.theamethyx.femboyclient.client.compat;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

import io.github.theamethyx.femboyclient.client.gui.ClientScreen;

/**
 * Optional Mod Menu integration: the "Configure" button of Femboy Client in Mod Menu's list opens the client menu.
 * Mod Menu is not bundled or required; this class is only loaded by Mod Menu when it is installed (its "modmenu"
 * entrypoint), so without Mod Menu nothing here runs.
 */
public final class ModMenuIntegration implements ModMenuApi {
	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		return ClientScreen::withParent;
	}
}
