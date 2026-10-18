package io.github.theamethyx.femboyclient.gametest;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

import org.lwjgl.glfw.GLFW;

import com.mojang.blaze3d.platform.NativeImage;

import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;

import io.github.theamethyx.femboyclient.client.FemboyClient;
import io.github.theamethyx.femboyclient.client.gui.widget.Button;
import io.github.theamethyx.femboyclient.client.gui.widget.CodeEditor;
import io.github.theamethyx.femboyclient.client.gui.widget.KeybindButton;
import io.github.theamethyx.femboyclient.client.gui.widget.RunnerPanel;
import io.github.theamethyx.femboyclient.client.gui.widget.SettingRow;
import io.github.theamethyx.femboyclient.client.modules.AutoAttributeSwapModule;
import io.github.theamethyx.femboyclient.client.modules.EntityEspModule;
import io.github.theamethyx.femboyclient.client.modules.FreeCameraModule;
import io.github.theamethyx.femboyclient.client.modules.FullbrightModule;
import io.github.theamethyx.femboyclient.client.modules.JavaRunnerModule;
import io.github.theamethyx.femboyclient.client.modules.TrackersModule;
import io.github.theamethyx.femboyclient.client.modules.WaypointsModule;
import io.github.theamethyx.femboyclient.client.render.EspRenderer;
import io.github.theamethyx.femboyclient.core.module.Module;
import io.github.theamethyx.femboyclient.core.runner.JavaRunner;
import io.github.theamethyx.femboyclient.core.setting.Setting;

/** The ESP overhaul, Trackers, Fullbright, the reworked Free Camera and Java Runner, in singleplayer. */
@SuppressWarnings({"UnstableApiUsage", "unchecked"})
final class AdvancedFeaturesTest {
	private static final int MAGENTA = 0xFFFF00FF;

	private AdvancedFeaturesTest() {
	}

	static FemboyClient femboy() {
		return FemboyClient.get();
	}

	static <T> void set(ClientGameTestContext context, Class<? extends Module> module, String setting, T value) {
		context.runOnClient(client -> {
			String problem = ((Setting<T>) femboy().modules().get(module).settings().get(setting)).set(value);
			if (problem != null) {
				throw new IllegalStateException(setting + ": " + problem);
			}
		});
	}

	static void reset(ClientGameTestContext context, Class<? extends Module> module) {
		context.runOnClient(client -> femboy().modules().get(module).settings().resetAll());
	}

	// ----- screenshot analysis -----------------------------------------------------------------

	/** Pixels that are clearly magenta (red and blue high, green low); channel order does not matter. */
	static int magentaPixels(Path screenshot) {
		return count(screenshot, (r, g, b) -> r > 190 && b > 190 && g < 90);
	}

	interface PixelTest {
		boolean test(int r, int g, int b);
	}

	static int count(Path screenshot, PixelTest test) {
		try (InputStream in = Files.newInputStream(screenshot); NativeImage image = NativeImage.read(in)) {
			int n = 0;
			for (int y = 0; y < image.getHeight(); y++) {
				for (int x = 0; x < image.getWidth(); x++) {
					int p = image.getPixel(x, y);
					if (test.test((p >> 16) & 0xFF, (p >> 8) & 0xFF, p & 0xFF)) n++;
				}
			}

			return n;
		} catch (java.io.IOException e) {
			throw new IllegalStateException(e);
		}
	}

	/** Mean brightness (0-255) of the middle half of the screenshot. */
	static double brightness(Path screenshot) {
		try (InputStream in = Files.newInputStream(screenshot); NativeImage image = NativeImage.read(in)) {
			long sum = 0;
			int n = 0;
			for (int y = image.getHeight() / 4; y < image.getHeight() * 3 / 4; y++) {
				for (int x = image.getWidth() / 4; x < image.getWidth() * 3 / 4; x++) {
					int p = image.getPixel(x, y);
					sum += ((p >> 16) & 0xFF) + ((p >> 8) & 0xFF) + (p & 0xFF);
					n += 3;
				}
			}

			return (double) sum / n;
		} catch (java.io.IOException e) {
			throw new IllegalStateException(e);
		}
	}

	static List<EspRenderer.DrawnLabel> labels(ClientGameTestContext context) {
		context.waitTicks(3);
		return context.computeOnClient(client -> femboy().modules().get(EntityEspModule.class).lastLabels());
	}

	static EspRenderer.DrawnLabel label(List<EspRenderer.DrawnLabel> labels, String name) {
		return labels.stream().filter(l -> l.name() != null && l.name().startsWith(name)).findFirst().orElse(null);
	}

	// ----- ESP ---------------------------------------------------------------------------------

	static void espOverhaul(ClientGameTestContext context, TestSingleplayerContext singleplayer, TestReport report) {
		var server = singleplayer.getServer();
		server.runCommand("tp @a 100.5 -60 0.5 0 0");
		server.runCommand("summon zombie 100.5 -60 6.5 {NoAI:1b,Silent:1b,PersistenceRequired:1b,CustomName:'Target'}");
		context.waitTicks(20);
		reset(context, EntityEspModule.class);
		ClientGameTest.enable(context, EntityEspModule.class, true);
		set(context, EntityEspModule.class, "color_hostile", MAGENTA);
		set(context, EntityEspModule.class, "names", false);
		set(context, EntityEspModule.class, "distance", false);

		// Outline thickness: count magenta pixels at two widths.
		set(context, EntityEspModule.class, "outline_width", 1.0);
		context.waitTicks(3);
		int thin = magentaPixels(context.takeScreenshot("esp-outline-1x"));
		set(context, EntityEspModule.class, "outline_width", 5.0);
		context.waitTicks(3);
		int thick = magentaPixels(context.takeScreenshot("esp-outline-5x"));
		report.check("Outline thickness changes the rendered outline (magenta pixels at 1x vs 5x)", thin > 50 && thick > thin * 1.8,
				thin + " vs " + thick + " pixels");
		set(context, EntityEspModule.class, "outline_opacity", 0.2);
		context.waitTicks(3);
		int faint = magentaPixels(context.takeScreenshot("esp-outline-faint"));
		report.check("Outline opacity changes the rendered outline (strongly magenta pixels at 100% vs 20%)", faint < thick / 2,
				thick + " vs " + faint + " pixels");
		set(context, EntityEspModule.class, "outline_opacity", 1.0);
		set(context, EntityEspModule.class, "outline_width", 1.0);
		set(context, EntityEspModule.class, "outline_hostile", false);
		context.waitTicks(3);
		int none = magentaPixels(context.takeScreenshot("esp-outline-category-off"));
		report.check("Per-category outline switch removes the outline of hostile mobs", none < thin / 4, thin + " vs " + none + " pixels");
		set(context, EntityEspModule.class, "outline_hostile", true);

		// Tracers.
		set(context, EntityEspModule.class, "tracers", true);
		set(context, EntityEspModule.class, "tracer_width", 3.0);
		set(context, EntityEspModule.class, "tracer_opacity", 1.0);
		context.runOnClient(client -> client.player.setXRot(-30));
		context.waitTicks(3);
		int withTracer = magentaPixels(context.takeScreenshot("esp-tracer"));
		set(context, EntityEspModule.class, "tracers", false);
		context.waitTicks(3);
		int withoutTracer = magentaPixels(context.takeScreenshot("esp-no-tracer"));
		report.check("Tracer lines are drawn when enabled and gone when disabled", withTracer > withoutTracer + 30,
				withoutTracer + " without, " + withTracer + " with");
		context.runOnClient(client -> client.player.setXRot(0));

		// Labels: each text element has its own size.
		set(context, EntityEspModule.class, "names", true);
		set(context, EntityEspModule.class, "distance", true);
		var small = label(labels(context), "Target");
		set(context, EntityEspModule.class, "name_scale", 1.5);
		var large = label(labels(context), "Target");
		report.check("Name size changes the drawn label width", small != null && large != null && large.width() > small.width() * 1.5,
				small + " -> " + large);
		set(context, EntityEspModule.class, "name_scale", 0.75);
		set(context, EntityEspModule.class, "distance_scale", 1.6);
		var tallDistance = label(labels(context), "Target");
		report.check("Distance size changes the drawn label height", small != null && tallDistance != null && tallDistance.height() > small.height(),
				small + " -> " + tallDistance);
		set(context, EntityEspModule.class, "distance_scale", 0.65);
		set(context, EntityEspModule.class, "label_padding", 6.0);
		var padded = label(labels(context), "Target");
		report.check("Background padding changes the drawn label size", padded != null && padded.width() == small.width() + 8 && padded.height() == small.height() + 8,
				small + " -> " + padded);
		set(context, EntityEspModule.class, "label_padding", 2.0);
		set(context, EntityEspModule.class, "distance", false);
		var noDistance = label(labels(context), "Target");
		report.check("Distance can be turned off independently of the name", noDistance != null && noDistance.height() < small.height(),
				small + " -> " + noDistance);
		set(context, EntityEspModule.class, "distance", true);

		// Health indicators: optional, per category, bar and/or number.
		report.check("Health is not shown by default", small != null && !small.healthBar() && !small.healthText());
		set(context, EntityEspModule.class, "health", true);
		var bar = label(labels(context), "Target");
		report.check("Health bar is shown for hostile mobs when enabled (bar only by default)", bar != null && bar.healthBar() && !bar.healthText(),
				String.valueOf(bar));
		set(context, EntityEspModule.class, "health_style", EspRenderer.HealthStyle.BAR_AND_TEXT);
		var both = label(labels(context), "Target");
		report.check("Health style 'Bar and number' shows both", both != null && both.healthBar() && both.healthText(), String.valueOf(both));
		set(context, EntityEspModule.class, "health_bar_scale", 3.0);
		var bigBar = label(labels(context), "Target");
		report.check("Health bar size changes the drawn label", both != null && bigBar != null && bigBar.height() > both.height(),
				both + " -> " + bigBar);
		set(context, EntityEspModule.class, "health_hostile", false);
		var noHealth = label(labels(context), "Target");
		report.check("Health can be limited to chosen categories", noHealth != null && !noHealth.healthBar() && !noHealth.healthText(),
				String.valueOf(noHealth));
		set(context, EntityEspModule.class, "health_hostile", true);
		set(context, EntityEspModule.class, "health_bar_scale", 1.0);

		// Long names and overlapping labels.
		server.runCommand("summon zombie 103.5 -60 7.5 {NoAI:1b,Silent:1b,PersistenceRequired:1b,CustomName:'"
				+ "Averyveryveryveryveryveryverylongname".repeat(3) + "'}");
		server.runCommand("summon zombie 98.5 -60 8.5 {NoAI:1b,Silent:1b,PersistenceRequired:1b,CustomName:'Stacked A'}");
		server.runCommand("summon zombie 98.55 -59.9 8.55 {NoAI:1b,Silent:1b,PersistenceRequired:1b,CustomName:'Stacked B'}");
		context.waitTicks(10);
		List<EspRenderer.DrawnLabel> all = labels(context);
		var longLabel = label(all, "Averyvery");
		report.check("Long names are shortened to the maximum name width", longLabel != null && longLabel.width() <= 110 + 4 + 1, String.valueOf(longLabel));
		boolean overlap = false;
		for (int i = 0; i < all.size(); i++) {
			for (int j = i + 1; j < all.size(); j++) {
				var a = all.get(i);
				var b = all.get(j);
				overlap |= a.x() < b.x() + b.width() && b.x() < a.x() + a.width() && a.y() < b.y() + b.height() && b.y() < a.y() + a.height();
			}
		}

		report.check("Labels of entities at the same spot do not overlap", label(all, "Stacked A") != null && label(all, "Stacked B") != null && !overlap,
				all.toString());
		set(context, EntityEspModule.class, "tracers", true);
		set(context, EntityEspModule.class, "tracer_width", 1.0);
		set(context, EntityEspModule.class, "tracer_opacity", 0.6);
		context.waitTicks(3);
		context.takeScreenshot("world-esp-overhaul");
		context.getInput().resizeWindow(854, 480);
		context.waitTicks(5);
		List<EspRenderer.DrawnLabel> small854 = labels(context);
		context.takeScreenshot("world-esp-overhaul-854x480");
		context.getInput().resizeWindow(1280, 720);
		context.waitTicks(5);
		report.check("Labels are drawn at 854x480 as well", label(small854, "Target") != null, small854.toString());
		reset(context, EntityEspModule.class);

		// Performance with many entities: 250 more zombies.
		server.runOnServer(s -> {
			for (int i = 0; i < 250; i++) {
				var zombie = EntityType.ZOMBIE.create(s.overworld(), EntitySpawnReason.COMMAND);
				zombie.setNoAi(true);
				zombie.setSilent(true);
				zombie.setPersistenceRequired();
				zombie.snapTo(90 + (i % 25) * 0.9, -60, 12 + (i / 25) * 0.9, 0, 0);
				s.overworld().addFreshEntity(zombie);
			}
		});
		context.waitTicks(40);
		set(context, EntityEspModule.class, "health", true);
		set(context, EntityEspModule.class, "tracers", true);
		ClientGameTest.enable(context, TrackersModule.class, true);
		set(context, TrackersModule.class, "hostile", true);
		context.waitTicks(60);
		context.runOnClient(client -> femboy().performance().reset());
		context.waitTicks(60);
		double frameWith = context.computeOnClient(client -> femboy().performance().frameStats().averageFrameMs());
		int tracked = context.computeOnClient(client -> femboy().modules().get(EntityEspModule.class).tracked().size());
		String moduleCost = context.computeOnClient(client -> {
			StringBuilder out = new StringBuilder();
			for (String key : List.of("entity_esp/tick", "entity_esp/render_world", "entity_esp/render_hud", "trackers/render_hud")) {
				var timing = femboy().performance().timing(key);
				out.append(String.format(Locale.ROOT, "%s %.3f ms, ", key, timing == null ? -1 : timing.averageMicros() / 1000));
			}

			return out.toString();
		});
		ClientGameTest.enable(context, EntityEspModule.class, false);
		ClientGameTest.enable(context, TrackersModule.class, false);
		context.runOnClient(client -> femboy().performance().reset());
		context.waitTicks(60);
		double frameWithout = context.computeOnClient(client -> femboy().performance().frameStats().averageFrameMs());
		report.note(String.format(Locale.ROOT, "254 zombies in view (Mesa software rendering): average frame %.1f ms with ESP (labels, health, tracers) and "
				+ "Trackers, %.1f ms without. Average cost per call: %s", frameWith, frameWithout, moduleCost));
		report.check("With 254 entities, ESP keeps to its highlight limit (64)", tracked == 64, tracked + " tracked");
		report.check("No module errors with many entities", context.computeOnClient(client -> femboy().diagnostics().errorCount()) == 0);
		server.runCommand("kill @e[type=minecraft:zombie,x=85,y=-62,z=-20,dx=30,dy=10,dz=60]");
		server.runCommand("kill @e[type=minecraft:item]");
		reset(context, EntityEspModule.class);
		reset(context, TrackersModule.class);
		context.waitTicks(30);
		server.runCommand("kill @e[type=minecraft:item]");
	}

	// ----- Trackers ----------------------------------------------------------------------------

	static void trackers(ClientGameTestContext context, TestSingleplayerContext singleplayer, TestReport report) {
		var server = singleplayer.getServer();
		server.runCommand("tp @a 100.5 -60 0.5 0 0");
		server.runCommand("summon zombie 100.5 -60 8.5 {NoAI:1b,Silent:1b,PersistenceRequired:1b,CustomName:'Ahead'}");
		server.runCommand("summon zombie 100.5 -60 -9.5 {NoAI:1b,Silent:1b,PersistenceRequired:1b,CustomName:'Behind'}");
		context.waitTicks(20);
		ClientGameTest.enable(context, WaypointsModule.class, true);
		context.runOnClient(client -> femboy().modules().get(WaypointsModule.class).addAtPlayer("Tracker test"));
		ClientGameTest.enable(context, TrackersModule.class, true);
		set(context, TrackersModule.class, "hostile", true);
		context.waitTicks(10);
		List<TrackersModule.Indicator> indicators = context.computeOnClient(client -> femboy().modules().get(TrackersModule.class).lastIndicators());
		int height = context.computeOnClient(client -> client.getWindow().getGuiScaledHeight());
		var ahead = indicators.stream().filter(i -> i.name().equals("Ahead")).findFirst().orElse(null);
		var behind = indicators.stream().filter(i -> i.name().equals("Behind")).findFirst().orElse(null);
		var waypoint = indicators.stream().filter(i -> i.name().equals("Tracker test")).findFirst().orElse(null);
		report.check("Trackers: a mob in front gets an on-screen marker", ahead != null && ahead.onScreen(), String.valueOf(ahead));
		report.check("Trackers: a mob behind you gets an arrow at the bottom edge", behind != null && !behind.onScreen() && behind.y() > height / 2,
				behind + ", screen height " + height);
		report.check("Trackers: waypoints are tracked and labelled as waypoints", waypoint != null && waypoint.label().contains("waypoint"), String.valueOf(waypoint));
		context.takeScreenshot("visuals-trackers");

		server.runCommand("kill @e[type=minecraft:zombie,name=Behind]");
		context.waitTicks(15);
		boolean gone = context.computeOnClient(client -> femboy().modules().get(TrackersModule.class).lastIndicators().stream()
				.noneMatch(i -> i.name().equals("Behind")));
		report.check("Trackers: a target that disappears loses its indicator", gone);
		set(context, TrackersModule.class, "size", 20.0);
		context.waitTicks(3);
		context.takeScreenshot("visuals-trackers-large");
		ClientGameTest.enable(context, TrackersModule.class, false);
		report.check("Trackers: no indicators after turning it off",
				context.computeOnClient(client -> femboy().modules().get(TrackersModule.class).lastIndicators().isEmpty()));
		reset(context, TrackersModule.class);
		context.runOnClient(client -> {
			WaypointsModule waypoints = femboy().modules().get(WaypointsModule.class);
			waypoints.visibleHere().stream().filter(w -> w.name().equals("Tracker test")).toList().forEach(waypoints::remove);
		});
		server.runCommand("kill @e[type=minecraft:zombie,x=85,y=-62,z=-20,dx=30,dy=10,dz=60]");
	}

	// ----- Fullbright --------------------------------------------------------------------------

	static void fullbright(ClientGameTestContext context, TestSingleplayerContext singleplayer, TestReport report) {
		var server = singleplayer.getServer();
		server.runCommand("fill 99 -61 19 105 -55 25 minecraft:stone hollow");
		server.runCommand("time set midnight");
		server.runCommand("tp @a 102.5 -60 21.5 0 10");
		context.waitTicks(30);
		double gammaBefore = context.computeOnClient(client -> client.options.gamma().get());
		double dark = brightness(context.takeScreenshot("visuals-fullbright-off"));
		ClientGameTest.enable(context, FullbrightModule.class, true);
		context.waitTicks(5);
		double bright = brightness(context.takeScreenshot("visuals-fullbright-on"));
		set(context, FullbrightModule.class, "intensity", 0.3);
		context.waitTicks(5);
		double partial = brightness(context.takeScreenshot("visuals-fullbright-30"));
		ClientGameTest.enable(context, FullbrightModule.class, false);
		context.waitTicks(5);
		double restored = brightness(context.takeScreenshot("visuals-fullbright-restored"));
		double gammaAfter = context.computeOnClient(client -> client.options.gamma().get());
		String values = String.format(Locale.ROOT, "brightness off %.1f, 100%% %.1f, 30%% %.1f, off again %.1f", dark, bright, partial, restored);
		report.check("Fullbright brightens a dark room", bright > dark + 40, values);
		report.check("Fullbright intensity changes the brightness", partial > dark + 5 && partial < bright - 5, values);
		report.check("Turning Fullbright off restores the original brightness", Math.abs(restored - dark) < 3, values);
		report.check("Fullbright never changes the brightness video setting", gammaBefore == gammaAfter, gammaBefore + " -> " + gammaAfter);
		reset(context, FullbrightModule.class);
		server.runCommand("fill 99 -61 19 105 -55 25 minecraft:air");
		server.runCommand("time set day");
	}

	// ----- Free Camera -------------------------------------------------------------------------

	static void freeCamera(ClientGameTestContext context, UiDriver ui, TestSingleplayerContext singleplayer, TestReport report) {
		singleplayer.getServer().runCommand("tp @a 100.5 -60 0.5 0 0");
		context.waitTicks(10);
		reset(context, FreeCameraModule.class);
		boolean smartCullBefore = context.computeOnClient(client -> client.smartCull);
		ClientGameTest.enable(context, FreeCameraModule.class, true);
		context.waitTicks(2);
		report.check("Occlusion culling is off while the camera is detached", !context.computeOnClient(client -> client.smartCull));

		// Mouse turns the camera, not the player.
		float playerYaw = context.computeOnClient(client -> client.player.getYRot());
		float cameraYaw = context.computeOnClient(client -> femboy().modules().get(FreeCameraModule.class).cameraYaw());
		context.runOnClient(client -> client.mouseHandler.grabMouse());
		context.waitTicks(2);
		boolean grabbed = context.computeOnClient(client -> client.mouseHandler.isMouseGrabbed());
		// Vanilla ignores the first cursor event after grabbing the mouse (it only sets the starting point).
		context.getInput().moveCursor(1, 0);
		context.waitTicks(2);
		cameraYaw = context.computeOnClient(client -> femboy().modules().get(FreeCameraModule.class).cameraYaw());
		context.getInput().moveCursor(300, 0);
		context.waitTicks(3);
		float cameraYawAfter = context.computeOnClient(client -> femboy().modules().get(FreeCameraModule.class).cameraYaw());
		float playerYawAfter = context.computeOnClient(client -> client.player.getYRot());
		report.check("Mouse movement turns the free camera", grabbed && Math.abs(cameraYawAfter - cameraYaw) > 5,
				"grabbed " + grabbed + ", camera yaw " + cameraYaw + " -> " + cameraYawAfter);
		report.check("Mouse movement leaves the player's rotation alone", Math.abs(playerYawAfter - playerYaw) < 0.01f, playerYaw + " -> " + playerYawAfter);

		// Configurable keys: rebind "up" through the GUI and use it.
		ClientGameTest.openMenu(context);
		ClientGameTest.openModule(context, FreeCameraModule.class);
		ui.openSection("Keys");
		SettingRow upRow = ui.find(SettingRow.class, row -> row.setting().id().equals("key_up"));
		ui.click(upRow.control());
		ui.key(GLFW.GLFW_KEY_R);
		context.waitTicks(2);
		report.check("The up key can be rebound in the menu", context.computeOnClient(client ->
				(Integer) femboy().modules().get(FreeCameraModule.class).settings().get("key_up").get()) == GLFW.GLFW_KEY_R
				&& upRow.control() instanceof KeybindButton);
		context.takeScreenshot("visuals-free-camera-settings");
		ClientGameTest.closeMenu(context, ui);
		double y0 = context.computeOnClient(client -> femboy().modules().get(FreeCameraModule.class).cameraPosition().y);
		double playerY = context.computeOnClient(client -> client.player.getY());
		context.getInput().holdKeyFor(GLFW.GLFW_KEY_R, 10);
		context.waitTicks(10);
		double y1 = context.computeOnClient(client -> femboy().modules().get(FreeCameraModule.class).cameraPosition().y);
		report.check("The rebound up key raises the camera; the player stays", y1 - y0 > 2 && Math.abs(context.computeOnClient(client -> client.player.getY()) - playerY) < 0.01,
				String.format(Locale.ROOT, "camera %.2f -> %.2f", y0, y1));

		// Edge of the loaded area.
		set(context, FreeCameraModule.class, "speed", 3.0);
		set(context, FreeCameraModule.class, "boost", 5.0);
		set(context, FreeCameraModule.class, "smoothing", 0.0);
		context.getInput().holdKey(GLFW.GLFW_KEY_LEFT_CONTROL);
		context.getInput().holdKey(options -> options.keyUp);
		boolean outside = ClientGameTest.waitUntil(context, () -> context.computeOnClient(client ->
				femboy().modules().get(FreeCameraModule.class).outsideLoaded()), 120);
		context.getInput().releaseKey(options -> options.keyUp);
		context.getInput().releaseKey(GLFW.GLFW_KEY_LEFT_CONTROL);
		context.waitTicks(3);
		report.check("Flying past the loaded chunks is detected and shown on the HUD", outside,
				String.valueOf(context.<Vec3, RuntimeException>computeOnClient(client -> femboy().modules().get(FreeCameraModule.class).cameraPosition())));
		context.takeScreenshot("visuals-free-camera-edge");

		set(context, FreeCameraModule.class, "stay_in_loaded", true);
		ClientGameTest.enable(context, FreeCameraModule.class, false);
		ClientGameTest.enable(context, FreeCameraModule.class, true);
		context.waitTicks(2);
		context.getInput().holdKey(GLFW.GLFW_KEY_LEFT_CONTROL);
		context.getInput().holdKeyFor(options -> options.keyUp, 120);
		context.getInput().releaseKey(GLFW.GLFW_KEY_LEFT_CONTROL);
		context.waitTicks(3);
		Vec3 stopped = context.computeOnClient(client -> femboy().modules().get(FreeCameraModule.class).cameraPosition());
		boolean inside = !context.computeOnClient(client -> femboy().modules().get(FreeCameraModule.class).outsideLoaded());
		report.check("'Stay inside loaded chunks' stops the camera at the edge", inside && stopped.z > 50, String.valueOf(stopped));

		ClientGameTest.enable(context, FreeCameraModule.class, false);
		context.waitTicks(2);
		report.check("Disabling restores the view, occlusion culling and the player's camera", context.computeOnClient(client ->
				client.getCameraEntity() == client.player && client.smartCull == smartCullBefore && !FreeCameraModule.detached));
		reset(context, FreeCameraModule.class);
	}

	// ----- Java Runner -------------------------------------------------------------------------

	private static JavaRunner.Execution execution(ClientGameTestContext context) {
		return context.computeOnClient(client -> femboy().modules().get(JavaRunnerModule.class).execution());
	}

	private static void setCode(ClientGameTestContext context, UiDriver ui, String code) {
		context.runOnClient(client -> {
			RunnerPanel panel = ((io.github.theamethyx.femboyclient.client.gui.ClientScreen) client.screen).currentPage().descendants(RunnerPanel.class).getFirst();
			panel.editor().setText(code);
			femboy().modules().get(JavaRunnerModule.class).setSource(code);
		});
	}

	private static void clickButton(UiDriver ui, String label) {
		ui.click(ui.find(Button.class, b -> b.label().equals(label)));
	}

	private static JavaRunner.Execution awaitEnd(ClientGameTestContext context, int ticks) {
		ClientGameTest.waitUntil(context, () -> {
			JavaRunner.Execution e = execution(context);
			return e != null && !e.status().active();
		}, ticks);
		return execution(context);
	}

	static void javaRunner(ClientGameTestContext context, UiDriver ui, TestReport report) {
		ClientGameTest.openMenu(context);
		ClientGameTest.showPage(context, "World");
		ClientGameTest.openModule(context, JavaRunnerModule.class);
		ClientGameTest.enable(context, JavaRunnerModule.class, true);
		context.waitTicks(2);

		// Type a program into the editor and press Run, like a user.
		CodeEditor editor = ui.find(CodeEditor.class, e -> true);
		ui.click(editor);
		context.getInput().holdControl();
		context.getInput().pressKey(GLFW.GLFW_KEY_A);
		context.getInput().releaseControl();
		ui.type("int answer = 6 * 7;");
		ui.key(GLFW.GLFW_KEY_ENTER);
		ui.type("System.out.println(\"answer \" + answer);");
		context.waitTicks(2);
		String typed = context.computeOnClient(client -> femboy().modules().get(JavaRunnerModule.class).source());
		report.check("Typing in the code editor updates the program", typed.equals("int answer = 6 * 7;\nSystem.out.println(\"answer \" + answer);"), typed);
		clickButton(ui, "Run");
		JavaRunner.Execution run = awaitEnd(context, 1200);
		report.check("Java Runner: Run compiles and runs in a separate process and shows the output",
				run != null && run.status() == JavaRunner.Status.FINISHED && run.text().equals("answer 42"),
				run == null ? "no run" : run.status() + ": " + run.text());
		context.takeScreenshot("developer-java-runner");
		clickButton(ui, "Copy");
		context.waitTicks(2);
		String clipboard = context.computeOnClient(client -> client.keyboardHandler.getClipboard());
		report.check("Java Runner: Copy puts the output on the clipboard", clipboard.equals("answer 42"), clipboard);
		clickButton(ui, "Clear");
		context.waitTicks(2);
		report.check("Java Runner: Clear empties the output", execution(context) == null);

		// Real client data through the snapshot API.
		set(context, FreeCameraModule.class, "speed", 1.7);
		setCode(context, ui, "System.out.println(Femboy.setting(\"free_camera\", \"speed\"));\nSystem.out.println(Femboy.enabled(\"java_runner\"));");
		clickButton(ui, "Run");
		run = awaitEnd(context, 1200);
		report.check("Java Runner: snippets read the client's real settings through Femboy.*", run != null && run.text().equals("1.7 blocks/tick\ntrue"),
				run == null ? "no run" : run.status() + ": " + run.text());
		reset(context, FreeCameraModule.class);

		// Errors.
		setCode(context, ui, "int x = 1;\nString s = x;");
		clickButton(ui, "Run");
		run = awaitEnd(context, 1200);
		report.check("Java Runner: compile errors are shown with the line number", run != null && run.status() == JavaRunner.Status.COMPILE_ERROR
				&& run.text().startsWith("Line 2: "), run == null ? "no run" : run.status() + ": " + run.text());
		context.takeScreenshot("developer-java-runner-compile-error");
		setCode(context, ui, "throw new IllegalStateException(\"boom\");");
		clickButton(ui, "Run");
		run = awaitEnd(context, 1200);
		report.check("Java Runner: runtime exceptions are shown", run != null && run.status() == JavaRunner.Status.FAILED && run.text().contains("boom"),
				run == null ? "no run" : run.status() + ": " + run.text());
		setCode(context, ui, "System.out.println(net.minecraft.client.Minecraft.getInstance());");
		clickButton(ui, "Run");
		run = awaitEnd(context, 1200);
		report.check("Java Runner: the game itself is not reachable (compile error)", run != null && run.status() == JavaRunner.Status.COMPILE_ERROR,
				run == null ? "no run" : run.status() + ": " + run.text());

		// Timeout while the game keeps running.
		set(context, JavaRunnerModule.class, "timeout", 6.0);
		setCode(context, ui, "while (true) { Thread.onSpinWait(); }");
		clickButton(ui, "Run");
		ClientGameTest.waitUntil(context, () -> execution(context).status() == JavaRunner.Status.RUNNING, 600);
		long started = System.nanoTime();
		context.waitTicks(20);
		double seconds = (System.nanoTime() - started) / 1e9;
		boolean stillRunning = execution(context).status() == JavaRunner.Status.RUNNING;
		report.check("Minecraft keeps ticking while a program runs (20 ticks while the worker loops)", stillRunning && seconds < 4,
				String.format(Locale.ROOT, "20 ticks took %.2f s, still running: %s", seconds, stillRunning));
		run = awaitEnd(context, 600);
		report.check("Java Runner: an endless loop is stopped at the time limit", run.status() == JavaRunner.Status.TIMED_OUT
				&& !ProcessHandle.of(run.pid()).map(ProcessHandle::isAlive).orElse(false), run.status() + ", pid " + run.pid());

		// Stop button.
		setCode(context, ui, "System.out.println(\"sleeping\");\nThread.sleep(60_000);");
		clickButton(ui, "Run");
		ClientGameTest.waitUntil(context, () -> !execution(context).lines().isEmpty(), 600);
		clickButton(ui, "Stop");
		run = awaitEnd(context, 200);
		report.check("Java Runner: Stop kills the program", run.status() == JavaRunner.Status.STOPPED
				&& !ProcessHandle.of(run.pid()).map(ProcessHandle::isAlive).orElse(false), run.status().toString());

		// Excessive output.
		set(context, JavaRunnerModule.class, "max_output", 16.0);
		setCode(context, ui, "while (true) System.out.println(\"x\".repeat(100));");
		clickButton(ui, "Run");
		run = awaitEnd(context, 600);
		report.check("Java Runner: excessive output is cut off", run.status() == JavaRunner.Status.OUTPUT_LIMIT, run.status() + ", " + run.lines().size() + " lines");

		// Risky code needs confirmation; Cancel runs nothing.
		JavaRunner.Execution before = execution(context);
		setCode(context, ui, "java.nio.file.Files.deleteIfExists(java.nio.file.Path.of(\"nothing-here.txt\"));");
		clickButton(ui, "Run");
		context.waitTicks(3);
		List<String> risks = context.computeOnClient(client -> femboy().modules().get(JavaRunnerModule.class).pendingRisks());
		report.check("Java Runner: code that deletes files asks for confirmation and does not run yet", risks.equals(List.of("deletes files"))
				&& execution(context) == before, risks.toString());
		ui.hoverVirtual(5, 5);
		context.waitTicks(2);
		context.takeScreenshot("developer-java-runner-confirm");
		clickButton(ui, "Cancel");
		context.waitTicks(2);
		report.check("Java Runner: Cancel runs nothing", context.computeOnClient(client -> femboy().modules().get(JavaRunnerModule.class).pendingRisks().isEmpty())
				&& execution(context) == before);

		reset(context, JavaRunnerModule.class);
		setCode(context, ui, JavaRunnerModule.EXAMPLES.get(1).code());
		clickButton(ui, "Run");
		run = awaitEnd(context, 1200);
		report.check("Java Runner: the 'Module overview' example runs", run != null && run.status() == JavaRunner.Status.FINISHED
				&& run.text().contains("Java Runner"), run == null ? "no run" : run.status() + ": " + run.text());
		context.takeScreenshot("developer-java-runner-table");
		ClientGameTest.enable(context, JavaRunnerModule.class, false);
		ClientGameTest.closeMenu(context, ui);
	}

	// ----- pages -------------------------------------------------------------------------------

	static void pages(ClientGameTestContext context, UiDriver ui, TestReport report) {
		ClientGameTest.openMenu(context);
		ClientGameTest.openModule(context, AutoAttributeSwapModule.class);
		report.check("Auto Attribute Swap is on the Combat page", ClientGameTest.currentTitle(context).equals("Combat"));
		context.takeScreenshot("combat-auto-attribute-swap");
		ClientGameTest.openModule(context, EntityEspModule.class);
		List<String> sections = context.computeOnClient(client -> ((io.github.theamethyx.femboyclient.client.gui.ClientScreen) client.screen).currentPage()
				.descendants(SettingRow.SectionHeader.class).stream().map(SettingRow.SectionHeader::title).toList());
		report.check("Entity ESP settings are grouped into sections", sections.equals(List.of("Targets", "Highlight", "Colors", "Labels", "Health", "Target lines", "Advanced", "Performance")),
				sections.toString());
		context.takeScreenshot("visuals-entity-esp-sections");
		ClientGameTest.openModule(context, FreeCameraModule.class);
		String cameraPage = ClientGameTest.currentTitle(context);
		ClientGameTest.openModule(context, io.github.theamethyx.femboyclient.client.modules.FullbrightModule.class);
		String brightPage = ClientGameTest.currentTitle(context);
		report.check("Free Camera is on the Movement page and Fullbright on Visuals", cameraPage.equals("Movement") && brightPage.equals("Visuals"),
				cameraPage + " / " + brightPage);
		ClientGameTest.closeMenu(context, ui);
	}
}
