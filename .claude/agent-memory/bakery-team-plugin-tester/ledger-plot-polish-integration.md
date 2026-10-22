---
name: ledger-plot-polish-integration
description: plot-polish integration review at 2d3805f (+ff95d20): geometry facts (layout cells, basket vs panels/belt, cookie box), checks run, open LOWs
metadata:
  type: project
---

Verified 2026-10-22 on feature/plot-polish (head 2d3805f, my commit ff95d20). check-quiet 655 tests green, validate-assets 51 files, sound-check 24, balance-check 6/6. Verdict READY. Report: docs/reviews/2026-10-22-plot-polish.md.

Geometry facts (shipped factory.yml layout: pedestal 18,6, shop 20,6, clicks 16,6, viewpoint 15,2, belt 18,11 -> 18,7):
- Cookie box = (scale 1.4 + margin 0.1) 1.5 wide, 1.7 high, centred on the pedestal axis; panel text columns 1.8 wide start 1.1 from the axis. The box shadows only the pedestal-side edge of a panel at shallow angles.
- Viewpoint to shop panel is 6.4 > click-distance 6 (LOW follow-up); viewpoint to clicks panel 4.1.
- Golden basket (1.375 wide) spans 0.19 into the belt's last cell; belt cookies end at z 7.0, y ~1.06 (above basket edge/posts 0.69).
- Keep-out front zone x12..23 z1..8, belt zone x17..19 z6..12 cover basket, panels, shelf.
- LoopPacer lives in SoundService (survives load); keyed (player, event) only, so a plugin disable/enable can double a loop for <= one period.

Test recipe: view/PanelCookieOverlapTest (frame yaw 0, panels at world x=+-2 on z=0, eye 1.62, BoundingBox.rayTrace vs PanelLayout.pick) - no server needed.
Architecture note: config.StationVisualSettings imports view.PanelLayout.ROW_LINES and view.TextWidth (LOW, move ROW_LINES to TextWidth).
Re-review: `git diff ff95d20..HEAD -- src/main` and re-run the 4 mise checks.
