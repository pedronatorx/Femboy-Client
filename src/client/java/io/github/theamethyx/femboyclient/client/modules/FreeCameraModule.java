package io.github.theamethyx.femboyclient.client.modules;

import java.util.List;
import java.util.Locale;

import org.lwjgl.glfw.GLFW;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Marker;
import net.minecraft.world.phys.Vec3;

import io.github.theamethyx.femboyclient.client.gui.Theme;
import io.github.theamethyx.femboyclient.client.hud.HudAnchor;
import io.github.theamethyx.femboyclient.client.hud.HudLine;
import io.github.theamethyx.femboyclient.client.module.ClientHooks;
import io.github.theamethyx.femboyclient.client.world.LoadedChunks;
import io.github.theamethyx.femboyclient.core.module.Module;
import io.github.theamethyx.femboyclient.core.module.ModuleCategory;
import io.github.theamethyx.femboyclient.core.setting.BooleanSetting;
import io.github.theamethyx.femboyclient.core.setting.KeySetting;
import io.github.theamethyx.femboyclient.core.setting.NumberSetting;

/**
 * Free camera: detaches the view from the player and flies it while the player stays where it is.
 * Works in singleplayer and on servers.
 *
 * <p>A client-only marker entity (never added to the world) becomes the camera entity. The camera has its
 * own position and rotation: mouse movement turns the camera instead of the player ({@code EntityMixin}),
 * and the player's movement input is swapped for an empty one. Other mixins read {@link #detached}:
 * <ul>
 *   <li>{@code LocalPlayerMixin} keeps the player sending its normal position updates. Vanilla stops
 *       sending them while another entity is the camera, which on a server leaves the player out of
 *       sync and causes a rubber-band correction when the camera returns.</li>
 *   <li>{@code MinecraftMixin} blocks attacking, using items and pick-block. Vanilla aims those from the
 *       camera, so a click would target blocks next to the camera, far from the player.</li>
 *   <li>{@code GameRendererMixin} hides the first-person hand.</li>
 * </ul>
 * Vanilla's occlusion culling is switched off while the camera is detached: with the camera inside a
 * solid block it would otherwise hide nearly everything. The previous value is restored afterwards.
 *
 * <p>Chunks and entities only exist around the player, because that is what the server sends; the
 * camera never causes anything to be loaded. Outside that area the HUD says so.
 */
public final class FreeCameraModule extends Module implements ClientHooks.HudBlock {
	{ settings().section("Movement"); }
	private final NumberSetting speed = settings().add(new NumberSetting("speed", "Speed",
			"Blocks per tick (20 ticks per second).", 0.8, 0.1, 3, 0.1, "blocks/tick"));
	private final NumberSetting boost = settings().add(new NumberSetting("boost", "Boost multiplier",
			"Speed multiplier while the boost key is held.", 2.5, 1, 5, 0.5, "x"));
	private final NumberSetting smoothing = settings().add(new NumberSetting("smoothing", "Smoothing",
			"How gently the camera speeds up and slows down. 0 starts and stops instantly.", 0.5, 0, 0.9, 0.1, ""));
	private final BooleanSetting flyWhereLooking = settings().add(new BooleanSetting("fly_where_looking", "Fly where you look",
			"Forward follows the camera's pitch, so looking down and pressing forward flies downwards. "
					+ "When off, forward and back stay level.", false));
	private final BooleanSetting stayInLoaded = settings().add(new BooleanSetting("stay_in_loaded", "Stay inside loaded chunks",
			"Stop the camera at the edge of the chunks your client has, instead of flying into empty space.", false));

	{ settings().section("Keys"); }
	private final KeySetting upKey = settings().add(new KeySetting("key_up", "Up", "Key that moves the camera up. "
			+ "Forward, back and sideways use your normal movement keys.", GLFW.GLFW_KEY_SPACE));
	private final KeySetting downKey = settings().add(new KeySetting("key_down", "Down", "Key that moves the camera down.", GLFW.GLFW_KEY_LEFT_SHIFT));
	private final KeySetting boostKey = settings().add(new KeySetting("key_boost", "Boost", "Hold to fly faster.", GLFW.GLFW_KEY_LEFT_CONTROL));

	/** True while the view is detached; read by the mixins listed in the class comment. */
	public static volatile boolean detached;

	private Marker camera;
	private LocalPlayer player;
	private ClientInput savedInput;
	private CameraType savedCameraType;
	private boolean savedSmartCull;
	private float yaw;
	private float pitch;
	private Vec3 velocity = Vec3.ZERO;
	private boolean outsideLoaded;
	private boolean stoppedAtEdge;

	public FreeCameraModule() {
		super("free_camera", "Free Camera",
				"Fly the camera away from your player with its own rotation. Your player stays still and can't attack or use items meanwhile.",
				ModuleCategory.MOVEMENT, false, NO_KEY);
	}

	public boolean isActive() {
		return camera != null;
	}

	public Vec3 cameraPosition() {
		return camera == null ? null : camera.position();
	}

	public float cameraYaw() {
		return yaw;
	}

	public float cameraPitch() {
		return pitch;
	}

	/** Whether the camera is over a chunk the client has not loaded (so nothing can be shown there). */
	public boolean outsideLoaded() {
		return outsideLoaded;
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
		yaw = current.getYRot();
		pitch = current.getXRot();
		applyRotation();
		velocity = Vec3.ZERO;

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
		savedSmartCull = client.smartCull;
		client.smartCull = false;
		client.setCameraEntity(camera);
		detached = true;
	}

	@Override
	protected void onDisable() {
		Minecraft client = Minecraft.getInstance();
		boolean wasAttached = camera != null;
		detached = false;
		if (player != null && savedInput != null) {
			player.input = savedInput;
		}

		if (savedCameraType != null) {
			client.options.setCameraType(savedCameraType);
		}

		if (wasAttached) {
			client.smartCull = savedSmartCull;
		}

		if (client.player != null) {
			client.setCameraEntity(client.player);
		}

		camera = null;
		player = null;
		savedInput = null;
		savedCameraType = null;
		velocity = Vec3.ZERO;
		outsideLoaded = false;
		stoppedAtEdge = false;
	}

	/** Mouse movement while detached; called by {@code EntityMixin} instead of turning the player. */
	public void turn(double dx, double dy) {
		yaw += (float) dx * 0.15F;
		pitch = Mth.clamp(pitch + (float) dy * 0.15F, -90, 90);
		applyRotation();
	}

	private void applyRotation() {
		if (camera != null) {
			camera.setYRot(yaw);
			camera.setXRot(pitch);
			camera.yRotO = yaw;
			camera.xRotO = pitch;
		}
	}

	private static boolean down(Minecraft client, KeySetting key) {
		return key.isBound() && InputConstants.isKeyDown(client.getWindow(), key.get());
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

		// No movement while a screen (chat, inventory, this menu) is open, so typed keys never fly the camera.
		Options options = client.options;
		boolean input = client.screen == null;
		double forward = input ? (options.keyUp.isDown() ? 1 : 0) - (options.keyDown.isDown() ? 1 : 0) : 0;
		double strafe = input ? (options.keyLeft.isDown() ? 1 : 0) - (options.keyRight.isDown() ? 1 : 0) : 0;
		double vertical = input ? (down(client, upKey) ? 1 : 0) - (down(client, downKey) ? 1 : 0) : 0;
		double step = speed.get() * (input && down(client, boostKey) ? boost.get() : 1);

		double yawRad = Math.toRadians(yaw);
		double pitchRad = flyWhereLooking.get() ? Math.toRadians(pitch) : 0;
		Vec3 look = new Vec3(-Math.sin(yawRad) * Math.cos(pitchRad), -Math.sin(pitchRad), Math.cos(yawRad) * Math.cos(pitchRad));
		Vec3 side = new Vec3(Math.cos(yawRad), 0, Math.sin(yawRad));
		Vec3 wish = look.scale(forward).add(side.scale(strafe)).add(0, vertical, 0);
		if (wish.lengthSqr() > 1e-6) {
			wish = wish.normalize().scale(step);
		}

		double keep = smoothing.get();
		velocity = velocity.scale(keep).add(wish.scale(1 - keep));
		if (velocity.lengthSqr() < 1e-6 && wish.lengthSqr() < 1e-6) {
			velocity = Vec3.ZERO;
		}

		Vec3 from = camera.position();
		Vec3 to = from.add(velocity);
		ClientLevel level = client.level;
		stoppedAtEdge = false;
		if (stayInLoaded.get() && !loaded(level, to) && loaded(level, from)) {
			// Keep the axes that stay inside loaded chunks, so the camera slides along the edge.
			to = new Vec3(loaded(level, new Vec3(to.x, from.y, from.z)) ? to.x : from.x, to.y,
					loaded(level, new Vec3(from.x, from.y, to.z)) ? to.z : from.z);
			stoppedAtEdge = true;
		}

		camera.xo = from.x;
		camera.yo = from.y;
		camera.zo = from.z;
		camera.setPos(to.x, to.y, to.z);
		applyRotation();
		outsideLoaded = !loaded(level, to);
	}

	private static boolean loaded(ClientLevel level, Vec3 position) {
		return LoadedChunks.isLoaded(level, Mth.floor(position.x) >> 4, Mth.floor(position.z) >> 4);
	}

	/** Shows the camera position while active, and a warning at the edge of the loaded area. */
	@Override
	public HudAnchor hudAnchor() {
		return HudAnchor.BOTTOM_LEFT;
	}

	@Override
	public void buildHud(List<HudLine> lines) {
		if (camera == null) {
			return;
		}

		lines.add(HudLine.of("Free camera", String.format(Locale.ROOT, "%.1f / %.1f / %.1f", camera.getX(), camera.getY(), camera.getZ()), Theme.ACCENT_STRONG));
		if (outsideLoaded) {
			lines.add(HudLine.of("No world data here", "the server only sends chunks near your player", Theme.WARNING));
		} else if (stoppedAtEdge) {
			lines.add(HudLine.of("Edge of loaded chunks", "", Theme.WARNING));
		}
	}
}
