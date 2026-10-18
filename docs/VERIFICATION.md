# Verification report: Femboy Client 1.1.0

Date: 2026-10-18. Sections up to "Servers and stability" were written for 0.4.0 and still pass; later additions are in the sections after it, ending with 0.9.0.

## 1.1.0 (2026-10-18): interface redesign

Commands: `./gradlew build` and `rm -rf build/run/clientGameTest/config/femboyclient && ./scripts/run-client-tests.sh` (in-game, Xvfb, Mesa llvmpipe); benchmark `./scripts/run-benchmark.sh` for 1.1.0 and for 1.0.0 (temporary worktree), one after the other.

- `./gradlew build`: BUILD SUCCESSFUL. Unit tests: 143, 0 failures (new: the light module state query agrees with the health monitor and never asks for the workload; profiles list the modules they turn on and keep their notes through a rename and a restart; the advanced flag only marks a setting).
- In-game suite: **Result: PASSED (321 checks)**, twice in a row on the final code. New checks (`UxTest`): every module is on exactly one of the section pages; World's groups and folded developer tools; the search finds settings, HUD elements, help topics and actions, and opens a setting folded behind More options; Settings › Help lists guides and glossary and its search narrows; Entity, Block and Storage ESP share Targets / Highlight / Labels / Target lines / Advanced and show a preview; tuning and rarely used settings start folded and open on click; status in words; the Show filter and its empty state; typing narrows a long dropdown; Home's parts hide and show; New profile and Delete through dialogs (Escape keeps the profile, Enter deletes); the HUD editor toolbar moves to the bottom and back; Ctrl+4, Down and Space; no layout overlaps at 854x480, 1280x720, 1920x1080 and menu scales 1x, 2x and Minecraft's; the menu reopens cleanly five times after being closed with the search or a dialog open. Updated checks: navigation (nine sections), Home and search instead of the module grid, Enter opens / Tab switches, help mode and tooltips, module reset with two clicks, profile export from the ⋯ menu.
- Found by the tests and fixed: a dropdown near the bottom of the screen opens upward (the old test assumed downward). Found on the screenshots and fixed: Settings tabs showed only icons at 1280x720 (now labels, and the selected tab always keeps its label); the module header read "Visuals · Visual" (now "Visuals · Shows information"); HUD editor names ran under the "Turn on" buttons; the delete dialog said "its 0 enabled module(s)".
- One run before the final ones crashed in Fabric's client-test network synchronizer while switching from the singleplayer world to the dedicated test server (an `AssertionError` in `NetworkSynchronizer`, no Femboy Client code in the stack), after all 291 singleplayer checks had passed. The next two runs of the same code passed completely. It is recorded here as a test-harness instability seen once under heavy memory pressure.
- Benchmark: no slowdown against 1.0.0 measured in the same session (see `docs/benchmarks/README.md`).
- Screenshots: `docs/screenshots/0018`-`0020` (Home, search, a module opened from the search) and `0097`-`0108` (World groups, Help, Entity ESP with preview, filter, Home, profile dialogs, profiles, HUD toolbar at the bottom, the layout at three window sizes).
- **Not tested:** a real GPU; a real player's first impression (no user testing was done; "understandable without documentation" is a design goal checked only against the screenshots); keyboard focus moving with Tab between all controls (not implemented); screen readers (Minecraft has none for mod menus).

## 1.0.0 (2026-10-18)

Commands: `./gradlew build` (unit tests and server GameTests) and `rm -rf build/run/clientGameTest/config/femboyclient && ./scripts/run-client-tests.sh` (in-game, Xvfb, Mesa llvmpipe).

- `./gradlew build`: BUILD SUCCESSFUL. Unit tests: 140, 0 failures (new: module failure suspension and retry, health states, aliases; preserving unknown modules and settings, before-repair copies, restore points and pruning, diffs, history, rename, per-profile HUD sections, full reset, schema-update snapshot; HUD layout anchoring, legacy format, snapping, undo; rule engine triggers, any/all, cooldowns, loop prevention, dry run, rate limit, validation, JSON; experience formulas; shape generator).
- In-game suite: **Result: PASSED (297 checks)**. New checks (`ProductTest`): Ctrl+K palette search and Enter toggle, Tab opens settings, alias search, Escape, jumping to a settings tab; quick wheel order and toggle, recently used; Theme Studio presets, save/export/import with invalid values skipped, corner style and panel opacity, restoring defaults; HUD editor placing, scaling, hiding (faint in the editor, gone in game), undo, "in combat" condition, independent layouts; profile diff, per-profile HUD layout, history, rename, revert; restore point; validator; rules (notification and visual module, automation refused, dry run), timeline (profile, rule, marker, search); on/off comparison with both halves measured and the module restored, automation excluded; health state; diagnostic report file; folded notifications; Compass, Experience, Session Stats and Shape Planner on the HUD, hollow sphere count and outlines drawn; Mention Alerts whole-word, keyword and own-message handling. The previous checks still pass (sidebar now ten sections, 77 modules, 24 presets, Developer tab).
- Found and fixed by the tests: K as the default palette key took a key the tests (and players) bind to modules, so it is now unbound; Ctrl shortcuts in the menu and HUD editor read only the event's modifier flags, which the test input (and some keyboards) do not set, so they now also check the key state, as the code editor already did.
- Screenshots: `docs/screenshots/0088`-`0097` (palette, Home favorites, Theme Studio, HUD editor, rules, timeline, Performance Center, Developer tab, new modules).
- **Not tested:** a real GPU (all frame numbers come from software rendering), the quick wheel with a physically held key (the test opens it and chooses a slot directly), Mention Alerts with real server chat formats, rules on a multiplayer server, the Performance Center's recommendations under a real slow frame on hardware, macOS Cmd shortcuts.

## Status values

- **Implemented, not tested**
- **Compiled successfully**
- **Automated tests passed**
- **Verified in the running Minecraft client:** a real Minecraft 1.21.11 client was launched (headless, under Xvfb with Mesa llvmpipe). The feature was driven by the client game test through simulated mouse and keyboard input or game commands. The result was checked by an assertion, and for visual features by a screenshot that was inspected.
- **Partially implemented**
- **Blocked**
- **Failed**

## Evidence

- `docs/test-reports/client-game-test-report.txt`: in-game checks, all passing.
- `docs/test-reports/unit-tests.txt`: unit tests, all passing.
- `./gradlew runGameTest`: 6 server GameTests, all passing, including the research experiment that produced `docs/research/`.
- `docs/screenshots/`: every page at 854x480 and 1280x720, plus the world overlays.

## Rename and structure

| Item | Status | Evidence |
|---|---|---|
| Renamed to Femboy Client (name, mod id `femboyclient`, package, lang, menu title, docs) | Verified in the running Minecraft client | Client starts with the new mod id; sidebar title in screenshots; `ResourceFilesTest` checks the metadata |
| Settings copied from `config/nova/` on first launch, old folder kept | Automated tests passed | `LegacyConfigMigrationTest` (3 tests). Not exercised with a real NOVA install |
| Navigation is exactly Home, Combat, Movement, Visuals, Inventory, Trackers, World, HUD, Developer, Profiles, Settings (0.5.0) | Verified in the running Minecraft client | Check "Sidebar is exactly ..."; screenshots `gui-*` |
| Development pages removed (Dashboard, World Analysis, Test Scenarios, Test Results) | Verified in the running Minecraft client | Check "No development pages remain" |
| Only categories with modules get a sidebar entry | Implemented, not tested | All four categories have modules, so the rule never triggered |
| Diagnostics as a tab under Settings | Verified in the running Minecraft client | Check "Settings has a Diagnostics tab"; screenshot `settings-diagnostics` |

## Module framework and GUI

| Feature | Status | Evidence |
|---|---|---|
| Home: search across all modules, category filter, opening a module on its page | Verified in the running Minecraft client | Typed "geode", clicked the result: World page opened with the Geode Analyzer selected |
| Category pages search only their own modules | Verified in the running Minecraft client | "chunk" on Visuals lists only Chunk Borders |
| On/off switch, key capture, in-world key toggle | Verified in the running Minecraft client | Switch click; key K captured; K pressed in world toggles off/on |
| Sliders, dropdowns | Verified in the running Minecraft client | Slider end click sets 4; dropdown opens and selects Cyan |
| Color setting (swatches, `#RRGGBB` in file) | Verified in the running Minecraft client | Swatch click sets #F29E4C; value survives save/reload; hex in profile file; `CollectionSettingsTest` |
| Id-list setting (grouped, searchable checklist, registry ids) | Verified in the running Minecraft client | Expanded the list, typed "pig", pressed All: `minecraft:pig` added, other ids kept; screenshots `visuals-entity-esp-types`, `...-filter-pig`; unit tests for validation, unknown ids, malformed JSON |
| Per-setting reset, module reset, tooltips | Verified in the running Minecraft client | Reset button restores the default; module Reset restores all settings; hovering a setting shows its description after the delay (screenshot `visuals-tooltip`) |
| Profiles, save and reload, malformed-config recovery | Automated tests passed and verified in the running Minecraft client | In-game: settings of every type survive save/reload; profiles keep independent state. Recovery: unit tests |
| Adding a module needs no GUI changes | Compiled successfully | The new modules use only settings fields and `ModuleRegistry`; pages are built from categories |
| Error isolation (a failing module is turned off) | Automated tests passed | `ModuleManagerTest`; in game, no module errors during the run |

## Visual modules

| Feature | Status | Evidence |
|---|---|---|
| Entity ESP default selection (players and hostile mobs) | Verified in the running Minecraft client | Zombie highlighted; pig, villager and wolf not. On the dedicated server, the other player is highlighted |
| Entity type selection by registry id | Verified in the running Minecraft client | After selecting `minecraft:pig` through the GUI, the pig is highlighted |
| Pets mode "Always" | Verified in the running Minecraft client | The tamed wolf is highlighted although wolves are not selected |
| Invisible entities skipped by default | Verified in the running Minecraft client | Invisible husk not highlighted |
| Maximum distance | Verified in the running Minecraft client | At 8 blocks, wolf and pig drop out and the zombie stays |
| Names, distance labels, colors per group, outline | Verified in the running Minecraft client | Screenshot `world-entity-esp`: red zombie, green pig, pink pet wolf, labels with distances |
| Fill, through-walls toggle, label limit, "Include invisible" | Implemented, not tested | Settings exist; not changed by the tests |
| Outline thickness changes the drawn outline | Verified in the running Minecraft client | Magenta pixels in screenshots: 2354 at 1x, 7968 at 5x |
| Outline opacity | Verified in the running Minecraft client | Strongly magenta pixels: 7968 at 100%, 36 at 20% |
| Per-category outline switch | Verified in the running Minecraft client | Hostile outline off: 2354 -> 0 pixels |
| Tracer lines | Verified in the running Minecraft client | 750 magenta pixels without, 2900 with |
| Label sizes (name, distance, padding) change the drawn label | Verified in the running Minecraft client | Drawn label rectangles: name 0.75x -> 1.5x width 30 -> 55; distance 0.65x -> 1.6x height 16 -> 24; padding 2 -> 6 adds 8 px each way |
| Distance can be turned off independently of the name | Verified in the running Minecraft client | Label height 16 -> 10 |
| Health indicator: off by default, bar / bar and number, size, per category | Verified in the running Minecraft client | Drawn label flags and sizes; screenshot `world-esp-overhaul` |
| Long names shortened; labels at the same spot don't overlap; labels at 854x480 | Verified in the running Minecraft client | Long-name label 112 px wide (limit 110 + padding); no intersecting label rectangles; screenshot `world-esp-overhaul-854x480` |
| Old underline under labels removed | Verified in the running Minecraft client | Screenshots `world-esp-overhaul`, `world-storage-esp` |
| ESP with many entities | Verified in the running Minecraft client | 254 zombies: highlights capped at 64; 22.5 ms vs 21.3 ms average frame with/without ESP and Trackers (Mesa software rendering); no errors |
| Color override for chosen types, text color, background opacity | Implemented, not tested | Settings exist; not changed by the tests |
| Trackers: on-screen marker, edge arrow for targets behind, waypoints labelled, stale targets removed, nothing after disabling | Verified in the running Minecraft client | Indicator data; screenshot `visuals-trackers` |
| Trackers: last-seen player labelled with its age, dropped after logging off; no overworld waypoints in the Nether | Verified in the running Minecraft client | Dedicated server with a second (fake) player; dimension test |
| Fullbright: brightens, intensity, restores exactly, never writes the video setting, runs in the Nether | Verified in the running Minecraft client | Mean screen brightness in a closed stone room: 9.1 off, 86.2 at 100%, 35.6 at 30%, 9.1 off again; gamma option 0.5 -> 0.5 |
| Storage ESP default selection by block id (chests, copper chests, trapped chests, barrels, shulker boxes) | Verified in the running Minecraft client | Check lists all five; furnace and hopper not highlighted |
| Storage ESP: adding a block type, removal noticed at the next scan | Verified in the running Minecraft client | Furnace added after selection; removed chest gone after the next scan |
| Storage ESP custom colors, labels, limits | Implemented, not tested | Settings exist; not changed by the tests |
| Both ESP modules clear their highlights when disabled | Verified in the running Minecraft client | Checks after disabling |
| Chunk Borders | Verified in the running Minecraft client | Screenshot `world-chunk-borders` |

## Geode Analyzer

| Feature | Status | Evidence |
|---|---|---|
| Counts equal the server's exact census (four stages, budding blocks) | Verified in the running Minecraft client | Vanilla geode fixture: analyzer counts identical to the server census |
| Partial view reported as incomplete, never complete or absent | Verified in the running Minecraft client | 1-chunk radius: geode incomplete with lower counts |
| Unloaded or unread chunks never treated as empty | Automated tests passed | `ChunkObservationCacheTest`: uncached columns are unavailable in the snapshot |
| Automatic operation: radius change and growth picked up without pressing anything | Verified in the running Minecraft client | Radius 1 → 4 picked up automatically; after 20 more random ticks on the server, counts matched the new census within the 2 s interval |
| Unchanged data is not re-analyzed; distant chunks evicted | Automated tests passed | `ChunkObservationCacheTest` (version only changes on content change, eviction) |
| Pause and resume | Verified in the running Minecraft client | While paused, counts stayed at the old census although the server changed; after resuming, they caught up |
| No double counting | Verified in the running Minecraft client | The fixture geode appears once |
| Results cleared on world or dimension change (also while paused) | Verified in the running Minecraft client | Analyzer paused before travelling: overworld results dropped in the Nether |
| Panel: status, coverage, Pause / Read again, cards, details on click | Verified in the running Minecraft client | Panel, cards and details found; screenshots `world-geode-analyzer-panel` and `-details` |
| Explicit denominators, Wilson intervals, evidence quality (not a probability) | Automated tests passed and verified in the running Minecraft client | `StatisticsTest`; the card shows `n = 30` and "Evidence: Limited" (screenshot) |
| Growth-stage display in the world | Verified in the running Minecraft client | Screenshot `world-geode-stage-overlay` |
| Interpretation card quoting controlled tests, with limits | Verified in the running Minecraft client | Rendered in the panel; error rates quoted as the range across three research runs |
| Natural geodes in a normal world | Verified in the running Minecraft client | Seed 8675309: geodes found, re-identified, grew under boosted ticking (`docs/research/natural-world.md`) |
| Research engine and experiment (headless) | Automated tests passed | `./gradlew runGameTest` |

## Player module

| Feature | Status | Evidence |
|---|---|---|
| Player list from the server's player list; self excluded by default | Verified in the running Minecraft client | Singleplayer: 0 entries; with "Include yourself": own entry, known position, game mode, ping |
| Navigation to a waypoint (click in panel), HUD line with distance and arrow, stop | Verified in the running Minecraft client | Check plus screenshot `world-navigation-hud` |
| Other players in range: listed with their real position | Verified in the running Minecraft client | Dedicated server with a second (fake) player: entry "In range", position within 0.5 blocks of the server position |
| Last seen after leaving range; gone after logging off | Verified in the running Minecraft client | Same player removed from the world: entry "Last seen" with the old position; removed from the player list: entry gone |
| Search and sorting with other players | Implemented, not tested | Only one other player in the test |
| Player Alert: warning when a player comes into range (chat) | Verified in the running Minecraft client | "Alice (5 m ↑) is in range"; screenshot `multiplayer-player-alert` shows the chat line |
| Player Alert: warning when they leave (setting on) | Verified in the running Minecraft client | "Alice left your range" |
| Player Alert: sound, pop-up, repeat delay, ignore NPCs, "logged off" note | Implemented, not tested | Not changed or triggered by the tests |
| Server API integration for servers you control | Not implemented | Optional in the spec; documented in KNOWN_ISSUES.md |
| Free Camera in singleplayer | Verified in the running Minecraft client | Camera moved 11.8 blocks, player 0; view returns to the player |
| Free Camera: own rotation (mouse turns the camera, not the player) | Verified in the running Minecraft client | Simulated mouse movement: camera yaw 0 -> 45, player yaw 0 -> 0 |
| Free Camera: up key rebound in the menu and used | Verified in the running Minecraft client | Rebound to R through the key button; holding R raised the camera 8 blocks |
| Free Camera: edge of loaded chunks shown; "Stay inside loaded chunks" stops there | Verified in the running Minecraft client | HUD warning (screenshot `visuals-free-camera-edge`); with the setting the camera stopped at z 105.5 |
| Free Camera: occlusion culling off while detached, restored afterwards; re-attaches after a dimension change | Verified in the running Minecraft client | Checks in singleplayer and in the Nether trip |
| Free Camera on a dedicated server | Verified in the running Minecraft client | Camera flies 12 blocks; player moved 0.000 on server and client; a rotation made meanwhile reaches the server (yaw 90.0); after returning, server and client positions differ by 0.000. Screenshot `multiplayer-free-camera` |
| Free Camera blocks attacks aimed from the camera | Verified in the running Minecraft client | Crosshair on a block under the camera, left click: no arm swing and the block stays. Control after returning: swing, block broken |
| Both Free Camera mixins are needed | Verified in the running Minecraft client | Run without `LocalPlayerMixin`: "server keeps receiving updates" fails (server yaw 0.0). Run without `MinecraftMixin`: "does not attack" fails (swung=true) |

## Combat

| Feature | Status | Evidence |
|---|---|---|
| Auto Attribute Swap: the server applies the hit with the swapped item | Verified in the running Minecraft client | Dedicated server, survival: sword in slot 1, axe in slot 2; after the hit the axe lost 2 durability, the sword none |
| Switches back in the same tick, on client and server | Verified in the running Minecraft client | Selected slot 0 on client and server right after; swap record "held during the hit", "switched back" |
| Default axe condition (only against a raised shield) | Verified in the running Minecraft client | Normal mob: no swap (sword used) |
| Axe against a mob that is actually blocking with a shield | Implemented, not tested | No blocking target in the test |
| Per-item cooldowns | Verified in the running Minecraft client | 1 s: no second swap ("Axe on cooldown"); 3 s: a hit 1.5 s later still not swapped |
| Swap-back delay | Verified in the running Minecraft client | Delay 3: axe selected right after the hit, sword again later on client and server |
| Missing item skipped safely | Verified in the running Minecraft client | Axe removed: "no axe in hotbar", slot unchanged |
| Mace, spear, sword, trident and custom rules; player/mob filter | Implemented, not tested | Same code path as the axe rule; not exercised |
| Item cards (icon, toggle, cooldown, conditions, reset) | Verified in the running Minecraft client | Screenshot `combat-auto-attribute-swap` |

## Developer: Java Runner

| Feature | Status | Evidence |
|---|---|---|
| Typing in the editor and pressing Run | Verified in the running Minecraft client | Typed program printed "answer 42" |
| Separate process, time limit, Stop, output limit, missing Java, System.exit, compile errors with line numbers, exceptions | Automated tests passed and verified in the running Minecraft client | `JavaRunnerTest` (13 tests, real worker processes); in game: timeout, Stop and output limit with the worker process gone afterwards |
| The game keeps running while a program runs | Verified in the running Minecraft client | 20 ticks took 1.00 s while the worker looped |
| Minecraft not reachable from snippets | Verified in the running Minecraft client | "package net.minecraft.client does not exist" |
| Snapshot API reads real client values | Verified in the running Minecraft client | Printed the Free Camera speed as set in the menu |
| Risky code needs confirmation; Cancel runs nothing | Verified in the running Minecraft client | "deletes files"; no run started; screenshot `developer-java-runner-confirm` |
| Copy and Clear | Verified in the running Minecraft client | Clipboard "answer 42"; output cleared |
| Runner with the Minecraft launcher's own Java | Not verified | Tested with a JDK; the launcher's Java may lack a compiler (documented) |

## Settings framework

| Feature | Status | Evidence |
|---|---|---|
| Key, text and panel-only settings, sections | Automated tests passed and verified in the running Minecraft client | `KeyAndTextSettingsTest`; key, text, panel settings and the runner program survive save and reload in game; Entity ESP shows 7 sections |

## Servers and stability

| Feature | Status | Evidence |
|---|---|---|
| Every module runs on a dedicated server (no authorization) | Verified in the running Minecraft client | Real local dedicated server: Geode Analyzer, Entity ESP, Storage ESP, Player Finder, Player Alert and Free Camera all running |
| Old `authorizedServers` config entry ignored and dropped | Automated tests passed | `ConfigurationManagerTest` |
| Dimension changes | Verified in the running Minecraft client | Overworld → Nether → Overworld: modules keep running, waypoints filtered, no errors |
| No errors in the client log | Verified in the running Minecraft client | Log check (known offline noise excluded and listed) |

## Foundation (0.5.0)

| Feature | Status | Evidence |
|---|---|---|
| Every module off by default and at first start | Verified in the running Minecraft client | Checks "No module is on by default" and "No module is on at the first start" (fresh config folder) |
| Emergency off key: all modules off, camera returned, automatic switching paused | Verified in the running Minecraft client | Bound to F7 in the test and pressed with Free Camera, Fullbright, Entity ESP and Coordinates on: nothing left on, camera back on the player |
| Hold-mode keys | Automated tests passed and verified in the running Minecraft client | `ModuleManagerTest` (hold, release, missed release, saved mode); in game, Fullbright runs while G is held and stops on release |
| Duplicate, export, import (with validation), reset profile, overwrite | Automated tests passed | `ConfigurationManagerTest`: garbage, wrong shapes and bad names rejected; invalid values dropped with warnings; nothing applied before switching |
| Export from the Profiles page | Verified in the running Minecraft client | Clicking Export puts the profile JSON on the clipboard |
| Automatic switching by held item | Verified in the running Minecraft client | Sword → `auto_sword` (Fullbright on), mace → `auto_mace` (Fullbright off, Coordinates on), unmatched item keeps the profile, a hand-picked profile is kept until the item changes |
| Rule parsing and order | Automated tests passed | `ProfileRuleTest`, invalid saved rules skipped with a warning |

## Combat, movement and inventory information (0.5.0)

Where possible, each module was checked against what the game really did in the same test, not only against its own formula.

| Feature | Status | Evidence |
|---|---|---|
| Combat Info: click, attack, landed hit, reach | Verified in the running Minecraft client | A real left click on a husk 3 blocks ahead: 1 click, 1 attack, 1 landed, reach 2.70 m (geometry: 3.2 - 0.5) |
| Combat Info: combo counts and ends on damage | Verified in the running Minecraft client | 2 of 2 hits counted, combo 0 after `/damage`. A burning zombie earlier made a hit not land (fire immunity), and the tracker correctly reported it as not landed |
| Target HUD: crosshair target, lock, unlock | Verified in the running Minecraft client | Screenshot `combat-target-hud` (name, distance, health bar, HP, armor) |
| Weapon Info: mace smash bonus | Verified in the running Minecraft client | Equal to the server's `MaceItem.getAttackDamageBonus` at falls of 2, 3, 5, 8, 12 and 30 blocks |
| Weapon Info: spear, bow, crossbow and trident lines | Compiled successfully | Read from the item components and vanilla methods; not exercised in game |
| Movement Info: fall damage prediction | Verified in the running Minecraft client | 20-block fall in survival: predicted 17, took 17 |
| Elytra Info | Compiled successfully | Not tested in flight |
| Crystal & Anchor Info: placement spot and focus | Verified in the running Minecraft client | Obsidian within reach listed; under the crosshair it is "Crystal here"; screenshot `combat-crystal-placement`. Loot lying in the crystal space blocked the spot, as it does in vanilla |
| Crystal damage estimate | Verified in the running Minecraft client | Crystal 9 blocks away, no armor, Normal: estimate 14.13, damage taken 14.13 |
| Anchor status and armored/enchanted estimates | Compiled successfully | Not tested in game |
| Item Counters: totals per item and potion type, refill reminder | Verified in the running Minecraft client | 3 totems (inventory only → refill reminder), 5 golden apples, 2 Healing and 1 Swiftness splash potions |
| Health & Armor: durability and warning | Verified in the running Minecraft client | Diamond chestplate with 8 durability listed as "8  2%" in red |
| Effects | Verified in the running Minecraft client | "Speed II 0:29" |
| Hitboxes and filter | Verified in the running Minecraft client | 2 zombies drawn, 0 with hostile mobs turned off; screenshot `visuals-hitboxes` |
| Trajectories | Verified in the running Minecraft client | Snowball thrown at -20° pitch: predicted landing 0.12 and 0.02 blocks from where the real snowball hit (two runs). Bow and crossbow paths use the same simulation but were not compared with real arrows |

## Presets (0.5.0)

| Feature | Status | Evidence |
|---|---|---|
| All 10 presets use existing modules and valid values | Verified in the running Minecraft client | Each preset validated against the registered modules: no warnings |
| Switching preset profiles does not leak modules | Verified in the running Minecraft client | sword → crystal → elytra → sword: exactly each preset's modules are on |
| Reset to preset | Verified in the running Minecraft client | A changed setting and an extra module are restored and turned off |
| Presets section of the Profiles page | Verified in the running Minecraft client | Screenshot `gui-1280x720-9-profiles` |

## Automation (0.6.0)

| Feature | Status | Evidence |
|---|---|---|
| Private-address check and test-environment gating | Automated tests passed and verified in the running Minecraft client | `PrivateNetworkTest` (loopback, LAN, link-local, 100.64/10, IPv6 ULA vs public addresses; module enabled but not running outside, starts and stops with the environment); singleplayer and the localhost dedicated server count as test environments |
| KillAura | Verified in the running Minecraft client | Facing away from a husk: turned to 0.0° and hit it once at full charge (server health 20 → 14.1) |
| Trigger Bot | Verified in the running Minecraft client | Husk under the crosshair hit once (20 → 14.1) |
| Aim Assist | Verified in the running Minecraft client | 34.2° → 3.0° in 30 ticks, crosshair on the husk, no damage dealt |
| Reach | Verified in the running Minecraft client | Husk 4.5 blocks away: not reachable before; with +2 range 5.0, a real click hurt it; range back to 3.0 when off |
| Velocity | Verified in the running Minecraft client | `/damage ... by` a husk pushed you 1.69 blocks without it, 0.00 with 0% |
| Flight | Verified in the running Minecraft client | Survival: hovered (no height change in 20 ticks), +4.5 blocks with jump, server position agreed, fell when off, no flying ability left |
| AutoTotem | Verified in the running Minecraft client | Totem moved from inventory slot to offhand on client and server |
| Auto StunSlam | Verified in the running Minecraft client | Falling 4.8 blocks onto a player holding up a shield: shield disabled, damage through it (20 → 13.2 with Resistance II), back to the mace slot on client and server |
| Auto Attribute Swap: spear reach | Verified in the running Minecraft client | Husk 3.5 blocks away: a sword click did nothing; with the rule one click jabbed it with the spear (20 → 14.1) and returned to the sword slot on client and server |
| Auto Attribute Swap: spear lunge | Verified in the running Minecraft client | Holding attack with bread: nothing without the rule; with it 6 lunges in 20 ticks (Lunge III spear, 150 ms cooldown), 18.1 blocks forward, server position within 0.01, slot stayed on the bread |

## GUI and new modules (0.7.0)

| Feature | Status | Evidence |
|---|---|---|
| Themes and custom accent | Verified in the running Minecraft client | Switching to Midnight magenta changes the accent to #E05CC8; a custom accent #33CC99 overrides it; screenshot `theme-midnight-magenta` |
| HUD editor | Verified in the running Minecraft client | A block dragged with the mouse is stored and drawn at the new position; right-click returns it to its corner; screenshot `hud-editor` |
| Sandbox gating of every Sandbox-tier module | Verified in the running Minecraft client | On a simulated public server none of them runs while enabled; in singleplayer all of them run |
| Zoom | Verified in the running Minecraft client | Field of view 70 → 17.5 while zooming |
| Ore ESP | Verified in the running Minecraft client | Both placed diamond ores found; "touching air only" keeps the exposed one; screenshot `visuals-ore-esp` |
| Block Counter | Verified in the running Minecraft client | Diamond Ore = 2, scanned 81/81 chunks |
| Light Level | Verified in the running Minecraft client | A dark spot is marked and disappears after a torch is placed; screenshot `visuals-light-level` |
| Block Highlight | Verified in the running Minecraft client | The block under the crosshair is reported |
| Better Chat | Verified in the running Minecraft client | A repeated line is stacked with a timestamp: `[00:07] femboy repeat x2` |
| Notifications | Verified in the running Minecraft client | A module toggle appears as a toast; screenshot `utilities-notification` |
| Auto Reconnect | Verified in the running Minecraft client | After a disconnect the screen shows "Reconnecting in 60 s"; the reconnect itself was not run |
| Death Coordinates | Verified in the running Minecraft client | Death at 3 / -60 / 7 recorded with a waypoint |
| Auto Tool | Verified in the running Minecraft client | Slot 1 (pickaxe) selected while mining stone, back to slot 0 afterwards |
| Inventory Sorter | Verified in the running Minecraft client | Mixed slots sorted and merged on the server: apple 1, dirt 10, stone 8 |
| Auto Armor | Verified in the running Minecraft client | Leather chestplate swapped for the diamond one from the inventory (server agrees) |
| Auto Refill | Verified in the running Minecraft client | A hotbar stack of 1 golden apple topped up to 11 (server) |
| Auto Offhand | Verified in the running Minecraft client | Shield moved to the offhand |
| Speed | Verified in the running Minecraft client | 15.3 blocks in the test time vs 4.1 without |
| No Slow | Verified in the running Minecraft client | Walking while drawing a bow: 4.06 blocks vs 0.81 without |
| Step | Verified in the running Minecraft client | Walked onto a 1-block wall: y -59.00 vs -60.00 without |
| Safe Walk | Verified in the running Minecraft client | Stayed on the ledge at y -58.00; fell to -60.00 without |
| No Fall | Verified in the running Minecraft client | 20-block fall (17 damage without it): health stayed 20 |
| Auto Weapon Switch | Verified in the running Minecraft client | Switched to the sword slot when aiming; the hit took the husk to 14.1 |
| Auto Criticals | Verified in the running Minecraft client | Hit dealt 8.86 (critical) |
| Sprint Reset | Verified in the running Minecraft client | Sprint paused after the hit and resumed |
| All 17 presets | Verified in the running Minecraft client | Each preset validated against the registered modules: no warnings |
| Profile switch delay | Verified in the running Minecraft client | Automatic switching still passes with the 4-tick delay |
| Shulker Preview | Verified in the running Minecraft client | A shulker box gets the item-grid tooltip (first item diamond) only while on; the drawn tooltip was not screenshotted |
| Sprint | Verified in the running Minecraft client | Sprinting while walking forward |
| Elytra Flight, Strafe Assist, Keystrokes key highlighting | Implemented, not tested | Compiled; no in-game check |

## GUI layout and remaining modules (0.8.0)

| Feature | Status | Evidence |
|---|---|---|
| Home grid, left-click toggles, right-click settings panel, Keybinds tab, Esc closes | Verified in the running Minecraft client | Right-click on Geode Analyzer opened its panel and left it off; left-click turned it on and off; screenshots `home-grid`, `home-settings-panel` |
| Item Swap | Verified in the running Minecraft client | Totem swapped from inventory into the held slot and back (server agrees); golden apple selected in the hotbar and back |
| Hotbar Profiles | Verified in the running Minecraft client | Saved layout restored after the items were moved to other slots (server: sword, pickaxe, cobblestone) |
| Chest Organizer | Verified in the running Minecraft client | Buttons on the chest screen; Sort gave dirt 5, stone 7 (merged); Stack moved the stone but not the apples; Take emptied the chest (server agrees); screenshot `inventory-chest-organizer` |
| Anti Hunger | Verified in the running Minecraft client | 30 ticks of sprinting: the server counted 808 cm of sprinting without it, 0 cm with it (the same server branch charges sprint hunger) |
| Auto Pot | Verified in the running Minecraft client | At 6 health one healing potion was thrown; health rose to 10.8; slot and pitch restored |
| AutoCrystal | Verified in the running Minecraft client | Placed 2 crystals on obsidian next to a husk, broke 1; the husk died |
| AutoAnchor | Verified in the running Minecraft client | Placed, charged and detonated an anchor next to a husk in the overworld; the husk died |
| Entity Outlines | Verified in the running Minecraft client | The husk glows with the vanilla outline only while on; screenshot `visuals-entity-outlines` |
| Entity ESP equipment icons | Verified in the running Minecraft client | Husk label drawn with 2 icons (helmet, sword); screenshot `visuals-esp-equipment` |
| Aim Trainer | Verified in the running Minecraft client | Aimed click = 1 hit with a new target, click at the sky = 1 miss |
| Schematic Viewer | Verified in the running Minecraft client | Test-written `.nbt`, `.schem` (v2) and `.litematic` files read identically; placed schematic compared: 1 placed, 1 wrong |
| Material List | Verified in the running Minecraft client | 1 oak planks left of 1, carrying 3 |
| Structure Finder | Verified in the running Minecraft client | Normal world (seed 8675309): a plains village and a stronghold found; on the dedicated server it is unavailable with the seed explanation |
| Player Alert auto leave | Verified in the running Minecraft client | A second player 6 blocks away: the client disconnected to the server list and auto leave turned itself off |
| Moving Aim Trainer targets, Hotbar Profiles on profile switch, schematic rotation/layer | Implemented, not tested | Compiled; no in-game check |

## Performance, ESP target lines and menu redesign (0.9.0)

| Feature | Status | Evidence |
|---|---|---|
| Ore ESP renamed Block ESP, settings migrated | Automated tests passed | `ConfigMigrationTest`: a schema-1 profile with `ore_esp` loads into `block_esp` (on, radius 7, only exposed), is saved without the old id, `.bak` kept; imported old profiles are migrated too |
| Block ESP follows block changes | Verified in the running Minecraft client | Breaking and placing an ore updated the list within 3 ticks with 0 additional chunk reads and 2 single-block updates |
| Block ESP target lines and visibility modes | Verified in the running Minecraft client | All targets: 2 lines; only visible: 1 (the buried ore has none); only on screen: 0 facing away, 2 facing the ores; screenshot `block-esp-target-lines` |
| Block ESP only in test environments | Verified in the running Minecraft client | With a simulated public server it stopped and gave the test-environment reason; the sandbox check in AutomationTest now includes Block ESP, Block Counter and Geode Analyzer |
| Entity ESP lines per group | Verified in the running Minecraft client | Husk and item tracked; 1 line with "Lines to items" off, 2 with it on; screenshot `entity-esp-target-lines` |
| Storage ESP lines and immediate rescan | Verified in the running Minecraft client | Line to a chest; the broken chest disappeared 4 ticks later (scan interval 20 ticks) |
| Trackers target lines | Verified in the running Minecraft client | At least one line drawn to a waypoint |
| Lines start at the crosshair | Verified in the running Minecraft client | The line origin projects to the middle of the screen within 0.75 GUI pixels |
| Performance modes | Verified in the running Minecraft client | Low impact paused KillAura with the reason "Paused in Low-Impact mode..."; Balanced resumed it; an interval of 2 ticks became 6 (low impact) and 4 (performance); performance mode turns menu animations off |
| Performance mode per profile | Automated tests passed | `ConfigMigrationTest`: the mode set in one profile comes back when switching back to it, the other profile keeps its own |
| Module conflicts | Verified in the running Minecraft client, automated tests passed | Turning Aim Assist on turned KillAura off with the message "KillAura turned off"; `ModuleManagerTest` checks both directions |
| Shared entity index, HUD built per tick | Verified in the running Minecraft client | With Entity ESP, Trackers and Hitboxes on, the entity list was built at most once per tick; HUD text rebuilt at most once per tick |
| Background config saving | Automated tests passed | `ConfigMigrationTest`: an automatic save hands the write to the writer and writes nothing on the calling thread; the writer writes the captured state |
| Repeated log messages counted | Automated tests passed | `DiagnosticLoggerTest`: 50 identical errors give one entry and "repeated 49 more time(s)" |
| World change, Low-Impact pause, category reset, cached lookups | Automated tests passed | `ModuleManagerTest` |
| Menu: nine sidebar sections, header search, profile selector, notifications | Verified in the running Minecraft client | Sidebar check; "geode" in the header search lists the Geode Analyzer on Home and Escape only leaves the field; the profile chip opens the profile list; the bell opens the notification list and marks messages read; screenshots `gui-v2-home`, `gui-v2-profile-menu`, `gui-v2-notifications` |
| Collapsible sections, typed values, page reset | Verified in the running Minecraft client | Entity ESP "Performance" starts folded and opens with a click; "96" typed for Maximum highlights; Reset Visuals asks for a second click, then restores Chunk Borders' radius |
| Reduce motion | Verified in the running Minecraft client | The menu works with reduced motion on; screenshot `gui-v2-home` |
| Smooth shapes, shadows, glows, icons | Verified in the running Minecraft client | Screenshots of every page at 854x480 and 1280x720 inspected |
| Performance before and after | Measured | [docs/benchmarks](benchmarks/README.md): module tick work 2.10-2.19 ms to 0.18-0.23 ms per tick, allocations 3,130 KB to 37 KB per tick, client drawing per frame 4.84-5.42 ms to 3.07-3.62 ms (llvmpipe) |
| Texture fallback (plain shapes when the art is missing) | Implemented, not tested | Compiled; no test removes the textures |
| Lines while view bobbing in a real walk, third person, free camera | Implemented, not tested | The projection check was done standing still |

## Not verified

- **Visible window on the NVIDIA GPU:** all rendering was checked with Mesa llvmpipe under Xvfb. Real keyboard shortcuts in the code editor were not tried in a visible window.
- **A second real client:** the other player in the tests is a server-side fake player; real movement and latency were not tested.
- **Other server software:** Paper, proxies and anti-xray servers.
- **Not built:** hidden (server-only) rotations or other stealth features, on purpose.
- **Test harness:** one of three runs after the last change stopped when the Fabric test harness's network synchronizer failed while connecting to the dedicated server ("Network synchronizer in invalid state"). All checks run before that passed; the other two runs passed completely.
- **Performance at scale:** the benchmark covers 160 entities, 121 chests and 4,802 ores in singleplayer; busy servers, many players and the largest radii were not measured. Frame rates were measured only with software rendering.
