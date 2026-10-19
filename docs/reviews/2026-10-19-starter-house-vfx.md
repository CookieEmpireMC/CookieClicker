# Review: starter-house/vfx-designer (2026-10-19)

Target: Paper 26.3 (Java 25). Reviewed at 2bcf65a (+ my test commit). Scope: vfx package changes since bakery-core.

## Tests
- `mise run check`: green (271 tests incl. ArchitectureRulesTest, DropTrackTest, HouseJobsTest, HouseEffectsConfigTest). `validate-assets`: 0 problems. `tools-test`: 133 OK. `sound-check`: OK.
- Added `EffectTickerTest` (5 tests, Proxy-based fake plugin/scheduler): job started inside a callback is stepped once per tick (age 0 only at start), task stops/restarts, mid-list job ends never skip others, `cancelAll` from inside a callback, pending job also cancelled by `cancelAll`. All pass.

## Findings
1. Contract signatures match the brief rows exactly: `boolean dropBlock(viewer, data, target, @Nullable onImpact, onLanded)` (+4-arg), `loop(id, loc, viewer[, @Nullable onRepeat]) -> EffectHandle`, `cancelFor`, `EffectHandle{cancel,isActive,NONE}`, `activeFallingBlocks/activeLoops`; limits 24/240/4, `loop-every`, `force`, all six effect IDs exist in effects.yml.
2. Callback rules hold (DropJob): impact before landed, each at most once; land = impact-if-missing, `cancel()` (display removed + cap slot freed), then `onLanded` in the same tick; `cancelFor`/quit/reload/shutdown/viewer leaving (offline or other world, checked each tick) run `cleanup` only, no callbacks; a cancel from inside `onImpact` is honoured; callback exceptions are swallowed and logged. Early vanish (chunk unload, `isValid()` false) lands the drop on the next tick (no listener needed; stalls impossible).
3. "Landed instantly" false-return path (cap hit, air, not loaded, spawn failure) runs impact + onLanded inside the call. Documented in Javadoc. LOW note for the cinematic coder: do not assume `onLanded` is asynchronous; if iterating your own queue/list while calling `dropBlock`, advance/remove the entry before the call, and handle re-entrant `dropBlock` from inside `onLanded` (works: the new job goes through `pending`).
4. Visibility: spawned in the spawn consumer with `setVisibleByDefault(false)`, non-persistent, `cookieclicker` tag + `cookieclicker:vfx` PDC, then `showEntity` to the viewer only; first keyframe at `startTick` >= 1 (default 2, enforced in `Params`); `sweepLeftovers`/`EntitiesLoadEvent` remove untracked drop displays (tracked via `dropEntities`).
5. EffectTicker `pending` buffering is correct (tests above); swap-remove and `cancelAll`-in-callback safe. No behaviour change for existing effects (cookie_click/crit/purchase/pop untouched except `ParticleJob` passing `force`, default false = previous behaviour).
6. Caps: separate falling (24/viewer, 240 global) and loop (4/viewer) caps, not counted against click-number display caps (intended). Main-thread guards on dropBlock/loop/cancelFor/sweep.

No bugs found. No fixes required.

## Performance
- Per tick: one shared task, only while jobs exist. DropJob.tick = viewer online/world check + <=3 keyframe comparisons; `setTransformation` only on 3 ticks per drop. 24 x N drops: O(drops) trivial (~240 jobs x a few field reads). LoopJob: one modulo per tick; plays 3 particles per 10 ticks.
- PERF-LOW: `Effects.dropBlock` calls `getHighestBlockYAt` + 2 light reads per drop when `brightness: auto` (chunk is loaded; cheap). If a cinematic spawns many in one tick, set a fixed [block, sky] brightness.
- PERF-LOW: each impact = 4 block particles + 8-particle `house_block_land` play (an extra pending job); if the coder's blocks-per-tick is high (e.g. 20), that's ~240 particles in one tick to one viewer. Acceptable; cap blocks-per-tick in config.
- Entity budget: <=24 BlockDisplays per viewer, 240 global; each removed on landing.
- spark: profile `/spark profiler start --timeout 60` during a full cinematic; expect the plugin's share well under 1% of the tick and no `EffectTicker.run` hot spot; check entity count stays <= 24 per player (`activeFallingBlocks`).

## Architecture
vfx stays self-contained (ArchitectureRulesTest green); Job/LoopJob/DropJob use testable seams (Body, suppliers). No static player maps; viewer sets cleaned in cleanup. No docs/architecture.md change needed beyond the vfx package gaining DropJob/LoopJob/ViewerJobs/EffectHandle.

VERDICT: READY
