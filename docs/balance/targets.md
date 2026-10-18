# Balance targets

What "good pacing" means for CookieClicker. **Edit the YAML block below**; `mise run balance` reports against it and `mise run balance-check` (also in CI) fails if any target is violated. Prose outside the block is ignored by the tools.

## Player profiles
- **idle:** never clicks after buying the first building (clicks at the bootstrap rate until then, otherwise nothing could ever start).
- **casual:** 3 clicks/s.
- **active:** 8 clicks/s.

All profiles buy with the same "optimal-ish" strategy: among unlocked buildings and upgrades, pick the one with the lowest cost per extra cookie/s (clicks count as cookies/s at the profile's click rate), wait until it's affordable, buy it, repeat.

## Metrics
| Metric | Meaning |
|---|---|
| `first-purchase` | seconds until the first building is bought |
| `all-buildings` | seconds until at least one of every building is owned |
| `longest-dead-zone` | longest stretch (s) where **nothing** unlocked is affordable, after the first purchase; optional `window-minutes: N` only counts the first N minutes (a zone crossing the edge is cut off there) |
| `time-to` | seconds until `count` of `building` are owned (needs `building`, `count`) |
| `upgrade` | seconds until upgrade `upgrade` is bought (needs `upgrade`) |

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
```
