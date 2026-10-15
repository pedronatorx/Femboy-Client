# Changelog

## 0.6.1 - 2026-10-15

### Changed
- **Auto Attribute Swap: the spear rule is now two parts.**
  - **Spear reach:** a click at a target beyond normal reach but within the spear's range jabs with the spear and switches back.
  - **Spear lunge:** holding attack with food or wind charges jabs a Lunge spear repeatedly to dash, with its own cooldown. Test environments only.

## 0.6.0 - 2026-10-15

### Added
- **Automation page** (singleplayer and servers in your own network only, no prompts):
  - **KillAura:** target filters, range, field of view, priority, attack-charge or CPS timing with an extra cooldown, visible turning.
  - **Trigger Bot**, **Aim Assist**.
  - **Reach** (+3 entity, +1 block; what vanilla servers accept).
  - **Velocity** (knockback from hits and explosions).
  - **Auto StunSlam** (axe, then mace smash, then back; delay, cooldown, minimum fall).
  - **AutoTotem**.
- **Flight** (Movement): hover-and-move and creative-style.
- Presets configure the matching automation modules (left off): KillAura and Trigger Bot in Sword/Axe, Auto StunSlam in Mace and Spear + Mace, AutoTotem in Crystal and Anchor.

### Changed
- Auto Attribute Swap stays out of the way while Auto StunSlam picks the weapons.
- The session line shows "(your network)" on servers that count as a test environment.

## 0.5.0 - 2026-10-15

### Added
- **Profiles page.** Use, copy, export (clipboard and `exports/`), import from the clipboard with every value checked, delete, and reset the current profile.
- **Ten presets:** Sword, Axe, Mace, Spear + Mace, Crystal, Anchor, Pot, UHC / Survival, Elytra and Bow / Crossbow. Each installs as a normal profile and can be reset to the preset. Presets turn on information modules only; Auto Attribute Swap is configured but off where it fits.
- **Automatic profile switching** by held item (sword, axe, mace, spear, trident, bow, crossbow, crystal, anchor, potion, totem, golden apple, rocket, empty hand) or gliding, optionally per dimension. Off by default; never triggered by Auto Attribute Swap's single-hit swap.
- **Emergency off:** a button in Settings and an optional key (unbound by default) that turn every module off and pause automatic switching.
- **Hold-mode keys:** each module's key can toggle it or keep it on only while held.
- **Combat Info:** clicks per second, combo, reach, accuracy.
- **Target HUD:** target card with health, absorption, armor, distance, equipment, shield state; lock key for manual target selection.
- **Weapon Info:** attack charge; axe/shield state; mace fall, smash bonus, vertical speed, wind charges; spear Lunge status and charge windows; bow, crossbow and trident progress and ammunition.
- **Crystal & Anchor Info:** crystal placement preview and explosion damage estimates for you and your target; anchor charges and explosion warning.
- **Movement Info:** speed, vertical speed, fall distance, landing and fall damage prediction with a fatal-fall warning.
- **Elytra Info:** speed, height, pitch, durability, rockets, obstacle warning.
- **Hitboxes** and **Trajectories** (Visuals).
- **Item Counters** with refill reminders, **Health & Armor** with durability alerts, **Effects** (Inventory).

### Changed
- **Everything is off on first start**, including the HUD panels that were on before. Existing profiles keep their saved state.
- **Sidebar:** Home, Combat, Movement, Visuals, Inventory, Trackers, World, HUD, Developer, Profiles, Settings. Free Camera moved to Movement; Trackers, Player Finder, Player Alert and Waypoints to Trackers. The Player section is gone.
- **Settings page:** profiles moved to the Profiles page; the emergency button is here.
- **HUD:** the top-right corner starts below vanilla's effect icons.

### Not included
- KillAura, Trigger Bot, automatic aiming, AutoCrystal, AutoAnchor, Auto StunSlam, Auto Lunge Swap, AutoTotem, Auto Armor, Auto Pot, Flight and Aim Trainer were not built (see README, "Not included").

## 0.4.0 - 2026-10-14

### Added
- **Combat page with Auto Attribute Swap.**
  - Switches to an axe, mace, spear, sword, trident or custom items for a single hit, then switches back in the same tick.
  - Each rule has its own card: item icon, on/off switch, cooldown, target filter, item-specific conditions and a reset button.
  - Status of the last swap in the panel and on the HUD.
- **Trackers (Visuals).** Screen-edge arrows and on-screen markers for players, mobs, waypoints and Player Finder's last-seen positions. Labels show the source and age.
- **Fullbright (Visuals).** Adjustable intensity; your video settings are never changed.
- **Developer page with Java Runner.**
  - Code editor with syntax highlighting.
  - Each run happens in a separate Java process with time, memory and output limits.
  - Stop, Clear, Copy and examples.
  - A read-only snapshot of the client through `Femboy.*`.
  - Confirmation before running code that changes files, starts programs or uses the network.
- **Settings framework:** key settings, text settings, section headings, and settings shown only in a module's own panel.
- **Icons:** combat, code, arrow, sun and target.

### Changed
- **Entity ESP overhaul.**
  - Smaller labels without the underline; name, distance and health each with their own switch and size.
  - Optional health bar and/or number, per category.
  - Outline thickness, opacity and per-category switches.
  - Tracer lines, a color override for chosen types, an update interval, and settings grouped into sections.
- **Storage ESP** and waypoint labels use the new label style.
- **Free Camera (now on Visuals).**
  - Its own rotation, so the mouse no longer turns your player.
  - Configurable up, down and boost keys; boost multiplier, smoothing and "fly where you look".
  - A HUD warning beyond the loaded chunks, and "Stay inside loaded chunks".
  - Occlusion culling is off while detached and restored afterwards.
- **Player Alert** stays on Player; Free Camera moved from Player to Visuals.

### Fixed
- **`ClientLevel.hasChunk` always returns true on the client.**
  - Free Camera's edge detection and the Geode Analyzer's budding-face check relied on it.
  - Both now ask the chunk cache, so faces next to unloaded chunks are reported as unavailable instead of open.
- **Test fixes:**
  - The geode test turns off natural random ticks; growth during the check could make its census stale.
  - The multiplayer test computes block centres correctly at negative coordinates.

## 0.3.0 - 2026-10-14

### Removed
- **The authorization system.** Every module now runs everywhere, in singleplayer and on any server.
  - Removed the "Authorized test servers" list from Settings and the "Authorized / Restricted" footer.
  - An old `authorizedServers` entry in `femboyclient.json` is ignored and dropped on the next save.

### Changed
- **Player Finder** shows the position of every player within your range (the server's entity tracking range, roughly render distance), not only players in your line of sight. "In view" is now "In range".
- **Free Camera works properly on servers.**
  - Your player keeps sending position updates while the camera is detached. Vanilla stops them whenever another entity is the camera, which left the server without updates for the whole flight.
  - Attack, use and pick-block are blocked while the camera is detached, because vanilla aims them from the camera.
  - Turning it on stops any block you were mining or item you were using.

### Added
- **Player Alert (Player).** Warns when another player comes within your render distance.
  - Chat message, sound and/or pop-up.
  - Optional warning when they leave or log off.
  - Ignore NPCs, and a repeat delay per player.
- **Tests.**
  - A second player is spawned on a real dedicated server to test Player Alert, Player Finder and Entity ESP.
  - Free Camera is tested on that server: position sync, no attacks from the camera, no rubber-band.
  - Both Free Camera mixins were checked by removing each one: the matching check then fails.

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
