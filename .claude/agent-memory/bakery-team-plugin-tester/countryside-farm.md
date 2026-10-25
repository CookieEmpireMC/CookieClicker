---
name: countryside-farm
description: countryside-builds farm code (farm/ package, house.setup FarmSetup/FarmBackup): where it lives, format rules, fixtures, review state at e747f3c (2026-10-25)
metadata:
  type: project
---

Verified 2026-10-25 at `countryside-builds/plugin-coder-setup` 7fc2604 (review, NOT READY). Tests at `countryside-builds/plugin-tester` 87dd3dd, 436 Java tests green.

**Where it lives**
- Pure `farm/`: `PlacePoints` (place `points:`), `FarmDesigns` (`houses/countryside.yml` + places, checked together), `FarmPlan` (anchor, box, world points, `neededSize`), `PasteCursor` (cells in paste order), `FarmCodec` (`plots.yml farms:`), `Farm`, `FarmMarks`.
- Bukkit `farm/`: `FarmRegistry`, `FarmTeleports` (cooldown, last plot per player), `FarmAmbience` (smoke loops, one task while a smoke point exists).
- Setup `house/setup/`: `FarmSetup` (ticked snapshot/paste/restore), `FarmBackup` (slabs + manifest), `FarmBackupFiles` (disk), `ChunkHold` (plugin tickets), `PasteProgress`. Shares `WorldOperation`/`OperationGate` with the house paste.
- `command/FarmCommand`, `config/FarmSettings` (`farm.teleport-cooldown-seconds` 5, `farm.paste.blocks-per-tick` 5000, range 100..100000).

**Format rules (from the code, stable for this branch)**
- Countryside = house design with `ground-depth` up to 4 (houses 2). Region min y = ground level; countryside corner = regionMin - ground.
- A place cell `[0,0,0]` lands on countryside cell `places.<p>.at`. The place's own `origin` cancels out (anchor = target + origin, `toWorld` subtracts it again). Place yaw must be 0.
- `neededSize` measures up from regionMin.y and checks x/z only against regionMin; box.minY is not checked.
- `FarmPlan.plan` checks each place against the countryside box, never the countryside box against the region's min corner.

**Fixtures** (not shipped): `src/test/resources/farm/{countryside,mill,market}.yml`, helper `farm/FarmFixtures` (REGION_MIN 100,64,200; countryside 12x5x8, mill 5x5x4 ground 3, market 4x4x4 ground 1; mill at [1,0,1], market at [8,0,4]).

**Test facts (verified)**
- `PlotRegistryBoundsTest.FakeRegions` + `RegionInfo` 5-arg canonical (plotFlag, houseFlag, farmFlag) works in a world with no plots.
- `YamlFiles.toMap(read.getConfigurationSection("farms"))` round-trips `FarmCodec.toMap` through `saveToString`/`loadFromString`.
- `paper-plugin.yml` lookup `isConfigurationSection("permissions.cookieclicker.teleport")` works with a dotted permission name (same as cookieempire.admin).

**Open at 7fc2604 (sent to the coder; fix round, then re-review `git diff 7fc2604..HEAD`)**
- MEDIUM: countryside box can start outside the region's min x/z. Probe (deleted): origin `[2,0,0]` → plan accepted, box minX 98 vs regionMin 100, `neededSize` gives [10,3,8] which can never fit. Fix: refuse in `FarmPlan.plan` (regression: `FarmPlanTest`, origin [2,0,0] → plan null).
- MEDIUM: `ChunkHold` tickets not released when `writeBackup` fails (IO branch calls `ops.finish` with no release) and when `finishPaste` throws (release skipped). Tickets pin ~80 chunks until the next farm command or restart. Fix: release on the main hop of every failure branch; regression: a `World` Proxy counting add/remove on `ChunkHold`.
- PERF-MEDIUM (brief acceptance: no long stalls): snapshot/restore `do { } while (budget > 0)` always runs one slab (16x16xH, ~12.8k blocks at H 50) and budget = blocksPerTick x 4, so ~2 slabs per tick (fill + gzip NBT, or gunzip + place) on main. Measure with spark during `/bakery farm setup` before the user gate.
- LOW: `HouseDesignParser` skips any Map-valued point for house designs (typo `door: {at: ..}` no longer reported). `FarmTeleports` starts the cooldown before the teleport resolves. `FarmPlan` message says "below the countryside's base" but checks regionMin. Setup needs the whole box loaded ("stand in the middle") and the 30 s `PlotRegistry` paste-area cache can lag a new flag.
- PERF-LOW: `pasteAreasOf` now scans `regions(world)` every 30 s in any world with a flow (cheap); `FarmSetup.check` runs `cellCount()` on main once (~1M cells at 150x120x50); `paste-blocks-per-tick` 5000 is ~3x the house paste per tick (spark to confirm).

**Verified OK in this review:** Gate (hold/step/submit/finish, stale tokens stop the task and release chunks), reload/disable (`reset` cancels, undo restores from the backup written before the first block change), root command permissions (teleport-only sees farm/home), paste cell maths vs `HouseDesign.toWorld`, codec and backup slabs, PlotFile serialises on main and writes on IO.

Related: [[reviews-ledger]], [[testing-recipes]].

**Re-review at fix round 1 (2026-10-25)**: coder branch 7fc2604..dd016b7 merged into tester branch; tests at e747f3c (439 green). MEDIUM-1 (origin refused, `FarmPlanTest`), MEDIUM-2 (`failFromIo` + try/finally, `ChunkHoldTest`), PERF-MEDIUM (blocks-per-tick budget, slabs split by y, FarmBackupTest budget test) fixed. VERDICT READY with follow-ups:
- Threading of `BoxStructures.serialize/deserialize` (`saveStructure`/`loadStructure` on the IO thread): UNKNOWABLE here. The API sources jar (`~/.m2/.../paper-api-26.3.build.205-stable-sources.jar`) has no threading text on `StructureManager`/`Structure`, and the server implementation (`CraftStructureManager`) is not in the local ~/.m2 (only paper-api and paperlib). Ask for main-thread serialization (cheap at the block budget) unless a Paper source reference proves it safe.
- Undo deserializes every slab uncompressed up front on IO (`FarmSetup.undo`): ~0.8M blocks at the guideline region, transient, unmeasured. Follow-up: deserialize one slab per restore tick.
- `FarmTeleports`: cooldown now starts on success, so repeated `/bakery farm` calls while one is in flight queue several teleports (LOW).
- Single-thread IO executor (`PluginExecutors` newSingleThreadExecutor) makes the serialize-then-submit ordering safe; keep it single-threaded if this code relies on it.
