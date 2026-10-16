package io.github.theamethyx.femboyclient.client.modules;

import java.util.List;

import io.github.theamethyx.femboyclient.client.gui.Theme;
import io.github.theamethyx.femboyclient.client.gui.UiContext;
import io.github.theamethyx.femboyclient.client.hud.Notifications;
import io.github.theamethyx.femboyclient.client.module.ClientHooks;
import io.github.theamethyx.femboyclient.core.module.Module;
import io.github.theamethyx.femboyclient.core.module.ModuleCategory;
import io.github.theamethyx.femboyclient.core.setting.NumberSetting;

/** Shows Femboy Client messages (key bind toggles, profile switches, alerts) as toasts instead of the action bar. */
public final class NotificationsModule extends Module implements ClientHooks.ScreenOverlay {
	private final NumberSetting duration = settings().add(new NumberSetting("duration", "Duration", "How long a toast stays.", 3, 1, 10, 0.5, "s"));
	private final NumberSetting max = settings().add(new NumberSetting("max", "Maximum shown", "Toasts shown at once.", 4, 1, 8, 1, ""));

	public NotificationsModule() {
		super("notifications", "Notifications", "Shows client messages (toggles, profile switches, alerts) as toasts at the top right.",
				ModuleCategory.HUD, false, NO_KEY);
	}

	@Override
	protected void onEnable() {
		Notifications.setToastsEnabled(true);
	}

	@Override
	protected void onDisable() {
		Notifications.setToastsEnabled(false);
	}

	@Override
	public void renderOverlay(UiContext ui, int screenWidth, int screenHeight, float partialTick) {
		List<Notifications.Toast> toasts = Notifications.active(Math.round(duration.get() * 1000));
		int shown = Math.min(toasts.size(), max.intValue());
		int y = screenHeight / 3;
		for (int i = toasts.size() - shown; i < toasts.size(); i++) {
			Notifications.Toast toast = toasts.get(i);
			String text = toast.text().isEmpty() ? toast.title() : toast.title() + "  " + toast.text();
			int width = ui.textWidth(text) + 14;
			int x = screenWidth - width - 6;
			ui.roundRect(x, y, width, 16, Theme.withAlpha(Theme.SURFACE, 0.92f));
			ui.rect(x, y + 2, 2, 12, toast.color());
			ui.text(toast.title(), x + 7, y + 4, Theme.TEXT);
			if (!toast.text().isEmpty()) {
				ui.text(toast.text(), x + 7 + ui.textWidth(toast.title()) + 6, y + 4, toast.color());
			}

			y += 19;
		}
	}
}
