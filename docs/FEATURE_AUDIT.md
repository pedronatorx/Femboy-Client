# Feature audit: the "module ecosystem" request (1.2.0)

This file classifies every feature named in the 1.2.0 request against what Femboy Client actually does, and records the
third-party projects that were checked. It is the honest answer to "is X in the client?".

**Status values**

| Status | Meaning |
|---|---|
| **Implemented** | Native code in Femboy Client, working; *new* marks what 1.2.0 added. |
| **Partial** | Part of the feature exists; the missing part is named. |
| **Optional** | Works with another mod the user installs; that mod is not bundled. |
| **Planned** | Not built yet; worth doing; nothing in the client pretends it exists. |
| **Unsupported** | Not built and not planned here, with the reason. |

Checked on 2026-10-18 for Minecraft 1.21.11 with Fabric Loader 0.19.5 and Fabric API 0.141.6+1.21.11.

## Third-party projects: license and 1.21.11 availability

Versions and licenses come from the Modrinth API (`api.modrinth.com/v2/project/<slug>` and its version list filtered to
1.21.11 + Fabric) and, for SeedcrackerX, from its GitHub repository and the `gradle.properties` / `fabric.mod.json` of
its release tags. **No code or assets from any of these projects are in Femboy Client.**

| Project | License | Fabric 1.21.11 build | Decision |
|---|---|---|---|
| Sodium | Polyform Shield 1.0.0 (source-available, no competing use) | yes (0.8.15-beta.1) | Not integrated, not bundled. Compatibility **not tested** (see below). |
| Iris Shaders | LGPL-3.0-only | yes (1.10.8) | Not integrated. Compatibility **not tested**. |
| Entity Culling | tr7zw Protective License | yes (1.11.3) | Not integrated; its license does not allow reuse. |
| Mod Menu | MIT | yes (17.0.1) | **Optional integration** (compile-only API, `modmenu` entrypoint). |
| BetterF3 | MIT | yes (17.0.0) | Not integrated; native panels cover the information. |
| Zoomify | LGPL-3.0-only | yes (2.15.2) | Not integrated; native Zoom module. |
| LambDynamicLights | Lambda License (custom) | yes (4.9.1) | Not integrated; not reimplemented (needs light-engine changes). |
| Xaero's Minimap / World Map | All rights reserved | yes | Not integrated (closed source); native Minimap written from scratch. |
| MiniHUD / Tweakeroo / MaLiLib | LGPL-3.0-only | yes | Not integrated; equivalent native panels exist. |
| Jade | CC-BY-NC-SA-4.0 | yes (21.1.6) | Not integrated; native Look At written from scratch. |
| AppleSkin | Unlicense | yes (3.0.8) | Not integrated; saturation is in Health & Armor. |
| Inventory Profiles Next | AGPL-3.0-or-later | yes (2.2.6) | Not integrated (AGPL is incompatible with this project's license). |
| Mouse Tweaks | BSD-3-Clause | yes (2.30) | Not integrated. |
| ShulkerBoxTooltip | MIT | yes (5.2.16) | Not integrated; native Shulker Preview. |
| EMI | MIT | **no 1.21.11 Fabric build** | Not available. |
| REI | MIT | yes (21.11.816) | Not integrated; recommended companion for recipes. |
| Litematica | LGPL-3.0-only | yes (0.26.16) | Not integrated; Schematic Viewer reads `.litematic` files natively and does not replace Litematica. |
| SeedcrackerX | MIT | yes (2.15.6 targets 1.21.11; 2.16.x targets 26.x) | Not integrated or bundled; detected when installed and named in Ore Simulation's panel. |
| OreSim (Modrinth `oresim`) | All rights reserved, **no published source** | yes (1.0.0) | Not used. Ore Simulation is an independent implementation on vanilla's own generator code. |
| Meteor Rejects (where an older OreSim lives) | GPL-3.0 | n/a | Not used (GPL code cannot go into this project). |

**Rendering mods.** Femboy Client draws its world overlays (ESP boxes, lines, labels) itself and hooks the level renderer with
a mixin. Sodium replaces large parts of that renderer, so overlays could be missing, drawn twice or in the wrong order
with Sodium or Iris installed. This was **not tested**; nothing in the client or its documentation claims compatibility.

## Performance and rendering (request §2)

| Feature | Status | Where / notes |
|---|---|---|
| Performance, Balanced, Low-Impact modes | Implemented | Settings › General, per profile (`Performance.java`). |
| FPS and frame-time monitoring | Implemented | Performance HUD, Performance Center (frame graph). |
| Profiling expensive modules | Implemented | Performance Center and Settings › Developer (per-module time). |
| Render limits and update intervals | Implemented | Each ESP/scan module's Advanced fold; capped by the performance mode. |
| Shared resource management | Implemented | One block scanner, entity index and render batch shared by modules. |
| Resource reloads / missing assets | Partial | GUI textures are built-in; the minimap's texture is recreated after a world change. A resource-pack reload while the minimap is on was not tested. |
| Sodium-style rendering optimization | Unsupported | Out of scope for a client utility mod; use Sodium itself (untested together). |
| Shaders | Unsupported | Use Iris; untested together. |
| Entity culling | Unsupported | Overlays skip what is outside the view; vanilla entity rendering is not changed. |
| Dynamic lights | Unsupported | Would need light-engine changes. |
| Mod browsing (Mod Menu) | Optional *new* | Mod Menu's Configure button opens the client menu. |
| Better debug screen (BetterF3) | Partial | Performance HUD, Coordinates (chunk position), Movement Info; F3 itself is unchanged. |
| Zoom | Implemented | Zoom module. |

## HUD and information (request §3)

| Feature | Status | Where / notes |
|---|---|---|
| Coordinates | Implemented | Coordinates HUD (with chunk position and Nether/Overworld conversion). |
| Direction / compass | Implemented | Compass. |
| FPS, frame time, ping | Implemented | Performance HUD. |
| Keystrokes, CPS | Implemented | Keystrokes; CPS in Combat Info. |
| Armor and durability | Implemented | Health & Armor (alerts at a chosen level). |
| Hunger and saturation | Partial | Health & Armor shows food and saturation; no food-value preview on items (AppleSkin's tooltip). |
| Potion / status effects | Implemented | Effects. |
| Cooldowns | Implemented *new* | **Cooldowns**: pearls, chorus, wind charges, shield, goat horns... with seconds left. Attack cooldown is in Weapon Info. |
| Waypoints and death coordinates | Implemented | Waypoints, Death Coordinates. |
| Minimap | Implemented *new* | **Minimap**: loaded chunks in map colors, cave layer under a roof, players, waypoints, last death, rotation. |
| World map | Planned | Needs storage of explored terrain on disk. |
| Block and entity tooltips (Jade) | Implemented *new* | **Look At**: block/creature, its mod, growth, redstone power, harvest tool, break time, health, armor, owner, villager job. |
| Searchable session information | Implemented | Session Stats, Timeline page (filterable), Notifications history. |
| Customizable HUD layouts | Implemented | HUD editor; every new HUD element uses it. |

## Inventory and quality of life (request §4)

| Feature | Status | Where / notes |
|---|---|---|
| Inventory Sorter | Implemented | Inventory Sorter (Sort button). |
| Inventory / hotbar profiles | Implemented | Hotbar Profiles. |
| Smart hotbar | Partial | Auto Weapon Switch and Item Swap; no general "smart hotbar" layout engine. |
| Auto Tool, Auto Armor, Auto Refill | Implemented | Same names. |
| Auto Offhand | Partial | AutoTotem fills the offhand with totems; other offhand items are not managed. |
| Item Swap | Implemented | Item Swap. |
| Durability manager | Partial | Health & Armor alerts; no automatic tool swap on low durability. |
| Consumable tracker | Implemented | Item Counters. |
| Shulker preview | Implemented | Shulker Preview. |
| Chest organization | Implemented | Chest Organizer (Take, Store, Stack, Sort). |
| Inventory search | Implemented *new* | **Inventory Search**: box next to containers; matches names, ids, enchantments and shulker/bundle contents. |
| Better / enchantment / potion tooltips | Planned | Vanilla tooltips are unchanged. |
| Recipe search, crafting calculator | Unsupported | Use REI (MIT, has a 1.21.11 build); not integrated. |
| Item comparison, inventory snapshots, item history | Planned | |
| Slot conflicts between inventory modules | Partial | Inventory modules use the same clicks a player makes and re-check the menu before acting (the shared `InventoryActions` also clears a leftover cursor stack), but there is no shared lock: two automation modules that move the same slots in the same tick can undo each other. Planned. |

## Building and world tools (request §5)

| Feature | Status | Where / notes |
|---|---|---|
| Schematic viewer / workspace | Partial | Schematic Viewer reads Sponge `.schem` (v2/v3, also when named `.schematic`), structure-block `.nbt` and `.litematic` (regions merged); old MCEdit `.schematic` files are not supported. It draws missing and wrong blocks as outlines, not block models. |
| Build progress, material list, missing blocks | Implemented | Schematic Viewer, Material List. |
| Block counter | Implemented | Block Counter. |
| Placement preview / guide | Partial | Schematic outlines; no ghost blocks. |
| Circle, sphere, dome, cylinder generators | Implemented | Shape Planner (also spiral stairs and walls). |
| Block palette analyzer | Partial | Material List counts a schematic's blocks. |
| Area selector, region markers, world notes, waypoint groups | Planned | |
| Local world inspector, chunk information | Partial | Look At, Coordinates (chunk), Chunk Borders. |
| Light level overlay | Implemented | Light Level. |
| Redstone visualization | Partial | Look At shows a block's redstone power; no overlay. |
| Crop and farm analysis | Partial | Look At shows growth; no area analysis. |
| Scan progress, cancellation, export | Implemented | Block ESP and Geode Analyzer show progress; Ore Simulation has progress, Cancel and CSV export *new*. |

## ESP (request §6)

| Feature | Status | Where / notes |
|---|---|---|
| Block ESP (formerly Ore ESP) | Implemented | Block ESP; old settings migrate. |
| Player, entity, item, projectile ESP | Implemented | Entity ESP groups. |
| Storage ESP | Implemented | Storage ESP. |
| Structure markers | Implemented | Structure Finder (singleplayer, needs the seed). |
| Hitboxes, nametags/labels, health bars, distance labels, outlines | Implemented | Hitboxes, Entity ESP labels/health, Entity Outlines. |
| Armor indicators | Partial | Target HUD shows the target's armor; ESP labels do not. |
| Target highlighting | Implemented | Entity ESP colors, Target HUD. |
| Chunk borders, light level | Implemented | Same names. |
| Target lines in each ESP | Implemented | Block, Entity, Storage ESP and Ore Simulation *new*: from the crosshair to each target, with count, distance, color, opacity, thickness, fading, visibility and depth options. |
| Restriction of hidden-information scans | Implemented | Block ESP, Geode Analyzer and Ore Simulation run only in singleplayer and (Block ESP, Geode) on servers in your own network. |

## Seed analysis and ore simulation (request §7)

| Feature | Status | Where / notes |
|---|---|---|
| Ore Simulation | Implemented *new* | **Ore Simulation** (World): predicts ores from your singleplayer world's seed with vanilla's own terrain, surface, cave and ore code, shows them like Block ESP with lines and labels, and marks each prediction *estimated*, *verified* or *not there* as chunks load. Ore filters, radius, limits, follow mode, cache, cancel, CSV export. |
| Measured accuracy | Implemented | In-game test, seed 8675309: fresh chunks far from spawn match almost exactly; near spawn 96% precision / 99% recall (numbers in VERIFICATION.md). |
| Server-side comparison for your own test server | Implemented *new* | `/femboyclient oresim compare [radius] [export]`: operators only, and off until `ore_simulation_command` is `true` in the server's `config/femboyclient-server.json`. Reports totals only. |
| Seed recovery (SeedcrackerX-style cracking) | Unsupported | Not part of Femboy Client. SeedcrackerX (MIT) is a separate mod; when installed, Ore Simulation's panel says so but does not read or control it. |
| Seed source information | Implemented *new* | Ore Simulation's panel shows where the seed comes from (singleplayer world) or why there is none. |
| Ore simulation on other servers | Unsupported by design | There is no way to enter a seed; on servers the module says it needs singleplayer. It cannot be used against anti-xray. |

## Combat and training (request §8)

| Feature | Status | Where / notes |
|---|---|---|
| Aim Assist, Trigger Bot, KillAura, Criticals, W-Tap, Auto Weapon Switch, Auto Attribute Swap, Auto Armor, Auto Totem, Auto Refill | Implemented | Automation section; sandbox ones only in test environments. |
| Target HUD, cooldowns, hitboxes | Implemented | Target HUD, Weapon Info, Cooldowns *new*, Hitboxes. |
| Damage calculator | Partial | Explosion Info (crystal/anchor/bed estimates), Weapon Info (mace and spear); labelled as estimates. |
| Combat timeline | Implemented | Timeline page. |
| Projectile predictor | Implemented | Trajectories. |
| Aim trainer | Implemented | Aim Trainer. |
| Movement trainer, practice dummy, combo replay, loadout comparison | Planned | |

## Movement and utility (request §9)

| Feature | Status | Where / notes |
|---|---|---|
| No Fall, Anti Hunger, Sprint, Safe Walk, Step, Strafe, Speed, Flight, Free Camera | Implemented | Movement section; sandbox ones only in test environments. |
| Elytra HUD, landing prediction | Implemented | Elytra Info, Movement Info (fall damage and landing block). |
| Movement recorder | Planned | |
| Waypoints, death markers | Implemented | Waypoints, Death Coordinates; both on the Minimap *new*. |
| Auto Reconnect, Better Chat, chat timestamps | Implemented | Same names. |
| Chat search | Planned | Better Chat stacks repeats and adds timestamps; no search. |
| Notification center, session journal | Implemented | Notifications history, Timeline page. |
| Favorites, recently used, command palette | Implemented | Home, search (Ctrl+K). |

## Profiles (request §10) and GUI (request §11)

| Feature | Status | Where / notes |
|---|---|---|
| Presets: sword, axe, mace, spear, spear+mace, crystal, anchor, potion, UHC/survival, SMP, elytra, bow, building, mining, farming, exploration, parkour, speedrunning, recording, performance, local testing, developer | Implemented | Profiles › Presets (also bed PvP and authorized testing). |
| Profile contents (modules, settings, HUD, theme, keys, performance mode) | Implemented | |
| Preview, duplicate, import/export, compare, history, revert | Implemented | Profiles page, restore points. |
| Automatic switching with rules, delays, environment limits | Implemented | Rules page; automatic switches never bypass test-environment limits. |
| Sections Home ... Settings | Implemented | Nine sections. |
| Separate "Developer Tools" section | Partial | Developer tools are a group in World and Settings › Developer; a tenth section was left out in 1.1.0 to keep the sidebar short. |
| Search, descriptions, favorites, states, folds, Theme Studio, HUD editor, notifications, import/export, backups, comparison, help | Implemented | |
