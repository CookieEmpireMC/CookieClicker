# Balance targets

What "good pacing" means for CookieClicker. **Edit the YAML block below**; `mise run balance` reports against it and `mise run balance-check` (also in CI) fails if any target is violated. Prose outside the block is ignored by the tools.

## Player profiles
- **idle:** never clicks after buying the first building (clicks at the bootstrap rate until then, otherwise nothing could ever start).
- **casual:** 3 clicks/s.
- **active:** 8 clicks/s.

All profiles buy with the same "optimal-ish" strategy: among unlocked buildings and upgrades, pick the one with the lowest cost per extra cookie/s (clicks count as cookies/s at the profile's click rate), wait until it's affordable, buy it, repeat. Recipes count once their ingredients are in stock (scored by cookie cost per extra cookie/s); the Recipe Book adds no CPS itself, so it is bought as soon as it's affordable.

## Metrics
| Metric | Meaning |
|---|---|
| `first-purchase` | seconds until the first building is bought |
| `all-buildings` | seconds until at least one of every building is owned |
| `longest-dead-zone` | longest stretch (s) where **nothing** unlocked is affordable, after the first purchase; optional `window-minutes: N` only counts the first N minutes (a zone crossing the edge is cut off there) |
| `time-to` | seconds until `count` of `building` are owned (needs `building`, `count`) |
| `upgrade` | seconds until upgrade `upgrade` is bought (needs `upgrade`) |
| `baked` | seconds until `cookies` have been baked in total (needs `cookies`) |
| `recipes` | seconds until `count` recipes are bought (default: all of them); with `since: <upgrade>`, counted from when that upgrade was bought |
| `recipe-ingredient-wait` | longest time any recipe waits on its ingredients alone: from when it is shown **and** every ingredient it needs has a producer running, until the stock covers it (an unfinished wait counts to the end of the horizon) |

Every target has `max-seconds` (reached no later than this), `min-seconds` (not before this, so content isn't
blown through), or both. "Never reached within the horizon" fails a `max-seconds` target and passes a target
that only has `min-seconds`.

## Targets

```yaml
horizon-minutes: 240          # how long each profile is simulated
profiles:
  idle:   { clicks-per-second: 0, bootstrap-clicks-per-second: 3 }
  casual: { clicks-per-second: 3 }
  active: { clicks-per-second: 8 }
targets:
  - id: first-villager-baker
    description: First Villager Baker within ~20 s of clicking
    profiles: [casual, active]
    metric: time-to
    building: villager_baker
    count: 1
    max-seconds: 20
  - id: first-wheat-farm
    description: First Wheat Farm within ~3 minutes
    profiles: [casual, active]
    metric: time-to
    building: wheat_farm
    count: 1
    max-seconds: 180
  - id: no-dead-zones-early
    description: Never more than 60 s with nothing affordable in the first 15 minutes
    profiles: [casual, active]
    metric: longest-dead-zone
    window-minutes: 15
    max-seconds: 60
  # building-roster (2026-10-22): 11 buildings + milestones. Unlocks stand in for the plot expansions
  # (GDD §5); the windows follow the GDD §6.1 pacing guideline (Expansion 1 in 5–20 min, Expansion 2 in
  # 20 min – 1.5 h, Expansion 3 in 1.5–6 h, Rebirth threshold 1B in 6–10 h).
  - id: first-cow-barn
    description: Cow Barn (Expansion 1 tier) within 20 minutes
    profiles: [casual, active]
    metric: time-to
    building: cow_barn
    count: 1
    max-seconds: 1200
  - id: first-cow-barn-idle
    description: Idle players reach the Cow Barn within 30 minutes
    profiles: [idle]
    metric: time-to
    building: cow_barn
    count: 1
    max-seconds: 1800
  - id: first-industrial-oven
    description: Industrial Oven (Expansion 2 tier) between 20 minutes and 1.5 hours
    profiles: [casual]
    metric: time-to
    building: industrial_oven
    count: 1
    min-seconds: 1200
    max-seconds: 5400
  - id: first-industrial-oven-fast
    description: Active players reach the Industrial Oven within 1.5 hours
    profiles: [active]
    metric: time-to
    building: industrial_oven
    count: 1
    max-seconds: 5400
  - id: first-industrial-oven-idle
    description: Idle players reach the Industrial Oven within 2 hours
    profiles: [idle]
    metric: time-to
    building: industrial_oven
    count: 1
    max-seconds: 7200
  - id: first-chicken-farm
    description: Chicken Farm (Expansion 3 tier) not before 1.5 hours, within the 4-hour horizon
    profiles: [casual]
    metric: time-to
    building: chicken_farm
    count: 1
    min-seconds: 5400
    max-seconds: 14400
  - id: first-chicken-farm-active
    description: Active players reach the Chicken Farm within the 4-hour horizon
    profiles: [active]
    metric: time-to
    building: chicken_farm
    count: 1
    max-seconds: 14400
  - id: rebirth-threshold-not-early
    description: 1B baked (the Rebirth threshold) not before 3.5 hours, before golden cookies and stars exist
    profiles: [casual]
    metric: baked
    cookies: 1000000000
    min-seconds: 12600
  - id: rebirth-threshold-not-early-active
    description: Active players reach 1B baked not before 3 hours
    profiles: [active]
    metric: baked
    cookies: 1000000000
    min-seconds: 10800
  - id: no-dead-zones
    description: Never more than 3 minutes with nothing affordable over the whole horizon
    profiles: [casual, active]
    metric: longest-dead-zone
    max-seconds: 180
  - id: idle-no-stall
    description: Idle players never wait more than 10 minutes with nothing affordable
    profiles: [idle]
    metric: longest-dead-zone
    max-seconds: 600
  # ingredients (2026-10-25): the Recipe Book (about 10K baked, the Expansion 1 tier) starts ingredient
  # production; the 7 recipes cost cookies + ingredients and give +% CPS.
  - id: recipe-book
    description: Recipe Book bought between 5 and 20 minutes (the Expansion 1 window)
    profiles: [casual]
    metric: upgrade
    upgrade: recipe_book
    min-seconds: 300
    max-seconds: 1200
  - id: recipe-book-active
    description: Active players buy the Recipe Book within 20 minutes
    profiles: [active]
    metric: upgrade
    upgrade: recipe_book
    max-seconds: 1200
  - id: recipe-book-idle
    description: Idle players buy the Recipe Book within 30 minutes
    profiles: [idle]
    metric: upgrade
    upgrade: recipe_book
    max-seconds: 1800
  - id: first-recipe-soon
    description: The first recipe within 10 minutes of the Recipe Book
    profiles: [idle, casual, active]
    metric: recipes
    count: 1
    since: recipe_book
    max-seconds: 600
  - id: all-recipes
    description: All 7 recipes within the 4-hour horizon, not in the first 2 hours
    profiles: [casual, active]
    metric: recipes
    count: 7
    min-seconds: 7200
    max-seconds: 14400
  - id: recipe-ingredient-wait
    description: No recipe waits more than 15 minutes on ingredients alone once its producers run
    profiles: [idle, casual, active]
    metric: recipe-ingredient-wait
    max-seconds: 900
```
