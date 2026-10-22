# Plot decoration: `houses/decor.yml`

Status: placeholder (first design; judge in game)

- **Purpose:** cosy bakery-village props around the factory so the fenced plot no longer feels plain (feature `plot-polish`). `/bakery plot setup` places them after house, fence and belt; backup/undo covers them like the rest.
- **Pipeline:** plugin data file, vanilla blocks only. **Generated** by `design/scripts/decor_style.py` (`mise run assets`); don't edit the YAML by hand. Preview: `design/scripts/previews/decor_plot.png` (top-down plan: keep-out zones, factory, belt, pedestal and panels, decor colours).
- **Target:** Paper 26.3. Block ids checked against `Material.java` in the paper-api 26.3 sources (2026-10-22); block data is checked by `house_lib.check_block_data` (new schemas added for leaves, farmland, wheat, water, carved pumpkin, small flowers).
- 360 blocks, 9 clusters. Every plant is self-contained: flowers and trees stand on a `grass_block[snowy=false]` the decor places at y 0 (plant at y 1, trunk from y 1), wheat on its own farmland at y 0; nothing depends on the unknown plot ground.

## Format (the coder parses exactly this)
```yaml
blocks:
  - {at: [11, 0, 2], block: "minecraft:poppy"}
  - {at: [24, 3, 4], block: "minecraft:lantern[hanging=false,waterlogged=false]"}
```
- `at: [x, y, z]` ints in **plot cells from the plot's min corner** (same frame as `layout:` in factory.yml, `design/factory.md`): x west to east, z north to south, y 0 = first air layer above the ground. All y >= 0 (nothing replaces ground); y max is 6.
- `block`: full block data with explicit states for every stateful block. No rotation, nothing relative to the house. YAML `#` comment lines name the clusters.
- Placement: physics off (like the house). One block per cell, no duplicates. Plants other than potted ones, and the wheat, assume grass/dirt ground (flowers) or the farmland below them (wheat).
- All cells are inside the interior, x 1..29, z 1..28.

## Keep-out (parser check; any y)
A decor cell `(x, z)` is rejected when it is inside one of these (inclusive). The generator asserts the same rules.
| Name | Cells | Derived from |
|---|---|---|
| fence ring | `x == 0`, `x == 30`, `z == 0`, `z == 29` | `plot-size [31, 30]` |
| factory | `x 5..25`, `z 9..24` | design box 19 x 14 at `house-at [6, 0, 10]` = x 6..24, z 10..23, plus 1 cell margin all round (door at plot `[14, 12]`, hatch, belt start at z 11) |
| front zone | `x 12..23`, `z 1..8` | covers: the gate walk (gate x 14..16 at z 0, walk straight south), the free click rows in front of the pedestal, the pedestal `[18, 6]` +-2 (x 16..20, z 4..8), clicks `[16, 6]` +-2 (x 14..18), shop `[20, 6]` +-2 (x 18..22), belt cells x 18, z 7..11 (z 9..11 are in the factory zone), and the shelf growing west of clicks (x 12..13) |

Panels hang above their cells and stay inside the front zone, so no decor sits in a hitbox. The belt cells, the pedestal and both panels are inside these two rectangles (checked in the generator via the numbers above). The path from gate to pedestal is the open grass corridor x 12..23, z 1..8; the decor stands only along its edges (x 11 and x 24).

## Clusters (what they are)
| Cluster | Where (x, z) | Content |
|---|---|---|
| `path_edge_west` / `path_edge_east` | x 11 and x 24, z 2..7 | flower rows (poppy, dandelion, cornflower, azure bluet, oxeye daisy, pink tulip) edging the walk, a **lamp post** (3 spruce logs + lantern) at z 4 on each side |
| `front_west_garden` | x 3..8, z 1..8 | **cherry tree** (cherry log trunk 4, round pink canopy) at `[3, 3]`, a 3-stair **bench** at x 8, z 5..7 facing east (towards the pedestal), potted poppy and cornflower at its ends, loose flowers |
| `front_east_wheat` | x 26..29, z 2..8 | 3 x 3 **wheat patch** (x 26..28, z 2..4): 8 farmland (moisture 7) with ripe wheat, centre `[27, 0, 3]` is water so the farmland stays wet; **scarecrow** at `[29, 0..2, 6]` (spruce log, hay block, carved pumpkin facing west); hay bales and a potted dandelion |
| `west_store` | x 1..4, z 11..22 | barrels (a stack of 2 high), **flour sacks** (white wool + carpet, like the factory's flour shed), hay bales, a potted plant, lamp posts at `[4, 11]` and `[4, 22]` |
| `west_bench_garden` | x 1..3, z 18..22 | bench (3 stairs facing east), two pots, a flower bed |
| `east_yard` | x 26..29, z 11..22 | small cherry tree at `[28, 13]` (radius 1), barrels, hay, sacks, lamp posts at `[26, 11]`, `[26, 22]` |
| `east_bench` | x 26, z 14..16 | 2-stair bench facing west towards the factory, potted poppy |
| `back_yard` | z 24..28 | oak and cherry tree at `[3, 26]` / `[27, 26]`, oak-leaf hedges, a spruce **log pile** (x 12..16 at z 28), two lamp posts at `[10, 26]` and `[20, 26]`, flowers |

Style: the factory palette (spruce, dark oak greens, bricks, lanterns), pink cherry canopy and flowers for a little colour, kept to the edges and the back so the walk, the pedestal and the factory front stay clear. No fences (their connections would need computed states); lamp posts use a spruce log so nothing connects. Lantern light: 8 lamp posts, lit at night.

## Notes for the other roles
- **plugin-coder:** parse `blocks:` as a list of `{at, block}`; reject cells in the keep-out table and outside x 1..29 / z 1..28 / y >= 0. Block entities: barrels (empty, 8), none else. The only **fluid** is the single `water[level=0]` source at `[27, 0, 3]`, enclosed by 4 farmland blocks; with physics off it stays put. Backup must capture y 0..6 for these cells (leaves reach y 6). The wheat y 1 cells sit on farmland y 0.
- **plugin-tester:** parser cases: a decor cell inside each keep-out zone is rejected, an out-of-range cell is rejected, the shipped file loads (360 blocks) and none of them hit the keep-out.

## Placeholder / needs a human
Everything is a first design judged from a top-down plan only (no in-game view): check the density, tree size, bench orientation, and that the flowers/wheat survive on the real ground block (they need grass/dirt; if the plot ground is something else, say so and I switch to potted plants only).
