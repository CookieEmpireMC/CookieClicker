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

Related: [[reference-paper263-vfx-api]]
