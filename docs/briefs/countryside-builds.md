# countryside-builds

**Status:** parked (2026-10-25: the user stopped new builds; not merged to main)  <!-- planned | in progress | review | done -->
**Created:** 2026-10-25 · **Depends on:** build-quality (merged, PR #17, 1.4.0)

Part 1 of 3 of the ingredient farm (user, 2026-10-25): **countryside-builds** (1.5.0, the shared world) →
`ingredients` (1.6.0: economy, Farm Deed, recipes, textured Recipe Book GUI) → `farm-life` (1.7.0: per-player
fields/animals/stages, place unlocks, overgrown paths, ripe harvest, first-visit reveal). The old one-PR draft is the
local branch `ingredient-farm-old-draft` and is reused by parts 2 and 3.

## Goal
Build the **countryside** of GDD §3.3 as shared, professional block builds: a market village and five places (Mill,
Cow Barn, Sugar Works, Cocoa Grove, Chicken Farm) along a looping road with a stream. They are styled like the
reworked factory (not the cottage), sized and detailed after `docs/research/farm-buildings.md`, and reviewed with
renders before anything else uses them. Admins place the whole countryside with one command. Players can walk or
teleport there and look around. No economy or per-player content yet. Version **1.5.0**.

## Player story
I type `/bakery farm` and arrive at a lychgate into a small market village: a cobbled square with a well, a
timber shelter where a book will one day lie, market stalls and a signpost. I follow the gravel road between hedges.
The first stop is a big stone watermill rising straight out of its pond, with the wheel turning slowly in a stone
slot under the jettied upper floor and a granary on stilts beside it. Further on, a long, low barn with a huge dark
roof and one stepped gable over the cart doors sits in its pasture. A stone chicken coop with coped gables stands by
an orchard. Down in a warm hollow under a cliff there's a jungle lodge with a straw-coloured roof, a drying platform
and a striped awning. On the sunny riverbank the Sugar Works steps down three terraces, from a round horse-mill to a
brick boiling house with one smoking chimney. Every place has a clear, lit front yard where something will clearly go
later. `/bakery home` takes me back to my bakery.

## Acceptance criteria
### Generator kits and lint (model-designer, first run)
- [ ] `design/scripts/lib/` gets the research kits: section 9 plus 10b of `docs/research/farm-buildings.md`.
  - **Roofs:** half-hip, catslide, hip, cone/pyramid, hay-roll thatch, pent skirt, coped gable, corbie gable, eyebrow
    dormer.
  - **Shapes and parts:** round/octagon solver, buttress, braced barn door, hoist gablet, board-and-batten, vent slits,
    staddle stones, nest boxes, hurdle and stone-pillar fences, drying rack, awning.
  - **Water and terrain:** wheel slot and mill race, retaining wall, stone bridge, terraces/cut-and-fill, furrow field,
    orchard grid.
  - Each kit is a named, seeded function with a small tool test.
- [ ] The build lint gets L-F1 to L-F11 from the research note, with tests. A place that fails the lint is not written.
- [ ] Every block id is checked against the 26.3 sources-jar validator; the `(v?)` ids in the note are resolved
      (kept if they exist, replaced if not).
### The places (model-designer)
- [ ] **Mill gate:** the designer builds the kits, the lint and the **Mill** first. Rick renders it and shows the user.
      The other places start only after the user's go (the Mill fixes the shared look).
- [ ] Six place designs, each a house design file with ground (height field, water), the building(s) and their scene,
      specs from the research note sections 3–8:

  | Place | File | Size and parts |
  |---|---|---|
  | Mill | `mill.yml` | about 15×11 plus the wheel bay. Stone watermill on a retaining wall out of a pond of at least 13×9; the wheel slot sits between piers under the overhang; staddle granary, mill race, stone bridge. Roof: dark-oak shingle. |
  | Cow Barn | `cow_barn.yml` | about 25×13. A medieval threshing barn: the roof is 55–60% of the façade, one corbie gable over the cart entrance, 2–3 dormers, a catslide outshut, a dairy lean-to and a paddock. Roof: clay-tile look. |
  | Sugar Works | `sugar_works.yml` | 3 terraces: a round horse-mill with a cone roof, a brick boiling house with the **only** chimney, a curing loft, cane beds and a flume. Roof: clay-tile look. |
  | Cocoa Grove | `cocoa_grove.yml` | jungle lodge with a hay-roll thatch hip and eyebrow dormers, a fermentation shed, a drying platform with a striped awning, a round plaza ringed by terraces under a cliff, cocoa trees. No chimney. |
  | Chicken Farm | `chicken_farm.yml` | stone coop with coped gables and a buttress (a staddle variant is allowed), a dovecote with a louvre cupola (not a chimney), a hurdle and stone-pillar run, an orchard tree. Roof: clay-tile look. |
  | Market village | `market.yml` | lychgate, a cobbled square with a well and a market cross, the Recipe Book shelter, a notice board, 2–3 stalls or cottages, a signpost. Roof: dark-oak shingle. |
- [ ] **Sizes:** the Mill, Cow Barn and Sugar Works have 1,500–3,500 blocks each (building plus scene), the others
      600–1,200. Water is about 1/3 of the Mill's front view.
- [ ] **Shared rules:** every place keeps the factory's craft: plinth ramp, dark timber frame, darker eave and ridge,
      a bracket or jetty, lit windows, and at most one chimney.
- [ ] **Points:** every place has a clear **front yard** of about 11×7 (lint L-F3) on its road side, with the points
      `viewpoint`, `yard` (rectangle), `hologram`, `panel`, `ripe` and optional `smoke` (0–1). The Mill also has
      `wheel`; the market has `recipe_book` and `notice_board`.
  - Everything clickable is within 5 blocks of the viewpoint.
  - A calm backdrop stands behind the panel, lanterns keep it lit, and there is no water, leaves or flowers in a
    panel or hologram hitbox (GDD §3.3 "Room for holograms and panels").
- [ ] **`countryside.yml`:** the whole countryside, about 150×120. It holds:
  - where each place sits along the loop (30–40 blocks apart, clockwise from the village: Mill on the stream, Cow Barn
    on meadow hills, Chicken Farm by an orchard, Cocoa Grove against a cliff, Sugar Works on the riverbank);
  - the 3-wide gravel loop road with hedges and verges;
  - the stream (north-west to south-east) with bridges;
  - scenery between places: oaks, birches, willows, orchard, skeps, a shrine, carts, woodpiles;
  - the edge, blended into the surrounding terrain.
- [ ] The **waterwheel** item model `cookieclicker:waterwheel`: breastshot, 7 across, 2 wide, 12 paddles, at most
      about 64 cuboids, centred on its axle. A static block fallback is built into `mill.yml`.
- [ ] Specs and hand-off are in `design/countryside.md` (one section per place: IDs, points, palette, what's
      placeholder) and `design/refs-<place>.md`. Every place has textured previews.
### Placing the countryside (plugin-coder)
- [ ] The WorldGuard flag **`bakery-farm <id>`**, registered in `onLoad` like `bakery-plot`.
- [ ] `/bakery farm setup <id>` (permission `cookieempire.admin`, the plugin's existing admin permission) pastes `countryside.yml` and every place file at its
      layout spot into that region.
  - It uses the same paste, ground-depth, liquid and leaf rules and backup as the house paste.
  - It is spread over ticks, with no long main-thread stalls.
  - Progress shows in chat.
  - The points go to `plots.yml` under `farms.<id>`.
- [ ] `/bakery farm undo <id>` restores everything exactly. `/bakery farm show <id>` outlines the region and marks
      every place's yard and viewpoint (particles, for the admin only).
- [ ] The paste refuses a region smaller than the layout and says how big it must be.
- [ ] Water placed by the paste stays still. The stream and ponds don't flow out of the region.
- [ ] **Waterwheel:** one shared, non-persistent item display at the Mill's `wheel` point.
  - It turns slowly (`config.yml: farm.wheel.rpm`) with display interpolation; no per-tick teleporting.
  - It only runs while a player is within `farm.wheel.view-range`.
  - It is cleaned up on disable or undo.
  - Without the pack, the block fallback in `mill.yml` shows.
- [ ] **Smoke:** the Sugar Works chimney smokes through the existing smoke effect at its `smoke` point, like the
      factory.
- [ ] **Teleports:** `/bakery farm` goes to the market viewpoint, and `/bakery home` goes back to the player's bakery
      viewpoint.
  - Permission `cookieclicker.teleport`, default true.
  - Cooldown `config.yml: farm.teleport-cooldown-seconds`.
  - With more than one farm, `/bakery farm` uses the farm whose id equals the player's plot id, else the first.
  - Without a farm, it says none exists yet.
- [ ] `mise run review-render` accepts the place files and `countryside` (camera presets per place: eye from the
      viewpoint, 3/4, and a close-up of the signature detail). The output names fit the shot list below.
### Review (orchestrator)
- [ ] Rick renders each place (eye, 3/4, detail) and the whole countryside from above, and judges them as a
      first-time player against the style rules and the references. At most 3 review rounds per place.
- [ ] The final shots go into the PR.
### Release
- [ ] Version **1.5.0**. Checks green: `check`, `validate-assets`, `sound-check`, `balance-check`, `tools-test`.
- [ ] The GDD §3.3 update (sizes, the medieval threshing barn instead of the red gambrel barn, the 3-part split) is a
      CookieMemory PR that the user merges.

## Out of scope
- Ingredients, the Farm Deed, recipes, the Recipe Book GUI and the stock hologram → `ingredients` (1.6.0).
- Per-player fields, animals, stages, ripe spots, place unlocks, overgrown "coming soon" paths, the first-visit reveal,
  windmill sails on the hill (wheat stage 3), and ambient sounds and particles → `farm-life` (1.7.0).
- The horse-mill lever animation → `farm-life` or later.
- The map-wide world (hub, bakery village, world border, the 256×256 guideline) → a later map/hub feature. This
  feature only fills the farm region an admin gives it.

## Assets needed
| Kind | What | Owner |
|---|---|---|
| model / texture / glyph | kits + lint; `market.yml`, `mill.yml`, `cow_barn.yml`, `sugar_works.yml`, `cocoa_grove.yml`, `chicken_farm.yml`, `countryside.yml`; `cookieclicker:waterwheel` item model + texture; `design/countryside.md`, `design/refs-*.md`, previews | model-designer |
| sound | none (ambience comes with `farm-life`) | — |
| effect | none (the Sugar Works chimney reuses the existing smoke effect) | — |

## Balance impact
none

## Research needed
none: `docs/research/farm-buildings.md` (merged on this branch, including Rick's first-hand looks at
PlanetMinecraft/BuiltByBit) and `docs/research/build-quality.md` cover it.

## Open questions
none. Decided with the user on 2026-10-25:
- watermill (not a windmill);
- mixed roofs: clay-tile look for Barn, Sugar Works and Chicken Farm; dark-oak shingle for Mill and market; thatch for
  the Cocoa Grove;
- hero builds of 1.5–3.5k blocks;
- turning wheel in this feature, sails later;
- the 3-part split;
- the GDD updated to the research sizes.

## Contract (fixed names; change only via the orchestrator)
| Kind | ID | Owner | Notes |
|---|---|---|---|
| house design file | `src/main/resources/houses/{market,mill,cow_barn,sugar_works,cocoa_grove,chicken_farm}.yml` | model-designer | house format + ground-depth; cells from the file's own min corner |
| house design file | `src/main/resources/houses/countryside.yml` | model-designer | the format is decided by the coder with the designer and documented in `design/countryside.md`: `size`, ground/terrain layers, `places: { <place>: { at: [x,y,z] } }` (unrotated; a non-zero yaw is rejected) |
| design points | `viewpoint {at,yaw,pitch}`, `yard {from,to}`, `hologram`, `panel {at,yaw}`, `ripe`, `smoke` (0–1), `wheel {at,axis}` (mill), `recipe_book`, `notice_board` (market) | model-designer | in each place file under `points:`; required: viewpoint, yard, hologram, panel, ripe (the others optional for every place); the countryside's origin lands on the region's min corner, so the region's min y is ground level |
| item model | `cookieclicker:waterwheel` | model-designer | rotation about its local x axis through the model centre |
| WorldGuard flag | `bakery-farm` (string id) | plugin-coder | |
| command | `/bakery farm`, `/bakery home`, `/bakery farm setup <id>`, `/bakery farm undo <id>`, `/bakery farm show <id>` | plugin-coder | perms `cookieclicker.teleport` (default true), `cookieempire.admin` |
| plots.yml | `farms.<id>` (region, origin, the points of every place) | plugin-coder | |
| config key | `config.yml: farm.teleport-cooldown-seconds`, `farm.wheel.rpm`, `farm.wheel.view-range`, `farm.paste.blocks-per-tick` | plugin-coder | |
| message key | `farm.*` (setup progress/done/too small, undo, teleport, cooldown, no farm) | plugin-coder | messages.yml |
| mise task | `review-render <design>` now also takes the place files and `countryside` | plugin-coder | mise.toml change → Needs human review |

## Tasks
| Agent | Task | Needs (gate) | Branch |
|---|---|---|---|
| model-designer (run 1) | kits + lint + tests; **Mill** + waterwheel model; `design/countryside.md` (format agreed with the coder) | brief approved | `countryside-builds/model-designer` |
| plugin-coder (setup) | `bakery-farm` flag; `countryside.yml` + place parser with points; `farm setup/undo/show`; ticked paste; teleports; `plots.yml farms`; config + messages; version 1.5.0 | brief approved; format agreed with the designer | `countryside-builds/plugin-coder-setup` |
| plugin-coder (wheel+review) | waterwheel display + rotation; review-render for places and the countryside | setup part merged | `countryside-builds/plugin-coder-wheel` |
| orchestrator | render the Mill, then the **user gate** | Mill merged | — |
| model-designer (run 2) | Cow Barn, Sugar Works, Cocoa Grove, Chicken Farm, market, `countryside.yml` | the user's go on the Mill | same branch |
| orchestrator | review rounds (max 3 per place) | run 2 done | — |
| plugin-tester | review the code branches, update tests (parser, points, paste bounds); integration (sonnet) | owner DONE | (reviewed branch) |
| orchestrator | CookieMemory PR: GDD §3.3 sizes/barn + 3-part split + Differences | brief approved | CookieMemory branch |

**Format agreement first:** before run 1 starts building, the coder and the designer settle the `countryside.yml` and
`points` format. Rick passes the coder's proposal to the designer; the coder writes it into `design/countryside.md`'s
format section through the designer.

## Status
| Agent | Status | Branch | Verdict | Notes |
|---|---|---|---|---|
| resource-pack-researcher | done | merged | — | `docs/research/farm-buildings.md` |
| plugin-coder (setup) | done | merged | READY (1 fix round) | 439 tests; admin perm `cookieempire.admin` |
| plugin-coder (wheel+review) | done | merged | READY | wheel scale 7/3 in code; in-game checks listed by the tester |
| model-designer (run 1) | done | merged | user gate passed (Mill kept at its size) | kits, lint, Mill, waterwheel (16 paddles) |
| model-designer (run 2a) | in progress | `countryside-builds/model-designer` | | Cow Barn, Chicken Farm, market |
| model-designer (run 2b) | planned | same | | Sugar Works, Cocoa Grove, countryside.yml |

## Merge order
0. research (done), 1. model-designer run 1 (Mill), 2. plugin-coder (setup), 3. plugin-coder (wheel+review),
   4. model-designer run 2 (the other places + countryside), then the review rounds

## Needs human review
- [ ] **Mill gate:** look at the Mill renders before the other places are built
- [ ] Final shots of every place and the countryside from above in the PR
- [ ] In game: `/flag <region> bakery-farm farm1`, `/bakery farm setup farm1`, walk the loop, `/bakery farm`, `/bakery home`, `/bakery farm undo farm1`
- [ ] Is the waterwheel turning at a pleasant speed?
- [ ] `mise.toml` change (review-render)
