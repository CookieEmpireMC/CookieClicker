# Plot fence style: `houses/fence.yml`

Status: placeholder (reworked 2026-10-24 for `build-quality`; textured preview reviewed)

- **Purpose:** the look of the fence `/bakery plot fence place` builds on the outer ring of the plot (`layout.plot-size` in `houses/factory.yml`), with the 3-wide gate at `layout.entrance`.
- **File:** `src/main/resources/houses/fence.yml`, **generated** by `design/scripts/fence_style.py` (`mise run assets`); block states checked with `lib/house_lib.py`, the rhythm with `lib/build_lint.lint_fence` (R16).
- **Preview:** `design/scripts/previews/fence_preview.png` (textured, front ring with the gate, seen from outside); the whole ring is in `decor_sheet.png`. `fence_style.ring()` mirrors the placement rules below for the previews.
- Only non-directional blocks and vertical logs, so the coder never rotates anything: one style for every side and every polygon corner.

![fence](scripts/previews/fence_preview.png)

## Look
Dark oak fence rails between **2-high posts** (stone-brick base, dark oak log), posts at **irregular gaps** (4, 3, 5, 4, 6, 3, 4, 5 cycled), a lantern on some posts (every 2nd, 3rd, 2nd, 4th post), 4-high **corners** (stone brick, mossy stone brick, dark oak log, lantern). The gate: two stone-brick pillars (stone/mossy mix, chiselled cap, copper lantern) either side of a 3-wide, 3-high opening under a dark oak plank lintel with a slab. Same family as the factory (stone, dark oak, copper).

## Format (contract; the coder parses it, the tester tests the parser)
```yaml
rail: "minecraft:dark_oak_fence"                       # block id only, no states
post: ["minecraft:stone_bricks", "minecraft:dark_oak_log[axis=y]"]   # column, bottom -> top
post-spacing: [4, 3, 5, 4, 6, 3, 4, 5]                # cycled gaps (blocks) from each side's starting corner
corner: ["minecraft:stone_bricks", "minecraft:mossy_stone_bricks", "minecraft:dark_oak_log[axis=y]"]
topper: "minecraft:lantern[hanging=false,waterlogged=false]"
topper-spacing: [2, 3, 2, 4]                          # cycled gaps counted in POSTS between toppers; [] = none on posts
gate:
  width: 3
  left:  [stone_bricks, mossy_stone_bricks, stone_bricks, chiseled_stone_bricks, waxed_copper_lantern[...]]
  right: [...]
  arch:  [null, null, null, dark_oak_planks, dark_oak_slab[type=bottom,...]]
```
Every value is a full 26.3 block data string; `null` = air.

- **`post-spacing`** replaces `post-every`; **`topper-spacing`** replaces `topper-every` (still accepted by the plugin for old files). Combining a new key with its old one is an error. Each gap is 1..64, at most 32 gaps.
- **Placement rules.** Columns (`post`, `corner`, `gate.*`) are bottom -> top from the ground surface (index 0 = y 0, the first air block above the ground).
  - **Rail:** every ring cell that is not a corner, post or gate cell gets `rail` at y 0; the coder sets `north/east/south/west` from the neighbours after placing the whole ring (a fence connects to fences, gates and full solid blocks; every post, corner and pillar here has a full solid block at y 0), `waterlogged=false`.
  - **Corners:** every cell where the ring turns gets `corner`, then `topper`.
  - **Posts:** along each side, starting at that side's starting corner, the n-th post sits at the sum of the first n gaps (cycled); cells within 1 of a corner or of a gate pillar stay rails. The k-th post of a side (counting from 1) gets `topper` when k reaches the running sum of `topper-spacing` (2, 5, 7, 11, 13 ... for `[2, 3, 2, 4]`).
  - **Gate:** centred on `layout.entrance`: the 3 opening cells get `gate.arch`, the cell left of the opening `gate.left`, the one right `gate.right`; the gate uses the ground height of the entrance cell.
- Nothing hangs or needs side support: lanterns stand on posts or corner logs, the slab sits on the planks.

## Placeholder / needs a human
- Check in game: the post rhythm on the 31 x 30 ring, lantern light along the fence at night, whether the gate lintel at 3 high feels roomy.
