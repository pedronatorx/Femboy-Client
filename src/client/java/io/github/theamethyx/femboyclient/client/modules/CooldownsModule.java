package io.github.theamethyx.femboyclient.client.modules;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemCooldowns;
import net.minecraft.world.item.ItemStack;

import io.github.theamethyx.femboyclient.client.gui.Theme;
import io.github.theamethyx.femboyclient.client.hud.HudAnchor;
import io.github.theamethyx.femboyclient.client.hud.HudLine;
import io.github.theamethyx.femboyclient.client.module.ClientHooks;
import io.github.theamethyx.femboyclient.core.module.Module;
import io.github.theamethyx.femboyclient.core.module.ModuleCategory;
import io.github.theamethyx.femboyclient.core.setting.BooleanSetting;
import io.github.theamethyx.femboyclient.core.setting.EnumSetting;

/**
 * Item cooldowns: ender pearls, chorus fruit, wind charges, a disabled shield, goat horns and anything else the game
 * puts on cooldown, with the seconds left. Only items you carry are listed (the game tracks cooldowns per item kind).
 */
public final class CooldownsModule extends Module implements ClientHooks.HudBlock {
	/** Length of the latest cooldown per cooldown group, in ticks; filled by {@code ItemCooldownsMixin}. */
	private static final Map<Identifier, Integer> DURATIONS = new ConcurrentHashMap<>();

	private final EnumSetting<HudAnchor> anchor = settings().add(new EnumSetting<>("anchor", "Position",
			"Screen corner when not placed with the HUD editor.", HudAnchor.BOTTOM_LEFT));
	private final BooleanSetting wholeInventory = settings().add(new BooleanSetting("whole_inventory", "Whole inventory",
			"Also list items outside the hotbar and offhand.", true));
	private final BooleanSetting seconds = settings().add(new BooleanSetting("seconds", "Seconds left",
			"Show seconds left instead of a percentage.", true));

	public CooldownsModule() {
		super("cooldowns", "Cooldowns", "Ender pearl, chorus fruit, wind charge, shield and other item cooldowns with the seconds left.",
				ModuleCategory.COMBAT, false, NO_KEY);
	}

	@Override
	public Tier tier() {
		return Tier.VISUAL;
	}

	@Override
	public List<String> aliases() {
		return List.of("cooldown hud", "pearl cooldown", "item cooldown");
	}

	@Override
	public HudAnchor hudAnchor() {
		return anchor.get();
	}

	public static void started(Identifier group, int ticks) {
		DURATIONS.put(group, ticks);
	}

	/** Seconds left of a cooldown that is {@code fraction} through (0..1 left) and lasted {@code ticks}, or -1 if unknown. */
	static double secondsLeft(float fraction, Integer ticks) {
		return ticks == null || ticks <= 0 ? -1 : fraction * ticks / 20.0;
	}

	@Override
	public void buildHud(List<HudLine> lines) {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null) {
			return;
		}

		ItemCooldowns cooldowns = player.getCooldowns();
		Map<Identifier, ItemStack> carried = new LinkedHashMap<>();
		int slots = wholeInventory.get() ? player.getInventory().getContainerSize() : 9;
		addIfCooling(cooldowns, player.getOffhandItem(), carried);
		for (int slot = 0; slot < slots; slot++) {
			addIfCooling(cooldowns, player.getInventory().getItem(slot), carried);
		}

		float partial = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
		for (Map.Entry<Identifier, ItemStack> entry : carried.entrySet()) {
			float left = cooldowns.getCooldownPercent(entry.getValue(), partial);
			double secondsLeft = secondsLeft(left, DURATIONS.get(entry.getKey()));
			String value = seconds.get() && secondsLeft >= 0 ? String.format(Locale.ROOT, "%.1f s", secondsLeft)
					: Math.round(left * 100) + "%";
			lines.add(HudLine.of(entry.getValue().getHoverName().getString(), value, left > 0.5f ? Theme.ERROR : Theme.WARNING));
		}
	}

	private static void addIfCooling(ItemCooldowns cooldowns, ItemStack stack, Map<Identifier, ItemStack> carried) {
		if (!stack.isEmpty() && cooldowns.isOnCooldown(stack)) {
			carried.putIfAbsent(cooldowns.getCooldownGroup(stack), stack);
		}
	}
}
