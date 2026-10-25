"""Ingredient glyphs for chat/HUD text: flour, milk, sugar, chocolate, eggs (16x16 drawn, rendered at height 8).

Outputs: resourcepack/assets/cookieclicker/textures/font/ingredients.png  (80x16 atlas, 5 cells)
         resourcepack/assets/cookieclicker/font/ingredient.json  (bitmap provider, U+E000..U+E004, ascent 7, height 8)
Run with `mise run assets`. Deterministic. Spec: design/recipe-book-gui.md
"""
import json
import sys
from pathlib import Path

import numpy as np
from PIL import Image
from scipy import ndimage

sys.path.insert(0, str(Path(__file__).resolve().parent / "lib"))
from pixel import Canvas, clean_orphans, ellipse, hex_rgba, polygon, rect, shade  # noqa: E402

PNG = Path("resourcepack/assets/cookieclicker/textures/font/ingredients.png")
FONT = Path("resourcepack/assets/cookieclicker/font/ingredient.json")
S = 16
OUTLINE = "#3A2230"
IDS = ["flour", "milk", "sugar", "chocolate", "eggs"]
CHARS = "".join(chr(0xE000 + i) for i in range(5))

FLOUR = ["#FFFBEF", "#ECE3D2", "#CDBDA8"]
KRAFT = ["#E6C48C", "#C89A5C", "#946A3E"]
MILK = ["#FFFFFF", "#E8F0F8", "#B8C8E0"]
BLUE = ["#8CB4E8", "#5C84C8", "#3E5CA0"]
SUGAR = ["#FFFFFF", "#F0F0F4", "#C8CCE0"]
CHOC = ["#9A6040", "#6E4030", "#4A2A24"]
FOIL = ["#F4CF55", "#D79B2B", "#A96A24"]
EGG = ["#FFF4DC", "#F2D8A8", "#D4A870"]


def outline(c):
    a = c.alpha()
    ring = ndimage.binary_dilation(a, structure=np.ones((3, 3), bool)) & ~a
    c.paint(ring, OUTLINE)


def lump(c, mask, ramp, round_px=2.0, thr=(0.8, 0.45)):
    c.paint_ramp(shade(mask, list(thr), round_px=round_px), ramp)


def flour():
    c = Canvas(S)
    body = polygon(S, [(3.5, 14), (12.5, 14), (13, 7), (11, 5), (5, 5), (3, 7)])
    lump(c, body, FLOUR, 2.4)
    c.paint(rect(S, 5, 2, 10, 4), FLOUR[1])               # gathered neck
    c.paint(rect(S, 5, 2, 5, 4), FLOUR[0])
    c.paint(rect(S, 10, 2, 10, 4), FLOUR[2])
    c.paint(rect(S, 4, 5, 11, 5), KRAFT[1])              # rope tie
    c.paint(rect(S, 4, 5, 11, 5) & (np.indices((S, S))[1] % 2 == 0), KRAFT[2])
    c.paint(ellipse(S, 8, 10.2, 2.1, 2.1), KRAFT[0])      # wheat-ear badge (kraft disc)
    c.paint(ellipse(S, 8, 10.2, 1.1, 1.1), KRAFT[2])
    outline(c)
    return c.image()


def milk():
    c = Canvas(S)
    body = polygon(S, [(4, 14), (12, 14), (12, 7), (10.5, 5), (5.5, 5), (4, 7)])
    lump(c, body, MILK, 2.2, (0.8, 0.5))
    c.paint(rect(S, 6, 2, 9, 5), MILK[1])                  # neck
    c.paint(rect(S, 6, 2, 9, 3), BLUE[1])                  # cap
    c.paint(rect(S, 6, 2, 6, 3), BLUE[0])
    c.paint(rect(S, 9, 2, 9, 3), BLUE[2])
    c.paint(rect(S, 4, 9, 11, 12), BLUE[1])                # label band
    c.paint(rect(S, 4, 9, 11, 9), BLUE[0])
    c.paint(rect(S, 4, 12, 11, 12), BLUE[2])
    c.paint(rect(S, 7, 10, 8, 11), "#FFFFFF")              # drop
    outline(c)
    return c.image()


def sugar():
    c = Canvas(S)

    def cube(x, y, w):
        top = polygon(S, [(x, y + 2), (x + w / 2 + 1, y), (x + w + 2, y + 2), (x + w / 2 + 1, y + 4)])
        left = polygon(S, [(x, y + 2), (x + w / 2 + 1, y + 4), (x + w / 2 + 1, y + 4 + w), (x, y + 2 + w)])
        right = polygon(S, [(x + w + 2, y + 2), (x + w / 2 + 1, y + 4), (x + w / 2 + 1, y + 4 + w), (x + w + 2, y + 2 + w)])
        c.paint(left, SUGAR[1])
        c.paint(right, SUGAR[2])
        c.paint(top, SUGAR[0])
    cube(1.5, 5.5, 4)
    cube(8.5, 5.5, 4)
    cube(5, 2, 4)
    pass
    outline(c)
    return c.image()


def chocolate():
    c = Canvas(S)
    for sx in (3, 8):                                       # 2 x 2 bevelled squares above the foil
        for sy in (3, 7):
            c.paint(rect(S, sx, sy, sx + 4, sy + 3), CHOC[1])
            c.paint(rect(S, sx, sy, sx + 4, sy), CHOC[0])
            c.paint(rect(S, sx, sy, sx, sy + 3), CHOC[0])
            c.paint(rect(S, sx + 4, sy, sx + 4, sy + 3), CHOC[2])
            c.paint(rect(S, sx, sy + 3, sx + 4, sy + 3), CHOC[2])
    c.paint(rect(S, 3, 11, 12, 13), FOIL[1])                # foil wrapper at the bottom
    c.paint(rect(S, 3, 11, 12, 11), FOIL[0])
    c.paint(rect(S, 3, 13, 12, 13), FOIL[2])
    for x, y in ((12, 3), (12, 4), (11, 3)):                # bitten corner
        c.px[y, x] = (0, 0, 0, 0)
    outline(c)
    return c.image()


def eggs():
    c = Canvas(S)
    big = ellipse(S, 8, 8.5, 4.2, 5.6)
    lump(c, big, EGG, 2.6, (0.78, 0.45))
    for x, y in ((6, 9), (9, 6), (10, 11), (7, 12)):        # speckles
        c.px[y, x] = hex_rgba(EGG[2])
    c.paint(rect(S, 6, 4, 6, 4), "#FFFFFF")
    outline(c)
    return c.image()


def main() -> None:
    PNG.parent.mkdir(parents=True, exist_ok=True)
    FONT.parent.mkdir(parents=True, exist_ok=True)
    atlas = Image.new("RGBA", (S * 5, S))
    for i, fn in enumerate((flour, milk, sugar, chocolate, eggs)):
        atlas.paste(clean_orphans(fn(), {hex_rgba(OUTLINE)}), (i * S, 0))
    atlas.save(PNG)
    FONT.write_text(json.dumps({"providers": [{"type": "bitmap", "file": "cookieclicker:font/ingredients.png",
                                                 "ascent": 7, "height": 8, "chars": [CHARS]}]}, indent=2) + "\n")
    print(f"wrote {PNG} and {FONT}")


if __name__ == "__main__":
    main()
