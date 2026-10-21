# VFX style guide

House rules for Cookie Empire effects. Rules here came from `design/vfx-lessons.md` or are deliberate defaults.
The effect catalogue and config format are in `design/effects.md`.

## Palette

| Name | Hex | Use |
|---|---|---|
| Cookie gold | `#f5c542` | rewards, purchases, rings, "+N" text |
| Crit light gold | `#fff2a8` | crit highlights, gradient start |
| Crit deep gold | `#f5a623` | crit gradient end, dust transitions |
| Dough | `#e8c49a` | soft accents, crumbs |
| Choc chip | `#5a3825` | contrast flecks (sparingly; reads as dirt in the dark) |
| Spark white | (firework / end_rod default) | sparkles, highlights |
| Mortar dust | `#c9b79c` | construction dust at ground level (house foundation) |
| Confetti pink / mint / sky | `#ff8fc8`, `#7fe3c4`, `#8ec5ff` | celebration finales only, always next to cookie gold |

Keep one dominant colour per effect plus at most one accent. Crumbs come from the cookie itself (`item` particle
with the cookie item), so they always match the cookie's texture.

## Timing

- 20 ticks = 1 s. Click feedback: ≤ 10 ticks of particles, floating text ~1 s. Purchases / upgrades: 10–20 ticks.
  Crits: up to 30 ticks (the big number). Milestones: up to 40.
- Motion eases **out** (fast start, settle at the end): `1 - (1 - p)^2`. Things that rise should hang at the top.
  Display entities only interpolate linearly, so build the ease-out from 2–3 keyframes (fast first segment, slow
  long second one, short exit).
- Pops are fast: squash 2 ticks, stretch 2, settle 3 (the contract's 2+2+3). Floating text pops in over 3–4 ticks
  with a 1.15–1.3 overshoot.
- Stagger groups (start times spread over 3–5 ticks, or a 1–2 tick `delay-ticks` on secondary layers) so nothing
  moves in lockstep.
- Use deterministic patterns (golden angle, even spacing) instead of randomness so previews match the game.

## Density

| Effect size | Particles total | Display entities | Notes |
|---|---|---|---|
| Click (spammable) | ≤ 15 | 1 (the number) | personal only |
| Crit (≈5 % of clicks) | ≤ 130 | ≤ 4 | personal only |
| Purchase / upgrade | 40–120 | 0–1 | personal by default |
| Milestone / global | ≤ 200 (hard cap `max-particles-per-effect`) | ≤ 6 | |
| Per-block (played hundreds of times in a row) | ≤ 10 (impact), ≤ 30 (all layers of one block) | the drop itself | sum over blocks per tick matters more than one block |
| Per-event repeat (fires several times a second, e.g. belt arrivals) | ≤ 20 per play, ≤ 100 per second per viewer via `min-interval-ticks` | 0 | a soft "plip": small radius (≤ 0.25), light colours, no sparks; cap the rate in the preset, never rely on the caller's pacing |
| Ambient loop | ≤ 5 per play (≤ 10 per cycle for multi-puff cycles ≥ 1.5 s), ≤ 10 per second | 0 | long-lived particles (campfire smoke) instead of many short ones; irregular rhythm = prime `loop-every` + delayed layers at uneven gaps |

- Rings: about 24–30 points per block of radius. Fewer looks dotted, more turns into a solid line and costs more.
- Growing rings/spheres redraw every tick (cost × ticks): keep them to 3 ticks.
- Moving sparks are re-emitted every tick along their path; dust lingers 8+ ticks on the client, so the result
  reads as a short trail. `emit-every: 2` halves the cost but leaves visible steps at the start of an ease-out.

## Display entities

- Every effect entity: spawned with a consumer (non-persistent, hidden by default, tagged), shown to the viewer
  only, removed by the engine. Never per-tick teleports: one keyframe = one interpolated `setTransformation`.
- Floating text: transparent background, shadow on, billboard `center`, full-bright, view-range 0.5. Rapid repeats
  fan out on a small golden-angle disc (`spread`) so numbers never stack into one blob.
- Text opacity isn't interpolated: shrink the scale (interpolated) at the end and step opacity down (≥ 27).
- Pops change the scale only, relative to the rest scale captured at the first pop of a run.

- Falling blocks: `BlockDisplay` at the block corner, 3 segments (fall, bounce + squash, settle), squash centred on
  x/z with the bottom face anchored, removed in the same tick the real block is resent. Fixed brightness, never the
  light sampled inside a (fake-hollow) building.

## Placement

- Per-block effects take the block's **bottom centre** `(x + 0.5, y, z + 0.5)`. Anything that should be seen must
  start **outside the block volume**: rings at radius ≥ 0.72 (the corner diagonal is 0.71), sparks from the top face
  (y ≥ 1.0). Particles inside a solid block are invisible.
- Finales over a structure: a dome (`burst`, `direction: none`, `up-bias` ~0.6) centred about 1 block above the
  highest point, radius up to half the structure's width.

## Juicy but readable

- The player must still see the cookie: keep effects outside a ~0.5 block radius of the clicked point,
  or short (≤ 10 ticks) when on top of it.
- The number is the reward: it pops in, rises, hangs, and is never hidden behind particles (sparkles frame it).
- Personal feedback goes to the viewer only (`play(id, loc, player)`); pass `null` only for shared moments.
- A crit must look clearly bigger than a click at a glance: ×1.8 text, gold gradient, a second motion (sparks
  outward + sparkles upward) and longer life. A purchase is celebratory but compact and faces the buyer.
- Read every preview PNG for symmetry, radius, density and timing before handing off.

## Game-feel notes (technique, in my own words)

- **Anticipation, action, follow-through.** A click reads best as squash (anticipation, 2 ticks), release with a
  slight overshoot (action), then a settle (follow-through). The overshoot sells the "springiness"; skip it and the
  cookie feels rubbery-dead.
- **Layering beats count.** Two or three layers with different speeds (fast sparks outward, slow sparkles upward,
  a lingering shell) look richer than one layer with three times the particles, and cost less.
- **Silhouette first.** Each effect should have one readable shape at a glance (burst, ring, column). Extra layers
  decorate the silhouette; they don't compete with it.
- **Contrast in time.** Fast start, slow end. The eye catches the fast part and reads the slow part; constant-speed
  motion looks mechanical.
- **Reward scaling.** Bigger rewards get more of everything at once (size, duration, layers, colour richness), not
  just more particles, so players learn the hierarchy without reading numbers.
- **Feedback must survive spam.** The most frequent effect is the cheapest and is capped; the newest feedback wins
  over older feedback (evict oldest, never block the newest).
