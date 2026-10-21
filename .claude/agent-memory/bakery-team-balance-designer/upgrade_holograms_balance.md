---
name: upgrade-holograms-balance
description: Click upgrades + repeatable bakers_strength numbers (2026-10-21), bulk-cost/Max contract decisions, and which levers moved what
metadata:
  type: project
---

upgrade-holograms (2026-10-21): GDD §4.4 tools at GDD prices, each unlocks at ~half price (spatula + bakers_strength after 1 villager_baker); bakers_strength 200, cost-growth 1.25, max-level 50, click-add 1/level. Handy Fingers values 0.01/0.02/0.02 (totals 1/3/5 %).
Contract: bulk cost = ceil(base × g^n × (g^k−1)/(g−1)) rounded ONCE (was a per-item sum); Max = largest k ≤ cap with bulk_cost ≤ pouch, log estimate + ±1 fix-up (verified vs brute force on 200k cases, max 1 step). MAX_BULK 10000. Effects: additive × level, multipliers ^ level. State.upgrades is a dict id→level; State() still accepts a set (the tester's older tests).

**Why:** the brief asked for a closed-form Max; per-item rounding can't be inverted exactly.
**How to apply:** Wooden Spatula price is the lever for the casual first-Farm target (2m36s; 150 → 2m46s); bakers_strength cost barely moves it. Active players buy click tools before the Farm (49 s → 2m00s). Related: [[bakery-core-balance]], [[balance-tooling-facts]].
