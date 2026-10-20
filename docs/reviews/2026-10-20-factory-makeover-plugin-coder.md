# factory-makeover: plugin-coder branch review (Gandalf, 2026-10-20)

Branch `factory-makeover/plugin-coder` at 55dbac1 (b58ecc8..8209cc8). Target: Paper 26.3, Java 25 (unchanged).

## Tests
- `mise run test`: 485 run, 0 failures, 1 skipped (the "shipped factory/fence/cottage parse" test skips on this branch because the real
  `factory.yml`/`fence.yml` only exist on `feature/factory-makeover`; it runs at integration).
- `mise run check`: green. `validate-assets`: green (this branch carries no assets).
- Fixed: `HouseSettingsTest.emptyConfigGivesTheDocumentedDefaults` now expects 2.25 / 5 / teleport true / steam 7 / ambient 80 / 24.
- New: `HouseLayoutTest` (9), `FenceStyleTest` (7), `FenceLayoutTest` (10, real 31x30 ring + real fence.yml), `SetupBackupTest` (6, YAML text
  round trip, corrupt files, restore), `PlotCodecSetupKeysTest` (6), `HouseDesignPointsTest` (10: smoke/steam lists, legacy chimney,
  layout-in-ring check, real factory.yml fixture), plus `ArchitectureRulesTest.onlyHousePlacerAndPlotSetupWriteBlocks`.
  Fixtures: `src/test/resources/fixtures/factory-makeover-{factory,fence}.yml` are snapshots of the feature branch files.

## Bugs / findings (no blockers)
- LOW `PlotSetup.build`: an unexpected RuntimeException after the old setup was restored (e.g. in `known.get`) is caught by `step`, but the old
  backup files stay. The next setup/undo then replays them over the already clean world and could overwrite blocks built in between. The
  expected failure paths (flat, height, structure, IO, reload) all go through `abandon`/deleteOld and are consistent.
- LOW `PlotSetup`: changes are computed in `build` and applied after the async backup write; blocks changed by players in those few ms are
  backed up stale. Negligible for an admin command.
- LOW layout is centred on the region bounding box; for a non-rectangular (polygon) region the footprint can stick out of the region and
  the ground vote only counts columns inside it. Matches Decision 4; just note it in the test guide (draw a rectangle).
- LOW `ground()`: a tree trunk (logs are solid) inside the footprint raises the spread above 2 and gives a confusing "not flat" message.
- LOW `ChimneySmoke.Session.active()`: a finished one-shot `runTaskLater` still reports `!isCancelled()`, so `start()` never restarts a
  session whose loops died on their own (the engine ends loops on quit/world change/reload; `HouseCinematics` stops those paths, so no live bug found).

## The points you flagged
- Setup ordering/abandon: restore of the old setup happens before the ground is measured; every failure after it deletes the used backup
  (state = before the old setup, consistent); the new backup is on disk before the first block changes, and a reload cancels via the gate
  (late callbacks delete the new backup). OK.
- Fence-to-house gap only at the column height: not a problem. `HouseDesignParser` rejects a `layout.house-at` that puts the house outside the
  area inside the ring, so the fence ring and the house box cannot touch (tested in `HouseDesignPointsTest.layoutMustFitInsideTheFenceRing`);
  `FenceLayout` still drops pieces inside the house box (tested).
- Main-thread writes: PERF-LOW. ~796 house cells + ~120 ring columns x 1-5 blocks is about 1,500 `setBlockData(.., false)` in one tick (no physics),
  roughly a one-off 20-60 ms spike in an admin-only command; the box is bounded by `house.max-blocks` (40000). No batching needed now; if
  bigger designs come, split `apply` over ticks. `FakeBlocks` for the cinematic is unaffected.
- Polygon centring and ground sampling: see LOW above; the vote (mode, ties go to the higher Y) and the spread check (<= 2) behave as documented;
  the fence follows per-column ground within +-3 of the layout ground, the gate uses the entrance ground (tested level lintel).
- docs/architecture.md updated (PlotSetup is a second world writer; new pure classes; the enforcing test now allows exactly HousePlacer and PlotSetup).

## Performance
`ChimneySmoke`: per player in a plot 2 smoke + 2 steam effect loops (effect cap `max-loops-per-player` 8 respected) and one 80-tick ambient
timer (a distance check + one sound): negligible. Nothing new per tick. Setup is one-off (see above). Spark: profile `/bakery plot setup`
once (expect one short spike, plugin share only that tick) and 60 s standing in the plot (expect ~0% for ChimneySmoke).

## Architecture
No new cycles; `house` stays Bukkit-free in the pure classes. No NMS, no async Bukkit calls, no static player maps (`sessions` keyed by UUID, removed on stop).

VERDICT: READY
