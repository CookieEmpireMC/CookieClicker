---
name: building-roster-balance
description: 11-building roster + milestones (2026-10-22): final numbers, unlocks, which levers moved what, and the open GDD rebirth-pace gap
metadata:
  type: project
---

building-roster (2026-10-22, report docs/balance/report-2026-10-22.md): GDD §4.2 roster with changes: Apprentice 75 (GDD 100), Wheat Field cps 0.15 (GDD 0.1), Oven 160K/240, Belt 2M/1300, Beehive 30M/6000, Cocoa 400M/40000; tiers 9–11 at GDD values. Unlocks (expansion stand-ins): 10K / 750K / 25M / 1B / 100B baked. Milestones: top-level [10,25,50,100,150,200], optional per-building `milestones:` override (matches the coder parser), factor fixed ×2 (no multiplier key). Buildings must stay in cost/unlock order in the file (shop default view). Handy Fingers I 25M (unlock 10M).
Result: 19/19 targets; 1B baked at 3h49m casual / 3h25m active; cocoa only for active in 4 h.

**Why:** GDD numbers raced to 1B at ~3h09m before golden cookies/recipes/stars exist; GDD wants Rebirth at 6–10 h *with* buffs.
**How to apply:** when golden cookies, recipes or stars land, balance them together with the late tiers (they will speed up 1B). Tier first-buy times follow unlock thresholds. The tester's `test_balance.py` CLI tests hardcode "6/6 targets pass" and `base-cost: 50` (broken by this roster; requested a fix). Related: [[bakery-core-balance]], [[upgrade-holograms-balance]], [[balance-tooling-facts]].
