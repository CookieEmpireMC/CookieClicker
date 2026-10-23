# Tidy: world part review (`tidy/plugin-coder-world`)

Gandalf · 2026-10-23 · branch `tidy/plugin-tester-world` (merge of `tidy/plugin-coder-world`, 10 commits on `b09a659`) · Paper 26.x, Java 25

**VERDICT: READY** (no HIGH/MEDIUM; the LOWs go to the PR follow-ups)

## Checks
`mise run check-quiet` green (390 tests: 387 existing, 3 new), `validate-assets` 0 problems, `sound-check` 28 ok, `balance-check` 19/19.
Merge was conflict free. Test adaptation (commit `ae24097`): the 18 `test/house/` tests moved into `house/design|setup|cinematic`,
imports and source paths fixed. `ArchitectureRulesTest`: paths follow the moves, and "the world is never modified by the
cinematic classes" now scans **every** file of `house/cinematic` (so new classes are covered without editing a list).
`PlotRegistryBoundsTest` (3 tests, commit after it): inside/edge/outside incl. plot + house regions, a flag naming no plot
ignored, world without plots and unloaded world never match, bounds refresh on `replaceAll` and `put`, stale until then.
The belt ride-task start/stop is not tested: it needs `Bukkit.getScheduler()` (no server in the unit tests); reviewed by reading.

## Review of the asked points
- **Package layering:** `house.design` imports nothing from `setup`/`cinematic`; `setup` and `cinematic` do not import each
  other; no `plot`/`belt`/`economy`/`session` class imports `house`. No cycles. Sizes: PlotSetup 264, HouseCinematics 489,
  ConveyorBelts 401, VisibleBuildings 395 (the 500 stop sign is respected; HouseCinematics is over the 400 target by 89, fine).
- **W1 correctness:** the bounds are the union of the shapes of every region whose `bakery-plot` **or** `bakery-house` flag
  names a known plot, per world UID (a world without a plot of its own gets an empty box). One union box is conservative (a
  false "yes" only costs the old WorldGuard query), never a false "no" for a flagged cuboid or polygon (polygon shapes carry
  their min/max). House boxes of set-up plots only count inside the plot region (`known()` needs the plot flag), which the
  bounds contain. Plot save/command paths go through `PlotService.save -> registry.put`, which clears the cache; so does
  `/bakery reload`. Multiple worlds are keyed separately.
- **"/rg flag needs /bakery reload" is acceptable, rated LOW.** Before, region ids were already re-resolved only on reload
  (`resolved`), and the documented admin flow is `/rg redefine` + `/bakery reload`. New is only that a *live* `at()` used to
  notice a region that grew or got a flag without a reload. Cheap fix if wanted (follow-up): give each cached `Bounds` a
  timestamp and rebuild after about 30 s (one `regions(world)` + shape reads per world per 30 s), which also removes the
  next point.
- **LOW: an empty box is cached.** If the first lookup for a world happens while WorldGuard has no region data for it, the
  empty box stays until the next reload/put (silent "no plots in that world"). Unlikely (first lookup is a player already in
  that world), same TTL fix.
- **CinematicExits listener order:** the old class had `onTeleportToViewpoint` (starts a run, only when **not** running) and
  `onTeleport` (aborts, only when running) at the same priority; they are mutually exclusive, so the registration order
  (`HouseCinematics`, camera, guards, exits) changes nothing. The LOW teleport guard (`CinematicGuards`) still runs before
  both. Death, quit, world change and join handlers moved unchanged.
- **WorldOperation / BackupFiles:** compared with the old `PlotSetup`/`HousePlacer`: same gate token logic (`step` ends the
  gate in `finally` unless handed to IO, stale token runs nothing and stays silent), same cancelled-operation path (backup
  deleted, nothing built), same IO-rejected path, same newest-pair undo and delete. HouseBackup/SetupBackup share the cell
  codec (`x y z data`), format unchanged; `HouseBackupTest`/`SetupBackupTest` pass.
- **House undo world-height check:** `HousePlacer.loaded` (min/max height + chunks loaded) is unchanged and is called by
  place, undo and (via the planner) setup, as before.
- **W3 timing:** the ride task is started by the 10-tick refresh when a rider exists, runs every tick, and stops itself after
  the first tick without riders (also after `restart()`/`removeAll()`/an exception). A new rider's first cookie can start
  at most one tick later than before. `tick` only counts while riding, which is fine because lanes and rides are dropped
  with the riders.
- **W7/W8:** `PedestalPrivacy.check` early-out is exact (nothing hidden and fewer than 2 players: nothing to do); scratch
  collections are cleared at the start. `WarnOnce` in `WorldGuardHook` keeps the one-warning-per-key behaviour.

## Findings
| Sev | Where | Finding |
|---|---|---|
| LOW | `PlotRegistry.mayContainPlot` | bounds cached until reload/put: a changed or new flagged region is invisible to the tracker until `/bakery reload` (see above); optional 30 s TTL |
| LOW | `PlotRegistry.boundsOf` | an empty box is cached when WorldGuard has no region data yet; same TTL |
| LOW | `PlotRegistry.boundsOf` | a flag naming a plot of another world is ignored in worlds without any plot of their own (the old code accepted it); not a supported setup |
| LOW | `ConveyorBelts` | ride-task start/stop untested (needs a server); profile with spark |

No HIGH/MEDIUM. No PERF-HIGH.

## Performance
Per block change of a player outside every plot: one map lookup and 6 compares instead of a WorldGuard region query (W1, the
biggest world-side saving). Belts: no per-tick work without riders (W3), one player pass per 10 ticks (W2). Numbers in
`2026-10-23-tidy-perf.md`. Spark guide: with 5 players walking outside the plot, `PlotTracker.update` and
`WorldGuardHook.at` must not appear; inside the plot they appear only on block changes.
