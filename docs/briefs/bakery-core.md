# bakery-core

**Status:** in progress  <!-- planned | in progress | review | done -->
**Created:** 2026-10-18 · **Approved:** 2026-10-18 20:40 · **Depends on:** none (first gameplay feature)

## Goal
The first playable vertical slice of **Cookie Empire**: a per-player bakery station with a giant clickable 3D cookie, crits, a two-building shop panel, an action-bar HUD, per-player saving, and admin commands. Small on purpose: it's also the first real end-to-end test of the agent workflow, so every agent does one focused piece.

## Player story
An admin runs `/bakery station set` somewhere in the world. When I walk up, I see my own bakery: a giant cookie slowly spinning and bobbing on a pedestal, and a floating panel beside it. I click the cookie: it squashes and pops, crumbs fly, a crunchy click plays (never quite the same twice), and a "+1" floats up and fades. Sometimes it's a **crispy crit**: ×10 cookies, a golden sparkle burst, a special sound and a big number. My action bar shows my cookies and cookies per second next to a little cookie icon, with big numbers written as "1.2 million". When I can afford a Villager Baker or a Wheat Farm, its row on the panel lights up; clicking buys one with a satisfying sound and effect, and if I can't afford it I get a soft "nope". My buildings bake while I'm online, and my progress is still there after a restart. Other players at the same spot see only their own bakery.

## Acceptance criteria
**Gameplay**
- [ ] `/bakery station set` places a station at the admin's location (saved with the world **key**, not name); `/bakery station remove` removes the nearest; `/bakery reload` reloads config, messages, sounds, effects and balance without orphaning anything. Permission `cookieempire.admin`.
- [ ] Per-player: every display, text and panel at a station is visible only to its owner player; players near the same station never see each other's UI or numbers.
- [ ] Giant 3D cookie: custom item model on an `ItemDisplay` on a pedestal, an `Interaction` entity as the hitbox, slow idle spin + bob via client-side display interpolation (no per-tick teleports).
- [ ] Click: cookies += click value; squash/pop on the cookie display, crumb burst, click sound (3+ variants, random pitch), floating "+N" that rises and fades.
- [ ] Crispy crit: chance and multiplier from config (target 5 %, ×10); golden sparkle burst, distinct crit sound, bigger floating number.
- [ ] HUD: action bar with cookie glyph + cookies + CPS, refreshed about once per second; big numbers formatted ("1.2 million", "3.4 billion"), suffixes in `messages.yml`.
- [ ] Panel: one floating world panel next to the cookie with 2 rows (Villager Baker, Wheat Farm): icon, name, owned, cost, CPS. Click a row = buy one. Affordable vs unaffordable look clearly different; purchase plays a sound + small effect; can't-afford plays a soft error sound.
- [ ] CPS ticks once per second while the player is online.
- [ ] Progress saves per player **asynchronously** (join → load, quit → save, autosave interval, flush on disable) and survives restart and `/bakery reload`.
- [ ] Resource pack: the plugin sends our pack (URL + SHA-1 from `config.yml`) on join; vanilla fallbacks work when it's declined.

**Quality**
- [ ] All checks green on a clean tree: `check`, `validate-assets`, `sound-check`, `balance-check`, `tools-test`, and `ownership-check` for every agent branch.
- [ ] No orphaned entities after `/bakery reload` or a restart: every spawned entity is non-persistent and tagged, tracked, removed on reload/disable/chunk unload, and swept on enable. The tester checks every cleanup path.
- [ ] Every sound key and effect ID used in code exists in `sounds.yml` / `effects.yml` and the pack.
- [ ] No balance number in Java; all text in `messages.yml` via MiniMessage; entity/display work on the main thread, I/O async.
- [ ] Unit tests: economy math (costs, CPS, crit), number formatting, save/load round trip (incl. missing/corrupt data), golden values from the simulator.
- [ ] The PR has a step-by-step in-game test guide and lists every texture, sound and effect for review (`mise run review`).

## Out of scope
Building models growing around the station, more buildings, upgrades, golden cookies, Oven Heat/Frenzy, achievements, prestige, social, ItemsAdder/ModelEngine, offline earnings, leaderboard.

## Assets needed
| Kind | What | Owner |
|---|---|---|
| model / texture / glyph | 3D cookie item model (built on the existing 64×64 cookie texture), 2 building icons, HUD cookie glyph (font), panel background if the research recommends one, `pack.mcmeta` | model-designer |
| sound | `cookie_click` (3+ variants), `cookie_crit`, `purchase`, `cant_afford` | sound-designer |
| effect | Effects engine (first one) + `cookie_click`, `cookie_crit`, `purchase` presets incl. floating text and the cookie squash/pop | vfx-designer |

## Balance impact
New: `buildings.yml` with 2 buildings, click value, crit chance/multiplier. Targets (user): first Villager Baker within ~20 s of clicking; first Wheat Farm within ~3 min; no dead zone over 60 s in the first 15 minutes. The crit is a formula change → `docs/balance/model.md` + simulator.

## Research needed
For the researcher (Citation Cindy), one note per topic in `docs/research/`, each ending with a Hand-off:
1. `display-cookie.md`: ItemDisplay with a custom `item_model` + `Interaction` hitbox; transformation interpolation for idle spin/bob and a squash/pop; how the click event looks on 26.3.
2. `per-player-visibility.md`: hiding entities by default and showing them to one player (exact 26.3 API), incl. Interaction entities and chunk/rejoin behaviour.
3. `world-panel.md`: building a clickable panel from TextDisplay/ItemDisplay + Interaction per row; affordable/unaffordable styling options; text backgrounds.
4. `hud-glyph.md`: a bitmap font glyph in the action bar via MiniMessage `<font:…>` on 26.3.
5. `resource-pack.md`: 26.3 pack format; sending a pack with the Paper API (URL + SHA-1, required/optional, prompt), coexistence with ItemsAdder's pack on the same server; also how to ship DB drivers (H2/MariaDB) to the server: plugin.yml `libraries:` vs shading.

## Changes to your proposed split (and why)
1. **The coder starts right away, in two passes.** Most of Wesly's work (economy, saving, commands, station config, number formatting, HUD text, sound service) doesn't call the Effects engine and doesn't need the research on displays. **Pass 1** starts immediately in parallel with everyone. **Pass 2** (cookie and panel entities, click handling with effects, cleanup, pack sending) starts when the research notes **and** Larry's Effects engine are merged into the feature branch. That keeps your rule (the coder waits for Effects) without idling Wesly for the whole first phase.
2. **The sound designer and the balance designer start immediately too.** Neither needs the display research.
3. **Pixel Pat's existing cookie art comes in first.** His 64×64 cookie (`cookie-art/model-designer`) gets a review from Gandalf and is merged at the start, so Pat's new work builds on it (3D model, icons, glyph) instead of starting over.
4. **Pat and Larry wait for the research** (cookie model display transforms, panel approach, glyph font, particle/display API).

## Decisions (user approved the brief with the recommended defaults)
1. **Storage:** database as decided earlier: H2 embedded by default, MariaDB optional, configured in `config.yml` (`database.*`).
2. **Pack hosting for the in-game test:** a GitHub release asset (or any https URL); URL + SHA-1 go in `config.yml`. Only needed for the in-game test, not blocking.
3. **Names:** game "Cookie Empire", command `/bakery`, permission `cookieempire.admin`; plugin `CookieClicker`, namespace `cookieclicker`.

## Contract (fixed names; change only via the orchestrator)
| Kind | ID | Owner | Notes |
|---|---|---|---|
| item model | `cookieclicker:cookie` | model-designer | 3D cookie for the ItemDisplay |
| item model | `cookieclicker:building/villager_baker`, `cookieclicker:building/wheat_farm` | model-designer | panel icons |
| font glyph | font `cookieclicker:hud`, char `` (cookie) | model-designer | action bar |
| sound event | `cookie_click`, `cookie_crit`, `purchase`, `cant_afford` | sound-designer | keys `cookieclicker:ui.<event>`; vanilla fallbacks in `sounds.yml` |
| effect | `cookie_click` (crumbs + "+N"), `cookie_crit` (golden sparkles + big number), `purchase` | vfx-designer | placeholder `amount`; per-player |
| vfx API | `Effects.play(id, loc, viewer, placeholders)`, `Effects.pop(display, viewer)` | vfx-designer | squash/pop on an existing display |
| building | `villager_baker`, `wheat_farm` | balance-designer | `buildings.yml` |
| balance keys | `click.base-power`, `click.crit-chance`, `click.crit-multiplier` | balance-designer | `upgrades.yml` / model.md |
| command | `/bakery station set\|remove`, `/bakery reload` | plugin-coder | permission `cookieempire.admin` |
| config | `config.yml`: `database.*`, `resource-pack.url/sha1/required/prompt`, `hud.*`, `station.*`; `stations.yml` | plugin-coder | |
| messages | `messages.yml` (HUD, panel rows, number suffixes, commands) | plugin-coder | MiniMessage |

## Tasks
| Agent | Task | Needs (gate) | Branch |
|---|---|---|---|
| resource-pack-researcher | the 5 research notes above | brief approved | feature branch (docs) |
| plugin-tester | review existing `cookie-art/model-designer` | brief approved | (reviewed branch) |
| balance-designer | crit in model.md + simulator, `buildings.yml`, `upgrades.yml` click/crit, targets per your numbers, report + golden values | brief approved | `bakery-core/balance-designer` |
| sound-designer | 4 events, 3+ click variants, `sounds.yml`, `sounds.json`, subtitles | brief approved | `bakery-core/sound-designer` |
| plugin-coder (pass 1) | economy (model.md), storage (H2/MariaDB, async), number formatting, sound service, HUD text, station config, commands, reload | brief approved | `bakery-core/plugin-coder` |
| model-designer | 3D cookie model, 2 icons, HUD glyph font, panel bg, pack.mcmeta | research notes + cookie art merged | `bakery-core/model-designer` |
| vfx-designer | Effects engine + `cookie_click`, `cookie_crit`, `purchase`, pop | research notes | `bakery-core/vfx-designer` |
| plugin-coder (pass 2) | cookie + panel entities, per-player visibility, click/crit/purchase wiring, cleanup, pack sending | research + **vfx merged** + pass 1 merged | `bakery-core/plugin-coder-2` |
| plugin-tester | review every branch, unit tests, integration review | each owner DONE | (reviewed branch) |

## Status
| Agent | Status | Branch | Verdict | Started | Done | Notes |
|---|---|---|---|---|---|---|
| resource-pack-researcher | in progress | feature branch (docs) | | 20:40 | | 5 notes |
| plugin-tester | in progress | `cookie-art/model-designer` | | 20:40 | | review existing cookie art |
| balance-designer | in progress | `bakery-core/balance-designer` | | 20:40 | | |
| sound-designer | in progress | `bakery-core/sound-designer` | | 20:40 | | |
| plugin-coder (pass 1) | in progress | `bakery-core/plugin-coder` | | 20:40 | | |
| model-designer | waiting | | | | | gate: research notes + cookie art merged |
| vfx-designer | waiting | | | | | gate: research notes |
| plugin-coder (pass 2) | waiting | | | | | gate: research + vfx + pass 1 merged |

## Merge order
1. research notes (as they land) · `cookie-art/model-designer`
2. model-designer, sound-designer
3. vfx-designer
4. balance-designer
5. plugin-coder (pass 1), then plugin-coder (pass 2)

## Needs human review
- [ ] Every texture, sound and effect (`mise run review`)
- [ ] Balance numbers + report vs your targets
- [ ] In-game test guide (in the PR)
- [ ] `mise.toml`, `.gitattributes` or human-controlled changes, if any
