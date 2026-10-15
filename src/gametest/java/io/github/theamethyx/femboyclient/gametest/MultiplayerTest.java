package io.github.theamethyx.femboyclient.gametest;

import java.util.List;
import java.util.UUID;

import org.lwjgl.glfw.GLFW;

import com.mojang.authlib.GameProfile;

import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerConnection;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import io.github.theamethyx.femboyclient.client.FemboyClient;
import io.github.theamethyx.femboyclient.client.modules.AutoAttributeSwapModule;
import io.github.theamethyx.femboyclient.client.modules.EntityEspModule;
import io.github.theamethyx.femboyclient.client.modules.FullbrightModule;
import io.github.theamethyx.femboyclient.client.modules.TrackersModule;
import io.github.theamethyx.femboyclient.client.modules.FreeCameraModule;
import io.github.theamethyx.femboyclient.client.modules.GeodeAnalyzerModule;
import io.github.theamethyx.femboyclient.client.modules.PlayerAlertModule;
import io.github.theamethyx.femboyclient.client.modules.PlayerFinderModule;
import io.github.theamethyx.femboyclient.client.modules.StorageEspModule;
import io.github.theamethyx.femboyclient.core.module.Module;
import io.github.theamethyx.femboyclient.core.setting.BooleanSetting;

/**
 * On a real dedicated server: every module runs, a second player (a Fabric fake player added on the server)
 * triggers Player Alert and shows up in Player Finder and Entity ESP, and the free camera keeps the server
 * in sync and does not act on blocks in front of the camera.
 */
@SuppressWarnings("UnstableApiUsage")
final class MultiplayerTest {
	private static final List<Class<? extends Module>> MODULES = List.of(GeodeAnalyzerModule.class, EntityEspModule.class,
			StorageEspModule.class, PlayerFinderModule.class, PlayerAlertModule.class, TrackersModule.class, FullbrightModule.class);
	private static final UUID ALICE = UUID.nameUUIDFromBytes("femboyclient-test-alice".getBytes());

	private MultiplayerTest() {
	}

	private static FemboyClient femboy() {
		return FemboyClient.get();
	}

	private static ServerPlayer serverPlayer(net.minecraft.server.MinecraftServer server) {
		return server.getPlayerList().getPlayers().stream().filter(p -> !(p instanceof FakePlayer)).findFirst().orElseThrow();
	}

	/** "x y z" of the bottom center of a block; correct for negative coordinates too (-4 -> -3.5, not -4.5). */
	private static String center(BlockPos pos) {
		return String.format(java.util.Locale.ROOT, "%.1f %d %.1f", pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
	}

	static void run(ClientGameTestContext context, UiDriver ui, TestReport report) {
		try (TestDedicatedServerContext server = context.worldBuilder().createServer();
				TestServerConnection connection = server.connect()) {
			connection.getClientWorld().waitForChunksRender();
			String address = context.computeOnClient(client -> client.getCurrentServer().ip);
			report.note("Connected to dedicated test server at " + address);
			server.runCommand("gamemode creative @a");

			context.runOnClient(client -> MODULES.forEach(type -> femboy().modules().setEnabled(femboy().modules().get(type), true)));
			context.waitTicks(3);
			report.check("Every module runs on a dedicated server (no server list, no pausing)", context.computeOnClient(client -> MODULES.stream()
					.map(type -> femboy().modules().get(type)).allMatch(Module::isRunning)));

			// A 3x3 platform in the air, so positions and the blocks under the camera are known.
			BlockPos base = server.computeOnServer(s -> serverPlayer(s).blockPosition().above(30));
			server.runOnServer(s -> {
				for (int dx = -1; dx <= 1; dx++) {
					for (int dz = -1; dz <= 1; dz++) {
						s.overworld().setBlockAndUpdate(base.offset(dx, 0, dz), Blocks.STONE.defaultBlockState());
					}
				}
			});
			server.runCommand("tp @a " + center(base.above()) + " -90 20");
			context.waitTicks(20);

			secondPlayer(context, server, base, report);
			attributeSwap(context, server, base, report);
			freeCamera(context, server, base, report);

			context.runOnClient(client -> MODULES.forEach(type -> femboy().modules().setEnabled(femboy().modules().get(type), false)));
		}
	}

	private static void secondPlayer(ClientGameTestContext context, TestDedicatedServerContext server, BlockPos base, TestReport report) {
		context.runOnClient(client -> ((BooleanSetting) femboy().modules().get(PlayerAlertModule.class).settings().get("on_leave")).set(true));
		Vec3 at = Vec3.atBottomCenterOf(base.above()).add(5, 0, 0);
		server.runOnServer(s -> {
			ServerLevel level = s.overworld();
			FakePlayer alice = FakePlayer.get(level, new GameProfile(ALICE, "Alice"));
			alice.snapTo(at.x, at.y, at.z, 90, 0);
			s.getPlayerList().broadcastAll(ClientboundPlayerInfoUpdatePacket.createPlayerInitializing(List.of(alice)));
			level.addNewPlayer(alice);
		});

		boolean arrived = ClientGameTest.waitUntil(context, () -> context.computeOnClient(client ->
				client.level.players().stream().anyMatch(p -> p.getUUID().equals(ALICE))), 60);
		report.check("A second player on the server reaches the client", arrived);
		context.waitTicks(2);
		List<String> warnings = context.computeOnClient(client -> femboy().modules().get(PlayerAlertModule.class).recentWarnings());
		report.check("Player Alert warns when the player comes into range", !warnings.isEmpty() && warnings.getFirst().startsWith("Alice")
				&& warnings.getFirst().contains("in range"), warnings.toString());

		var entry = context.computeOnClient(client -> femboy().modules().get(PlayerFinderModule.class).entries().stream()
				.filter(e -> e.id().equals(ALICE)).findFirst().orElse(null));
		report.check("Player Finder lists the player in range with their real position", entry != null
				&& entry.knowledge() == PlayerFinderModule.Knowledge.IN_RANGE && entry.position().distanceTo(at) < 0.5, String.valueOf(entry));
		report.check("Entity ESP highlights the other player", context.computeOnClient(client -> femboy().modules().get(EntityEspModule.class)
				.tracked().stream().map(Entity::getUUID).anyMatch(ALICE::equals)));
		context.takeScreenshot("multiplayer-player-alert");

		// Out of range while still online: the entity goes, the player-list entry stays.
		server.runOnServer(s -> s.overworld().removePlayerImmediately(FakePlayer.get(s.overworld(), new GameProfile(ALICE, "Alice")),
				Entity.RemovalReason.DISCARDED));
		boolean gone = ClientGameTest.waitUntil(context, () -> context.computeOnClient(client ->
				client.level.players().stream().noneMatch(p -> p.getUUID().equals(ALICE))), 60);
		context.waitTicks(2);
		warnings = context.computeOnClient(client -> femboy().modules().get(PlayerAlertModule.class).recentWarnings());
		report.check("Player Alert reports when the player leaves range (setting on)", gone && warnings.getFirst().equals("Alice left your range"),
				warnings.toString());
		entry = context.computeOnClient(client -> femboy().modules().get(PlayerFinderModule.class).entries().stream()
				.filter(e -> e.id().equals(ALICE)).findFirst().orElse(null));
		report.check("Player Finder keeps the last seen position once the player is out of range", entry != null
				&& entry.knowledge() == PlayerFinderModule.Knowledge.LAST_SEEN && entry.position().distanceTo(at) < 0.5, String.valueOf(entry));
		boolean trackedLastSeen = ClientGameTest.waitUntil(context, () -> context.computeOnClient(client -> femboy().modules().get(TrackersModule.class)
				.lastIndicators().stream().anyMatch(i -> i.source() == TrackersModule.Source.LAST_SEEN && i.name().equals("Alice")
						&& i.label().contains("last seen"))), 20);
		report.check("Trackers point to the last seen position, labelled with its age", trackedLastSeen,
				context.computeOnClient(client -> femboy().modules().get(TrackersModule.class).lastIndicators().toString()));

		server.runOnServer(s -> s.getPlayerList().broadcastAll(new ClientboundPlayerInfoRemovePacket(List.of(ALICE))));
		boolean offline = ClientGameTest.waitUntil(context, () -> context.computeOnClient(client -> femboy().modules().get(PlayerFinderModule.class)
				.entries().stream().noneMatch(e -> e.id().equals(ALICE))), 40);
		report.check("A player who logs off disappears from Player Finder", offline);
		context.waitTicks(10);
		report.check("Trackers drop a player who logged off", context.computeOnClient(client -> femboy().modules().get(TrackersModule.class)
				.lastIndicators().stream().noneMatch(i -> i.name().equals("Alice"))));
	}

	private static int damage(TestDedicatedServerContext server, int slot) {
		return server.computeOnServer(s -> serverPlayer(s).getInventory().getItem(slot).getDamageValue());
	}

	private static void hit(ClientGameTestContext context) {
		ClientGameTest.waitUntil(context, () -> context.computeOnClient(client -> client.hitResult instanceof net.minecraft.world.phys.EntityHitResult), 20);
		context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
		context.waitTicks(3);
	}

	private static <T> void swapSetting(ClientGameTestContext context, String id, T value) {
		AdvancedFeaturesTest.set(context, AutoAttributeSwapModule.class, id, value);
	}

	/**
	 * Durability is the server's own record of which item hit: a hit costs the held weapon durability, so
	 * a damaged axe and an untouched sword prove the server used the axe for that hit.
	 */
	private static void attributeSwap(ClientGameTestContext context, TestDedicatedServerContext server, BlockPos base, TestReport report) {
		server.runCommand("gamemode survival @a");
		server.runCommand("item replace entity @a hotbar.0 with minecraft:diamond_sword");
		server.runCommand("item replace entity @a hotbar.1 with minecraft:iron_axe");
		server.runCommand("summon zombie " + center(base.offset(0, 1, 2)) + " {NoAI:1b,NoGravity:1b,Silent:1b,PersistenceRequired:1b,CustomName:'Dummy',"
				+ "attributes:[{id:\"minecraft:max_health\",base:1000.0}],Health:1000.0f}");
		server.runCommand("tp @a " + center(base.above()) + " 0 15");
		context.runOnClient(client -> client.player.getInventory().setSelectedSlot(0));
		context.waitTicks(20);
		context.runOnClient(client -> femboy().modules().get(AutoAttributeSwapModule.class).settings().resetAll());
		context.runOnClient(client -> femboy().modules().setEnabled(femboy().modules().get(AutoAttributeSwapModule.class), true));
		context.waitTicks(2);

		int sword0 = damage(server, 0);
		int axe0 = damage(server, 1);
		hit(context);
		int sword1 = damage(server, 0);
		int axe1 = damage(server, 1);
		report.check("Attribute swap: by default the axe is only used against a raised shield (no swap on a normal mob)",
				sword1 > sword0 && axe1 == axe0, "sword " + sword0 + "->" + sword1 + ", axe " + axe0 + "->" + axe1);

		swapSetting(context, "axe_shield_only", false);
		context.waitTicks(25);
		hit(context);
		int sword2 = damage(server, 0);
		int axe2 = damage(server, 1);
		var record = context.computeOnClient(client -> femboy().modules().get(AutoAttributeSwapModule.class).lastSwap());
		int clientSlot = context.computeOnClient(client -> client.player.getInventory().getSelectedSlot());
		int serverSlot = server.computeOnServer(s -> serverPlayer(s).getInventory().getSelectedSlot());
		report.check("Attribute swap: the server applied the hit with the axe (axe lost durability, sword did not)",
				axe2 > axe1 && sword2 == sword1, "sword " + sword1 + "->" + sword2 + ", axe " + axe1 + "->" + axe2);
		report.check("Attribute swap: switched back to the sword in the same tick, on client and server", clientSlot == 0 && serverSlot == 0
				&& record != null && record.usedForHit() && record.restored() && record.toSlot() == 1, "client " + clientSlot + ", server " + serverSlot + ", " + record);
		context.takeScreenshot("combat-attribute-swap-hud");

		// Past the target's 10-tick damage immunity (so the hit lands), but within the axe's 1 s cooldown.
		context.waitTicks(10);
		hit(context);
		int sword3 = damage(server, 0);
		int axe3 = damage(server, 1);
		String skip = context.computeOnClient(client -> femboy().modules().get(AutoAttributeSwapModule.class).lastSkip());
		report.check("Attribute swap: no second swap during the axe's cooldown (1 s)", axe3 == axe2 && sword3 > sword2 && "Axe on cooldown".equals(skip),
				"sword " + sword2 + "->" + sword3 + ", axe " + axe2 + "->" + axe3 + ", skip: " + skip);

		swapSetting(context, "axe_cooldown", 3000.0);
		context.waitTicks(25);
		hit(context);
		int axe4 = damage(server, 1);
		hit(context);
		context.waitTicks(25);
		hit(context);
		int axe5 = damage(server, 1);
		report.check("Attribute swap: a longer per-item cooldown (3 s) blocks swaps for longer", axe4 > axe3 && axe5 == axe4,
				"axe " + axe3 + "->" + axe4 + "->" + axe5);

		swapSetting(context, "axe_cooldown", 0.0);
		swapSetting(context, "swap_back_delay", 3.0);
		context.waitTicks(25);
		context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
		context.waitTick();
		int heldRightAfter = context.computeOnClient(client -> client.player.getInventory().getSelectedSlot());
		context.waitTicks(6);
		int heldLater = context.computeOnClient(client -> client.player.getInventory().getSelectedSlot());
		int serverLater = server.computeOnServer(s -> serverPlayer(s).getInventory().getSelectedSlot());
		report.check("Attribute swap: with a swap-back delay the axe stays selected for those ticks, then the sword returns",
				heldRightAfter == 1 && heldLater == 0 && serverLater == 0, "right after " + heldRightAfter + ", later " + heldLater + ", server " + serverLater);

		server.runCommand("item replace entity @a hotbar.1 with minecraft:air");
		swapSetting(context, "swap_back_delay", 0.0);
		context.waitTicks(25);
		hit(context);
		String missing = context.computeOnClient(client -> femboy().modules().get(AutoAttributeSwapModule.class).lastSkip());
		report.check("Attribute swap: a missing item is skipped safely", "no axe in hotbar".equals(missing)
				&& context.computeOnClient(client -> client.player.getInventory().getSelectedSlot()) == 0, String.valueOf(missing));

		context.runOnClient(client -> femboy().modules().setEnabled(femboy().modules().get(AutoAttributeSwapModule.class), false));
		context.runOnClient(client -> femboy().modules().get(AutoAttributeSwapModule.class).settings().resetAll());
		server.runCommand("kill @e[type=minecraft:zombie,name=Dummy]");
		// Creative players cannot break blocks while holding a sword; later tests break blocks.
		server.runCommand("clear @a");
		server.runCommand("gamemode creative @a");
		context.waitTicks(5);
	}

	private static void freeCamera(ClientGameTestContext context, TestDedicatedServerContext server, BlockPos base, TestReport report) {
		server.runCommand("tp @a " + center(base.above()) + " 0 90");
		context.waitTicks(10);
		Vec3 serverBefore = server.computeOnServer(s -> serverPlayer(s).position());
		Vec3 clientBefore = context.computeOnClient(client -> client.player.position());

		context.runOnClient(client -> femboy().modules().setEnabled(femboy().modules().get(FreeCameraModule.class), true));
		context.waitTicks(2);
		report.check("Free Camera runs on a dedicated server", context.computeOnClient(client ->
				femboy().modules().get(FreeCameraModule.class).isRunning() && client.getCameraEntity() != client.player));
		Vec3 cameraStart = context.computeOnClient(client -> femboy().modules().get(FreeCameraModule.class).cameraPosition());
		context.getInput().holdKeyFor(options -> options.keyUp, 15);
		context.waitTicks(2);
		Vec3 camera = context.computeOnClient(client -> femboy().modules().get(FreeCameraModule.class).cameraPosition());
		report.check("The free camera flies on the server", camera.distanceTo(cameraStart) > 5, String.format("moved %.2f", camera.distanceTo(cameraStart)));

		// A block right under the camera, far from the player: the crosshair aims at it, a click must not break it.
		BlockPos underCamera = new BlockPos((int) Math.floor(camera.x), base.getY(), (int) Math.floor(camera.z));
		server.runOnServer(s -> s.overworld().setBlockAndUpdate(underCamera, Blocks.STONE.defaultBlockState()));
		boolean aimed = ClientGameTest.waitUntil(context, () -> context.computeOnClient(client -> client.hitResult instanceof BlockHitResult hit
				&& hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(underCamera)), 20);
		report.check("The crosshair aims at the block under the camera", aimed);
		context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
		context.waitTick();
		// The server would reject a dig this far away anyway; what matters is that the client never attacks:
		// an attack always swings the arm, so no swing means nothing was sent.
		boolean swung = context.computeOnClient(client -> client.player.swinging);
		context.waitTicks(4);
		report.check("Clicking in free camera does not attack (no swing, the block stays)", !swung
				&& server.computeOnServer(s -> s.overworld().getBlockState(underCamera).is(Blocks.STONE)), "swung=" + swung);

		// Position updates keep flowing: a rotation made during free camera reaches the server.
		context.runOnClient(client -> client.player.setYRot(90));
		boolean synced = ClientGameTest.waitUntil(context, () -> Math.abs(server.computeOnServer(s -> serverPlayer(s).getYRot()) - 90) < 1, 20);
		report.check("The server keeps receiving the player's updates during free camera", synced,
				String.format("server yaw %.1f", (float) server.<Float, RuntimeException>computeOnServer(s -> serverPlayer(s).getYRot())));
		Vec3 serverDuring = server.computeOnServer(s -> serverPlayer(s).position());
		Vec3 clientDuring = context.computeOnClient(client -> client.player.position());
		report.check("The player stays put on the server and on the client", serverDuring.distanceTo(serverBefore) < 0.1 && clientDuring.distanceTo(clientBefore) < 0.1,
				String.format("server moved %.3f, client moved %.3f", serverDuring.distanceTo(serverBefore), clientDuring.distanceTo(clientBefore)));
		context.takeScreenshot("multiplayer-free-camera");

		context.runOnClient(client -> femboy().modules().setEnabled(femboy().modules().get(FreeCameraModule.class), false));
		context.runOnClient(client -> client.player.setYRot(0));
		context.waitTicks(5);
		report.check("Disabling the free camera returns the view to the player", context.computeOnClient(client -> client.getCameraEntity() == client.player));
		Vec3 serverAfter = server.computeOnServer(s -> serverPlayer(s).position());
		Vec3 clientAfter = context.computeOnClient(client -> client.player.position());
		report.check("No rubber-band: server and client agree on the position after the camera returns", serverAfter.distanceTo(clientAfter) < 0.1
				&& clientAfter.distanceTo(clientBefore) < 0.1, String.format("server-client difference %.3f", serverAfter.distanceTo(clientAfter)));

		// Control: with the camera back, the same click does reach the server (the platform block under the player).
		BlockPos underPlayer = base;
		boolean aimedAtFeet = ClientGameTest.waitUntil(context, () -> context.computeOnClient(client -> client.hitResult instanceof BlockHitResult hit
				&& hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(underPlayer)), 20);
		context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
		context.waitTick();
		boolean swungAfter = context.computeOnClient(client -> client.player.swinging);
		context.waitTicks(4);
		String state = server.computeOnServer(s -> s.overworld().getBlockState(underPlayer) + ", held " + serverPlayer(s).getMainHandItem()
				+ ", mode " + serverPlayer(s).gameMode() + ", at " + serverPlayer(s).position());
		String aim = context.computeOnClient(client -> String.valueOf(client.hitResult instanceof BlockHitResult hit ? hit.getBlockPos() : client.hitResult)
				+ " pitch " + client.player.getXRot());
		report.check("After the camera returns, clicking works again (control: swing, block broken)", aimedAtFeet && swungAfter
				&& server.computeOnServer(s -> s.overworld().getBlockState(underPlayer).isAir()),
				"swung=" + swungAfter + ", aimed=" + aimedAtFeet + " (" + aim + ", expected " + underPlayer + "), server: " + state);
	}
}
