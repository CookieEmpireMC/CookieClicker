# Integration review: starter-house

Reviewer: Gandalf. Feature branch state = `starter-house/plugin-coder-2` at b03c8e3 + Gandalf's tests (all other agent branches merged in 56fb760). Date 2026-10-19. Diff reviewed: `main..HEAD`. Earlier branch reviews (sounds 319fdff, house design b44bfa3, vfx 2bcf65a + outline, coder pass 1 up to fix round 2) were not re-read line by line; they were re-checked only at the integration level, per the ledger. Pass 2 details: `docs/reviews/2026-10-19-starter-house-pass2.md`.

**VERDICT: READY** (feature complete against the brief; no blocking findings).
**MERGE ORDER:** `starter-house/plugin-coder-2` is the last open branch: merge it into `feature/starter-house` with `--no-ff` (ownership OK, 5 commits), then open the PR. No other `starter-house/*` branch is open.

## Checks (all green on this tree)
`mise run check` (435 tests), `validate-assets` (0 problems), `sound-check` (17 sounds, 0 failed), `balance-check` (6/6; no economy change), `tools-test` (146), `ownership-check starter-house/plugin-coder-2 --base feature/starter-house` (OK). Build settings unchanged (Java 25, `release` 25, `api-version 26.3`, pinned `-stable` paper-api). WorldGuard/WorldEdit `provided`, not shaded, `depend: [WorldGuard]`. One deprecation warning in the whole build (`Sound#key()`, see findings).

## Contract IDs
- Sound events in code (`house_block_land`, `house_hammer`, `house_block_place` `@block`, `house_fanfare`, `house_chimney_crackle`): all in `sounds.yml`, `sounds.json` and the pack (sound-check green, `design/sounds.md`).
- Effect IDs in code (`house_foundation_dust`, `house_sparkle`, `house_confetti`, `house_chimney_smoke`, `house_block_drop` and the engine's `house_block_land`, `plot_outline`, `house_outline`): all in `effects.yml`. No sound or particle call outside `SoundService` / `vfx` (architecture test).
- `Effects.dropBlock(viewer, data, target, onImpact, onLanded)`, `loop(id, loc, viewer, onRepeat)`, `cancelFor`, `EffectHandle` used as specified. Cap `max-falling-displays` clamped to the engine's 24.
- Config keys: brief keys plus the final ones from the coder's report (`house.cinematic.{viewpoint-radius, hammer-every, slow-amount, confetti-height}`, `house.chimney.{smoke, crackle-every}`, `blocks-per-tick` double); `HouseSettingsTest` proves `config.yml` equals the code defaults. Messages `house.cinematic.title/subtitle`, `house.rebuild-*`, `plot.*`, `plot.missing-worldguard` exist and parse (MessagesTest). Storage `house_seen` schema v2 present. Command tree has `plot setviewpoint|setpedestal|setchimney|show`, `house place|undo|rebuild <player>`.

## Acceptance criteria -> where
| Criterion | Evidence |
|---|---|
| Research before code | `docs/research/worldguard-plot.md`, `fake-blocks.md` (merged first) |
| Regions via WG, no selection commands, multi-plot model | `worldguard/`, `plot/` (PlotResolver tests, PlotCodecTest) |
| setviewpoint/setpedestal, migration (Decision 2) | `BakeryCommand`, `StationMigration` + test |
| `/bakery plot show` incl. polygons | `Outline`, `OutlineTest` (hang fixed round 1), `RegionShapeTest` |
| `house place` with backup, `undo` exact; default design | `HousePlacer`, `HouseBackupTest`, `OperationGateTest`, `HouseDesignParserTest`, `tools/test_house_design.py` |
| Hand-built house works | house region = house; chimney via `setchimney` |
| Region checks only on block change | `PlotTracker.onMove` guard + `ArchitectureRulesTest` |
| integrations.md WG section | present (pass 1) |
| `/rg redefine` + reload | `PlotService.load` re-resolves ids; `abortAll` clears shape cache |
| Cinematic starts on enter (outside house) or at viewpoint | `HouseCinematics.onEnterPlot/request`, `onTeleportToViewpoint` |
| Fake air over region box, polygon-filtered, reveal bottom-up outward, drop+bounce, resend | `RevealOrder`/`RevealPlan` (tests), `FakeBlocks`, `Effects.dropBlock` |
| Per block sounds/effects, completion confetti/fanfare/title/smoke | `impact`, `landed`, `complete`, `ChimneySmoke` (in-game check) |
| Others see nothing; world never changed | all sends are per player; architecture test forbids world writes in the cinematic classes |
| Kept out of the solid house, no kicks | `KeepOut` (hold = transient modifiers) + move/teleport guards (in-game check) |
| Safety on quit/death/teleport/world change/reload/disable | single `abort` funnel, reviewed; **not unit-testable without a server**: in-game list below |
| Seen only on completion, later no cinematic, `rebuild` | `complete()`, `HouseSeen` (JdbcPlayerStoreTest), `rebuild` |
| Per-player cookie/pedestal/shop; optional hide-others | unchanged `views`; `PedestalPrivacy` (pairs tested); verify with 2 accounts |
| Batching, caps, cleanup on reload/disable/quit | `fake-air-per-tick`, `max-falling-displays`, effects limits; cleanup reviewed |
| World modified only by place/undo | `ArchitectureRulesTest` (HousePlacer only + cinematic classes read-only + packets only in FakeBlocks) |
| Unit tests listed in the brief | layer ordering, polygon filtering, backup round trip, parser, plot config + migration, seen storage, settings: yes. "Cinematic cleanup paths": the pure part (`RevealPlan.abort`, re-entrancy, late callbacks) is tested; the Bukkit wiring needs a server (gap, covered by the in-game list) |
| PR has in-game guide + lists sounds/effects | below |

## Findings (all from the pass 2 review; none blocking)
1. **MEDIUM** `SoundService.java:131` `Sound#key()` deprecated for removal (since 1.20.5, removal 1.22; see pass 2 review for the 3-line `Registry.SOUNDS.getKey(...)` replacement). Fix before the PR if there is any coder round left.
2. LOW `start()` does not guard against a player standing on top of the house (feet cell not checked); add a check one cell below the feet.
3. LOW `abort()` / `landed()` could be hardened with try/finally (restore even if a cancel throws).
4. LOW `RevealPlan` budget cap drops fractions for `blocks-per-tick` > 1 non-integer.
5. LOW `pending` is per player, not per plot (matters only with several plots).
6. LOW minor allocation sizing (`sendBuffer`, boxed index map), bounded by `house.max-blocks`.

Docs: `docs/architecture.md` updated (cinematic classes, enable/reload/disable order, abort rule, fake-block ownership). Ownership: all five pass 2 commits inside Wesly's globs; the earlier branches were checked before their merges.

## Performance summary (for the PR)
- Idle cost: one early-return move handler, a few map lookups on interact/break/place/chunk-sent events, no task.
- While one player watches the cinematic (default 1 block/tick, about 17 s for the 170-block cottage): about 1 entity spawn + 1 removal, 1 small block packet, 3 sound packets and about 32 particles per tick; at most 12 display entities alive per player (hard cap 24, global 240). Hide pass: one packet batch per chunk section, the cottage in one tick.
- Smoke: 3 particles per 10 ticks per player standing in the plot.
- No blocking I/O on the main thread; seen flag on the IO thread.
- **Spark guide:** `/spark profiler start --timeout 60`, then (a) with one account run `/bakery house rebuild <name>` and watch it to the end, (b) with 3-4 accounts rebuild at the same time. Open the report, search for `HouseCinematics` and `Effects`: expect the plugin well under 1% of the tick (about 0.1-0.3 ms) for one viewer, linear in the number of simultaneous viewers; `FakeBlocks.sendAir` appears once at the start only. Red flags: `RevealPlan.<init>` or `start` above 5 ms (huge region), `sendAir` every tick (refresh loop stuck), `HouseCinematics.tick` running when nobody is building.

## Cleanup pass (workflow 4b)
**Skip as a general refactor; do one tiny coder fix round instead** (findings 1-4 are about 30 lines in total: `Registry.SOUNDS.getKey`, one extra cell below the feet in `start()`, try/finally in `abort`/`landed`, budget cap `blocksPerTick + 1`). No PERF-HIGH/MEDIUM exists, and `HouseCinematics` (689 lines, listener + trigger + run + smoke glue) is long but cohesive; splitting it is optional and should wait for `bakery-growth`. If no round is wanted, only finding 1 is worth doing before the PR (it will become a compile error when the API is removed in 1.22).

## In-game test guide (2 accounts, A = first visit, B = friend)
Setup: plot + house regions with `bakery-plot` / `bakery-house` flags, `/bakery house place default`, `/bakery plot setviewpoint`, `setpedestal`, `setchimney` (or the design's chimney point), plot flags `build deny`, `interact allow`.
1. A walks into the plot from outside: house disappears (about 1 s), then blocks fall in layer by layer, bottom first, centre outward, with thunk, vanilla place sounds, hammer taps every 3rd, dust on the first layer, sparkle per block. B standing next to A sees the house standing the whole time and no particles or sounds.
2. End: confetti, fanfare, title "Your bakery is open!", smoke from the chimney with occasional crackle. B also sees smoke (not mid-cinematic). A relogs: house there, no cinematic.
3. `/bakery house rebuild A` while A is in the plot: replays. While it runs, try each exit: A quits, dies (`/kill`), `/tp` away, changes world, an admin runs `/bakery reload`, `/plugman`-style disable: the house must be fully visible straight away, A can move normally (no slowness stuck), no title, and a later visit replays (not marked seen).
4. During the cinematic A cannot walk into the unrevealed area and is not kicked; right-clicking blocks near the house does not bring the house back early.
5. Polygon house region: no fake air outside the polygon, notch blocks untouched.
6. Each account sees only its own cookie, pedestal and shop.
7. New sounds to audition: `house_hammer`, `house_block_land`, `house_fanfare`, `house_chimney_crackle`, `@block` place sounds. New effects: `house_foundation_dust`, `house_block_land`, `house_sparkle`, `house_confetti`, `house_chimney_smoke`, the block drop (`house_block_drop`) and `plot_outline` / `house_outline`.

## Addendum: re-review of plugin-coder-fixes (2026-10-19)

Diff `8fe40e4..HEAD -- src/main` (HouseCinematics, RevealPlan, SoundService). `mise run check` green (435 tests), validate-assets green, compile with `-Dmaven.compiler.showDeprecation=true` has no warnings.

1. SoundService: `Registry.SOUNDS` is `@ApiStatus.Obsolete(since 1.21.4)` "use SOUND_EVENT" in paper-api 26.3.build.205 Registry.java; `SOUND_EVENT.getKey(...)` null-checked. OK, replaces the deprecated `Sound#key()`.
2. start(): INSIDE check now runs before the teleport, at the viewpoint when it applies, body plus cell under the feet. OK.
3. abort(): removes the run and sets `alive=false` before any step, each step guarded; `landed()` calling `abort()` from a fall callback is safe (later callbacks return on `!alive`, the retry is guarded and the plan's hidden list excludes the landed block). OK.
   LOW: a failing first `step()` returns `NOTHING_TO_BUILD`, which shows `house.rebuild-no-house` ("no house"); slightly misleading but the cause is logged SEVERE and the house is restored. Acceptable without a contract change; a dedicated `FAILED` value would be cleaner later.
4. RevealPlan `Math.nextDown(bpt+1.0)` cap: 2.5 now gives 10 in 4 ticks, no burst after a wait (the 1-cap case still passes). Test tightened to `assertEquals(10, total)`.

VERDICT: READY
