# Known issues, limitations and unverified assumptions

## Limitations

- **Server rules.** Visual, Assist and most Automation-tier modules run on every server; Sandbox modules run only in test environments. Many servers forbid ESP, free cameras and similar clients, and their anti-cheat may notice them. Femboy Client does nothing to hide itself.
- **Player Finder and Player Alert only know about players within your range.** The server sends a player's position only while they are within its entity tracking range (roughly your render distance; servers can set it lower). Anyone further away is "Position unknown".
  - "Last seen" positions are where someone was when they left your range, not where they are.
  - Server API integration (for servers you control) is not implemented.
  - "Ignore NPCs" treats a player entity that is missing from the server's player list as an NPC. A server that hides real players from the player list would make Player Alert ignore them; turn the setting off there.
- **Geode grouping.** Geodes are grouped by touching blocks.
  - Two geodes whose shells touch are reported as one.
  - A geode cut apart by a cave or the analysis edge can appear as fragments.
- **Only loaded chunks can be read.** The analyzer reads only chunks the client has loaded; a larger radius never loads more. Geodes at the edge of what is loaded are marked *Incomplete* and their counts are a lower bound.
- **Analyzer timing.**
  - Growth is noticed at the next re-read of the chunk (the scan interval, default 10 s).
  - Re-analysis is limited to once every 1.5 s.
  - "Observed" shows when the oldest chunk data behind a geode was read.
- **Storage ESP** reads block entities the client already has.
  - On servers that hide block entities (anti-xray plugins), it shows what the server sent, which may be fake or nothing. Not tested.
  - A storage block placed or broken nearby triggers a rescan within 2 ticks; other changes (a chunk unloading far away) appear at the next scan.
- **Entity ESP pets** are entities with an owner (tamed wolves, cats, parrots, horses...). Other "pet-like" mobs, such as name-tagged ones, count by their type.
- **World labels** (ESP, waypoints) stack among themselves but can be drawn under HUD panels in the same screen corner.
- **Free camera.**
  - Attacking, using items and pick-block are disabled while the camera is detached.
  - Chunks and entities load only around your player, so far away from it the camera shows empty space (the HUD says so).
  - Forward, back and sideways use your normal movement keys; only up, down and boost have their own key settings.
- **Auto Attribute Swap.**
  - Only hotbar items are used.
  - Servers don't confirm slot changes; the panel reports what the client selected and sent.
  - Some servers' anti-cheat flags fast slot switching.
  - The shield condition (axe only against a raised shield) was not tested against a real blocking target.
- **Java Runner.**
  - Not a sandbox: the separate process runs with your normal user permissions. The risky-API confirmation is a speed bump that can be bypassed (e.g. through reflection or obfuscated code).
  - It needs a Java with a compiler; the Minecraft launcher's Java often has none. Then set "Java for the runner" to a JDK 21+.
  - Each run starts a new Java process, which takes about half a second.
  - Snippets only see a snapshot from the moment you press Run.
- **Combat Info.**
  - A hit counts as landed when the target's hurt animation starts within 12 ticks of your attack. Someone else hitting the same target at that moment is counted as yours.
  - Combo ends when you take damage or after the timeout.
  - Clicks are counted only while no menu is open.
- **Weapon Info.**
  - The mace bonus is the base formula; Density and other server-side enchantment effects add more.
  - The spear speed is your look direction times your movement, like vanilla. Server-side target movement is not known exactly.
- **Crystal & Anchor Info.**
  - Estimates count armor, toughness, Resistance, Protection and Blast Protection.
  - Not counted: other enchantments, plugins, totems, and whether the explosion source is blocked by an entity.
  - The anchor warning assumes vanilla dimension rules: only the Nether sets spawn.
- **Movement Info.** Fall damage is predicted for a straight drop, before Feather Falling and Protection. Landing blocks that change damage (slime, hay, honey, beds, powder snow, cobwebs, water) are handled; others count as normal ground.
- **Elytra Info.** The obstacle warning follows a straight line along your current velocity; turns and rockets change the real path. Not tested in flight.
- **Trajectories.**
  - The path is the center of vanilla's random spread.
  - Wind charges and firework rockets are not predicted.
  - Bow, trident and thrown items inherit your movement on the server from its own record of your motion, which can differ slightly from the client's.
- **Automatic profile switching** saves and loads profile files on the game thread. A switch takes a few milliseconds; scrolling quickly through many weapons switches several times.
- **Presets** describe starting setups, not a best way to play. They depend only on modules that exist; nothing in them claims to do automation.
- **Test environments.** The private-address check is a guard against using automation on public servers, not a security boundary: a local proxy that forwards to a public server would also count as local.
- **KillAura / Trigger Bot / Aim Assist** turn your real view; they target one entity at a time. There is no multi-target or switch mode.
- **Reach** beyond what the server accepts (+3 entity, +1 block on vanilla) makes the server ignore the attack or click.
- **Velocity** changes knockback that arrives while you are taking damage, plus explosions. Knockback from other sources (wind charges, riptide) stays normal unless "Only knockback from damage" is off.
- **Flight** cannot avoid a dedicated server's flying kick (`allow-flight=false`) and does not prevent fall damage after descending.
- **Auto StunSlam** needs an axe and a mace in the hotbar; it starts when you fall at least the minimum distance onto a target with a raised shield within normal reach.
- **AutoTotem** only works with no container open (the inventory screen is fine).
- **Entity ESP.** The color override uses one color for all chosen types; there are no per-type outline widths.
- **Fullbright** raises the lightmap brightness. The darkness effect and blindness still darken the view.
- **What the research results cover.** The figures quoted in the Geode Analyzer hold only for the tested setup:
  - vanilla geodes in a flat test world;
  - exposure produced by calling the game's random-tick code;
  - exposure between 0 and about 38 hours.
- **Covered by other modules:** player, item and projectile ESP, tracers, health bars and distance are options of Entity ESP; CPS and reach are in Combat Info; ping and FPS in Performance; durability alerts in Health & Armor.
- **Structure Finder** works only in singleplayer (it needs the world seed). Positions are the structure's chunk start like /locate; the height is not known.
- **Schematic Viewer** draws outlines of missing and wrong blocks, not block models. Old (pre-1.13) schematics with numeric block ids are not supported. Only the first palette of a multi-palette structure file is used. The Litematica reader was checked with a file written by the test, not with a file saved by Litematica itself.
- **Material List** counts the item that places each block; blocks without an item (fire, portals) are left out, double slabs count 2, the upper half of doors and the head of beds are not counted twice.
- **AutoCrystal/AutoAnchor/Auto Pot** turn your view visibly; they act one step per tick, so they are slower than clients that hide rotations. AutoAnchor does nothing in the Nether.
- **Anti Hunger** makes the server treat you as walking, so sprint-jumps cost the walking jump hunger and other server-side sprint effects (such as sprint knockback when the "report sprint when attacking" setting is off) are lost.
- **Player Alert auto leave** only leaves multiplayer servers, never singleplayer.
- **Block ESP and Block Counter** see only what the server sent; anti-xray servers send fake ores. Since 0.9.0 they (and Geode Analyzer) run only in singleplayer and on servers in your own network.
- **Target lines.**
  - "Only targets you can see" checks the line of sight from your player's eyes a few times a second (not every frame, and from the player, not from a free camera).
  - "Hide behind blocks" uses the depth buffer, so a line can also be cut where it passes behind a block near the target.
  - Checked standing still; not tested while walking with view bobbing, in third person or with the free camera.
- **Performance modes.** Low impact pauses KillAura, Trigger Bot, Aim Assist, AutoCrystal, AutoAnchor, Auto Criticals and Auto StunSlam; they resume when you change the mode. The caps in Performance and Low impact (for example 48 entities, 150 blocks, 12 labels) are fixed, not settings.
- **Profiles store the look:** switching to a profile also switches its theme, animation options and performance mode. Profiles saved before 0.9.0 have none stored and keep the current ones.
- **Module conflicts** are resolved by turning the other module off; nothing is merged.
- **Menu art** comes from small filtered textures. A resource pack that replaces them changes the look; if they are missing the menu falls back to plain shapes (not tested). The HUD keeps plain shapes on purpose: smooth corners would cost batching every frame.
- **Benchmark limits:** see [docs/benchmarks](docs/benchmarks/README.md). Keystrokes did not get faster.
- **No Fall** tells the server you are on the ground; it does not help when the server checks falls itself.
- **HUD editor:** the name label of a block dragged to the very top can overlap the help panel.

## 1.1.0 limitations (interface redesign)

- **Small windows:** with the automatic menu scale, a window under about 1000x580 pixels draws the menu at 1x, where text is Minecraft's smallest size. Small print then stays full size (scaled text would be unreadable), so some lines are cut with "..." and show in full on hover. A fixed menu scale (Settings › General › Menu scale) can be chosen instead.
- **Keyboard:** sections (Ctrl+1 to Ctrl+9, Ctrl+Tab), module lists (Up, Down, Space), the search, dialogs, text fields and the HUD editor work from the keyboard. Moving focus with Tab through every control (switches, sliders, dropdowns) is still not implemented; those need the mouse.
- **Module icons:** modules have no icons of their own; section icons are used for groups and the sidebar. Each module row shows its name and a line of text instead.
- **Help mode** is on by default, also for existing installations (the option is new). Turn it off with the i in any module's header for the previous, compact list.
- **"Incompatible settings"** are not detected as a module state. Conflicting modules (which cannot run together) are listed in a module's "About this module" fold; switching one on switches the other off, as before.
- **ESP preview** is a drawing of the settings, not a rendering of the world: it does not show depth ("hide behind blocks"), real distances or per-group overrides.
- **HUD editor colors:** colors of HUD elements are module settings; the inspector's *Settings* button opens them in the menu rather than editing them in the editor.
- **Background art** is now off by default. Profiles that stored the option before keep their choice.

## 1.0.0 limitations

- **Command palette key** is unbound by default (K would take a key people often give to modules); Ctrl+K inside the menu always works. The quick toggle wheel key is unbound too.
- **HUD opacity** fades backgrounds, text and icons, but item icons (Keystrokes, Item Counters, Target HUD) are drawn opaque by Minecraft.
- **HUD layouts** anchor elements to the nearest screen third. An element placed exactly between two thirds may anchor differently than you expect after a large resize; drag it once more.
- **Theme Studio:** only Minecraft's font; text size follows the menu scale, it cannot be set separately.
- **Rules** see what the client sees once per tick. A profile switch made by a rule is noticed (and fires "the profile changes") on the next tick; the minimum cooldown of 3 s keeps two rules from switching back and forth. Rules cannot press keys, place blocks or switch on automation modules, by design.
- **Session timeline** lives in memory for the session (export it to keep it). Deaths are detected from the client's health state.
- **On/off comparison** measures under whatever is happening at the time; movement, chunk loading and garbage collection add noise. Differences under about 0.2 ms per frame are not meaningful. It does not run for automation modules.
- **Failure recovery:** an error while a module starts (or while loading its settings) still turns it off at once; only errors while running are retried.
- **Restore points** are created and listed on the client thread when you click (a few small files, typically under a few milliseconds).
- **Shape Planner** draws the nearest outlines up to its limit (default 1,500) and recomputes the shape when a setting changes; a radius-64 filled cylinder is cut at 40,000 blocks (the summary shows "+").
- **Mention Alerts** match the message text as shown. Your own messages are recognized only in the vanilla `<Name> message` format; servers that format chat differently can alert on your own lines.
- **Not built from the 1.0 plan:** chat search, a better player list (tab), tooltip extras (enchantment, potion, durability tooltips beyond vanilla), crafting, fall-damage and projectile calculators as separate tools, screenshot tools, a developer console, a test-world launcher, inventory snapshots and comparison, item history and favorites, inventory search, armor and weapon loadouts, quick equip, a durability manager, road and wall planners beyond the Shape Planner's wall, area selection and build notes, the combat training tools beyond Aim Trainer (click timing, movement and parkour trainers, practice dummy, combat timeline, replay camera), developer visualization mode and target grouping for ESP, and keyboard focus traversal of every control in the menu (the palette, the HUD editor and text fields are keyboard-driven; other controls need the mouse).

## Test environment notes

- In-game tests ran headless under Xvfb with Mesa llvmpipe, which renders in software, so frame times are much slower than on real hardware.
- The log check ignores known offline-test noise:
  - missing X11 cursor shapes;
  - Realms and account-property requests failing without a real login;
  - an anisotropic-filtering option warning.
- A visible-window run (`--visible`) on the NVIDIA GPU was not performed.
- Automation was tested in singleplayer (and the localhost test server counts as a test environment); a server on a real LAN or VPN address was not tried. Flight was not tried on a dedicated server.
- Not tested in game in 0.5.0: Elytra Info in flight, the spear charge windows and Lunge (no spear test), bow and crossbow paths (only a thrown snowball was compared with a real throw), anchor explosions, armored or enchanted targets in damage estimates, the lock key pressed on a real keyboard (the test calls the same method).
- The second player in the multiplayer test is a Fabric fake player added on the server, not a second real client. The server sends it to the client like any player, but movement over time and real network latency were not tested.
- The test harness sends key events without modifier flags, so the code editor also checks the keyboard state for Ctrl. Real keyboard shortcuts were not exercised in a visible window.
- Not tested in game in 0.8.0: moving Aim Trainer targets, Hotbar Profiles loading on a profile switch (loading by button was tested), Schematic Viewer rotation and layer filter, Structure Finder for structures other than villages and strongholds, the GUI at menu scales other than Auto.
- Not tested in game in 0.7.0: Elytra Flight, Strafe Assist, Keystrokes key highlighting (only its placement), the drawn Shulker Preview tooltip (only its data), the menu scale option with the new themes, Auto Reconnect actually reconnecting (only the countdown screen).
- The in-game tests sometimes stop with "Network synchronizer in invalid state" when the machine is short on memory (another Minecraft client running); a rerun passes.
- Performance was measured with Mesa software rendering (254 zombies: about 26 ms per frame with ESP, health, tracers and Trackers on vs. 24 ms with them off). Numbers on a real GPU will differ.

## Unverified assumptions

- Dedicated-server behavior was tested with a local Fabric test server only. Other server software (Paper, proxies) was not tested.
- The `.corrupt-<time>` and `.bak` recovery was verified in unit tests with simulated corrupt files, not during a real crash.
- The `config/nova/` → `config/femboyclient/` migration is unit-tested; it was not exercised with a real NOVA install.
- Performance with very many entities or storage blocks, or a 16-chunk analysis radius in a busy world, was not measured. Per-tick time budgets and caps bound the cost by design.
