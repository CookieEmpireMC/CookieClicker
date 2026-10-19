# Review: starter-house, plugin-coder pass 1 (Gandalf, 2026-10-19)

Branch `starter-house/plugin-coder` (Works4Me Wesly), reviewed after one authorised sync merge of `feature/starter-house` (sound, model and vfx branches), merge commit `57df6ec`. Brief: `docs/briefs/starter-house.md`.

## 1. Target version
Paper `26.3.build.205-stable`, Java 25, `api-version: '26.3'`, WorldGuard 7.0.20 + WorldEdit 7.4.6 (`provided`, not shaded, `depend: [WorldGuard]`). Build settings consistent.

## 2. Test results
`mise run check`, `validate-assets` (12 files), `sound-check` (17 sounds), `balance-check` (6/6) and `tools-test` (146): **all green**. `mise run test`: 376 run, 0 failed, **1 skipped on purpose** (`OutlineTest.aMaxPointsLimitBelow...`, see Bug 1: the test reproduces a real hang and is `@Disabled` so the suite stays green; remove the annotation when it is fixed).

Written by me (all under `src/test/`):
- `JdbcPlayerStoreTest`: known red test fixed (expects `SCHEMA_VERSION`, 2); new v1 -> v2 migration test (hand-built v1 database keeps its data, `house_seen` appears, second init harmless); `house_seen` per player and plot, idempotent, `clear`, bad plot ids.
- `MessagesTest`: sample keys now `plot.*`; the "every path used in source exists" scan now also covers `plot.`/`house.` (config readers excluded).
- `HouseDesignParserTest` (17): keep (`.`, space) vs `_` air, quoted `- | ^ =` via real YAML, wrong row length/row count/layer count, unknown char, `.`/space in palette, bad palette entries, size caps, origin/point/sign out of bounds, optional door/signs, sign text stays plain, all problems collected, `toWorld`, and the shipped `houses/default.yml` (9x7x7, 170 blocks, chimney, 1 sign).
- `HouseBackupTest` (7): place -> restore round trip through a map-based `BlockAccess` (states kept), identical blocks skipped, arrays copied, real YAML round trip, 20x10x20 round trip, `fromMap` rejects broken files.
- `RegionShapeTest` (7), `OutlineTest` (7, 1 disabled), `PlotCodecTest` (11), `PlotResolverTest` (9), `StationMigrationTest` (6), `PedestalPrivacyPairsTest` (8), `PlotFileTest` (6), `PlotServiceTest` (8: first start, migration incl. leftovers and `.bak`, no stations.yml in the data folder = nothing invented, broken stations.yml left in place, reload re-resolves regions after a "redefine", broken plots.yml keeps current plots, unloaded world, setting a pedestal), `CookieClickerVersionCheckTest` (4, `olderThan`), `SoundConfigParserTest` (+3 for `@block`).
- `ArchitectureRulesTest` (+6): no `com.sk89q` outside `worldguard/`; `setBlockData`/`setType`/`breakNaturally`/`setBiome`/`getStructureManager` only in `house/`; no `sendBlockChange` yet; the tracker only queries regions behind `hasChangedBlock()`; no WG region types outside `worldguard/` (ids only); pure classes stay free of Bukkit world types.

Not unit-testable without a server (needs an in-game check): `WorldGuardHook`, `HousePlacer` (structure snapshot/undo, signs, chunk checks), `SoundService.playBlock` (`BlockData#getSoundGroup`), `PlotTracker` events, `PedestalPrivacy` hide/show.

## 3. Bugs, most severe first
1. **BLOCKING, main-thread hang:** `plot/Outline.java:23-27` `points(shape, step, maxPoints)` loops `while (pts.size() > max(1, maxPoints)) { s *= 1.5; ... }`. Every edge yields at least one point, so a cuboid never has fewer than 12 points (polygon: 3 per vertex). `PlotSettings` accepts `plot.show.max-points` from **4** to 2000 (`config/PlotSettings.java`), so `max-points: 4` (any value below 12) makes `/bakery plot show` spin forever on the main thread and freeze the server. Reproduced: `OutlineTest.aMaxPointsLimitBelowTheMinimumOutlineStillTerminates` times out for 4, and the same code path is hit for 8 and 11. Fix: stop the loop when the point count no longer shrinks (or cap iterations), and/or raise the clamp's lower bound to a safe value; for polygons the floor depends on the vertex count, so the loop guard is the real fix. Then remove the `@Disabled` on that test.
2. **MEDIUM, `house/HousePlacer.java` `paste()`:** blocks are changed on the main thread first and the backup files (`.nbt` + `.yml`) are written afterwards on the IO thread. If that write fails (disk full, permissions) the log gets a SEVERE but the admin still sees `house.placed` and only finds out at `undo` that nothing can be restored; a crash in between loses the undo as well. Suggest: tell the admin when the backup write failed (new message key, or reuse `house.place-failed` wording), and ideally write the backup before pasting (snapshot bytes are already in memory before the first block is set, so the order can be IO write first, then paste on main).
3. **LOW, `HousePlacer.paste()`:** `busy` is cleared only through `finish`/`finishNow`. An unexpected `RuntimeException` between `begin()` and the end of `paste()` (e.g. a failing `Bukkit.createBlockData`-like call outside the try, a sign update) leaves `busy = true` until `/bakery reload`. Wrap `paste` in try/finally that clears it and replies `house.place-failed`.
4. **LOW, `BakeryCommand.CHIMNEY_REACH = 16`:** a tunable in Java (not a balance number, so allowed); could move to `config.yml` with the other `plot.*` keys.
5. **LOW:** `/bakery plot show` and `/bakery house place` call `plots.update(...)` (writes `plots.yml`) when the flagged plot is not yet in the file. Intentional (comment says so) but a read-only command writing a file is surprising; consider documenting in the in-game guide.
6. **LOW:** dead `commands.station.*` messages (known; the coder will drop them, tests no longer use them).

No other defects found. Verified against the brief and the review list:
- Only `/bakery house place|undo` modify the world (`HousePlacer`; enforced by the new architecture test). Entities/particles go through `Effects`; `playSound` only in `sound/`.
- Region checks only on block change: `PlotTracker.onMove` is guarded by `hasChangedBlock()`, plus teleport, respawn, join, quit; reload `resyncAll()`. Region **ids** are cached (`PlotRegistry.resolved`), shapes are re-read on use; reload re-resolves (tested with `PlotServiceTest`).
- Cleanup: outline tasks stopped on quit/reload/disable (`PlotOutlines`), privacy hides undone on quit/restart(disabled)/shutdown (`PedestalPrivacy` only shows players it hid itself), tracker unregistered and cleared on disable, `HousePlacer.reset()` on reload/disable, disable steps wrapped so the session flush always runs.
- Threads: Bukkit/WG/world on main; plots.yml, backups, `house_seen` and design reads on the single IO thread; `HouseSeen` callbacks hop to main; storage failure on read counts as "seen" (logged).
- `com.sk89q` only in `worldguard/WorldGuardHook` (flags registered in `onLoad`, `FlagConflictException` reused for same type); WG/WE are `provided`, shade plugin does not include them.
- Text/security: all new player input (`id`, `name`, `problem`) goes in with `Placeholder.unparsed`; no hardcoded player text in Java beyond log lines; every `plot.*`/`house.*` key used in source exists in `messages.yml` and parses strictly (tests). `house.cinematic.*` messages exist for pass 2. Sign text from designs is set as plain `Component.text` (never parsed).
- Balance: no balance numbers in the new code. Sounds: `house_block_place` is `@block` through `SoundService.playBlock` only; `validate_assets.py` accepts only `@block` as special key (tools tests green).
- `stations.yml` deletion (commit c287294): the jar no longer ships it; `StationFile`'s default-copy is a no-op when the jar has no resource, and migration only looks at `<dataFolder>/stations.yml` (`PlotService.migrate`, tested). The `.bak` rename runs only after `plots.yml` was written (`afterWrite`). Intentional per Decision 2. Fine.
- Docs: `docs/integrations.md` WorldGuard section present and matches the code (versions, maven, onLoad flags, RegionQuery, recommended flags, interaction-click caveat).

## 4. Architecture
`docs/architecture.md` updated (new `worldguard`, `plot`, `house` packages, schema v2, enable/disable/reload order, world-write and threading rules). No circular packages: `plot` and `house` depend on `station`/`config`/`text`/`vfx` (down), `worldguard` depends on `plot` only. `CookieClicker` stays a wiring class (it grew by about 100 lines for WG/plot wiring; acceptable). `PlotService` is the only place that touches both `plots.yml` and `StationRegistry`. Suggestion for the `optimize` feature: `paper-plugin.yml` must keep WorldGuard loading first (documented).

## 5. Performance
- Hot paths: `PlotTracker.onMove` runs on every `PlayerMoveEvent` but returns at `hasChangedBlock()` false; on a block change it does one WorldGuard `RegionQuery` (WG caches for 1-2 s, one small `ApplicableRegionSet` allocation) and a record comparison: PERF-LOW, comparable to what WG itself does per block change. Nothing per tick for idle players.
- `PedestalPrivacy` (off by default): every `check-ticks` (10) O(players x pedestals) plus O(near^2) pairs; pure allocation of a few sets: PERF-LOW. `hidePlayer` also hides the player in the tab list for the viewer, mention in the guide.
- `PlotOutlines`: per admin viewing, every `redraw-ticks` (20) up to `max-points` x 2 `Effects.play` calls (default 240/s, bounded by Effects limits); stops after `duration-seconds`. PERF-LOW (subject to Bug 1).
- `/bakery house place`: up to `house.max-blocks` (40000) block writes + a structure snapshot on the main thread in one tick; a one-off admin action; the default cottage is 170 blocks. PERF-LOW. `RegionShape.blocks()` (box iteration with `contains`) is documented "call once"; pass 2 must cache it.
- Reload: one `regions(world)` scan per world; no I/O on main (plots.yml read on main is a small file, same as stations.yml before).
- Storage: `house_seen` one indexed PK lookup per join/enter on the IO thread; no per-tick queries.
- PERF-HIGH: none (Bug 1 is a correctness/hang issue, listed as blocking above).

spark guide for the PR: `/spark profiler start --timeout 60` while two accounts run across the plot border and a third walks around nearby; the plugin's share (`PlotTracker.onMove`, `WorldGuardHook.at`) should stay far below 1 % of the tick and show no frames from `Outline`, `PedestalPrivacy.check` unless enabled; run `/bakery plot show` for 15 s and check `PlotOutlines` below 1 %.

## 6. In-game checks for the human (not coverable by tests)
Cookie and shop clicks inside a `build deny` plot (else `interact allow`); `/rg redefine` + `/bakery reload` moves the plot; `house place` then `house undo` restores chests with items and sign text; `playBlock` sound for stairs/glass/bricks is the vanilla one; WG missing/disabled message on the console.

## Verdict
**VERDICT: NOT READY**, for one blocking item (Bug 1, server hang for `plot.show.max-points < 12`). Fix list for Works4Me Wesly: Bug 1 (blocking), Bug 2 (medium, should fix in this round), Bug 3 (low). Everything else is READY; re-review after the fix only needs `git diff` of `Outline.java`, `PlotSettings.java`, `HousePlacer.java` and the un-disabled test.
