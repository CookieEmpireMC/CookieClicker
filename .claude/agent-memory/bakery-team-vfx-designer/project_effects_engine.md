---
name: project-effects-engine
description: Where the Effects engine lives after the tidy split, its stable public API, and non-obvious design decisions
metadata:
  type: project
---

Built in bakery-core (2026-10-18), extended per feature, restructured in tidy (2026-10-23). Re-check class names with
`ls src/main/java/dev/casperrs/cookieClicker/vfx/` before relying on them.

**Layout (tidy):** `Effects` (lifecycle, play/pop, delegates) -> `PresetLibrary` (reads effects.yml, resolves layer data,
floating text -> `text/MarkerTemplate` for `<amount>`), `BlockDrops` (dropBlock), `Loops` (loop), `Spawner` (wraps public
`PrivateDisplays.spawn(plugin, loc, type, owner, setup)` + `isOurs`), `Transformations` (all `Transformation` building),
`log/WarnOnce` for once-per-reload warnings. Parser: `EffectsConfigParser` (root, limits, presets, dispatch) +
`ParticleLayerParser` + `DisplayLayerParsers`. Plain maths in `vfx/shape/`, tested offline.

**Public API is a contract (add only; report breaks under CONTRACT CHANGES):** `play(id, loc, viewer[, placeholders])`,
`pop(display, viewer[, presetId[, PopMotion]])`, `load/reload/shutdown`, `sweepLeftovers`, `setPackCheck`, `has`,
`effectIds`, `entityKey`, `activeDisplayEntities`, `dropBlock(viewer, data, target[, onImpact], onLanded) -> boolean`,
`loop(id, loc, viewer[, startDelay][, onRepeat]) -> EffectHandle`, `cancelFor`, `activeFallingBlocks/activeLoops`,
`BLOCK_DROP`, `POP`.

Decisions that are not obvious from the code:
- Pop is a regular preset (`pop`) with a `scale-pop` layer: `tools/validate_assets.py` requires a `layers` list for every
  top-level key except `limits`.
- Display caps count entities (`max-display-entities-per-player/global`); per-player evicts the viewer's oldest, global
  drops new ones. Drops/loops have their own caps (`max-falling-blocks-*`, `max-loops-per-player`; default 8 in both
  effects.yml and `Limits.DEFAULTS`, keep equal).
- Item-model particles/crumbs only for viewers where the pack check is true (default false). In-game check of ITEM
  particles with item_model is still pending.
- Every effect entity must carry both the `cookieclicker` scoreboard tag and the `cookieclicker:vfx` PDC key
  (`isOurs` checks the tag first, a cheap set lookup).
- `min-interval-ticks` (0..1200, `Throttle`, per viewer per preset) only affects `play()`; use it for any per-event
  effect whose rate scales with progress (`belt_arrive`).
- `EffectTicker` buffers jobs started during its own run (`pending`) so callback-started jobs aren't stepped twice.
- `LoopJob` checks viewer validity only just before a play (quit/world change are also handled by `cancelFor`).
- Floating text: only `{amount}` alone takes the precompiled `MarkerTemplate` path; any other placeholder map falls
  back to a real parse.
- Per-point tool presets (`plot_outline`, `house_outline`) stay 1 forced particle; never reuse player presets for them.
- `PopJob.setScale` re-sends the current target rotation with a 2-3 tick duration (cause of the old cookie-spin bug;
  fixed by passing a `PopMotion`).

Related: [[reference-paper263-vfx-api]]
