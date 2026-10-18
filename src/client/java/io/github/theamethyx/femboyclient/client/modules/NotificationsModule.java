package io.github.theamethyx.femboyclient.client.modules;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import io.github.theamethyx.femboyclient.client.FemboyClient;
import io.github.theamethyx.femboyclient.client.Performance;
import io.github.theamethyx.femboyclient.client.gui.Theme;
import io.github.theamethyx.femboyclient.client.gui.UiContext;
import io.github.theamethyx.femboyclient.client.hud.Notifications;
import io.github.theamethyx.femboyclient.client.module.ClientHooks;
import io.github.theamethyx.femboyclient.core.module.Module;
import io.github.theamethyx.femboyclient.core.module.ModuleCategory;
import io.github.theamethyx.femboyclient.core.setting.BooleanSetting;
import io.github.theamethyx.femboyclient.core.setting.EnumSetting;
import io.github.theamethyx.femboyclient.core.setting.NumberSetting;

/**
 * Shows Femboy Client messages (key bind toggles, profile switches, errors, alerts) as toasts instead of the action bar.
 * Repeats of the same message within a few seconds are folded into one toast with a count; every message also stays in
 * the menu's notification list (the bell), whichever categories are shown here.
 */
public final class NotificationsModule extends Module implements ClientHooks.ScreenOverlay {
	public enum Position implements EnumSetting.Labeled {
		TOP_RIGHT("Top right"), TOP_LEFT("Top left"), TOP_CENTER("Top center"), BOTTOM_RIGHT("Bottom right");

		private final String label;

		Position(String label) {
			this.label = label;
		}

		@Override
		public String label() {
			return label;
		}
	}

	public enum Style implements EnumSetting.Labeled {
		ACCENT_BAR("Accent bar"), FILLED("Tinted"), MINIMAL("Text only");

		private final String label;

		Style(String label) {
			this.label = label;
		}

		@Override
		public String label() {
			return label;
		}
	}

	private final NumberSetting duration = settings().add(new NumberSetting("duration", "Duration", "How long a toast stays.", 3, 1, 10, 0.5, "s"));
	private final NumberSetting max = settings().add(new NumberSetting("max", "Maximum shown", "Toasts shown at once.", 4, 1, 8, 1, ""));
	private final EnumSetting<Position> position = settings().add(new EnumSetting<>("position", "Position", "Where toasts stack.", Position.TOP_RIGHT));
	private final EnumSetting<Style> style = settings().add(new EnumSetting<>("style", "Style",
			"Accent bar: a colored stripe on a dark card. Tinted: the card takes the message color. Text only: no card.", Style.ACCENT_BAR));
	private final BooleanSetting icons = settings().add(new BooleanSetting("icons", "Icons", "Show the category icon on each toast.", true));

	{
		settings().section("Show");
	}

	private final BooleanSetting showModules = category("show_modules", Notifications.Category.MODULE, "Turning modules on or off with keys.");
	private final BooleanSetting showProfiles = category("show_profiles", Notifications.Category.PROFILE, "Profile switches.");
	private final BooleanSetting showConfig = category("show_config", Notifications.Category.CONFIG, "Saving, restoring and importing settings.");
	private final BooleanSetting showErrors = category("show_errors", Notifications.Category.ERROR, "A module paused or turned off after an error.");
	private final BooleanSetting showPerformance = category("show_performance", Notifications.Category.PERFORMANCE,
			"Performance warnings (for example a module taking a large share of the frame).");
	private final BooleanSetting showEnvironment = category("show_environment", Notifications.Category.ENVIRONMENT,
			"Modules that cannot run where you are, and why.");
	private final BooleanSetting showRules = category("show_rules", Notifications.Category.RULE, "Suggestions and actions of your rules.");
	private final BooleanSetting showAlerts = category("show_alerts", Notifications.Category.ALERT, "Alerts from modules such as Player Alert.");
	private final BooleanSetting showOther = category("show_other", Notifications.Category.GENERAL, "Everything else.");

	public NotificationsModule() {
		super("notifications", "Notifications", "Shows client messages (toggles, profile switches, errors, alerts) as toasts. Repeats are folded "
				+ "into one toast with a count; every message stays in the menu's notification list.", ModuleCategory.HUD, false, NO_KEY);
	}

	private BooleanSetting category(String id, Notifications.Category category, String description) {
		BooleanSetting setting = settings().add(new BooleanSetting(id, category.label(), description, true));
		setting.onChange(value -> applyCategories());
		return setting;
	}

	private void applyCategories() {
		if (!isRunning()) {
			return;
		}

		Set<Notifications.Category> shown = EnumSet.noneOf(Notifications.Category.class);
		if (showModules.get()) shown.add(Notifications.Category.MODULE);
		if (showProfiles.get()) shown.add(Notifications.Category.PROFILE);
		if (showConfig.get()) shown.add(Notifications.Category.CONFIG);
		if (showErrors.get()) shown.add(Notifications.Category.ERROR);
		if (showPerformance.get()) shown.add(Notifications.Category.PERFORMANCE);
		if (showEnvironment.get()) shown.add(Notifications.Category.ENVIRONMENT);
		if (showRules.get()) shown.add(Notifications.Category.RULE);
		if (showAlerts.get()) shown.add(Notifications.Category.ALERT);
		if (showOther.get()) shown.add(Notifications.Category.GENERAL);
		Notifications.setShownCategories(shown);
	}

	@Override
	public List<String> aliases() {
		return List.of("toasts", "notification center", "alerts");
	}

	@Override
	protected void onEnable() {
		Notifications.setToastsEnabled(true);
		applyCategories();
	}

	@Override
	protected void onDisable() {
		Notifications.setToastsEnabled(false);
		Notifications.setShownCategories(EnumSet.allOf(Notifications.Category.class));
	}

	@Override
	public void renderOverlay(UiContext ui, int screenWidth, int screenHeight, float partialTick) {
		long durationMillis = Math.round(duration.get() * 1000);
		List<Notifications.Toast> toasts = Notifications.active(durationMillis);
		int shown = Math.min(toasts.size(), max.intValue());
		if (shown == 0) {
			return;
		}

		var options = FemboyClient.get().options();
		boolean animate = Performance.animations(options.animations.get());
		boolean slide = animate && !options.reduceMotion.get();
		Position where = position.get();
		long now = System.currentTimeMillis();
		int y = switch (where) {
			case BOTTOM_RIGHT -> screenHeight - 40;
			case TOP_CENTER -> 6;
			default -> screenHeight / 3;
		};

		for (int n = 0; n < shown; n++) {
			Notifications.Toast toast = toasts.get(toasts.size() - 1 - n);
			long age = now - toast.shownAt();
			float alpha = animate ? Math.clamp(Math.min(age / 150f, (durationMillis - age) / 300f), 0, 1) : 1;
			if (alpha <= 0.02f) {
				continue;
			}

			String count = toast.count() > 1 ? "  ×" + toast.count() : "";
			int iconSpace = icons.get() ? 12 : 0;
			int width = ui.textWidth(toast.title()) + (toast.text().isEmpty() ? 0 : 6 + ui.textWidth(toast.text())) + ui.textWidth(count) + 14 + iconSpace;
			width = Math.min(width, screenWidth - 12);
			int offset = slide ? Math.round((1 - Math.min(1, age / 180f)) * 16) : 0;
			int x = switch (where) {
				case TOP_LEFT -> 6 - offset;
				case TOP_CENTER -> (screenWidth - width) / 2;
				default -> screenWidth - width - 6 + offset;
			};

			drawToast(ui, toast, x, y, width, alpha, count, iconSpace);
			y += where == Position.BOTTOM_RIGHT ? -19 : 19;
		}
	}

	private void drawToast(UiContext ui, Notifications.Toast toast, int x, int y, int width, float alpha, String count, int iconSpace) {
		switch (style.get()) {
			case ACCENT_BAR -> {
				ui.roundRect(x, y, width, 16, Theme.withAlpha(Theme.SURFACE, 0.92f * alpha));
				ui.rect(x, y + 2, 2, 12, Theme.withAlpha(toast.color(), alpha));
			}
			case FILLED -> ui.roundRect(x, y, width, 16, Theme.withAlpha(Theme.lerp(Theme.SURFACE, toast.color(), 0.35f), 0.92f * alpha));
			case MINIMAL -> {
			}
		}

		int textX = x + 7;
		if (iconSpace > 0) {
			ui.icon(toast.category().icon(), textX, y + 4, 8, Theme.withAlpha(toast.color(), alpha));
			textX += iconSpace;
		}

		int maxText = x + width - textX - 4;
		ui.textClipped(toast.title(), textX, y + 4, maxText, Theme.withAlpha(Theme.TEXT, alpha));
		int after = textX + ui.textWidth(toast.title()) + 6;
		if (!toast.text().isEmpty() && after < x + width - 4) {
			ui.textClipped(toast.text(), after, y + 4, x + width - after - 4 - ui.textWidth(count), Theme.withAlpha(style.get() == Style.FILLED ? Theme.TEXT : toast.color(), alpha));
		}

		if (!count.isEmpty()) {
			ui.textRight(count, x + width - 4, y + 4, Theme.withAlpha(Theme.TEXT_MUTED, alpha));
		}
	}
}
