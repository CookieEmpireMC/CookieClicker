# conveyor-belt: integration review (2026-10-21, Gandalf)

Branch `feature/conveyor-belt` at 0c5e1aa (all 5 agent branches merged). Target: Paper 26.3 (Java 25).

## Checks (all run for real)
| Check | Result |
|---|---|
| `mise run check` | green, 576 tests, 0 failures, 0 skipped, shaded jar built |
| `validate-assets` | 15 files, 0 problems |
| `sound-check` | 19 sounds, 0 failed |
| `balance-check` | 6/6 pass |
| `tools-test` | 131 tests OK (1 skipped, unrelated to this feature) |

Previously open integration items are closed: `BeltStyleTest` shipped-file test runs (9 run, 0 skipped) against Pat's `houses/belt.yml`; `EffectsConfigParserTest` (16) and `HouseEffectsConfigTest` (11) are green with Larry's `min-interval-ticks` engine.

## Contract IDs
| ID | Exists | Used |
|---|---|---|
| `cookieclicker:conveyor_belt` (items/, models/item/) | yes | `ConveyorBelts.BELT_MODEL`, `belt.yml item-model` |
| `cookieclicker:cookie` | yes | belt cookies |
| `houses/belt.yml` | yes (generated) | `BeltStyle` parser, shipped-file test |
| `layout.camera` in `factory.yml` | yes (`at [23,12,-13], yaw 20, pitch 24`) | `HouseLayout`, `Plot.camera`, `CinematicCamera` |
| `belt_loop` (sounds.yml, key `cookieclicker:factory.belt_loop`) | yes | `ConveyorBelts.SOUND_LOOP` via `SoundService` |
| `belt_arrive` (effects.yml, `min-interval-ticks: 4`) | yes | `ConveyorBelts.EFFECT_ARRIVE` via `Effects.play` |
| `belt.*` config (enabled, max-cookies, cookie-scale, speed, interval, cps-scale, sound-every, fallback-item, surface-yaw) | yes | `BeltSettings` |
| `house.cinematic.camera` | yes (default true) | `HouseSettings` |
| panel `text-left-edge` | yes (default true) | `StationVisualSettings`, `TextWidth`, `StationViews` |
| plots.yml `belt`, `camera` | yes | `PlotCodecBeltCameraTest` |

No `playSound` outside `sound/`, no `spawnParticle` outside `vfx/` (ArchitectureRulesTest green). No balance numbers in code: belt curve numbers live in `config.yml` as visual settings. The economy is only read (`totalCps`), never written.

## Acceptance criteria
- Belt placement/undo/backup: covered by the belt style, setup-backup and plot codec tests. Real placement needs a live server (see checklist).
- Moving surface + vanilla fallback (`BLACK_CARPET`): in code and config; look needs a live check.
- Per-player cookies, one interpolated transformation per ride, arrive effect, cap `max-cookies` (BeltLane tests), 0 CPS = idle (BeltCurve tests): pass.
- Belts only for players in the plot and not in their cinematic: in `refresh()` (`eligible`).
- Camera: parsing, PDC return record, every abort path (source-level guards and `CameraReturnTest`). `camera:false` and layouts without a camera (default cottage) keep the old flow. A live check is still needed (no MockBukkit in the pom, so spectator behaviour is verified at source level only).
- Panel left edge: `TextWidth`, left-edge shift and defaults tested.

## Bugs
None blocking. Open LOWs from the coder review, unchanged:
1. `CinematicCamera.complete()`: `camera.exit` is unguarded; a throw there would skip later steps. The join and disable nets still restore the player.
2. `onLost` ignores `abort() == false`.
3. Missing return world on restore is not handled specially.
4. `ConveyorBelts.plotChanged/removeAll` copy the key list (rare paths, fine). The earlier per-tick `List.copyOf` in `tick()` is gone: the tick now iterates directly.
5. Surfaces are built for plots with a `belt` only. Plots whose setup predates the belt show nothing until `/bakery plot setup` is re-run (expected).

## Performance
Hot paths:
- `ConveyorBelts.tick` (every tick): one iterator over riders (players in a plot), `Bukkit.getPlayer` per rider, up to `max-cookies` (8) rides each. Per rider per tick, roughly 8 `isValid` checks plus a spawn decision, which is O(1) arithmetic. 4 players: about 32 ride checks and 4 spawn checks per tick, no allocation except an occasional spawn. The `Location` for the sound is built once every 100 ticks per rider.
- Entity updates: one `setTransformation` per cookie per ride (about 1 per 8 to 80 ticks per player), none per tick.
- `refresh()` (every 10 ticks): `getOnlinePlayers` pass for `hasPack`, a loop over plots, `world.getPlayers`, show/hide only on a pack change, a CPS recompute only when the pouch changed. About O(players + plots) every half second. Fine.
- Entity budget: 5 cells x 2 sets (pack and vanilla) = 10 shared displays per plot, plus at most 8 cookies per player: 4 players = 10 + 32 = 42 entities.
- Effects: `belt_arrive` is capped at 5 plays/s per viewer, 17 particles per play, 85 particles/s per viewer at most, no entities. At max curve speed the belt makes 2.5 arrivals/s per player (min interval 8 ticks), so the cap is not even reached.
- Sound: one packet per player per 100 ticks, only within the ambient radius.
- No blocking I/O on the main thread in the belt or camera code.

Estimate: well under 0.1 ms/tick for 4 players at high CPS; the 1 ms/tick target is met with a wide margin. No PERF-HIGH, MEDIUM or LOW findings.

## Architecture
`docs/architecture.md` already describes `belt`, camera and the belt rule (updated at the coder review); no change needed. `ConveyorBelts` is 520 lines but holds a single concern. Pure logic (curve, lane, path, style) is split out and unit-tested. Nothing is held per player after quit or reload.

## Spark steps (for the PR)
1. Stand in the plot with 4 players, buy buildings until CPS is at least `belt.cps-scale` (1000) so the belt runs at full speed.
2. `/spark profiler start --timeout 60`, keep clicking for the whole minute, then open the report.
3. Search the tree for `ConveyorBelts.tick` and `CookieClicker`. Good: the plugin's total share is below 2% of the tick, `ConveyorBelts.tick` is a small fraction of that, and MSPT stays near its idle value.
4. Repeat once with `belt.max-cookies: 0` to compare. The difference should be negligible.

## In-game test checklist
- [ ] `/bakery plot setup` places 5 belt cells from the hatch to the cell before the pedestal; `setup undo` restores the world exactly.
- [ ] With the pack: slats move toward the pedestal (if they run backwards, set `belt.surface-yaw: 180`). Without the pack: a black carpet belt shows.
- [ ] 0 CPS: no cookies. A few buildings: a cookie now and then, slow. High CPS: dense, fast cookies; max 8 on the belt.
- [ ] A second player on the same plot sees the belt with their own cookies only.
- [ ] The crumb puff and pop show when a cookie lands; the belt hum is audible nearby and quieter than the oven.
- [ ] First visit: the view lifts to the raised camera and shows the whole factory, belt, pedestal and fence; you can't move or interact; afterwards you are back in your original game mode (also from creative) at the gate.
- [ ] Quit, die or `/bakery reload` mid-build: no stuck spectator mode. Kill the server mid-build, then rejoin: restored.
- [ ] `house.cinematic.camera: false` and the default cottage: the old ground-level cinematic.
- [ ] Shop panel rows: every row's text starts at the same edge right of its icon, also for long and wrapped rows, with and without the pack.
- [ ] Human: listen to `belt_loop` looping with the oven hum.

VERDICT: READY
MERGE ORDER: none open; all branches merged.
