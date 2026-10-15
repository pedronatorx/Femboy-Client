package io.github.theamethyx.femboyclient.client.modules;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ChargedProjectiles;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import io.github.theamethyx.femboyclient.client.combat.Trajectory;
import io.github.theamethyx.femboyclient.client.module.ClientHooks;
import io.github.theamethyx.femboyclient.client.render.RenderingUtilities;
import io.github.theamethyx.femboyclient.core.module.Module;
import io.github.theamethyx.femboyclient.core.module.ModuleCategory;
import io.github.theamethyx.femboyclient.core.setting.BooleanSetting;
import io.github.theamethyx.femboyclient.core.setting.ColorSetting;
import io.github.theamethyx.femboyclient.core.setting.NumberSetting;

/**
 * Predicted flight path of what you are about to shoot or throw: bow, crossbow, trident, snowball, egg, ender
 * pearl, splash and lingering potions and experience bottles. The end is marked, and an entity in the way is boxed.
 */
public final class TrajectoriesModule extends Module implements ClientHooks.WorldRender {
	private final ColorSetting color = settings().add(new ColorSetting("color", "Path color", "Color of the predicted path.", 0x9D6EFF));
	private final ColorSetting hitColor = settings().add(new ColorSetting("hit_color", "Hit color",
			"Color of the end marker and of an entity the projectile would hit.", 0xF27272));
	private final NumberSetting width = settings().add(new NumberSetting("width", "Line width", "Thickness of the path.", 2, 1, 5, 0.5, "px"));
	private final NumberSetting maxTicks = settings().add(new NumberSetting("max_ticks", "Maximum flight time",
			"Stop the prediction after this many ticks (20 per second).", 200, 20, 400, 10, "ticks"));
	private final BooleanSetting idleBow = settings().add(new BooleanSetting("idle_bow", "Show full-power bow path while not drawing",
			"Without this, the bow path appears only while you draw it.", false));
	private final BooleanSetting throwables = settings().add(new BooleanSetting("throwables", "Throwable items",
			"Snowballs, eggs, ender pearls, splash and lingering potions and experience bottles.", true));

	private Trajectory.Result last;

	public TrajectoriesModule() {
		super("trajectories", "Trajectories", "The predicted path of arrows, tridents, pearls, potions and other projectiles you hold.",
				ModuleCategory.VISUALS, false, NO_KEY);
	}

	/** The prediction drawn in the last frame, or null (for tests). */
	public Trajectory.Result last() {
		return last;
	}

	@Override
	protected void onDisable() {
		last = null;
	}

	/** Launch parameters for the held item, or null if it does not shoot anything right now. */
	private record Launch(Vec3 velocity, Trajectory.Physics physics) {
	}

	private Launch launch(LocalPlayer player, ItemStack stack, float pt) {
		float xRot = player.getViewXRot(pt);
		float yRot = player.getViewYRot(pt);
		Vec3 motion = player.getDeltaMovement();
		Vec3 inherited = new Vec3(motion.x, player.onGround() ? 0 : motion.y, motion.z);
		boolean using = player.isUsingItem() && player.getUseItem() == stack;
		if (stack.is(Items.BOW)) {
			if (!using && !idleBow.get()) return null;
			float power = using ? BowItem.getPowerForTime(player.getTicksUsingItem()) : 1;
			if (power < 0.1f) return null;
			return new Launch(Trajectory.direction(xRot, yRot, 0).scale(power * 3.0).add(inherited), Trajectory.Physics.ARROW);
		}

		if (stack.is(Items.CROSSBOW)) {
			ChargedProjectiles charged = stack.get(DataComponents.CHARGED_PROJECTILES);
			if (!CrossbowItem.isCharged(stack) || charged == null || charged.contains(Items.FIREWORK_ROCKET)) return null;
			return new Launch(player.getViewVector(pt).scale(3.15), Trajectory.Physics.ARROW);
		}

		if (stack.is(Items.TRIDENT)) {
			if (!using || player.getTicksUsingItem() < 10) return null;
			return new Launch(Trajectory.direction(xRot, yRot, 0).scale(2.5).add(inherited), Trajectory.Physics.TRIDENT);
		}

		if (!throwables.get()) return null;
		if (stack.is(Items.SNOWBALL) || stack.is(Items.EGG) || stack.is(Items.ENDER_PEARL)) {
			return new Launch(Trajectory.direction(xRot, yRot, 0).scale(1.5).add(inherited), Trajectory.Physics.THROWN);
		}

		if (stack.is(Items.SPLASH_POTION) || stack.is(Items.LINGERING_POTION)) {
			return new Launch(Trajectory.direction(xRot, yRot, -20).scale(0.5).add(inherited), Trajectory.Physics.POTION);
		}

		if (stack.is(Items.EXPERIENCE_BOTTLE)) {
			return new Launch(Trajectory.direction(xRot, yRot, -20).scale(0.7).add(inherited), Trajectory.Physics.BOTTLE);
		}

		return null;
	}

	@Override
	public void renderWorld(RenderingUtilities render) {
		Minecraft client = Minecraft.getInstance();
		LocalPlayer player = client.player;
		last = null;
		if (player == null || client.level == null) {
			return;
		}

		float pt = render.partialTick();
		Launch launch = launch(player, player.getMainHandItem(), pt);
		if (launch == null) {
			launch = launch(player, player.getOffhandItem(), pt);
		}

		if (launch == null) {
			return;
		}

		Vec3 eye = player.getPosition(pt);
		Vec3 start = new Vec3(eye.x, eye.y + player.getEyeHeight() - 0.1, eye.z);
		Trajectory.Result result = Trajectory.simulate(client.level, player, start, launch.velocity, launch.physics, maxTicks.intValue());
		last = result;
		int lineColor = color.withAlpha(0.9f);
		float w = width.get().floatValue();
		List<Vec3> points = result.points();
		// The first few points are inside or right in front of the camera; skip them so the path does not cover the view.
		for (int i = 2; i < points.size(); i++) {
			render.line(points.get(i - 1), points.get(i), lineColor, w, false);
		}

		int hit = hitColor.withAlpha(0.9f);
		if (result.hitEntity() != null) {
			render.boxOutline(result.hitEntity().getBoundingBox(), hit, w, true);
		} else if (result.hitBlock() != null) {
			Vec3 end = result.end();
			render.boxOutline(new AABB(end, end).inflate(0.25), hit, w, false);
		}
	}
}
