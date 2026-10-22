# Ledger: building-roster / plugin-coder-economy (2026-10-22)

Verified at Gandalf test commit `6ce0d9a` on `building-roster/plugin-coder-economy` (coder 22c64df..4a42bcb).
`mise run check-quiet` 666 green, validate-assets + balance-check (6/6) green. READY, no HIGH/MEDIUM, no PERF-HIGH.

Re-review range when it changes: `git diff 6ce0d9a..HEAD -- src/main/java/dev/casperrs/cookieClicker/economy src/main/java/dev/casperrs/cookieClicker/view`.

## Facts (stable, verified in code)
- Milestones: `BuildingDef.production(owned) = owned × baseCps × 2^(milestones ≤ owned)`; top-level `milestones:` in buildings.yml, per-building override (empty list = off). Parser sorts + dedups, rejects non-list / entries < 1 with message containing "milestones".
- `Economy.buildingCpsGain(pouch, id, n) = (production(owned+n) − production(owned)) × building-mult × global-mult` (not linear once a milestone is crossed).
- `Economy.nextBuildingCps` is no longer used by main code (only tests): dead-code candidate.
- Shipped `buildings.yml` has no milestones yet: GoldenValuesTest unaffected on this branch; milestone golden values come with the balance-designer branch (integration must add them).
- Panel row pick geometry at shipped line-width 150 (rowWidthPx 146): icon centre ≈ −0.9375, icon left edge ≈ −1.0875, row box spans [−1.0875, +0.9] → the "beside the icon" point is ≤ −1.12 (test uses −1.2).
- Paging: maxTop = buildings − rows; shipped max-rows = 8 (11 buildings → paging on). Default window = next locked at bottom (file order = cost order assumed).
- ▲/▼ toggle line: `panel.page.line` = "<up>  <toggle>  <down>"; disabled arrows stay tagged (click = no-op via clickPage → scroll false).
- Scroll state: `PanelPages` (HashMap, main thread) keyed by player UUID; forgotten in `StationViews.onQuit` only; dropped when the default window moves.
- ShippedContentConsistencyTest icon check now: icon keys must have a message name (passes before and after the balance merge).

## Open LOWs / follow-ups (no fix round)
- Scrolling changes every visible slot's id → `update()` id-changed branch sets `pulseStart` on every row with an item → all rows pulse on each scroll. Cosmetic; pulse should be for purchase/unlock only.
- `Economy.nextBuildingCps` unused in main (dead code).
- 5-arg `PanelUpdater` constructor used only by tests (throwaway PanelPages).
- Default window relies on buildings.yml being in ascending cost order; document in buildings.yml/model.md.
- `toggleKey` string built on every refresh (1/s per panel): PERF-LOW.
- In-game: ▲/▼ glyphs present in the default font; row text now not bold.

## Test recipes used
- Paged toggle pick: `ToggleBox(80, labels, pages)` with the existing `aim()` helper; px→right = −0.3375 (10 px), 0.3375 (70 px) at toggle scale 0.45.
- Shipped config/messages in view tests: `StationVisualSettings.from(YamlConfiguration.loadConfiguration(config.yml stream), …).panel().lineWidth()`; `Messages` + `m.load(RealMessages.yaml())`; row fit via `TextWidth.lineWidths(TextWidth.runs(row), lineWidth)` size == `PanelLayout.ROW_LINES`.
- Worktree `mise run test` works without extra trust steps (checked 2026-10-22).
