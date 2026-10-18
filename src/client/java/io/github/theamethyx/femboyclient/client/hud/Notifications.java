package io.github.theamethyx.femboyclient.client.hud;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import io.github.theamethyx.femboyclient.client.gui.Icon;

/**
 * Short messages from the client and its modules (key bind toggles, profile switches, errors, alerts).
 *
 * <p>Every message goes into the history (the bell in the menu). With the Notifications module running, messages of the
 * categories it shows also appear as toasts drawn by that module; otherwise they go to the action bar above the hotbar.
 *
 * <p>Repeats are folded: the same title and text within {@link #REPEAT_WINDOW_MILLIS} updates the earlier message with a
 * count instead of adding another one, and at most {@link #MAX_PER_SECOND} new toasts appear per second (the rest still
 * reach the history).
 */
public final class Notifications {
	public static final long REPEAT_WINDOW_MILLIS = 5_000;
	public static final int MAX_PER_SECOND = 4;
	private static final int HISTORY = 60;

	/** What a message is about; the Notifications module can show or hide each kind as toasts. */
	public enum Category {
		MODULE("Module toggles", Icon.POWER),
		PROFILE("Profiles", Icon.LAYERS),
		CONFIG("Configuration", Icon.SAVE),
		ERROR("Errors", Icon.WARNING),
		PERFORMANCE("Performance", Icon.BOLT),
		ENVIRONMENT("Environment", Icon.GLOBE),
		RULE("Rules", Icon.SPARKLE),
		ALERT("Alerts", Icon.BELL),
		GENERAL("Other", Icon.INFO);

		private final String label;
		private final Icon icon;

		Category(String label, Icon icon) {
			this.label = label;
			this.icon = icon;
		}

		public String label() {
			return label;
		}

		public Icon icon() {
			return icon;
		}
	}

	/** One message. {@code count} is how often it arrived within the repeat window; {@code shownAt} is the latest time. */
	public record Toast(String title, String text, int color, long shownAt, Category category, int count) {
		public Toast(String title, String text, int color, long shownAt) {
			this(title, text, color, shownAt, Category.GENERAL, 1);
		}

		boolean sameMessage(String otherTitle, String otherText) {
			return title.equals(otherTitle) && text.equals(otherText);
		}
	}

	private static final Deque<Toast> toasts = new ArrayDeque<>();
	private static final List<Toast> history = new ArrayList<>();
	private static final long[] recentToastTimes = new long[MAX_PER_SECOND];
	private static int recentToastCursor;
	private static boolean toastsEnabled;
	private static Set<Category> toastCategories = EnumSet.allOf(Category.class);
	private static int dropped;

	private Notifications() {
	}

	public static void setToastsEnabled(boolean enabled) {
		toastsEnabled = enabled;
		if (!enabled) {
			toasts.clear();
		}
	}

	/** Categories shown as toasts or on the action bar; the others only reach the history. */
	public static void setShownCategories(Set<Category> categories) {
		toastCategories = categories.isEmpty() ? EnumSet.noneOf(Category.class) : EnumSet.copyOf(categories);
	}

	/** A general message. */
	public static void show(String title, String text, int color) {
		post(Category.GENERAL, title, text, color);
	}

	/** Posts a message; must be called on the client thread. */
	public static void post(Category category, String title, String text, int color) {
		long now = System.currentTimeMillis();
		String safeText = text == null ? "" : text;

		// A repeat of a recent message updates it instead of adding another.
		for (int i = history.size() - 1; i >= 0; i--) {
			Toast earlier = history.get(i);
			if (now - earlier.shownAt() > REPEAT_WINDOW_MILLIS) {
				break;
			}

			if (earlier.sameMessage(title, safeText)) {
				Toast repeated = new Toast(title, safeText, color, now, category, earlier.count() + 1);
				history.remove(i);
				history.add(repeated);
				replaceToast(earlier, repeated);
				return;
			}
		}

		Toast toast = new Toast(title, safeText, color, now, category, 1);
		history.add(toast);
		while (history.size() > HISTORY) {
			history.removeFirst();
		}

		if (!toastCategories.contains(category)) {
			return;
		}

		// Rate limit: the oldest of the last MAX_PER_SECOND toasts must be at least a second old.
		if (now - recentToastTimes[recentToastCursor] < 1000) {
			dropped++;
			return;
		}

		recentToastTimes[recentToastCursor] = now;
		recentToastCursor = (recentToastCursor + 1) % MAX_PER_SECOND;

		if (toastsEnabled) {
			toasts.addLast(toast);
			while (toasts.size() > 8) {
				toasts.removeFirst();
			}

			return;
		}

		Minecraft client = Minecraft.getInstance();
		if (client.player != null) {
			client.player.displayClientMessage(Component.literal(safeText.isEmpty() ? title : title + ": " + safeText), true);
		}
	}

	private static void replaceToast(Toast earlier, Toast repeated) {
		if (!toastsEnabled) {
			return;
		}

		List<Toast> copy = new ArrayList<>(toasts);
		int index = copy.indexOf(earlier);
		if (index >= 0) {
			copy.set(index, repeated);
			toasts.clear();
			toasts.addAll(copy);
		} else if (toastCategories.contains(repeated.category())) {
			toasts.addLast(repeated);
		}
	}

	/** Current toasts, oldest first, dropping those older than {@code durationMillis}. */
	public static List<Toast> active(long durationMillis) {
		long now = System.currentTimeMillis();
		toasts.removeIf(toast -> now - toast.shownAt() > durationMillis);
		return List.copyOf(toasts);
	}

	/** Recent messages, oldest first (the menu's bell). */
	public static List<Toast> history() {
		return List.copyOf(history);
	}

	public static void clearHistory() {
		history.clear();
		toasts.clear();
	}

	/** Toasts skipped by the rate limit since the client started (they are in the history). */
	public static int dropped() {
		return dropped;
	}
}
