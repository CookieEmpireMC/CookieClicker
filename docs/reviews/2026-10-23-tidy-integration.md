# Tidy: integration review (and review of `tidy/plugin-coder-cleanup`)

Gandalf · 2026-10-23 · branch `tidy/plugin-tester-integration` (feature/tidy `b40b6c1` + `tidy/plugin-coder-cleanup` merged) · Paper 26.3.build.205-stable, Java 25

**VERDICT: NOT READY** until one MEDIUM is fixed (below, a one-line change in `LoopPlayer`; re-review = read that diff, no new tests needed). Everything else is READY: no HIGH, no PERF-HIGH.

## Results
| Check | Result |
|---|---|
| `mise run check-quiet` | green, **392 tests** (390 + 2 added here; baseline before tidy ~686) |
| `validate-assets` | 93 files, 0 problems |
| `sound-check` | 28 sounds, 0 failed |
| `balance-check` | 19/19 targets |
| `tools-test` | 73 tests, OK (1 env skip; baseline 146) |
| `TidyPerfTest` | 3 green, numbers in `2026-10-23-tidy-perf.md` (summary table there) |
| Package cycles | one strongly connected group, see LOW-1 |

## Cleanup branch review (`tidy/plugin-coder-cleanup`)
Changed: `BeltRider` (CPS key), `PlotRegistry` (bounds TTL), new `sound/LoopPlayer`, `PlotAmbience`.

- **`BeltRider.cpsChanged` on `CookiePouch.revision()`: OK.** `addBuilding` and `addUpgradeLevels` are the only mutators of owned/upgrades (grep: `owned.put`/`upgrades.putAll` appear only in `fromSnapshot`, a new pouch, caught by the `pouch ==` identity check); balance reload is caught by `config ==`. No other CPS input exists yet (stars/level/crowns come later and must bump `revision` or extend the key; note for those features). Closes the ui review's LOW "revision unused".
- **Bounds TTL: OK.** `now - readAt >= 30 000`, monotonic `nanoTime/1e6`, `replaceAll`/`put` still clear. Tests added (fake clock): stale box refreshes at exactly the TTL without a `put`, not one ms before; an empty box recovers when the region is flagged later. Closes the world review's two LOWs.
- **Steam via `Effects.loop(..., startDelay)`: OK.** `LoopJob` applies `startDelay` to age, checks viewer online and world on every play tick, ends with `cancel()`. Delayed loops now count toward `max-loops-per-player` from the start (was: only once started); with the shipped 2 smoke + steam points this is nowhere near the cap. Accepted.
- **`LoopPlayer`: beat equals the old timers in every case except one.**
  - No doubles: restart after leave/enter, after the cinematic, after `/bakery reload` (`stopAll` then `start`): `start` takes its first due from `SoundService.loopDelay` (the `LoopPacer`), and `playLoop` still goes through `tryPlay`, so a second play inside one period is refused as before. A cancelled loop is removed at the top of the next `tick()` before anything is played.
  - Skip-a-beat when the player is away or out of range: the `when` supplier is checked on every due tick, `due` still advances. Same as the old timers.
  - Idle stop/restart: the task cancels itself when the list is empty and `start` creates it again; `stopAll` cancels it. No re-entrancy into `loops` from `when`/`playLoop`.
  - Cost: one 1-tick task while any loop exists, per tick one `removeIf` and a long compare per loop (2 per player in a plot). Cheaper than a task per player per sound.
  - **MEDIUM-1 (finding below): first beat after a fresh start.**

### MEDIUM-1 `LoopPlayer.tick`: the beat drifts one tick when a loop is started with delay 0 (`sound/LoopPlayer.java:84-92`)
`start` sets `due = getCurrentTick() + loopDelay`. When `loopDelay` is 0 (first time on this server, or the last play ended long ago) and the shared task first runs one tick later (an event handler runs after the scheduler's heartbeat, so `runTaskTimer(.., 0, 1)` fires at N+1), the first play happens at N+1 but `due` advances from N: `due += period * ((now - due)/period + 1)` = N+period. At N+period the pacer says `now < before + period` (before = N+1) and **refuses**; the next due is N+2*period. Result: after the first play the loop is silent for about 2 periods minus 1 tick instead of 1 period, i.e. one beat of `house_chimney_crackle` / `factory_ambient` is missing right after the first plot entry of a session. The old per-player timer fired every `period` from its real first run, so it had no gap. (Also visible only on a fresh start; a restart with a pending pacer delay fires exactly on its tick.)
Fix (one line, in `tick()`): after a due tick set `l.due = now + l.period;` (no catch-up arithmetic needed, the task runs every tick). Optional: give `LoopPlayer` a `LongSupplier` tick source so this is unit-testable without a server; I could not add a test because `Bukkit.getCurrentTick()`/the scheduler need one.

## Integration: acceptance criteria (brief `docs/briefs/tidy.md`)
| Criterion | State | Evidence / why |
|---|---|---|
| Comments removed/kept per rule (Java, tests, YAML) | done | spot-checks over the tree: no history narration left in `src/main` (4 grep hits are real behaviour text); comment/Javadoc lines are 12 % of `src/main/java` (21 377 lines), 2 % of `src/test/java`; YAML headers hold only admin-facing notes. Old-version fixture `config-1.2.1.yml` keeps old comments on purpose |
| Memory pruned per agent | done | coder 13 files/301 lines -> 4/152, vfx-designer 4/96, tester 26 files/488 lines -> 5/97, designers 2-5 small files. The CookieMemory PR is the orchestrator's task (open) |
| Tests: important only | done | 686 -> 392 Java (vfx included), 146 -> 73 tools; kept: economy/golden, JDBC round trips and schema, shipped-file/ID/asset contracts, ArchitectureRulesTest, descriptor/permission, geometry pins, safety paths |
| `config.yml`: resource-pack, database first | done | 79 lines |
| Unread keys removed, defaults in code | done | config review (415 tests then) + `PluginSettingsOldConfigTest` (value-equal old vs new) |
| Internal knobs to code, same values | done | config review; `VisualFallbacks`, `*Settings` records |
| Under ~100 lines | done | 79 |
| Old configs load without errors | done | `PluginSettingsOldConfigTest` with `config-1.2.1.yml` |
| Standards section in `docs/architecture.md` | done | "Standards" section; this review adds "Known package cycle" |
| Classes over ~500 lines split | done | largest `HouseCinematics` 489; `StationViews` 439, `Effects` 407. `Effects` still has 17 fields (vfx LOW-3) |
| Duplication/naming/package findings fixed | partly | `MarkerTemplate`, `WarnOnce`, `TextWidth`/`TextPadding` -> `text/`, panel types -> `view/panel`, house -> `design/setup/cinematic` all landed. **Not done:** per-owner display spawning is still written four times (`Spawner`, `StationViews`/`StationSpawner`, `ConveyorBelts`, `VisibleBuildings`); `PrivateDisplays` adoption skipped by the coder (its vfx key would be swept on reload and delete station/belt/building entities; `Interaction` unsupported) -> follow-up |
| Behaviour-preserving, checks green, formats compatible | partly | all checks green, formats unchanged; MEDIUM-1 is a (small) behaviour change in the ambient loop |
| PERF findings listed, HIGH/MEDIUM fixed | done | review `tidy-architecture.md`: no HIGH; MEDIUM U1/V1/W1/U3 fixed and measured; LOWs below |
| Repeating tasks have a reason, idle ones stop | done | about 12 timer tasks now (was ~19): belt ride, `EffectTicker`, `StationViews` animation and `LoopPlayer` start on demand and stop themselves; `PedestalPrivacy` is off by default |
| Before/after measurement | done | `tidy-perf.md` incl. summary table; row text ~9x, floating number ~35x, no regressions |
| `paper-plugin.yml` + `PluginLoader`/`MavenLibraryResolver` on Paper's mirror, no `plugin.yml` | done (needs a real start) | `CookieClickerLoader` uses `MAVEN_CENTRAL_DEFAULT_MIRROR`; `PluginDescriptorTest` pins coordinates, fields and permission. WorldGuard `load: BEFORE`, `join-classpath: true`. Class-loader reachability read from Paper source, not run on a server -> human check |
| Async only with a spark profile | done | no `runTaskAsynchronously` / async scheduler in `src/main`; DB/file I/O stay on `PluginExecutors` |
| Version 1.2.2 | done | `pom.xml` |
| Shared-skill (CookieMemory) PR | open | orchestrator, after this review is READY |

## Findings by severity
**HIGH:** none. **PERF-HIGH:** none.
**MEDIUM:** MEDIUM-1 above (owner: plugin-coder; one line).
**LOW (PR follow-ups, no fix round):**
- LOW-1 Package cycle: `config.BeltSettings` imports `belt.BeltCurve` and `config.PluginSettings` imports `storage.DatabaseSettings`, while `belt`/`plot`/`view` import `config`; `plot -> station -> config -> belt -> plot` is a cycle. Move `BeltCurve` and `DatabaseSettings` into `config`, then add an ArchitectureRulesTest rule "config imports no feature package".
- LOW-2 `PrivateDisplays` adoption and the four copies of per-owner display spawning (vfx follow-up; design needs a registry that the reload sweep respects, and `Interaction`).
- LOW-3 `LoopPlayer`/`PlotAmbience` have no unit test (needs a server clock); extract the due-time rule if it grows.
- LOW-4 `PlotRegistry` bounds re-read: `regions(world)` is called on the main thread every 30 s per world on the next block change; fine, but a very large region set would show it in spark.

## Merged follow-up list (all part reviews, LOWs only)
Config: intermediate commit `1579519` deletes `plugin.yml` before `paper-plugin.yml` exists (do not bisect through it); `required: true` makes the `plot.missing-worldguard` branch in `onEnable` nearly unreachable (harmless); `WarnOnce` keys are never evicted until `clear()` (never key by player name or coordinates). Resolved since: unused `MarkerTemplate`/`WarnOnce` (both used now), stale `plugin.yml libraries` and `station.panel.colors` text (gone).
UI: static `PanelText.buildingRow` exists only as the test reference; entity heal now every 5th refresh (visibility fixed up to 5 x `refresh-ticks` later); ambiguous-commit edge in the JDBC save skip (commit succeeded but the driver threw leaves an older `lastSaved`, next save just writes again); `sendActionBar` per click (PERF-U8; coalesce over 2 ticks only if spark shows packet cost). Resolved: `CookiePouch.revision()` now used.
Vfx: DropJob callback tests trimmed (2 of 3 not restored); overload-signature guard removed (hand-verified); `Effects` 17 fields and `BlockDrops`/`Loops` back-reference to `Effects` (small `EffectsHost` interface); `DisplayJob.nextKeyframe` is `List<int[]>`; `DisplayTrack` tick order not enforced (add a check in the compact constructor); `cookie_crit` (gradient) parses on every crit; V5 packets per looped particle, V6 `Throttle.forget`. Intended changes: a malformed floating text skips only its layer; a loop ends at its next play tick after the viewer leaves.
World: `ConveyorBelts` ride-task start/stop is untested (profile with spark); a flag naming a plot of a world without plots is ignored (unsupported setup); `HouseCinematics.tick` copies `running.values()` per tick while a run exists (PERF-W6). Resolved by the TTL: stale bounds until reload, empty box cached.
Earlier features still open (from their reviews): in-game check of the panel text vertical anchor; v2 -> v3 ALTER idempotence (use `ADD COLUMN IF NOT EXISTS`); building-roster: stage thresholds duplicate milestone numbers, no retry for a spawn in an unloaded chunk, 0.81 vs 0.82 footprint; viewpoint -> shop panel is 6.4 > `click-distance` 6.

## In-game smoke test list (for the PR)
1. First start on Paper 26.3: the plugin loads from `paper-plugin.yml`, the H2 and MariaDB drivers download through the mirror (first start only), WorldGuard flags `bakery-plot` / `bakery-house` register, `/bakery` and `cookieempire.admin` work.
2. Old `config.yml`: start with the previous file; no errors or warnings about unknown keys; then compare with the new default and copy url/sha1/database.
3. Enter the plot for the first time in a session: house cinematic, chimney smoke, steam vents staggered, factory hum and chimney crackle audible **without a missing beat** (MEDIUM-1: listen for a gap of ~2 periods after the first play); leave and re-enter within a few seconds: no doubled sound; `/bakery reload` while standing in the plot: ambience continues, no doubling.
4. Click the cookie 15/s, switch the buildings panel ×1/×10/Max, buy buildings and upgrades: rows roll, numbers floating text and HUD look identical to 1.2.1; belt speeds up after a purchase (CPS change key).
5. Walk to the edge of the plot and out: tracker still detects entry (also after `/rg flag` of a new region, within 30 s).
6. spark: `/spark profiler start --timeout 60` with 5+ players, two clicking in Max mode. Good: plugin under ~2 % of the tick; no `MiniMessage.deserialize` under `PanelUpdater.render`; `ConveyorBelts.rideTick` and `LoopPlayer.tick` absent with nobody in a plot; `WorldGuardHook.at` absent outside the plot region.
7. Autosave with several players: no main-thread stall (IO thread `CookieClicker-IO` only).

## Memory
`.claude/agent-memory/bakery-team-plugin-tester/`: 26 files / 488 lines -> 5 files / 97 lines (index + testing recipes, repo facts, test rule, review ledger).
