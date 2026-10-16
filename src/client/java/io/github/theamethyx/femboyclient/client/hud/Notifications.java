package io.github.theamethyx.femboyclient.client.hud;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * Short messages from modules (key bind toggles, profile switches, alerts). With the Notifications module running they
 * appear as toasts drawn by that module; otherwise they go to the action bar above the hotbar as before.
 */
public final class Notifications {
	public record Toast(String title, String text, int color, long shownAt) {
	}

	private static final Deque<Toast> toasts = new ArrayDeque<>();
	private static boolean toastsEnabled;
	private static final List<Toast> history = new ArrayList<>();

	private Notifications() {
	}

	public static void setToastsEnabled(boolean enabled) {
		toastsEnabled = enabled;
		if (!enabled) {
			toasts.clear();
		}
	}

	public static void show(String title, String text, int color) {
		Toast toast = new Toast(title, text, color, System.currentTimeMillis());
		history.add(toast);
		if (history.size() > 50) {
			history.removeFirst();
		}

		if (toastsEnabled) {
			toasts.addLast(toast);
			while (toasts.size() > 8) {
				toasts.removeFirst();
			}

			return;
		}

		Minecraft client = Minecraft.getInstance();
		if (client.player != null) {
			client.player.displayClientMessage(Component.literal(text.isEmpty() ? title : title + ": " + text), true);
		}
	}

	/** Current toasts, oldest first, dropping those older than {@code durationMillis}. */
	public static List<Toast> active(long durationMillis) {
		long now = System.currentTimeMillis();
		toasts.removeIf(toast -> now - toast.shownAt() > durationMillis);
		return List.copyOf(toasts);
	}

	/** Recent messages, for tests. */
	public static List<Toast> history() {
		return List.copyOf(history);
	}
}
