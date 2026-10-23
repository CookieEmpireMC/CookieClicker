---
name: pack-facts-26-3
description: Verified resource pack facts (Paper 26.3, pack format 97.1, asset tooling, ItemDisplay NONE placement rule)
metadata:
  type: project
---

- Target Paper 26.3 (`26.3.build.205-stable`); pack format **97.1** (Minecraft Wiki "Pack format", checked 2026-10-18; 26.2 = 88.0, 26.1 = 84.0). `pack.mcmeta` uses `min_format`/`max_format` 97. Re-check if `pom.xml`'s Paper version changes.
- The big cookie is an upright 3D model shown with `ItemDisplayTransform.NONE` (model `display` block ignored). Specs: `design/cookie.md`, `design/building-icons.md`, `design/hud-glyph.md`.
- ItemDisplay NONE: model y 8 = display position (centre 8,8,8 drawn at the entity), so models placed at a floor point need lowest element y == 8 (conveyor_belt does; basket fixed 2026-10-22). Whether NONE turns the model 180 degrees about Y is NOT verified in game.
- Tooling: `mise run assets` runs `design/scripts/*.py` alphabetically (`cookie.py` before `cookie_model.py`); a fresh worktree first needs `mise run assets-deps` (creates .venv); `lib/model_render.py` renders JSON models (frame 0 of `.png.mcmeta` strips); `mise run pack` zips to `target/cookieclicker-pack.zip`.
- upgrade icons `cookieclicker:upgrade/<id>`: `design/scripts/upgrade_icons.py`, spec `design/upgrade-icons.md`; `layout.clicks` = `[16,0,6]` (CLICKS in `house_factory.py`, mirror of shop).
- Generators are deterministic (2026-10-23, `building_world.py` run twice: identical sha1). The portal_bakery rewrite on 2026-10-23 was a stale committed output, not randomness. See [[sandbox-bash-limits]].
