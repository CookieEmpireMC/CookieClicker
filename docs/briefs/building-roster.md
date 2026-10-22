# building-roster

**Status:** in progress  <!-- planned | in progress | review | done -->
**Created:** 2026-10-22 · **Approved:** 2026-10-22 · **Depends on:** plot-polish (merged, PR #11/#12, 1.1.0)

## Goal
Grow the economy from 2 buildings to the GDD's 11 (§4.2) with milestones (§4.3), and make the bakery buildings
**visible on the factory plot** (§3.2): every purchase shows up in the world, and the models get nicer at milestones.
The ingredient buildings (Wheat Field, Cow Barn, Sugar Cane Field, Beehive, Cocoa Plantation) are part of the
economy now, but their visuals live on the coming **ingredient farm** (feature `ingredient-farm`, next).

## Player story
I buy a Baker's Apprentice and a little baker pops up next to the factory with a puff and a "ding". After a while I
have six of them, then an Industrial Oven appears in the yard, smoking. At 25 Apprentices they upgrade their look,
with a sparkle and a fanfare. The Bakery Shop panel stays tidy even with 11 buildings: I scroll it with ▲/▼.

## Acceptance criteria
### Economy
- [ ] `buildings.yml` has the 11 GDD buildings with GDD costs/CPS as the starting point, tuned by the
      balance-designer against `docs/balance/targets.md`. Existing IDs stay (no save migration):
      `wheat_farm` = Wheat Field, `villager_baker` = Baker's Apprentice; new IDs below.
- [ ] Unlocks stay `unlock: { baked: N }` for now (plot expansions don't exist yet); the balance-designer picks N
      so the pacing targets hold. The later `plot-expansions` feature will replace them.
- [ ] **Milestones:** each building's production ×2 at 10/25/50/100/150/200 owned, from `buildings.yml`
      (`milestones:`), with the maths in `docs/balance/model.md` and new golden values. CPS shown everywhere
      (HUD, panel `+<cps>/s`) includes milestones.
- [ ] Late click upgrades re-checked against the new roster (user note: long late gaps are fine only if more
      buildings follow; otherwise retune them).
### Panel
- [ ] The Bakery Shop panel shows at most `max-rows` rows and pages with **▲ / ▼** labels on the toggle line
      (ray-picked like the ×N labels; hidden when not needed). Default view: the next locked building at the
      bottom, the most expensive unlocked above it.
- [ ] Rows stay two lines and fit `line-width` with the longest name ("Baker's Apprentice", "Extra Conveyor Belt")
      and ×1000 / big costs (shorter template or wider line-width; checked in a test).
### Visible buildings (factory plot)
- [ ] A new design file `houses/building-slots.yml` (model-designer) lists slots per bakery building on the factory
      plot: `at` (plot cell, like `layout:`), `yaw`. Instance *i* of a building shows on slot *i*, up to its slot
      count (the visual cap, ≤ 10 per type); the count behind it keeps counting.
- [ ] Each visible building is an item display (`cookieclicker:building_world/<id>`, `custom_model_data`
      strings[0] = stage `1`/`2`/`3`; stage 2 from 25 owned, stage 3 from 100 owned). **Client-side, per player:** each player
      sees only their own buildings at the admin-placed location (like the cookie, panels and basket; plots are
      one shared location, not per-player regions). Vanilla fallback: a fitting block display.
- [ ] A new instance appears with effect `building_appear` + sound `building_appear`; a stage change plays
      `building_milestone` + sound `building_milestone` on every visible instance of that type (at most one
      sound per purchase, even for ×Max buys).
- [ ] Spawning/updating only on a change (purchase, join, plot enter, reload); no per-tick work; entities are
      non-persistent and cleaned up like the other plot visuals.
- [ ] The decoration (`houses/decor.yml`) makes room: the slot zones are free of decor and in the decor
      keep-out (`DecorKeepOut` adds the slot cells), the walk and panels stay clear.
### Release
- [ ] Version bumped to **1.2.0** in this PR.

## Out of scope
- Ingredient visuals and the farm plot, ingredients as a resource, recipes → `ingredient-farm`.
- Expansion-based unlocks and plot growth → `plot-expansions`.
- Chest-GUI shop (§4.7).

## Assets needed
| Kind | What | Owner |
|---|---|---|
| model / texture / glyph | world models for the 6 bakery buildings × 3 stages; 9 new panel icons (`cookieclicker:building/<id>`); `houses/building-slots.yml`; decor rework | model-designer |
| sound | `building_appear`, `building_milestone` | sound-designer |
| effect | `building_appear` (small pop/puff), `building_milestone` (sparkle burst) | vfx-designer |

## Balance impact
New roster of 11 buildings, milestones, unlock thresholds, click-upgrade re-check; `targets.md` gets targets for the
new buildings (e.g. first Industrial Oven, all buildings within the horizon where sensible). balance-designer.

## Research needed
none

## Open questions
none

## Contract (fixed names; change only via the orchestrator)
| Kind | ID | Owner | Notes |
|---|---|---|---|
| building | `wheat_farm` (Wheat Field), `villager_baker` (Baker's Apprentice), `cow_barn`, `sugar_cane_field`, `industrial_oven`, `extra_belt`, `beehive`, `cocoa_plantation`, `shipping_centre`, `alchemy_lab`, `portal_bakery` | balance-designer | ingredient buildings: wheat_farm, cow_barn, sugar_cane_field, beehive, cocoa_plantation |
| config key | `buildings.yml: milestones` (list of counts, ×2 each) | balance-designer | format in `docs/balance/model.md` |
| item model | `cookieclicker:building/<id>` | model-designer | panel icons (2 exist) |
| item model | `cookieclicker:building_world/<id>` for the 6 bakery buildings | model-designer | `select` on `custom_model_data` strings[0] = `1`/`2`/`3`; bottom at model y 8 (ItemDisplay NONE), scale 1; footprint/height per stage in `design/buildings-world.md` |
| house design file | `src/main/resources/houses/building-slots.yml` | model-designer | `slots: { <id>: [ {at: [x, y, z], yaw: <deg>}, … ] }`, plot cells like factory.yml `layout:` |
| sound event | `building_appear`, `building_milestone` | sound-designer | keys `cookieclicker:building.appear` / `.milestone`, fallbacks `minecraft:block.wood.place` / `minecraft:ui.toast.challenge_complete` |
| effect | `building_appear`, `building_milestone` | vfx-designer | at the building's bottom centre |
| message key | `buildings.<id>.name` | plugin-coder | GDD names |
| config key | `config.yml: plot.buildings.*` (stage thresholds 25/100, fallback blocks, view range) | plugin-coder | |

## Tasks
| Agent | Task | Needs (gate) | Branch |
|---|---|---|---|
| balance-designer | roster, milestones (model + golden values + simulator), unlocks, targets, click-upgrade re-check, report | contract | `building-roster/balance-designer` |
| model-designer | world models (6 × 3 stages), 9 icons, building-slots.yml, decor rework, `design/buildings-world.md` | contract | `building-roster/model-designer` |
| sound-designer | `building_appear`, `building_milestone` | contract | `building-roster/sound-designer` |
| vfx-designer | `building_appear`, `building_milestone` presets | contract | `building-roster/vfx-designer` |
| plugin-coder (economy) | milestones in Economy/BuildingDef/parser, panel paging ▲/▼, row fit, messages, version 1.2.0 | contract | `building-roster/plugin-coder-economy` |
| plugin-coder (world) | building-slots parser, visible buildings (spawn/update/stages/effects/sounds), decor keep-out of slots, config `plot.buildings` | contract | `building-roster/plugin-coder-world` |
| plugin-tester | review each code branch, then integration | owner DONE | (reviewed branch) |

The world part reads owned counts through the existing Economy API only; milestones affect it only through the
stage thresholds in config.yml, so the two coder parts don't share files.

## Status
| Agent | Status | Branch | Verdict | Notes |
|---|---|---|---|---|
| | planned | | | |

## Merge order
1. model-designer, 2. sound-designer, 3. vfx-designer, 4. balance-designer, 5. plugin-coder (economy), 6. plugin-coder (world)

## Needs human review
- [ ] Look: the 6 bakery buildings × 3 stages on the plot, in scale with the factory and basket
- [ ] Listen: `building_appear`, `building_milestone`
- [ ] In game: buy ×1/×10/Max; buildings appear in their slots up to the cap; stage change at 25/100
- [ ] In game: the panel pages with ▲/▼; long names fit
- [ ] Balance report: pacing with 11 buildings and milestones
