# Balance research: idle / incremental pacing

My notes on how incremental games are balanced, in my own words, and how they apply to Cookie Empire. Sources are at the bottom. I took the techniques from them, not their tables, names or numbers.

Last updated: 2026-10-22 (building-roster)

---

## 1. The core tug-of-war
An idle economy is a race between **production**, which grows roughly linearly with what you own (times occasional multipliers), and **prices**, which grow exponentially with what you own. Early on, production wins and purchases come fast. Later, prices catch up and the time between purchases grows. Balancing means deciding *when* the price curve wins and what the player gets to do about it: a new tier, an upgrade, or eventually prestige. Exponential always beats linear in the end, so walls can't be avoided. You can only place them, and give the player a way over each one.

- Next price: `base × growth^owned`. Production: `owned × base_cps × multipliers`.
- Buying k at once is a geometric series, `base × growth^owned × (growth^k − 1)/(growth − 1)`. Since 2026-10-21 our contract rounds this sum up **once** (it used to sum separately rounded prices). That makes bulk cost O(1) and lets "Max" invert it: `k = floor(log(1 + pouch × (growth − 1) / next_price) / log(growth))`. Floating point makes that estimate off by one at exact boundaries, so the contract defines Max as "largest k whose bulk cost fits" and the log only as a starting point with a ±1 fix-up (never off by more than 1 in 200,000 random cases).

## 2. Cost growth per purchase (1.07–1.15)
- **Low growth (~1.07)** makes buying many copies of one generator cheap, so you get long "buy 25/50/100" runs. That only works with milestone multipliers that make piles of one building worth it.
- **High growth (~1.15)** makes each copy feel earned. With no milestone bonuses, it pushes the player toward the next tier sooner. It's forgiving about starting prices: if a base price is too cheap, the curve takes the pace back to normal after a few copies.
- Per generator, the time to the next copy grows by about `growth` per copy once production of that generator dominates, so 1.15 roughly doubles the wait every 5 copies (1.15^5 ≈ 2.0).
- **Measured here (2026-10-18):** in the 2-building slice, changing growth between 1.12 and 1.18 barely moves the first-15-minute metrics (dead zone 24–25 s casual). Early pacing is set by base prices and click income, and growth matters later. I kept 1.15.

## 3. Tier ratios and payback time
- **Payback time** = cost ÷ extra cookies/s. It's the main pacing lever: it says how many seconds of the new income it takes to earn the purchase back, and an optimal player buys the lowest payback first. Our simulator's buy strategy uses this.
- Successive tiers usually cost ~7–12× more and produce ~5–8× more, so the base payback gets a little *longer* each tier. A new tier first looks expensive, then becomes the best buy once the old tier's copies have grown in price.
- If a new tier's payback is far *better* than the old tier's, the old one is dead from then on (one dominant purchase). If it's far *worse*, nobody buys it until much later, which leaves a gap. Keep base paybacks within about 1.1–1.5× of each other so the two prices "interleave": the player switches back and forth, and that is the choice we want.
- **Applied:** Villager Baker 50 → 0.5/s (payback 100 s), Wheat Farm 500 → 4/s (payback 125 s): cost ×10, CPS ×8, payback ×1.25. The optimal buyer takes two Villager Bakers, then the first Farm, then alternates between them for the whole 15 minutes.

## 4. Click vs idle income
- At the start, clicks are all the income. The first building has to be reachable by clicking alone within a short attention span (our target is ~20 s). After that, production should take over gradually so idle players can progress and active players stay ahead.
- Expected click value with crits = `base × (1 + p × (m − 1))`. 5 % × 10 gives ×1.45, so crits are a real (+45 %) share of early click income, not just decoration. The simulator uses the expected value so it stays deterministic. Real players see the variance, which is fine at these scales (in 20 s at 3 clicks/s you expect 3 crits).
- **Profiles:** an idle player only needs to *not stall*. They're slower by design (here: first Farm at ~10 min instead of ~2 min) and then catch up, because once the buildings are running, clicks are a small share of the income.
- Later features: `click-cps-fraction` upgrades are how clicking stays relevant once CPS dwarfs base click power.

## 5. Upgrade timing (for later slices)
- Commonly land shortly after a building unlocks (first copy), then at milestones (e.g. 1/5/25/50… owned), each a clear multiplier (×2) so the jump is perceptible. People judge gains relative to what they already have, so +5 % feels like nothing.
- Price upgrades so their payback is about the same as the buildings on offer at that moment. They should compete with the buildings, not always win.

### Click upgrades and repeatables (2026-10-21)
- Flat `+CPC` tools are worth `clicks/s × crit factor` cookies/s per +1, so for an active player (8/s × 1.45 = 11.6/s per +1) a +1 click upgrade is worth ~23 Villager Bakers. In a building-light slice, an optimal active player therefore spends most early money on clicks. That is the genre norm (clicking is the engine before buildings take over); the buildings still win for idle players, who never buy click upgrades.
- A **repeatable** +1 CPC with growth per level is a steady "always something to buy" sink: it fills the gaps between big one-time tools. Its payback grows by the growth factor each level while its gain stays flat, so it fades by itself as the run goes on. A `max-level` keeps its list row from becoming a wall of absurd prices.
- Unlocking each one-time tool at about half its price makes the next row visible before it's affordable (§7).

### Count milestones (2026-10-22)
- A ×2 at fixed owned counts turns cost growth from a pure wall into a staircase: the copies just before a
  milestone are bad buys alone but great as a group. That gives the player a short goal ("3 more to 25") and a
  reason to keep buying an old tier, which is how lower tiers stay relevant without changing their base numbers
  (Pecorella Part I/II on multipliers at count thresholds).
- Milestones compound with growth: at 1.15, the 50th copy costs ~33× the 25th (the 25 copies together ~210×) for one
  more ×2, so later milestones arrive further apart and late tiers take over. Spacing them closer late (100, 150,
  200) keeps something to aim for.
- Measured: with milestones, the old tiers keep pace so well that the run is faster overall. Late tier prices
  (or the later buffs) have to absorb that. A buyer model has to look ahead to the next milestone, or the
  simulation undervalues it.

## 6. Dead zones and walls
- **Dead zone** (our metric): stretches where *nothing* is affordable. These are what feel like "stuck". A long wait for the *best* item while a cheaper one is affordable is a choice, not a dead zone.
- Gaps grow naturally with the exponential curve. Plan the next layer (a new tier, upgrades, golden cookies) to arrive before the gap goes over the target. With only 2 buildings, gaps reach ~5–6 min after an hour, and that's expected. The next slices need more tiers or upgrades before then.
- Engagement decays over a session: dense early rewards (seconds), then minutes, then longer check-ins. The 15-minute target window matches this.

## 7. Process
- Simulate. Don't tune by feel: small changes swing pacing from too slow to too fast. Change one lever at a time and write down what moved.
- A stall in a playtest can be a UI problem (the player doesn't see the next purchase), not a numbers problem. Unlock thresholds should make the next row visible *before* the player can afford it.

## Sources
- Anthony Pecorella, "The Math of Idle Games, Part I": https://www.gamedeveloper.com/design/the-math-of-idle-games-part-i (cost/production formulas, bulk-buy series, costs exponential vs production linear, multipliers, ranking purchases by income-to-cost)
- Anthony Pecorella, "The Math of Idle Games, Part II": https://www.gamedeveloper.com/disciplines/the-math-of-idle-games-part-ii (keeping lower tiers relevant, alternate growth models)
- "The Math of Idle Games, Part III": https://www.gamedeveloper.com/design/the-math-of-idle-games-part-iii (prestige cycles, for later)
- Pecorella, "Quest for Progress", GDC Europe 2016: https://www.slideshare.net/slideshow/quest-for-progress-gdc-europe-2016/65405507
- "Balancing tips: how we managed math on Idle Idol": https://gamedeveloper.com/design/balancing-tips-how-we-managed-math-on-idle-idol (one central model, tuning swings, UI problems that look like balance problems)
- Eric Guan, "Idle Game Design Principles": https://ericguan.substack.com/p/idle-game-design-principles (decaying engagement, relative perception of gains, exponential costs make starting prices forgiving)
