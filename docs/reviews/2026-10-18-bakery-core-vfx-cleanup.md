# Review: bakery-core / vfx-designer-cleanup (step 4b)

Reviewer: Gandalf. Target: Paper 26.x (unchanged). Branch `bakery-core/vfx-designer-cleanup`, 7 commits by Flare Larry.

## Checks
- `mise run test`: 228 run, 0 failures, 0 errors. `mise run check`: green. `validate-assets`: 12 files, 0 problems.
- `ownership-check`: OK (7 commits, all inside author's files).
- `git diff feature/bakery-core...HEAD -- src/test`: empty (no test edits).
- Public API: only `Effects.sweepLeftovers()` Javadoc changed; the new `onMainThread` is private. No signature, effect ID or effects.yml change.

## Behaviour, item by item
1. 6ec9f55 `Math.floorMod(jitter++, 12)` vs `jitter++ % 12`: identical for jitter >= 0. Differs only after int overflow (~2.1e9 floating texts), where the old code gave a negative slot; the new one is the intended fix. Equivalent for all reachable inputs.
2. 3a2f829 `sweepLeftovers()` now returns 0 and warns once ("async" key) when off the main thread. Main-thread behaviour identical. Internal callers (`load()` line 134, line 155) are already main-thread. `ready()` refactor keeps the same order (thread check, then loaded check) and the same warning text. Off-thread calls previously touched world entity lists from another thread, so the guard is strictly safer. Only visible change, and it is a bug fix consistent with the rest of the API.
3. 42e89e9 `isOurs` checks the scoreboard tag, then the PDC key. `spawn()` always sets both, so every entity we spawn passes both. Edge case: an entity with our PDC key but without the `cookieclicker` tag (tag removed by a command/plugin) is no longer swept on load or in `sweepLeftovers`. Effect entities are `setPersistent(false)`, so they are never saved to disk and cannot survive a restart or crash to be found later. The case is practically unreachable, and the entity would still be removed by its job if tracked. ACCEPTABLE. Note the opposite case (tag present, PDC missing) was already not swept.
4. cdd3ca2 early return in `onEntitiesUnload` when the registry is empty: the loop only cancels jobs found by `registry.job(uuid)`, which reads `byEntity`; with an empty map it always returns null, so the loop was a no-op. Identical. `onEntitiesLoad` untouched.
5. 5779dd9 removed `DisplayJob.entities()`, `DisplayRegistry.viewerEntities()`, `EffectTicker.running()`, `Job.cancelled()`: grep shows no remaining references in `src/main` or `src/test` (`StationView.entities()` is a different class). Compiles, tests pass.
6. afa0f18/e42799d: docs/memory only.

## Performance
- `onEntitiesUnload` (fires per chunk unload, MONITOR): now O(1) when no effect is running (common case); previously O(entities in chunk) map lookups. Improvement.
- `isOurs` (per entity on chunk entity load and in sweep): tag set lookup first avoids a PDC read for the vast majority of foreign entities. Improvement.
- `sweepLeftovers`: one-time at load/reload, scans all world entities; unchanged, acceptable. No new hot-path cost anywhere. No PERF findings.
- Spark: nothing new to profile; chunk-load/unload share of the vfx listener should be negligible.

## Findings
None blocking. Optional note: `JITTER_SLOTS` guard is fine as is.

VERDICT: READY
