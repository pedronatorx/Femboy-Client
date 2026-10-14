# Known issues, limitations and unverified assumptions

## Limitations

- **Restricted modules.** Entity ESP, Storage ESP, the Geode Analyzer and Free Camera run only in singleplayer and on servers listed under Settings → Authorized test servers. This is deliberate; on other servers they pause and say why.
- **Player Finder on ordinary servers** shows positions only for players in your direct line of sight (a ray test from your eyes). A player behind a single leaf block or glass pane may count as out of view.
  - "Last seen" positions are where you last saw someone, not where they are.
  - Server API integration (for servers you control) is not implemented.
- **Geode grouping.** Geodes are grouped by touching blocks.
  - Two geodes whose shells touch are reported as one.
  - A geode cut apart by a cave or the analysis edge can appear as fragments.
- **Only loaded chunks can be read.** The analyzer reads only chunks the client has loaded; a larger radius never loads more. Geodes at the edge of what is loaded are marked *Incomplete* and their counts are a lower bound.
- **Analyzer timing.**
  - Growth is noticed at the next re-read of the chunk (the scan interval, default 10 s).
  - Re-analysis is limited to once every 1.5 s.
  - "Observed" shows when the oldest chunk data behind a geode was read.
- **Storage ESP** reads block entities the client already has.
  - On servers that hide block entities (anti-xray plugins), it shows what the server sent, which may be fake or nothing. Not tested.
  - Changes appear at the next scan (default every second).
- **Entity ESP pets** are entities with an owner (tamed wolves, cats, parrots, horses...). Other "pet-like" mobs, such as name-tagged ones, count by their type.
- **World labels** (ESP, waypoints) stack among themselves but can be drawn under HUD panels in the same screen corner.
- **Free camera.**
  - Rotation is refreshed once per frame, so it can lag by up to one frame.
  - Interacting with blocks uses the camera's crosshair; the server still applies its normal reach checks.
- **What the research results cover.** The figures quoted in the Geode Analyzer hold only for the tested setup:
  - vanilla geodes in a flat test world;
  - exposure produced by calling the game's random-tick code;
  - exposure between 0 and about 38 hours.

## Test environment notes

- In-game tests ran headless under Xvfb with Mesa llvmpipe, which renders in software, so frame times are much slower than on real hardware.
- The log check ignores known offline-test noise:
  - missing X11 cursor shapes;
  - Realms and account-property requests failing without a real login;
  - an anisotropic-filtering option warning.
- A visible-window run (`--visible`) on the NVIDIA GPU was not performed.
- Player Finder was only tested with no other players present (singleplayer, and a dedicated server with one client). Listing, line-of-sight and last-seen behavior with real other players is untested.

## Unverified assumptions

- Dedicated-server behavior was tested with a local Fabric test server only. Other server software (Paper, proxies) was not tested.
- The `.corrupt-<time>` and `.bak` recovery was verified in unit tests with simulated corrupt files, not during a real crash.
- The `config/nova/` → `config/femboyclient/` migration is unit-tested; it was not exercised with a real NOVA install.
- Performance with very many entities or storage blocks, or a 16-chunk analysis radius in a busy world, was not measured. Per-tick time budgets and caps bound the cost by design.
