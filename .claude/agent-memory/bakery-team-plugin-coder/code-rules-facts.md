---
name: code-rules-facts
description: Non-obvious rules of this codebase after tidy (2026-10-23) - tester pins, contracts from designers, engine behaviour, review lessons
metadata:
  type: project
---

Checked against the tidy layout (feature/tidy, 2026-10-23). Package map: `docs/architecture.md`.

**Tests pin things (src/test is Gandalf's; list needed changes in the report)**
- `text/MessagesTest` scans every `"plot.x"`/`"house.x"`/`"panel.x"`… literal in src/main/java and fails if it isn't in
  messages.yml: never build message paths by concatenation; config paths with those prefixes use a relative section.
- Records/ctors that tests build by arity: keep a compat ctor when adding components (Plot, Station, HouseBackup,
  PouchSnapshot Set-ctor, BuildingDef 4-arg, StationView 9-arg, Row 5-arg, Panel 8-arg, ToggleBox 2-arg).
- `ArchitectureRulesTest` pins hard rules (`com.sk89q` only in worldguard, `spawnParticle` only in vfx, `playSound`
  only in sound, cinematic classes never modify the world, ...); a change that needs a new exception fails it until Gandalf updates it.
- Static reference helpers kept only for tests: `PanelText.buildingRow(Messages, ...)` (PanelUpdaterTest, PanelTextTest,
  TidyPerfTest), `Economy.nextBuildingCps` (RowStateTest). Don't delete them.
- Config-validation tests count problems exactly: a new optional key must not report when absent.

**Designer contracts**
- Layout yaw (design/factory.md): layout `pedestal/shop.yaw` = the way the thing faces; `Station.yaw` = layout yaw + 180.
- Layout cell (0,0,0) in the world = `plot.house()` min - `layout.houseAt` + `design.origin` (`building/BuildingLayout.origin`).
- Belt (design/conveyor-belt.md): surface display at (cell+0.5, y0+h, cell+0.5), NONE, cookies ride +0.0625; camera `at` is a feet cell (eye +1.62).
- Cookie model (design/cookie.md): upright, faces along z; flat = rotateY(travel).rotateX(90deg). Basket heights: design/basket.md.
- Loop sound periods: ambient 80, crackle 60 (`house.chimney.crackle-ticks`), belt 100; all through `SoundService.playLoop`
  / `loopDelay` (`sound/LoopPacer` keeps the beat per player+event). Plot ambience uses `sound/LoopPlayer` (one task); the belt polls inside its ride task.

**Engine behaviour**
- `EffectTicker.start(job)` runs the first step synchronously (a pop's step 0 happens inside the click handler).
- `Effects.play` may keep the Location for multi-tick jobs: pass a fresh Location per call.
- `vfx.PrivateDisplays.spawn` adds the `cookieclicker:vfx` PDC key and full brightness. `Effects.sweepLeftovers` (every
  effects load, i.e. `/bakery reload`) and `VfxListener.onEntitiesLoad` REMOVE any untracked entity with that key, so
  station/belt/building displays must not use it (they keep their own spawns; station key `cookieclicker:station`).
- `Effects.loop(id, at, viewer, startDelay, onRepeat)`: a delayed loop counts against `max-loops-per-player` from the start.
- `CookiePouch.revision()` bumps on every building/upgrade level added: the cheap "CPS inputs changed" key (belt uses it).
- `PlotRegistry.mayContainPlot` caches per-world bounds for `BOUNDS_TTL_MILLIS` (30 s), cleared on put/replaceAll.
- javac (Java 25) refuses a final field used in a lambda inside the constructor that assigns it: use a method ref.

**Review lessons (Gandalf)**
- "Grow until it fits" loops must terminate below the shape's minimum; write undo backups before changing the world.
- Async multi-step world work uses a token (`house/setup/OperationGate`: begin/valid/end(token)/reset), checked first in
  every main-thread continuation; reload/disable invalidates in-flight IO callbacks.
- Storage migrations: DB type "mysql" uses the MariaDB driver against possibly real MySQL, so no `ADD COLUMN IF NOT EXISTS`;
  `addColumn` checks DatabaseMetaData. In H2, DEFAULT must come before NOT NULL.
