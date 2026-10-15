package io.github.theamethyx.femboyclient.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.lwjgl.glfw.GLFW;

import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.projectile.throwableitemprojectile.Snowball;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

import io.github.theamethyx.femboyclient.client.FemboyClient;
import io.github.theamethyx.femboyclient.client.combat.CombatTracker;
import io.github.theamethyx.femboyclient.client.combat.Trajectory;
import io.github.theamethyx.femboyclient.client.hud.HudLine;
import io.github.theamethyx.femboyclient.client.module.ClientHooks;
import io.github.theamethyx.femboyclient.client.modules.CombatInfoModule;
import io.github.theamethyx.femboyclient.client.modules.EffectsModule;
import io.github.theamethyx.femboyclient.client.modules.ExplosionInfoModule;
import io.github.theamethyx.femboyclient.client.modules.HealthArmorModule;
import io.github.theamethyx.femboyclient.client.modules.HitboxesModule;
import io.github.theamethyx.femboyclient.client.modules.ItemCountersModule;
import io.github.theamethyx.femboyclient.client.modules.MovementInfoModule;
import io.github.theamethyx.femboyclient.client.modules.TargetHudModule;
import io.github.theamethyx.femboyclient.client.modules.TrajectoriesModule;
import io.github.theamethyx.femboyclient.client.modules.WeaponInfoModule;
import io.github.theamethyx.femboyclient.client.profile.Presets;
import io.github.theamethyx.femboyclient.core.module.Module;

/** Combat, movement, inventory and visual information modules, and the presets, checked against what the game does. */
final class InfoModulesTest {
	private InfoModulesTest() {
	}

	static FemboyClient femboy() {
		return FemboyClient.get();
	}

	static ServerPlayer serverPlayer(TestSingleplayerContext singleplayer) {
		return singleplayer.getServer().computeOnServer(server -> server.getPlayerList().getPlayers().getFirst());
	}

	static List<String> hud(ClientGameTestContext context, Class<? extends Module> type) {
		return context.computeOnClient(client -> {
			List<HudLine> lines = new ArrayList<>();
			((ClientHooks.HudBlock) femboy().modules().get(type)).buildHud(lines);
			return lines.stream().map(line -> line.label() + ": " + line.value()).toList();
		});
	}

	private static void enable(ClientGameTestContext context, Class<? extends Module> type, boolean on) {
		context.runOnClient(client -> femboy().modules().setEnabled(femboy().modules().get(type), on));
	}

	static void presets(ClientGameTestContext context, TestReport report) {
		List<String> warnings = context.computeOnClient(client -> {
			List<String> all = new ArrayList<>();
			for (Presets.Preset preset : Presets.all()) {
				List<String> local = new ArrayList<>();
				femboy().modules().sanitizeModules(preset.modules(), local);
				local.forEach(w -> all.add(preset.profile() + ": " + w));
			}

			return all;
		});
		report.check("All 10 presets use only existing modules and valid setting values",
				warnings.isEmpty() && context.computeOnClient(client -> Presets.all().size()) == 10, warnings.toString());

		String switching = context.computeOnClient(client -> {
			var config = femboy().config();
			for (Presets.Preset preset : Presets.all()) {
				String problem = config.createProfileFrom(preset.profile(), preset.modules());
				if (problem != null) return "install " + preset.profile() + ": " + problem;
			}

			StringBuilder out = new StringBuilder();
			for (String name : List.of("sword_pvp", "crystal_pvp", "elytra_pvp", "sword_pvp")) {
				config.switchProfile(name);
				Set<String> on = new TreeSet<>();
				femboy().modules().all().stream().filter(Module::isEnabled).forEach(m -> on.add(m.id()));
				out.append(name).append('=').append(on).append(' ');
			}

			return out.toString().trim();
		});
		report.check("Switching preset profiles turns on exactly each preset's modules (no leftovers)", switching.equals(
				"sword_pvp=[combat_info, health_armor, target_hud, weapon_info] "
						+ "crystal_pvp=[explosion_info, health_armor, item_counters, target_hud] "
						+ "elytra_pvp=[elytra_info, health_armor, item_counters, target_hud] "
						+ "sword_pvp=[combat_info, health_armor, target_hud, weapon_info]"), switching);

		String reset = context.computeOnClient(client -> {
			var combat = femboy().modules().get(CombatInfoModule.class);
			@SuppressWarnings("unchecked")
			var accuracy = (io.github.theamethyx.femboyclient.core.setting.Setting<Boolean>) combat.settings().get("accuracy");
			accuracy.set(false);
			femboy().modules().setEnabled(femboy().modules().get(HitboxesModule.class), true);
			Presets.Preset sword = Presets.all().getFirst();
			String problem = femboy().config().overwriteProfile(sword.profile(), sword.modules());
			return problem != null ? problem : "accuracy=" + accuracy.get() + " hitboxes=" + femboy().modules().get(HitboxesModule.class).isEnabled();
		});
		report.check("Resetting the active preset profile restores its settings and turns extra modules off", reset.equals("accuracy=true hitboxes=false"), reset);

		context.runOnClient(client -> {
			femboy().config().switchProfile("default");
			Presets.all().forEach(preset -> femboy().config().deleteProfile(preset.profile()));
			femboy().modules().disableAll();
		});
	}

	static void combat(ClientGameTestContext context, TestSingleplayerContext singleplayer, TestReport report) {
		var server = singleplayer.getServer();
		server.runCommand("kill @e[type=!player]");
		server.runCommand("tp @a 0.5 -60 0.5 0 15");
		// A husk, because zombies burn in daylight: fire damage gives them brief damage immunity, so a punch
		// right after a fire tick really does not land (the tracker then correctly counts it as not landed).
		server.runCommand("summon husk 0.5 -60 3.5 {NoAI:1b,PersistenceRequired:1b,Silent:1b}");
		enable(context, CombatInfoModule.class, true);
		enable(context, TargetHudModule.class, true);
		context.waitTicks(15);

		ClientGameTest.waitUntil(context, () -> context.computeOnClient(client -> client.hitResult instanceof net.minecraft.world.phys.EntityHitResult), 20);
		context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
		context.waitTick();
		int cps = context.computeOnClient(client -> femboy().combat().leftCps());
		ClientGameTest.waitUntil(context, () -> context.computeOnClient(client -> femboy().combat().landed() == 1), 15);
		String first = context.computeOnClient(client -> {
			CombatTracker t = femboy().combat();
			return String.format(Locale.ROOT, "attacks=%d landed=%d combo=%d cps=%d reach=%.2f", t.attacks(), t.landed(), t.combo(), cps, t.lastReach());
		});
		report.check("A real left click on a mob counts a click, an attack, a landed hit and the reach (2.70 m)",
				first.startsWith("attacks=1 landed=1 combo=1 cps=1 reach=2.7"), first);

		// Mobs ignore a second hit for 10 ticks after one lands (invulnerability frames).
		context.waitTicks(20);
		ClientGameTest.waitUntil(context, () -> context.computeOnClient(client -> client.hitResult instanceof net.minecraft.world.phys.EntityHitResult), 20);
		context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
		ClientGameTest.waitUntil(context, () -> context.computeOnClient(client -> femboy().combat().landed() == 2), 15);
		int combo = context.computeOnClient(client -> femboy().combat().combo());
		String hits = context.computeOnClient(client -> "attacks " + femboy().combat().attacks() + ", landed " + femboy().combat().landed());
		server.runCommand("gamemode survival @a");
		server.runCommand("damage @p 1 minecraft:generic");
		context.waitTicks(3);
		int afterHurt = context.computeOnClient(client -> femboy().combat().combo());
		server.runCommand("gamemode creative @a");
		report.check("Combo counts landed hits in a row and ends when you take damage", combo == 2 && afterHurt == 0,
				"after 2 hits " + combo + " (" + hits + "), after taking damage " + afterHurt);

		String target = context.computeOnClient(client -> {
			TargetHudModule hud = femboy().modules().get(TargetHudModule.class);
			return hud.target() instanceof Zombie ? "husk" : String.valueOf(hud.target());
		});
		context.takeScreenshot("combat-target-hud");
		context.runOnClient(client -> femboy().modules().get(TargetHudModule.class).toggleLock());
		server.runCommand("tp @a 0.5 -60 0.5 180 0");
		context.waitTicks(3);
		String locked = context.computeOnClient(client -> {
			TargetHudModule hud = femboy().modules().get(TargetHudModule.class);
			return hud.isLocked() + " " + (hud.target() instanceof Zombie);
		});
		context.runOnClient(client -> femboy().modules().get(TargetHudModule.class).toggleLock());
		context.waitTicks(1);
		boolean unlocked = context.computeOnClient(client -> !femboy().modules().get(TargetHudModule.class).isLocked());
		report.check("Target HUD shows the mob under the crosshair, keeps a locked target when looking away, and unlocks",
				target.equals("husk") && locked.equals("true true") && unlocked, target + ", locked: " + locked + ", unlocked " + unlocked);

		// Smash bonus formula against the server's own MaceItem.getAttackDamageBonus.
		String mace = server.computeOnServer(s -> {
			ServerPlayer player = s.getPlayerList().getPlayers().getFirst();
			Zombie zombie = s.overworld().getEntitiesOfClass(Zombie.class, player.getBoundingBox().inflate(10)).getFirst();
			StringBuilder out = new StringBuilder();
			for (double fall : new double[] {2, 3, 5, 8, 12, 30}) {
				player.fallDistance = fall;
				float vanilla = Items.MACE.getAttackDamageBonus(zombie, 0, s.overworld().damageSources().playerAttack(player));
				double ours = WeaponInfoModule.smashBonus(fall);
				if (Math.abs(vanilla - ours) > 0.001) out.append(fall).append(": vanilla ").append(vanilla).append(" ours ").append(ours).append("; ");
			}

			player.fallDistance = 0;
			return out.toString();
		});
		report.check("Weapon Info's mace smash bonus equals the server's value at falls of 2-30 blocks", mace.isEmpty(), mace);

		server.runCommand("item replace entity @a weapon.mainhand with minecraft:mace");
		enable(context, WeaponInfoModule.class, true);
		context.waitTicks(2);
		List<String> maceLines = hud(context, WeaponInfoModule.class);
		report.check("Weapon Info shows fall, smash state and wind charges while holding a mace",
				maceLines.stream().anyMatch(l -> l.startsWith("Smash:")) && maceLines.stream().anyMatch(l -> l.startsWith("Wind charges:")), maceLines.toString());

		server.runCommand("clear @a");
		server.runCommand("kill @e[type=husk]");
		for (Class<? extends Module> type : List.of(CombatInfoModule.class, TargetHudModule.class, WeaponInfoModule.class)) {
			enable(context, type, false);
		}
	}

	static void movement(ClientGameTestContext context, TestSingleplayerContext singleplayer, TestReport report) {
		var server = singleplayer.getServer();
		enable(context, MovementInfoModule.class, true);
		server.runCommand("gamemode survival @a");
		server.runCommand("effect clear @a");
		server.runCommand("tp @a 0.5 -40 0.5 0 0");
		server.computeOnServer(s -> {
			s.getPlayerList().getPlayers().getFirst().setHealth(20);
			return null;
		});
		context.waitTicks(4);
		int predicted = context.computeOnClient(client -> {
			MovementInfoModule.Landing landing = MovementInfoModule.predict(client.player);
			return landing == null ? -1 : landing.damage();
		});
		List<String> lines = hud(context, MovementInfoModule.class);
		boolean landed = ClientGameTest.waitUntil(context, () -> context.computeOnClient(client -> client.player.onGround()), 200);
		context.waitTicks(3);
		float health = server.computeOnServer(s -> s.getPlayerList().getPlayers().getFirst().getHealth());
		int actual = Math.round(20 - health);
		server.runCommand("gamemode creative @a");
		server.runCommand("effect give @a instant_health 1 5");
		report.check("Fall damage prediction while falling 20 blocks equals the damage really taken", landed && predicted == actual && actual > 0,
				"predicted " + predicted + ", took " + actual + "; HUD " + lines);
		enable(context, MovementInfoModule.class, false);
		server.runCommand("tp @a 0.5 -60 0.5 0 15");
		context.waitTicks(3);
	}

	static void inventory(ClientGameTestContext context, TestSingleplayerContext singleplayer, TestReport report) {
		var server = singleplayer.getServer();
		server.runCommand("clear @a");
		// Totems and splash potions stack to 1, so each goes into its own slot.
		for (int slot = 10; slot < 13; slot++) {
			server.runCommand("item replace entity @a inventory." + slot + " with minecraft:totem_of_undying");
		}

		server.runCommand("item replace entity @a hotbar.1 with minecraft:golden_apple 5");
		server.runCommand("item replace entity @a hotbar.2 with minecraft:splash_potion[potion_contents={potion:\"minecraft:healing\"}]");
		server.runCommand("item replace entity @a hotbar.4 with minecraft:splash_potion[potion_contents={potion:\"minecraft:healing\"}]");
		server.runCommand("item replace entity @a hotbar.3 with minecraft:splash_potion[potion_contents={potion:\"minecraft:swiftness\"}]");
		server.runCommand("item replace entity @a armor.chest with minecraft:diamond_chestplate[damage=520]");
		server.runCommand("effect give @a speed 30 1");
		enable(context, ItemCountersModule.class, true);
		enable(context, HealthArmorModule.class, true);
		enable(context, EffectsModule.class, true);
		context.waitTicks(4);

		Map<String, Integer> counts = context.computeOnClient(client -> femboy().modules().get(ItemCountersModule.class).counts());
		List<String> counterLines = hud(context, ItemCountersModule.class);
		report.check("Item Counters: totals per item and per potion kind", Integer.valueOf(3).equals(counts.get("Totem of Undying"))
				&& Integer.valueOf(5).equals(counts.get("Golden Apple")) && Integer.valueOf(2).equals(counts.get("Splash Potion of Healing"))
				&& Integer.valueOf(1).equals(counts.get("Splash Potion of Swiftness")), counts.toString());
		report.check("Item Counters: reminds to refill totems that are only in the inventory", counterLines.stream()
				.anyMatch(l -> l.startsWith("Refill: Totem of Undying (3")) && counterLines.stream().noneMatch(l -> l.startsWith("Refill: Golden Apple")),
				counterLines.toString());

		List<String> armorLines = hud(context, HealthArmorModule.class);
		report.check("Health & Armor lists the nearly broken chestplate with its durability", armorLines.stream()
				.anyMatch(l -> l.contains("Diamond Chestplate: 8  2%")), armorLines.toString());
		List<String> effectLines = hud(context, EffectsModule.class);
		report.check("Effects lists Speed II with its remaining time", effectLines.stream().anyMatch(l -> l.startsWith("Speed II: 0:2")), effectLines.toString());
		context.takeScreenshot("inventory-modules");

		server.runCommand("clear @a");
		server.runCommand("effect clear @a");
		for (Class<? extends Module> type : List.of(ItemCountersModule.class, HealthArmorModule.class, EffectsModule.class)) {
			enable(context, type, false);
		}
	}

	static void visuals(ClientGameTestContext context, TestSingleplayerContext singleplayer, TestReport report) {
		var server = singleplayer.getServer();
		server.runCommand("tp @a 0.5 -60 0.5 0 15");
		server.runCommand("summon zombie 2.5 -60 5.5 {NoAI:1b,PersistenceRequired:1b,Silent:1b}");
		server.runCommand("summon zombie -1.5 -60 6.5 {NoAI:1b,PersistenceRequired:1b,Silent:1b}");
		enable(context, HitboxesModule.class, true);
		context.waitTicks(10);
		int drawn = context.computeOnClient(client -> femboy().modules().get(HitboxesModule.class).drawn());
		context.takeScreenshot("visuals-hitboxes");
		context.runOnClient(client -> {
			@SuppressWarnings("unchecked")
			var hostile = (io.github.theamethyx.femboyclient.core.setting.Setting<Boolean>) femboy().modules().get(HitboxesModule.class).settings().get("hostile");
			hostile.set(false);
		});
		context.waitTicks(3);
		int filtered = context.computeOnClient(client -> femboy().modules().get(HitboxesModule.class).drawn());
		context.runOnClient(client -> femboy().modules().resetToDefaults(femboy().modules().get(HitboxesModule.class)));
		server.runCommand("kill @e[type=zombie]");
		report.check("Hitboxes draws both zombies, and none once hostile mobs are filtered out", drawn == 2 && filtered == 0,
				"drawn " + drawn + ", filtered " + filtered);

		// Trajectory: predict a snowball throw, then throw it and follow it on the server.
		server.runCommand("tp @a 0.5 -60 0.5 0 -20");
		server.runCommand("item replace entity @a weapon.mainhand with minecraft:snowball 4");
		enable(context, TrajectoriesModule.class, true);
		context.waitTicks(5);
		Vec3 predicted = context.computeOnClient(client -> {
			Trajectory.Result result = femboy().modules().get(TrajectoriesModule.class).last();
			return result == null || result.hitBlock() == null ? null : result.end();
		});
		context.takeScreenshot("visuals-trajectory");
		context.getInput().pressKey(options -> options.keyUse);
		List<Vec3> path = new ArrayList<>();
		for (int i = 0; i < 100; i++) {
			context.waitTick();
			Vec3 pos = server.computeOnServer(s -> s.overworld().getEntitiesOfClass(Snowball.class, s.getPlayerList().getPlayers().getFirst()
					.getBoundingBox().inflate(64)).stream().findFirst().map(Snowball::position).orElse(null));
			if (pos == null && !path.isEmpty()) {
				break;
			}

			if (pos != null) {
				path.add(pos);
			}
		}

		Vec3 last = path.isEmpty() ? null : path.getLast();
		double error = predicted == null || last == null ? Double.NaN : Math.sqrt(Math.pow(predicted.x - last.x, 2) + Math.pow(predicted.z - last.z, 2));
		report.check("Trajectories: a real snowball lands within 2 blocks of the predicted point", error <= 2.0,
				String.format(Locale.ROOT, "predicted %s, last server position %s (%d ticks), horizontal error %.2f", predicted, last, path.size(), error));
		enable(context, TrajectoriesModule.class, false);
		server.runCommand("clear @a");
	}

	static void explosions(ClientGameTestContext context, TestSingleplayerContext singleplayer, TestReport report) {
		var server = singleplayer.getServer();
		server.runCommand("difficulty normal");
		// Eyes are 1.62 above the feet; at 40° down the crosshair meets the ground about 1.9 blocks ahead (z = 2).
		server.runCommand("tp @a 0.5 -60 0.5 0 40");
		// Items dropped by earlier tests (zombie loot) would block the crystal's space, as they do in vanilla.
		server.runCommand("kill @e[type=!player]");
		server.runCommand("setblock 0 -61 2 minecraft:obsidian");
		enable(context, ExplosionInfoModule.class, true);
		context.waitTicks(5);
		boolean spot = context.computeOnClient(client -> femboy().modules().get(ExplosionInfoModule.class).spots().contains(new BlockPos(0, -61, 2)));
		var focus = context.computeOnClient(client -> femboy().modules().get(ExplosionInfoModule.class).focus());
		context.takeScreenshot("combat-crystal-placement");
		String around = context.computeOnClient(client -> client.level.getBlockState(new BlockPos(0, -61, 2)).getBlock() + " "
				+ client.level.getEntities(null, new net.minecraft.world.phys.AABB(0, -60, 2, 1, -58, 3)) + " " + client.hitResult.getLocation());
		report.check("Crystal & Anchor Info: obsidian within reach is a placement spot and under the crosshair it is the focus",
				spot && focus != null && focus.label().equals("Crystal here"), "spot " + spot + ", focus " + focus + "; " + around);

		server.runCommand("setblock 0 -61 2 minecraft:grass_block");
		server.runCommand("gamemode survival @a");
		server.runCommand("effect clear @a");
		server.computeOnServer(s -> {
			s.getPlayerList().getPlayers().getFirst().setHealth(20);
			return null;
		});
		server.runCommand("summon end_crystal 0.5 -60 9.5 {ShowBottom:0b}");
		context.waitTicks(5);
		float estimate = context.computeOnClient(client -> {
			EndCrystal crystal = client.level.getEntitiesOfClass(EndCrystal.class, client.player.getBoundingBox().inflate(16)).getFirst();
			return ExplosionInfoModule.estimate(client.player, crystal.position(), 6);
		});
		server.runCommand("damage @e[type=end_crystal,limit=1] 1 minecraft:generic");
		context.waitTicks(3);
		float health = server.computeOnServer(s -> s.getPlayerList().getPlayers().getFirst().getHealth());
		server.runCommand("gamemode creative @a");
		server.runCommand("effect give @a instant_health 1 5");
		float taken = 20 - health;
		report.check("Crystal damage estimate (no armor, Normal) matches the damage really taken within 0.5",
				taken > 0 && Math.abs(taken - estimate) <= 0.5f, String.format(Locale.ROOT, "estimate %.2f, taken %.2f", estimate, taken));
		enable(context, ExplosionInfoModule.class, false);
		server.runCommand("fill -12 -64 -12 12 -62 20 minecraft:dirt replace minecraft:air");
		server.runCommand("fill -12 -61 -12 12 -61 20 minecraft:grass_block replace minecraft:air");
		server.runCommand("tp @a 0.5 -60 0.5 0 15");
		context.waitTicks(3);
	}
}
