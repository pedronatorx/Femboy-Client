package io.github.theamethyx.femboyclient.client.modules;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;

import io.github.theamethyx.femboyclient.client.FemboyClient;
import io.github.theamethyx.femboyclient.client.gui.Theme;
import io.github.theamethyx.femboyclient.client.gui.UiContext;
import io.github.theamethyx.femboyclient.client.hud.HudAnchor;
import io.github.theamethyx.femboyclient.client.module.ClientHooks;
import io.github.theamethyx.femboyclient.core.module.Module;
import io.github.theamethyx.femboyclient.core.module.ModuleCategory;
import io.github.theamethyx.femboyclient.core.setting.BooleanSetting;
import io.github.theamethyx.femboyclient.core.setting.EnumSetting;

/** Shows movement keys, jump and mouse buttons (with clicks per second) as they are pressed. Draggable in the HUD editor. */
public final class KeystrokesModule extends Module implements ClientHooks.HudWidget {
	private static final int KEY = 18;
	private static final int GAP = 2;

	private final EnumSetting<HudAnchor> anchor = settings().add(new EnumSetting<>("anchor", "Position", "Screen corner.", HudAnchor.BOTTOM_LEFT));
	private final BooleanSetting mouse = settings().add(new BooleanSetting("mouse", "Mouse buttons", "Show left and right mouse buttons with CPS.", true));
	private final BooleanSetting space = settings().add(new BooleanSetting("space", "Jump", "Show the jump key.", true));

	public KeystrokesModule() {
		super("keystrokes", "Keystrokes", "Shows WASD, jump and mouse buttons with clicks per second as you press them.", ModuleCategory.HUD, false, NO_KEY);
	}

	@Override
	public HudAnchor hudAnchor() {
		return anchor.get();
	}

	@Override
	public int hudWidth(UiContext ui) {
		return KEY * 3 + GAP * 2;
	}

	@Override
	public int hudHeight(UiContext ui) {
		return KEY * 2 + GAP + (mouse.get() ? KEY + GAP : 0) + (space.get() ? 10 + GAP : 0);
	}

	@Override
	public void drawHud(UiContext ui, int x, int y) {
		Options o = Minecraft.getInstance().options;
		key(ui, x + KEY + GAP, y, KEY, KEY, "W", o.keyUp);
		key(ui, x, y + KEY + GAP, KEY, KEY, "A", o.keyLeft);
		key(ui, x + KEY + GAP, y + KEY + GAP, KEY, KEY, "S", o.keyDown);
		key(ui, x + 2 * (KEY + GAP), y + KEY + GAP, KEY, KEY, "D", o.keyRight);
		int row = y + 2 * (KEY + GAP);
		if (mouse.get()) {
			int half = (hudWidth(ui) - GAP) / 2;
			var combat = FemboyClient.get().combat();
			key(ui, x, row, half, KEY, "LMB " + combat.leftCps(), o.keyAttack);
			key(ui, x + half + GAP, row, half, KEY, "RMB " + combat.rightCps(), o.keyUse);
			row += KEY + GAP;
		}

		if (space.get()) {
			key(ui, x, row, hudWidth(ui), 10, "—", o.keyJump);
		}
	}

	private static void key(UiContext ui, int x, int y, int w, int h, String label, KeyMapping mapping) {
		boolean down = mapping.isDown();
		ui.roundRect(x, y, w, h, down ? Theme.withAlpha(Theme.ACCENT, 0.85f) : Theme.withAlpha(Theme.SURFACE, 0.8f));
		ui.textCentered(label, x + w / 2, y + (h - 8) / 2, down ? 0xFFFFFFFF : Theme.TEXT_SECONDARY);
	}
}
