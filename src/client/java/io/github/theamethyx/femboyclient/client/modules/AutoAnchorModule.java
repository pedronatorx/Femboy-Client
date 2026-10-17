package io.github.theamethyx.femboyclient.client.modules;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

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
 * Respawn anchor PvP outside the Nether: places an anchor next to the target, charges it with glowstone and uses it, which
 * makes it explode (power 5). One right-click per step, each a normal block use; the view turns visibly. Does nothing in
 * the Nether, where anchors set your spawn instead. Sandbox only.
 */
public final class AutoAnchorModule extends Module implements ClientHooks.HudBlock, ClientHooks.WorldRender {
	private static final float POWER = 5;

	private final TargetSettings targets = new TargetSettings(settings(), 8, 12, 360, false);
	private final ExplosionSafety safety = new ExplosionSafety(settings(), 6, 8);

	{
		settings().section("Actions");
	}

	private final NumberSetting delay = settings().add(new NumberSetting("delay", "Step delay", "Ticks between place, charge and explode.", 2, 0, 20, 1, "ticks"));
	private final BooleanSetting rotate = settings().add(new BooleanSetting("rotate", "Turn toward the anchor", "Turn your view to the anchor before each step.", true));

	private LivingEntity target;
	private BlockPos anchor;
	private ExplosionSafety.Spot spot;
	private int cooldown;
	private int explosions;
	private String status = "";

	public AutoAnchorModule() {
		super("auto_anchor", "AutoAnchor", "Places, charges and detonates respawn anchors next to your target (not in the Nether).",
				ModuleCategory.AUTOMATION, false, NO_KEY);
	}

	@Override
	public boolean testEnvironmentOnly() {
		return false;
	}

	public int explosions() {
		return explosions;
	}

	public String status() {
		return status;
	}

	@Override
	protected void onEnable() {
		explosions = 0;
		anchor = null;
	}

	@Override
	protected void onDisable() {
		target = null;
		anchor = null;
	}

	@Override
	protected void onTick() {
		Minecraft client = Minecraft.getInstance();
		LocalPlayer player = client.player;
		Level level = client.level;
		cooldown = Math.max(0, cooldown - 1);
		if (client.gameMode == null || client.screen != null) {
			return;
		}

		if (level.dimension() == Level.NETHER) {
			status = "Anchors set your spawn in the Nether";
			target = null;
			return;
		}

		List<LivingEntity> found = targets.find(player);
		target = found.isEmpty() ? null : found.getFirst();
		if (target == null) {
			status = "no target";
			anchor = null;
			return;
		}

		int anchorSlot = InventoryActions.findHotbar(player, stack -> stack.is(Items.RESPAWN_ANCHOR));
		int glowstoneSlot = InventoryActions.findHotbar(player, stack -> stack.is(Items.GLOWSTONE));
		if (glowstoneSlot < 0) {
			status = "no glowstone in hotbar";
			return;
		}

		if (anchor == null || !level.getBlockState(anchor).is(Blocks.RESPAWN_ANCHOR) && !level.getBlockState(anchor).canBeReplaced()) {
			anchor = choose(player, level, anchorSlot >= 0);
		}

		if (anchor == null) {
			return;
		}

		spot = safety.evaluate(player, target, anchor.getCenter(), POWER, anchor);
		if (spot == null) {
			anchor = null;
			return;
		}

		if (cooldown > 0) {
			return;
		}

		if (player.isShiftKeyDown()) {
			status = "release sneak";
			return;
		}

		BlockState state = level.getBlockState(anchor);
		if (!state.is(Blocks.RESPAWN_ANCHOR)) {
			if (anchorSlot < 0) {
				status = "no respawn anchor in hotbar";
				return;
			}

			Direction face = supportFace(player, level, anchor);
			if (face == null) {
				anchor = null;
				return;
			}

			BlockPos support = anchor.relative(face.getOpposite());
			player.getInventory().setSelectedSlot(anchorSlot);
			turn(player, Placement.faceCenter(support, face));
			Placement.useOn(player, support, face);
			status = "placed";
		} else if (state.getValue(RespawnAnchorBlock.CHARGE) == 0) {
			player.getInventory().setSelectedSlot(glowstoneSlot);
			Direction face = Placement.nearestFace(player, anchor);
			turn(player, Placement.faceCenter(anchor, face));
			Placement.useOn(player, anchor, face);
			status = "charged";
		} else {
			// Any item but glowstone makes the block use go through to the anchor, which explodes outside the Nether.
			int hand = anchorSlot >= 0 ? anchorSlot : InventoryActions.findHotbar(player, stack -> !stack.is(Items.GLOWSTONE));
			if (hand < 0) {
				status = "need a hotbar slot without glowstone";
				return;
			}

			player.getInventory().setSelectedSlot(hand);
			Direction face = Placement.nearestFace(player, anchor);
			turn(player, Placement.faceCenter(anchor, face));
			Placement.useOn(player, anchor, face);
			explosions++;
			status = "exploded";
			anchor = null;
		}

		cooldown = delay.intValue();
	}

	private void turn(LocalPlayer player, net.minecraft.world.phys.Vec3 point) {
		if (rotate.get()) {
			Rotations.turnToward(player, point, 180, true);
		}
	}

	/** The best anchor spot near the target: an existing anchor in reach, or a free spot with a block to place against. */
	private BlockPos choose(LocalPlayer player, Level level, boolean canPlace) {
		BlockPos best = null;
		float bestDamage = -1;
		int blocked = 0;
		int noSupport = 0;
		int rejected = 0;
		float bestRejectedTarget = -1;
		float bestRejectedSelf = -1;
		BlockPos center = target.blockPosition();
		for (BlockPos pos : BlockPos.betweenClosed(center.offset(-2, -1, -2), center.offset(2, 2, 2))) {
			BlockState state = level.getBlockState(pos);
			boolean existing = state.is(Blocks.RESPAWN_ANCHOR);
			if (existing) {
				if (!player.isWithinBlockInteractionRange(pos, 0)) {
					noSupport++;
					continue;
				}
			} else if (!canPlace || !state.canBeReplaced() || !state.getFluidState().isEmpty() || !level.getEntities(null, new AABB(pos)).isEmpty()) {
				blocked++;
				continue;
			} else if (supportFace(player, level, pos) == null) {
				noSupport++;
				continue;
			}

			ExplosionSafety.Spot candidate = safety.evaluate(player, target, pos.getCenter(), POWER, pos.immutable());
			if (candidate == null) {
				rejected++;
				float targetDamage = ExplosionInfoModule.estimate(target, pos.getCenter(), POWER, pos);
				if (targetDamage > bestRejectedTarget) {
					bestRejectedTarget = targetDamage;
					bestRejectedSelf = player.isCreative() ? 0 : ExplosionInfoModule.estimate(player, pos.getCenter(), POWER, pos);
				}
			} else if (candidate.targetDamage() > bestDamage) {
				best = pos.immutable();
				bestDamage = candidate.targetDamage();
			}
		}

		if (best == null) {
			status = "no spot: " + rejected + " outside the damage limits"
					+ (rejected > 0 ? String.format(java.util.Locale.ROOT, " (best %.1f to target, %.1f to you)", bestRejectedTarget, bestRejectedSelf) : "") + ", " + noSupport + " out of reach or without support, " + blocked + " occupied";
		}

		return best;
	}

	/** The face of a solid neighbour block that touches {@code pos} and is in reach, as the clicked face; or {@code null}. */
	private static Direction supportFace(LocalPlayer player, Level level, BlockPos pos) {
		for (Direction face : Direction.values()) {
			BlockPos support = pos.relative(face.getOpposite());
			BlockState state = level.getBlockState(support);
			if (state.isCollisionShapeFullBlock(level, support) && !state.is(Blocks.RESPAWN_ANCHOR) && Placement.inReach(player, support, face)) {
				return face;
			}
		}

		return null;
	}

	@Override
	public void renderWorld(RenderingUtilities render) {
		BlockPos pos = anchor;
		if (pos != null) {
			render.boxOutline(new AABB(pos), Theme.ACCENT, 1.5f, false);
		}
	}

	@Override
	public HudAnchor hudAnchor() {
		return HudAnchor.BOTTOM_RIGHT;
	}

	@Override
	public void buildHud(List<HudLine> lines) {
		ExplosionSafety.Spot current = spot;
		String damage = target != null && current != null
				? String.format(java.util.Locale.ROOT, "  %.1f / self %.1f", current.targetDamage(), current.selfDamage()) : "";
		lines.add(HudLine.of("AutoAnchor", (target == null ? "" : target.getDisplayName().getString() + " - ") + status + damage,
				target == null ? Theme.TEXT_MUTED : Theme.ACCENT_STRONG));
	}
}
