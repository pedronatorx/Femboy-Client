# Known issues, limitations and unverified assumptions

## Limitations

- **Server rules.** All modules run on every server. Many servers forbid ESP, free cameras and similar clients, and their anti-cheat may notice them. Femboy Client does nothing to hide itself.
- **Player Finder and Player Alert only know about players within your range.** The server sends a player's position only while they are within its entity tracking range (roughly your render distance; servers can set it lower). Anyone further away is "Position unknown".
  - "Last seen" positions are where someone was when they left your range, not where they are.
  - Server API integration (for servers you control) is not implemented.
  - "Ignore NPCs" treats a player entity that is missing from the server's player list as an NPC. A server that hides real players from the player list would make Player Alert ignore them; turn the setting off there.
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
  - Attacking, using items and pick-block are disabled while the camera is detached.
  - Chunks and entities load only around your player, so far away from it the camera shows empty space (the HUD says so).
  - Forward, back and sideways use your normal movement keys; only up, down and boost have their own key settings.
- **Auto Attribute Swap.**
  - Only hotbar items are used.
  - Servers don't confirm slot changes; the panel reports what the client selected and sent.
  - Some servers' anti-cheat flags fast slot switching.
  - The shield condition (axe only against a raised shield) was not tested against a real blocking target.
- **Java Runner.**
  - Not a sandbox: the separate process runs with your normal user permissions. The risky-API confirmation is a speed bump that can be bypassed (e.g. through reflection or obfuscated code).
  - It needs a Java with a compiler; the Minecraft launcher's Java often has none. Then set "Java for the runner" to a JDK 21+.
  - Each run starts a new Java process, which takes about half a second.
  - Snippets only see a snapshot from the moment you press Run.
- **Combat Info.**
  - A hit counts as landed when the target's hurt animation starts within 12 ticks of your attack. Someone else hitting the same target at that moment is counted as yours.
  - Combo ends when you take damage or after the timeout.
  - Clicks are counted only while no menu is open.
- **Weapon Info.**
  - The mace bonus is the base formula; Density and other server-side enchantment effects add more.
  - The spear speed is your look direction times your movement, like vanilla. Server-side target movement is not known exactly.
- **Crystal & Anchor Info.**
  - Estimates count armor, toughness, Resistance, Protection and Blast Protection.
  - Not counted: other enchantments, plugins, totems, and whether the explosion source is blocked by an entity.
  - The anchor warning assumes vanilla dimension rules: only the Nether sets spawn.
- **Movement Info.** Fall damage is predicted for a straight drop, before Feather Falling and Protection. Landing blocks that change damage (slime, hay, honey, beds, powder snow, cobwebs, water) are handled; others count as normal ground.
- **Elytra Info.** The obstacle warning follows a straight line along your current velocity; turns and rockets change the real path. Not tested in flight.
- **Trajectories.**
  - The path is the center of vanilla's random spread.
  - Wind charges and firework rockets are not predicted.
  - Bow, trident and thrown items inherit your movement on the server from its own record of your motion, which can differ slightly from the client's.
- **Automatic profile switching** saves and loads profile files on the game thread. A switch takes a few milliseconds; scrolling quickly through many weapons switches several times.
- **Presets** describe starting setups, not a best way to play. They depend only on modules that exist; nothing in them claims to do automation.
- **Entity ESP.** The color override uses one color for all chosen types; there are no per-type outline widths.
- **Fullbright** raises the lightmap brightness. The darkness effect and blindness still darken the view.
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
- Not tested in game in 0.5.0: Elytra Info in flight, the spear charge windows and Lunge (no spear test), bow and crossbow paths (only a thrown snowball was compared with a real throw), anchor explosions, armored or enchanted targets in damage estimates, the lock key pressed on a real keyboard (the test calls the same method).
- The second player in the multiplayer test is a Fabric fake player added on the server, not a second real client. The server sends it to the client like any player, but movement over time and real network latency were not tested.
- The test harness sends key events without modifier flags, so the code editor also checks the keyboard state for Ctrl. Real keyboard shortcuts were not exercised in a visible window.
- Performance was measured with Mesa software rendering (254 zombies: about 26 ms per frame with ESP, health, tracers and Trackers on vs. 24 ms with them off). Numbers on a real GPU will differ.

## Unverified assumptions

- Dedicated-server behavior was tested with a local Fabric test server only. Other server software (Paper, proxies) was not tested.
- The `.corrupt-<time>` and `.bak` recovery was verified in unit tests with simulated corrupt files, not during a real crash.
- The `config/nova/` → `config/femboyclient/` migration is unit-tested; it was not exercised with a real NOVA install.
- Performance with very many entities or storage blocks, or a 16-chunk analysis radius in a busy world, was not measured. Per-tick time budgets and caps bound the cost by design.
