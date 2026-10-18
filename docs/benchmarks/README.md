# Benchmarks

`./scripts/run-benchmark.sh` runs one in-game measurement in a fixed scene and writes `build/run/clientGameTest/benchmark.txt` (copied to `build-logs/`). The files here are its output:

- [0.8.0-baseline.txt](0.8.0-baseline.txt): Femboy Client 0.8.0 (commit `ef43ab2`) with only the measurement code added.
- [0.9.0.txt](0.9.0.txt): 0.9.0.
- [1.0.0.txt](1.0.0.txt): 1.0.0, same harness, to check that the 1.0 additions (HUD conditions, scale and opacity, rules and timeline checks each tick, the health bookkeeping) did not make the client slower.

## Method

- **Scene** (flat singleplayer world): 100 husks, 40 cows, 20 dropped items, 121 chests, a layer of 4,802 diamond ores; the player stands still and turns 1.5° per tick.
- **Modules on:** the 28 visual, assist and HUD modules listed in `BenchmarkTest.java` (Entity ESP with health, equipment and tracers on and 64 labels; Storage ESP with labels; Block ESP; Trackers with hostile and passive mobs; Hitboxes, Light Level, Chunk Borders, the HUD panels and others).
- **Measured** with the client's `PerformanceMonitor`: wall-clock time and bytes allocated (HotSpot's per-thread counter) of every call into a module, and of the three places the client calls modules (`client/tick`, `client/hud`, `client/world`), which include the dispatch itself. 100 ticks of warm-up, then three rounds of 300 ticks; per-module numbers are the median of the three rounds.
- **Environment:** the client game test under Xvfb with Mesa llvmpipe (software rendering) on an i5-14400F with 16 threads, while another Minecraft client was running. Frame times therefore depend on the CPU renderer and on load, and the number of frames per round varied (447 to 813 in the two final runs); compare the **client cost per frame** and the **module tick cost**, not frame times.

The 0.8.0 baseline was measured with the same harness, from a separate checkout of commit `ef43ab2` with only `PerformanceMonitor`, the three dispatch timers and `BenchmarkTest` added, right before the 0.9.0 measurement.

## Results (Balanced mode)

| | 0.8.0 | 0.9.0 | Change |
|---|---|---|---|
| Module tick work per tick | 2.10 to 2.19 ms | 0.18 to 0.23 ms | about -90% |
| Allocated during module ticks, per tick | 3,130 KB | 37 KB | -98.8% |
| Client drawing (HUD + world overlays) per frame | 4.84 to 5.42 ms | 3.07 to 3.62 ms | about -35% |
| HUD allocations per tick | 1,069 to 1,934 KB | 678 to 1,234 KB | about -37% |
| World-overlay allocations per tick | 2,566 to 4,650 KB | 742 to 1,386 KB | about -70% |

Per module, time per game tick (median of three rounds):

| Module | 0.8.0 | 0.9.0 | Why |
|---|---|---|---|
| Ore ESP / Block ESP | 1,894.5 µs | 250.6 µs | Chunks are read once and then follow block events; the shown list is rebuilt only when something changed; boxes outside the view are skipped. |
| Block Counter | 867.1 µs | 6.0 µs | Counts only when the scanner's hits change. |
| Entity ESP | 1,097.7 µs | 727.2 µs | Shared entity index, labels only for the drawn entities, culling, cached distance text. |
| Storage ESP | 853.0 µs | 250.3 µs | Boxes, colors and names worked out per scan, labels only for the nearest, culling. |
| Light Level | 398.2 µs | 134.8 µs | One pass per column without extra block reads or allocations, culling. |
| Hitboxes | 285.4 µs | 102.7 µs | Shared entity index instead of every loaded entity every frame, culling. |
| Module List, Coordinates, Movement Info, Performance... | 85 / 45 / 66 / 44 µs | 49 / 22 / 26 / 18 µs | HUD text built once per tick instead of every frame. |
| Trackers | 546.9 µs | 539.5 µs | About the same, although it now also draws target lines (on by default). |
| Keystrokes | 220.0 µs | 272.3 µs | Not improved; it draws every frame and was not changed. The difference was not investigated further. |

## Performance modes (0.9.0, same scene)

| Mode | Module tick per tick | Allocated per tick | Client drawing per frame |
|---|---|---|---|
| Balanced | 0.18 to 0.23 ms | 37 KB | 3.07 to 3.62 ms |
| Performance | 0.13 ms | 20 KB | 3.01 ms |
| Low impact | 0.11 ms | 14 KB | 2.98 ms |

The mode rounds ran after the Balanced rounds with the same modules on; each is a single round.

## 1.0.0 against 0.9.0 (same scene, Balanced)

| | 0.9.0 | 1.0.0 |
|---|---|---|
| Module tick work per tick | 0.18 to 0.23 ms | 0.19 to 0.25 ms |
| Allocated during module ticks, per tick | 37 KB | 38 KB |
| Client drawing (HUD + world overlays) per frame | 3.07 to 3.62 ms | 3.08 to 3.42 ms |
| Performance / Low impact, module tick | 0.13 / 0.11 ms | 0.15 / 0.12 ms |

Within run-to-run variation; 1.0 neither sped up nor slowed down the measured modules. Each run is one measurement on a loaded machine with software rendering, so differences of a few hundredths of a millisecond are noise.

## 1.1.0 against 1.0.0 (same scene, same session, Balanced)

The redesign changed the menu, not the modules, so this run checks that playing did not get slower. Both builds were measured one after the other on the same machine (`1.1.0.txt` holds both runs). The machine was swapping heavily at the time, so all times are higher than in the older files; compare the two columns with each other only.

| | 1.0.0 | 1.1.0 |
|---|---|---|
| Module tick work per tick | 0.18 to 0.27 ms | 0.18 to 0.22 ms |
| Allocated during module ticks, per tick | 37 to 38 KB | 37 to 38 KB |
| Client drawing (HUD + world overlays) per frame | 3.52 to 5.69 ms | 3.49 to 4.17 ms |
| Entity ESP / Trackers / Keystrokes, per tick | 924 / 716 / 320 µs | 934 / 720 / 318 µs |

Within run-to-run variation: no slowdown from the redesign. The menu itself is not part of this benchmark (it is closed while playing).

## Not measured

- Frame rate on a real GPU (the NVIDIA card was not used; see above).
- Memory footprint (heap size) over a long session; only allocation rates are measured.
- Busy multiplayer servers, many players, or a 12-chunk Block ESP radius.
