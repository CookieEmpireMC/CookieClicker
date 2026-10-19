# starter-house

**Status:** in progress  <!-- planned | in progress | review | done -->
**Created:** 2026-10-19 · **Approved:** 2026-10-19 · **Depends on:** bakery-core (merged, PR #4)

## Goal
A **bakery plot** on the map, defined with WorldGuard regions, with one **real, shared house** built from normal server-side blocks. The pedestal, cookie and shop panel stay per-player (each player sees only their own). The first time a player arrives, the house is built in a ~20–30 s cinematic that only they see. Nothing in the world changes for anyone else.

## Player story
I walk onto the bakery plot for the first time and the house isn't there: just a bare patch of ground. Then the house starts going up, just for me. Blocks drop out of the air one by one with a little bounce, bottom layer first, spreading out from the middle. Each one lands with a soft thunk and its own block sound, with hammer taps, foundation dust and construction sparkles around it. When the roof is on, confetti bursts, a short fanfare plays, the title "Your bakery is open!" shows up, and smoke starts curling from the chimney (and keeps going). My cookie, pedestal and shop are waiting in front of it, as before. A friend next to me just saw the house standing there the whole time. When I come back later, the house is simply there.

## Acceptance criteria
**WorldGuard & admin setup**
- [ ] WorldGuard (+ WorldEdit or FAWE) versions that support Paper 26.3 are confirmed by the researcher with sources **before any code is written**; otherwise the feature stops and the user is told.
- [ ] Plot and house areas are WorldGuard regions the admin makes with `//wand` + `/rg define`; the plugin has no selection commands. How regions are found (custom flags vs. names in config) follows the research recommendation.
- [ ] The data model supports **multiple plots** (we start with one).
- [ ] `/bakery plot setviewpoint` and `/bakery plot setpedestal` store position + facing for the plot the admin stands in (saved in `plots.yml`). Cookie and shop offsets stay relative to the pedestal. Existing `stations.yml` entries are migrated once (Decision 2).
- [ ] `/bakery plot show`: particle outline of the plot and house regions, correct for **polygon** regions too.
- [ ] `/bakery house place <design>`: pastes a design as real blocks at the house region's origin, after **backing up** every replaced block; `/bakery house undo` restores the world exactly (block data incl. states; tile-entity contents if any). Ships a `default` design: a small bakery cottage (~9×7×7, chimney, windows, door, sign) in a vanilla palette.
- [ ] If the admin builds the house by hand, no command is needed: the house region is the house.
- [ ] Region enter/leave goes through WorldGuard's `RegionContainer`/`RegionQuery` API, checked **only when the player changes block**, not on every move event.
- [ ] `docs/integrations.md` gets a WorldGuard section (Maven repo `https://maven.enginehub.org/repo/`, `provided` scope, compatibility row, custom flag registration in `onLoad`, RegionQuery usage) and recommended plot flags (e.g. `build deny`, `interact allow` for doors). The plugin never sets flags itself.
- [ ] `/rg redefine` + `/bakery reload` moves or resizes the plot/house without a restart.

**Per-player build cinematic (first visit only)**
- [ ] Starts when the player enters the plot region while standing outside the house region, or after they are moved to the viewpoint (`config.yml`, see Contract).
- [ ] That player gets fake air over the house region (bounding box, keeping only blocks the region contains, so polygons work). Then the real blocks are revealed layer by layer, bottom to top, outward from the centre: a block display drops in with a small bounce, is removed on landing, and the real block is resent to that player.
- [ ] Per block: landing effect + thunk, the block's own vanilla place sound, hammer taps; foundation dust on the first layer; construction sparkle. Completion: confetti, fanfare, title "Your bakery is open!", chimney smoke that keeps going.
- [ ] Other players see nothing unusual. The house is **never** changed server-side by the cinematic.
- [ ] During the cinematic the player is kept out of the invisible-but-solid house (hold at the viewpoint, or slow briefly; configurable; method from research, no kicks or rubber-banding).
- [ ] Safety: quit, death, teleport, world change, `/bakery reload` or disable mid-cinematic → the real blocks are resent immediately (when the player is still online); nobody is left with a missing house. Relog shows the house normally.
- [ ] "Seen" is stored per player per plot, only when the cinematic **completes**. Later visits: no cinematic. `/bakery house rebuild <player>` replays it (for testing).

**Per-player parts**
- [ ] Cookie, pedestal and shop stay visible only to their owner (verified again with 2 accounts).
- [ ] Optional config to hide other players near the pedestal (off by default).

**Performance & safety**
- [ ] Block sends batched per tick (configurable); cap on simultaneous falling block displays; displays removed right after landing.
- [ ] Everything (fake blocks, displays, loops, tasks) cleaned up on reload, disable and quit.
- [ ] The world is only ever modified by `/bakery house place` and `/bakery house undo`.

**Quality**
- [ ] With 2 accounts: both see the same real house; only the first-visit account sees the cinematic; each sees only their own cookie and shop.
- [ ] Works with a polygon house region.
- [ ] All checks green on a clean tree (`check`, `validate-assets`, `sound-check`, `balance-check`, `tools-test`, `ownership-check` per branch). Unit tests for: layer ordering, polygon filtering of the bounding box, backup/undo round trip, design file parsing, plot config + station migration, seen-flag storage (schema migration), cinematic cleanup paths.
- [ ] The PR has an in-game test guide (2 accounts) and lists every new sound and effect for review.

## Out of scope
House tiers/upgrades and per-player building models around the house (later feature `bakery-growth`: keep the plot/house model and the cinematic's reveal engine reusable for it), interior furniture, the lobby/start flow (`game-start`, next), selection commands of our own, setting WorldGuard flags automatically, balance changes. Queued separately: `optimize` (paper-plugin.yml + async).

## Assets needed
| Kind | What | Owner |
|---|---|---|
| house design | `default` bakery cottage as a block list file (~9×7×7, chimney, windows, door, sign, vanilla palette) + chimney-top point, generated by a script, with a spec page and preview | model-designer |
| sound | hammer taps (variants), landing thunk (variants), fanfare, chimney crackle loop | sound-designer |
| effect | foundation dust, block drop + landing bounce, landing puff, construction sparkle, confetti, chimney smoke (loop) | vfx-designer |
| model / texture / glyph | none (falling blocks are `BlockDisplay`s of the real block data) | — |

## Balance impact
none

## Research needed
For Citation Cindy, **blocking**, note `docs/research/worldguard-plot.md` (+ `docs/research/fake-blocks.md`), each with a Hand-off:
1. WorldGuard + WorldEdit/FAWE releases supporting **Paper 26.3** exactly (versions, links, dates). If none: stop, report.
2. Maven coordinates/repo, `provided` scope; hard `depend` vs. soft-depend with a clear startup error; custom `StateFlag` registration in `onLoad` (and what happens on `/reload`); `RegionContainer`/`RegionQuery` usage for "which plot/house is this block in"; reading polygon regions (points, min/max Y) for outlines and bounding-box iteration.
3. Recommendation: custom flags (`bakery-plot`, `bakery-house`) vs. region names in config, incl. how multiple plots map to house regions.
4. Recommended default flags for the plot (build deny, door interact allow, …).
5. Per-player fake blocks on 26.3: `Player#sendBlockChange(s)` / `sendMultiBlockChange` over real blocks; when the client loses/resets them (chunk resend, block interaction/click, distance/view range, respawn, world change); the best way to restore real blocks per player in batches; limits per packet/tick.
6. Keeping a player out of an invisible-but-solid region without kicks or rubber-banding (hold at viewpoint, slowness, velocity push, spectator-free options), and anti-cheat/"moved wrongly" behaviour.
7. `BlockDisplay` for falling blocks: interpolation for a drop + bounce, per-player visibility (reuse `per-player-visibility.md`).
8. Per block: getting the vanilla place sound from `BlockData#getSoundGroup()` on 26.3 (still present? replacement?).

## Open questions
none (decided below)

## Decisions (user, 2026-10-19)
1. **House design file:** `src/main/resources/houses/**` is added to `model-designer` in `.claude/ownership.json` (user-approved human-controlled change; listed under Needs human review). Pat writes `src/main/resources/houses/default.yml`.
2. **Station migration:** `/bakery station set|remove` are replaced by `/bakery plot setpedestal`. On the first load with `plots.yml` missing, every station in `stations.yml` inside a `bakery-plot` region becomes that plot's pedestal; stations outside any plot are logged, kept in `stations.yml.bak`, and stop working.
3. **Chimney smoke:** everyone standing in the plot (and not mid-cinematic) sees the smoke loop as their own per-player particles. The smoke point comes from the design file (`points.chimney`), or `/bakery plot setchimney` for hand-built houses.

## Contract (fixed names; change only via the orchestrator)
| Kind | ID | Owner | Notes |
|---|---|---|---|
| WorldGuard flags | `bakery-plot`, `bakery-house` (**StringFlag**, value = plot id) | plugin-coder | registered in `onLoad` (reuse on `FlagConflictException` if same type); `bakery-plot: <id>` on the plot region, `bakery-house: <id>` on its house region; cache region **ids**, not objects (research: worldguard-plot) |
| house design | `src/main/resources/houses/default.yml` | model-designer | format: `size [x,y,z]`, `origin`, `palette: {char: block data}`, `layers` (bottom→top, rows from z=0 north, chars from x=0 west; `.` = keep existing block (no change, no backup), `_` = explicit air), `signs: [{at, lines}]` (optional); full format in `design/house-default.md`, `points.chimney [x,y,z]`, optional `points.door`; coder parses it, tester tests the parser |
| sound event | `house_hammer` | sound-designer | key `cookieclicker:house.hammer`, 3+ variants, fallback `minecraft:block.anvil.place` (quiet, high pitch) |
| sound event | `house_block_land` | sound-designer | key `cookieclicker:house.block_land`, 3+ variants, fallback `minecraft:block.wood.place` |
| sound event | `house_block_place` | sound-designer | **special**: `key: "@block"` = the block's own vanilla place sound (from its sound group); volume/pitch/source from `sounds.yml`; played only through `SoundService` |
| sound event | `house_fanfare` | sound-designer | key `cookieclicker:house.fanfare`, fallback `minecraft:ui.toast.challenge_complete` |
| sound event | `house_chimney_crackle` | sound-designer | key `cookieclicker:house.chimney_crackle`, loopable 2–4 s, replayed by the smoke loop; fallback `minecraft:block.campfire.crackle`, source `ambient` |
| effect | `house_foundation_dust` | vfx-designer | first layer, per block |
| effect | `house_block_land` | vfx-designer | small puff on landing |
| effect | `house_sparkle` | vfx-designer | construction sparkle around the build |
| effect | `house_confetti` | vfx-designer | completion |
| effect | `house_chimney_smoke` | vfx-designer | looping preset |
| vfx API | `Effects.dropBlock(viewer, BlockData, Location target, Runnable onLanded)` | vfx-designer | per-player `BlockDisplay` (hidden by default, anchored at the block corner), first transformation ≥1 tick after spawn, 3 segments fall/bounce-squash/settle (timings in `effects.yml`), counts toward a new `limits` cap (default 24 per player), removed in the same tick `onLanded` runs; `Effects.cancelFor(viewer)` removes it and **never** calls `onLanded` (research: fake-blocks Q7) |
| vfx API | `Effects.loop(id, Location, viewer) → EffectHandle` (`cancel()`), `Effects.cancelFor(viewer)` | vfx-designer | chimney smoke; all loops/drops stopped on reload/disable/quit |
| vfx API (additions, vfx-designer) | `boolean dropBlock(viewer, data, target, @Nullable Runnable onImpact, Runnable onLanded)`; `loop(id, loc, viewer, @Nullable Runnable onRepeat)`; `EffectHandle{cancel(), isActive(), NONE}`; `activeFallingBlocks()`, `activeLoops()` | vfx-designer | `dropBlock` returns false when it "landed instantly" (cap hit / air / not loaded: callbacks already ran) or did nothing (off main thread, viewer offline/other world). Engine plays `house_block_land` + block particles at impact itself. Callbacks never run after `cancelFor`/quit/reload/shutdown |
| effect | `house_block_drop` | vfx-designer | drop timings (`block-drop` layer), used only by `dropBlock` |
| config | `effects.yml` `limits`: `max-falling-blocks-per-player: 24`, `max-falling-blocks-global: 240`, `max-loops-per-player: 4`; preset `loop-every`, particle `force` | vfx-designer | |
| message keys | `house.cinematic.title`, `house.cinematic.subtitle`, `plot.*`, `house.*` command feedback, `plot.missing-worldguard` | plugin-coder | MiniMessage in `messages.yml`; title text "Your bakery is open!" |
| command | `/bakery plot setviewpoint\|setpedestal\|setchimney\|show`, `/bakery house place <design>\|undo\|rebuild <player>` | plugin-coder | permission `cookieempire.admin` |
| config | `plots.yml`: `plots.<id>.{world, plot-region?, house-region? (optional fallback; flags are the source of truth), viewpoint{x,y,z,yaw,pitch}, pedestal{x,y,z,yaw}, chimney?}`; `house-backups/` (undo data) | plugin-coder | migration from `stations.yml` (Decision 2) |
| config | `config.yml`: `house.cinematic.{enabled, trigger: enter-plot\|viewpoint, teleport-to-viewpoint, blocks-per-tick, max-falling-displays, layer-delay-ticks, keep-out: hold\|slow\|none, fake-air-per-tick, refresh-hidden-ticks, reassert-radius}`, `plot.show.{particle-step, duration-seconds}`, `plot.hide-others-near-pedestal.{enabled, radius}` | plugin-coder | names may shift slightly; the coder reports final keys |
| dependency | `pom.xml`: `com.sk89q.worldguard:worldguard-bukkit:7.0.20`, `com.sk89q.worldedit:worldedit-bukkit:7.4.6` (`provided`, repo `https://maven.enginehub.org/repo/`); `plugin.yml`: `depend: [WorldGuard]` | plugin-coder | WG/WE/FAWE row in `docs/integrations.md`; missing/disabled WG → log `plot.missing-worldguard`, disable |
| keep-out | `hold` = transient `MOVEMENT_SPEED` + `JUMP_STRENGTH` modifiers (-1, `MULTIPLY_SCALAR_1`) + move/teleport guard; removed on every cleanup path and on join | plugin-coder | research: fake-blocks Q6; no `setWalkSpeed`, no spectator |
| storage | schema v2: `<prefix>house_seen(uuid, plot_id, seen_at)` | plugin-coder | migrated from v1 on startup |

## Tasks
| Agent | Task | Needs (gate) | Branch |
|---|---|---|---|
| resource-pack-researcher | `worldguard-plot.md` + `fake-blocks.md` (questions 1–8) | brief approved | feature branch (docs) |
| model-designer | `default` cottage design file + generator script + `design/house-default.md` spec/preview | research OK  | `starter-house/model-designer` |
| sound-designer | 4 new sounds (hammer, block_land, fanfare, chimney_crackle) + `house_block_place` entry, `sounds.yml`, `sounds.json`, subtitles | research OK (WG supported) | `starter-house/sound-designer` |
| vfx-designer | 5 presets + `dropBlock`, `loop`, `cancelFor` API + caps | research OK | `starter-house/vfx-designer` |
| plugin-coder (pass 1) | WorldGuard hook + flags, plots model + `plots.yml` + migration, region tracking on block change, `plot` commands + outline, design parser, `house place`/`undo` with backups, schema v2 seen-store, `@block` sound support in `SoundService`, hide-others option, integrations.md section | research OK | `starter-house/plugin-coder` |
| plugin-coder (pass 2) | cinematic: fake air, layer ordering, batched reveal, keep-out, completion, smoke loop, every safety/cleanup path, `house rebuild` | pass 1 + **vfx merged** (new Effects API) | `starter-house/plugin-coder-2` |
| plugin-tester | review each branch, tests, integration review incl. performance | each owner DONE | (reviewed branch) |
| balance-designer | none | — | — |

## Status
| Agent | Status | Branch | Verdict | Started | Done | Notes |
|---|---|---|---|---|---|---|
| resource-pack-researcher | merged | feature branch (docs) | — | 20:28 | 20:40 | WG 7.0.20 + WE 7.4.6 support 26.3; 2 notes + memory |
| model-designer | merged | `starter-house/model-designer` | READY | 20:39 | 20:55 | default cottage, 170 blocks; needs an in-game look |
| sound-designer | merged | `starter-house/sound-designer` | READY | 20:39 | 20:49 | 4 sounds + @block entry; needs a listen |
| vfx-designer | merged | `starter-house/vfx-designer` | READY | 20:39 | 20:59 | 6 presets + dropBlock/loop/cancelFor; needs in-game look |
| plugin-coder (pass 1) | running | `starter-house/plugin-coder` | | 20:39 | | |
| plugin-coder (pass 2) | planned | `starter-house/plugin-coder-2` | | | | |
| plugin-tester | planned | (all) | | | | |

## Merge order
1. research notes (as they land)
2. model-designer, sound-designer
3. vfx-designer
4. plugin-coder (pass 1), then plugin-coder (pass 2)

## Needs human review
- [ ] Audition sounds: `house_hammer`, `house_block_land`, `house_fanfare`, `house_chimney_crackle` (`mise run review`)
- [ ] Effects: `house_foundation_dust`, `house_block_land`, `house_sparkle`, `house_confetti`, `house_chimney_smoke`, block drop + bounce
- [ ] The `default` cottage design (preview + in game)
- [ ] `.claude/ownership.json` change for the house design path (Decision 1)
- [ ] `.claude/agents/*.md`: new "Memory: check before you look up / fetch" section in every agent file, incl. Gandalf's review ledger (user request 2026-10-19)
- [ ] `pom.xml` WorldGuard/WorldEdit dependencies, `plugin.yml` `depend: [WorldGuard]`
- [ ] In game: cookie/panel clicks still work inside a `build deny` plot (else set `interact allow`, research Q4)
- [ ] In-game test guide with 2 accounts (in the PR)
