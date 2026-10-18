# HUD cookie glyph (action bar)

**Status: placeholder.** Generated pixel art; a human may repaint it (keep 16×16 and the 1 px top/bottom margin).

**Originality:** original work by Pixel Pat, drawn from scratch by `design/scripts/hud_glyph.py` in the big cookie's palette. Not copied from any other game, pack or plugin.

## Purpose
A tiny cookie icon in front of the cookie count in the action bar HUD (`docs/research/hud-glyph.md`).

## Pipeline
Plain resource pack, namespace `cookieclicker`, Paper 26.3. Bitmap font provider.

| What | Path |
|---|---|
| Font | `resourcepack/assets/cookieclicker/font/hud.json` → font id **`cookieclicker:hud`** |
| Glyph image | `resourcepack/assets/cookieclicker/textures/font/hud_cookie.png` (provider `file`: `cookieclicker:font/hud_cookie.png`) |
| Generator | `design/scripts/hud_glyph.py` (writes both files; `mise run assets`) |

## IDs for the code
| Font | Char | Escape | `ascent` | `height` | Meaning |
|---|---|---|---|---|---|
| `cookieclicker:hud` | U+E000 (Private Use Area) | `` | 7 | 8 | cookie icon |

MiniMessage (in `messages.yml`, YAML double quotes): `"<white><font:cookieclicker:hud></font> <gold><cookies></gold> ..."`. Wrap the glyph in `<white>` so the PNG keeps its colours (glyph colours are multiplied by the text colour), and close `</font>` before the text. Players without the pack get `hud.no-pack` (see `docs/minimessage.md` and the research note).

U+E000 is the only character in this font so far; new HUD icons get U+E001 and up in the same font.

## Dimensions
- **16×16** RGBA, transparent background. Cookie disc 14 px across: rows 1–14 and columns 1–14, so there is a 1 px transparent margin on every side (the right margin column is trimmed automatically: glyph width is measured to the right-most opaque column).
- Rendered at `height` 8 (a clean 2× downscale): the icon is as tall as a capital letter plus descender space, sitting on the baseline with `ascent` 7, like vanilla text.

## Palette (big cookie palette, 9 colours)
Dough `#F7DC9A` `#EBBE6C` `#D69A4C` `#B8743A`, outline `#8E5032` (lit side) / `#5C2F2A` (shadow side), chocolate `#86523C` `#54301F` `#311A1E`. Five 2×2 chocolate chunks with a lit top-left and a dark bottom-right pixel.

## Placeholder status and human follow-up
- Check in game: icon height roughly equal to the text, baseline aligned. If it floats or sinks, change `ascent` by 1 at a time in `hud_glyph.py` (and this spec) and re-run `mise run assets`.
