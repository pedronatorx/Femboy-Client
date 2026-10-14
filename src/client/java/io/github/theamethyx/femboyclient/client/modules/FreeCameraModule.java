package io.github.theamethyx.femboyclient.client.modules;

import java.util.List;
import java.util.Locale;

import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Marker;
import net.minecraft.world.phys.Vec3;

import io.github.theamethyx.femboyclient.client.gui.Theme;
import io.github.theamethyx.femboyclient.client.hud.HudAnchor;
import io.github.theamethyx.femboyclient.client.hud.HudLine;
import io.github.theamethyx.femboyclient.client.module.ClientHooks;
import io.github.theamethyx.femboyclient.core.module.Module;
import io.github.theamethyx.femboyclient.core.module.ModuleCategory;
import io.github.theamethyx.femboyclient.core.setting.NumberSetting;

/**
 * Free camera: detaches the view from the player and flies it with the movement keys while the player
 * stays where it is. Works in singleplayer and on servers.
 *
 * <p>A client-only marker entity (never added to the world) becomes the camera entity, and the player's
 * movement input is swapped for an empty one. The camera copies the player's look direction, so the
 * mouse still steers. Three mixins read {@link #detached}:
 * <ul>
 *   <li>{@code LocalPlayerMixin} keeps the player sending its normal position updates. Vanilla stops
 *       sending them while another entity is the camera, which on a server leaves the player out of
 *       sync and causes a rubber-band correction when the camera returns.</li>
 *   <li>{@code MinecraftMixin} blocks attacking, using items and pick-block. Vanilla aims those from the
 *       camera, so a click would target blocks next to the camera, far from the player.</li>
 *   <li>{@code GameRendererMixin} hides the first-person hand.</li>
 * </ul>
 * Chunks and entities are only loaded around the player, so flying far away shows empty space.
 */
public final class FreeCameraModule extends Module implements ClientHooks.HudBlock {
	private final NumberSetting speed = settings().add(new NumberSetting("speed", "Speed",
			"Blocks per tick (20 ticks per second). Hold the sprint key to double it.", 0.8, 0.1, 3, 0.1, "blocks/tick"));

	private Marker camera;
	private LocalPlayer player;
	private ClientInput savedInput;
	private CameraType savedCameraType;

	public FreeCameraModule() {
		super("free_camera", "Free Camera",
				"Fly the camera away from your player. Your player stays still and can't attack or use items meanwhile; disable to snap back.",
				ModuleCategory.PLAYER, false, NO_KEY);
	}

	public boolean isActive() {
		return camera != null;
	}

	/** True while the view is detached; read by the mixins listed in the class comment. */
	public static volatile boolean detached;

	public Vec3 cameraPosition() {
		return camera == null ? null : camera.position();
	}

	@Override
	protected void onEnable() {
		attach();
	}

	private void attach() {
		Minecraft client = Minecraft.getInstance();
		LocalPlayer current = client.player;
		if (current == null || client.level == null) {
			return;
		}

		player = current;
		camera = new Marker(EntityType.MARKER, client.level);
		Vec3 eye = current.getEyePosition();
		camera.setPos(eye.x, eye.y, eye.z);
		camera.xo = eye.x;
		camera.yo = eye.y;
		camera.zo = eye.z;
		copyRotation();

		// Stop anything started from the player's view: mining would otherwise continue on the server.
		if (client.gameMode != null) {
			client.gameMode.stopDestroyBlock();
			if (current.isUsingItem()) {
				client.gameMode.releaseUsingItem(current);
			}
		}

		savedInput = current.input;
		current.input = new ClientInput();
		savedCameraType = client.options.getCameraType();
		client.options.setCameraType(CameraType.FIRST_PERSON);
		client.setCameraEntity(camera);
		detached = true;
	}

	@Override
	protected void onDisable() {
		detached = false;
		Minecraft client = Minecraft.getInstance();
		if (player != null && savedInput != null) {
			player.input = savedInput;
		}

		if (savedCameraType != null) {
			client.options.setCameraType(savedCameraType);
		}

		if (client.player != null) {
			client.setCameraEntity(client.player);
		}

		camera = null;
		player = null;
		savedInput = null;
		savedCameraType = null;
	}

	@Override
	protected void onTick() {
		Minecraft client = Minecraft.getInstance();
		// Respawning or changing dimension replaces the player and world: re-attach cleanly.
		if (client.player != player || camera == null || camera.level() != client.level) {
			onDisable();
			attach();
			return;
		}

		Options options = client.options;
		double forward = (options.keyUp.isDown() ? 1 : 0) - (options.keyDown.isDown() ? 1 : 0);
		double strafe = (options.keyLeft.isDown() ? 1 : 0) - (options.keyRight.isDown() ? 1 : 0);
		double vertical = (options.keyJump.isDown() ? 1 : 0) - (options.keyShift.isDown() ? 1 : 0);
		double step = speed.get() * (options.keySprint.isDown() ? 2 : 1);

		double yaw = Math.toRadians(player.getYRot());
		double dx = (-Math.sin(yaw) * forward + Math.cos(yaw) * strafe);
		double dz = (Math.cos(yaw) * forward + Math.sin(yaw) * strafe);
		double length = Math.sqrt(dx * dx + dz * dz);
		if (length > 1e-6) {
			dx /= length;
			dz /= length;
		}

		camera.xo = camera.getX();
		camera.yo = camera.getY();
		camera.zo = camera.getZ();
		camera.setPos(camera.getX() + dx * step, camera.getY() + vertical * step, camera.getZ() + dz * step);
		copyRotation();
	}

	private void copyRotation() {
		camera.setYRot(player.getYRot());
		camera.setXRot(player.getXRot());
		camera.yRotO = camera.getYRot();
		camera.xRotO = camera.getXRot();
	}

	/** Shows the camera position while active, so it is clear the view is detached. */
	@Override
	public HudAnchor hudAnchor() {
		return HudAnchor.BOTTOM_LEFT;
	}

	@Override
	public void buildHud(List<HudLine> lines) {
		if (camera != null) {
			// Rotation follows the mouse every frame through the player; refresh it here as well
			// so looking around is not limited to the 20 Hz tick rate.
			copyRotation();
			lines.add(HudLine.of("Free camera", String.format(Locale.ROOT, "%.1f / %.1f / %.1f", camera.getX(), camera.getY(), camera.getZ()), Theme.ACCENT_STRONG));
		}
	}
}
