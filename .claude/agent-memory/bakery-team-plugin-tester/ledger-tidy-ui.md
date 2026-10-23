---
name: ledger-tidy-ui
description: tidy ui branch review READY at d2ae11c (2026-10-23): U3 audit result, test files added, sandbox quirks
metadata:
  type: project
---

`tidy/plugin-coder-ui` (head d2ae11c) reviewed, READY, 387 tests after my tests (e8b7420). Doc: docs/reviews/2026-10-23-tidy-ui.md.

- New tests: `storage/JdbcPlayerStoreSkipTest` (sentinel-row trick: UPDATE cc_players SET pouch=-777 straight in H2, then save; skipped iff the sentinel survives; mutable clock via AtomicLong) and `view/PanelTextTest` (compiled rows vs real parse; the click-row reference is re-implemented in the test because PanelText only exposes the static real parse for building rows).
- U3 facts (stable until JdbcPlayerStore changes): lastSaved set after commit only; load/saveFinal clear it; IO executor is a single thread (`PluginExecutors`); PouchSnapshot maps are Map.copyOf so equals is by value.
- Open LOWs: `CookiePouch.revision()` unused in main; static `PanelText.buildingRow` is a test-only reference; heal every 5th refresh.
- Moves: `format/NumberFormatter` -> `text/`; panel pure types -> `view/panel/` (PanelUpdater/PanelText stay in `view`, package-private); `StationViews.mirroredClicks` -> `StationAnchors`; `PanelLayout.SINGLE_LINE_WIDTH/ROW_LINES` -> `TextWidth`.
- Sandbox quirk: git commands chained with loops/variables, and bash scripts that edit many files, are refused ("too complex"); plain `mv`, `sed -i` on literal paths, python files from the scratchpad, and the Write tool work.
