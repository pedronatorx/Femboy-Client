# Changelog

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
