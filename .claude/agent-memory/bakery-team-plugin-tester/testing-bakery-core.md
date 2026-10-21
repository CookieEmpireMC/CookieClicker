---
name: testing-bakery-core
description: Recipes for testing the bakery-core Java code offline (golden values, H2, MiniMessage strict, Gson/SnakeYAML on test classpath)
metadata:
  type: reference
---

- Paper API (provided) puts Gson, SnakeYAML, Adventure + plain serializer on the test classpath; no extra test deps needed. No Mockito/MockBukkit: test pure classes only.
- Golden values (2026-10-21, schema of the repeatable-upgrade contract): top keys `cost-growth`, `max-bulk`, `click`, `costs.<b>.{single[101], buy-10-from, bulk[{owned,count,cost}]}`, `upgrades.<id>.{cost, repeatable, cost-growth, max-level, type, value, single[max-level], bulk[{level,count,cost}]}`, `max-affordable[]` (190 cases: `{kind,id,owned,pouch,max,case}`), `states[].upgrades` = MAP id→level (not an array; Gson getAsJsonArray on it throws ClassCastException). Max cases: build the pouch with baked=Double.MAX_VALUE and every building owned=1000 so unlocks never hide the boundary. `GoldenValuesTest` = 6 tests, none skipped.
- Economy test fixtures: `EconomyTestSupport.config()` = one-time upgrades only; `repeatableConfig()` adds `strength` (growth 1.25, max 3) and `focus` (×2, growth 2, max 5). Bulk numbers: 15 base, 1.15 growth: ×10 from 0 = 305, ×4 = 75, ×5 = 102; 200 base, 1.25: 0→2 = 450, lvl2→3 = 313.
- Schema v3 migration tests: v1 DB uses `zz_` prefix and schema_version 1; v2 DB needs a `zz_house_seen` table too. Corrupt-row inserts must name columns (upgrade_level).
- NumberFormatter real list: `RealMessages.formatter()` / `RealMessages.yaml().getStringList("numbers.suffixes")`; 33 entries K..DTg; large-format is `{value}{suffix}` (no space).
- Python: `tools/balance/test_balance.py` has SeriesMathTest (series_cost/series_max brute force, seeded `random.Random`). `mise run tools-test` = 134 tests, 1 env skip.
- Strict MiniMessage test: building `.name` values leave `<#hex>` open on purpose, parse those leniently.
- NumberFormatter 1e22 off-by-one was fixed in pass 2 (BigDecimal); the regression test is enabled.
- `StationFile` has a plugin-free constructor (Path, Logger, Executor, Defaults); no Unsafe hack needed any more.
- `YamlConfiguration.loadFromString` works without a server: use it for config-parser tests (StationVisualSettings, PluginSettings). Shipped config: `new File("src/main/resources/config.yml")`.
- No MockBukkit/Mockito: view/StationViews/ClickRouter are in-game only; test the pure helpers (StationFrame, CookieMotion, RowState).
- `ShippedContentConsistencyTest` (added at integration) cross-checks sounds/effects/item models/glyph/building links against the pack; extend it when events/buildings are added.
- Python one-offs that need PyYAML: use `.venv/bin/python -I` (system python3 has no yaml).
- `mise run pack` is reproducible (same SHA-1 twice); bakery-core pack = 23 files, 39321 bytes at integration (2026-10-18). Wheat Farm unlocks at 100 baked.
- Integration baseline 2026-10-18: 238 tests (233 + 5), validate-assets 12 files, sound-check 7 sounds, balance-check 6/6, tools-test 133.
