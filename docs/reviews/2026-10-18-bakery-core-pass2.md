# Review: bakery-core / plugin-coder-2 (pass 2), 2026-10-18

Reviewer: Gandalf. Branch `bakery-core/plugin-coder-2` (Wesly), 7 commits, review at `7e9f49c`.

**Target:** Paper 26.3 (`26.3.build.205-stable`), Java 25, `api-version: '26.3'` (all match). No NMS or reflection in `src/main`, nothing shaded.

## Verdict: READY (no blocker; findings below go into the cleanup pass)

## Test results
`mise run test`: **228 run, 0 failed, 0 skipped** (was 191 with 1 skipped). `check` BUILD SUCCESS. `validate-assets` 0 problems, `sound-check` 7/7, `balance-check` 6/6, `tools-test` 133 OK, `ownership-check bakery-core/plugin-coder-2` OK.

New/changed tests (all in `src/test`):
- `NumberFormatterTest`: `@Disabled` removed; `roundTenthsStayRoundAcrossAllMagnitudes` passes (1e22..1e99 fixed).
- `view/StationFrameTest` (6): yaw 0/90/180/270 forward and right vectors, offset length for any yaw, relative offsets, `facingViewers` range and direction.
- `view/CookieMotionTest` (5): angle range and wrap, constant per-segment step (never the long way round), bob bounds and period, spin/bob off, 5 years of ticks.
- `view/RowStateTest` (6): affordable / unaffordable / locked, owned+cost+cps, `nextBuildingCps` equals the real `totalCps` delta for every upgrade combination, unknown building, equal states do not redraw.
- `config/StationVisualSettingsTest` (9): defaults, shipped `config.yml` parses without problems, bad offsets/non-positive/NaN numbers, segment clamp + >120 degree spin warning, ARGB/RGB colours, brightness clamp, fallbacks.
- `config/PluginSettingsResourcePackTest` (7): valid, trim/lowercase, empty url/hash, bad hash lengths/chars, non-http and malformed URLs, disabled pack not validated, delay clamp and prompt default.
- `station/StationFileTest` (3): load during a queued write returns the newest stations (not the old file), queueing after IO shutdown restores the counter, file round trip. `JavaPlugin` cannot be constructed without a server, so the test allocates the plugin with `Unsafe` and sets only `dataFolder` (test only; see finding T1 for a cleaner seam).
- `SessionManagerTest.joinAfterShutdown...`: pass-1 finding regression.

Not unit-testable here (needs a server): everything in `StationViews`, `ClickRouter`, `StationInteractions`, `ResourcePackSender` (no MockBukkit for 26.3; see "Only in-game").

## Acceptance criteria covered by pass 2
| Criterion | Result |
|---|---|
| Per-player visibility | OK. Every entity is spawned inside the spawn consumer with `setVisibleByDefault(false)`, so nobody else sees it even for a tick, then `showEntity` to the owner; `heal()` re-shows each refresh. Hitboxes included. `ClickRouter` also checks `owner == player` before any economy call (never trusts visibility). |
| 3D cookie + Interaction | OK. ItemDisplay with `item_model` `cookieclicker:cookie` only when the player has our pack, vanilla COOKIE otherwise. Hitbox sized from scale + bob + margin. |
| Idle spin/bob, no teleports | OK. One interpolated transformation per `segment-ticks` (default 10) per cookie, no `teleport`. `spinStepRadians` is validated (<120 degrees). |
| Click -> pop, crumbs, sound, "+N" | OK. `economy.click` -> `beforePop` -> `effects.pop` -> `effects.play(cookie_click/cookie_crit, loc, player, amount)` -> `sounds.play` -> HUD + panel. |
| Crit | OK. `result.crit()` selects `cookie_crit` (effect and sound). |
| Panel affordable/unaffordable/locked | OK. `RowState` with 3 styles, own message and background colour per style, icon brightness. Redraw only when `RowState` changes (equals), so no metadata packets otherwise. |
| purchase / cant_afford | OK. `buyBuilding` result is the truth (re-validated server side); `purchase` effect + sound, or `cant_afford` sound. |
| Pack sending | OK. Stable UUID, `replace(false)`, delayed send, URL/hash validated, re-sent on reload when changed. Tracker counts only our UUID (ItemsAdder's pack ignored) except `resource-pack.enabled: false` (any pack). |
| Every sound key / effect ID exists | OK. Code uses sounds `cookie_click`, `cookie_crit`, `purchase`, `cant_afford` (all in `sounds.yml`) and effects `cookie_click`, `cookie_crit`, `purchase`, `pop` (all in `effects.yml`). Every `messages.yml` path used in Java exists (`panel.header`, `panel.row.*`, `hud.action-bar[-no-pack]`, `resource-pack.prompt/declined`, `storage.load-failed`). |
| No balance numbers/text in Java | OK except the items in finding L2/L3 (look tunables, not balance). Row names/costs/CPS come from `economy` + `messages.yml`; placeholders are `unparsed`. |
| Main thread / owner check | OK. No async Bukkit calls added; owner check is first in `ClickRouter.route`. |

## No orphaned entities: every cleanup path
All entities: non-persistent, scoreboard tag `cookieclicker`, PDC `cookieclicker:station`, tracked in `byEntity`.

| Path | Mechanism | OK |
|---|---|---|
| Quit | `PlayerQuitEvent` (MONITOR) + `SessionListener.onUnloaded` + refresh drops owners that are offline | yes |
| World change / teleport | `PlayerChangedWorldEvent`, cross-world `PlayerTeleportEvent` | yes |
| Walk away / long teleport (same world) | refresh removes views out of `view-distance` (<= `refresh-ticks`) | yes |
| Station removed / replaced | `StationListener.onStationRemoved` -> `removeStation` (also fired by `replaceAll` on reload) | yes |
| Chunk unload | `EntitiesUnloadEvent`: whole view dropped, respawned when back in range and chunks loaded | yes (verify in game, see below) |
| Entity killed / invalid | `healthy()` check each refresh -> whole view respawned | yes |
| Reload | `views.restart()` removes all, next refresh respawns with new layout/balance | yes |
| Disable | `views.shutdown()` (cancel tasks, `removeAll`, unregister, `sweep(false)`), then effects, `stations.clear()`, sessions; does not depend on `onUnloaded` | yes |
| Spawn failure mid-view | `spawned.forEach(remove)` in the catch | yes |
| Crash leftovers | non-persistent entities are never saved; enable sweep `sweep(true)` removes every tagged entity in loaded worlds **before** `effects.load()` (confirmed in `CookieClicker.onEnable`: `views.sweep(true)` then `effects.load()`); `EntitiesLoadEvent` removes untracked entities with our PDC key | yes |
| Effects entities | Effects engine paths reviewed in the vfx review (tracked, capped, removed on end/unload/quit/reload/shutdown); pops put the cookie back to rest scale on shutdown | yes |

Edge notes: if `views.shutdown()` threw, `effects.shutdown()`, `stations.clear()` and the session flush in `onDisable` would be skipped (finding L5).

## Findings
Severity: B = bug, L = low/polish, T = test seam, PERF = performance. No blocker and no PERF-HIGH/MEDIUM.

### plugin-coder (cleanup pass)
- **L1 (UX, low):** the bakery appears up to `refresh-ticks` (1 s) after the session loads or the player walks in; and a row lights up up to 1 s after the HUD tick that made it affordable (the ticker and the refresh run on different phases). Suggest: call the same refresh for that player from `SessionListener.onLoaded` and from `BakeryTicker` after `produce` (or after the HUD refresh), cheap thanks to `RowState` equality.
- **L2 (hardcoded tunable, reported as asked):** `StationInteractions.TOWARD_PLAYER = 0.1` blocks. Move to `config.yml` (`station.cookie.click-effect-offset`).
- **L3 (hardcoded looks):** `Display.Brightness(15, 15)` for cookie and texts, `setShadowed(true)`, `setSeeThrough(false)`, `setDefaultBackground(false)` in `StationViews`. Not balance; fine to leave, or put under `station.panel`.
- **L4 (config drift):** in-code default of `station.panel.colors.locked` is `#B0202020` (same as unaffordable), `config.yml` ships `#90181818`. Make the defaults agree (test `shippedConfigYml...` pins the file value).
- **L5:** wrap `views.shutdown()` and `effects.shutdown()` in `onDisable` in try/catch (log) so the session flush always runs.
- **L6:** `PluginSettings` accepts any scheme starting with `http` (`httpx://...`). Use `equals("http") || equals("https")`. The default (empty) pack url reports a "config problem" on every start and reload of a fresh install; log it at info instead.
- **L7:** `ClickRouter` marks the click tick before the distance/view checks (harmless), and `StationViews.onEntitiesUnload` removes entities inside the unload event: confirm in game that Paper tolerates it (see in-game list).
- **PERF-LOW-1** `StationViews.refresh`: per in-range view per refresh (default 1/s): `healthy()` and `heal()` each build a new 10-element list (`StationView.entities()`), `chunksLoaded` allocates `List.of` + 3 arrays, `updateRows` allocates 2 `RowState`s. About 20 small allocations and 20 `isValid`/`canSee` calls per view per second, negligible. Fix: make `entities()` an immutable list built once in the constructor.
- **PERF-LOW-2** `StationViews.onEntitiesLoad` runs for every entity of every chunk load on the server and calls `getPersistentDataContainer()` first. Non-persistent entities never come back from disk, so the handler is nearly redundant. Fix: test `getScoreboardTags().contains(TAG)` first (cheap) or drop the handler. Same pattern exists in `VfxListener` (vfx-designer, not blocking).
- **PERF-LOW-3** Hud: `Hud.refresh` runs `MiniMessage.deserialize` plus two number formats on every click and every second per player (cost about 20-50 us each). An auto-clicker at 20 cps = 20 parses/s/player. Fix (optional): pre-parse the template once per reload, or skip the click refresh when the 1 Hz ticker refreshes anyway.
- **PERF-LOW-4** `motionTick`: all views get their segment in the same tick (one metadata packet per view every 10 ticks, about 4 allocations each). 100 views = 100 packets once per 0.5 s. Fine; stagger by view index only if spark shows it.
- **PERF-LOW-5** Entity budget: static view = 10 entities per player per station (cookie, hitbox, pedestal, header, 3 per row) plus effect displays (`max-display-entities-per-player: 8`, global 150). 100 players at stations = 1000 server-side entities, each only tracked by its owner. OK for expected counts; the global effect cap (150) is reached by about 19 players clicking at once (effect-side, vfx-designer's call).

### vfx-designer / balance-designer / sound-designer / model-designer
Nothing new from this review.

### tester notes
- **T1:** `StationFile` takes a `JavaPlugin`, which cannot be built in a unit test. A seam (`StationFile(Path dataFolder, Logger, Executor, ResourceOpener)`) would let the pending-write test run without the `Unsafe` workaround. Optional.
- The branch is based on an older feature-branch tip (diff against `feature/bakery-core` also shows reversed doc/agent-file changes); the merge is clean because ownership-check passes, but merge with `--no-ff` and check `git diff` of `.claude/` after.

## Performance
Checked: refresh task (`station.refresh-ticks`, default 20): per online player, one session lookup and a loop over all stations with a distance check (players x stations cheap arithmetic, 100 x 20 = 2000 calls/s), full work only for in-range views. Motion task (every 10 ticks): one transformation per view, no teleports. Ticker (1 Hz): production + HUD per loaded player. Click path: owner/distance/dedupe checks, economy, up to about 5 effect entities (capped), one sound, one action bar, two `RowState`s (no text rebuild unless changed). No blocking I/O on the main thread (station file write and DB on the IO thread; `StationFile.saveAsync` serialises on main, tiny). No per-tick work that grows with total players/stations beyond the 1 Hz distance loop. Estimated cost: well under 0.1 ms per tick at 50 players with stations, dominated by the periodic motion/refresh spikes. No PERF-HIGH or PERF-MEDIUM.

**Profile with spark:** `/spark profiler start --timeout 60` while 2-3 players stand at stations and click fast (and one auto-clicker at about 15 cps), then open the report and look for `StationViews.refresh`, `StationViews.motionTick`, `ClickRouter`/`StationInteractions`, `Hud.refresh`, `BakeryTicker`. Good: the whole plugin under about 1-2% of the tick, no single method over about 0.5 ms; `refresh` and `motionTick` do not show as spikes above the tick average. Also watch `/spark tps` and entity count (`/minecraft:execute if entity @e[tag=cookieclicker]` stays about 10 per player-station plus at most 8 effect displays per player).

## In-game test guide review
Wesly's own guide was not in the commits I was given (only commit messages and code comments), so this is the checklist I would hold the PR guide to. It must cover (add what is missing):
1. Fresh server: start, check the log (no errors; "config problem" about empty pack url is expected until the pack is hosted). `/bakery station set`, then `/bakery reload`, restart: station still there, no leftover entities (`/kill @e[tag=cookieclicker]` reports none before a player walks up).
2. Walk up: cookie, pedestal and panel appear within about 1 s; with 2 players at the same station each sees only their own (both click, numbers differ).
3. Click cookie (right AND left click, both hands, with an item in hand): "+1" floats, crumbs, pop, sound variants differ; crit about 1 in 20: gold sparkles, bigger number, crit sound.
4. HUD updates about once per second; "1.2 million" style after cheating cookies in.
5. Panel: row unaffordable (grey/red), becomes affordable (bright), locked row for Wheat Farm until 50 baked; buy Villager Baker (sound + effect, owned/cost update), try to buy with too few cookies (soft nope sound, nothing deducted).
6. Pack: with url+sha1 configured join (pack prompt, accept: custom cookie model + glyph + sounds; decline: friendly message and vanilla stand-ins: cookie item, bread/wheat icons, no glyph box); with ItemsAdder 4.0.18 both packs load.
7. Disable/restart/reload: `/bakery reload` mid-pop and while clicking, no duplicate cookies; `/plugman`-style reload or stop the server; relog; progress saved.
8. `debug.log-click-events: true` once to see which of the three click events fire on an Interaction, then turn it off.

**Can only be verified in game:** which click events Paper 26.3 fires for an Interaction (right/left); that `showEntity`/`canSee` for hidden Interaction entities behaves per player; text/icon facing (the `text-yaw-offset`/`icon-yaw-offset` calibration) and panel readability; the pop not being disturbed by a spin segment that lands mid-pop (the `beforePop` guard only covers the same/next tick); smooth spin/bob with no snapping at the wrap (angle 2 pi -> 0); `EntitiesUnloadEvent` removal inside the event; respawn after walking away across chunk borders; whether the ItemDisplay `item_model` renders for pack players; ItemsAdder pack coexistence; real latency of the pack prompt.
