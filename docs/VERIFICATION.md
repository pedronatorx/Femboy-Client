# Final verification report

Date: 2026-10-14. Commit: the "Stage 9/10" commit on `main`.

**Status values (as defined in the spec):**
- Implemented, not tested
- Compiled successfully
- Automated tests passed
- Verified in the running Minecraft client
- Partially implemented
- Blocked
- Failed

"Verified in the running Minecraft client" means a real Minecraft 1.21.11 client was launched (headless, under Xvfb), the feature was exercised by the client game test, and the result was checked by an assertion and/or a screenshot that I inspected.

**Evidence files:**
- `docs/test-reports/client-game-test-report.txt`: 59 in-game checks, all passing.
- `docs/test-reports/unit-tests.txt`: 49 unit tests, all passing.
- Server GameTests: 6 tests, all passing (`./gradlew runGameTest`).
- `docs/screenshots/`: 28 screenshots.
- `docs/research/`: research reports.

## Build and environment

| Item | Status | Evidence |
|---|---|---|
| Fabric 1.21.11 project builds | Compiled successfully | `./gradlew build`; jar `build/libs/nova-0.1.0.jar`, Java 21 class files (version 65) |
| Metadata and resource files | Automated tests passed | `ResourceFilesTest`: fabric.mod.json, mixin config, gametest entrypoints, lang, PNG sizes |
| Mixins apply at runtime | Verified in the running Minecraft client | Client starts; key-bind test passes (keyboard mixin); free-camera screenshot without the hand (renderer mixin) |

## Framework

| Feature | Status | Evidence |
|---|---|---|
| Module lifecycle (register, enable, tick, disable, save, load) | Automated tests passed | `ModuleManagerTest.lifecycleRunsInOrder` and others |
| Error isolation (a failing module is turned off, others continue) | Automated tests passed | `aFailingModuleIsDisabledWithoutAffectingOthers`; in game, the "No module errors" checks |
| Pausing without a world / outside authorized environments | Verified in the running Minecraft client | Dedicated-server test: paused when unauthorized, running when authorized, paused again when removed |
| Settings validation and reset | Automated tests passed | `SettingsTest` (8 tests) |
| Configuration persistence, autosave, profiles | Verified in the running Minecraft client | Unit tests plus in-game "setting survives save and reload" and "profiles keep independent module state" |
| Recovery from malformed config files | Automated tests passed | `ConfigurationManagerTest` (corrupt file kept aside, restored from backup, defaults without a backup) |
| Key binds (capture in GUI, toggle in world) | Verified in the running Minecraft client | Key K bound through the GUI, then pressed in the world: toggled off and on |
| Diagnostic logger and performance monitor | Verified in the running Minecraft client | Diagnostics page screenshot with frame graph and per-module cost |

## ClickGUI

| Feature | Status | Evidence |
|---|---|---|
| Seven screens (Dashboard, Modules, Analysis, Scenarios, Results, Diagnostics, Settings) | Verified in the running Minecraft client | Screenshots at 854x480 and 1280x720 |
| Sidebar navigation, opening with Right Shift, closing with Escape | Verified in the running Minecraft client | Real key presses in the test |
| Search and category filter | Verified in the running Minecraft client | Typed "chunk": name match listed first, then setting matches |
| Switches, sliders, dropdowns, text fields, key capture | Verified in the running Minecraft client | Mouse clicks on a switch, a slider end (value 4), a dropdown option (Cyan), text fields |
| Reset-to-default buttons | Implemented, not tested | Present per setting and per module; not clicked by the test (reset logic itself is unit-tested) |
| Tooltips | Implemented, not tested | Rendered after a hover delay; the tests do not hover |
| Menu scale (Auto / Minecraft / 1x-4x) | Verified in the running Minecraft client | Auto chose 2x at 1280x720 and 1x at 854x480 (screenshots) |
| Animations | Implemented, not tested | Disabled during tests for deterministic screenshots |
| Profiles UI, authorized servers UI | Verified in the running Minecraft client | Server address typed into Settings and stored; profile operations tested through the API |

## Modules

| Module | Status | Evidence |
|---|---|---|
| Coordinates, Performance, Module List HUD | Verified in the running Minecraft client | Visible in world screenshots |
| Waypoints | Verified in the running Minecraft client | Added at the player position, rendered with a label, hidden in the Nether, shown again in the Overworld |
| Chunk Borders | Verified in the running Minecraft client | Screenshot `world-chunk-borders` |
| Fixture ESP | Verified in the running Minecraft client | Highlights exactly the 2 registered entities, not the decoy |
| Fixture Containers | Verified in the running Minecraft client | 2 registered containers present, decoy not shown; a removed chest is reported as missing |
| Free Camera | Verified in the running Minecraft client | Camera moved 12 blocks, player moved 0; view returns to the player |
| Multiplayer restriction | Verified in the running Minecraft client | Real dedicated server, see above |
| Dimension changes | Verified in the running Minecraft client | Overworld → Nether → Overworld: modules keep running, no errors, stale results cleared |

## Amethyst Geode Analyzer

| Feature | Status | Evidence |
|---|---|---|
| Geode grouping, no double counting | Verified in the running Minecraft client | Exactly one geode linked to the fixture run; `GeodeClustererTest` |
| Counts per stage equal the ground truth | Verified in the running Minecraft client | Analyzer counts equal the server census exactly (in-game check) |
| Incomplete observations flagged, unreadable data never treated as air | Verified in the running Minecraft client | 1-chunk radius: geode reported incomplete with a reason; unit tests for unreadable faces |
| Explicit denominators, Wilson intervals, evidence quality | Automated tests passed | `StatisticsTest`; Analysis page screenshot |
| Analysis radius setting | Verified in the running Minecraft client | Radius changed between scans (1 chunk → incomplete geode; larger → complete) |
| Other analyzer settings (minimum sample, completeness, overlays, filter, budget, rescan) | Implemented, not tested | Shown in the settings panel; not changed by the test |
| Scan progress and per-tick time budget | Verified in the running Minecraft client | Progress shown during the scan; scans complete across several ticks |
| Scan cancel | Implemented, not tested | Cancel button present; not clicked by the test |
| Save observation and link to report | Verified in the running Minecraft client | Record saved, "Open report" navigates to Results, Markdown report written |
| Natural geodes in a normal world | Verified in the running Minecraft client | Seed 8675309: natural geodes found, re-identified, grew under boosted ticking (`docs/research/natural-world.md`) |

## Activity Research Engine and indicators

| Feature | Status | Evidence |
|---|---|---|
| Ground truth stored separately; classifiers receive label-free features | Automated tests passed | Type separation (`Features` has no label); engine tests |
| Train/test split by geode, pre-registered criteria, calibration | Automated tests passed | `ActivityResearchEngineTest` (8 tests, including "noise is not called supported") |
| Full experiment (sweep, harvest, partial scan) on real game code | Automated tests passed | Headless GameTest, `docs/research/evaluation.md` |
| In-game quick experiment and Results tabs | Verified in the running Minecraft client | Ran from the GUI, evaluation saved, test area cleaned up |
| Indicator registry (amethyst status from evidence; others untested) | Verified in the running Minecraft client | Indicators tab screenshot; check that untested indicators stay untested |

## Not verified

- **Visible-window run on the NVIDIA GPU:** Implemented, not tested on that driver. All graphical tests ran with Mesa llvmpipe under Xvfb.
- **Behavior with non-vanilla servers or anti-xray:** not tested (see KNOWN_ISSUES.md).
