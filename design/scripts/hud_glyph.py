"""HUD cookie glyph for the action bar: a tiny 16x16 cookie in the big cookie's palette.

Output: resourcepack/assets/cookieclicker/textures/font/hud_cookie.png (16x16 RGBA)
Font:   resourcepack/assets/cookieclicker/font/hud.json (bitmap provider, U+E000, ascent 7, height 8)
Run with `mise run assets`. Deterministic. Spec: design/hud-glyph.md
"""
import json
import sys
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parent / "lib"))
from pixel import Canvas, edge_of, ellipse, grid, lit_side, shade  # noqa: E402

PNG = Path("resourcepack/assets/cookieclicker/textures/font/hud_cookie.png")
FONT = Path("resourcepack/assets/cookieclicker/font/hud.json")
S = 16

# Same ramps as the big cookie (design/cookie.md)
DOUGH = ["#F7DC9A", "#EBBE6C", "#D69A4C", "#B8743A", "#8E5032", "#5C2F2A"]
CHOC = ["#86523C", "#54301F", "#311A1E"]
# chocolate chunks: top-left pixel of each 2x2 chunk
CHUNKS = [(4, 4), (9, 3), (10, 8), (5, 9), (9, 11)]

FONT_JSON = {
    "providers": [
        {
            "type": "bitmap",
            "file": "cookieclicker:font/hud_cookie.png",
            "ascent": 7,
            "height": 8,
            "chars": [""],
        }
    ]
}


def build():
    c = Canvas(S)
    ys, xs = grid(S)
    fy, fx = np.floor(ys), np.floor(xs)
    body = ellipse(S, 8, 8, 7.0, 7.0) & (fy >= 1) & (fy <= 14)   # 1 px margin top and bottom
    c.paint_ramp(shade(body, [0.86, 0.70, 0.52], round_px=2.5), DOUGH[:4])
    for x, y in CHUNKS:
        chunk = (fx >= x) & (fx <= x + 1) & (fy >= y) & (fy <= y + 1) & body
        c.paint(chunk, CHOC[1])
        c.paint(chunk & (fx == x) & (fy == y), CHOC[0])              # lit corner
        c.paint(chunk & (fx == x + 1) & (fy == y + 1), CHOC[2])      # shadow corner
    edge = edge_of(body)
    lit = lit_side(S, 8, 8)
    c.paint(edge & lit, DOUGH[4])
    c.paint(edge & ~lit, DOUGH[5])
    return c.image()


def main() -> None:
    PNG.parent.mkdir(parents=True, exist_ok=True)
    FONT.parent.mkdir(parents=True, exist_ok=True)
    img = build()
    img.save(PNG)
    # ensure_ascii keeps the private-use char visible in the file as 
    FONT.write_text(json.dumps(FONT_JSON, indent=2) + "\n")
    print(f"wrote {PNG} ({img.width}x{img.height}) and {FONT}")


if __name__ == "__main__":
    main()
