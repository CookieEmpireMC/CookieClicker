# conveyor-belt: plugin-coder branch review (2026-10-21)

Reviewer: Gandalf. Branch `conveyor-belt/plugin-coder` at `8da4215` (+ the tests in this commit). Target: Paper 26.3 (`26.3.build.205-stable`), Java 25. No `pom.xml`, `mise.toml` or `plugin.yml` changes on the branch.

## Tests
`mise run check` green: 554 tests, 0 failures, 1 skipped (the skip is `BeltStyleTest.theShippedBeltFileParsesWhenPresent`, which needs Pat's `houses/belt.yml`; it runs at integration). New tests (68):
- `belt/BeltCurveTest`, `BeltPathTest`, `BeltLaneTest`: idle at 0/negative/NaN CPS, ranges and cap, monotonic, full speed at `cps-scale`, cap of cookies, no overtaking while CPS ramps, a purchase shortens the gap at once, straight/level cell lists, single-cell direction, broken belts give no path.
- `house/BeltStyleTest` (parse, middle cycle, every problem collected, `none()`), `HouseLayoutCameraTest` (camera outside/above the plot, defaults, clamp, 12 malformed shapes, belt shape/length check), `CameraReturnTest` (round trip, every game mode, 15 bad inputs -> null), `HouseCinematicsCameraSourceTest` (9 source-level guards: every `running.remove` is in `complete`/`abort`, both end the camera, every abort path goes through `abort`, PDC written before the mode change and removed after the restore, join/disable nets, only `CinematicCamera` calls `setGameMode`).
- `plot/PlotCodecBeltCameraTest` (YAML round trip, negatives, old files, bad belt/camera dropped with a problem, derived plots keep belt/camera), `config/BeltSettingsTest` (defaults incl. `sound-every` 100, clamps, inverted ranges, bad scale, garbage types, shipped `config.yml` == code defaults, `house.cinematic.camera` default true).
- Run against the feature branch's shipped `belt.yml`, `factory.yml`, `sounds.yml`, `effects.yml` copied in temporarily (then removed): everything passes except two vfx tests that need Larry's `min-interval-ticks` engine support (`EffectsConfigParserTest.shippedEffectsLoadWithoutProblems`, `HouseEffectsConfigTest.everyContractPresetShipsWithoutProblems`); they go green once the vfx branch code is in (it is on the feature branch). Integration must confirm.
Not unit-testable without a server (needs MockBukkit, absent): the actual spectator/entity behaviour; covered by the source guards and the in-game list below.

## Findings
No blockers.

1. LOW `house/HouseCinematics.java` `complete()`: `camera.exit(p, true, true)` is not wrapped in `guarded(...)` like the abort path. If it threw after `running.remove`, `abort` would find no run and nothing would retry (PDC marker still lets the next join fix it). Wrap it, or call it inside a try that falls back to `abort`.
2. LOW `CinematicCamera` `onLost` callback: `HouseCinematics` ignores a `false` from `abort(id, true)`. A shot with no run (only possible if something throws between `camera.enter` and `running.put`, e.g. `smoke.stop`) leaves the player in spectator until quit. Suggest `if (!abort(id, true)) camera.exit(p, true, true)`, and a try/catch around the code between `enter` and `running.put`.
3. LOW `CinematicCamera.location(...)`: if the stored world key no longer exists, the player gets their mode back but is not moved (stays at the camera point in the air). Fall back to the world spawn.
4. LOW (unverified, in-game): an `ItemDisplay` as spectator target and `setSpectatorTarget` one tick after enter are design assumptions (Wesly's memory says so). If the camera does not lock, the tick re-target loop retries every tick and spams nothing, but the player would sit at the camera point. Check in game.
5. PERF-LOW `ConveyorBelts.tick`: `List.copyOf(riders.entrySet())` allocates a list every tick (use the map iterator and collect removals); `refresh` (every 10 ticks) recomputes `economy.totalCps` per player and walks `world.getPlayers()` per belt plot with `hasPack` per player.
6. PERF-LOW surfaces are spawned for every plot with a belt even when nobody is in it (10 hidden displays per plot, bounded by loaded chunks). Fine at normal plot counts; spawn only while a player is in the plot if plots grow into the hundreds.

## Checked, no problem
- Game mode safety: PDC marker (mode + return spot) written first, removed only after the mode is restored; exits on completion, quit/kick (`abort(id,false)` sets the mode while the player data is still unsaved; the spot is applied on the next join), death, world change, any real teleport (own mode kept, no teleport back), `/bakery reload` (`abortAll`), disable (`shutdown` -> `abortAll` + `exitAll`), step exceptions, another plugin/admin changing the mode (their mode kept, the build aborted, player sent back). `onJoin` handles a crash/kill (marker present: mode + spot restored; unparsable marker: removed and spectator reset). Own mode changes are not mistaken for foreign ones (`changing` set); own teleports use `selfTeleport`. Spectator menu teleports and sneaking out are cancelled. `seen` is stored before the camera brings the player back into the plot so the return does not start a second cinematic.
- Threading: everything main thread; belt.yml read on the IO thread through the existing `Inputs` path; no blocking I/O added.
- Entities: every belt/camera display `setPersistent(false)`, `setVisibleByDefault(false)`, tagged `StationViews.TAG`; removed on `plotChanged`, `restart`, `shutdown`, owner quit/world change, chunk unload (`isValid` check respawns the surface, drops the ride).
- Visibility: cookies and the camera are shown only to their owner; the surface shows each player one of two sets (pack/vanilla) and re-sends only when the pack state changes.
- Sound/effects: only through `SoundService` and `Effects`; no `playSound`/`spawnParticle`; `ArchitectureRulesTest` green. Defaults: `belt.sound-every` 100, `belt_loop` pitch is fixed 0.8 in Bruno's `sounds.yml` and the code never changes pitch.
- No balance numbers in Java; belt numbers are visual settings in `config.yml`; the belt never touches the economy.
- Version: Paper 26.3 API only (`DataComponentTypes.ITEM_MODEL`, `PlayerStopSpectatingEntityEvent`, `Math.clamp`), no NMS/reflection, nothing shaded.

## Performance
Per tick: one task; for each rider (online player standing in a belt plot, at most the number of players in plots): one map lookup, walking at most `max-cookies` (8) rides, at most one spawn and one sound check. No Component building, no per-tick entity update: a cookie costs one spawn, one `setTransformation`, one remove, and one `belt_arrive` effect (rate-capped by the effect's `min-interval-ticks`). 4 players at max rate (interval 8 ticks, 2.5 b/s): about 5 cookies live per player, 1 spawn per player per 8 ticks, 20 cookie entities total: estimated well under 0.1 ms/tick. Every 10 ticks: per belt plot a `healthy()` stream over 10 displays and a pass over `world.getPlayers()`, per player one `totalCps`: estimated < 0.1 ms. Camera: one 1-tick task only while someone is in the camera. Confirm with spark.

Spark steps for the PR: `/spark profiler start --timeout 60` with 4 players standing in their plots at high CPS (buy buildings to pass `cps-scale` 1000), then look at the CookieClicker share in the tick: good = below about 1% of a 50 ms tick (0.5 ms), with `ConveyorBelts.tick` a small part of it; also watch the entity count (`/spark tps`, or `/minecraft:execute ... ` entity count) staying about 10 per belt plot plus 8 per rider.

## In-game checks for the human
Camera lock and framing; back at the gate in the right game mode (try survival and creative); quit mid-build and rejoin (marker restored); `/gamemode` mid-build; `/bakery reload` mid-build; two players in one plot see only their own cookies; belt look with and without the pack; slats move toward the pedestal (else `belt.surface-yaw: 180`).

VERDICT: READY (the LOW items may be fixed in the cleanup pass; none blocks).
