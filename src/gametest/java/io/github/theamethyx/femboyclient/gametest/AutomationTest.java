package io.github.theamethyx.femboyclient.gametest;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.lwjgl.glfw.GLFW;

import com.mojang.authlib.GameProfile;

import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

import io.github.theamethyx.femboyclient.client.FemboyClient;
import io.github.theamethyx.femboyclient.client.combat.Rotations;
import io.github.theamethyx.femboyclient.client.modules.AimAssistModule;
import io.github.theamethyx.femboyclient.client.modules.AutoStunSlamModule;
import io.github.theamethyx.femboyclient.client.modules.AutoTotemModule;
import io.github.theamethyx.femboyclient.client.modules.FlightModule;
import io.github.theamethyx.femboyclient.client.modules.KillAuraModule;
import io.github.theamethyx.femboyclient.client.modules.ReachModule;
import io.github.theamethyx.femboyclient.client.modules.TriggerBotModule;
import io.github.theamethyx.femboyclient.client.modules.VelocityModule;
import io.github.theamethyx.femboyclient.core.module.Module;
import io.github.theamethyx.femboyclient.core.setting.Setting;

/** The automation modules in singleplayer, each checked against what the server did. */
final class AutomationTest {
	private static final UUID BOB = UUID.fromString("00000000-0000-0000-0000-00000000b0b0");

	private AutomationTest() {
	}

	static FemboyClient femboy() {
		return FemboyClient.get();
	}

	private static void enable(ClientGameTestContext context, Class<? extends Module> type, boolean on) {
		context.runOnClient(client -> femboy().modules().setEnabled(femboy().modules().get(type), on));
	}

	@SuppressWarnings("unchecked")
	private static <T> void set(ClientGameTestContext context, Class<? extends Module> type, String id, T value) {
		context.runOnClient(client -> ((Setting<T>) femboy().modules().get(type).settings().get(id)).set(value));
	}

	private static float huskHealth(TestSingleplayerContext singleplayer) {
		return singleplayer.getServer().computeOnServer(s -> s.overworld().getEntitiesOfClass(Husk.class,
				s.getPlayerList().getPlayers().getFirst().getBoundingBox().inflate(16)).stream().filter(Husk::isAlive).findFirst().map(Husk::getHealth).orElse(-1f));
	}

	/** Replaces all mobs with one husk. Killed mobs stay for a death animation (20 ticks), so wait for them to go. */
	private static void freshHusk(ClientGameTestContext context, TestSingleplayerContext singleplayer, String position) {
		singleplayer.getServer().runCommand("kill @e[type=!player]");
		context.waitTicks(25);
		singleplayer.getServer().runCommand("summon husk " + position + " {NoAI:1b,PersistenceRequired:1b,Silent:1b}");
	}

	static void run(ClientGameTestContext context, TestSingleplayerContext singleplayer, TestReport report) {
		var server = singleplayer.getServer();
		boolean test = context.computeOnClient(client -> femboy().modules().environment().testEnvironment());
		report.check("Singleplayer counts as a test environment, so automation modules can run", test);
		server.runCommand("gamemode survival @a");
		server.runCommand("difficulty normal");
		server.runCommand("clear @a");

		killAura(context, singleplayer, report);
		triggerBotAndAimAssist(context, singleplayer, report);
		reach(context, singleplayer, report);
		velocity(context, singleplayer, report);
		flight(context, singleplayer, report);
		autoTotem(context, singleplayer, report);
		stunSlam(context, singleplayer, report);
		spearReach(context, singleplayer, report);
		spearLunge(context, singleplayer, report);

		server.runCommand("kill @e[type=!player]");
		server.runCommand("clear @a");
		server.runCommand("gamemode creative @a");
		server.runCommand("effect give @a instant_health 1 5");
		server.runCommand("tp @a 0.5 -60 0.5 0 15");
		context.runOnClient(client -> femboy().modules().disableAll());
		context.waitTicks(3);
	}

	private static void killAura(ClientGameTestContext context, TestSingleplayerContext singleplayer, TestReport report) {
		var server = singleplayer.getServer();
		server.runCommand("item replace entity @a weapon.mainhand with minecraft:iron_sword");
		server.runCommand("tp @a 0.5 -60 0.5 180 0");
		freshHusk(context, singleplayer, "2.5 -60 2.5");
		context.waitTicks(10);
		enable(context, KillAuraModule.class, true);
		boolean hit = ClientGameTest.waitUntil(context, () -> huskHealth(singleplayer) < 20, 60);
		String state = context.computeOnClient(client -> {
			KillAuraModule aura = femboy().modules().get(KillAuraModule.class);
			Entity husk = aura.target();
			double angle = husk == null ? -1 : Rotations.angleTo(client.player, husk.getBoundingBox().getCenter());
			return String.format(Locale.ROOT, "attacks %d, angle to target %.1f°", aura.attacks(), angle);
		});
		enable(context, KillAuraModule.class, false);
		report.check("KillAura turns from facing away to the husk and hits it (server health below 20)", hit && !state.startsWith("attacks 0"),
				"health " + huskHealth(singleplayer) + ", " + state);
	}

	private static void triggerBotAndAimAssist(ClientGameTestContext context, TestSingleplayerContext singleplayer, TestReport report) {
		var server = singleplayer.getServer();
		freshHusk(context, singleplayer, "0.5 -60 3.5");
		server.runCommand("tp @a 0.5 -60 0.5 0 15");
		context.waitTicks(10);
		enable(context, TriggerBotModule.class, true);
		boolean hit = ClientGameTest.waitUntil(context, () -> huskHealth(singleplayer) < 20, 60);
		int attacks = context.computeOnClient(client -> femboy().modules().get(TriggerBotModule.class).attacks());
		enable(context, TriggerBotModule.class, false);
		report.check("Trigger Bot attacks the husk under the crosshair", hit && attacks > 0, "attacks " + attacks + ", health " + huskHealth(singleplayer));

		server.runCommand("tp @a 0.5 -60 0.5 35 15");
		context.waitTicks(3);
		double before = context.computeOnClient(client -> Rotations.angleTo(client.player, new Vec3(0.5, -59, 3.5)));
		float healthBefore = huskHealth(singleplayer);
		enable(context, AimAssistModule.class, true);
		context.waitTicks(30);
		double after = context.computeOnClient(client -> Rotations.angleTo(client.player, new Vec3(0.5, -59, 3.5)));
		boolean onTarget = context.computeOnClient(client -> client.crosshairPickEntity instanceof Husk);
		enable(context, AimAssistModule.class, false);
		report.check("Aim Assist turns the view onto the husk without attacking", before > 25 && onTarget && huskHealth(singleplayer) == healthBefore,
				String.format(Locale.ROOT, "angle %.1f° -> %.1f°, crosshair on husk %s", before, after, onTarget));
	}

	private static void reach(ClientGameTestContext context, TestSingleplayerContext singleplayer, TestReport report) {
		var server = singleplayer.getServer();
		freshHusk(context, singleplayer, "0.5 -60 5.3");
		server.runCommand("tp @a 0.5 -60 0.5 0 0");
		context.waitTicks(10);
		boolean pickedBefore = context.computeOnClient(client -> client.crosshairPickEntity instanceof Husk);
		set(context, ReachModule.class, "entity", 2.0);
		enable(context, ReachModule.class, true);
		context.waitTicks(3);
		double range = context.computeOnClient(client -> client.player.entityInteractionRange());
		boolean picked = ClientGameTest.waitUntil(context, () -> context.computeOnClient(client -> client.crosshairPickEntity instanceof Husk), 10);
		context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
		boolean hit = ClientGameTest.waitUntil(context, () -> huskHealth(singleplayer) < 20, 20);
		enable(context, ReachModule.class, false);
		context.waitTicks(2);
		double restored = context.computeOnClient(client -> client.player.entityInteractionRange());
		context.runOnClient(client -> femboy().modules().resetToDefaults(femboy().modules().get(ReachModule.class)));
		report.check("Reach +2: a husk 4.5 blocks away becomes reachable and a real click hurts it; range returns to 3 when off",
				!pickedBefore && picked && hit && range == 5.0 && restored == 3.0,
				String.format(Locale.ROOT, "picked before %s, range %.1f, picked %s, hit %s, range after %.1f", pickedBefore, range, picked, hit, restored));
	}

	private static double knockbackDistance(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
		var server = singleplayer.getServer();
		server.runCommand("tp @a 0.5 -60 0.5 0 0");
		context.waitTicks(25);
		Vec3 start = context.computeOnClient(client -> client.player.position());
		server.runCommand("damage @p 1 minecraft:mob_attack by @e[type=husk,limit=1]");
		context.waitTicks(10);
		Vec3 end = context.computeOnClient(client -> client.player.position());
		return Math.sqrt(Math.pow(end.x - start.x, 2) + Math.pow(end.z - start.z, 2));
	}

	private static void velocity(ClientGameTestContext context, TestSingleplayerContext singleplayer, TestReport report) {
		freshHusk(context, singleplayer, "0.5 -60 3.5");
		double normal = knockbackDistance(context, singleplayer);
		enable(context, VelocityModule.class, true);
		double reduced = knockbackDistance(context, singleplayer);
		int changed = context.computeOnClient(client -> femboy().modules().get(VelocityModule.class).changed());
		enable(context, VelocityModule.class, false);
		singleplayer.getServer().runCommand("effect give @a instant_health 1 5");
		report.check("Velocity at 0%: a hit that pushes you without it no longer moves you", normal > 0.3 && reduced < 0.05 && changed > 0,
				String.format(Locale.ROOT, "pushed %.2f blocks without, %.2f with (%d updates changed)", normal, reduced, changed));
	}

	private static void flight(ClientGameTestContext context, TestSingleplayerContext singleplayer, TestReport report) {
		var server = singleplayer.getServer();
		server.runCommand("kill @e[type=!player]");
		server.runCommand("tp @a 0.5 -55 0.5 0 0");
		context.waitTicks(2);
		enable(context, FlightModule.class, true);
		context.waitTicks(5);
		double startY = context.computeOnClient(client -> client.player.getY());
		context.waitTicks(20);
		double hoverY = context.computeOnClient(client -> client.player.getY());
		context.getInput().holdKeyFor(options -> options.keyJump, 10);
		double upY = context.computeOnClient(client -> client.player.getY());
		boolean serverAgrees = Math.abs(singleplayer.getServer().computeOnServer(s -> s.getPlayerList().getPlayers().getFirst().getY()) - upY) < 1;
		enable(context, FlightModule.class, false);
		boolean landed = ClientGameTest.waitUntil(context, () -> context.computeOnClient(client -> client.player.onGround()), 100);
		boolean mayfly = context.computeOnClient(client -> client.player.getAbilities().mayfly);
		report.check("Flight: hovers in survival, rises with jump (server agrees), falls again when off and leaves no flying ability",
				Math.abs(hoverY - startY) < 0.05 && upY > hoverY + 2 && serverAgrees && landed && !mayfly,
				String.format(Locale.ROOT, "y %.2f -> %.2f hover, %.2f after jump; server agrees %s; landed %s; mayfly %s", startY, hoverY, upY,
						serverAgrees, landed, mayfly));
		server.runCommand("effect give @a instant_health 1 5");
	}

	private static void autoTotem(ClientGameTestContext context, TestSingleplayerContext singleplayer, TestReport report) {
		var server = singleplayer.getServer();
		server.runCommand("clear @a");
		server.runCommand("item replace entity @a inventory.5 with minecraft:totem_of_undying");
		context.waitTicks(3);
		enable(context, AutoTotemModule.class, true);
		boolean client = ClientGameTest.waitUntil(context, () -> context.computeOnClient(c -> c.player.getOffhandItem().is(Items.TOTEM_OF_UNDYING)), 20);
		context.waitTicks(3);
		boolean onServer = server.computeOnServer(s -> s.getPlayerList().getPlayers().getFirst().getOffhandItem().is(Items.TOTEM_OF_UNDYING));
		boolean leftInventory = server.computeOnServer(s -> !s.getPlayerList().getPlayers().getFirst().getInventory().getItem(14).is(Items.TOTEM_OF_UNDYING));
		enable(context, AutoTotemModule.class, false);
		report.check("AutoTotem moves a totem from the inventory into the offhand (client and server)", client && onServer && leftInventory,
				"client " + client + ", server " + onServer + ", slot emptied " + leftInventory);
	}

	/** A fake player holding up a shield, ticked on the server so the shield really counts as raised. */
	private static void stunSlam(ClientGameTestContext context, TestSingleplayerContext singleplayer, TestReport report) {
		var server = singleplayer.getServer();
		server.runCommand("clear @a");
		server.runCommand("kill @e[type=!player]");
		server.runCommand("item replace entity @a hotbar.0 with minecraft:iron_sword");
		server.runCommand("item replace entity @a hotbar.1 with minecraft:iron_axe");
		server.runCommand("item replace entity @a hotbar.2 with minecraft:mace");
		// Falling with the mace in hand, as you would; the sequence switches to the axe and back by itself.
		context.runOnClient(client -> client.player.getInventory().setSelectedSlot(2));
		server.runOnServer(s -> {
			ServerLevel level = s.overworld();
			TargetPlayer bob = new TargetPlayer(level, new GameProfile(BOB, "Bob"));
			target = bob;
			bob.snapTo(0.5, -60, 2.5, 180, 0);
			bob.setHealth(20);
			bob.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
			// A full mace smash could kill a 20-health player; Resistance keeps it alive while damage still shows.
			bob.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.RESISTANCE, 2000, 2));
			s.getPlayerList().broadcastAll(ClientboundPlayerInfoUpdatePacket.createPlayerInitializing(List.of(bob)));
			level.addNewPlayer(bob);
			bob.startUsingItem(InteractionHand.OFF_HAND);
		});
		for (int i = 0; i < 10; i++) {
			server.runOnServer(AutomationTest::tickBob);
			context.waitTick();
		}

		boolean serverBlocking = server.computeOnServer(s -> bob(s).isBlocking());
		boolean clientBlocking = context.computeOnClient(client -> client.level.players().stream()
				.anyMatch(p -> p.getUUID().equals(BOB) && p.isBlocking()));
		report.check("Test target holds up a shield (server and client agree)", serverBlocking && clientBlocking,
				"server " + serverBlocking + ", client " + clientBlocking);

		enable(context, AutoStunSlamModule.class, true);
		server.runCommand("tp @p 0.5 -54 0.5 0 50");
		boolean done = false;
		double maxFall = 0;
		boolean sawBob = false;
		for (int i = 0; i < 60 && !done; i++) {
			server.runOnServer(AutomationTest::tickBob);
			context.waitTick();
			done = context.computeOnClient(client -> femboy().modules().get(AutoStunSlamModule.class).completed() > 0);
			maxFall = Math.max(maxFall, context.computeOnClient(client -> client.player.fallDistance));
			sawBob |= context.computeOnClient(client -> client.crosshairPickEntity != null && BOB.equals(client.crosshairPickEntity.getUUID()));
		}

		context.waitTicks(3);
		String result = context.computeOnClient(client -> femboy().modules().get(AutoStunSlamModule.class).lastResult());
		float health = server.computeOnServer(s -> bob(s).getHealth());
		boolean shieldDisabled = server.computeOnServer(s -> bob(s).getCooldowns().isOnCooldown(new ItemStack(Items.SHIELD)));
		int clientSlot = context.computeOnClient(client -> client.player.getInventory().getSelectedSlot());
		int serverSlot = server.computeOnServer(s -> s.getPlayerList().getPlayers().stream().filter(p -> !(p instanceof FakePlayer))
				.findFirst().orElseThrow().getInventory().getSelectedSlot());
		report.check("Auto StunSlam falling onto a shielding player: axe disables the shield, mace damages through it, slot restored",
				done && shieldDisabled && health < 20 && clientSlot == 2 && serverSlot == 2,
				String.format(Locale.ROOT, "%s; shield disabled %s; health 20 -> %.1f; slot client %d server %d (mace = 2); max fall %.1f, crosshair on Bob %s",
						result, shieldDisabled, health, clientSlot, serverSlot, maxFall, sawBob));
		enable(context, AutoStunSlamModule.class, false);
		server.runOnServer(s -> s.overworld().removePlayerImmediately(bob(s), Entity.RemovalReason.DISCARDED));
	}

	private static TargetPlayer target;

	private static int serverSlot(TestSingleplayerContext singleplayer) {
		return singleplayer.getServer().computeOnServer(s -> s.getPlayerList().getPlayers().stream().filter(p -> !(p instanceof FakePlayer))
				.findFirst().orElseThrow().getInventory().getSelectedSlot());
	}

	private static void swapRule(ClientGameTestContext context, String id, Object value) {
		set(context, io.github.theamethyx.femboyclient.client.modules.AutoAttributeSwapModule.class, id, value);
	}

	/** Spear reach: a husk 3.5 blocks away is out of sword reach; with the rule a click jabs it with the spear. */
	private static void spearReach(ClientGameTestContext context, TestSingleplayerContext singleplayer, TestReport report) {
		var server = singleplayer.getServer();
		server.runCommand("clear @a");
		server.runCommand("item replace entity @a hotbar.0 with minecraft:iron_sword");
		server.runCommand("item replace entity @a hotbar.1 with minecraft:iron_spear");
		context.runOnClient(client -> client.player.getInventory().setSelectedSlot(0));
		freshHusk(context, singleplayer, "0.5 -60 4.3");
		server.runCommand("tp @p 0.5 -60 0.5 0 5");
		context.waitTicks(25);
		context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
		context.waitTicks(25);
		float withoutRule = huskHealth(singleplayer);

		var swap = io.github.theamethyx.femboyclient.client.modules.AutoAttributeSwapModule.class;
		enable(context, swap, true);
		swapRule(context, "spear_enabled", true);
		context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
		boolean hit = ClientGameTest.waitUntil(context, () -> huskHealth(singleplayer) < 20, 20);
		context.waitTicks(2);
		int clientSlot = context.computeOnClient(client -> client.player.getInventory().getSelectedSlot());
		String last = context.computeOnClient(client -> String.valueOf(femboy().modules().get(swap).lastSwap()));
		report.check("Spear reach: out of sword reach a click does nothing; with the rule it jabs with the spear and returns to the sword",
				withoutRule == 20 && hit && clientSlot == 0 && serverSlot(singleplayer) == 0,
				String.format(Locale.ROOT, "without rule health %.1f; with rule hit %s (health %.1f); slot client %d server %d; %s",
						withoutRule, hit, huskHealth(singleplayer), clientSlot, serverSlot(singleplayer), last));
		swapRule(context, "spear_enabled", false);
		enable(context, swap, false);
	}

	/** Spear lunge: holding bread and the attack button, with a Lunge spear in the hotbar, dashes forward. */
	private static void spearLunge(ClientGameTestContext context, TestSingleplayerContext singleplayer, TestReport report) {
		var server = singleplayer.getServer();
		server.runCommand("kill @e[type=!player]");
		server.runCommand("clear @a");
		server.runCommand("item replace entity @a hotbar.2 with minecraft:iron_spear[enchantments={\"minecraft:lunge\":3}]");
		server.runCommand("item replace entity @a hotbar.3 with minecraft:bread 16");
		server.runCommand("effect give @a saturation 60 10");
		context.runOnClient(client -> client.player.getInventory().setSelectedSlot(3));
		server.runCommand("tp @p 0.5 -60 -20.5 0 0");
		context.waitTicks(25);

		Vec3 start = context.computeOnClient(client -> client.player.position());
		context.getInput().holdMouseFor(GLFW.GLFW_MOUSE_BUTTON_LEFT, 20);
		context.waitTicks(10);
		double withoutRule = context.computeOnClient(client -> client.player.position().distanceTo(start));

		var swap = io.github.theamethyx.femboyclient.client.modules.AutoAttributeSwapModule.class;
		enable(context, swap, true);
		swapRule(context, "spear_lunge_enabled", true);
		Vec3 before = context.computeOnClient(client -> client.player.position());
		context.getInput().holdMouseFor(GLFW.GLFW_MOUSE_BUTTON_LEFT, 20);
		context.waitTicks(10);
		Vec3 after = context.computeOnClient(client -> client.player.position());
		Vec3 serverAfter = server.computeOnServer(s -> s.getPlayerList().getPlayers().getFirst().position());
		int lunges = context.computeOnClient(client -> femboy().modules().get(swap).lunges());
		int clientSlot = context.computeOnClient(client -> client.player.getInventory().getSelectedSlot());
		double forward = after.z - before.z;
		report.check("Spear lunge: holding attack with bread jabs the Lunge spear repeatedly and dashes forward; slot stays on the bread",
				withoutRule < 0.1 && lunges >= 3 && forward > 3 && after.distanceTo(serverAfter) < 1 && clientSlot == 3 && serverSlot(singleplayer) == 3,
				String.format(Locale.ROOT, "without rule moved %.2f; with rule %d lunges, moved %.1f blocks forward (server agrees within %.2f); slot client %d server %d",
						withoutRule, lunges, forward, after.distanceTo(serverAfter), clientSlot, serverSlot(singleplayer)));
		swapRule(context, "spear_lunge_enabled", false);
		enable(context, swap, false);
		server.runCommand("effect clear @a");
	}

	private static FakePlayer bob(MinecraftServer server) {
		return target;
	}

	/**
	 * Fabric's FakePlayer is always invulnerable, so the server would ignore every hit before checking the shield.
	 * This test player takes damage like a normal survival player.
	 */
	private static final class TargetPlayer extends FakePlayer {
		TargetPlayer(ServerLevel level, GameProfile profile) {
			super(level, profile);
		}

		@Override
		public boolean isInvulnerableTo(ServerLevel level, net.minecraft.world.damagesource.DamageSource source) {
			return false;
		}
	}

	private static void tickBob(MinecraftServer server) {
		FakePlayer bob = bob(server);
		bob.doTick();
		if (!bob.isUsingItem() && !bob.getCooldowns().isOnCooldown(bob.getOffhandItem())) {
			bob.startUsingItem(InteractionHand.OFF_HAND);
		}
	}
}
