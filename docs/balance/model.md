# Economy model (contract)

The single definition of the CookieClicker economy maths. The balance simulator (`tools/balance/`) and the plugin's Java economy code must both implement **exactly** this. The tester checks the Java code against `docs/balance/golden-values.json`, which the simulator generates from these formulas.

Owner: **balance-designer**. Changing a formula or config field is a contract change: propose it under `CONTRACT CHANGES`, and the orchestrator schedules the matching coder and tester work.

Last updated: 2026-10-18

---

## State per player

| Field | Meaning |
|---|---|
| `pouch` | the **Cookie Pouch**: spendable cookies (double) |
| `baked` | cookies baked all time (double, never decreases; used for unlocks and stats) |
| `owned[b]` | number of building `b` owned (int) |
| `upgrades` | set of purchased upgrade IDs |

Clicking and production add to both `pouch` and `baked`. Purchases subtract from `pouch` only.

## Buildings (`src/main/resources/buildings.yml`)

```yaml
cost-growth: 1.15            # cost multiplier per building already owned
buildings:
  whisk_sprite:              # ID; display names live in messages.yml, not here
    base-cost: 15
    base-cps: 0.1
    unlock: { baked: 0 }     # visible & purchasable once baked >= this
```

- **Cost of the next building:** `cost(b) = ceil(base_cost[b] × cost_growth ^ owned[b])`
- **Cost of buying `k` at once:** the sum of the next `k` single costs (each rounded up separately).
- A building is **unlocked** when `baked >= unlock.baked`. Unlocks never re-lock.

## Upgrades (`src/main/resources/upgrades.yml`)

```yaml
click:
  base-power: 1              # cookies per click before upgrades
upgrades:
  buttered_mitts:
    cost: 100
    effect: { type: click-multiplier, value: 2 }
    unlock: { building: whisk_sprite, owned: 1 }   # or { baked: 1000 }
```

Effect types:

| `type` | Fields | Effect |
|---|---|---|
| `click-multiplier` | `value` | multiplies click power |
| `click-add` | `value` | adds to base click power (before multipliers) |
| `click-cps-fraction` | `value` | each click also gives `value × total CPS` |
| `building-multiplier` | `building`, `value` | multiplies one building's CPS |
| `global-multiplier` | `value` | multiplies total CPS |

An upgrade is **unlocked** when its `unlock` condition holds (`owned[building] >= owned`, or `baked >= baked`); it can be bought once.

## Derived values

```
building_cps(b) = owned[b] × base_cps[b] × Π(value of bought building-multiplier upgrades for b)
total_cps       = Σ building_cps(b) × Π(value of bought global-multiplier upgrades)
click_power     = (base_power + Σ click-add) × Π(click-multiplier) + Σ(click-cps-fraction) × total_cps
```

- Production is applied once per second: `pouch += total_cps; baked += total_cps`.
- A click: `pouch += click_power; baked += click_power`.
- All values are doubles; only costs are rounded (up). Display formatting is separate (messages.yml).

## Later phases (reserved, not active yet)
Golden cookies (temporary multipliers), prestige (permanent global multiplier) and offline earnings will add fields here first, then to the configs.
