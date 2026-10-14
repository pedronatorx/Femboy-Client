# Known issues, limitations and unverified assumptions

## Limitations

- **Geode grouping.** Geodes are grouped by touching blocks.
  - Two geodes whose shells touch are reported as one.
  - A geode cut apart by a cave or the scan edge can appear as fragments.
  - Registered test fixtures avoid both cases. Unit tests document the merge behavior.
- **Only loaded chunks can be read.** The analyzer reads only chunks the client has loaded. A larger analysis radius never loads more.
  - Geodes near the edge of render distance are marked *Incomplete*, and their counts are a lower bound.
  - In the natural-world test, whether edge chunks were loaded at scan time varied between runs (0 vs 2 complete geodes). The analyzer reports this as incompleteness, as designed.
- **Anti-xray servers.** On servers that obfuscate block data (anti-xray plugins), the analyzer would read the obfuscated blocks. This has not been tested; singleplayer is the supported case.
- **What the research results cover.** They hold only for the tested setup:
  - vanilla geodes in a flat test world;
  - exposure produced by calling the game's random-tick code;
  - exposure between 0 and about 38 hours.

  Precision depends on how common long-exposed geodes are in the sample, and that mix is set by the experiment design.
- **Reproducibility of research samples.** Geode shapes also depend on world-seeded noise and position. Reports record the world seed and position, but reruns at a different position produce a different, equally valid sample.
- **Small arms.** The harvested and regrowth experiment arms are small (12 geodes; 4 in the test set). Their numbers show direction, not precision.
- **World labels.** Labels (waypoints, fixtures) can be drawn under HUD panels in the same screen corner. They stack among themselves but don't avoid HUD panels.
- **Free camera.**
  - Rotation follows the mouse via the player and is refreshed once per frame by the HUD layer, which can lag by up to one frame.
  - Interacting with blocks while the free camera is active uses the camera's crosshair. The server still applies its normal reach checks.
- **Rendering.** World overlays are drawn at the end of the main world pass with custom pipelines. They were verified with Mesa llvmpipe (Xvfb), not on the NVIDIA driver.
- **Module key binds.** They work only while no screen is open, and a key can be shared by several modules. Conflicts are shown in the key bind tooltip but not prevented.

## Test environment notes

- In-game tests ran headless under Xvfb with Mesa llvmpipe, which renders in software, so frame times are much slower than on real hardware.
- The log check ignores known offline-test noise:
  - missing X11 cursor shapes;
  - Realms and account-property requests failing without a real login;
  - an anisotropic-filtering option warning from the test's default options.
- A visible-window run (`--visible`) on the user's desktop with the NVIDIA GPU was not performed.

## Unverified assumptions

- Behavior on dedicated servers was tested with a local Fabric test server only. Other server software (Paper, proxies) was not tested.
- The `.corrupt-<time>` and `.bak` recovery was verified in unit tests with simulated corrupt files. It wasn't exercised during a real crash.
- Performance on large worlds (radius 16, many geodes) was not measured. The scanner's per-tick time budget bounds its cost per tick by design.
