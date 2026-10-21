# Integration review: upgrade-holograms

- **Reviewer:** Gandalf (plugin-tester), integration review on `feature/upgrade-holograms` (HEAD d29b441), compared with `main`
- **Target:** Paper 26.3 (`26.3.build.205-stable`), Java 25, Adventure 5; no version, shading or NMS problems in the diff
- **Not reviewed here:** `upgrade-holograms/plugin-coder-cleanup` (running in parallel)
- **VERDICT: READY**

## Checks (run in the main checkout)
| Check | Result |
|---|---|
| `mise run check-quiet` | 623 tests, 0 failures, BUILD SUCCESS |
| `mise run validate-assets` | 35 files, 0 problems |
| `mise run sound-check` | 23 sounds, 0 failed (`upgrade_shelved` 300 ms mono, `bulk_toggle_1..3` present) |
| `mise run balance-check` | 6/6 targets pass |
| `mise run tools-test` | 134 tests OK (1 skip: the ownership LibSync test) |
| Golden values | `GoldenValuesTest` green (costs exact, doubles within 1e-9, repeatable upgrades and bulk costs) |

## Reuse of earlier verdicts
vfx at a648c97/8067d28, economy at 3780e24 and panels at 5b4b90d were reviewed line by line earlier and are not re-read. `git diff` since those commits only contains merges and the brief/memory commits, so those verdicts stand.

## Acceptance criteria
| Criterion | Status |
|---|---|
| Two per-player panels (`buildings` at `layout.shop`, `clicks` at new `layout.clicks`), header, `max-rows` | Met by code and config (`factory.yml` `clicks: [16,0,6]`, `station.panels.*.max-rows` 8/4, plots.yml `clicks` round trip tested). Look: in-game only |
| Buildings panel rows for the chosen bulk amount | Met (RowState tests) |
| Clicks panel: repeatable pinned at top, cheapest unbought one-time upgrades below, bought ones leave | Met (`PanelRowsTest`) |
| Affordable / unaffordable / locked looks, left-aligned text | Met in config/code; look in-game only |
| ×1 ×5 ×10 Max toggle under the header, highlighted, per player and panel, default ×1 | Met (`BulkModes` cycle test); highlight look in-game only |
| ×N buys exactly N or nothing; Max buys at least 1; one-time always once; repeatable capped by levels left | Met (`EconomyTest`, `BulkMathTest`, `UpgradeRowStateTest`) |
| Max in closed form, capped by `max-level` | Met (BulkMath geometric series with +-1 correction, boundary tests) |
| Smooth price tween (8 ticks, eased, log space, step 2, exact end, 1 Hz refresh never cuts it short) | Met by code and `PriceTweenTest`; smoothness in-game only |
| Atomic bulk buy: one sound/effect, one save, one refresh | Met (single `purchase` sound + effect per buy in `StationInteractions`; economy buy is all-or-nothing) |
| `upgrades.yml` has the 9 one-time click upgrades + repeatable `bakers_strength` | Met (all 10 ids present; messages `upgrades.<id>.{name,effect}` exist for each) |
| `model.md`, simulator, golden values and `balance-check` cover repeatables | Met (balance-check 6/6, GoldenValuesTest) |
| Saves store levels; old id sets load as level 1 | Met (JDBC schema v3, v1->v3 and v2->v3 tested). LOW: v2->v3 ALTER not idempotent |
| `upgrade_bought` at the row, icon glides to shelf, lands with `upgrade_shelved` effect + sound | Met by code (one teleport + one scale interpolation, `animateShelf`; IDs exist in `effects.yml` / `sounds.yml` / `sounds.json`). Looks and sounds: in-game only |
| Shelf per player, `station.shelf.*`, click shows name/effect in action bar | Met by code and config (`panel.shelf.tooltip`); in-game only for feel |
| Shelf icons count toward the entity budget, at most `max-icons` | Met (24 cap; only 9 one-time upgrades exist today, so 18 entities max) |
| `NumberFormatter` short suffixes from 10^3, 33 suffixes in `messages.yml`, truncates, scientific past the end | Met (`NumberFormatterTest`, boundaries) |
| Used everywhere (panels, HUD, messages, floating "+N") | Met: no other number formatting in `src/main` apart from admin coordinates and block counts |
| Cookie spin fix | Met by code and tests, end to end (see below). Real-client feel: in-game only |
| Tests listed in the brief | Met (BulkMath/Economy/Golden, tween, save migration, formatter, row order, toggle cycle, PopMotion keyframes) |
| All checks green, `ownership-check` on every branch | Checks green; `ownership-check` passed per branch in the branch reviews |
| Performance: two panels + full shelf for 4 players well under 1 ms/tick | Met by estimate (see Performance) |

## Cross-part wiring
- **Economy API <-> panels:** panels only call `Economy` / `BulkMath` for costs, Max counts and buys; there are no balance numbers in `view/` or `hud/`. Click-panel text and effect lines come from `messages.yml`, `upgrades.<id>.*` exists for every id in `upgrades.yml`.
- **Spin fix end to end:** `StationInteractions.clickCookie` calls `views.beforePop` (sends a due spin segment first, so the pop is the last transformation of that tick), then `effects.pop(cookie, player, POP, views.popMotion(view))`. `CookiePopMotion` samples `CookieMotion` (the same functions a segment uses) at each step's end tick and reports it, so `StationViews.animationTick` leaves the cookie alone until that tick (`motionFromPop`: resume at `now > target`) and then starts the next segment. Result: spin, bob and squash travel in one interpolation, no frozen segment, no jump. Unit tested by `PopMotionTest` (vfx) and `CookiePopMotionTest`.
- **IDs vs files:** sound events `cookie_click`, `cookie_crit`, `purchase`, `cant_afford`, `upgrade_shelved`, `bulk_toggle` and effects `upgrade_bought`, `upgrade_shelved`, `purchase`, `cookie_click`, `cookie_crit`, `pop` all exist in `sounds.yml` / `effects.yml`; sounds go only through `SoundService`, particles only through `vfx`. Item models `cookieclicker:upgrade/<id>` exist for all 10 upgrades; `layout.clicks` is in `factory.yml`; every `panel.*` and `numbers.suffixes` message key read in code exists in `messages.yml`; `station.*` config keys of the contract all exist.
- **Lifecycle:** enable: messages -> balance -> sessions -> `views.sweep(true)` (removes crash-leftover shelf/panel entities) -> effects -> listeners -> `views.start()` (two tasks, a listener, the shelf-landing callback set before start). Reload: `loadBalance` then `views.restart()` removes every view (panels, shelf, flights) and respawns. Disable: tasks cancelled, `views.shutdown()` removes everything incl. in-flight icons, before `sessions.shutdown()` flushes. Shelf icons and hitboxes are in `StationView.entities()`, so quit / world change / unload paths clean them as well. Bulk modes are a session-only map keyed by UUID, forgotten on quit (`BulkModes.forget`), kept across view respawns and reload.
- **Assets vs code:** model-designer, sound-designer and vfx-designer outputs agree with the code and `design/*.md`; no direct `playSound` / `spawnParticle` outside their services.

## Bugs
None HIGH or MEDIUM.

## Performance (4 players, two panels + full shelf + belt)
- Hot paths: `animationTick` (every tick): per view a `List.copyOf` of the views map, the cookie segment check (a transformation send every `segment-ticks`, or during pops), `panels.animate` (returns immediately when no tween is running; a running tween renders about 10-20 us per row every `step-ticks`, for 8 ticks), `animateShelf` (empty shelf returns at once; otherwise one pass over at most 9 icons today, max 24). Per view per tick: well under 10 us idle, 50-150 us during an active tween burst. For 4 players: about 0.02-0.05 ms/tick idle on average, peak about 0.3 ms in a tick where all four players toggle a panel at once. Target "well under 1 ms/tick": met.
- 1 Hz `BakeryTicker` refresh: per player 2 panels x at most 8 + 4 rows; cost and Max count are closed-form (`Math.pow`), the text is re-sent only when the formatted text changed (Max churn aside, below).
- Entities per player: cookie, hitbox, pedestal (3), buildings panel header + toggle + hitbox + 3 x 2 rows (about 9), clicks panel header + toggle + hitbox + 3 x 4 rows (about 15), shelf up to 2 x 9 now (cap 2 x 24) = about 45 now, 75 at the cap; plus belt cookies. For 4 players about 200-300 entities, each shown only to its owner; this is the biggest cost to watch, not CPU.
- No blocking I/O on the main thread added; saves stay on the IO thread. Bulk buy = one save request.
- PERF-LOW: per-tick `List.copyOf` of `byOwner` and of each views map and of the shelf values (`StationViews.animationTick`, `animateShelf`); iterate a snapshot only when a flight is active, or iterate directly with a guard against removal. PERF-LOW: `PanelUpdater.update` renders immediately on a price change, bypassing `step-ticks` under constant Max churn (many CPS ticks per second changing the Max count); acceptable, but could be rate-limited.

## Follow-ups (LOW, no fix round; go in the PR)
1. Schema v2->v3 `ALTER TABLE ... ADD COLUMN` is not idempotent; use `ADD COLUMN IF NOT EXISTS` (JdbcPlayerStore).
2. Unused `groupingSeparator` in `NumberFormatter` settings.
3. `design/effects.md` says `upgrade_shelved` costs 10 particles; the preset spawns 16.
4. Can't-afford on a panel is sound-only (no action-bar reason such as "need 1.2K more").
5. `StationViews.spawnShelfIcon` passes a throwaway spawned list (cosmetic cleanup).
6. PERF-LOW items above (per-tick `List.copyOf`, immediate render under Max churn). The cleanup branch may already cover some of these; re-check after it lands.
7. The GDD "Differences from the current build" (shared `game-design` skill, CookieMemory) needs a line that holograms replaced the §4.7 chest GUI and click upgrades (§4.4) are in. This needs a PR on CookieMemory (shared knowledge); not done here.
8. **Balance (user approval was conditional, 2026-10-21):** the click upgrade prices and the `bakers_strength` curve were approved on the condition that more buildings follow. If the roster stays at 2 buildings, the late click upgrades (Diamond Fingers, Netherite Hands, Handy Fingers I-III) have saves of up to about 1.5 h and need retuning. `balance-check` only covers the early targets and passes.
9. `bulk_toggle` and `upgrade_shelved` sounds are placeholder until a human has listened (needs-human-review item in the brief).
10. `docs/reviews` has no separate panels review doc; the panels verdict (5b4b90d) is recorded in the brief and in tester memory.

## In-game test guide
Setup: build the pack (`mise run pack`) and test once with the pack and once after declining it (vanilla fallback icons/sounds). Use a fresh plot with `/bakery plot setup` and also an existing plot (checks the `clicks` fallback). Start spark before the clicking tests: `/spark profiler start --timeout 60`.
1. **Panels:** stand at the factory. The Buildings panel hangs by the shop spot, the Click upgrades panel on the other side of the pedestal (`layout.clicks`). A second player must not see your panels, shelf or tooltips. Rows show affordable / unaffordable / locked looks, text left-aligned.
2. **Toggle:** tap ×1 ×5 ×10 Max on each panel. The chosen amount is highlighted, `bulk_toggle` ticks, every price and +CPS/+CPC rolls (about 8 ticks) to the new value and ends on the exact formatted number. Rolling 1K -> 1M should look even, no stutter, no flicker. Toggle quickly several times: no jump. Each panel keeps its own mode; relogging resets to ×1.
3. **Buy in bulk:** with exactly enough for ×5 buy ×5 (one sound, one effect, counts +5). With less than ×5 try buying: nothing is spent, `cant_afford` sounds. On Max the row shows "×N" and the total; with nothing affordable it shows the price of 1 in the unaffordable look. Near `max-level` on Baker's Strength a ×10 buys the levels left.
4. **Upgrades and shelf:** buy Wooden Spatula: sparkle at the row, the icon glides to the shelf under the clicks panel (smooth, not teleporting) and lands with a chime and glint. The row leaves and the next tool moves up. Baker's Strength stays pinned and gets more expensive per level. Click a shelf icon: the action bar shows its name and effect for about 3 s. Relog: the shelf is rebuilt with all bought upgrades, without a fly-out.
5. **Cookie spin:** click the cookie as fast as you can for 10 s, then stop. It must keep turning and bobbing at the same speed the whole time, without freezing or jumping, and continue smoothly after the last click. Repeat with a friend clicking at the same time (each sees their own cookie).
6. **Numbers:** HUD, panels, floating "+N" and messages use K, M, B, T, Qa ... Qa and above are hard to reach by playing; edit the saved cookie count in the test database to see them and the scientific fallback.
7. **Lifecycle:** `/bakery reload` with panels and a full shelf: everything respawns once, no duplicates. Walk away and back (despawn/respawn), change world, quit and rejoin, `/stop`: `/minecraft:kill @e[tag=cookieclicker]` afterwards must find nothing left behind.
8. **Existing saves:** load a save from before this feature: bought upgrades are level 1 on the shelf, cookies and buildings unchanged.

**Profile with spark** (4 players if possible, two panels visible, full shelf, belt running, everybody spamming clicks): the plugin's share of the tick should be well under 1 ms (about 2 % of 50 ms) averaged, and no single tick above about 1 ms from `StationViews.animationTick`. Also watch entity counts (`/spark healthreport` or F3 on the client): roughly 50-80 plugin entities per player on the shelf-full plot, and none after leaving or disabling.
