# build-quality

**Status:** in progress  <!-- planned | in progress | review | done -->
**Created:** 2026-10-24 · **Depends on:** mariadb (merged, PR #16, 1.3.0)

## Goal
Make our in-world builds look professional before the farm adds six more. Rework the **factory**, the **starter
cottage** (`default`), the **decor** and the **fence** with the techniques in `docs/research/build-quality.md`
(medieval: Norman/Tudor, timber framing, depth, block-palette ramps, layered roofs, one chimney). Add the **ground
layer** so builds sit in shaped terrain instead of on a flat green pad. Build the **review pipeline**: a local Paper test
server plus Chunky path-traced renders that the orchestrator checks like a first-time player. The farm's builds then
reuse the same kits, rules and pipeline. Version **1.4.0**. No gameplay or economy change.

## Player story
I walk up to my bakery and it looks like a place someone built with care. A stone-and-timber bakehouse with a
plastered upper floor that juts out over the street, a dark roof with a proper eave and dormers, one brick oven
chimney that smokes, warm light in the windows and a bit of a room behind them. Around it, the ground isn't a flat
lawn: there's a worn path to the door, gravel at the edges, a flower bed against the wall, barrels and sacks by the
flour shed, a woodpile. Nothing is just dropped on the grass. The small villager workers are still there.

## Acceptance criteria
### Style rules and lint (model-designer)
- [ ] `design/style-guide.md` "Block structures" gets the ranked rules R1–R21 and decor rules D1–D7 from the research
      note. **Hard rule: at most one chimney per building** (and `points.smoke` length ≤ 1).
- [ ] `design/build-lint.md` plus lint asserts in the house generator library (`design/scripts/lib/`). A design that
      fails the lint doesn't get written:
  - at least 2 volumes;
  - a depth change at least every 4 blocks;
  - every material used as a ramp of 3 or more blocks;
  - a roof eave of at least 1 block in 2 materials;
  - plinth of 1–2 courses;
  - every window with a sill, shutters or a box, and a lit interior;
  - chimney count ≤ 1;
  - a clear front yard where the layout needs one;
  - no lone props (D1/D2);
  - nothing organic straight for more than 7 blocks.
- [ ] `design/refs/<house>.md` per reworked build: style, references, palette ramps.
- [ ] The generator's own preview renderer uses real block textures from a git-ignored client-jar cache, so
      `mise run review` shows what the game shows.
### Reworked builds (model-designer)
- [ ] **factory:** per research section 7.
  - One 2×2 oven chimney; the tall boiler stack, the duct and the copper steam pipe are gone, and the ridge vent
    becomes a flush louvre.
  - Stone-brick base ramp, with plastered close-studded dark oak framing above.
  - The roof gets a spruce eave course, a ridge cap, bargeboards, and dormers instead of glass cubes.
  - A 1-block jetty on the north gable, with brackets.
  - The lean-tos get shutters, flower boxes, lit interiors and scene clutter.
  - Kept unchanged: `points.door`, the sign at `[10,4,1]`, the loading hatch, `points.belt_out`, the layout block,
    the design box and the building-slot keep-outs.
- [ ] **default (starter cottage):** a Tudor cottage with at least 2 volumes, plus:
  - a timber frame with plaster infill;
  - a 2-material roof with eave;
  - a 1–2 course plinth;
  - windows with sills and shutters, and a lit interior behind them;
  - exactly one chimney.
- [ ] **decor:** rebuilt as scenes (research section 5).
  - Drop the lone flowers, the `path_edge` rows, the hedge rectangles, the raised single grass pads and the 8
    grid lamp posts.
  - Lamp posts only at the door and junctions.
  - Rework `front_east_wheat` into the garden scene.
  - Keep the keep-out asserts.
  - Keep the small villager workers.
- [ ] **fence:** irregular rhythm (no posts every 4 blocks), in the same palette family.
- [ ] **Whole plot, not only the house (user, 2026-10-24):** ground and decor cover the **entire plot**, around the
      house and between house, belt, pedestal, panels and building slots.
  - **Ground:** shaped and graded (path to the door and the pedestal, gravel and coarse-dirt edges, beds, small
    mounds).
  - **Decor:** scenes anchored to the house, the fence, the paths and the belt.
  - **Keep-outs stay clear:** pedestal, shop/clicks panels, building slots, belt line, viewpoint, entrance, the
    hologram corridor, and the cinematic camera's view of the house.
### Ground layer (plugin-coder)
- [ ] House designs can own ground below their base. The format is decided by the coder with the model-designer
      and documented in `design/house-default.md`. The plugin places and backs it up like the rest of the design.
  - Layers at design y -2 and -1 and an optional height field: mounds up to 3, a ditch, terraces.
  - `/bakery house undo` restores it exactly.
  - Leaves placed by designs are `persistent=true`.
  - Water stays enclosed and doesn't flow outside the box.
- [ ] Old designs without ground load and paste exactly as before.
### Review pipeline (plugin-coder: tools; orchestrator: runs it)
- [ ] `tools/review/` holds the pipeline scripts. A mise task such as `mise run review-render <design>` runs it end
      to end.
  - Place a design on a local test server over RCON (`setblock … strict`, supports first).
  - Write a Chunky scene (camera in Minecraft yaw/pitch, y range -64..64).
  - Render headlessly with the Chunky snapshot channel and the 26.3 client textures.
  - The server, Chunky and client jar live outside the repo (default `~/bakery-review/`, overridable).
  - Never commit the jar.
  - RCON stays bound to 127.0.0.1.
- [ ] For every reworked build the orchestrator renders three shots:
  1. a player's eye from the viewpoint;
  2. a 3/4 overview;
  3. a close-up of the door.

  He reviews them as a first-time player against the style rules, with **at most 3 review rounds** per build.
  The final shots go into the PR, with the before shots from 2026-10-24 next to them.
### Release
- [ ] Version 1.4.0. Checks green: `check`, `validate-assets`, `sound-check`, `balance-check`, `tools-test`.

## Out of scope
- The farm and its places (market village, Mill, Cow Barn, Sugar Works, Cocoa Grove, Chicken Farm) → `ingredient-farm`
  (then 1.5.0), which reuses these kits, rules and the pipeline.
- New item-display models (waterwheel, farm villager workers) → `ingredient-farm`.
- Belt, building slots, panels and holograms: unchanged, except where the reworked house moves nothing they use.

## Assets needed
| Kind | What | Owner |
|---|---|---|
| model / texture / glyph | none new; reworked block designs `factory.yml`, `default.yml`, `decor.yml`, `fence.yml` + their scripts/specs | model-designer |
| sound | none | — |
| effect | none (the smoke and steam effects read the single remaining points) | — |

## Balance impact
none

## Research needed
none: `docs/research/build-quality.md` (committed on this branch). The orchestrator already proved the pipeline on
2026-10-24:
- Paper 26.3-218, WorldGuard 7.0.20, FAWE 2.16.1 and CookieClicker 1.3.0 start cleanly.
- Chunky snapshot 2.5.0-SNAPSHOT.483 renders 26.3 worlds once the y clip is set to -64.
- The camera yaw/pitch mapping is verified.
- A 1280×720, 128-SPP shot takes about 7 s.

## Open questions
1. Split: build-quality (1.4.0) first, then ingredient-farm (1.5.0). The orchestrator proceeded with the split; the
   user can still fold it into the farm.
2. Answered 2026-10-24: ground and decor cover every area of the plot, also around the house.

## Contract (fixed names; change only via the orchestrator)
| Kind | ID | Owner | Notes |
|---|---|---|---|
| house design | `factory`, `default`, `decor`, `fence` (ids unchanged) | model-designer | kept points listed above |
| house YAML | new optional `ground-depth: 0..2`: the first N `layers` lie **below** the house region's min y (design y -N..-1); `size[1]` counts them. Mounds/terraces are ordinary blocks above y0; ditches/water are air/water in the ground layers. Missing key = 0 (old files unchanged) | plugin-coder (parser, paste, backup, undo, cinematic) + model-designer (designs) | documented in `design/house-default.md` |
| decor.yml | `blocks[].at` y may be **-1 or -2** (replaces the plot's ground there, e.g. paths, beds, gravel edges); everything is backed up and restored by `/bakery plot setup` undo like today | plugin-coder + model-designer | `design/decor.md` |
| fence.yml | new optional `post-spacing: [4, 3, 5, …]` (cycled list of gaps, replaces `post-every`) and `topper-spacing` likewise; `post-every`/`topper-every` still accepted | plugin-coder + model-designer | `design/fence.md` |
| placement rule | every `*_leaves` block placed from a design gets `persistent=true` (forced by the plugin even if the file omits it) | plugin-coder | |
| design files | `design/refs/<house>.md`, `design/build-lint.md` | model-designer | |
| mise task | `review-render` | plugin-coder | outputs to `~/bakery-review/shots/` |

## Tasks
| Agent | Task | Needs (gate) | Branch |
|---|---|---|---|
| plugin-coder | ground-layer format + paste/backup/undo, persistent leaves, enclosed water; `tools/review/` + `review-render` task; version 1.4.0 | brief | `build-quality/plugin-coder` |
| model-designer | style guide R1–R21 + D1–D7, lint, textured preview renderer, refs; rework factory, default, decor, fence (using the ground format once merged) | brief; ground format merged | `build-quality/model-designer` |
| orchestrator | render before/after shots, review rounds (max 3 per build) | both merged | feature branch |
| plugin-tester | review the code branch, then integration | owner DONE | (reviewed branch) |

The coder goes first (the ground format is shared). The model-designer can start the style guide, lint and renderer
right away and the builds once the format is merged.

## Status
| Agent | Status | Branch | Verdict | Notes |
|---|---|---|---|---|
| plugin-coder | done | `build-quality/plugin-coder` (+ `-camera`), merged | READY | fix round: LiquidGuard scope, RCON loopback |
| model-designer | in progress | `build-quality/model-designer` (not merged) | | orchestrator review round 3 of 3 running; pinned default-design test needs the tester |

## Merge order
1. research note (done), 2. plugin-coder, 3. model-designer, then the review rounds (model-designer fixes on its branch).

## Needs human review
- [ ] Compare before/after shots in the PR.
- [ ] In game: walk up to the bakery as a new player. Check the build cinematic still runs, the smoke rises from the
      single chimney, the belt still exits at the hatch, and the panels/holograms aren't blocked.
- [ ] `/bakery house undo` restores the ground too.
