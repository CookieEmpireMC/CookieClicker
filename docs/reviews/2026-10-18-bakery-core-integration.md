# Integration review: bakery-core

Reviewer: Gandalf. Branch `feature/bakery-core` (HEAD `66e4fc2`), diff `main...HEAD`: 213 files. Target: Paper `26.3.build.205-stable`, Java 25, `api-version: '26.3'`, pack format 97.

**VERDICT: READY** (no blocker, no PERF-HIGH/MEDIUM; everything that needs a real client is listed under "Only verifiable in game").

## Numbers (clean tree at start; run in the main checkout)
| Check | Result |
|---|---|
| `mise run check` (`mvn clean verify`) | 233 tests, 0 failures, 0 errors, 0 skipped. BUILD SUCCESS. `GoldenValuesTest`: 3 run, 0 skipped. (238 after the new test below.) |
| `validate-assets` | 12 files, 0 problems |
| `sound-check` | 7 sounds, 0 failed (all 1 ch, 44.1 kHz, peaks -2.3 to -7.7 dBFS) |
| `balance-check` | 6/6 targets pass: first Villager Baker 12 s casual / 5 s active (max 20 s); first Wheat Farm 1m58 / 49 s (max 3m); no dead zone 25 s / 18 s (max 60 s) |
| `tools-test` | 133 tests OK |
| `ownership-check HEAD --base main --human-approved` | OK: 92 commits, 9 merge commits (none with conflict-resolution files). Human-controlled files changed (list under "Needs human review"): `.claude/skills/feature/SKILL.md`, `docs/workflow.md` (bc714a107), `.claude/agents/plugin-tester.md`, `.claude/skills/feature/SKILL.md`, `docs/workflow.md` (3d765e68e), all by Rickroll Rick. No flags. |
| `mise run pack` | `target/cookieclicker-pack.zip`, **23 files, 39321 bytes, SHA-1 `05775b91a110f20915cccb876aa97a6b5087059d`**. Run twice: identical (reproducible). |
| `git status --porcelain --untracked-files=all` | empty at start; at the end only my new test (`src/test/.../ShippedContentConsistencyTest.java`) and this review/memory are untracked |

Jar check: only our classes + yml files, no shaded Paper/Adventure/H2/MariaDB. Drivers are `provided` and loaded through `plugin.yml` `libraries:` (filtered to H2 2.5.252, MariaDB 3.5.10). The shade plugin runs but shades nothing (harmless).

## What I added
`src/test/java/dev/casperrs/cookieClicker/ShippedContentConsistencyTest.java` (5 tests, all pass): sound events (`sounds.yml` key -> `sounds.json` -> every .ogg, 3+ click variants, vanilla fallback), `StationInteractions` event names exist in `sounds.yml` / `effects.yml` (+ the `pop` preset), every item model (cookie, `building/<id>` from `buildings.yml`, `item-model` values in `effects.yml`) has item definition + model + textures, every building has a name in `messages.yml` and a fallback icon in `config.yml`, and the HUD glyph in `messages.yml` equals the `font/hud.json` char (and the no-pack bar has no glyph). Nothing in `validate-assets` covered the glyph or the building/message/icon links.

## 1. Acceptance criteria against the merged code
| Criterion | Verdict | Evidence |
|---|---|---|
| `/bakery station set/remove/reload`, perm `cookieempire.admin`, world **key** | OK | `BakeryCommand`: `requires(hasPermission)`, `world = getWorld().getKey().asString()`, nearest-remove, `reload` -> `CookieClicker.reload()` (settings, messages, balance, sounds, stations, effects, views). Plugin.yml permission default op. |
| Per-player visibility | OK | `StationViews.spawn`: `setVisibleByDefault(false)` inside the spawn consumer (nobody else gets it, not even for a tick), then `owner.showEntity`; `heal()` re-shows each refresh; `ClickRouter.route` also requires `player == target.owner()` (never trusts visibility alone). Effects: viewer-only particles + hidden displays. |
| Cookie: ItemDisplay + Interaction + pedestal, spin/bob without teleports | OK | `segment()` sends one interpolated transformation (delay 0, duration `segment-ticks` 10) per view per 10 ticks; no teleport anywhere. Spin <120 deg per segment (pinned by `CookieMotionTest`). `beforePop` orders a spin segment before a pop. |
| Click: +click value, pop, crumbs, sound variants, floating +N | OK | `StationInteractions.clickCookie`: `economy.click` -> `effects.pop` -> `effects.play(cookie_click)` (8 crumbs + "+N") -> `sounds.play` (4 variants, pitch 0.92-1.08) -> HUD + panel refresh. |
| Crit (chance/multiplier from config, golden burst, sound, big number) | OK | `Economy.click` reads `click.crit-chance/-multiplier` (5 %, x10) from `upgrades.yml`; `cookie_crit` preset (sphere + firework + crumbs + sparkles + big gradient number), `cookie_crit` sound. |
| HUD glyph + formatting, ~1/s | OK | `BakeryTicker` 1 Hz (`hud.refresh-seconds`), `Hud` uses `<font:cookieclicker:hud>` + U+E000 (matches `font/hud.json`; tested), no-pack variant without glyph, `NumberFormatter` + suffixes in `messages.yml`. |
| Panel: 2 rows, three states, buy, sounds | OK | `RowState` AFFORDABLE / UNAFFORDABLE / LOCKED (Wheat Farm locked until **100** baked, per `buildings.yml`), different text colour/background/icon brightness; redrawn only when `RowState` changes; `clickRow` re-validates server-side; `purchase` effect+sound or soft `cant_afford`. |
| CPS ticks 1/s while online | OK | `BakeryTicker.run` over loaded sessions (sessions exist only between join and quit), then `views::refreshPanels` so rows light up in the same second. |
| Async save, join/quit/autosave/flush, survives reload | OK | `SessionManager` + single IO thread; load failure -> no session (never overwrites a save); `shutdown(timeout)` flushes then closes. Reload keeps pouches (only the economy config swaps). |
| Pack sending | OK | `ResourcePackSender`: stable UUID, `replace(false)`, `required`/`prompt` from config, delayed 20 ticks, re-sent on reload only when url/hash changed; `ResourcePackTracker` tracks only **our** UUID (ItemsAdder's status events ignored). Default config has an empty url, so nothing is sent until the user sets url + sha1. |
| Quality: checks, no orphans, IDs exist, no balance in Java, tests, PR guide | OK | see sections 2-3 and the numbers; the guide is drafted below. |

## 2. Cross-agent consistency (all verified, plus the new test)
- Sound events used in code (`cookie_click`, `cookie_crit`, `purchase`, `cant_afford`): in `sounds.yml`, `sounds.json` (`ui.*`) and every .ogg exists; fallbacks are `minecraft:` keys.
- Effect IDs used in code (`cookie_click`, `cookie_crit`, `purchase`, `pop`): all in `effects.yml`.
- `item_model` keys: `cookieclicker:cookie`, `building/villager_baker`, `building/wheat_farm` (built in `StationViews` from the building ID) all have `items/*.json` -> `models/item/*` -> textures. Pack has 23 files, `pack.mcmeta` `min_format/max_format` 97 (matches the research for 26.3).
- HUD glyph U+E000 in `messages.yml` equals `font/hud.json`; font texture exists.
- Config keys read in `PluginSettings`/`StationVisualSettings`/`DatabaseSettings` all exist in the shipped `config.yml`; messages keys used in Java all exist in `messages.yml` (script check).
- Building IDs: `villager_baker`, `wheat_farm` in `buildings.yml` = names in `messages.yml` = icons in `config.yml` = items in the pack.
- Economy vs golden values: `GoldenValuesTest` runs (3 tests, 0 skipped), no balance literal in `economy/` (grep), cost growth and crit come from the yml files.
- `playSound` only in `SoundService`; `spawnParticle` only in `vfx/` (also pinned by `ArchitectureRulesTest`). No `ChatColor`, no `§`, no NMS/reflection, player/admin input only as `Placeholder.unparsed` (building name from the messages file, `<problem>`, `<world>`, numbers).

## 3. Orphaned entities: every cleanup path
All plugin entities are `setPersistent(false)`, hidden by default, tagged `cookieclicker`, plus PDC key (`cookieclicker:station` for views, `cookieclicker:vfx` for effects), created inside the spawn consumer and tracked.

| Path | Station views (`StationViews`) | Effect entities (`Effects`/vfx) |
|---|---|---|
| Enable | `sweep(true)` before Effects loads (removes everything tagged) | `load()` -> `sweepLeftovers()` (PDC key, untracked) |
| Reload | `restart()`: cancel tasks, `removeAll()`, respawn on next refresh (1 tick) | `reload()` -> `ticker.cancelAll()` (every job `cleanup()` removes its entities, pops restore rest scale) then sweep |
| Disable | `shutdown()`: tasks off, `removeAll()`, unregister, `sweep(false)`; wrapped in try/catch so the session flush always runs | `shutdown()`: `cancelAll()`, unregister, sweep |
| Restart / crash | non-persistent entities are never saved; `EntitiesLoadEvent` + enable sweep catch strays | same (`VfxListener.onEntitiesLoad`) |
| Quit | `onQuit` (MONITOR) + `SessionListener.onUnloaded` + refresh drops owners that are offline | `VfxListener.onQuit` -> `registry.cancelViewer` |
| World change / teleport to another world | `onWorldChange`, `onTeleport` -> `removeOwner`; respawn in range | job ends when `viewer.getWorld() != origin world` |
| Chunk unload | `onEntitiesUnload` removes the whole view; refresh respawns when in range and chunks loaded (`chunksLoaded` check) | `VfxListener.onEntitiesUnload` cancels the job; `DisplayJob.tick` also ends on `!isValid()` |
| Station removed / replaced | `onStationRemoved` -> `removeStation` (all owners) | n/a |
| Broken view (`/kill`, other plugin) | `healthy()` check each refresh -> remove + respawn | n/a |
| Spawn failure mid-way | catch -> removes everything already spawned | job cancel -> cleanup |

No leak found. Memory: `byOwner/byEntity/targets` are emptied by `remove()`; `ClickRouter.lastClickTick` is cleared on quit; `ResourcePackTracker.loaded` cleared on quit; `DisplayRegistry` maps released idempotently.

## 4. Findings (all LOW / information; none block)
**plugin-coder**
- **L1 (robustness):** `StationFile.load()` returns the in-memory stations only while a write is pending. If the write fails (IOException, logged SEVERE), a later `/bakery reload` reads the old file and drops the station from memory. Fix: keep a `dirty` flag after a failed write and keep returning `lastSaved` until a write succeeds. Rare (disk full / permissions).
- **L2 (UX, expected):** `/bakery reload` removes and respawns every view, so players near a station see the bakery blink for 1-2 ticks. Acceptable; the brief asks for no orphans, not for no flicker.
- **L3 (config):** the default `resource-pack.url` is empty, so a fresh install never sends the pack. Intended (the hosted URL doesn't exist yet), but the in-game test must set url + SHA-1 first (see the guide).
- **L4 (hardcoded looks, from earlier reviews, still true):** a few look constants in `StationViews`/`Spawner` (`Display.Brightness(15, 15)` for effect displays). Not balance; leave.

**vfx-designer**
- **Info:** `max-display-entities-per-player: 8` with a 20-tick "+N" means that above about 8 clicks/s the oldest floating number is evicted early (by design: newest feedback wins). Crit = 1 text + 3 crumbs = 4 entities. Global cap 150 is reached by about 19 players spamming at once.

**sound-designer / model-designer / balance-designer:** nothing new. Panel and HUD assets, sounds and balance targets match the code and the brief.

**tester**
- Wheat Farm unlocks at **100** baked cookies (`buildings.yml`); the pass-2 guide said 50. The guide below uses 100.

## 5. Performance
Hot paths and estimated cost per call:
| Path | Frequency | Work | Estimate |
|---|---|---|---|
| `StationViews.refresh` | every `refresh-ticks` (20) | per online player: session lookup + loop over stations (distance); full work only for in-range views (3 `isChunkLoaded`, `healthy`/`heal` over 10 entities, 2 `RowState`s) | 100 players x 20 stations = 2000 distance checks/s; about 20 small allocations per in-range view/s |
| `StationViews.motionTick` | every 10 ticks | per view: 1 `getTransformation` + 1 `setTransformation` (one metadata packet to the owner) | 100 views = 100 packets per 0.5 s, spiking in one tick; stagger only if spark shows it |
| `BakeryTicker` | 1 Hz | per loaded player: `produce`, HUD (template swap, no MiniMessage parse), `refreshPanels` (RowState equality, text rebuilt only on change) | microseconds per player |
| Click | per click | economy, up to 8 + 4-ish effect entities (capped), 8-16 particle calls (viewer only), pop job, 1 sound, 1 action bar, 2 RowStates | well under 0.1 ms; auto-clicker at 20 cps stays bounded by caps |
| `EffectTicker` | per tick only while jobs exist | steps live jobs, cancels itself when idle | idle cost zero |
| Listeners `EntitiesLoad/Unload` | per chunk load/unload | tag check first (set lookup), PDC only for tagged entities; unload returns early when no effect runs | negligible |
| Storage | join (1 load), quit (1 save), autosave 300 s (1 save per player), station file on set/remove | all on the single IO thread; main thread only serialises a small snapshot | fine; autosave snapshots all pouches in one tick (tiny objects) |

No blocking I/O on the main thread (config files are read at enable/reload only). Entity budget: 10 view entities per player per station in range (cookie, hitbox, pedestal, header, 3 per row) + at most 8 effect displays per player; each is visible to its owner only. Estimated total: under 0.1 ms/tick at 50 players near stations; peak at the 10-tick motion send. **No PERF-HIGH or PERF-MEDIUM.**

PERF-LOW (carried, not blocking): per-view allocations in `refresh`; all motion segments in the same tick; `Effects.play` re-creates a `TagResolver` per play (tiny).

**Spark guide for the PR:** run `/spark profiler start --timeout 60` while 3 players stand at stations and click quickly (one with an auto-clicker around 15 cps) and one stays idle in range. In the report search for `StationViews.refresh`, `StationViews.motionTick`, `ClickRouter`, `StationInteractions`, `Effects.play`, `Hud.refresh`, `BakeryTicker`, `EffectTicker`. Good: the plugin as a whole under about 1-2 % of the tick, no single method above about 0.5 ms, `/spark tps` steady 20, and `/minecraft:execute if entity @e[tag=cookieclicker]` shows roughly 10 per player-station plus at most 8 effect displays per player. Bad: `refresh` or `motionTick` above 1 ms with few players, or the entity count growing while idle (a leak).

## 6. Architecture
`docs/architecture.md` (updated at pass 2) is still accurate: wiring in the main class, `view`/`vfx`/`hud`/`pack`/`session`/`storage` packages with no cycles seen, `StationViews` (663 lines) is the largest class but has one job (view lifecycle) with pure helpers split out; enable/disable/reload orders match the doc. No `docs/architecture.md` change needed.

## 7. In-game test guide (for the PR)
**Setup**
1. `mise run build`, copy `target/CookieClicker-1.0.0.jar` to the Paper 26.3 test server's `plugins/` (ItemsAdder 4.0.18 installed there too). Start; the log must show "Storage: ...", "Loaded 5 effect(s)", "Enabled with 0 station(s), 2 building(s)" and no stack traces.
2. Host `target/cookieclicker-pack.zip` (from `mise run pack`; the SHA-1 is printed, currently `05775b91a110f20915cccb876aa97a6b5087059d` for this commit) on an https URL (e.g. a GitHub release asset). Put url + sha1 in `plugins/CookieClicker/config.yml` -> `resource-pack`. Set `debug.log-click-events: true` for the first run. `/bakery reload`.
3. Give yourself `cookieempire.admin` (op is enough).

**Station and visibility**
4. Stand where you want the bakery, face the direction you want players to look, `/bakery station set`. Expect the confirmation with `station-1` and the world key `minecraft:overworld`; a second `set` within 8 blocks is refused.
5. Within about 1 s: pedestal, spinning and bobbing cookie, "Bakery Shop" panel with Villager Baker (affordable only after 50 cookies) and Wheat Farm (locked, "bake 100 cookies to unlock"). Text must read left to right and not mirrored; if mirrored or edge-on, change `station.panel.text-yaw-offset`/`icon-yaw-offset` by 180 and `/bakery reload`.

**Clicks (with the debug log on)**
6. Right-click the cookie: "+1" rises and fades, crumbs, pop squash, crunch sound, HUD cookie count +1. Left-click the cookie (punch): the same must happen. In the console note which `[click-debug]` events fire for right and left (`PlayerInteractAtEntityEvent`, `EntityDamageByEntityEvent`, `PrePlayerAttackEntityEvent`) and that each click counts exactly once (HUD +1, not +2). Also click with an item in each hand and while holding nothing. Then set `debug.log-click-events: false`.
7. Keep clicking about 40 times: about 2 crits (5 %): golden burst, higher crit sound, large gold "+10".
8. Sound variants differ and pitch varies slightly; click from 5 blocks works, from 8 blocks does nothing.

**Panel states, buying**
9. Click until 50 cookies: the Villager Baker row turns from grey/red to bright/green within a second (without clicking). Click the row: purchase sound + gold ring effect, owned x1, next cost 58, cookies -50, CPS +0.5/s on the HUD. Right and left click both buy one.
10. Click the Wheat Farm row while locked or too poor: soft "nope" sound, nothing deducted. After 100 baked it shows as unaffordable, then affordable at 500.
11. Stand still: CPS adds up once per second on the HUD; big numbers read "1.2 million" (use a temporary balance edit or wait/cheat via DB).

**Two players**
12. A second account walks up to the same station: sees its own cookie/panel with its own 0 cookies; the first player's clicks, "+N" numbers and effects are invisible to the second and vice versa. Both click at once: numbers independent. The second cannot click the first player's entities.

**Resource pack accept / decline, ItemsAdder**
13. Join with the pack prompt: accept. The cookie becomes the 3D model, HUD shows the cookie glyph, sounds are the custom ones. With ItemsAdder 4.0.18 loaded confirm both packs are active (check an ItemsAdder item still looks right, and the ItemsAdder log shows no errors for the second pack; ItemsAdder lists support up to 26.2, so note any 26.3 warnings).
14. Rejoin and decline: friendly "vanilla stand-ins" message, flat cookie item instead of the 3D model, bread / wheat icons, HUD without glyph box (no-pack bar), vanilla fallback sounds.

**Reload, restart, leftovers**
15. While clicking, `/bakery reload` several times: the bakery blinks and returns, no duplicate cookies, effects don't linger, HUD and cookie count unchanged. Run `/minecraft:execute if entity @e[tag=cookieclicker]` (expect about 10 + a few) and again with nobody in range (expect 0).
16. Edit `buildings.yml` costs, `/bakery reload`: panel shows the new costs; break `messages.yml` YAML: reload reports a problem and keeps the old messages.
17. Quit and rejoin: cookies and buildings are as before. Buy something, then `stop` the server within a second; restart: progress is there.
18. After restart, before anyone walks up: `/minecraft:execute if entity @e[tag=cookieclicker]` = 0. Walk away more than 24 blocks and back, and cross a chunk border at the edge of the 24-block range: the bakery despawns and respawns once.
19. Station race: `/bakery station set` and in the same second `/bakery reload` (run both in one tick via the console/two admins or by typing quickly): the new station still exists afterwards and after a restart. Then `/bakery station remove` + `/bakery reload`: gone for good.
20. Teleport to another world and back: view removed, then respawned; `/kill @e[tag=cookieclicker]` as a griefer: the view respawns within a second.

**spark**
21. Follow the spark guide in section 5.

## 8. Only verifiable in game
- Which click events Paper 26.3 fires for an `Interaction` for left and right click, and that each click is counted once (the router dedupes per player per tick, but the real event set is untested).
- Per-player `showEntity`/`canSee` for hidden `Interaction` entities: another player must neither see nor target them.
- Panel text/icon facing and readability (yaw offsets), text backgrounds and brightness, locked-row look.
- Smooth spin/bob with no snap at the 2 pi wrap, and a pop landing during a spin segment (`beforePop` covers only the same/next tick).
- `EntitiesUnloadEvent` removal inside the event and respawn when crossing chunk borders.
- The 3D cookie, building icons, HUD glyph (ascent/height) and all four sounds in a real client, with and without the pack.
- ItemsAdder 4.0.18 pack coexistence on 26.3 (IA lists 26.2 as its newest) and the pack prompt latency.
- H2 file in `plugins/CookieClicker/data/`, `libraries:` download on first start (needs internet), MariaDB (never run against a server).
- Real tick cost with spark.

## MERGE ORDER
No branches left open for this feature. Next: Rick commits this review, my new test and the memory update on `feature/bakery-core`, then opens the PR.
