---
name: ledger-tidy-world
description: tidy world branch review READY (2026-10-23): house sub-package test moves, W1 bounds facts, open LOWs
metadata:
  type: project
---

`tidy/plugin-coder-world` (merged into `tidy/plugin-tester-world`) reviewed READY, 390 tests. Doc: docs/reviews/2026-10-23-tidy-world.md.

- Test move recipe (stable): a Python script in the scratchpad did `git mv` + package line + FQN rewrite + auto-added imports
  from a class->package map built from `src/main/.../house/{design,setup,cinematic}`; compile errors then showed only the
  path-based tests (ArchitectureRulesTest, HouseCinematicsCameraSourceTest read `src/main/java/...` paths).
- `theWorldIsNeverModifiedByTheCinematicClasses` now scans all of `house/cinematic` (no class list to maintain).
- W1 facts: `PlotRegistry.mayContainPlot` = one union box per world UID of regions flagged plot/house of a known plot; cleared
  only by `replaceAll`/`put`; open LOWs: stale until reload, empty box cached if WG has no data yet (optional 30 s TTL).
- `PlotRegistryBoundsTest` uses Proxy `World` with getUID/equals; the fake `PlotRegions.at` throws to prove no per-block query.
- Belt ride task (`ConveyorBelts.refreshSafely` starts, `rideTick` stops itself) is untestable without a server.
- Sandbox: `sed -i` with a `$VAR` path is refused; use literal paths.
