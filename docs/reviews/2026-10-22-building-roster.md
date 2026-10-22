# Review: building-roster (integration)

Reviewer: Gandalf. Branch: `feature/building-roster` at d158726 (+ the tests and docs of this review).
Target: Paper 26.3 (`26.3.build.205-stable`), Java 25, `api-version '26.3'`, version 1.2.0 (pom).

## Verdict: READY

No HIGH or MEDIUM bugs and no PERF-HIGH. Open items are LOW / PERF-LOW (follow-ups below, no fix round).

## Checks run (on the feature branch)
| Check | Result |
|---|---|
| `mise run check-quiet` | 682 tests, 0 failures, 0 skipped (677 before this review, +5 mine) |
| `mise run validate-assets` | 93 files, 0 problems |
| `mise run sound-check` | 28 sounds, 0 failed (building appear x3 + milestone are mono) |
| `mise run balance-check` | 19/19 targets pass |
| `mise run tools-test` | 146 tests, OK (1 environment skip) |

## Summary of the branch reviews (all READY, details in my ledger)
- **plugin-coder-economy** (verified at 6ce0d9a): milestones in `BuildingDef`/`Economy`/parser (per-building override, `[]` = none),
  `buildingCpsGain` for the panel, ▲/▼ paging (`PanelPages`, `PanelRows.maxTop`), two-line rows at line-width 150.
  Its scroll-pulse LOW was fixed in the cleanup branch (336e7d2).
- **plugin-coder-world** (verified at c11df8d): `VisibleBuildings` per-owner client-side displays, `BuildingSlots`, `BuildingLayout`,
  decor keep-out, `plot.buildings.*`. LOWs fixed in the cleanup branch: type cap checked before the cell is reserved (0a725c1),
  effects capped per purchase (803425e), keep-out covers each slot's footprint square (803425e).
- **balance-designer** (b7613e3): milestones/override in the simulator, baked and min-seconds targets, golden fix for representable
  one-short pouches (ae933a1). Pacing: first wheat farm casual 2m14s, rebirth threshold not before 3h49m casual / 3h24m active.
- Asset branches (model, sound, vfx): no branch review; checked here by the asset gates and the consistency tests below.

## Integration findings (fit)
- **Economy vs golden values:** the Java economy matches `golden-values.json` for costs, bulk, Max, upgrades and all `states`
  (total CPS across milestones, summed in file order, within 1e-9). `GoldenValuesTest` did not read the new keys, so I added
  `milestoneListsAndPerBuildingCpsMatch`: every shipped building has recorded `building-cps`, `building-milestones` equal the parsed
  `BuildingDef.milestones()`, and for every recorded row (owned 0, 1, 9, 10, 24, 25 ... 200, 250) `milestonesReached`, factor and
  `Economy.buildingCps`/`totalCps` match. Passes: no drift from `docs/balance/model.md`.
- **Roster fit:** 11 buildings in `buildings.yml` in ascending cost and unlock order (the shop default window relies on it; the
  file says so). Each has `messages.yml buildings.<id>.name`, a `station.panel.icons.<id>.fallback-material`, and an item definition
  `cookieclicker:building/<id>`. The stricter check is back in `ShippedContentConsistencyTest.everyBuildingHasNameAndFallbackIcon`:
  icon keys and message names must be a subset of `buildings.yml` (no stray ids).
- **World buildings:** the 6 ids in `building-slots.yml` are all in `buildings.yml`, have `plot.buildings.fallback-blocks` and
  `footprints` entries (key sets equal), `items/building_world/<id>.json` with cases `1`/`2`/`3`, the three stage models and their
  textures, and the `building_appear` / `building_milestone` sound (sounds.yml -> sounds.json -> .ogg) and effect presets exist
  (new test `visibleBuildingsHaveSlotsConfigModelsStagesSoundsAndEffects`).
- **Slots vs decor vs keep-out:** new test `shippedSlotsWithTheirFootprintsAreClearOfEveryLayoutZoneAndTheShippedDecor`: every cell
  covered by any shipped footprint is off the fence ring, factory + margin, front zone (gate, pedestal/basket, shop and click panels),
  walk to the door and belt + margin; and the shipped `decor.yml` has no block in any slot footprint. Passes.
- **Cleanup items covered:** `aFootprintKeepsOutEveryCellItsSquareCoversAndNoMore` (1.55 square = 3x3 cells, 0.82 = own cell, missing
  entry = own cell) and `anOverCapCellIsNotReservedSoAnotherTypeCanUseIt` (11th cell dropped by the cap can be used by another type).
- **Shop paging at 11 buildings, max-rows 8:** `maxTop = 3`, paging on; the row-fit test now runs over **all 11** shipped names (was 3),
  in both styles with a x10000 amount and huge costs: always exactly two lines within line-width 150.
- **Config vs code defaults:** `BuildingVisualSettings` defaults (stage 25/100, view-range 1, stagger 2, cap 12, scale 0.8, barrel)
  equal the shipped `plot.buildings`; `station.panel.line-width` 150 = the code default. `pom.xml` is 1.2.0; build settings unchanged.
- **Hard rules:** no `playSound` outside `sound/`, no `spawnParticle` outside `vfx/`, no NMS (`ArchitectureRulesTest` green); balance
  numbers only in `buildings.yml`/`upgrades.yml`; the only numbers in Java for buildings are the visual stage thresholds from config.

## Performance
- No per-tick work for buildings: they change only on purchase, join, plot enter/leave, plot change, pack swap, reload.
- Per purchase: one pass over the owner's 6 types; effects capped at 12 and staggered 2 ticks, one sound.
- Entities: up to 8+4+2+2+2+1 = 19 hidden item displays per owner in the plot; N players in the shared plot = N x 19 tracked entities
  at one location (PERF-LOW, estimated 0.1-0.3 ms/tick for ~1000 hidden displays, unmeasured).
- Panel: 1 Hz refresh, text re-sent only when changed; `toggleKey` string per refresh is negligible (PERF-LOW).

## Bugs
None HIGH/MEDIUM.

## Follow-ups (LOW / PERF-LOW, for the PR list)
1. `VisibleBuildings` spawn on an unloaded chunk returns null; the count is recorded and the rest waits for the next change (no retry). Rare.
2. Stage thresholds (`plot.buildings.stage-2/3` = 25/100) duplicate the milestone numbers in `buildings.yml`; if the balance changes
   the milestones the looks drift (visual only). Consider deriving them or noting it in `buildings.yml`.
3. `design/buildings-world.md` / slot file says the Apprentice footprint is 0.81, `config.yml` has 0.82 (same cells covered; cosmetic).
4. `Economy.nextBuildingCps` is unused in main (dead code); the 5-arg `PanelUpdater` constructor is test-only.
5. Parser treats a `null` per-building `milestones:` as none, `model.md` says absent falls back (absent does fall back; only an explicit null differs).
6. `tools/balance/main.py` line 188 is 129 chars; the two rebirth targets are min-only, so a too-slow 1B curve passes vacuously (add a max at the 14400 s horizon).
7. Default shop window relies on `buildings.yml` file order = cost order; add it to `docs/balance/model.md`.
8. PERF-LOW: profile hidden building displays with N players in one plot (below).

## In-game test guide (for the PR)
Needs the resource pack (also try once without it for the fallback blocks) and an admin-placed factory plot.
1. `/bakery plot setup`, join the plot. Buy Baker's Apprentice x1, x10, x25 (/give cookies as needed): a baker pops up in each slot with a puff and a "ding";
   at 25 the look upgrades with a sparkle and one fanfare; at 100 again (stage 3). A xMax buy plays one sound, not a burst.
2. Buy each bakery building once: oven, belt, shipping centre, alchemy lab, portal bakery appear on their slots, facing the factory, bottoms on the ground (check model height/alignment).
3. A second player in the same plot sees none of your buildings (and you see none of theirs).
4. Leave and re-enter the plot, relog, `/bakery reload`: buildings come back once, no duplicates, no effects replay.
5. The shop panel with 11 buildings: 8 rows, ▲ / ▼ on the toggle line, scrolling by clicking them, default view with the next locked building at the bottom; "Extra Conveyor Belt" and big costs stay on two lines; scrolling does not make every row pulse.
6. Decor: nothing stands in or under a building footprint, nor on the walk, panels, belt.
7. Listen to `building_appear` and `building_milestone`; look at the 18 models in scale with the factory and basket.
8. **spark:** `/spark profiler start --timeout 60` while buying x1/x10/Max repeatedly and with 10+ players standing in the plot. Good: the plugin's share of the tick stays about as before (well under 1 ms/tick); buying shows a short spike only on the tick of the purchase.
