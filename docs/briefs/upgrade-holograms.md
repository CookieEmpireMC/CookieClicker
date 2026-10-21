# upgrade-holograms

**Status:** in progress  <!-- planned | in progress | review | done -->
**Created:** 2026-10-21 · **Approved:** 2026-10-21 · **Depends on:** conveyor-belt (merged, PR #8)

## Goal
Make spending cookies feel like a real incremental game, in the world instead of a chest menu:
1. **Two hologram shop panels** by the factory: **Buildings** (automated income, the current shop panel) and a new **Click upgrades** panel (manual clicks, GDD §4.4).
2. **Buy in bulk:** a ×1 / ×5 / ×10 / Max toggle on each panel, with prices for the chosen amount, which roll smoothly to their new value when you switch.
3. **Short number format everywhere:** 1.5K, 2.3M, 4.1B, 7.8T, 1.2Qa, Qi, Sx, Sp, Oc, No, Dc … Vg …
4. **An upgrade shelf** that shows every one-time upgrade you've bought.
5. **Bug fix:** the giant cookie stops spinning (or never spins) when you click it.

User choice (2026-10-21): holograms instead of the GDD §4.7 chest GUI. The GDD's "Differences from the current build" gets a line for this.

## Player story
I walk up to my factory. Left of the pedestal hangs the **Buildings** panel, and on the other side the **Click upgrades** panel. At the top of each panel I tap **×1 ×5 ×10 Max**, and the chosen amount lights up. Every price on the panel rolls up or down to the cost of that many, written short ("12.4K", "3.1M"), like a counter ticking over. On Max, each row also shows how many I'd get ("×37"). The cookie keeps spinning smoothly however fast I click it. I buy a Wooden Spatula: its icon pops out of the row with a sparkle, flies over to the **upgrade shelf** under the panel and lands there with a soft chime. The row moves up and the next tool slides in. "Baker's Strength" (+1 per click) stays in the list: I can buy it again and again, and each level costs a bit more. When I tap an icon on the shelf, the action bar tells me what it is and what it does.

## Acceptance criteria
**Panels**
- [ ] Two per-player hologram panels at the factory, built on the existing station panel code (TextDisplay rows, icon, click hitbox, only the owner sees their own panel): `buildings` at `layout.shop` (today's panel) and `clicks` at the new `layout.clicks`. Each panel has a header and at most `station.panels.<panel>.max-rows` rows.
- [ ] **Buildings panel:** unchanged rows (icon, name, owned, cost, +CPS), but the cost and +CPS are for the chosen bulk amount.
- [ ] **Click upgrades panel:** the repeatable upgrade(s) are pinned at the top (name, level, cost for the bulk amount, +CPC). Below them are the cheapest one-time upgrades you haven't bought (name, cost, effect). Bought one-time upgrades leave the list, and the rest move up.
- [ ] Rows keep the affordable / unaffordable / locked looks and the left-aligned text from conveyor-belt.

**Bulk buying**
- [ ] Every panel has a toggle row **×1 ×5 ×10 Max** under its header. Clicking it cycles the mode, and the chosen mode is highlighted. The choice is remembered per player and per panel for the session (default ×1).
- [ ] Buying with ×N buys exactly N if you can afford all N (cost = the sum from `docs/balance/model.md`), otherwise nothing, with the "can't afford" feedback. There are no partial buys. **Max** buys as many as you can afford right now (at least 1). Its row shows that count and its total cost, or the cost of 1 (unaffordable look) when you can't afford any. One-time upgrades are always bought once, whatever the toggle says. A repeatable upgrade with fewer levels left than N shows and buys the levels that are left (orchestrator decision 2026-10-21, so ×10 near `max-level` doesn't refuse everything).
- [ ] Max count is computed in closed form (geometric series, `docs/balance/model.md`), not by looping, and is capped by `max-level` for repeatable upgrades.
- [ ] **Smooth price transitions:** when the mode changes, or a purchase changes the price, every visible price and +CPS/+CPC value rolls from the old value to the new one over `station.panel.tween.ticks` (default 8, eased, interpolated in log space so 1K → 1M looks even), with a short scale pulse on the row text through display interpolation. While a tween runs, a row's text updates at most every `station.panel.tween.step-ticks` (default 2). The 1 Hz refresh never cuts a tween short or makes it jump. A tween always ends on the exact formatted value.
- [ ] Bulk buys are atomic: one purchase sound/effect, one save, and one HUD/panel refresh.

**Upgrades (economy)**
- [ ] `upgrades.yml` holds the GDD §4.4 click upgrades as one-time upgrades (Wooden Spatula … Netherite Hands as `click-add`, Handy Fingers I–III as `click-cps-fraction`, where II and III bring the total to 3% and 5%) plus at least one **repeatable** upgrade (`bakers_strength`, +1 CPC per level, cost growing per level). All numbers come from the balance-designer, and none are in code.
- [ ] `docs/balance/model.md` gets **repeatable upgrades** (format: `max-level`, `cost-growth`, the effect per level; state: upgrade id → level) and the simulator, golden values and `balance-check` cover them.
- [ ] Saved player data stores upgrade **levels**. Existing saves (the set of upgrade ids) load as level 1, with no data loss.

**Fly-out and shelf**
- [ ] Buying a one-time upgrade plays `upgrade_bought` at the row. The row's icon then glides along an interpolated path (no per-tick teleports, like the belt cookies) to its slot on the **shelf** and lands with `upgrade_shelved` and the `upgrade_shelved` sound.
- [ ] The **shelf** is a per-player grid of small icons, one per bought one-time upgrade, under the click upgrades panel (config `station.shelf.*`: offset, columns, spacing, scale). Clicking an icon shows its name and effect in the action bar.
- [ ] Entity cost: shelf icons count toward the view's entity budget. At most `station.shelf.max-icons` icons are shown.

**Numbers**
- [ ] `NumberFormatter` writes short suffixes from 1,000: `1.5K`, `2.3M`, `4.1B`, `7.8T`, `1.2Qa`, then Qi, Sx, Sp, Oc, No, Dc, UDc, DDc, TDc, QaDc, QiDc, SxDc, SpDc, OcDc, NoDc, Vg, UVg, … (the list lives in `messages.yml`; past the last suffix it uses scientific notation). It still truncates and never rounds up.
- [ ] Used everywhere numbers show: panels, HUD, messages, the floating "+N" click numbers.

**Bug fix: cookie spin**
- [ ] The cookie keeps spinning and bobbing at its normal speed while you click it, at any click rate, and never freezes or jumps. Likely cause (verify): a display has one interpolation, and every `PopJob` step sends `setTransformation` with the *current target* rotation and the pop's short duration. The spin then reaches its segment target within the pop's 2–3 ticks and sits still until the next segment, and with steady clicking it keeps getting re-pinned. Fix direction: each pop step (and its end) also sets translation/rotation from `CookieMotion` at `now + step ticks`, so spin, bob and squash travel together in one interpolation, and the motion task resumes cleanly after the pop. Split: the vfx-designer adds a motion source to the pop API (`vfx` package); the plugin-coder passes `CookieMotion` from `StationViews` and makes the motion task pick up cleanly after a pop.
- [ ] Tests: `CookieMotion`-driven pop keyframes (rotation advances by the step's ticks, scale from the pop), and no frozen segment after a pop ends.

**Quality**
- [ ] Tests: bulk cost, Max count (exact boundary: affordable N but not N+1, 0 affordable, `max-level` cap), tween values (start, end exact, monotonic) and atomic bulk buy, repeatable upgrade levels and costs against the golden values, the save migration (set → levels), the formatter edges (999 → `999`, 1,000 → `1K`, 999,999 → `999.9K`, every suffix boundary, scientific past the end), panel row ordering, and the toggle cycle.
- [ ] All checks green (`check`, `validate-assets`, `sound-check`, `balance-check`), and `ownership-check` passes on every branch.
- [ ] Performance: two panels plus a full shelf for 4 players stays well under 1 ms/tick (an estimate in the integration review).

## Out of scope
- Recipes (§4.5), the full 11-building roster (§4.2), golden cookies, prestige: later features.
- The chest GUI (§4.7). Holograms replace it.
- Selling (the GDD says no).
- Building milestones (§4.3) and the stats hologram (§3.1): later features.

## Assets needed
| Kind | What | Owner |
|---|---|---|
| model / texture | `cookieclicker:upgrade/<id>` icon for every click upgrade (§4.4 tools and hands, plus `bakers_strength`); `layout.clicks` spot in `factory.yml` (plus `default` if it has a shop); update `design/` spec | model-designer |
| sound | `upgrade_shelved`: a soft "clink" as an icon lands on the shelf; `bulk_toggle`: a light UI tick | sound-designer |
| effect | `upgrade_bought`: a bright sparkle burst at the row as the icon pops out; `upgrade_shelved`: a small glint where it lands | vfx-designer |

## Balance impact
New: all §4.4 click upgrades and the repeatable `bakers_strength` (base cost, growth, +CPC per level, optional `max-level`). The model gets repeatable upgrades. `balance-check` has to stay green against `docs/balance/targets.md`. The current buildings stay as they are.

## Research needed
None. The fly-out reuses the display interpolation that the belt already uses.

## Open questions
None. Decided 2026-10-21: ×1/×5/×10/**Max**; smooth price tweens; cookie spin fix included. Milestones (§4.3) and the stats hologram (§3.1) are later features.

## Contract (fixed names; change only via the orchestrator)
| Kind | ID | Owner | Notes |
|---|---|---|---|
| item model | `cookieclicker:upgrade/<upgrade_id>` | model-designer | one per upgrade id below; vanilla fallback per upgrade in config |
| layout key | `factory.yml: layout.clicks` | model-designer | `{at: [x, y, z], yaw}` like `layout.shop`, on the other side of the pedestal |
| upgrade | `bakers_strength` | balance-designer | repeatable, +CPC per level |
| upgrade | `wooden_spatula`, `stone_rolling_pin`, `iron_rolling_pin`, `golden_glove`, `diamond_fingers`, `netherite_hands`, `handy_fingers_1`, `handy_fingers_2`, `handy_fingers_3` | balance-designer | one-time, §4.4 |
| model format | `upgrades.yml: upgrades.<id>.{max-level, cost-growth}` | balance-designer | absent = one-time (as today) |
| sound event | `upgrade_shelved` | sound-designer | key `cookieclicker:ui.upgrade_shelved`, fallback `minecraft:block.amethyst_block.chime` |
| sound event | `bulk_toggle` | sound-designer | key `cookieclicker:ui.bulk_toggle`, fallback `minecraft:ui.button.click` |
| sound event | `purchase` | sound-designer (exists) | also used for upgrade buys |
| effect | `upgrade_bought`, `upgrade_shelved` | vfx-designer | placeholders allowed |
| effect | `purchase` | vfx-designer (exists) | building buys, unchanged |
| Java API | `Effects.pop(Display, Player, String presetId, PopMotion motion)` + `vfx.PopMotion` (`Transformation`-free: translation + left rotation at `ticksAhead`) | vfx-designer | every pop step sets translation/left rotation from `motion` at the step's end tick, scale from the preset; the old overloads keep today's behaviour |
| message key | `panel.buildings.*`, `panel.clicks.*`, `panel.bulk.*`, `panel.shelf.*`, `upgrades.<id>.{name, effect}` | plugin-coder | messages.yml |
| message key | `numbers.suffixes` | plugin-coder | now starts at 10^3: K, M, B, T, Qa, … |
| config key | `config.yml: station.panels.{buildings,clicks}.{offset, max-rows}`, `station.panel.bulk.*`, `station.panel.tween.{ticks, step-ticks, pulse-scale}`, `station.shelf.{offset, columns, spacing, scale, max-icons}`, `station.upgrade-fallback-items` | plugin-coder | |

## Tasks
| Agent | Task | Needs (gate) | Branch |
|---|---|---|---|
| balance-designer | repeatable upgrades and the bulk / Max-affordable closed form in `model.md`, simulator, golden values; §4.4 upgrades + `bakers_strength` in `upgrades.yml`; report | contract | `upgrade-holograms/balance-designer` |
| model-designer | upgrade icons; `layout.clicks`; spec | contract | `upgrade-holograms/model-designer` |
| sound-designer | `upgrade_shelved`, `bulk_toggle` | contract | `upgrade-holograms/sound-designer` |
| vfx-designer | `upgrade_bought`, `upgrade_shelved` presets; `PopMotion` + motion-aware `Effects.pop` overload (+ tests) | contract | `upgrade-holograms/vfx-designer` |
| plugin-coder (economy) | `economy/`: repeatable upgrades, bulk cost and Max count (exactly as model.md); `storage/`: upgrade levels + migration from the id set; `format/NumberFormatter` short suffixes + `messages.yml: numbers`; keeps today's panel compiling (×1 behaviour) | balance-designer merged | `upgrade-holograms/plugin-coder-economy` |
| plugin-coder (panels) | `view/`, `hud/`, `config.yml: station.*`, `messages.yml: panel.*, upgrades.*`: two panels, ×1/×5/×10/Max toggle with price tweens, shelf and fly-out, `bulk_toggle`/`upgrade_*` wiring; cookie spin fix with `PopMotion` | economy part and vfx-designer merged | `upgrade-holograms/plugin-coder-panels` |
| plugin-tester | review each branch, then integration (incl. performance) | owner DONE | (reviewed branch) |

## Status
| Agent | Status | Branch | Verdict | Notes |
|---|---|---|---|---|
| balance-designer | merged | `upgrade-holograms/balance-designer` | checks (balance-check 6/6) | contract changes: bulk cost rounded once per batch, upgrades saved as id → level, costs > 0. Known red until the economy part: GoldenValuesTest, test_empty_upgrades_map_is_valid |
| model-designer | merged | `upgrade-holograms/model-designer` | asset-only (checks) | f606b10; 10 icons placeholder; `layout.clicks` at [16,0,6] |
| sound-designer | merged | `upgrade-holograms/sound-designer` | asset-only (checks) | 72df49a; sounds placeholder until listened to |
| vfx-designer | merged | `upgrade-holograms/vfx-designer` | READY (Gandalf) | a648c97 + tests 8067d28; LOW: upgrade_shelved cost doc says 10, is 16 |
| plugin-coder (economy) | merged | `upgrade-holograms/plugin-coder-economy` | READY (Gandalf) | all checks green again; review `docs/reviews/2026-10-21-upgrade-holograms-economy.md`; LOWs: v2→v3 ALTER not idempotent (use IF NOT EXISTS), unused groupingSeparator |
| plugin-coder (panels) | in progress | `upgrade-holograms/plugin-coder-panels` | | launched 2026-10-21 |
| plugin-tester | planned | | | |

## Merge order
1. model-designer
2. sound-designer
3. vfx-designer
4. balance-designer
5. plugin-coder (economy), launched once 4 is merged
6. plugin-coder (panels), launched once 3 and 5 are merged

## Needs human review
- [x] Balance: the click upgrade prices and the `bakers_strength` curve (balance report before/after). Approved by the user on 2026-10-21 on the condition that more buildings follow. If the roster stays at 2 buildings, the late click upgrades (Diamond Fingers, Netherite Hands, Handy Fingers) need retuning, because their saves run up to about 1.5 h. This goes into the PR's follow-ups.
- [ ] Listen: `upgrade_shelved`, `bulk_toggle`.
- [ ] Check in game: both panels with and without the pack, the ×1/×5/×10/Max toggle and how smooth the price roll looks, the cookie spin while clicking fast, the fly-out to the shelf, shelf tooltips, and number formatting in the HUD and panels.
