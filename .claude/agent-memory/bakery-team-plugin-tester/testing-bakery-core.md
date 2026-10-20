---
name: testing-bakery-core
description: Recipes for testing the bakery-core Java code offline (golden values, H2, MiniMessage strict, Gson/SnakeYAML on test classpath)
metadata:
  type: reference
---

- Paper API (provided) puts Gson, SnakeYAML, Adventure + plain serializer on the test classpath; no extra test deps needed. No Mockito/MockBukkit: test pure classes only.
- Golden values: `docs/balance/golden-values.json` has `cost-growth`, `costs.<id>.single[0..100]`, `costs.<id>.buy-10-from.{0,10,50}`, `states[]` (owned, upgrades, total-cps, click-power). `GoldenValuesTest` now runs for real (3 tests, merged balance files).
- Strict MiniMessage test: building `.name` values leave `<#hex>` open on purpose, parse those leniently.
- NumberFormatter 1e22 off-by-one was fixed in pass 2 (BigDecimal); the regression test is enabled.
- `StationFile` has a plugin-free constructor (Path, Logger, Executor, Defaults); no Unsafe hack needed any more.
- `YamlConfiguration.loadFromString` works without a server: use it for config-parser tests (StationVisualSettings, PluginSettings). Shipped config: `new File("src/main/resources/config.yml")`.
- No MockBukkit/Mockito: view/StationViews/ClickRouter are in-game only; test the pure helpers (StationFrame, CookieMotion, RowState).
- `ShippedContentConsistencyTest` (added at integration) cross-checks sounds/effects/item models/glyph/building links against the pack; extend it when events/buildings are added.
- Python one-offs that need PyYAML: use `.venv/bin/python -I` (system python3 has no yaml).
- `mise run pack` is reproducible (same SHA-1 twice); bakery-core pack = 23 files, 39321 bytes at integration (2026-10-18). Wheat Farm unlocks at 100 baked.
- Integration baseline 2026-10-18: 238 tests (233 + 5), validate-assets 12 files, sound-check 7 sounds, balance-check 6/6, tools-test 133.
