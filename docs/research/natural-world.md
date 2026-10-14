# Natural world control and natural ticking

World seed 8675309, normal world type. Observed by the client analyzer (same code as in the GUI).

Condition between the two observations: random_tick_speed 300 for 600 game ticks with the player present (server simulation distance 12 chunks). For chunks that were randomly ticking, the expected number of random ticks per block is 600 x 300 / 4096 = 43.9 (about 0.8 h at default speed).

| Geode | Complete | Chunk distance | Inhabited ticks before/after | Before (S/M/L/C) | After (S/M/L/C) | Clusters before/after |
|---|---|---:|---:|---|---|---:|
| minecraft:overworld@29,9,143 | yes/yes | 4 | 61 / 684 | 7/2/2/3 | 17/7/6/6 | 3 / 6 |
| minecraft:overworld@71,-7,40 | yes/yes | 3 | 81 / 704 | 2/1/1/1 | 7/2/4/4 | 1 / 4 |

Notes:

- 'Inhabited ticks' is the chunk's inhabitedTime counter, which only advances while a non-spectator player is within 128 blocks; random ticks reach every entity-ticking chunk, so the two need not agree.
- Natural geodes can be cut by caves, aquifers or the scan boundary; incomplete ones undercount.
- These are a handful of geodes from one seed: a qualitative check of the natural pathway, not a statistical evaluation.
