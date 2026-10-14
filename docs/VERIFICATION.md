# Verification report: Femboy Client 0.2.0

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
| Navigation is exactly Home, Visuals, World, Player, HUD, Settings | Verified in the running Minecraft client | Check "Sidebar is exactly ..."; screenshots `gui-*` |
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
| Per-setting reset, module reset, tooltips | Implemented, not tested | Present (existing code); not clicked or hovered by the tests |
| Profiles, save and reload, malformed-config recovery | Automated tests passed and verified in the running Minecraft client | In-game: settings of every type survive save/reload; profiles keep independent state. Recovery: unit tests |
| Adding a module needs no GUI changes | Compiled successfully | The new modules use only settings fields and `ModuleRegistry`; pages are built from categories |
| Error isolation (a failing module is turned off) | Automated tests passed | `ModuleManagerTest`; in game, no module errors during the run |

## Visual modules

| Feature | Status | Evidence |
|---|---|---|
| Entity ESP default selection (players and hostile mobs) | Verified in the running Minecraft client | Zombie highlighted; pig, villager and wolf not |
| Entity type selection by registry id | Verified in the running Minecraft client | After selecting `minecraft:pig` through the GUI, the pig is highlighted |
| Pets mode "Always" | Verified in the running Minecraft client | The tamed wolf is highlighted although wolves are not selected |
| Invisible entities skipped by default | Verified in the running Minecraft client | Invisible husk not highlighted |
| Maximum distance | Verified in the running Minecraft client | At 8 blocks, wolf and pig drop out and the zombie stays |
| Names, distance labels, colors per group, outline | Verified in the running Minecraft client | Screenshot `world-entity-esp`: red zombie, green pig, pink pet wolf, labels with distances |
| Fill, through-walls toggle, label limit, "Include invisible" | Implemented, not tested | Settings exist; not changed by the tests |
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
| Results cleared on world or dimension change (also while paused) | Verified in the running Minecraft client (not paused) | Overworld results not shown in the Nether. The paused case was fixed after code review and is not covered by a test |
| Panel: status, coverage, Pause / Read again, cards, details on click | Verified in the running Minecraft client | Panel, cards and details found; screenshots `world-geode-analyzer-panel` and `-details` |
| Explicit denominators, Wilson intervals, evidence quality (not a probability) | Automated tests passed and verified in the running Minecraft client | `StatisticsTest`; the card shows `n = 30` and "Evidence: Limited" (screenshot) |
| Growth-stage display in the world | Verified in the running Minecraft client | Screenshot `world-geode-stage-overlay` |
| Interpretation card quoting controlled tests, with limits | Verified in the running Minecraft client | Rendered in the panel. Its error-rate wording was changed to the range across three runs after the last in-game run (text only; compiled, not re-run) |
| Natural geodes in a normal world | Verified in the running Minecraft client | Seed 8675309: geodes found, re-identified, grew under boosted ticking (`docs/research/natural-world.md`) |
| Research engine and experiment (headless) | Automated tests passed | `./gradlew runGameTest` |

## Player module

| Feature | Status | Evidence |
|---|---|---|
| Player list from the server's player list; self excluded by default | Verified in the running Minecraft client | Singleplayer: 0 entries; with "Include yourself": own entry, known position, game mode, ping |
| Navigation to a waypoint (click in panel), HUD line with distance and arrow, stop | Verified in the running Minecraft client | Check plus screenshot `world-navigation-hud` |
| Runs on any server (no restricted data) | Verified in the running Minecraft client | Dedicated server: Player Finder running while restricted modules are paused |
| Other players: line-of-sight rule, "last seen" with age, "position unknown", search, sorting | Implemented, not tested | No second player was available in the test environment |
| Server API integration for servers you control | Not implemented | Optional in the spec; documented in KNOWN_ISSUES.md |
| Free Camera | Verified in the running Minecraft client | Camera moved 12 blocks, player 0; view returns to the player |

## Restrictions and stability

| Feature | Status | Evidence |
|---|---|---|
| Restricted modules pause on an unauthorized dedicated server and run after authorizing it in Settings | Verified in the running Minecraft client | Real local dedicated server; address typed into Settings |
| Dimension changes | Verified in the running Minecraft client | Overworld → Nether → Overworld: modules keep running, waypoints filtered, no errors |
| No errors in the client log | Verified in the running Minecraft client | Log check (known offline noise excluded and listed) |

## Not verified

- **Visible window on the NVIDIA GPU:** all rendering was checked with Mesa llvmpipe under Xvfb.
- **Real other players:** Player Finder with real other players; Entity ESP on players.
- **Other server software:** Paper, proxies and anti-xray servers.
- **Performance at scale:** very many entities or storage blocks, or a 16-chunk radius in a busy world.
