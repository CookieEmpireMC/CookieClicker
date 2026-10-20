# factory-makeover: model-designer branch review (2026-10-20)

Target: Paper 1.21.x (design files only). Reviewed ded796f..2b06c80 plus Gandalf's test fix.

- `mise run check`: green (435 tests). `validate-assets`: green. `tools-test`: was RED (15 errors), fixed, now green (146).
- Bug (fixed in tools/test_house_design.py): the `house_lib` refactor moved `to_layers`, `validate`, `check_block_data` and `GRAVITY` out of `house_default.py`; the test still called them on the generator. Test now goes through `house_lib`. Generator code untouched.
- Reproducible: `house_factory.py`, `fence_style.py`, `house_default.py` regenerate with no diff; `default.yml` is byte-identical to before.
- Contract: `points.smoke` (2), `points.steam` (2), `belt_out`, `door`, `layout:` all present and consistent (belt 5 cells from hatch to pedestal, shop beside it, 6 free blocks to the fence on every side). `fence.yml` has all contract keys. Palette is vanilla only.
- Java parser: it rejects list points, but no Java test loads `factory.yml`/`fence.yml` (only `default.yml`), so `check` is NOT broken on this branch alone. `/bakery house place factory` fails at runtime until the plugin-coder parser merges. Merge coder after this branch.
- Previews: reads clearly as a factory (tall brick stack, second stack, roof vents, oven/awning, crates, lanterns).
- Performance: n/a (static assets, 796 blocks).

VERDICT: READY
