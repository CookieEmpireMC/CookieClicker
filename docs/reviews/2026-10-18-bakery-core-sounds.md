# Review: bakery-core/sound-designer (Bassboost Bruno)

Target: Paper 26.x (sound assets only, no Java touched).

## Checks
| Check | Result |
|---|---|
| `mise run sounds` then `git status` | clean, byte-identical regeneration |
| `mise run sound-check` | 7 checked, 0 failed; all mono 44.1 kHz, peaks -2.3 to -7.7 dBFS, RMS -17.6 to -19.1 |
| `mise run validate-assets` | 0 problems |
| `mise run sound-previews` | OK; overview shows no leading silence, clean decays |
| `mise run tools-test` | 113 tests OK |
| `ownership-check bakery-core/sound-designer` | OK, 4 commits |

## Contract
- Events `cookie_click`, `cookie_crit`, `purchase`, `cant_afford` present in `sounds.yml` with keys `cookieclicker:ui.<event>`; 4 click variants (>= 3).
- Every `sounds.json` entry resolves to an .ogg; subtitles exist in `lang/en_us.json`.
- `sounds.yml`: vanilla fallbacks, volume <= 1, valid pitch ranges, source `ui`, documentation comment block.
- Synthesis only, scripts in `design/sound-scripts/`, previews git-ignored, no pack.mcmeta (model-designer's).

## Bugs
None.

## Notes (non-blocking)
- `source: ui` only exists on Paper 26.3; the coder's sounds.yml parser should fall back to a default category if the enum value is missing.
- Click volume 0.6 with fallback `block.wood.hit`: fine.

VERDICT: READY
