---
name: pack-facts-26-3
description: Verified resource pack facts for this project (Paper 26.3 target, pack format 97.1, asset paths, generator/preview tooling)
metadata:
  type: project
---

- Target Paper 26.3 (`26.3.build.205-stable`); resource pack format **97.1** (Minecraft Wiki "Pack format", checked 2026-10-18; 26.2 = 88.0, 26.1 = 84.0). `pack.mcmeta` uses `min_format`/`max_format` 97.
- The big cookie is an upright 3D model shown with `ItemDisplayTransform.NONE` (model `display` block ignored); faces use a derived `cookie_face.png`, sides `cookie_edge.png`. Specs: `design/cookie.md`, `design/building-icons.md`, `design/hud-glyph.md`.
- Tooling: `mise run assets` runs `design/scripts/*.py` alphabetically (so `cookie.py` runs before `cookie_model.py`); `design/scripts/lib/model_render.py` renders JSON models; `mise run pack` zips to `target/cookieclicker-pack.zip`.

**Why:** these took lookups/iterations to establish.
**How to apply:** re-check the pack format if `pom.xml`'s Paper version changes; build on the existing scripts instead of starting over. See [[sandbox-bash-limits]].
