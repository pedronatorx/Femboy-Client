package io.github.theamethyx.femboyclient.gametest;

import java.util.List;

import org.lwjgl.glfw.GLFW;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.KeyMapping;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

import io.github.theamethyx.femboyclient.client.ClientKeys;
import io.github.theamethyx.femboyclient.client.FemboyClient;
import io.github.theamethyx.femboyclient.client.gui.widget.Button;
import io.github.theamethyx.femboyclient.client.modules.CoordinatesHudModule;
import io.github.theamethyx.femboyclient.client.modules.EntityEspModule;
import io.github.theamethyx.femboyclient.client.modules.FreeCameraModule;
import io.github.theamethyx.femboyclient.client.modules.FullbrightModule;
import io.github.theamethyx.femboyclient.core.config.ProfileRule;
import io.github.theamethyx.femboyclient.core.module.Module;

/** Phase 2 foundation: defaults, emergency off, hold-mode keys, automatic profile switching and the Profiles page. */
final class FoundationTest {
	private FoundationTest() {
	}

	static FemboyClient femboy() {
		return FemboyClient.get();
	}

	/** Must run before any test turns a module on. */
	static void firstLaunch(ClientGameTestContext context, TestReport report) {
		List<String> onByDefault = context.computeOnClient(client -> femboy().modules().all().stream()
				.filter(Module::isEnabledByDefault).map(Module::id).toList());
		report.check("No module is on by default", onByDefault.isEmpty(), onByDefault.toString());
		List<String> enabled = context.computeOnClient(client -> femboy().modules().all().stream().filter(Module::isEnabled).map(Module::id).toList());
		report.check("No module is on at the first start", enabled.isEmpty(), enabled.toString());
	}

	static void emergencyOff(ClientGameTestContext context, TestReport report) {
		context.runOnClient(client -> {
			ClientKeys.disableAll.setKey(InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_F7));
			KeyMapping.resetMapping();
			for (Class<? extends Module> type : List.of(FreeCameraModule.class, FullbrightModule.class, EntityEspModule.class, CoordinatesHudModule.class)) {
				femboy().modules().setEnabled(femboy().modules().get(type), true);
			}

			femboy().options().autoProfile.set(true);
		});
		context.waitTicks(3);
		boolean cameraDetached = context.computeOnClient(client -> client.getCameraEntity() != client.player);

		context.getInput().pressKey(GLFW.GLFW_KEY_F7);
		context.waitTicks(2);
		String result = context.computeOnClient(client -> {
			List<String> stillOn = femboy().modules().all().stream().filter(m -> m.isEnabled() || m.isRunning()).map(Module::id).toList();
			return "on " + stillOn + ", camera on player " + (client.getCameraEntity() == client.player) + ", auto profile "
					+ femboy().options().autoProfile.get();
		});
		report.check("Emergency key turns every module off, returns the camera and pauses automatic profiles", cameraDetached
				&& result.equals("on [], camera on player true, auto profile false"), "free camera was detached: " + cameraDetached + "; " + result);

		context.runOnClient(client -> {
			ClientKeys.disableAll.setKey(InputConstants.UNKNOWN);
			KeyMapping.resetMapping();
		});
	}

	static void holdKeys(ClientGameTestContext context, TestReport report) {
		context.runOnClient(client -> {
			Module fullbright = femboy().modules().get(FullbrightModule.class);
			femboy().modules().setKey(fullbright, GLFW.GLFW_KEY_G);
			femboy().modules().setHoldKey(fullbright, true);
		});

		context.getInput().holdKey(GLFW.GLFW_KEY_G);
		context.waitTicks(5);
		boolean onWhileHeld = context.computeOnClient(client -> femboy().modules().get(FullbrightModule.class).isRunning());
		context.getInput().releaseKey(GLFW.GLFW_KEY_G);
		context.waitTicks(2);
		boolean offAfterRelease = context.computeOnClient(client -> !femboy().modules().get(FullbrightModule.class).isEnabled());
		report.check("Hold-mode key: module runs while the key is held and stops on release", onWhileHeld && offAfterRelease,
				"on while held " + onWhileHeld + ", off after release " + offAfterRelease);

		context.runOnClient(client -> femboy().modules().resetToDefaults(femboy().modules().get(FullbrightModule.class)));
	}

	static void automaticProfiles(ClientGameTestContext context, TestSingleplayerContext singleplayer, UiDriver ui, TestReport report) {
		String setup = context.computeOnClient(client -> {
			var modules = femboy().modules();
			var config = femboy().config();
			Module fullbright = modules.get(FullbrightModule.class);
			Module coords = modules.get(CoordinatesHudModule.class);
			modules.setEnabled(fullbright, true);
			String a = config.createProfile("auto_sword");
			modules.setEnabled(fullbright, false);
			modules.setEnabled(coords, true);
			String b = config.createProfile("auto_mace");
			modules.setEnabled(coords, false);
			config.setProfileRules(List.of(new ProfileRule(ProfileRule.Trigger.SWORD, ProfileRule.Dimension.ANY, "auto_sword"),
					new ProfileRule(ProfileRule.Trigger.MACE, ProfileRule.Dimension.ANY, "auto_mace")));
			return a == null && b == null ? "ok" : a + " / " + b;
		});
		report.check("Test profiles and switching rules created", setup.equals("ok"), setup);

		singleplayer.getServer().runCommand("item replace entity @a hotbar.0 with minecraft:diamond_sword");
		singleplayer.getServer().runCommand("item replace entity @a hotbar.1 with minecraft:mace");
		singleplayer.getServer().runCommand("item replace entity @a hotbar.2 with minecraft:dirt");
		context.runOnClient(client -> client.player.getInventory().setSelectedSlot(2));
		context.waitTicks(3);
		String before = state(context);
		context.runOnClient(client -> femboy().options().autoProfile.set(true));
		context.waitTicks(8);
		String unmatched = state(context);
		report.check("Holding an item no rule mentions keeps the current profile", unmatched.equals(before) && unmatched.startsWith("default"), unmatched);

		context.runOnClient(client -> client.player.getInventory().setSelectedSlot(0));
		context.waitTicks(8);
		String sword = state(context);
		context.runOnClient(client -> client.player.getInventory().setSelectedSlot(1));
		context.waitTicks(8);
		String mace = state(context);
		report.check("Holding a sword switches to its profile and turns its modules on", sword.equals("auto_sword fullbright=true coords=false"), sword);
		report.check("Switching to the mace changes profile and turns modules outside it off", mace.equals("auto_mace fullbright=false coords=true"), mace);

		// A profile chosen by hand stays until the held item matches a different rule.
		context.runOnClient(client -> femboy().config().switchProfile("default"));
		context.waitTicks(5);
		String manual = state(context);
		context.runOnClient(client -> client.player.getInventory().setSelectedSlot(0));
		context.waitTicks(8);
		String back = state(context);
		report.check("A hand-picked profile is kept until the held item changes", manual.startsWith("default ") && back.startsWith("auto_sword "),
				manual + " -> " + back);
		int switches = context.computeOnClient(client -> femboy().profileSwitcher().switches());
		report.note("Automatic profile switches during the test: " + switches);

		ClientGameTest.openMenu(context);
		ClientGameTest.showPage(context, "Profiles");
		context.takeScreenshot("profiles-page");
		Button export = ui.find(Button.class, button -> button.label().equals("Export"));
		if (export != null) {
			ui.click(export);
			context.waitTicks(2);
		}

		String clipboard = context.computeOnClient(client -> client.keyboardHandler.getClipboard());
		report.check("Profiles page: Export copies the profile to the clipboard", export != null && clipboard.contains("\"femboyClientProfile\""),
				export == null ? "no Export button" : clipboard.substring(0, Math.min(80, clipboard.length())));
		ClientGameTest.closeMenu(context, ui);

		context.runOnClient(client -> {
			femboy().options().autoProfile.set(false);
			femboy().config().setProfileRules(List.of());
			femboy().config().switchProfile("default");
			femboy().config().deleteProfile("auto_sword");
			femboy().config().deleteProfile("auto_mace");
			femboy().modules().disableAll();
		});
		singleplayer.getServer().runCommand("clear @a");
		context.waitTicks(2);
	}

	private static String state(ClientGameTestContext context) {
		return context.computeOnClient(client -> femboy().config().activeProfile() + " fullbright="
				+ femboy().modules().get(FullbrightModule.class).isEnabled() + " coords=" + femboy().modules().get(CoordinatesHudModule.class).isEnabled());
	}
}
