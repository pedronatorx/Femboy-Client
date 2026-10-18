# Changelog

## 1.0.0 - 2026-10-18

### Added
- **Command palette** (Ctrl+K or Ctrl+P in the menu, the header button, or its own key, unbound by default): find and run modules (Enter toggles, Tab opens settings), pages and settings tabs, profiles, individual settings, theme presets and actions (HUD editor, save, restore point, back to the previous profile, performance mode, timeline marker, diagnostic report, emergency off). With nothing typed it lists favorites, recently used modules and a pending rule suggestion. Arrow keys, Page Up/Down and mouse work.
- **Favorites and recently used:** a star in a module's settings header; Home shows Favorites and Recently used cards first. Stored once for all profiles.
- **Quick toggle wheel** (hold its key; unbound by default): up to eight favorites (then recent modules) and "previous profile" around the crosshair; point and release.
- **Theme Studio** (Settings, Appearance): presets Default purple, Deep violet, Midnight, Minimal dark, High contrast and Reduced motion; custom accent and second accent, background and text colors, panel opacity, corner style, colored or plain icons, background art, HUD background opacity, animation speed; a live preview; save, share (as text), import (validated) and delete your own themes. All of it is stored per profile.
- **Performance Center** (Settings, Performance): FPS, frame times and a frame graph, the client's share of each frame, memory, the cost of every running module per tick, HUD frame and world frame, what busy modules are working on, the update rates of the current mode, warnings and recommendations derived from the measurements, an **on/off comparison** for one visual or assist module, optional allocation measurement and an exportable **diagnostic report** (no server address, chat or names).
- **Developer tab** (Settings, Developer): the **module health monitor** (Active, Waiting, Unsupported here, Reduced, Suspended, Error, Off, with the reason, last update, errors, cost and current work), dependencies and conflicts, a **configuration validator** for all profiles, the newest crash reports, event counters, raw call timings and the log.
- **Rules** (Settings, Rules): "When / If / Then" rules with triggers (profile changed, world joined, dimension changed, GUI scale or window size changed, low FPS for 5 s, combat started or ended, held item type changed), condition groups (all or any: profile, dimension, environment, held item, HUD layout, FPS), actions (notify, suggest a profile or Performance mode, switch profile or HUD layout, show or hide a HUD element, turn visual modules on or off, set the performance mode, add a timeline marker), priority order, cooldowns, dry runs, a "check now" button, history, validation with conflict detection, and import/export. Suggestions wait in the command palette until applied. Two suggestion rules ship by default. Rules cannot switch on modules that act in the game, and loops are prevented (each rule once per chain, a minimum cooldown for rules that change something, at most five rules per second).
- **Session timeline** (Settings, Timeline): profile switches, module changes, worlds and dimensions (described as singleplayer, private network or public server only), deaths, your markers, schematics, performance warnings, module errors and rules; search, filter by kind, bookmarks, notes, export as text, clear, and a switch per kind of event. No chat is recorded.
- **HUD editor rebuilt:** snapping to edges, the center and other elements with guide lines (Shift: free), an optional 8-pixel grid, the safe area and hotbar zone, per-element **size** and **opacity**, **hide**, **show only** in combat / out of combat / holding a weapon / gliding / sneaking, arrow-key nudging, **undo and redo**, an elements list to turn HUD modules on, overlap warnings, and several **named layouts** with copy, rename, delete and clipboard import/export. Positions are anchored to the nearest screen edge or the center, so they hold at other window sizes and GUI scales. Each profile stores its own layout.
- **Profiles:** preview a profile before switching (exactly which modules, keys, settings and stored options differ), rename (rules and history follow), a list of recently used profiles with "Back", and seven more presets: Bed PvP, Farming, Parkour, Speedrunning, Recording / Streaming, Developer and Authorized Testing (24 in total).
- **Restore points:** create one by hand; one is made automatically before restoring, a full reset, replacing a profile with a preset, and a configuration format update. Restore (undoable), delete; the newest 20 are kept.
- **Reset everything** (two clicks) and **Reload from disk** (two clicks).
- **Notifications:** categories (module toggles, profiles, configuration, errors, performance, environment, rules, alerts, other) that the Notifications module can show or hide, position (top right, top left, top center, bottom right), style, icons, repeats folded into one toast with a count, a rate limit, and animations that follow Reduce motion. Turning on a module that cannot run where you are now says why.
- **New modules:** Compass (heading strip), Session Stats (play time, distance, deaths, hits and best combo), Experience (exact points to the next and a target level), Shape Planner (outlines circles, cylinders, spheres, domes, spiral staircases and walls with a block count; guides only), Mention Alerts (notification and sound when chat mentions your name or a keyword as a whole word).
- A separate **Trackers** page in the sidebar; World keeps the world tools and the Java runner.
- Search aliases for modules (for example "ore esp" finds Block ESP, "freecam" finds Free Camera).

### Changed
- **A failing module is suspended, not turned off:** it stops, cleans up and restarts by itself after 5 s (10 s after a second failure). The third failure within a minute turns it off until you turn it on again. Failures while starting or loading still turn it off at once. The notification says which happened.
- **Nothing you saved is dropped silently:** settings and modules this version does not know (from a newer version) are written back unchanged, and a profile whose values had to be replaced by defaults is copied to `<name>.json.before-repair-<time>` first.
- Configuration schema 3 (profiles may hold the HUD layout; the global file a profile history). Files of an older schema are copied into a restore point before they are rewritten.
- Settings is split into tabs: General, Appearance, Performance, Rules, Timeline, Developer.
- Busy modules (Block ESP, Entity ESP, Storage ESP, Trackers, Block Counter, Light Level, Hitboxes, Shape Planner) describe their current work for the health monitor and the Performance Center.
- A module that averages more than 2 ms per tick gets one performance warning per session.

### Fixed
- A dropdown whose value was empty showed "-" even when it had a name for "nothing" (for example "All events").

## 0.9.0 - 2026-10-18

### Added
- **ESP target lines.** Block ESP, Entity ESP, Storage ESP and Trackers draw lines from the crosshair to their targets, each module with its own settings: which targets (all, only on screen, only in line of sight), color, opacity, thickness, fade, hide behind blocks, distance and count. Entity ESP switches lines per group, Storage ESP per kind of storage. Lines start on the real centre ray of the drawn frame, so they leave exactly from the crosshair at any window size and GUI scale.
- **Performance modes** (Performance, Balanced, Low impact; stored per profile). They change update intervals, scan time per tick, how many highlights and labels are drawn, how often HUD text is rebuilt, menu animations and pauses between automatic actions; Low impact pauses modules that act many times a second and says so.
- **Menu redesign:** sidebar Home, Combat, Automation, Movement, Visuals, Inventory, World, Profiles, Settings; header with search over all modules (Ctrl+F), a profile selector and a notification bell; in-menu toasts; smooth anti-aliased rounded shapes, soft shadows, accent gradients and glows, a new logo and 32 px vector icons (made by `tools/make_gui_art.py` and `tools/make_icons.py`); collapsible setting sections with fine-tuning folded away; typed number entry and Shift + wheel steps on sliders; a two-click reset for a whole page; Reduce motion.
- **Module conflicts:** KillAura with Aim Assist or Trigger Bot, Flight with Elytra Flight or Speed; turning one on turns the other off, with a message.
- **Error notifications:** a module that fails is turned off and a notification names it and the reason; repeated identical log messages are counted instead of repeated.
- `./scripts/run-benchmark.sh` and `docs/benchmarks/`: a fixed in-game scene measuring the client's own time and allocations.

### Changed
- **Ore ESP is now Block ESP:** any blocks, picked by block or by category (ores, containers and spawners, redstone, plants and wood), outline and fill, per-ore or custom colors, name and distance labels, target lines. Ore ESP settings move over automatically (configuration schema 2; also for imported profiles and presets).
- **Block ESP, Block Counter and Geode Analyzer now run only in singleplayer and on servers in your own network**, because they read every block of the loaded chunks, which shows what other blocks hide.
- Trackers draws target lines (on by default) besides its edge arrows, which can be switched off.
- Entity ESP has item and projectile groups with their own color, outline and line switches; "Tracers" became "Target lines" (settings carried over).
- Configuration files are written on a background thread; a profile also stores the theme, animation options and performance mode.
- Labels and lines use the projection the world was drawn with (vanilla's leaves out view bobbing), so they stay on their boxes while walking.
- Shared work: one list of loaded entities per tick for Entity ESP, Trackers and Hitboxes; HUD text built once per tick; module lookups and per-call timing keys no longer allocate; the world and connection identity is cached.

### Fixed
- Auto Pot left your view pointing down when turned off during the aiming tick.
- Auto Armor, Auto Refill and Auto Offhand could click while you held an item on the cursor.
- Auto Tool switched back on a later, unrelated tick when turned off while mining.
- Sprint undid Sprint Reset's pause right after a hit.
- Player Alert kept every warned player forever; old entries are now forgotten.
- Auto Reconnect started a second countdown when the disconnect screen was resized.
- Structure Finder kept results (and a queued search) from the previous dimension.
- Storage ESP kept a broken chest highlighted until the next scan; it now rescans at once.
- In the menu, Escape went to a text field instead of closing an open list, and Enter after typing a value kept the field focused.

## 0.8.0 - 2026-10-17

### Added
- **New GUI layout.** Home shows every module in category cards (category chips, search, a strip of what is on). Left-click a module to toggle it, right-click it for a settings panel that slides in with Settings, Keybinds and Info tabs and a Reset button. Larger window, rounded corners, logo and tagline, a profile chip in the header, a client status card in the sidebar, sliding highlights.
- **Combat (sandbox):** AutoCrystal, AutoAnchor, Auto Pot. **Combat:** Aim Trainer.
- **Movement (sandbox):** Anti Hunger.
- **Visuals:** Entity Outlines (vanilla glowing outline); Entity ESP can show armor and held items above labels.
- **Inventory:** Item Swap, Hotbar Profiles, Chest Organizer.
- **World:** Structure Finder (singleplayer), Schematic Viewer (`.nbt`, `.schem`, `.litematic`), Material List.
- **Player Alert:** auto leave when a player is detected, with a distance limit and a friends list.

### Changed
- Right-click on a category page opens the module's settings instead of toggling it.
- Inventory Sorter and Chest Organizer merge partial stacks before sorting.
- Crystal & Anchor Info: anchor damage now ignores the anchor block itself (vanilla removes it before the explosion), so estimates near anchors are no longer far too low.

## 0.7.0 - 2026-10-17

### Added
- **Themes:** Femboy (navy, purple, magenta; new default), Charcoal, Midnight magenta and Deep ocean, with an optional custom accent color.
- **HUD editor** (Settings → Edit HUD layout): drag HUD blocks anywhere, right-click to send one back to its corner, R to reset all. Positions are stored as screen fractions.
- **Module tiers** shown on every module: Visual, Assist, Automation, Sandbox. Sandbox modules say in their description that they run only in test environments.
- **Combat:** Auto Criticals, Sprint Reset (W-tap), Auto Weapon Switch (all sandbox).
- **Movement:** Sprint; Elytra Flight, Speed, Strafe Assist, Step, Safe Walk, No Slow, No Fall (sandbox).
- **Visuals:** Ore ESP, Block Highlight, Light Level, Zoom.
- **Inventory:** Auto Tool, Inventory Sorter, Shulker Preview; Auto Armor and Auto Refill (sandbox).
- **World:** Block Counter (with scan progress), Death Coordinates.
- **Utilities** (was HUD): Keystrokes, Notifications (toasts), Better Chat, Auto Reconnect.
- **Seven new presets:** Spear PvP, Survival / SMP, Building, Mining, Exploration, Performance, Local Testing (17 in all).
- **Profile switching:** pickaxe/shovel and block triggers, and a switch delay (default 4 ticks) against flicker.

### Changed
- AutoTotem is now **Auto Offhand** (totem, shield, golden apple or end crystal).
- The test-environment restriction on automation modules, removed in commit 4102b2b, is restored.

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
