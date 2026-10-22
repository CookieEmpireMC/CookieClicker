# Economy model (contract)

The single definition of the CookieClicker economy maths. The balance simulator (`tools/balance/`) and the plugin's Java economy code must both implement **exactly** this. The tester checks the Java code against `docs/balance/golden-values.json`, which the simulator generates from these formulas.

Owner: **balance-designer**. Changing a formula or config field is a contract change: propose it under `CONTRACT CHANGES`, and the orchestrator schedules the matching coder and tester work.

Last updated: 2026-10-22 (building-roster: building milestones)

---

## State per player

| Field | Meaning |
|---|---|
| `pouch` | the **Cookie Pouch**: spendable cookies (double) |
| `baked` | cookies baked all time (double, never decreases; used for unlocks and stats) |
| `owned[b]` | number of building `b` owned (int) |
| `upgrades` | map upgrade ID → **level** (int). One-time upgrades have level 1 once bought; missing = 0. An older save that stores a *set* of IDs loads as level 1 for each. |

Clicking and production add to both `pouch` and `baked`. Purchases subtract from `pouch` only.

## Buildings (`src/main/resources/buildings.yml`)

```yaml
cost-growth: 1.15            # cost multiplier per building already owned
milestones: [10, 25, 50, 100, 150, 200]   # optional; owned counts at which a building's production doubles
buildings:                   # listed in cost / unlock order: the shop's default view relies on the file order
  whisk_sprite:              # ID; display names live in messages.yml, not here
    base-cost: 15
    base-cps: 0.1
    unlock: { baked: 0 }     # visible & purchasable once baked >= this
    milestones: [10, 25]     # optional; replaces the top-level list for this building only
```

- **Cost of the next building:** `cost(b) = bulk_cost(base_cost[b], cost_growth, owned[b], 1) = ceil(base_cost[b] × cost_growth ^ owned[b])`
- **Cost of buying `k` at once:** `bulk_cost(base_cost[b], cost_growth, owned[b], k)`, and the most you can buy is `max_affordable(…)`; both are defined under "Bulk cost and Max" below.
- `base-cost` must be `> 0`.
- A building is **unlocked** when `baked >= unlock.baked`. Unlocks never re-lock.

### Milestones

`list(b)` = building `b`'s own `milestones:` if it has that key, otherwise the top-level `milestones:`.
`owned` = `owned[b]`:

```
milestones_reached(b, owned) = number of entries c in list(b) with owned >= c    # an int, 0..len(list(b))
milestone_factor(b, owned)   = 2 ^ milestones_reached(b, owned)                  # pow(2, reached), exact in doubles
```

- With the shipped `[10, 25, 50, 100, 150, 200]`: 9 owned → ×1, 10 → ×2, 25 → ×4, 50 → ×8,
  100 → ×16, 150 → ×32, 200 (and more) → ×64. So **production of building b = base_cps × owned × 2^(milestones
  reached)** (before upgrade multipliers; full formula under "Derived values").
- The factor applies to that building's **own** production only, and is recomputed from `owned` (there's no
  stored state: buying the 10th copy doubles all 10 at once).
- Validation (top level and per building): `milestones` must be a list of integers `>= 1`, strictly
  increasing. Top level missing or empty = no milestones (factor 1). A per-building `milestones: []` means that
  building has none; a per-building key that is absent falls back to the top-level list.
- The ×2 per milestone is fixed by the GDD (§4.3), not a config value.
- Displays that show a building's CPS (`+<cps>/s` in the panel, the HUD total) use the value with the factor. The
  "+CPS of the next copy" is `building_cps` with `owned + 1` minus `building_cps` now (it includes the doubling when
  the next copy reaches a milestone).

## Upgrades (`src/main/resources/upgrades.yml`)

```yaml
click:
  base-power: 1              # cookies per click before upgrades
  crit-chance: 0.05          # probability (0..1) that a click is a crit; default 0
  crit-multiplier: 10        # a crit multiplies the whole click value (>= 1); default 1
upgrades:
  buttered_mitts:            # one-time (no cost-growth)
    cost: 100
    effect: { type: click-multiplier, value: 2 }
    unlock: { building: whisk_sprite, owned: 1 }   # or { baked: 1000 }
  strong_arms:               # repeatable (has cost-growth)
    cost: 200                # cost of level 1
    cost-growth: 1.25        # cost multiplier per level already owned (>= 1)
    max-level: 50            # optional, integer >= 1; absent = unlimited
    effect: { type: click-add, value: 1 }          # the effect of ONE level
    unlock: { building: whisk_sprite, owned: 1 }
```

- `cost` must be `> 0`.
- **One-time** upgrade (no `cost-growth`): costs exactly `cost`, can be bought once (level 0 → 1). Bulk modes don't apply: it is always bought once.
- **Repeatable** upgrade (`cost-growth` set): the next level costs `ceil(cost × cost_growth ^ level)`; `k` levels at once cost `bulk_cost(cost, cost_growth, level, k)`. It can be bought while `level < max-level` (no cap when `max-level` is absent; implementations may store levels as int, so the practical cap is 2,147,483,647). `max-level` without `cost-growth` is a config error.

Effect types:

| `type` | Fields | Effect |
|---|---|---|
| `click-multiplier` | `value` | multiplies click power |
| `click-add` | `value` | adds to base click power (before multipliers) |
| `click-cps-fraction` | `value` | each click also gives `value × total CPS` |
| `building-multiplier` | `building`, `value` | multiplies one building's CPS |
| `global-multiplier` | `value` | multiplies total CPS |

`upgrades: {}` (an empty map) is valid: a slice with only click values and no upgrades.

An upgrade is **available** when its `unlock` condition holds (`owned[building] >= owned`, or `baked >= baked`) and its level is below its cap (one-time: 1; repeatable: `max-level`, or unlimited).

**Effect at level `L`** (`L` = the upgrade's level, 0 = not bought): additive types scale linearly, multiplier types compound:

| `type` | effect at level `L` |
|---|---|
| `click-add`, `click-cps-fraction` | `value × L` |
| `click-multiplier`, `building-multiplier`, `global-multiplier` | `value ^ L` |

For one-time upgrades `L` is 0 or 1, so this is the same as before.

## Bulk cost and Max (buildings and repeatable upgrades)

One formula for both. `base` = `base-cost` (building) or `cost` (upgrade), `g` = the matching `cost-growth`, `n` = owned (building) or level (upgrade), `k` = how many to buy. All doubles; evaluate in exactly this order:

```
first     = base × g^n                                  # pow(g, n)
ratio     = (g^k − 1) / (g − 1)        if g > 1         # pow(g, k)
          = k                          if g == 1
bulk_cost(base, g, n, k) = ceil(first × ratio)          # k >= 1; bulk_cost(…, 0) = 0
```

- The geometric sum is **rounded up once** for the whole batch. For `k = 1`, `ratio` is exactly `1.0`, so `bulk_cost(…, 1)` is the single cost `ceil(base × g^n)`. (Buying ×1 ten times can cost up to 9 cookies more than one ×10 buy. That is intended and too small to notice.)
- A ×N buy succeeds only if `pouch >= bulk_cost(…, N)` (and, for a repeatable upgrade, `n + N <= max-level`). Otherwise nothing is bought. There are no partial buys.

**Max affordable** `max_affordable(base, g, n, cap, pouch)` = the **largest `k` in `[0, cap]` with `bulk_cost(base, g, n, k) <= pouch`**, where `bulk_cost` is exactly the formula above. That sentence is the definition; the steps below compute it in constant time:

```
cap = min(cap, MAX_BULK)                       # buildings: cap = MAX_BULK; upgrades: max-level − level (or MAX_BULK if unlimited)
if cap <= 0 or pouch < bulk_cost(base, g, n, 1): return 0
first    = base × g^n
estimate = log(1 + pouch × (g − 1) / first) / log(g)      if g > 1
         = pouch / first                                  if g == 1
k = clamp(floor(estimate), 1, cap)             # estimate not finite → k = cap
while k > 1   and bulk_cost(base, g, n, k)     >  pouch: k −= 1   # boundary rule: never claim an
while k < cap and bulk_cost(base, g, n, k + 1) <= pouch: k += 1   # unaffordable k, never miss one
return k
```

- `MAX_BULK = 10000`, the hard upper bound for any single bulk or Max buy.
- The log formula is only an **estimate**: floating point (and the `ceil`) can make it one too high or too low right at a boundary, e.g. when the pouch is exactly `bulk_cost(k)`. The two correction loops fix that; on 200,000 random cases (2026-10-21) they never moved `k` by more than 1, and the result always matched a brute-force count. They are a bounded fix-up, not a search: do not loop from 1.
- One-time upgrade: Max = 1 if not bought and `pouch >= cost`, else 0.
- The Max row shows `k` and `bulk_cost(…, k)`; when `k = 0` it shows the single cost (unaffordable).

## Derived values

```
building_cps(b) = owned[b] × base_cps[b] × milestone_factor(b, owned[b]) × Π(effect of building-multiplier upgrades for b)
total_cps       = Σ building_cps(b) × Π(effect of global-multiplier upgrades)
click_power     = (base_power + Σ effect of click-add) × Π(effect of click-multiplier)
                  + Σ(effect of click-cps-fraction) × total_cps
```

"effect" is the effect at the upgrade's current level (table above); upgrades at level 0 contribute nothing (Σ adds 0, Π multiplies 1).
Evaluate `building_cps` left to right in the order written (`owned × base_cps`, then the milestone factor, then the
upgrade product), and add up `total_cps` starting from 0.0 in `buildings.yml` order, so doubles match the
golden values exactly.


- Production is applied once per second: `pouch += total_cps; baked += total_cps`.
- A click: roll once per click, `crit = random() < crit_chance`, then
  `click_value = click_power × (crit ? crit_multiplier : 1)`; `pouch += click_value; baked += click_value`.
  A crit multiplies the **whole** click value, including the `click-cps-fraction` part.
- Expected click value (used by the simulator, which is deterministic; also for any "per click" stat):
  `expected_click = click_power × (1 + crit_chance × (crit_multiplier − 1))`.
- Validation: `crit-chance` must be in `[0, 1]`, `crit-multiplier` must be `>= 1`; both are optional
  (missing = no crits), so older configs keep working.
- All values are doubles; only costs are rounded (up). Display formatting is separate (messages.yml).

## Later phases (reserved, not active yet)
Golden cookies (temporary multipliers), prestige (permanent global multiplier) and offline earnings will add fields here first, then to the configs.

## Golden values (`docs/balance/golden-values.json`)

Generated by `mise run balance`; the Java economy must reproduce them exactly.

| Key | Meaning |
|---|---|
| `cost-growth` | the configured building growth |
| `milestones` | the top-level milestone counts |
| `building-milestones.<b>` | the list that applies to building `b` (its own, or the top-level one) |
| `building-cps.<b>[]` | `{owned, milestones-reached, factor, cps}`: one building's production (no upgrades) at owned counts on both sides of every milestone (0, 1, 9, 10, 24, 25, … 199, 200, 250) |
| `max-bulk` | `MAX_BULK` |
| `click` | `base-power`, `crit-chance`, `crit-multiplier`, `expected-factor` (= `1 + p × (m − 1)`), and `base-state` click values for an empty state |
| `costs.<b>.single[n]` | cost of the next building when `n` are owned |
| `costs.<b>.buy-10-from.<n>` | `bulk_cost` of 10 when `n` are owned |
| `costs.<b>.bulk[]` | `{owned, count, cost}`: `bulk_cost` for several owned/count pairs |
| `upgrades.<id>` | `cost`, `repeatable`, `cost-growth`, `max-level` (null = none), `type`, `value`; repeatable ones also `single[L]` (cost of the next level at level `L`) and `bulk[]` (`{level, count, cost}`) |
| `max-affordable[]` | `{kind: building\|upgrade, id, owned, pouch, max, case}`: `max_affordable` at `owned` (= level for upgrades) with that pouch. Cases cover an empty pouch, one cookie short of 1, exactly / one short of / just over k for several k, a huge pouch, the `max-level` cap and "already at max-level" |
| `states[]` | snapshots from the simulation: `owned`, `upgrades` (map id → level), `total-cps`, `click-power` (non-crit), `normal-click`, `crit-click`, `expected-click` |
