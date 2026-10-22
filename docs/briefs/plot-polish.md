# plot-polish

**Status:** in progress  <!-- planned | in progress | review | done -->
**Created:** 2026-10-22 · **Approved:** 2026-10-22 · **Depends on:** upgrade-holograms (merged, PR #10)

## Goal
Polish the plot after the user's in-game test: precise clicks on every hologram line, one-line row texts, a belt
that runs toward the pedestal with cookies lying flat on it, ambient sound without stutter, and a decorated plot
so it no longer feels empty around the fence and factory.

## Player story
As a player I aim at a hologram line and the click lands on exactly that line: the ×1/×5/×10/Max label I look at,
the row I look at, never its neighbour or the cookie. Rows read cleanly in two lines (name, then cost · effect),
like the Bakery Shop rows. The belt carries cookies from the factory hatch to the pedestal, lying flat like on a
real belt, the oven hum, chimney crackle and belt rattle loop smoothly, and the plot has paths, crates, flowers,
lamps and small bakery props that make it feel lived in.

## Acceptance criteria
### Panels (precise clicks)
- [ ] A click on a panel is resolved from the player's **eye ray** against the panel's text plane, not from
      which axis-aligned `Interaction` box was hit: the row or toggle under the crosshair is chosen by its
      rendered bounds (row pitch, text height, text width from `TextWidth`). Clicks between lines or off the text
      do nothing. Panels facing at any angle work the same.
- [ ] Hitboxes no longer overlap each other or the cookie's box (a hit on any panel box goes through the ray
      check; the cookie keeps working).
- [ ] Clicking a label of the toggle **selects that mode** directly (×1, ×5, ×10 or Max); the cycle still
      works as before when the click lands on the toggle but not on one label (e.g. the gaps).
- [ ] Same owner/distance/once-per-tick checks as today; new thresholds come from `config.yml` (`station.panel`).
### Panels (layout, user request)
- [ ] The panel looks designed and spacious: header, toggle and rows are centred on **one shared column**
      (icon + text together, so the toggle no longer looks off-centre next to the icons), each text block has
      padding inside its background (a little space left/right of the text), and there is clear air between
      header, toggle and rows. All spacing values live in `config.yml` `station.panel`, and the precise click
      bounds follow the same values.
### Cookie basket (added 2026-10-22, user request)
- [ ] The pedestal becomes a **cookie basket** (per owner, like the cookie today): an item display with the item
      model `cookieclicker:cookie_basket` that **fills with the cookies in your pouch** (fill 0-4) and **upgrades its
      look at crazy amounts** (tiers `wicker` -> `bushel` -> `golden`, like a Clash of Clans storage).
- [ ] Tier and fill come from `config.yml: station.basket` thresholds (cookies in the pouch, log-scaled by default:
      wicker up to 1M, bushel up to 1B, golden above; each tier's fill steps split its range on a log scale).
      These are visual thresholds, not economy values. The basket switches look at most once per refresh, never
      flickers at a boundary (small hysteresis), and needs no tick work when nothing changed.
- [ ] Size stays in scale with the factory and the decor: the golden tier is at most **1.4 blocks wide and 1.2 high**
      (wicker about 0.9 x 0.6), within the pedestal cell +-0.2, clear of both panels and the belt end.
- [ ] The rotating cookie floats **above the basket**: its centre is `basket top of the current look + gap`
      (heights per look from `design/basket.md`), it moves smoothly (interpolated) when the basket grows, and it
      stays clickable: cookie centre never higher than eye level + 0.6 from the ground in front of the pedestal,
      and the click hitbox follows it.
- [ ] Without our resource pack the old pedestal block is shown (vanilla fallback).
### Panels (texts)
- [ ] Every row is at most two lines at `line-width` 120: a locked row is `???` + one short line, e.g.
      `Bake 250M to unlock` / `Own 10 Wheat Farm to unlock`; the buildings panel's locked row uses the same style.
- [ ] One-time click upgrades show a short effect: `+20/click` (flat click upgrades), `+1% CPS/click`
      (Handy Fingers), via a new `upgrades.<id>.short` message; the long `effect` stays for the shelf tooltip.
### Belt
- [ ] The belt surface's slats move toward the pedestal (same direction as the cookies), on a fresh setup with
      the default config.
- [ ] Belt cookies lie flat on the belt (face up, rotated 90° about the belt's cross axis), with the bottom
      resting on the rubber; the vanilla fallback lies flat too.
### Ambient sound
- [ ] `factory_ambient`, `house_chimney_crackle` and `belt_loop` loop without audible gaps, doubles or clicks:
      each loop is replayed on its own fixed-period timer that matches the file length at its (fixed) pitch,
      and never restarted twice for one player (re-entering the plot, cinematic end, reload).
### Decoration
- [ ] `/bakery plot setup` also places a decoration set from a new design file `houses/decor.yml` (owned by the
      model-designer): blocks at plot-relative cells around the factory, inside the fence. Backup/undo covers
      them exactly like the house, fence and belt.
- [ ] Decorations never block the path from the gate to the pedestal, the pedestal, the panels, the belt or the
      factory door, and never sit in a hitbox; the parser rejects a decor cell inside those keep-out cells.

## Out of scope
- Animated decorations (display entities, NPCs), new buildings or prices.
- Per-label hover highlighting (a later polish feature could show which line you aim at).

## Assets needed
| Kind | What | Owner |
|---|---|---|
| model / texture / glyph | `cookieclicker:cookie_basket`: 3 tiers x 5 fill steps, spec + heights in `design/basket.md` | model-designer |
| model / texture / glyph | `houses/decor.yml` + generator script + spec `design/decor.md` | model-designer |
| sound | check the three loops are seamless at their fixed pitch; fix files or `sounds.yml` (crackle: fixed pitch) | sound-designer |
| effect | none | — |

## Balance impact
none

## Research needed
none

## Open questions
none

## Contract (fixed names; change only via the orchestrator)
| Kind | ID | Owner | Notes |
|---|---|---|---|
| item model | `cookieclicker:cookie_basket` | model-designer | one item model definition, `select` on `custom_model_data` strings[0] = `<tier>_<fill>`: tiers `wicker`, `bushel`, `golden`, fill `0`..`4` (e.g. `golden_4`); origin = bottom centre at the item display position (transform NONE, scale 1); `design/basket.md` lists each look's top height and width |
| config key | `config.yml: station.basket.*` (tier/fill thresholds, cookie gap, hysteresis) | plugin-coder | |
| house design file | `src/main/resources/houses/decor.yml` | model-designer | format in `design/decor.md`: `blocks:` list of `{at: [x, y, z], block: "<block data>"}` in plot cells from the plot's min corner (same frame as `layout:` in factory.yml), y 0 = first air layer above the ground |
| message key | `upgrades.<id>.short` | plugin-coder | `+<value>/click`, `+<value>% CPS/click` |
| message key | `panel.row.locked`, `panel.clicks.*.locked`, `panel.clicks.need-baked`, `panel.clicks.need-building` | plugin-coder | one line under the title |
| config key | `config.yml: station.panel.*` (ray-check thresholds) | plugin-coder | |
| config key | `config.yml: belt.surface-yaw` / belt cookie rotation | plugin-coder | |
| sound event | `factory_ambient`, `house_chimney_crackle`, `belt_loop` (existing) | sound-designer | lengths in `sounds.yml` comments stay the source of the replay period |

## Tasks
| Agent | Task | Needs (gate) | Branch |
|---|---|---|---|
| model-designer | design `houses/decor.yml` (+ script, `design/decor.md`, review page entry) for the factory plot | contract | `plot-polish/model-designer` |
| sound-designer | audit the three loops (seamless, length vs replay period, fixed pitch), fix files / `sounds.yml` | contract | `plot-polish/sound-designer` |
| plugin-coder (panels) | `view/` package + `messages.yml` + `config.yml` `station.panel`: ray-based row/toggle picking, direct toggle labels, one-line texts, `short` effects | contract | `plot-polish/plugin-coder-panels` |
| plugin-coder (plot) | `belt/`, `house/`, `plot/`, `config.yml` `belt`/`house`: belt direction, flat cookies, loop timers without stutter, decor parser + placement + backup/undo + keep-out | contract | `plot-polish/plugin-coder-plot` |
| plugin-tester | review each code branch, then integration | owner DONE | (reviewed branch) |

Both coder parts touch `config.yml` in different sections only (panels: `station.panel`; plot: `belt`, `house`).

## Status
| Agent | Status | Branch | Verdict | Notes |
|---|---|---|---|---|
| | planned | | | |

## Merge order
1. model-designer, 2. sound-designer, 3. plugin-coder (plot), 4. plugin-coder (panels), 5. model-designer (basket), 6. plugin-coder (basket)

## Needs human review
- [ ] In game: click every row and each toggle label at an angle and from 2–5 blocks; nothing hits a neighbour
- [ ] In game: belt slats and cookies move toward the pedestal, cookies lie flat
- [ ] Listen: stand by the factory for a minute; no stutter in the hum, crackle or belt
- [ ] In game: the basket fills and changes tier, the cookie floats above it and stays clickable, it looks in scale
- [ ] In game: `/bakery plot setup` on a fresh plot; the decoration looks good and `undo` removes it
