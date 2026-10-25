---
name: countryside-places
description: Countryside place pipeline (kits, L-F lint, place format, Mill generator, waterwheel model), where things live and gotchas (2026-10-25)
metadata:
  type: project
---

- Files (2026-10-25, run 1 of countryside-builds): kits `design/scripts/lib/farm_kits.py` (roof solver `hipped_roof`, dormer/hoist, wheel_slot, mill_race, pond, bridge, terrace ...), lint `lib/farm_lint.py` (L-F1..11), `lib/place_lib.py` (points contract, writer), `lib/place_views.py`, `lib/block_ids.py` (sources-jar validator), tests `lib/tests/test_farm_kits.py` run by `design/scripts/farm_kit_tests.py` (part of `mise run assets`; tools-test only discovers `tools/`, and `tools/**/test_*.py` belongs to the tester). Mill: `house_mill.py` + `mill_layout.py` -> `houses/mill.yml`; wheel: `waterwheel.py`. Spec `design/countryside.md` (format section is the contract with the coder).
- Format facts from the coder (2026-10-25): points y counts from the first layer above ground-depth; viewpoint `at` = feet cell; yard from/to inclusive any order; required viewpoint, yard, hologram, panel, ripe; smoke 0-1; countryside `places: {name: {at}}` no yaw; box volume limit 40,000 (Mill uses 39,744: 46 x 24 x 36).
- Gotchas: scratchpad dir is SHARED with other agents (file names like edit2.py got overwritten): use unique names. Worktree sandbox refuses `cat >` heredocs and `$VAR` python args: use Write + literal paths. Renderer needs `ground_y=-6` for water. Roof solver needs an explicit ridge `axis` for short wings. Wheel: element rotation only 22.5 steps -> 16 paddles; ItemDisplay scale 7/3.
- Waterwheel rotation sign (positive x rotation maps a z-bar to -theta) matches model_render only; verify in game.
- Run 2a (2026-10-25): `lib/place_gen.py` (road, lawn, meadow, hedge, drystone, trough, hay_rick, `finish()`) + `house_cow_barn.py`, `house_chicken_farm.py`, `house_market.py`; kits catslide_roof, corbie_gable, barn_door_double, fill_under, hipped_roof(clip=) done; `lint_place(size_range=)`. The house format has no lectern block (coder places it at point recipe_book). Lint traps: open trapdoors in walls leak the window flood fill; leaves near roofs = chimney; parapet caps -> roof_cells; hedges bridged by tree leaves. Stubs left: round_building, cone_roof, hay_roll_thatch, eyebrow_dormer.
