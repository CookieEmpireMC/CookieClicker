# Countryside: place files, `countryside.yml` and the Mill

Status: placeholder (the Mill is the only place; needs the user's look and a Chunky pass). Target Paper 26.3 (`26.3.build.205-stable`), pack format 97.1. Build rules: `design/style-guide.md`, `design/build-lint.md`. Vanilla blocks only, except the waterwheel item model.

## 1. File format (agreed with the coder, implemented in the plugin)

### Place files `src/main/resources/houses/<place>.yml`
The existing house format (`size`, `origin`, `ground-depth`, `palette`, `layers`, `signs`; see `design/house-default.md`) with these differences and additions:
- `ground-depth` is allowed up to **4** (design y -4..-1 are the first four layers). Surface block (grass, cobble ...) is design y **-1**; design y 0 is the first air layer above the ground.
- Generated in the **final orientation**: nothing is rotated at paste. The **road side faces north (-Z)**.
- `.` or a space = keep (the terrain below stays); every other char overrides the countryside (places paste after it).
- **Points**: y counts from the first layer above `ground-depth` (design y); every point lies inside `size`.

```yaml
points:
  viewpoint: {at: [16, 0, 5], yaw: -20, pitch: 12}   # required; `at` = the player's FEET cell; yaw/pitch default 0
  yard: {from: [11, 3], to: [21, 9]}                 # required; [x, z], inclusive, any order, at yard ground level
  hologram: [16, 1, 7]                               # required
  panel: {at: [20, 0, 7], yaw: 180}                  # required; yaw = the way the panel faces (180 = north, towards the viewer)
  ripe: [12, 0, 7]                                   # required
  smoke: [[x, y, z]]                                 # optional, 0 or 1 entries (the single chimney)
  wheel: {at: [28, 0, 16], axis: z}                  # optional (Mill): the axle centre CELL; axis = axle direction (x or z)
  recipe_book: [x, y, z]                             # optional (market, later run)
  notice_board: {at: [x, y, z], yaw: 180}            # optional (market, later run)
```
Rules the generators assert (and `lib/place_lib.check_points` re-checks): all required points present; clickable points (hologram, panel, ripe) within **5 blocks** of the viewpoint (cell centres); no water, leaves or flowers in their 1 x 2 hitbox cells; viewpoint feet and head free over a solid block; a calm backdrop and lanterns around the panel; lit at night (lint L-F3).

### `countryside.yml` (run 2)
House format for terrain, road, stream and scenery (about 150 x H x 120, `ground-depth` <= 4) plus
```yaml
places:                          # names [a-z0-9_]; each loads houses/<name>.yml; NO yaw (non-zero is rejected)
  market:  {at: [x, y, z]}
  mill:    {at: [x, y, z]}
  cow_barn: {at: ...}   sugar_works: {at: ...}   cocoa_grove: {at: ...}   chicken_farm: {at: ...}
```
- The file's `origin` cell (default `[0,0,0]`) lands on the **farm region's min corner**, so the region's min y = ground level.
- `at` = where the place file's min corner (its cell [0,0,0] above `ground-depth`) lands. A place must stay inside the countryside's x/z box and top, and may reach at most 4 layers below the region's min y.
- Places paste in file order after the countryside; the countryside leaves each place box as keep.
- The Mill's road (3-wide gravel, `z 0..2`, see below) must meet the countryside's road at the Mill's north edge, at the same height (ground y -1).

## 2. Kits (`design/scripts/lib/farm_kits.py`, tests `lib/tests/test_farm_kits.py`)
Every kit is a named, deterministic function (variation from the Build's seeded rng or explicit seeds). The tests run in `mise run assets` (`design/scripts/farm_kit_tests.py`) and with `python -m unittest discover -s design/scripts/lib/tests`.

| Status | Kits |
|---|---|
| **done** (used by the Mill, tested) | `hipped_roof` (one solver for gable / half-hip / hip / pyramid: level = min over sloping sides of distance + k, steep 1.5-per-step pitch table with filled risers, stairs face the ridge, eave and rake rows, ridge + cap), `half_hip_roof`, `gable_roof_s`, `pyramid_roof`, `pent_skirt`, `dormer` (3-wide gabled dormer that runs back until the main roof reaches it; `hatch=True, beam=True` = **hoist gablet** with chain and barrel), `coped_gable`, `staddle_stones`, `retaining_wall`, `stone_bridge` (fenced, lanterns, open span), `wheel_slot` (piers, floor, back wall, lintel beam, axle bearing, static fallback wheel), `mill_race` (stone-lined still water + coping), `pond` (bed, shelf, water, lily pads), `height_field_ground`, `terrace`, `cut_and_fill` |
| **simple** (tested, small, not yet used) | `vent_slits`, `board_and_batten`, `nest_box_row`, `hurdle_fence`, `stone_pillar_fence`, `buttress`, `awning`, `drying_rack`, `orchard_grid`, `furrow_field` |
| **done in run 2a** (Cow Barn, tested) | `catslide_roof` (stairs + bottom slabs, half a block per row), `fill_under`, `corbie_gable` (stepped gable wall + slab caps), `barn_door_double` (braced 5 x 4 leaves, centre stile, jambs, lintel, big-door registration), `hipped_roof(clip=main)` (cross-gable buried in the main roof); `coped_gable` now also registers its caps as roof cells |
| **stub** (raise `NotImplementedError`, test asserts it; run 2b) | `round_building` (8-gon), `cone_roof`, `hay_roll_thatch`, `eyebrow_dormer` |

Other new lib files: `place_gen.py` (run 2a: road, lawn, meadow, hedges, drystone walls, trough, hay rick, and `finish()` = block ids, points, lints, YAML, previews; `lint_place(size_range=)` for the market), `place_lib.py` (points contract + place YAML writer), `place_views.py` (previews), `farm_lint.py` (L-F rules), `block_ids.py` (sources-jar validator).

## 3. Lint (`lib/farm_lint.py`, rules in `design/build-lint.md` "Farm places")
L-F1 volumes, L-F2 roof forms and big planes, L-F3 yard, L-F4 clearance (wheel disc, enclosed water, cocoa), L-F5 big doors, L-F6 ground (5 x 5 patches, straight shores), L-F7 chimney/vent, L-F8 contrast and stair share, L-F9 roof heights (and optional roof share), L-F10 water share of the frontage, L-F11 gable finish, plus block count (hero 1,500-3,500, small 600-1,200 above ground). A place that fails is **not written** (previews are). Tests: `LintTests` in `lib/tests/test_farm_kits.py`.

## 4. Block ids
`mise run assets` runs `design/scripts/farm_block_ids.py` against `org/bukkit/Material.java` in the paper-api 26.3.build.205 sources jar (and every generator asserts its palette against it). All `(v?)` ids of the research note exist in 26.3: `resin_brick_stairs/slab`, `resin_bricks`, `pale_oak_*`, `bee_nest`, `beehive`, `weathered_cut_copper_stairs`, `deepslate_tile_stairs`, `blackstone_stairs`, `cobbled_deepslate_stairs`, `tuff_bricks`, `mud_brick_stairs`. 58 ids checked, none missing. They are kept; the Mill itself uses only the ones listed in its palette.

## 5. Mill (`houses/mill.yml`, generator `design/scripts/house_mill.py`, layout `mill_layout.py`)
- **Purpose:** flour place of the Ingredient Farm. A stone watermill standing on a retaining wall straight out of its pond; the waterwheel turns in a stone slot between two piers under the jettied upper floor.
- **File:** `size: [46, 24, 36]` = design box 46 x 20 x 36 above the ground + 4 ground layers (volume 39,744 < 40,000). **9,482 blocks written (3,045 above ground without water = building + scene, in the 1,500-3,500 range)**, 132 palette entries. Generated: do not edit the YAML by hand.
- **Orientation:** final. Road and yard on the north edge, the pond east of the yard and in front of the wheel, the back garden south.
- **Previews (textured, real 26.3 textures; the wheel is an item display and is **not** in the renders, only its block fallback):** `design/scripts/previews/place_mill_sheet.png`, `_eye` (player at the viewpoint), `_front`, `_ne`, `_nw`, `_back`, `_above`, `_slot` (wheel slot), `_water` (pond view), `_yard`.

![sheet](scripts/previews/place_mill_sheet.png)

### Layout (design cells)
| Part | Cells |
|---|---|
| Road | gravel, z 0..2 the whole width, south edge wobbles |
| **Front yard** | x 11..21, z 3..9 (11 x 7) cobble / mossy / andesite / gravel, wobbling edge, clear of blocks; lamp posts at (10,3) and (22,3) |
| Porch strip | z 10..14: gravel path to the door at x 16..19, flower beds, crates and sacks at the door, low backdrop wall + lanterns at x 19..23, z 10 |
| Hall | x 15..33; ground storey z 15..24 (plinth y0, stone y1..3, string course y4); upper floor z 14..24 (**jetty on the north**: bressumer y5, plaster upper y6..7, plate y8); half-hip roof east end, gable west end; ridge y15 + cap y16 |
| Wheel slot | interior x 25..31 (7 wide), z 15..17, piers x 23..24 and 32..33, back wall z 18, floor y -4, beam lintel y4, pilasters at z 14 (x 15, 24, 32, 33) carry the jetty; pit water y -3..-2 |
| Pond A | one broad organic pond (noisy ellipse + the strip against the hall wall), about x 23..44, z 3..14 (z <= 12 east of x 34), water y -3..-2, mud/clay/moss shore, lily pads; the tail race leaves through the east edge at z 8..9 (water exits `x 45`) |
| Wing (store) | x 8..14, z 15..23, in line with the hall, abuts its west gable; ridge y10 |
| Sack shed | lean-to x 25..31, z 25..27 on the south wall, barrels and sacks |
| Terrace | terrace surface y 1 for x 35..45, z 13..30 behind a stone retaining wall (z 13) to the pond; orchard (3 trees) and a hedge along its east edge (the earlier leat, sluice tunnel and stone bridge are gone: the wheel pit takes its water straight from the pond, nothing stands over water) |
| Granary | staddle stones, 7 x 7 at x 2..8, z 4..10, floor y2, walls y3..4, pyramid roof ridge y9 + cap y10, door + stone steps on the east face (towards the yard), shuttered lit windows |
| Scenery | meadow clusters, trees (one with vines over the pond), hedge segments at the south and west edge, back garden (farmland, water channel) at x 9..15 z 28..32, woodpile, bench, sack cart west of the yard |

### Look (factory craft, scaled)
Stone ground storey in a stone-brick ramp on a cobble/mossy plinth, jettied plastered upper floor in a dark-oak frame (posts every 4, braces, lit windows with sills (shutters on 4 only, no pots)), brackets under the bressumer, **steep (about 56 degrees) dark-oak shingle roof** (one block family: dark-oak field, deepslate-tile trim; no patches), **deepslate-tile eave and rake** and a dark-oak ridge with a slab cap, **half-hip** east end, **two dark-oak dormers** and the **hoist gablet** (open hatch, beam, chain and barrel) on the north slope plus one dormer on the south slope, roofs at 8 heights (3, 4, 5, 8, 10, 11, 12, 16). One chimney budget unused (**no chimney**, `smoke` omitted).

### Points (design cells)
| Point | Value |
|---|---|
| `viewpoint` | `at [16, 0, 5]`, yaw -42, pitch -4 (the north edge of the yard, looking south-east at the front, the wheel and the pond) |
| `yard` | `from [11, 3]`, `to [21, 9]` |
| `hologram` | `[16, 1, 7]` (yard centre) |
| `panel` | `at [20, 0, 7]`, yaw 180 |
| `ripe` | `[12, 0, 7]` (beside the sack cart at x 9..11) |
| `wheel` | `at [28, 0, 16]`, axis `z` |
| `smoke` | none |
| signs | `[23, 2, 14]`: "The Mill" on a spruce wall sign |

Distances from the viewpoint centre (16.5, 0.5, 5.5): hologram 2.1, ripe 4.5, panel 4.7 (all <= 5).

### Waterwheel (item model `cookieclicker:waterwheel`, spec `design/waterwheel.md`)
Breastshot, **7 blocks across, 2 wide**, **16 paddles** (not 12: single-axis element rotation only allows 22.5-degree steps), 58 cuboids, texture 32 x 32 placeholder. Placed as an item display with transform `NONE` at the centre of the `wheel` cell with scale **7/3 (2.333)**; the display rotates about the model's local x axis. The block fallback inside the slot (fences and open trapdoors, above water only) stays under it.

### What is placeholder / needs a human
- The whole place: placeholder until the user's look at the Chunky shots (`Status: final` only with approval).
- Waterwheel texture and the rotation sign (checked in the renderer only; verify the paddles look radial in game).
- Open: the head race does not drive a visible water jet onto the wheel (physics off); pond lily pads and mossy shore are random but seeded.


## 6. Scope change: Mill only, and `countryside.yml`
The market, Cow Barn and Chicken Farm were deleted (files, generators, previews, refs) at the user's request; the farm is the Mill alone for now. The kits and lint rules (L-F1..L-F12, L-D1..L-D7) and `lib/place_gen.py` stay.
- **`houses/countryside.yml`** (generator `design/scripts/countryside.py`): size = the Mill's box, `[46, 24, 36]`, `ground-depth: 4`, every cell keep (`.`), `places: {mill: {at: [0, 0, 0]}}`. The Mill's road meets the box's north edge as in the Mill file.
- **Mill pass (run 2c):** shutters on 4 windows only; one-family dark-oak roof with deepslate-tile trim; sluice tunnel, beam box, coping and stone bridge removed; viewpoint yaw -42 pitch -4 (the eye view shows the mill, the wheel and the pond, not cobble); meadow clusters thicker around the pond; hedge along the west edge beside the granary and along the terrace's east edge, one more tree on the terrace. The block fallback of the wheel stays inside radius 3.2 of the axle (the item display is 7 across). No footbridge: nothing in the Mill needs a way across the pond.
- **Final:** 9,472 blocks written, 3,059 above ground; volume 39,744. Points unchanged except the viewpoint's yaw/pitch.
