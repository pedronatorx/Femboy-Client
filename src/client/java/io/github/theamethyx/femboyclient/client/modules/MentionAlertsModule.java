package io.github.theamethyx.femboyclient.client.modules;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;

import io.github.theamethyx.femboyclient.client.FemboyClient;
import io.github.theamethyx.femboyclient.client.gui.Theme;
import io.github.theamethyx.femboyclient.client.hud.Notifications;
import io.github.theamethyx.femboyclient.core.module.Module;
import io.github.theamethyx.femboyclient.core.module.ModuleCategory;
import io.github.theamethyx.femboyclient.core.setting.BooleanSetting;
import io.github.theamethyx.femboyclient.core.setting.StringSetting;

/**
 * Mention alerts: a notification (and optionally a sound) when a chat message contains your name or one of your
 * keywords as a whole word. Messages are only read as they arrive; nothing is stored, logged or sent anywhere.
 */
public final class MentionAlertsModule extends Module {
	private final BooleanSetting name = settings().add(new BooleanSetting("name", "My name", "Alert when your player name is mentioned.", true));
	private final StringSetting keywords = settings().add(new StringSetting("keywords", "Keywords",
			"More words to alert on, separated by commas (whole words, any capitalization).", "", 200, "e.g. base, help"));
	private final BooleanSetting system = settings().add(new BooleanSetting("system", "Server messages too",
			"Also check messages from the server itself, not only player chat.", false));
	private final BooleanSetting sound = settings().add(new BooleanSetting("sound", "Sound", "Play a short sound with the alert.", true));

	private List<Pattern> patterns = List.of();
	private String patternsFor;
	private int alerts;

	public MentionAlertsModule() {
		super("mention_alerts", "Mention Alerts", "Notifies you (and plays a sound) when chat mentions your name or one of your keywords.",
				ModuleCategory.HUD, false, NO_KEY);
	}

	@Override
	public Tier tier() {
		return Tier.ASSIST;
	}

	@Override
	public List<String> aliases() {
		return List.of("chat notifications", "highlight", "ping me");
	}

	/** Registers the chat listeners once; they do nothing while the module is not running. */
	public static void registerEvents() {
		ClientReceiveMessageEvents.CHAT.register((message, signed, sender, params, time) -> handle(message, false));
		ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
			if (!overlay) {
				handle(message, true);
			}
		});
	}

	private static void handle(Component message, boolean fromServer) {
		FemboyClient femboy = FemboyClient.get();
		MentionAlertsModule module = femboy == null ? null : femboy.modules().get(MentionAlertsModule.class);
		if (module != null && module.isRunning() && (!fromServer || module.system.get())) {
			femboy.modules().runGuarded(module, io.github.theamethyx.femboyclient.core.module.ModuleManager.Phase.TICK, () -> module.check(message.getString()));
		}
	}

	/** Checks one message; returns whether it alerted (public for tests). */
	public boolean check(String text) {
		String own = Minecraft.getInstance().player == null ? "" : Minecraft.getInstance().player.getGameProfile().name();
		String key = own + "|" + name.get() + "|" + keywords.get();
		if (!key.equals(patternsFor)) {
			patternsFor = key;
			List<Pattern> list = new ArrayList<>();
			if (name.get() && !own.isBlank()) {
				list.add(word(own));
			}

			for (String keyword : keywords.get().split(",")) {
				if (!keyword.isBlank()) {
					list.add(word(keyword.strip()));
				}
			}

			patterns = list;
		}

		// Your own messages ("<Name> ...") do not alert.
		if (!own.isBlank() && text.startsWith("<" + own + ">")) {
			return false;
		}

		for (Pattern pattern : patterns) {
			if (pattern.matcher(text).find()) {
				alerts++;
				String excerpt = text.length() > 80 ? text.substring(0, 77) + "..." : text;
				Notifications.post(Notifications.Category.ALERT, "Mention", excerpt, Theme.ACCENT_2);
				if (sound.get()) {
					Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.6f));
				}

				return true;
			}
		}

		return false;
	}

	private static Pattern word(String word) {
		return Pattern.compile("(?<![\\p{L}\\p{N}_])" + Pattern.quote(word.toLowerCase(Locale.ROOT)) + "(?![\\p{L}\\p{N}_])",
				Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
	}

	@Override
	public String workload() {
		return alerts + " alert(s) this session";
	}
}
