# Review: bakery-core/model-designer (Pixel Pat), 2026-10-18

Target: Paper 26.3 (pack format 97). Reviewer: Gandalf.

## Checks
| Check | Result |
|---|---|
| ownership-check | OK (7 commits) |
| `mise run assets` reproduces files | yes, `git status` clean afterwards |
| `mise run validate-assets` | 0 problems |
| `mise run pack` | OK, 23 files |
| Contract IDs/paths | all present: items/models for `cookie`, `building/villager_baker`, `building/wheat_farm`; `font/hud.json` (U+E000); `textures/font/hud_cookie.png`; only `cookieclicker` namespace; no panel background |
| cookie.json | 9 cuboids, coords 0.75..15.25 (within -16..32), no rotations, UVs within 0..16, particle texture kept, display transforms present |
| Font JSON | matches docs/research/hud-glyph.md (bitmap, ascent 7, height 8, one char); glyph 16x16 RGBA, opaque bbox 1..15 so margin on all sides |
| Icons | 32x32 RGBA, read visually: baker (hat+face) and sheaf both readable |
| Specs | cookie.md, building-icons.md, hud-glyph.md list IDs and "Status: placeholder"; cookie.md has the originality line |
| pack.mcmeta | description renamed only, formats unchanged |

## Bugs
None blocking.

## Notes (non-blocking)
- cookie.json has all six faces per cuboid, not only N/S as the task summary said; fine (4-unit thickness is visible from the sides).
- wheat_farm.png touches the top and bottom rows (bbox y 0..32); may look cramped in the panel at 0.35 scale. Cosmetic.
- Not verified in game (no client): hud glyph ascent/height and 3D cookie look need a human check.

VERDICT: READY
