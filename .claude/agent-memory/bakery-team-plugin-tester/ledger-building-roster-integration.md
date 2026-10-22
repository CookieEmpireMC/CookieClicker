---
name: ledger-building-roster-integration
description: building-roster integration review at d158726 (+ Gandalf tests); READY; consistency tests added, open LOWs, in-game guide pointer
metadata:
  type: project
---
Verified 2026-10-22 on feature/building-roster at d158726. check-quiet 682 green (0 skipped), validate-assets 93 files, sound-check 28, balance-check 19/19, tools-test 146 (1 skip). READY. Report: docs/reviews/2026-10-22-building-roster.md.

- Added tests: GoldenValuesTest.milestoneListsAndPerBuildingCpsMatch (golden keys `building-cps`, `building-milestones` were not read before); ShippedContentConsistencyTest strict icon/name subset + visibleBuildingsHave... (slots/config/models stages/sounds/effects); DecorKeepOutTest footprint squares + shipped slots footprints vs layout zones + shipped decor; BuildingSlotsTest over-cap cell; PanelUpdaterTest row-fit now over all 11 shipped ids.
- Facts: config key is `plot.buildings.*` (not station.buildings); basket sits on the pedestal spot (inside the front keep-out zone); footprint cells = floor(c+0.5-h)..ceil(c+0.5+h)-1.
- Open LOWs: see report follow-ups (stage thresholds duplicate milestone numbers; unloaded-chunk spawn no retry; dead nextBuildingCps; 0.81 vs 0.82 footprint; main.py line 188).
- Re-review range: `git diff d158726..HEAD`.
