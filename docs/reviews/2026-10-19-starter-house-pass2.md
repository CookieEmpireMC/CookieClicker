# Review: starter-house, plugin-coder pass 2 (per-player build cinematic)

Reviewer: Gandalf. Branch `starter-house/plugin-coder-2` (commits a85278e..b03c8e3, cut from 56fb760). Date 2026-10-19.

**VERDICT: READY** (no blocking findings; six small fixes recommended, listed below).

## 1. Target
Paper 26.3 (`26.3.build.205-stable`), Java 25, `api-version: '26.3'`. Unchanged build settings.

## 2. Tests
`mise run test`: **435 tests, 0 failures**. New by Gandalf:
- `ArchitectureRulesTest`: the known red test replaced by `fakeBlocksAreOnlySentFromFakeBlocks` (`sendBlockChange(s)`, `sendMultiBlockChange`, `sendBlockUpdate` only in `house/FakeBlocks.java`, and it must actually send batches) and `theWorldIsNeverModifiedByTheCinematicClasses` (no `setType`, `setBlockData`, `applyPhysics`, `getState().update` in the 7 cinematic classes).
- `RevealOrderTest` (8): bottom-to-top layers, outward from the centre, even-sized centre, determinism for any input order, sparse/negative coordinates, L-shaped polygon (the notch is never hidden or revealed).
- `RevealPlanTest` (20): hiding paced by `sendPerTick`, layer delay, fractional blocks-per-tick, no burst after a stall, falling cap, layer gating (3 of 4 landed = nothing), re-entrant landing inside the drop call + idempotent `landed`, `abort()` returns every hidden/falling block exactly once (and nothing already real), abort while still hiding, late `landed` after abort ignored, `isHidden`, resend (paced, only hidden blocks, never ahead of the hiding cursor, automatic refresh), full run.
- `HouseBoxTest` (8): near, chunk overlap with negative coordinates, body overlap (feet/head/width, negatives, at most 12 cells), centre.
- `HouseSettingsTest` (7): defaults, `config.yml` equals the in-code defaults, double `blocks-per-tick`, `max-falling-displays` clamped to 24, other clamps, bad enums fall back with a problem.
Not unit-testable without a server (no MockBukkit in the pom): `HouseCinematics` event paths, `FakeBlocks`, `KeepOut`, `ChimneySmoke`. Covered by code review below and the in-game guide in the integration review.
Other checks: `check`, `validate-assets`, `sound-check`, `balance-check`, `tools-test` (146), `ownership-check` (vs feature branch: 5 commits OK) all green.

## 3. Safety review (acceptance-critical)
Verified by reading `HouseCinematics` (689 lines) line by line:
- **One abort funnel.** `abort(id, online)`: remove run, `run.alive=false`, `plan.abort()` (every hidden or falling block), `effects.cancelFor(p)` (no callback can run afterwards), `sendReal` of those blocks when online and in the run's world, `keepOut.clear`. Seen is only written in `complete()`. Callers: quit (online=false), death, teleport >= 1 block, world change, `onLeavePlot`, `abortAll` (reload and shutdown), tick exception, tick with offline player, `start()` (replay), `rebuild`. Disable order puts `cinematics.shutdown()` before plot tracking, views and effects shutdown, inside `safely(...)`.
- **Order "cancelFor first":** correct (before any `sendReal`). `alive=false` is set even earlier, so a stray callback is a no-op anyway.
- **selfTeleport exemption:** set around the `start()` viewpoint teleport in `try/finally`; checked in `onTeleportGuard`, `onTeleport` and `onTeleportToViewpoint`; removed on quit.
- **"Teleport under 1 block is ignored" cannot let a real teleport into the box:** `onTeleportGuard` (LOW, before the MONITOR abort) cancels every teleport whose body would overlap a still-hidden block, whatever its length. The < 1 block exemption exists only for the server's own rubber-band after the cancelled-move guard (`onMove`). A < 1 block teleport that does not touch hidden blocks is harmless.
- **Re-entrancy:** `RevealPlan.nextDrop` marks the block falling before returning it; `drop()` handles `dropBlock == false` ("landed instantly" or "did nothing") by landing it itself; `landed` and `impact` check `run.alive`; `step` loops on `run.alive`; `complete` is guarded by `alive` and removes with `running.remove(id, run)`.
- **Late seen answer after reload:** `generation` is bumped by `abortAll`; the callback compares both its own generation and `pending`; also re-checks player online, still in plot, not running. Same for `resumeSmokeLater` and the viewpoint trigger.
- **World never modified:** only reads (`getType`, `getBlockData`, `getState(false)`); enforced by the new architecture test.
- **Per player only:** `sendMultiBlockChange`/`sendBlockUpdate`, `dropBlock(viewer)`, `effects.play(..., p)`, `sounds.play(p, ...)`, `showTitle` all target the one player. Smoke is `Effects.loop(..., viewer)`.
- **Polygon filtering:** `RegionShape.blocks()` keeps only contained blocks (tested in pass 1 and again here through `RevealOrder`/`RevealPlan`); `start()` further skips air. Shape cache cleared in `abortAll` and `restart`.
- **Smoke (Decision 3):** started on enter plot / after completion / after a seen answer, not while a run exists, stopped on leave, quit, and before a cinematic starts, restarted after reload, after death and teleport (`resumeSmokeLater`).
- **Cleanup / leaks:** all maps keyed by UUID (`running`, `pending`, `selfTeleport`, `ChimneySmoke.loops`) are cleared on quit; `abortAll` clears `pending` and `shapes`; the 1-tick task is cancelled when `running` is empty and on shutdown; listener unregistered on shutdown.

## 4. Findings
Nothing blocks. Fix in one small coder round (or the 4b pass), most important first:

1. **MEDIUM** `sound/SoundService.java:131`: `Sound#key()` is `@Deprecated(since = "1.20.5", forRemoval = true)` and `@ScheduledForRemoval(inVersion = "1.22")` in the 26.3 API (verified in `paper-api-26.3.build.205-stable-sources.jar`, `org/bukkit/Sound.java` lines 4045-4073: "use `Registry#getKey(Keyed)` and `Registry#SOUNDS`. Sounds can exist without a key."). It is the only deprecation warning of the whole build. Replacement:
   ```java
   NamespacedKey k = Registry.SOUNDS.getKey(block.getSoundGroup().getPlaceSound()); // nullable
   if (k == null) { warnOnce(...); return null; }   // NamespacedKey is an Adventure Key
   cached = k;
   ```
   (`Registry.getKeyOrThrow` also exists; the existing `catch (RuntimeException)` would then cover it.)
2. **LOW** `HouseCinematics.start()` INSIDE check (`HouseBox.bodyOverlaps` at the feet only): a player standing **on** the house (feet on top of the roof, or on the floor of the hollow cottage, which is not inside any hidden block) gets the floor under them hidden, the client falls, the server rubber-bands. Reachable with `/bakery house rebuild` or `teleport-to-viewpoint`. Fix: also test the cell(s) just below the feet (`bodyOverlaps(x, y - 0.5, z, ...)`) and return `INSIDE`. Also `start()` teleports to the viewpoint before this check, so an INSIDE result after a teleport leaves the player moved without a cinematic (only if the viewpoint is inside the house).
3. **LOW** `abort()` has no `try/finally`: if `effects.cancelFor` threw, blocks would stay fake and keep-out stay on. Wrap `cancelFor` so `sendReal` and `keepOut.clear` always run. Likewise `landed()` marks the plan revealed before `sendReal`; if `sendReal` threw the block would stay fake air (until relog/chunk resend). Unlikely, cheap to harden. The very first `step()` inside `start()` is also not inside the tick's try/catch (the next tick does recover and abort).
4. **LOW** `RevealPlan.beginTick`: the budget cap `max(1.0, blocksPerTick)` drops the fractional part when `blocks-per-tick` > 1 and non-integer (2.5 behaves like 2). Harmless at the default 1.0 and for values < 1; fix: cap at `blocksPerTick + 1`. Documented in `RevealPlanTest.severalBlocksPerTickAndNoBurstAfterAWait`.
5. **LOW** `HouseCinematics.request`: `pending` holds one entry per player, not per plot; with several plots a quick plot A -> plot B move can swallow B's answer (B then has neither cinematic nor smoke until the next enter). Single plot today; key by plot id when multi-plot arrives.
6. **LOW (perf, memory)** `Run.sendBuffer = new long[2 * plan.size()]` could be `min(2 * size, sendPerTick)`; `RevealPlan.indexOf` is a boxed `HashMap<Long,Integer>` (2 x size entries; 40 000 blocks ~ 5 MB per running player). Fine for a 170-block cottage (box 441); bounded by `house.max-blocks`.

## 5. Performance (pass 2)
- **Idle:** one `PlayerMoveEvent` handler that returns after `running.isEmpty()`; interact/break/place/`PlayerChunkLoadEvent` handlers are one `HashMap.get`. No task while nobody watches a cinematic. `PlayerChunkLoadEvent` is created for every chunk sent to every player (join bursts of hundreds), PERF-LOW but only a map lookup.
- **Per running cinematic, per tick:** `RevealPlan` budgets (O(1)); hide: up to `fake-air-per-tick` (4096) positions in one `sendMultiBlockChange` (a 441-block box is one tick); re-assert every 40 ticks resends only still-hidden blocks over the same budget; at the default 1 block/tick: 1 `getBlockData`, 1 `dropBlock` (1 `BlockDisplay`, <= 12 alive at once), 1 landing = 1 `sendMultiBlockChange` of 1 block, ~3 sound packets (land, block place, hammer every 3rd), ~32 particles (land 8 + block 4 + sparkle 20; +28 dust on the bottom layer). Move guard: <= 12 hash lookups per move event of the watching player only.
- **Start:** one pass over the house region box (`getType` per block, `getState(false)` once per material) in a single tick: ~0.5 ms for the cottage, a few ms at the 40 000 `max-blocks` ceiling.
- **Smoke:** per player in the plot, 3 particles per 10 ticks and a sound packet every 60 ticks.
- No blocking I/O on main (seen read/write on the IO thread, answers hop to main). No PERF-HIGH or PERF-MEDIUM.

## 6. Version / text checks
Paper 26.3 API only; no NMS or reflection; no legacy colour codes; rebuild messages use `Placeholder.unparsed` (player name is user input); title via `Messages.component`. `Position` (`@Experimental`) confined to `FakeBlocks`. Attribute names `MOVEMENT_SPEED`, `JUMP_STRENGTH` are the 26.x names. Only deprecation: finding 1.

## 7. Ledger
Pass 2 verified at `b03c8e3` plus Gandalf's tests. Re-review with `git diff b03c8e3..HEAD -- src/main/java/dev/casperrs/cookieClicker/house src/main/java/dev/casperrs/cookieClicker/config`.
