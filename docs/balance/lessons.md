# Balance lessons

Dated notes from each balance job: which lever moved which metric and by how much, and what didn't work. Stable insights get promoted to `research.md`.

## 2026-10-18: bakery-core (2 buildings, click + crit)
- Baseline: villager_baker 50 / 0.5 cps, wheat_farm 500 / 4 cps (unlock baked 100), growth 1.15, click 1, crit 5 % × 10 (expected ×1.45). Every target passed on the first run (casual: Villager Baker 12 s, Farm 1m58s, dead zone 25 s).
- One-lever sweeps (casual / active):
  - Villager Baker cost 15 → 4 s / 2 s first buy, but the early dead zone **doubles** to 56 s casual (just under 60). A cheap first building leaves a long wait before anything else is affordable. 80 → 19 s (too close to 20). 50 is the sweet spot.
  - Wheat Farm cost is the most sensitive lever: 1000 → Farm at 3m42s casual and a 1m39s dead zone (both FAIL).
  - Farm CPS 3 → dead zone 52 s casual; 6 → 12 s (Farm becomes dominant: payback 83 s vs 100 s).
  - Growth 1.12 / 1.18: early metrics move by ≤ 4 s. Growth doesn't matter in the first 15 min of a 2-tier slice.
  - No crit: first buy 17 s, Farm 2m39s casual. Crits give ~30 % faster onboarding.
- An optimal buyer alternates the two buildings when base paybacks are within ~1.25× (100 s vs 125 s).
- Idle (bootstrap clicks only) waits ~8 min for the first Farm (saving for it at 1 cps). Slow, but no stall. The longest idle dead zone in the first 15 min is 2m59s, which isn't targeted.
- After 15 min, 2 buildings alone stretch gaps past 3 min (~40 min in) and up to ~20 min by 4 h. The old `no-dead-zones` (180 s over 4 h) and `all-tiers-in-2h` targets don't fit the slice. Expect to restore a long-horizon dead-zone target when more tiers/upgrades land.
- Tooling: `tools/balance/test_*.py` is owned by plugin-tester. Request simulator tests there instead of writing them.

## 2026-10-21: upgrade-holograms (click upgrades, repeatable `bakers_strength`, bulk/Max)
- Added the GDD §4.4 click tools at the GDD prices (100 … 500B), each unlocking at about half its price (the first two after the first Villager Baker), plus `bakers_strength` (200, ×1.25 per level, max 50, +1 CPC per level). Buildings unchanged.
- First run passed 6/6. What moved vs 2026-10-18: casual first Farm 1m58s → **2m36s** (the optimal buyer now spends on Wooden Spatula/Baker's Strength first); active Farm 49 s → 2m00s (an active player gets more from +CPC than from a Farm, so it buys the tools first); first Villager Baker unchanged (12 s / 5 s) because the click upgrades only unlock after it. Early dead zones dropped (casual 25 s → 12 s, active 18 s → 5 s): there's almost always a click upgrade to buy.
- One-lever sweeps on `bakers_strength` (casual first Farm): cost 100 → 2m36s, 200 → 2m36s, 400 → 2m33s. Level 1 lands at 55 s (200) vs 1m50s (400) casual. Its cost hardly touches the Farm target; Wooden Spatula at 100 does (it's the best buy right after the first building). Spatula 100 → 150: Farm 2m46s (less margin). Kept 200 / 1.25 / max 50.
- Gaps plot: the big single spikes (casual ~1h35m around 2h55m, active ~40 min) are the buyer *saving* for Diamond Fingers / Netherite Hands while cheaper items are affordable; not dead zones (longest dead zone over 4 h is 86 s casual, 116 s active). With only 2 buildings the GDD's ×10 price steps between tools feel steep late; more building tiers will fill this.
- Handy Fingers are never bought in 4 h: 1 % of ~250 CPS is ~2.5 per click. They're for the later building roster, as the GDD intends.
- Bulk cost now rounds once; the Max closed form matched brute force on 200,000 random cases with at most a ±1 correction step.
