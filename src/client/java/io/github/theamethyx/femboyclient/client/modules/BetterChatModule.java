package io.github.theamethyx.femboyclient.client.modules;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import io.github.theamethyx.femboyclient.client.FemboyClient;
import io.github.theamethyx.femboyclient.core.module.Module;
import io.github.theamethyx.femboyclient.core.module.ModuleCategory;
import io.github.theamethyx.femboyclient.core.setting.BooleanSetting;
import io.github.theamethyx.femboyclient.core.setting.NumberSetting;

/** Chat timestamps, repeated messages stacked into one line with a counter, and a longer chat history. Only your view. */
public final class BetterChatModule extends Module {
	private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

	private final BooleanSetting timestamps = settings().add(new BooleanSetting("timestamps", "Timestamps", "Prefix messages with the time.", true));
	private final BooleanSetting stack = settings().add(new BooleanSetting("stack", "Stack repeats",
			"Replace a message that repeats the previous one with a single line and a counter (x2, x3, ...).", true));
	private final NumberSetting history = settings().add(new NumberSetting("history", "History length",
			"Chat lines kept (vanilla keeps 100).", 500, 100, 2000, 100, "lines"));

	private String lastText;
	private int repeats;

	public BetterChatModule() {
		super("better_chat", "Better Chat", "Timestamps, stacked repeated messages and a longer chat history.", ModuleCategory.HUD, false, NO_KEY);
	}

	@Override
	protected void onDisable() {
		lastText = null;
		repeats = 0;
	}

	private static BetterChatModule running() {
		FemboyClient femboy = FemboyClient.get();
		BetterChatModule module = femboy == null ? null : femboy.modules().get(BetterChatModule.class);
		return module != null && module.isRunning() ? module : null;
	}

	/** Chat lines to keep: the setting while running, else vanilla's 100. */
	public static int historyLength() {
		BetterChatModule module = running();
		return module == null ? 100 : module.history.intValue();
	}

	/** Decorated message, and whether the previous line must be removed because this one repeats it. */
	public record Decorated(Component message, boolean replacePrevious) {
	}

	public static Decorated decorate(Component original) {
		BetterChatModule module = running();
		if (module == null) {
			return new Decorated(original, false);
		}

		String text = original.getString();
		boolean repeat = module.stack.get() && text.equals(module.lastText);
		module.repeats = repeat ? module.repeats + 1 : 1;
		module.lastText = text;
		MutableComponent result = Component.empty();
		if (module.timestamps.get()) {
			result.append(Component.literal("[" + LocalTime.now().format(TIME) + "] ").withStyle(ChatFormatting.DARK_GRAY));
		}

		result.append(original);
		if (module.repeats > 1) {
			result.append(Component.literal(" x" + module.repeats).withStyle(ChatFormatting.GRAY));
		}

		return new Decorated(result, repeat);
	}

	@Override
	public java.util.List<String> aliases() {
		return java.util.List.of("chat timestamps", "chat");
	}
}
