---
name: testing-bakery-core
description: Recipes for testing the bakery-core Java code offline (golden values, H2, MiniMessage strict, Gson/SnakeYAML on test classpath)
metadata:
  type: reference
---

- Paper API (provided) puts Gson, SnakeYAML, Adventure + plain serializer on the test classpath; no extra test deps needed. No Mockito/MockBukkit: test pure classes only.
- Golden values: `docs/balance/golden-values.json` has `cost-growth`, `costs.<id>.single[0..100]`, `costs.<id>.buy-10-from.{0,10,50}`, `states[]` (owned, upgrades, total-cps, click-power). To dry-run `GoldenValuesTest` before the balance merge: generate yamls + golden from `tools/balance/model.py` (script in scratchpad), copy yamls to `src/main/resources`, run, then delete them.
- Strict MiniMessage test: building `.name` values leave `<#hex>` open on purpose, parse those leniently.
- NumberFormatter has a known off-by-one from 1e22 (Math.pow inexact); test is @Disabled until fixed (bakery-core pass 1 review).
