---
name: project-effects-engine
description: Where the Effects engine lives, its stable public API, and non-obvious design decisions (pop preset, caps, pack check)
metadata:
  type: project
---

First built in feature bakery-core (2026-10-18, branch bakery-core/vfx-designer). Plugin-coder (Wesly) wires it in.

- Public API in `vfx/Effects.java` is a contract: `play(id, loc, viewer[, Map placeholders])`, `pop(display, viewer[, presetId])`,
  `load()/reload()/shutdown()`, `sweepLeftovers()`, `setPackCheck(Predicate<Player>)`, `has`, `effectIds`, `entityKey`,
  `activeDisplayEntities`. **Why:** the coder compiles against it and merges after us. **How to apply:** only add
  methods; report any breaking change under CONTRACT CHANGES.
- Pop is configured as a regular preset `pop` with a `scale-pop` layer, because `tools/validate_assets.py` requires
  every top-level key except `limits` in effects.yml to have a `layers` list.
- Display caps count **entities**, keys `max-display-entities-per-player/global` (not "effects"); per-player cap
  evicts the viewer's oldest display effect, global cap drops new ones.
- Item-model particles/crumbs are only used for viewers where the coder's pack check returns true (default false),
  to never show the missing-model texture. In-game verification of ITEM particles with item_model still pending.
- Plain maths in `vfx/shape/`, config parsing in `vfx/config/` (tested offline); preview export
  `EffectsPreviewExport` + `design/vfx-scripts/plot_effects.py`.
- Invariant (cleanup pass 2026-10-18): `Spawner.isOurs` checks the `cookieclicker` scoreboard tag before the
  `cookieclicker:vfx` PDC key (PERF), so every effect entity must keep getting **both**. `play`, `pop` and
  `sweepLeftovers` are main-thread guarded via `onMainThread` (log once, skip); `load`/`shutdown` are not (lifecycle).

- starter-house (2026-10-19, branch starter-house/vfx-designer) added to the contract API: `dropBlock(viewer,
  data, target, onLanded)` + 5-arg overload with `onImpact` (returns boolean; false+callbacks = landed instantly at
  cap/air/not loaded; false+nothing = off-thread/viewer gone), `loop(id, loc, viewer[, onRepeat]) -> EffectHandle`,
  `cancelFor(viewer)`, `activeFallingBlocks/activeLoops`, `BLOCK_DROP = "house_block_drop"`. Drop timings live
  in that preset's `block-drop` layer; preset-level `loop-every`; particle `force`. Drops/loops have their own caps
  (`max-falling-blocks-*`, `max-loops-per-player`), not the display caps. Pure parts: `DropTrack`, `DropJob`
  (fake `Body` in tests), `LoopJob`, `ViewerJobs`. **Why:** coder pass 2 compiles against these. **How to apply:**
  same add-only rule.
- starter-house outline follow-up (2026-10-19, branch starter-house/vfx-designer-outline): presets
  `plot_outline` (#8ec5ff) and `house_outline` (#f5c542), 1 forced dust each, for `/bakery plot show` (played per
  outline point). Pinned in `HouseEffectsConfigTest.outlinePresetsAreOneForcedParticle`. **How to apply:** keep
  per-point tool presets at 1 particle; never reuse player-facing presets for them.
- factory-makeover (2026-10-20): preset `factory_steam` (loop-every 37, 8 particles/cycle, cloud + white_smoke, force);
  `max-loops-per-player` default raised 4 -> 8 in both effects.yml and `Limits.DEFAULTS` (keep them equal). Pinned
  in `HouseEffectsConfigTest.factorySteamIsACheapIrregularLoop`.
- EffectTicker buffers jobs started during its own run (`pending`) so callback-started jobs aren't stepped twice.

Related: [[reference-paper263-vfx-api]]
