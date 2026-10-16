package io.github.theamethyx.femboyclient.client.combat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Using an item on a block face the way a right-click does ({@code MultiPlayerGameMode.useItemOn}), for the placing
 * modules. The server checks reach and the block like for any right-click.
 */
public final class Placement {
	private Placement() {
	}

	/** The middle of face {@code face} of block {@code pos}. */
	public static Vec3 faceCenter(BlockPos pos, Direction face) {
		return Vec3.atCenterOf(pos).add(face.getStepX() * 0.5, face.getStepY() * 0.5, face.getStepZ() * 0.5);
	}

	/** Whether the face center is within your block interaction range (the server's own check, without its extra margin). */
	public static boolean inReach(LocalPlayer player, BlockPos pos, Direction face) {
		return player.getEyePosition().distanceTo(faceCenter(pos, face)) <= player.blockInteractionRange();
	}

	/** Right-clicks face {@code face} of {@code pos} with the main hand and swings it. */
	public static InteractionResult useOn(LocalPlayer player, BlockPos pos, Direction face) {
		return useOn(player, InteractionHand.MAIN_HAND, pos, face);
	}

	/** Right-clicks face {@code face} of {@code pos} with {@code hand} and swings it. */
	public static InteractionResult useOn(LocalPlayer player, InteractionHand hand, BlockPos pos, Direction face) {
		Minecraft client = Minecraft.getInstance();
		BlockHitResult hit = new BlockHitResult(faceCenter(pos, face), face, pos, false);
		InteractionResult result = client.gameMode.useItemOn(player, hand, hit);
		if (result.consumesAction()) {
			player.swing(hand);
		}

		return result;
	}

	/** The face of {@code pos} nearest to the player's eyes. */
	public static Direction nearestFace(LocalPlayer player, BlockPos pos) {
		Vec3 eye = player.getEyePosition();
		Direction best = Direction.UP;
		double bestDistance = Double.MAX_VALUE;
		for (Direction face : Direction.values()) {
			double distance = eye.distanceToSqr(faceCenter(pos, face));
			if (distance < bestDistance) {
				bestDistance = distance;
				best = face;
			}
		}

		return best;
	}
}
