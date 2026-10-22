---
name: ledger-panel-align
description: panel-align (1.2.1 text column) review: ff9f3e4 NOT READY (cookie-box overlap), re-review 0190c7f READY with tests 0febb00/e8b5495; layout numbers, test recipes, px-boundary lesson
metadata:
  type: project
---

Branch panel-align/plugin-coder, reviewed 2026-10-22 at ff9f3e4 (coder) / 6531496 (coder memory). Verdict NOT READY:
one MEDIUM (cookie-box overlap in the no-plot fallback). Tests were NOT committed (red suite; rule: no commit on failing tests).
Uncommitted test edits sit in the worktree `agent-a82893e9d56ade9a8` (PanelLayoutTest, PanelUpdaterTest, PanelCookieOverlapTest).

## Status update (2026-10-22)
Coder fix 0190c7f moved fallback offsets to 2.15 / -2.15 (box clears cookie by 0.1075). Gandalf re-review READY: StationVisualSettingsTest pins
updated; commits 0febb00 (geometry pins) and e8b5495 (column + overlap sampling); check-quiet 683 green.
The "NOT READY" below is the history at ff9f3e4.

## Verified numbers (defaults = shipped config.yml, stable for this layout)
- TextWidth: PIXELS_PER_BLOCK 40, LINE_HEIGHT_PX 10, SPACE_PX 4; blocks(px, s) = px*s/40.
- rowWidthPx = lineWidth(150) - 4 = 146; columnWidth = 1.825; textLeft = -0.9125.
- Row shift = textLeft - textOffset.right(-0.3) = -0.6125; icon centre -1.1125, icon left -1.2625 (half 0.15).
- headerHeight 0.18 (scale 0.72); toggleHeight 0.125 (scale 0.5); toggleBottom -0.345; rowHeight 0.25; rowBottom(i) = -0.875 - 0.42 i.
- Toggle click band: toggleBottom - max(0.03, 0.28/2) = -0.485 .. toggleBottom + 0.125 + max(0.03, 0.22/2) = -0.11.
- Column px -> block probe for a toggle of width 80 px at scale 0.5: r = (px - 40)/80 (10 px -> -0.375, 70 px -> 0.375).
- Box: local right [-1.2925, 0.9425] (centre -0.175, width 2.235); box bottom = rowBottom(rows-1) - 0.03.
- Header/toggle padded target: round(1.825*40/scale) px; paged toggle "[×1]   ×5   ×10   Max" plain = 148 px padded
  (exactly half a space over the 146 target: not a bug, within tolerance).

## Finding (MEDIUM, goes back to coder)
- Fallback (no plot layout, config defaults; `station.panel.offset` 1.9 on the cookie's right axis): buildings box left edge
  0.6075 and top row icons (0.6375..) sit inside the cookie click box (|x| <= 0.75, cookie scale 1.4 + margin) by 0.14 blocks.
  Clicks on those icons can resolve to the cookie. Clicks panel (-1.9) is clear (gap 0.21).
  Fix to suggest: buildings offset right >= 2.15 (gap ~0.11) in the fallback, or another layout that clears it. Shipped plot
  layout uses the shop/clicks anchors (offset x ignored, "upOnly") so it is unaffected.
- PanelCookieOverlapTest (plot geometry, world space) passes even with full box sampling: it uses yaw 0 mirrored axes, so it
  does not model the fallback overlap. Keep the widened sampling (it is correct for its own model).

## Test recipes (reuse)
- Clearance measured to the edge facing the cookie: `edge = panel.right + boxBottom.right - signum(panel.right)*boxWidth/2`.
- Shipped config in tests: `StationVisualSettings.from(YamlConfiguration.loadConfiguration(config.yml stream), ...)`
  (PanelUpdaterTest.shippedSettings()); shipped messages via `new Messages(logger); m.load(RealMessages.yaml())`.
- Column check per element: header/toggle centred at 0 (edges -w/2, w/2); rows left-edge at textLeft with width from
  `TextPadding.right(buildingRow(...), pad, rowWidthPx, lineWidth)`; tolerance half a space = 2 px at that element's scale.
- Compare widths in integer px (`TextWidth.width(c, 1000) <= target + SPACE_PX/2`): a block-float compare at exactly
  half a space failed by rounding (1.85 vs 1.825+0.025).
- Shipped rows: all building rows fit within half a space of the 146 px column (2 count variants x both styles).
  TextPadding.right() pads with ceil, so a row can overshoot by up to 3 px in principle (LOW follow-up, not hit by shipped text).

## Open LOWs
- TextPadding.right ceil overshoot (3 px possible) vs centred round (2 px max).
- Clicks upgrade rows are not in the column test (only building rows are).
- In-game check still open (text vertical anchor; icon/cookie clearance in the plot layout).

Re-review range: `git diff ff9f3e4..HEAD -- src/main/java/dev/casperrs/cookieClicker/view src/main/java/dev/casperrs/cookieClicker/config/StationVisualSettings.java src/main/resources/config.yml src/main/resources/messages.yml`.
