# Build lint

The style rules of `design/style-guide.md` ("Block structures") as asserts. A design that fails the lint is **not written**
(the generator raises `LintError`, writes the textured previews anyway so you can see what is wrong, and leaves the YAML
untouched). Code: `design/scripts/lib/build_lint.py`; the generators call `lint_house`, `lint_decor` and `lint_fence`.
`mise run assets` runs all of it; there is no switch to turn a rule off (a generator may skip one rule per design by name in
`Notes.skip`, with a comment saying why: today none does).

## How it knows what the design is
- **Annotations** from the kits while building (`Notes`): volumes, material ramps, roofs (eave + field cells, overhang),
  plinth ring, chimney stacks, the roof cells, the body box, the wall rows, a sealed opening list and the front yard.
- **The block grid itself** for everything that can be derived: windows (connected glass-pane clusters, which side is
  outside by flood fill from outside the box), depth planes, props, ground treatments, chimney stacks above the roofline.
- When the 26.3 client jar is present, every block id and state value is also checked against the jar's blockstates
  (`block X has no blockstate`, `value n=v not in ...`).

## Rules
| Rule | Checks (house) | Failure message starts with |
|---|---|---|
| R1/R2 volumes | at least 2 volumes, different footprints, at least 2 different roof heights | `volumes:` |
| R3 depth | for each of the 4 sides, the per-column depth signature over the wall rows (first structural block, ignoring lanterns, pots, barrels, fences, signs, wool/carpet) is not identical for more than 4 columns in a row | `depth:` |
| R5 windows | every glass-pane cluster (bays with panes on both axes are skipped): a sill (trapdoor/slab/stair/pot under or in front of the bottom row), shutters on both sides (open trapdoors beside it, in the wall plane or in front) or a flower box, and a light block within 3 behind it | `window at (x, y, z):` |
| R6/R7 ramps | each ramp has at least 3 blocks used (each at least 1 block and 2 % of the ramp), a dominant base of at least 33 % and at most 93 %, no variant above 45 %; any building material (y >= 0) used 30+ times belongs to a ramp (glass, doors, lanterns, fences, floors of logs, plants etc. are exempt) | `ramp '<role>':` / `material X` |
| R8 roofs | at least one roof registered, eave overhang >= 1, eave and field use different materials | `roof <name>:` |
| R11 chimney | at most one registered stack, `points.smoke` / `points.steam` at most one entry, and at most one connected set of columns rising 2+ above the roofline (brick/stone, not fences/lanterns/logs) | `chimney:` / `points.smoke` |
| R12 plinth | 1-2 courses, at least 85 % of the ring cells are plinth blocks (doors, stairs, slabs and bars allowed), the course above is a different material for at least half the ring | `plinth:` |
| R15 organic edges | in the ground layers (y -1/-2) the union of path/gravel/coarse dirt/podzol/... cells has no straight edge run of more than 7 (edges at the design box border or against built cells are not judged); leaf rows (hedges) likewise | `organic edge:` |
| R19 front yard | in the yard rectangle: nothing in the 3-wide corridor at y 0-2 (plants/pots excepted), nothing above `max_y` elsewhere (lanterns and fences excepted) | `front yard:` |
| D1 props | every prop (barrel, hay, flowers, pots, lanterns, sacks, composter, pumpkin, campfire ...) has a non-prop non-grass block within 1 (26 neighbours, ground layers included) or two other props | `lone prop:` |

Decor (`lint_decor`): the same prop and organic-edge rules over the plot, plus per scene: an anchor, and at least 5 blocks of
3 types or a 9-cell ground patch (D2). Fence (`lint_fence`): `post-spacing` has at least 3 different gaps spanning 2 or more,
`topper-spacing` at least 2 different values.

## Reading a failure
1. Open `design/scripts/previews/*_sheet.png`, written even when the lint fails.
2. Fix the build, not the lint: add a post, reveal or bay (depth), a shelf + lantern (lit), a second material (roof/ramp),
   a wobble in the edge (organic), a ground patch or an anchor (scene).
3. A rule that is wrong for a real reason is changed here and in `style-guide.md` together, with a dated note in `lessons.md`.

## Known limits (judged by eye instead)
Silhouette and proportion, roof area versus facade, colour balance (squint test), whether a window row looks crowded, valleys
and corner stairs (their shapes are computed, their look is reviewed), the interior seen through the glass beyond the
lantern, hologram and camera sight lines (the camera framing of `factory` is asserted separately in `factory_layout.py`).
