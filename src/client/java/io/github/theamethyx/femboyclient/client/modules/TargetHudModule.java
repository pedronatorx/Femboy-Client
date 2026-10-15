package io.github.theamethyx.femboyclient.client.modules;

import java.util.Locale;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import io.github.theamethyx.femboyclient.client.FemboyClient;
import io.github.theamethyx.femboyclient.client.combat.Targets;
import io.github.theamethyx.femboyclient.client.gui.Theme;
import io.github.theamethyx.femboyclient.client.gui.UiContext;
import io.github.theamethyx.femboyclient.client.module.ClientHooks;
import io.github.theamethyx.femboyclient.core.module.Module;
import io.github.theamethyx.femboyclient.core.module.ModuleCategory;
import io.github.theamethyx.femboyclient.core.setting.BooleanSetting;
import io.github.theamethyx.femboyclient.core.setting.EnumSetting;
import io.github.theamethyx.femboyclient.core.setting.KeySetting;
import io.github.theamethyx.femboyclient.core.setting.NumberSetting;

/**
 * A card with the current target's name, health, armor, distance, equipment and shield state. The target is the
 * entity you look at, the one you hit last, or one you locked with the lock key. Only information the server
 * already sends to your client is shown.
 */
public final class TargetHudModule extends Module implements ClientHooks.ScreenOverlay {
	public enum Source implements EnumSetting.Labeled {
		LOOK_OR_LAST_HIT("Looked at, else last hit"), LAST_HIT("Last hit only"), LOOK("Looked at only");

		private final String label;

		Source(String label) {
			this.label = label;
		}

		@Override
		public String label() {
			return label;
		}
	}

	public enum Placement implements EnumSetting.Labeled {
		RIGHT_OF_CROSSHAIR("Right of crosshair"), ABOVE_HOTBAR("Above hotbar"), TOP_CENTER("Top center");

		private final String label;

		Placement(String label) {
			this.label = label;
		}

		@Override
		public String label() {
			return label;
		}
	}

	private static final int WIDTH = 132;

	{
		settings().section("Target");
	}

	private final EnumSetting<Source> source = settings().add(new EnumSetting<>("source", "Target",
			"Which entity the card shows when nothing is locked.", Source.LOOK_OR_LAST_HIT));
	private final NumberSetting memory = settings().add(new NumberSetting("memory", "Keep last hit for",
			"How long the entity you hit last stays on the card after you look away.", 6, 1, 30, 1, "s"));
	private final NumberSetting maxDistance = settings().add(new NumberSetting("max_distance", "Maximum distance",
			"Targets farther away are dropped, also a locked one.", 32, 6, 128, 2, "blocks"));
	private final KeySetting lockKey = settings().add(new KeySetting("lock_key", "Lock key",
			"Press while looking at an entity to keep it on the card until you press again (manual target selection).", KeySetting.NONE));

	{
		settings().section("Card");
	}

	private final EnumSetting<Placement> placement = settings().add(new EnumSetting<>("placement", "Position",
			"Where the card is drawn.", Placement.RIGHT_OF_CROSSHAIR));
	private final NumberSetting scale = settings().add(new NumberSetting("scale", "Size", "Size of the card.", 1, 0.5, 2, 0.05, "x"));
	private final NumberSetting background = settings().add(new NumberSetting("background", "Background opacity",
			"Opacity of the card background.", 75, 0, 100, 5, "%"));
	private final BooleanSetting showArmor = settings().add(new BooleanSetting("armor", "Armor and held items",
			"Show the target's armor pieces and held items with their durability.", true));
	private final BooleanSetting showDistance = settings().add(new BooleanSetting("distance", "Distance", "Show the distance to the target.", true));
	private final BooleanSetting showShield = settings().add(new BooleanSetting("shield", "Shield state",
			"Show when the target holds a raised shield (an axe hit disables it).", true));

	private LivingEntity locked;
	private boolean lockKeyWasDown;
	private LivingEntity current;

	public TargetHudModule() {
		super("target_hud", "Target HUD", "A card with your target's health, armor, distance, equipment and shield state.",
				ModuleCategory.COMBAT, false, NO_KEY);
	}

	/** The entity on the card this tick, or null. */
	public LivingEntity target() {
		return current;
	}

	public boolean isLocked() {
		return locked != null;
	}

	/** Locks or clears the target as if the lock key was pressed (also used by tests). */
	public void toggleLock() {
		LivingEntity looked = Targets.crosshair();
		locked = locked == null || (looked != null && looked != locked) ? looked : null;
	}

	@Override
	protected void onDisable() {
		locked = null;
		current = null;
	}

	@Override
	protected void onTick() {
		Minecraft client = Minecraft.getInstance();
		LocalPlayer player = client.player;
		boolean down = lockKey.isBound() && client.screen == null && InputConstants.isKeyDown(client.getWindow(), lockKey.get());
		if (down && !lockKeyWasDown) {
			toggleLock();
		}

		lockKeyWasDown = down;
		double max = maxDistance.get();
		if (locked != null && (!locked.isAlive() || locked.level() != player.level() || locked.distanceTo(player) > max)) {
			locked = null;
		}

		LivingEntity next = locked;
		if (next == null) {
			LivingEntity looked = source.get() == Source.LAST_HIT ? null : Targets.crosshair();
			LivingEntity lastHit = source.get() == Source.LOOK ? null : FemboyClient.get().combat().lastTarget(Math.round(memory.get() * 1000));
			next = looked != null ? looked : lastHit;
		}

		current = next != null && next.distanceTo(player) <= max ? next : null;
	}

	@Override
	public void renderOverlay(UiContext ui, int screenWidth, int screenHeight, float partialTick) {
		LivingEntity target = current;
		LocalPlayer player = Minecraft.getInstance().player;
		if (target == null || player == null) {
			return;
		}

		float s = scale.get().floatValue();
		int height = 34 + (showArmor.get() ? 18 : 0);
		int x = switch (placement.get()) {
			case RIGHT_OF_CROSSHAIR -> screenWidth / 2 + 14;
			case ABOVE_HOTBAR, TOP_CENTER -> Math.round(screenWidth / 2f - WIDTH * s / 2);
		};
		int y = switch (placement.get()) {
			case RIGHT_OF_CROSSHAIR -> screenHeight / 2 + 8;
			case ABOVE_HOTBAR -> Math.round(screenHeight - 62 - height * s);
			case TOP_CENTER -> 22;
		};

		var pose = ui.graphics().pose();
		pose.pushMatrix();
		pose.translate(x, y);
		pose.scale(s, s);
		drawCard(ui, target, player, height);
		pose.popMatrix();
	}

	private void drawCard(UiContext ui, LivingEntity target, LocalPlayer player, int height) {
		int bg = (int) Math.round(background.get() / 100.0 * 255) << 24 | 0x111116;
		ui.roundRect(0, 0, WIDTH, height, bg);
		ui.rect(0, 2, 2, height - 4, isLocked() ? Theme.WARNING : Theme.ACCENT);

		String name = target.getDisplayName().getString();
		ui.textClipped(name, 6, 5, WIDTH - 40, Theme.TEXT);
		if (showDistance.get()) {
			ui.textRight(String.format(Locale.ROOT, "%.1fm", target.distanceTo(player)), WIDTH - 5, 5, Theme.TEXT_SECONDARY);
		}

		float health = target.getHealth();
		float max = Math.max(1, target.getMaxHealth());
		float absorption = target.getAbsorptionAmount();
		int barWidth = WIDTH - 12;
		int filled = Math.round(barWidth * Math.min(1, health / max));
		int healthColor = health / max > 0.5f ? Theme.SUCCESS : health / max > 0.25f ? Theme.WARNING : Theme.ERROR;
		ui.rect(6, 17, barWidth, 4, 0xFF2A2A33);
		ui.rect(6, 17, filled, 4, healthColor);
		if (absorption > 0) {
			ui.rect(6, 21, Math.round(barWidth * Math.min(1, absorption / max)), 1, 0xFFF2C14E);
		}

		String hp = String.format(Locale.ROOT, "%.1f", health) + (absorption > 0 ? String.format(Locale.ROOT, " +%.0f", absorption) : "") + " HP";
		ui.text(hp, 6, 24, Theme.TEXT_SECONDARY);
		int armor = target.getArmorValue();
		String right = armor > 0 ? armor + " armor" : "";
		if (showShield.get() && target.isBlocking()) {
			right = "Shield up" + (right.isEmpty() ? "" : "  " + right);
		}

		ui.textRight(right, WIDTH - 5, 24, target.isBlocking() && showShield.get() ? Theme.WARNING : Theme.TEXT_MUTED);

		if (showArmor.get()) {
			int ix = 6;
			for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET,
					EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND}) {
				ItemStack stack = target.getItemBySlot(slot);
				if (!stack.isEmpty()) {
					ui.graphics().renderItem(stack, ix, 34);
					ui.graphics().renderItemDecorations(Minecraft.getInstance().font, stack, ix, 34);
				}

				ix += 20;
			}
		}

		if (target instanceof Player && target.isFallFlying()) {
			ui.textRight("Gliding", WIDTH - 5, height - 10, Theme.TEXT_MUTED);
		}
	}
}
