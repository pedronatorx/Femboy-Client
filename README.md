# Femboy Client

A client-side Fabric mod for **Minecraft Java Edition 1.21.11**: a themeable ClickGUI with 60 combat, automation, movement, visual, inventory, tracking, world and utility modules, a drag-and-drop HUD editor, profiles with automatic switching, and 17 ready-made presets.

Everything is **off on first start**; nothing runs until you turn it on or install a preset. Every module shows a **tier** badge: *Visual* and *Assist* modules (information, ESP, HUD) and *Automation* modules that only manage your own items (Auto Attribute Swap's spear reach and weapon rules, Auto Tool, Inventory Sorter, Sprint) work on every server. **Sandbox** modules (combat automation, movement changes, Auto Armor, Auto Refill, Auto Offhand) work only in a **test environment**: singleplayer, or a server you reach at a private address (your own PC, your LAN, or a VPN such as Tailscale or ZeroTier). There are no prompts or allowlists; elsewhere they simply stay off and their description says why. Femboy Client contains nothing that hides it from anti-cheat systems, and some servers' rules forbid clients like this, so check before you use it there.

![Home page](docs/screenshots/0012_gui-1280x720-0-home.png)

## Versions

| Component | Version |
|---|---|
| Minecraft | 1.21.11 |
| Fabric Loader | 0.19.5 |
| Fabric API | 0.141.6+1.21.11 |
| Fabric Loom | `net.fabricmc.fabric-loom-remap` 1.18.4 |
| Mappings | Mojang official mappings |
| Gradle | 9.7.1 (wrapper and distribution checksums pinned) |
| Java | 21 for compiling and running the game; Gradle itself needs **JDK 25+** (a Loom 1.18 requirement) |

## Installation (normal Minecraft launcher)

1. Install Fabric Loader 0.19.5 for Minecraft 1.21.11.
2. Put `femboy-client-0.6.1.jar` (from `build/libs/`) and Fabric API 0.141.6+1.21.11 into the profile's `mods` folder.
3. Start the game and press **Right Shift** to open the menu. You can rebind it under Options → Controls → Key Binds → Femboy Client.

Settings live in `config/femboyclient/`:
- `femboyclient.json`: client options, active profile
- `profiles/*.json`: modules, keys and settings per profile
- `exports/*.json`: profiles you exported

Every file is written atomically with a `.bak` copy. An unreadable file is moved aside as `*.corrupt-<time>`, never deleted, and the client falls back to the backup or to defaults.

**Upgrading from NOVA:** on first launch, settings from `config/nova/` are copied to `config/femboyclient/`. The old folder is left untouched.

## Building

You need a JDK 25 or newer to run Gradle. The build uses a Java 21 toolchain for compiling and for the game: Gradle finds an installed JDK 21 or downloads one through the foojay resolver.

```bash
export JAVA_HOME=$HOME/.local/share/jdks/jdk-25.0.4.1+1   # any JDK 25+
```

| Task | Command |
|---|---|
| Build the mod jar (also runs the unit tests) | `./gradlew build` → `build/libs/femboy-client-0.6.1.jar` |
| Unit tests only | `./gradlew test` |
| Server GameTests, headless (includes the research experiment) | `./gradlew runGameTest` |
| In-game client tests, headless (needs `xvfb-run`), with screenshots | `./scripts/run-client-tests.sh` |
| Same, in a visible window | `./scripts/run-client-tests.sh --visible` |
| Launch a development client to play | `./gradlew runClient` |
| Copy the latest screenshots and reports into `docs/` | `./scripts/collect-artifacts.sh` (run directly after the client tests: other Gradle tasks clear the client run folder) |

## Using the menu

The sidebar has twelve sections. Every module has an on/off switch, a key (click it and press a key; Backspace clears it) that either **toggles** the module or keeps it on only while **held**, settings grouped into sections with tooltips and per-setting reset, and a module reset.

| Section | Contents |
|---|---|
| **Home** | Search across all modules with a category filter (click a module to open it), session status, module health, frame rate |
| **Combat** | Auto Attribute Swap, Combat Info, Target HUD, Weapon Info, Crystal & Anchor Info, Auto Criticals\*, Sprint Reset\*, Auto Weapon Switch\* |
| **Automation** | KillAura\*, Trigger Bot\*, Aim Assist\*, Reach\*, Velocity\*, Auto StunSlam\*, Auto Offhand\* |
| **Movement** | Free Camera, Sprint, Movement Info, Elytra Info, Flight\*, Elytra Flight\*, Speed\*, Strafe Assist\*, Step\*, Safe Walk\*, No Slow\*, No Fall\* |
| **Visuals** | Entity ESP, Storage ESP, Ore ESP, Hitboxes, Trajectories, Block Highlight, Light Level, Fullbright, Zoom, Chunk Borders |
| **Inventory** | Item Counters, Health & Armor, Effects, Auto Tool, Inventory Sorter, Shulker Preview, Auto Armor\*, Auto Refill\* |
| **Trackers** | Player Finder, Trackers, Player Alert, Waypoints |
| **World** | Geode Analyzer, Block Counter, Death Coordinates |
| **Utilities** | Coordinates, Performance, Module List, Keystrokes, Notifications, Better Chat, Auto Reconnect |
| **Developer** | Java Runner |
| **Profiles** | Your profiles (use, copy, export, import, delete, reset), the 17 presets, automatic switching rules and delay |
| **Settings** | General tab: emergency off, **Edit HUD layout**, client options (theme, custom accent color, menu scale, animations, tooltips, automatic switching), config folder. Diagnostics tab: frame timing, time per module, message log |

\* Sandbox tier: test environments only (see [Test environments](#test-environments)).

**Themes:** Femboy (navy, purple, magenta; the default), Charcoal, Midnight magenta and Deep ocean, plus an optional custom accent color. **HUD editor:** drag any HUD block to place it anywhere (positions are stored relative to the screen size, so they survive resizing), right-click a block to send it back to its corner, R resets all.

### Modules

| Module | What it does |
|---|---|
| **Auto Attribute Swap** | Switches to the right weapon for a single hit and straight back: an axe against a raised shield, a mace while falling, optional sword, trident and custom-item rules, and two spear parts: **Spear reach** (click a target beyond your normal reach but within the spear's 4.5 blocks and it jabs with the spear) and **Spear lunge** (hold attack with food or wind charges in hand to jab a Lunge spear again and again and dash; test environments only). Each rule has its own cooldown and conditions. See below. |
| **Combat Info** | Clicks per second (left and right), combo (hits that landed in a row; ends when you take damage), reach of your last hit, accuracy. |
| **Target HUD** | A card with your target's name, health and absorption, armor points, distance, armor and held items, and whether its shield is raised. The target is what you look at, the entity you hit last, or one you lock with the lock key. |
| **Weapon Info** | Depends on what you hold. **Axe:** target shield state, your own shield cooldown. **Mace:** fall distance, smash ready, base smash bonus (same formula as the server), vertical speed, wind charges. **Spear:** Lunge level and why it can't trigger (riding, gliding, in water); while charging, the damage, knockback and dismount windows with the speed you have and need. **Bow/crossbow/trident:** draw, load and charge progress, Riptide conditions, ammunition. |
| **Crystal & Anchor Info** | Highlights obsidian and bedrock within reach where an end crystal can be placed; estimates the explosion damage to you and your target from the crystal, spot or anchor under your crosshair (vanilla formula with exposure, difficulty, armor, Resistance and Protection/Blast Protection); anchor charges and whether it explodes here. |
| **KillAura** | Attacks the best target in range: target filters (players, hostile, passive, teammates, invisible, creative), range, field of view, walls, priority; timing by attack charge or CPS range plus an extra cooldown; turns your view to the target with adjustable speed. |
| **Trigger Bot** | Attacks when your crosshair is on a matching target, with the same filters and timing. |
| **Aim Assist** | Turns your view toward the target nearest your crosshair at an adjustable speed; optionally only while holding attack. Never attacks. |
| **Reach** | Longer attack (+0–3 blocks) and block reach (+0–1). Vanilla servers accept up to +3 and +1; more is rejected by the server. |
| **Velocity** | Keep 0–100% of horizontal and vertical knockback from hits and explosions. |
| **Auto StunSlam** | While falling onto a target with a raised shield: axe hit (disables the shield), mace smash, back to your slot. Minimum fall, delay between hits (0 = same tick), cooldown. |
| **Auto Offhand** | Keeps a totem, shield, golden apple or end crystal in your offhand, with a delay, a health threshold and whether to replace other offhand items. |
| **Auto Criticals** | Jumps when you click a target on the ground so the hit lands while falling (a real critical, no fake packets). |
| **Sprint Reset** | W-tap: releases sprint briefly after a hit so the next hit has sprint knockback again. |
| **Auto Weapon Switch** | Selects the hotbar weapon with the highest attack damage while you look at a living target. |
| **Flight** | Hover-and-move or creative-style flying. Dedicated servers kick flying players unless `allow-flight=true`. |
| **Elytra Flight** | Controlled elytra flight while gliding: forward, up, down and hover, with adjustable speed. |
| **Speed, Strafe Assist, Step, Safe Walk, No Slow, No Fall** | Faster ground movement; air control toward your keys; walk up full blocks (step-height attribute); don't walk off edges; full speed while eating or blocking; no fall damage (tells the server you are on the ground after a set fall distance). |
| **Sprint** | Keeps sprint on while you move forward. Works on every server. |
| **Ore ESP** | Highlights chosen ores within a chunk radius, optionally only those touching air; scanned in time-limited steps. |
| **Block Highlight, Light Level** | Outlines the block you look at in a chosen color; marks spots where hostile mobs can spawn (block light 0; red now, yellow only at night). |
| **Zoom** | Hold a key to zoom in; scroll to change the zoom; mouse sensitivity drops with the zoom. |
| **Auto Tool** | Selects the best hotbar tool while you mine and switches back afterwards. |
| **Inventory Sorter** | A Sort button in your inventory that orders items by type and amount, merging stacks. |
| **Shulker Preview** | Shows a shulker box's contents in its tooltip. |
| **Auto Armor, Auto Refill** | Equips better armor from your inventory; refills a hotbar stack from the inventory when it runs low. |
| **Block Counter** | Counts chosen blocks around you with scan progress (`Scanned 81/81 chunks`). |
| **Death Coordinates** | Remembers where you died, shows it on the HUD and adds a waypoint. |
| **Keystrokes, Notifications, Better Chat, Auto Reconnect** | WASD, mouse and space display; toasts for module and profile changes; timestamps and stacking of repeated chat lines with longer history; reconnects to the last server after a disconnect, with a delay. |
| **Free Camera** | Fly the camera with its own rotation while your player stays put. Works on servers. See below. |
| **Movement Info** | Speed, vertical speed, fall distance and a landing prediction with the fall damage vanilla would deal (landing block included: water, slime, hay, beds...), red when it would kill you. |
| **Elytra Info** | Speed, height, pitch, elytra durability, rockets, and a warning when your current direction hits a block within a set time. |
| **Entity ESP** | Highlights the entity types you choose (picked by registry id in a searchable checklist). Compact labels with separately sized name, distance and health, outlines with real thickness and opacity per category, tracer lines, color overrides for chosen types. See below. |
| **Storage ESP** | Highlights storage blocks you choose by block id. Reads only block entities your client has already received. |
| **Hitboxes** | Entity hitboxes with eye height and look direction (like F3+B), filtered by players, hostile, passive, items, projectiles and others; color, opacity, line width, range. |
| **Trajectories** | The predicted path of arrows (bow and crossbow), tridents, snowballs, eggs, ender pearls, splash/lingering potions and experience bottles, with the landing point and any entity in the way. Uses vanilla's per-tick physics. |
| **Fullbright** | Lights up dark areas without changing your video settings, with adjustable intensity. |
| **Chunk Borders** | Chunk boundary lines around you. |
| **Item Counters** | Counts of the items you choose (totems, gapples, crystals, obsidian, anchors, glowstone, pearls, wind charges, rockets, potions by type...), red when low, and **refill reminders** when an item is in your inventory but not in your hotbar or offhand. |
| **Health & Armor** | Health with absorption, food and saturation, durability of armor and held items, low-health and low-durability warnings with a one-time alert. |
| **Effects** | Active potion effects with level and time left; harmful ones in red, expiring ones highlighted. |
| **Player Finder** | Online players from the server's player list (name, game mode, ping) with search and sorting, plus navigation to players in range and to your waypoints. See below. |
| **Trackers** | Arrows at the screen edge pointing at players, mobs, waypoints and last-seen positions, labelled with where the position comes from. See below. |
| **Player Alert** | Warns you when another player comes within your render distance. See below. |
| **Waypoints** | Save positions; see them as markers with names and distances. |
| **Geode Analyzer** | Finds amethyst geodes in loaded chunks and keeps exact growth-stage counts up to date as chunks load and buds grow. See below. |
| **Coordinates, Performance, Module List** | HUD panels. |
| **Java Runner** | Write and run small Java programs in a separate process, with output, errors, limits and examples. See below. |

HUD blocks stack in the corner you choose without overlapping, and the top-right corner starts below vanilla's effect icons.

An enabled module **pauses** while no world is loaded and resumes on its own. If a module throws an error, it is turned off and reported under Settings → Diagnostics; other modules keep running.

## Profiles and presets

A **profile** stores which modules are on, every setting and every key. Changes save automatically to the active profile.

- **Create** a profile on the Profiles page: type a name and press Create (it starts as a copy of the current one). **Use** switches to it; the current profile is saved first, and modules that are off in the new profile are turned off.
- **Copy** duplicates a profile, **Export** copies it to the clipboard and saves it under `config/femboyclient/exports/`, **Import** creates a profile from text on the clipboard. Every imported value is checked; unknown modules and invalid values are dropped and replaced by defaults, and you are told which.
- **Reset the current profile** turns every module off and restores defaults (two clicks).
- **Presets:** Sword PvP, Axe PvP, Mace PvP, Spear PvP, Spear + Mace PvP, Crystal PvP, Anchor PvP, Pot PvP, UHC / Survival, Survival / SMP, Elytra PvP, Bow / Crossbow, Building, Mining, Exploration, Performance and Local Testing. **Install** creates a normal profile from the preset; edit it like any other. **Reset** puts the preset's settings back. Presets turn on information and visual modules only. Automation that fits a style is configured but left off: Auto Attribute Swap (axe, mace, spear + mace), KillAura and Trigger Bot (sword, axe), Auto StunSlam (mace, spear + mace), AutoTotem (crystal, anchor).
- **Automatic switching:** add rules such as "Mace, any dimension → mace_pvp" (or press **Add rule** next to a preset), then turn on *Switch profiles automatically*. Rules are checked from the top; the first match wins (held item or gliding, optionally a dimension). If nothing matches, the profile stays. A rule result must hold for the **switch delay** (default 4 ticks, 0–40) before the profile changes, so scrolling past items doesn't flicker between profiles. A profile you pick by hand stays until your held item changes to something another rule matches. A weapon Auto Attribute Swap holds for a single hit never changes the profile.

## Emergency off

**Settings → Turn all modules off**, or a key you bind under Options → Controls → Key Binds → Femboy Client ("Turn all modules off (emergency)", unbound by default). Every module is turned off and restores what it changed (Free Camera returns the view, Fullbright the lighting, Auto Attribute Swap the held item), and automatic profile switching is paused.

## Test environments

Sandbox-tier modules (marked \* above, plus the Spear lunge part of Auto Attribute Swap) run only in singleplayer or on a server whose address is private: loopback (`127.x`, `::1`), LAN (`10.x`, `172.16–31.x`, `192.168.x`), link-local, the `100.64–127.x` range used by Tailscale and similar VPNs, and IPv6 unique local addresses. The check uses the address actually connected to, not the name in your server list. To use them on remote test servers, put those servers on a VPN with you. On any other server they show "Only in singleplayer and on servers in your own network (LAN or VPN)" and stay off; you never get a prompt.

Nothing here hides anything from anti-cheat or server checks (no fake rotations, no packet tricks, no anti-kick).

**Not built yet:** AutoCrystal, AutoAnchor, Auto Pot, Aim Trainer, Anti Hunger, Hotbar Profiles, Item Swap, Chest Organizer, Structure Finder, Material List, Schematic Viewer, armor in nametags and shader entity outlines.

### Geode Analyzer

- **Reads only what your client has.** It never loads chunks or asks the server for anything. Chunks in range that aren't loaded are reported as unavailable, never treated as empty.
- **Updates automatically.** New chunks are read when they load; already-read chunks are re-read after the scan interval; grouping into geodes runs in the background and only when the data actually changed. Reading is limited to a few milliseconds per tick. **Pause** stops reading and analysis while keeping the last results; **Read again** forces a fresh read.
- **Per geode:** distance and direction, completeness, a stage bar, counts per stage (with percentages of the geode's total buds and clusters), the sample size `n`, when the data was read, and an evidence-quality rating (based on completeness and sample size; it is *not* a probability). Click a geode for exact counts, budding faces, unavailable positions and Wilson intervals.
- **Settings:** analysis radius, scan interval, minimum observations, percentage display, listing incomplete geodes, growth-stage display in the world, progress on HUD, debug information, time per tick.
- **Interpretation:** the panel quotes what controlled tests showed (below) and states that growth stages are not evidence of player activity. It never labels a geode as "visited" or similar.

### Player Finder

- The list comes from the server's player list (everyone online, wherever they are).
- **Positions are known only for players within your range**: the server sends a player's position only while they are within its entity tracking range, roughly your render distance. Nothing on the client can see further; that would need the server to share positions.
- Players out of range show **"Position unknown"**, or, if they were in range earlier, **"Last seen … ago"** with that old position, clearly marked as stale. Nothing is inferred or invented. Last-seen positions are forgotten when you change world or dimension.
- Click a player with a known position, or one of your waypoints, to navigate: the HUD shows the target, distance and an arrow relative to where you're looking.

### Player Alert

- Turn it on in Trackers → Player Alert. It warns when a player comes within your range, i.e. when the server starts sending you that player.
- **Settings:** chat message (only you see it), sound, pop-up, warn when they leave (and whether they logged off), ignore NPCs (player-shaped entities missing from the server's player list), and a repeat delay so someone walking along the edge of your range doesn't flood the chat.
- Players already near you when you turn it on are not announced. Several arrivals in the same moment are combined into one message.

### Auto Attribute Swap

**The two spear parts.** A spear jab is its own action, aimed along the spear's attack range (2 to 4.5 blocks in survival, 6.5 in creative). The server checks the jab against your attack charge using the attack speed of the item it last saw you holding; it updates that only when it ticks you. Switching to the spear only for the jab (and back in the same tick) therefore uses the held item's charge time:
- **Spear reach** (any server, like the other rules): when you click and nothing is under the crosshair within your normal reach, but a target is along the spear's range, the module switches to the spear, jabs and switches straight back. Settings: cooldown, target filter.
- **Spear lunge** (singleplayer and servers in your own network only): while you hold attack with one of the "Hold to lunge" items (default: wind charges and common foods; optionally any non-weapon item), it jabs with a spear that has Lunge every cooldown (default 150 ms) and switches back. Items without weapon attack speed recharge in 5 ticks, and the server allows 5 ticks of leeway, so each jab is accepted. Lunge does not work while riding, gliding or in water, costs 1 durability and 4 exhaustion per level per jab, and food drains quickly.


- **What it does:** when you attack, it selects the hotbar slot of a weapon chosen by rule, the attack is sent with that item, and the original slot is selected and sent again in the same tick. The server applies the hit with the swapped item's effects and never sees the swap during its own tick, so your attack charge is kept.
- **Rules,** checked in this order, each in its own card with the item icon, an on/off switch, a reset button and its settings:

  | Rule | Default | Condition | Cooldown |
  |---|---|---|---|
  | Axe | on | only against a raised shield (switchable) | 1000 ms |
  | Mace | on | after falling at least 1.5 blocks | 0 ms |
  | Spear | off | any hit | 500 ms |
  | Sword | off | any hit | 0 ms |
  | Trident | off | any hit | 500 ms |
  | Custom items | off | items you pick from a searchable list | 500 ms |

  Every rule can also be limited to players or to mobs.
- **Global settings:** swap-back delay (0 ticks by default), no swapping while using an item, status on the HUD.
- **Safety:** only hotbar slots are used and nothing is moved in your inventory. A missing item is skipped, a swap is never stacked on one that is still waiting to be undone, and if you change slots yourself during a delayed swap-back your choice is kept.
- **What "confirmed" means:** the panel shows the last swap, which slot was selected and sent, and whether it was held during the hit. Servers don't acknowledge slot changes, so the panel never claims more than that.

### Entity ESP

- **Sections:** Targets, Boxes, Colors, Labels, Health, Tracers, Performance.
- **Labels** are small by default (names at 0.75x). Name, distance and health each have their own on/off switch and size. Background opacity, padding, line spacing, text color and maximum name width (long names end in "...") are adjustable. Names use the group color instead of the old underline. Labels of entities close together stack instead of overlapping.
- **Health** is off by default. When on, it is shown for the groups you choose (players and hostile mobs by default) as a compact bar, a number, or both, with its own size and opacity.
- **Outlines:** thickness (0.5x to 6x), opacity, and an on/off switch per group. The thickness goes into the line shader, so the slider really changes the drawn line.
- **Tracers:** optional lines from the middle of your view to each highlighted entity, with their own thickness and opacity.
- **Color override:** pick entity types to draw in one override color instead of their group color.

### Trackers

- **Display:** an arrow at the screen edge for each target that is off screen, and a small marker for targets that are on screen. Labels show the name, distance and source.
- **Sources:**
  - entities your client has loaded (live positions);
  - your waypoints in this world and dimension;
  - Player Finder's last-seen positions, labelled "last seen ... ago".

  Nothing is shown for players that were never in your range.
- **Settings:** target types, maximum distance and number of indicators, update interval, indicator size, distance from the screen edge, labels, distance, label size, opacity, and a color per source.
- Targets that despawn or log off disappear at the next update; removed entities are skipped immediately.

### Free Camera

- **Rotation:** the camera has its own rotation, so the mouse turns the camera and your player's head stays where it was.
- **Keys:** forward, back and sideways use your normal movement keys. Up, down and boost have their own key settings (Space, Left Shift, Left Ctrl by default).
- **Movement settings:** speed, boost multiplier, smoothing, and "fly where you look".
- **On servers:**
  - your player keeps sending its normal position updates, so nothing snaps back when the camera returns;
  - attacking, using items and pick-block are disabled while the camera is detached, because they would aim from the camera.
- **Edge of the loaded area:**
  - the server only sends chunks around your player, so far away there is nothing to show;
  - the HUD says "No world data here" when the camera is there;
  - "Stay inside loaded chunks" stops the camera at the edge instead.
- **Restoring:** occlusion culling is switched off while the camera is detached (inside blocks it would hide everything) and restored afterwards, along with the camera type and the player's input. Dimension changes and respawns re-attach the camera cleanly.

### Java Runner

- **Editor:**
  - code editor with Java syntax highlighting, line numbers, selection, undo, copy, cut, paste and automatic indentation;
  - Run, Stop, Clear and Copy buttons;
  - six examples;
  - status line with the run time;
  - console with errors in red.
- **Never inside Minecraft:** each run starts a separate Java process with a memory limit, a time limit and an output limit, and Stop kills it. The game keeps running normally meanwhile.
- **What code can read:** a read-only snapshot taken when you press Run, through `Femboy.*`:
  - client and Minecraft versions, frame rate, connection;
  - every module's state and settings, recent diagnostics.

  It has no access to the game, your world or other players. Code that tries to use Minecraft classes fails to compile.
- **Code you write:** plain statements (wrapped automatically; `java.util.*` is imported) or a class with `main`. Errors show the line you typed.
- **Confirmation:** code that deletes or writes files, starts programs, uses the network, exits the process or bypasses access checks needs an extra confirmation before it runs. This is a safety prompt, not a sandbox: the process runs with your normal permissions.
- **A JDK is needed:** the runner needs a Java with a compiler. If the Java Minecraft runs on has none (the launcher's often doesn't), set "Java for the runner" to a JDK 21+.

## Research background

The Geode Analyzer grew out of controlled experiments run by `./gradlew runGameTest`. Full report: [docs/research/evaluation.md](docs/research/evaluation.md).

- **Setup:** vanilla geodes in a flat test world, growth simulated with the game's own random-tick code, scored on held-out geodes against criteria fixed before the experiment.
- **Results:**
  - The cluster share separated under 2 h from 2 h+ of simulated ticking. The current report shows 2.2% false positives and 2.9% false negatives on 194 held-out samples; across three runs, the ranges were 2–7% and 3–5%.
  - It cannot separate 5–24 h from longer: the share saturates near 100% after about 6 h.
  - World generation already places clusters, so "any cluster" is not informative (87% false positives).
  - Harvesting resets the share.

None of this identifies players. It describes simulated ticking in test worlds.

## Project layout

```
src/main/java/.../femboyclient/core/     plain Java: settings, modules, config, diagnostics, research, Java Runner process (unit-tested)
src/main/java/.../femboyclient/world/    shared Minecraft code: geode fixtures, census, server scan, experiment generator
src/client/java/.../femboyclient/client/ client: entrypoint, GUI, HUD, rendering, modules, mixins
src/gametest/java/                       server GameTests and in-game client tests
src/test/java/                           JUnit tests
tools/make_icons.py                      generates the GUI icon atlas
scripts/                                 test runner and artifact collection
docs/                                    screenshots, test reports, research reports, verification report
```

Adding a module means writing one class (settings are declared as fields) and one line in `ModuleRegistry`. The GUI builds its page entry, settings panel and persistence from the module's category and settings.

Mixins (nine, each documented in its source file):
- **`KeyboardHandlerMixin`:** module key binds. Fabric API has no in-world key event, and polling once per tick misses short taps.
- **`GameRendererMixin`:** hides the first-person hand while the free camera is active.
- **`LocalPlayerMixin`:** keeps your player sending position updates while the free camera is active (vanilla stops them whenever another entity is the camera).
- **`MinecraftMixin`:** blocks attack, use and pick-block while the free camera is active, since vanilla would aim them from the camera.
- **`MouseHandlerMixin`:** sends mouse movement to the free camera instead of the player.
- **`LightTextureMixin`:** Fullbright's brightness, applied to the lightmap only (the video setting is never written).
- **`MultiPlayerGameModeMixin`** and **`MultiPlayerGameModeAccessor`:** Auto Attribute Swap's slot change around one attack.

## Documentation

- [docs/VERIFICATION.md](docs/VERIFICATION.md): what was verified, how, and what wasn't.
- [KNOWN_ISSUES.md](KNOWN_ISSUES.md): known limitations and unverified assumptions.
- [CHANGELOG.md](CHANGELOG.md)

## License

Not decided yet (`All-Rights-Reserved` in `fabric.mod.json` until the author chooses one).
