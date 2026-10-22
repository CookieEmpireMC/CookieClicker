# plot-polish: integration review

Date 2026-10-22 · Reviewer: Gandalf · Branch: `feature/plot-polish` (head 2d3805f + this commit) · Brief: `docs/briefs/plot-polish.md`

**Target:** Paper `26.3.build.205-stable`, Java 25, `api-version 26.3` (unchanged from the branch reviews; no version, shading or NMS findings).

## Summary of the branch reviews (all READY)
| Branch | Verified at | Result |
|---|---|---|
| plugin-coder (plot): decor parser + keep-out, LoopPacer, belt cookie pose | coder dae2d0d, tests f57fe49 | READY, LOWs below |
| plugin-coder (panels): `PanelLayout` ray pick, `StationViews.aim`, `TextPadding` | coder 79ad7c0, tests d2e33dd | READY, vertical text anchor needs an in-game check |
| plugin-coder (basket): `BasketLook`, `BasketSettings`, tier-up, respawn | coder 9cea984, tests b3f1b56 | READY, LOWs below |
| plugin-coder (cleanup): dropped legacy hitbox/crackle-every shims, doc fixes | (integration only) | green |
| model-designer / sound-designer / vfx-designer assets | (integration only) | `validate-assets`, `sound-check` green |

## Test results
- `mise run check-quiet`: BUILD SUCCESS, **655 tests, 0 failures, 0 skipped** (the two shipped-decor tests that were skipped on the plot branch now run: `decor.yml` is on this branch and avoids every keep-out cell).
- `mise run validate-assets`: 51 files, 0 problems. `mise run sound-check`: 24 sounds, 0 failed (`basket_tier_up` included). `mise run balance-check`: 6/6 targets pass (no balance change).
- New integration test `view/PanelCookieOverlapTest` (1 test): with the shipped layout geometry (pedestal 18,6, shop 20,6, clicks 16,6, viewpoint 15,2), cookie box at 1.2 / 1.8 / 2.088 (golden_4) / 2.2 high: the cookie box never sits in front of a panel's clickable text when the player stands in front of it or looks at the clicks panel from the viewpoint.

## Integration findings (how the parts fit)
| Check | Result |
|---|---|
| Panel picking vs the moving cookie hitbox | OK. `aim()` takes the cookie box from `cookieHitbox.getBoundingBox()` (moves with the basket) and the nearest hit wins. Cookie box is 1.5 wide / 1.7 high; panels' text columns start 1.1 blocks from the pedestal axis, so the box only shadows the pedestal-side edge of a panel when the player stands on the pedestal side at a shallow angle (see LOW 1). |
| Basket width (golden 1.375) vs panels and belt end | OK. Golden half width 0.69 -> x 17.81..19.19; panel columns (1.8 wide) end 0.4 blocks away on both sides (shop 19.6.., clicks ..17.4). Z 5.81..7.19: it reaches 0.19 into the belt's last cell; belt cookies leave at z 7.0 at y ~1.06, above the basket's pile edge and posts (0.69), so they drop onto the basket instead of clipping it. Within the brief's "pedestal cell +-0.2". |
| Decor keep-out vs basket/panels | OK. Front zone is x 12..23, z 1..8 and the belt zone x 17..19, z 6..12: basket, both panels, the shelf and the click rows are all inside; the shipped decor test confirms no block in them. |
| LoopPacer vs ChimneySmoke / belt after reloads | OK. `SoundService.loops` survives `load()` (reload keeps the beat), new timers start at `loopDelay`, both `ChimneySmoke.loopTimer` and the belt use `playLoop`; `ConveyorBelts.onQuit` forgets loops (shared by crackle and hum). Server restart resets the tick counter (`now < before` is handled). Disabling and re-enabling the plugin (new `SoundService`) can double a loop for at most one period (<= 5 s), LOW. |
| config.yml vs code defaults | OK. Shipped-file tests exist and pass for station (cookie, panel, basket), house and belt (`...MatchesTheCodeDefaults`). Message keys of the contract all exist (`upgrades.<id>.short`, `panel.row.locked`, `panel.clicks.*.locked`, `need-baked`, `need-building`); `MessagesTest` strict-parses messages.yml. |
| Contract IDs | `cookieclicker:cookie_basket` (items/, 15 models, 3 textures), `basket_tier_up` in sounds.yml / sounds.json / effects.yml all exist and are used once in `StationInteractions`. Sounds only through `SoundService`, effects only through `Effects`. |
| Cleanup's config -> view import | `config.StationVisualSettings` imports `view.PanelLayout` (`ROW_LINES`) and `view.TextWidth`: config and view now depend on each other (view already imports config). Pure constants, no runtime problem; reported as LOW: move `ROW_LINES` into `TextWidth`. |

## Performance
- Click path: `aim()` = one AABB ray trace + one `PanelLayout.pick` per panel (two panels, a handful of doubles and a small record each). Under 5 us per click.
- `updateBasket`: 2-4 `Math.log` and one small record per in-range view per refresh (20 ticks) and per cookie click (0.2 us). `animationTick` adds a null check per view per tick. Entity count is unchanged (basket replaces the pedestal entity).
- `PlotSetup` places about 360 decor blocks in the same main-thread step as the house and fence (one-off admin command, no physics). Loop timers: one task per loop per player in the plot, firing every 60-100 ticks.
- PERF-HIGH: none. PERF-LOW: `TextPadding.lines` re-parses MiniMessage per row render (only when the text changed).

## Bugs
None HIGH or MEDIUM. **VERDICT: READY** (the in-game checks below are not blocking for the merge, but the panel text anchor check (3) decides whether row clicks hit).

## Follow-ups (LOW, for the PR; no fix round)
1. Clicks panel from the plot viewpoint (15,2) is in reach; the shop panel (20,6) is 6.4 blocks from it, beyond `click-distance` 6. The few shop points in reach (its pedestal-side edge) are behind the cookie box. Players must step closer; consider `click-distance` 7 or a viewpoint closer to the shop.
2. Cookie hitbox teleports at once while the cookie glides for `move-ticks` (10): clicks during a glide aim at the final height.
3. Brief says the look switches at most once per refresh, but `update()` also runs on every cookie click; still at most one change per call and hysteresis keeps it rare.
4. `StationViews` is 1,000+ lines; basket logic (`updateBasket`/`showLook`/`moveUp`) would fit a `CookieBasket` helper.
5. `config.StationVisualSettings` -> `view.PanelLayout.ROW_LINES` (see above): move to `TextWidth`.
6. Decor uses one height for all columns while setup accepts ground variance up to 2 (like the house); on uneven ground decor can float or sink. Use per-column ground like `FenceLayout`.
7. Legacy leftovers in panels: `Row` 6-arg ctor, `hitbox-*` reads, `rowPitch` hardcodes `2*10` and `/40`; `ClickRouter` Javadoc line ~190 chars; `text-yaw-offset` 180 would mirror the text and `pick` ignores it (default 0 is fine); `pulse-scale` is not in the pick bounds.
8. `DecorKeepOut` Javadoc says the door walk is "one column wide" but the code is the bounding box of gate and door (conservative).
9. The vanilla fallbacks of the three loops (beehive.work, campfire.crackle, furnace.fire_crackle) have other lengths: gap-free looping only with our pack (documented in config comments).
10. An admin who set `belt.surface-yaw: 180` as the old workaround now gets the slats reversed.
11. Brief wording: `need-building` is `Own <owned> <building>` (no "to unlock"; it would wrap to three lines at 120 px).
12. `design/basket.md` golden_0/1 tops (0.563/0.625) differ from config (0.69) on purpose (corner posts); keep the note in the PR.

## In-game test guide (for the PR)
Needs the resource pack. Set up with `/bakery plot setup`, then walk in.
1. **Panel rows (blocking)**: stand 2-3 blocks in front of the shop panel, aim at the middle of a row's second line, the first line, the icon, the gap between two rows, and just beside the text. Rows must buy only when aimed at text or icon, gaps do nothing. If clicks land half a text height too high or low, the text anchor assumption in `PanelLayout` is wrong (text grows up from its position): report it.
2. **Toggle**: click each of ×1, ×5, ×10, Max (selects that mode) and the gap between labels (cycles). Repeat on the clicks panel and from an angle (not straight on).
3. **Cookie vs panels**: click the cookie from the viewpoint and from each side of the pedestal; click the panels' edges nearest the cookie. Expected: cookie clicks count, panel edges still work except where the cookie visually covers them.
4. **Basket**: with `/bakery` debug or a test balance, check wicker -> bushel (1M) -> golden (1B) and fills in between. The cookie must float above the pile (no clipping, also next to golden's corner posts), glide up when the basket grows, and stay clickable at golden_4 (centre about 2.09). Click during the glide.
5. **Tier-up**: crossing 1M and 1B plays the sparkle and chime once, the look switches at the peak (about 8 ticks). No effect on join, reload, plot re-entry or when the look drops.
6. **Pack respawn**: join without the pack (old pedestal block, cookie at 1.8), accept the pack (basket appears, one flicker).
7. **Belt**: slats move toward the pedestal, cookies lie flat face up, and they arrive over the basket's edge without clipping the golden posts.
8. **Loops**: stand in the plot 2+ minutes: oven hum (4 s), chimney crackle (3 s) and belt rattle (5 s) must have no gaps, doubles or clicks. Reload (`/bakery reload`), leave and re-enter the plot, end the cinematic: still one beat.
9. **Decor**: paths, crates, flowers and lamps around the factory; nothing on the path from the gate to the pedestal, in the panels' space, at the belt or at the door. `/bakery plot setup undo` removes the decor with the rest.
10. **spark**: `/spark profiler start --timeout 60` while clicking the cookie and the panels; the plugin's share of the tick should stay under about 1% (clicks are event-driven, the basket adds almost nothing per tick).
