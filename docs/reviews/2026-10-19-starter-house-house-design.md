# Review: starter-house / model-designer (house design)

Reviewer: Gandalf. Branch `starter-house/model-designer` at `b44bfa3` (+ my test commit). Target: Paper 26.3 (`26.3.build.205-stable`, Java 25).

## Results
| Check | Result |
|---|---|
| `mise run assets` regenerates | no diff (`git status` clean afterwards) |
| `mise run validate-assets` | 12 files, 0 problems |
| `mise run check` | BUILD SUCCESS |
| `mise run tools-test` | 146 tests OK (133 before + 13 new) |
| Ownership (changed paths) | all inside model-designer globs (houses yml, design/, previews); my test is under `tools/test_*.py` (tester glob) |

## Contract / spec match
`default.yml`: `size [9,7,7]`, `origin [0,0,0]`, `palette` (21 entries, `.` not in it, `_` = air), 7 layers x 7 rows x 9 chars (bottom to top, z=0 first, x=0 first), `points.chimney [8,6,3]`, `points.door [4,1,1]`, optional `signs`. Matches the brief's contract row and `design/house-default.md`. 170 non-air blocks (spec header agrees). Chimney point sits on a brick (`[8,5,3]`); door point is the lower door half; sign at `[5,2,0]` is the hanging sign.

## Block data validity (26.3)
- All 16 block ids exist as `Material` constants in `paper-api-26.3.build.205-stable-sources.jar` (checked against `Material.java`).
- Property sets are complete and valid: stairs (facing/half/shape/waterlogged), slabs (type/waterlogged), doors (facing/half/hinge/open/powered, both halves match), glass panes (4 sides + waterlogged), logs (axis), hanging sign (attached=false, rotation 0-15 -> 8 = faces north, waterlogged), lantern (hanging/waterlogged), pots/blocks without properties.
- Pane connections match neighbours (generator asserts; I re-read the gable and front windows by hand). Stairs are `straight` and have no perpendicular stair neighbour, so vanilla would not re-shape them.
- No gravity blocks. Nothing needs outside support: door stands on a plinth block, lantern and sign hang under bottom-half stairs (full bottom face), pots and chimney need none. Placement must use physics off (spec says so).
- Only block entity: the hanging sign; sign text is plain lines for the coder to set.

## Tests added (`tools/test_house_design.py`, 13 tests)
Independent parse of `default.yml`: dimensions, `.` vs `_`, quoted specials `- | ^ =` survive YAML as 1-char strings, every char known and every palette entry used, palette syntax, no gravity, points/signs in bounds and pointing at the right blocks, block count, hanging blocks have a ceiling. Generator: accepts own design, committed file equals generator output (drift guard), rejects bad block data (bad value, missing property, gravity, unknown block, no namespace, uppercase), rejects short row / missing row / missing layer / unknown char / out-of-bounds point.

## Findings
- No bugs.
- LOW (note): `tools/house/` is not used; tests live at `tools/test_house_design.py` because a new subdirectory would need an `__init__.py`, which the tester does not own (unittest discovery needs it).
- LOW (human check, already in spec): proportions, roof read, sign text fit and lantern light need a look in game.
- Performance: data file only, 170 blocks; nothing runs per tick.

## For the plugin-coder branch review (parser test cases)
`.` (keep, not backed up) vs `_` (air, backed up and cleared); quoted special chars `- | ^ =` read as plain 1-char strings; row length != `size[0]`, row count != `size[2]`, layer count != `size[1]`; unknown char; `.` inside palette rejected; point / sign / origin out of bounds; invalid block data string (`Bukkit.createBlockData` failure) gives a clear error naming the char; `points.door` and `signs` optional; world position = regionMin + (cell - origin); region smaller than 9x7x7 refused; no physics on placement (door halves and pane states stay); sign text set via `Side.FRONT` after placement.

## Architecture
New code is design tooling only; no `src/main` Java touched. Format is documented in the spec and matches the brief.

VERDICT: READY
