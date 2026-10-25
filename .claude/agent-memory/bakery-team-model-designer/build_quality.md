---
name: build-quality
description: Block-structure pipeline (textured renderer from the client jar, kit library, lint, scenes), where things live, contract details and pitfalls (2026-10-24)
metadata:
  type: project
---

- Textured previews (2026-10-24, stable): `design/scripts/lib/block_models.py` reads blockstates/models/textures from the 26.3 client jar in place (`$BAKERY_CLIENT_JAR`, default `~/bakery-review/chunky/home/resources/minecraft.jar`; *.jar is git-ignored; no cache). 26.x model JSON may give textures as `{"sprite": ...}`. `lib/tex_render.py` ray-casts quads (persp/ortho, translucent pass, tints for plains). `lib/design_views.py`, `lib/plot_views.py` write the PNG sets. Renders in ~1 s; matches Chunky almost 1:1.
- Kit library: `lib/blocks.py` (builders, `resolve()` = connections + stair shapes like the game, `to_palette`), `lib/build.py` (`Build`, `Ramp` with `assign` quotas, `Wall`, `window`, `door`, `gable_roof`, `stack`), `lib/scenes.py`, `lib/build_lint.py` (rules in `design/build-lint.md`), `lib/house_lib.py` (validate, YAML writer with `ground-depth`). Generators: `house_factory.py` (+ `factory_layout.py`), `house_default.py`, `decor_style.py` (class `Decor` enforces keep-outs), `fence_style.py` (`ring()` mirrors placement).
- Contract (brief, coder merged e85ac44): `ground-depth` N: first N layers are design y -N..-1, points/signs/origin use design y. decor `at` y -1/-2 allowed (not in house box/belt cells); y >= 0 keeps old keep-outs. fence `post-spacing`/`topper-spacing` (topper gaps counted in posts), not combinable with `-every`. Leaves forced persistent.
- Ownership: `design/refs/` is NOT writable by model-designer (glob `design/*.md` only) -> sheets are `design/refs-<house>.md`.
- `mise run review-render <design> --at 400 -60 0` works from the own worktree (~65 s for 3 shots, server stays up); for designs without `layout:` its camera sits inside the building.
- Pitfalls: lint flood fill must not pass through fences/slabs/stairs; ramps need exact quotas for tiny stacks; `mise run assets` regenerates `portal_bakery` model/texture differently than committed (pre-existing, revert before commit); the `HouseDesignParserTest` default test pins the old 9x7x7/170 blocks.
