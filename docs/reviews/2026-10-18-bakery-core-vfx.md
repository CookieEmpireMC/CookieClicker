# Review: bakery-core / vfx-designer (Flare Larry)

Reviewer: Gandalf. Branch `bakery-core/vfx-designer` (7 commits) against `feature/bakery-core`.

**Target:** Paper 26.3 (`26.3.build.205-stable`), Java 25.

## Results

| Check | Result |
|---|---|
| `mise run test` | 57 pass (44 Larry + 13 mine), 0 fail |
| `mise run check` | BUILD SUCCESS |
| `mise run validate-assets` | 0 problems |
| `mise run tools-test` | 133 pass |
| `mise run vfx-previews` | 4 PNGs, read: shapes match the config (burst + arcs, growing ring, sparkles rising, pop squash/stretch) |
| ownership-check | OK, 7 commits inside Larry's files |
| Memory committed | yes (`.claude/agent-memory/vfx-designer/`) |

Tests added (`src/test/.../vfx/`): `PopSequenceEasingTest` (6: empty/non-rest/rest-ending sequences, start ticks, immutability, easing clamps/monotonic), `EffectsConfigEdgeTest` (7: empty file, shipped fallback, limit clamping, garbage layers/IDs/NaN, uppercase ID, crumb caps, pop step ticks >= 1).

## Traced paths (all OK)

- **Spawn:** `Spawner.spawn` sets persistent=false, visibleByDefault=(viewer==null), scoreboard tag `cookieclicker`, PDC `cookieclicker:vfx` inside the spawn consumer (no packet to others), then `showEntity` to the viewer only.
- **Tracked:** `DisplayJob.add` registers every entity in `DisplayRegistry`.
- **Removed on:** completion (`finish` -> `cleanup`), invalid entity mid-run (ends the whole effect), chunk unload (`EntitiesUnloadEvent` -> `job.cancel`), viewer quit (`cancelViewer`), reload and shutdown (`ticker.cancelAll`), cap eviction, job exception (`safeStep` cancels).
- **Sweep:** `load()`, `shutdown()` and `EntitiesLoadEvent` remove tagged untracked entities.
- **Shutdown:** task cancelled, listener unregistered, jobs cleaned, pops put back at rest scale; safe to call twice.
- **Pop:** read-modify-write of scale only (keeps translation and both rotations); re-pop hands over and reuses the first rest scale (no drift); step ticks clamped >= 1 so the one-step-per-tick loop cannot skip steps.
- **Caps:** per-player cap evicts oldest, global cap drops new, particle cap thinned at load. Shape points precomputed (`ShapePlan` flat arrays), no allocation per point; single shared ticker that sleeps when idle.
- **Errors:** unknown ID, async call, not loaded, bad config all log once and return; no throw out of `play`/`pop`.
- **Text:** `amount` goes through `Placeholder.unparsed`; config text only is parsed. No legacy codes, no reflection/NMS, no `playSound`, no deprecated API seen.

## Findings (no blockers)

Larry (all minor, optional):
1. `Effects.jitter++ % JITTER_SLOTS` goes negative after int overflow (~2^31 floating texts); use `Math.floorMod` or wrap. `Effects.java` (play, FloatingText case).
2. `sweepLeftovers()` is public and not main-thread guarded (only `play`/`pop` use `ready()`). Document or guard.
3. Cap design note: cookie_click holds one text for 20 ticks, so above ~8 clicks/s the oldest number is cut early by the per-player cap of 8 (crit uses 4). Intended eviction; tune `limits` if it looks choppy in play.
4. Effects with `viewer == null` skip the per-player cap (only global applies). Fine, but the coder should always pass a viewer for personal feedback.

Coder (plugin-coder pass 2):
- Call `Effects.load()` in `onEnable` and `shutdown()` in `onDisable`; wire `/bakery reload` to `load()`; set `setPackCheck`; pass the shared MiniMessage; main thread only.
- Engine has no runtime test (needs a server; MockBukkit for 26.3 not in pom). Do a manual smoke test: click spam, quit during effect, reload, chunk unload, stop server, then check `activeDisplayEntities()` is 0 and no tagged entities remain.

Architecture: `docs/architecture.md` does not exist yet; not created here to avoid merge conflicts with other branches. Package `vfx` = engine (`Effects` facade, jobs, registry, spawner, listener), `vfx.shape` = pure maths (no Bukkit), `vfx.config` = parser. No cycles seen (`shape` and `config` do not depend on engine classes).

## VERDICT: READY
