# Verification report: Femboy Client 0.4.0

Date: 2026-10-14.

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
| Navigation is exactly Home, Combat, Visuals, World, Player, HUD, Developer, Settings | Verified in the running Minecraft client | Check "Sidebar is exactly ..."; screenshots `gui-*` |
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

## Not verified

- **Visible window on the NVIDIA GPU:** all rendering was checked with Mesa llvmpipe under Xvfb. Real keyboard shortcuts in the code editor were not tried in a visible window.
- **A second real client:** the other player in the tests is a server-side fake player; real movement and latency were not tested.
- **Other server software:** Paper, proxies and anti-xray servers.
- **Performance at scale:** very many entities or storage blocks, or a 16-chunk radius in a busy world.
