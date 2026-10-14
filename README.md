# Femboy Client

A client-side Fabric mod for **Minecraft Java Edition 1.21.11**: a clean ClickGUI with configurable visual, world, player and HUD modules.

Highlighting entities and storage through walls, the Geode Analyzer and the free camera only run in **singleplayer and on servers you explicitly authorize** (Settings → Authorized test servers). On any other server they pause. Femboy Client contains nothing that hides it from anti-cheat systems.

![Home page](docs/screenshots/0006_gui-1280x720-0-home.png)

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
2. Put `femboy-client-0.2.0.jar` (from `build/libs/`) and Fabric API 0.141.6+1.21.11 into the profile's `mods` folder.
3. Start the game and press **Right Shift** to open the menu. You can rebind it under Options → Controls → Key Binds → Femboy Client.

Settings live in `config/femboyclient/`:
- `femboyclient.json`: client options, active profile, authorized servers
- `profiles/*.json`: modules, keys and settings per profile

Every file is written atomically with a `.bak` copy. An unreadable file is moved aside as `*.corrupt-<time>`, never deleted, and the client falls back to the backup or to defaults.

**Upgrading from NOVA:** on first launch, settings from `config/nova/` are copied to `config/femboyclient/`. The old folder is left untouched.

## Building

You need a JDK 25 or newer to run Gradle. The build uses a Java 21 toolchain for compiling and for the game: Gradle finds an installed JDK 21 or downloads one through the foojay resolver.

```bash
export JAVA_HOME=$HOME/.local/share/jdks/jdk-25.0.4.1+1   # any JDK 25+
```

| Task | Command |
|---|---|
| Build the mod jar (also runs the unit tests) | `./gradlew build` → `build/libs/femboy-client-0.2.0.jar` |
| Unit tests only | `./gradlew test` |
| Server GameTests, headless (includes the research experiment) | `./gradlew runGameTest` |
| In-game client tests, headless (needs `xvfb-run`), with screenshots | `./scripts/run-client-tests.sh` |
| Same, in a visible window | `./scripts/run-client-tests.sh --visible` |
| Launch a development client to play | `./gradlew runClient` |
| Copy the latest screenshots and reports into `docs/` | `./scripts/collect-artifacts.sh` (run directly after the client tests: other Gradle tasks clear the client run folder) |

## Using the menu

The sidebar has six sections. Every module has an on/off switch, a key bind (click it and press a key; Backspace clears it), settings with tooltips and per-setting reset, and a module reset.

| Section | Contents |
|---|---|
| **Home** | Search across all modules with a category filter (click a module to open it), session status, module health, frame rate |
| **Visuals** | Entity ESP, Storage ESP, Chunk Borders |
| **World** | Geode Analyzer, Waypoints |
| **Player** | Player Finder, Free Camera |
| **HUD** | Coordinates, Performance, Module List |
| **Settings** | General tab: profiles, client options (menu scale, animations, tooltips), authorized servers, config folder. Diagnostics tab: frame timing, time per module, message log |

### Modules

| Module | What it does | Where it runs |
|---|---|---|
| **Entity ESP** | Highlights the entity types you choose. Types are picked by registry id (e.g. `minecraft:zombie`) in a searchable checklist grouped into players, hostile mobs, passive mobs, villagers and other. Pets can follow the type selection, always show, or never show. Options: outline, fill, through walls, names, distance, colors per group, maximum distance, maximum highlights and labels. Entities invisible to you are skipped unless you turn that on. | Singleplayer, authorized servers |
| **Storage ESP** | Highlights storage blocks you choose by block id: chests and copper chests, trapped chests, ender chests, barrels, shulker boxes, hoppers, dispensers/droppers/crafters, furnaces, and other storage. Options: outline, fill, through walls, labels, built-in or custom colors per group, maximum distance, scan interval, maximum highlights. Reads only block entities your client has already received. | Singleplayer, authorized servers |
| **Chunk Borders** | Chunk boundary lines around you. | Everywhere |
| **Geode Analyzer** | Finds amethyst geodes in loaded chunks and keeps exact growth-stage counts up to date as chunks load and buds grow. See below. | Singleplayer, authorized servers |
| **Waypoints** | Save positions; see them as markers with names and distances. | Everywhere (your own data) |
| **Player Finder** | Online players from the server's player list (name, game mode, ping) with search and sorting, plus navigation to players you can see and to your waypoints. See below. | Everywhere |
| **Free Camera** | Detach the camera and fly it while your player stays put. | Singleplayer, authorized servers |
| **Coordinates, Performance, Module List** | HUD panels. | Everywhere |

An enabled module **pauses** when its environment doesn't allow it (no world loaded, or the server isn't authorized), and resumes on its own. If a module throws an error, it is turned off and reported under Settings → Diagnostics; other modules keep running.

### Geode Analyzer

- **Reads only what your client has.** It never loads chunks or asks the server for anything. Chunks in range that aren't loaded are reported as unavailable, never treated as empty.
- **Updates automatically.** New chunks are read when they load; already-read chunks are re-read after the scan interval; grouping into geodes runs in the background and only when the data actually changed. Reading is limited to a few milliseconds per tick. **Pause** stops reading and analysis while keeping the last results; **Read again** forces a fresh read.
- **Per geode:** distance and direction, completeness, a stage bar, counts per stage (with percentages of the geode's total buds and clusters), the sample size `n`, when the data was read, and an evidence-quality rating (based on completeness and sample size; it is *not* a probability). Click a geode for exact counts, budding faces, unavailable positions and Wilson intervals.
- **Settings:** analysis radius, scan interval, minimum observations, percentage display, listing incomplete geodes, growth-stage display in the world, progress on HUD, debug information, time per tick.
- **Interpretation:** the panel quotes what controlled tests showed (below) and states that growth stages are not evidence of player activity. It never labels a geode as "visited" or similar.

### Player Finder

- The list comes from the server's player list. Positions are shown **only when you have them legitimately**: on ordinary servers for players in your direct line of sight; in singleplayer and on authorized servers also for other players your client has loaded.
- Players out of view show **"Position unknown"**, or, if you saw them earlier, **"Last seen … ago"** with that old position, clearly marked as stale. Nothing is inferred or invented. Last-seen positions are forgotten when you change world or dimension.
- Click a player with a known position, or one of your waypoints, to navigate: the HUD shows the target, distance and an arrow relative to where you're looking.
- Not implemented: integration with a server-side API. The spec allows it for servers you control, but nothing like it exists yet.

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
src/main/java/.../femboyclient/core/     plain Java: settings, modules, config, diagnostics, research (unit-tested)
src/main/java/.../femboyclient/world/    shared Minecraft code: geode fixtures, census, server scan, experiment generator
src/client/java/.../femboyclient/client/ client: entrypoint, GUI, HUD, rendering, modules, mixins
src/gametest/java/                       server GameTests and in-game client tests
src/test/java/                           JUnit tests
tools/make_icons.py                      generates the GUI icon atlas
scripts/                                 test runner and artifact collection
docs/                                    screenshots, test reports, research reports, verification report
```

Adding a module means writing one class (settings are declared as fields) and one line in `ModuleRegistry`. The GUI builds its page entry, settings panel and persistence from the module's category and settings.

Mixins (two, each documented in its source file):
- **`KeyboardHandlerMixin`:** module key binds. Fabric API has no in-world key event, and polling once per tick misses short taps.
- **`GameRendererMixin`:** hides the first-person hand while the free camera is active. No API exists to do this.

## Documentation

- [docs/VERIFICATION.md](docs/VERIFICATION.md): what was verified, how, and what wasn't.
- [KNOWN_ISSUES.md](KNOWN_ISSUES.md): known limitations and unverified assumptions.
- [CHANGELOG.md](CHANGELOG.md)

## License

Not decided yet (`All-Rights-Reserved` in `fabric.mod.json` until the author chooses one).
