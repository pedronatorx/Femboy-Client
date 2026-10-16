package io.github.theamethyx.femboyclient.client.modules;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

import io.github.theamethyx.femboyclient.client.world.WorldIdentity;
import io.github.theamethyx.femboyclient.core.module.Module;
import io.github.theamethyx.femboyclient.core.module.ModuleCategory;
import io.github.theamethyx.femboyclient.core.setting.BooleanSetting;
import io.github.theamethyx.femboyclient.core.setting.NumberSetting;
import io.github.theamethyx.femboyclient.core.setting.StringSetting;

/**
 * Warns when another player comes within range of your client, i.e. when the server starts sending you
 * that player (its entity tracking range, roughly your render distance), and optionally when they leave.
 *
 * <p>Players already nearby when the module is turned on are taken as the starting point and not
 * announced. After a world or dimension change, everyone who appears is announced. Several arrivals in
 * the same tick are combined into one message. All messages are local: nothing is sent to the server.
 */
public final class PlayerAlertModule extends Module {
	private static final SystemToast.SystemToastId TOAST = new SystemToast.SystemToastId(4_000L);
	private static final int HISTORY = 20;

	private final BooleanSetting chat = settings().add(new BooleanSetting("chat", "Chat message",
			"Write the warning into your chat (only you see it).", true));
	private final BooleanSetting sound = settings().add(new BooleanSetting("sound", "Sound", "Play a short ping.", true));
	private final BooleanSetting toast = settings().add(new BooleanSetting("toast", "Pop-up",
			"Show a pop-up in the top-right corner.", false));
	private final BooleanSetting onLeave = settings().add(new BooleanSetting("on_leave", "Warn when they leave",
			"Also tell you when a player goes out of range or logs off.", false));
	private final BooleanSetting ignoreNpcs = settings().add(new BooleanSetting("ignore_npcs", "Ignore NPCs",
			"Skip player-shaped entities that are not in the server's player list. Servers use these for NPCs.", true));
	private final NumberSetting cooldown = settings().add(new NumberSetting("cooldown", "Repeat delay",
			"Don't warn about the same player again within this time, so someone walking along the edge of your range "
					+ "doesn't flood the chat.", 30, 0, 600, 5, "s"));


	{
		settings().section("Auto leave");
	}

	private final BooleanSetting autoLeave = settings().add(new BooleanSetting("auto_leave", "Leave the server",
			"Disconnect from a multiplayer server as soon as another player is detected (like pressing Disconnect). Auto Reconnect does "
					+ "not bring you back after this.", false));
	private final NumberSetting leaveDistance = settings().add(new NumberSetting("leave_distance", "Leave within",
			"Only leave when the player is this close; 0 leaves for anyone in range.", 0, 0, 256, 4, "blocks")).visibleWhen(autoLeave::get);
	private final StringSetting leaveIgnore = settings().add(new StringSetting("leave_ignore", "Don't leave for",
			"Names of friends to stay for, separated by commas.", "", 256, "Friend1, Friend2")).visibleWhen(autoLeave::get);
	private final BooleanSetting leaveOff = settings().add(new BooleanSetting("leave_off", "Turn auto leave off after leaving",
			"So that joining again does not make you leave right away while the player is still there.", true)).visibleWhen(autoLeave::get);

	private final Map<UUID, String> inRange = new LinkedHashMap<>();
	private String leftBecause;
	private boolean leaving;
	private final Map<UUID, Long> lastWarned = new HashMap<>();
	private final Deque<String> recent = new ArrayDeque<>();
	private String world;
	private boolean baseline;

	@Override
	public Tier tier() {
		return Tier.ASSIST;
	}

	public PlayerAlertModule() {
		super("player_alert", "Player Alert", "Warns you when another player comes within your render distance, and can leave the server for you.",
				ModuleCategory.TRACKERS, false, NO_KEY);
	}

	@Override
	protected void onEnable() {
		inRange.clear();
		lastWarned.clear();
		world = null;
		baseline = true;
	}

	@Override
	protected void onDisable() {
		inRange.clear();
	}

	@Override
	protected void onTick() {
		Minecraft client = Minecraft.getInstance();
		ClientPacketListener connection = client.getConnection();
		if (client.level == null || client.player == null || connection == null) {
			return;
		}

		String here = WorldIdentity.current() + "|" + WorldIdentity.dimension();
		if (!here.equals(world)) {
			// Nobody from the previous world is near any more; whoever shows up here is new.
			inRange.clear();
			world = here;
		}

		Map<UUID, String> now = new LinkedHashMap<>();
		List<String> arrived = new ArrayList<>();
		long time = System.currentTimeMillis();
		for (AbstractClientPlayer other : client.level.players()) {
			if (other == client.player || ignoreNpcs.get() && connection.getPlayerInfo(other.getUUID()) == null) {
				continue;
			}

			String name = other.getGameProfile().name();
			now.put(other.getUUID(), name);
			if (!inRange.containsKey(other.getUUID()) && !baseline && due(other.getUUID(), time)) {
				arrived.add(String.format(Locale.ROOT, "%s (%s)", name, PlayerFinderModule.distanceAndArrow(other.position())));
			}
		}

		List<String> left = new ArrayList<>();
		if (onLeave.get() && !baseline) {
			for (Map.Entry<UUID, String> entry : inRange.entrySet()) {
				if (!now.containsKey(entry.getKey())) {
					boolean online = connection.getPlayerInfo(entry.getKey()) != null;
					left.add(entry.getValue() + (online ? "" : " (logged off)"));
				}
			}
		}

		inRange.clear();
		inRange.putAll(now);
		baseline = false;
		checkAutoLeave(client);

		if (!arrived.isEmpty()) {
			warn(arrived.size() == 1 ? arrived.getFirst() + " is in range" : arrived.size() + " players in range: " + String.join(", ", arrived),
					ChatFormatting.GOLD, true);
		}

		if (!left.isEmpty()) {
			warn((left.size() == 1 ? left.getFirst() : String.join(", ", left)) + " left your range", ChatFormatting.GRAY, false);
		}
	}

	private boolean due(UUID id, long time) {
		Long previous = lastWarned.get(id);
		if (previous != null && time - previous < cooldown.get() * 1000) {
			return false;
		}

		lastWarned.put(id, time);
		return true;
	}

	private void warn(String text, ChatFormatting color, boolean arrival) {
		Minecraft client = Minecraft.getInstance();
		recent.addFirst(text);
		while (recent.size() > HISTORY) {
			recent.removeLast();
		}

		if (chat.get()) {
			client.gui.getChat().addMessage(Component.literal("[Femboy] ").withStyle(ChatFormatting.LIGHT_PURPLE)
					.append(Component.literal(text).withStyle(color)));
		}

		if (sound.get()) {
			client.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_PLING, arrival ? 1.6F : 0.8F));
		}

		if (toast.get()) {
			SystemToast.addOrUpdate(client.getToastManager(), TOAST, Component.literal(arrival ? "Player nearby" : "Player left"), Component.literal(text));
		}
	}

	/** Leaves a multiplayer server when a player that is not ignored is close enough. */
	private void checkAutoLeave(Minecraft client) {
		if (!autoLeave.get() || leaving || client.isLocalServer() || client.getCurrentServer() == null) {
			return;
		}

		java.util.Set<String> ignored = new java.util.HashSet<>();
		for (String name : leaveIgnore.get().split(",")) {
			if (!name.isBlank()) {
				ignored.add(name.trim().toLowerCase(Locale.ROOT));
			}
		}

		for (AbstractClientPlayer other : client.level.players()) {
			String name = other.getGameProfile().name();
			if (!inRange.containsKey(other.getUUID()) || ignored.contains(name.toLowerCase(Locale.ROOT))
					|| leaveDistance.get() > 0 && other.distanceTo(client.player) > leaveDistance.get()) {
				continue;
			}

			leaving = true;
			leftBecause = String.format(Locale.ROOT, "%s was %.0f blocks away", name, other.distanceTo(client.player));
			if (leaveOff.get()) {
				autoLeave.set(false);
			}

			// Disconnect after this tick, like the pause menu's Disconnect button.
			client.execute(() -> {
				client.disconnectFromWorld(Component.literal("Player Alert: " + leftBecause));
				SystemToast.addOrUpdate(client.getToastManager(), TOAST, Component.literal("Left the server"), Component.literal(leftBecause));
				leaving = false;
			});
			return;
		}
	}

	/** Why Player Alert last left a server, or {@code null}. */
	public String leftBecause() {
		return leftBecause;
	}

	/** Players currently in range, by name. */
	public List<String> playersInRange() {
		return List.copyOf(inRange.values());
	}

	/** The latest warnings, newest first. */
	public List<String> recentWarnings() {
		return List.copyOf(recent);
	}
}
