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
| `longest-dead-zone` | longest stretch (s) where **nothing** unlocked is affordable, after the first purchase |
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
  - id: first-building-fast
    description: First building within 30 s
    profiles: [casual, active]
    metric: first-purchase
    max-seconds: 30
  - id: all-tiers-in-2h
    description: Every building tier owned within ~2 hours of active play
    profiles: [active]
    metric: all-buildings
    max-seconds: 7200
  - id: no-dead-zones
    description: Never more than ~3 minutes with nothing affordable
    profiles: [casual, active]
    metric: longest-dead-zone
    max-seconds: 180
```
