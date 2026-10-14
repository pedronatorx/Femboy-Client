# Changelog

## 0.2.0 - 2026-10-14: Femboy Client

### Changed
- **Renamed to Femboy Client.**
  - Mod id `femboyclient`, package `io.github.theamethyx.femboyclient`, jar `femboy-client-<version>.jar`.
  - The config folder moved to `config/femboyclient/`. On first launch, settings from `config/nova/` are copied there; the old folder is kept.
- **New navigation: Home, Visuals, World, Player, HUD, Settings.**
  - The development pages Dashboard, World Analysis, Test Scenarios and Test Results were removed.
  - Diagnostics is now a tab under Settings.
  - Each category page searches its own modules; Home searches all modules with a category filter and opens a module on its page.
- **Geode Analyzer (World), replacing the Amethyst Geode Analyzer.**
  - Every control lives in the module's own panel: status, Pause / Read again, one card per geode, details on click, and a reference card quoting the controlled-test results.
  - It now runs automatically:
    - New chunks are read as they load, and read chunks are re-read after the scan interval.
    - Unchanged data is not re-analyzed.
    - Unloaded chunks are dropped and reported as unavailable.
  - New settings: scan interval, list incomplete geodes, progress on HUD, debug information.
  - Removed: the test-fixture filter, saving observations to files, and the in-game research experiment. The research engine and its experiments still run headless in `./gradlew runGameTest`.
- **Categories reassigned.**
  - Waypoints moved to World and Free Camera to Player.
  - Restricted modules now say "Singleplayer and authorized servers" instead of "Test environments only".

### Added
- **Entity ESP (Visuals).**
  - Entity types are chosen by registry id in a searchable checklist grouped into players, hostile, passive, villagers and other.
  - Pets can follow the type selection, always show, or never show.
  - Options: outline, fill, through walls, names, distance, invisible entities, a color per group, maximum distance, highlights and labels.
- **Storage ESP (Visuals).**
  - Storage blocks are chosen by block id, so copper chests and each shulker box color are separate choices.
  - Options: outline, fill, through walls, labels, built-in or custom group colors, maximum distance, scan interval, maximum highlights.
- **Player Finder (Player).**
  - Player list with search and sorting by name, distance or ping, plus navigation to visible players and your waypoints, with a HUD arrow.
  - Positions are shown only when legitimately available. Otherwise the list says "position unknown", or shows a last-seen position marked as stale.
- **New setting types.**
  - A color setting (swatches; saved as `#RRGGBB`).
  - An id-list setting (grouped, searchable checklist; saved as registry ids, so unknown ids from removed mods are kept).
- **Shared ESP renderer** used by both highlight modules.
- **Icons:** home, player, storage, navigate, pause.

### Removed
- Fixture ESP, Fixture Containers, the client-side test scenarios, the fixture registry and the in-game experiment runner. The automated tests now build their fixtures directly on the integrated server.

### Fixed
- Unit tests wrote Minecraft's `logs/` folder into the project directory. They now run in `build/test-run/`.

## 0.1.0 - 2026-10-14

First version. Built in stages; each stage is a git commit.

### Added
- **Stage 2: project setup.** Fabric project for Minecraft 1.21.11 (Loom 1.18.4 remap, Mojang mappings, Loader 0.19.5, Fabric API 0.141.6), with a Java 21 toolchain.
- **Stage 3: module framework.**
  - Module lifecycle with per-module error isolation and pausing when the environment doesn't allow a module to run.
  - Validated settings (on/off, number, choice).
  - Key binds.
  - Profiles, autosave, atomic config files with backups and recovery from corrupt files.
  - Diagnostic log and performance monitor.
- **Stage 4: ClickGUI.**
  - Sidebar navigation, a custom widget toolkit, tooltips and animations.
  - A crisp, independent menu scale.
- **Stage 5: modules and fixtures.**
  - Coordinates, Performance and Module List HUD.
  - Waypoints, Chunk Borders, Fixture ESP, Fixture Containers and Free Camera.
  - Test scenarios and a fixture registry, with decoys as negative controls.
- **Stage 6: Amethyst Geode Analyzer.**
  - Time-budgeted, cancellable scanner; completeness tracking; explicit denominators; Wilson intervals; evidence-quality rating.
  - Geode fixtures built with the vanilla feature, with ground truth stored separately.
  - Saved observations with Markdown reports.
- **Stage 7: Activity Research Engine.**
  - Pre-registered hypotheses, geode-grouped train/test split, threshold baseline, logistic calibration, per-condition results.
  - Experiment generator: sweep, harvest and partial-scan arms.
  - Headless research GameTest, in-game quick experiment, indicator registry.
- **Stage 8: in-game verification.**
  - Real mouse and keyboard tests, a natural-world control with natural ticking, log inspection.
- **Stage 9: further tests and polish.**
  - A dedicated-server authorization test, a dimension-change test, and resource/metadata unit tests.
  - Dashboard testing card, HUD contrast and layout polish.

### Fixed (found by the automated tests)
- Containers broadcast key presses to every child, so an unfocused search field swallowed Escape and typing.
- The vanilla geode feature refuses to place in air, so fixtures now use stone host rock.
- Long labels and titles overflowed on the Results page.
- World labels overlapped each other; they now stack.
- The first-person hand showed while the free camera was active.
- Analyzer results from one dimension were drawn in another; they are now cleared when the dimension changes.
