---
name: bakery-core-balance
description: bakery-core slice numbers (2026-10-18) and which levers matter for early pacing; starting point for the next buildings
metadata:
  type: project
---

bakery-core (2026-10-18): villager_baker 50 / 0.5 cps, wheat_farm 500 / 4 cps (unlock baked 100), growth 1.15, click 1, crit 5 % × 10 (expected ×1.45). User targets: first Villager Baker ≤ 20 s, first Farm ≤ 3 min, no dead zone > 60 s in the first 15 min (casual + active). Casual result: 12 s, 1m58s, 25 s.

**Why:** these are the user's first real targets. Later tiers must slot in after wheat_farm with a similar payback ratio (~1.25× per tier; cost ×10, cps ×8).
**How to apply:** the Farm base cost is the most sensitive early lever (×2 → fails). Growth hardly matters in the first 15 min. With only 2 buildings, gaps pass 3 min after ~40 min, so a long-horizon dead-zone target needs more tiers or upgrades first. See docs/balance/lessons.md.
