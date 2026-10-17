package io.github.theamethyx.femboyclient.client.modules;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import io.github.theamethyx.femboyclient.client.combat.ExplosionSafety;
import io.github.theamethyx.femboyclient.client.combat.InventoryActions;
import io.github.theamethyx.femboyclient.client.combat.Placement;
import io.github.theamethyx.femboyclient.client.combat.Rotations;
import io.github.theamethyx.femboyclient.client.combat.TargetSettings;
import io.github.theamethyx.femboyclient.client.gui.Theme;
import io.github.theamethyx.femboyclient.client.hud.HudAnchor;
import io.github.theamethyx.femboyclient.client.hud.HudLine;
import io.github.theamethyx.femboyclient.client.module.ClientHooks;
import io.github.theamethyx.femboyclient.client.render.RenderingUtilities;
import io.github.theamethyx.femboyclient.core.module.Module;
import io.github.theamethyx.femboyclient.core.module.ModuleCategory;
import io.github.theamethyx.femboyclient.core.setting.BooleanSetting;
import io.github.theamethyx.femboyclient.core.setting.NumberSetting;

/**
 * Places end crystals on obsidian or bedrock next to a target and breaks crystals that hurt it, within the damage limits.
 * Places with a normal right-click and breaks with a normal attack, turning your view visibly. Sandbox only.
 */
public final class AutoCrystalModule extends Module implements ClientHooks.HudBlock, ClientHooks.WorldRender {
	private static final float POWER = 6;

	private final TargetSettings targets = new TargetSettings(settings(), 10, 16, 360, false);
	private final ExplosionSafety safety = new ExplosionSafety(settings(), 6, 8);

	{
		settings().section("Actions");
	}

	private final BooleanSetting place = settings().add(new BooleanSetting("place", "Place crystals", "Place crystals where they hurt the target most.", true));
	private final BooleanSetting breakCrystals = settings().add(new BooleanSetting("break", "Break crystals",
			"Attack crystals in reach whose explosion passes the damage limits.", true));
	private final NumberSetting placeDelay = settings().add(new NumberSetting("place_delay", "Place delay", "Ticks between two placements.", 2, 0, 20, 1, "ticks"));
	private final NumberSetting breakDelay = settings().add(new NumberSetting("break_delay", "Break delay", "Ticks between two attacks on crystals.", 2, 0, 20, 1, "ticks"));
	private final BooleanSetting autoSwitch = settings().add(new BooleanSetting("switch", "Switch to crystals",
			"Select end crystals in your hotbar to place. When off, crystals must be in your hand or offhand.", true));
	private final BooleanSetting rotate = settings().add(new BooleanSetting("rotate", "Turn toward the spot", "Turn your view to each spot before acting.", true));

	private LivingEntity target;
	private ExplosionSafety.Spot planned;
	private BlockPos plannedBlock;
	private int placeCooldown;
	private int breakCooldown;
	private int placed;
	private int broken;

	public AutoCrystalModule() {
		super("auto_crystal", "AutoCrystal", "Places and breaks end crystals next to your target, within damage limits for you and it.",
				ModuleCategory.AUTOMATION, false, NO_KEY);
	}

	@Override
	public boolean testEnvironmentOnly() {
		return false;
	}

	public int placed() {
		return placed;
	}

	public int broken() {
		return broken;
	}

	@Override
	protected void onEnable() {
		placed = 0;
		broken = 0;
	}

	@Override
	protected void onDisable() {
		target = null;
		planned = null;
	}

	@Override
	protected void onTick() {
		Minecraft client = Minecraft.getInstance();
		LocalPlayer player = client.player;
		placeCooldown = Math.max(0, placeCooldown - 1);
		breakCooldown = Math.max(0, breakCooldown - 1);
		if (client.gameMode == null || client.screen != null) {
			return;
		}

		List<LivingEntity> found = targets.find(player);
		target = found.isEmpty() ? null : found.getFirst();
		planned = null;
		plannedBlock = null;
		if (target == null) {
			return;
		}

		if (breakCrystals.get() && breakCooldown == 0 && breakBest(client, player)) {
			breakCooldown = breakDelay.intValue();
			return;
		}

		if (place.get() && placeCooldown == 0 && placeBest(player, client.level)) {
			placeCooldown = placeDelay.intValue();
		}
	}

	private boolean breakBest(Minecraft client, LocalPlayer player) {
		EndCrystal best = null;
		float bestDamage = -1;
		for (EndCrystal crystal : client.level.getEntitiesOfClass(EndCrystal.class, player.getBoundingBox().inflate(8))) {
			if (!crystal.isAlive() || !player.isWithinEntityInteractionRange(crystal, 0)) {
				continue;
			}

			ExplosionSafety.Spot spot = safety.evaluate(player, target, crystal.position(), POWER);
			if (spot != null && spot.targetDamage() > bestDamage) {
				best = crystal;
				bestDamage = spot.targetDamage();
			}
		}

		if (best == null) {
			return false;
		}

		if (rotate.get()) {
			Rotations.turnToward(player, best.getBoundingBox().getCenter(), 180, true);
		}

		client.gameMode.attack(player, best);
		player.swing(InteractionHand.MAIN_HAND);
		broken++;
		return true;
	}

	private boolean placeBest(LocalPlayer player, Level level) {
		InteractionHand hand;
		if (player.getOffhandItem().is(Items.END_CRYSTAL)) {
			hand = InteractionHand.OFF_HAND;
		} else if (player.getMainHandItem().is(Items.END_CRYSTAL)) {
			hand = InteractionHand.MAIN_HAND;
		} else if (autoSwitch.get() && InventoryActions.findHotbar(player, stack -> stack.is(Items.END_CRYSTAL)) >= 0) {
			hand = InteractionHand.MAIN_HAND;
		} else {
			return false;
		}

		int radius = (int) Math.ceil(player.blockInteractionRange());
		BlockPos origin = player.blockPosition();
		for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-radius, -radius, -radius), origin.offset(radius, radius, radius))) {
			if (!Placement.inReach(player, pos, Direction.UP) || !ExplosionInfoModule.canPlaceCrystal(level, pos)) {
				continue;
			}

			Vec3 center = new Vec3(pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5);
			ExplosionSafety.Spot spot = safety.evaluate(player, target, center, POWER);
			if (spot != null && (planned == null || spot.targetDamage() > planned.targetDamage())) {
				planned = spot;
				plannedBlock = pos.immutable();
			}
		}

		if (plannedBlock == null) {
			return false;
		}

		if (hand == InteractionHand.MAIN_HAND && !player.getMainHandItem().is(Items.END_CRYSTAL)) {
			player.getInventory().setSelectedSlot(InventoryActions.findHotbar(player, stack -> stack.is(Items.END_CRYSTAL)));
		}

		if (rotate.get()) {
			Rotations.turnToward(player, Placement.faceCenter(plannedBlock, Direction.UP), 180, true);
		}

		if (Placement.useOn(player, hand, plannedBlock, Direction.UP).consumesAction()) {
			placed++;
			return true;
		}

		return false;
	}

	@Override
	public void renderWorld(RenderingUtilities render) {
		BlockPos block = plannedBlock;
		if (block != null) {
			render.boxOutline(new net.minecraft.world.phys.AABB(block.above()), Theme.ACCENT, 1.5f, false);
		}
	}

	@Override
	public HudAnchor hudAnchor() {
		return HudAnchor.BOTTOM_RIGHT;
	}

	@Override
	public void buildHud(List<HudLine> lines) {
		ExplosionSafety.Spot spot = planned;
		String text = target == null ? "no target"
				: target.getDisplayName().getString() + (spot == null ? "" : String.format(java.util.Locale.ROOT, "  %.1f / self %.1f", spot.targetDamage(), spot.selfDamage()));
		lines.add(HudLine.of("AutoCrystal", text, target == null ? Theme.TEXT_MUTED : Theme.ACCENT_STRONG));
	}
}
