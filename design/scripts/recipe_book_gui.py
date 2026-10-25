"""Recipe Book chest GUI background (4 rows, 176x186) and the font `cookieclicker:gui`.

Outputs: resourcepack/assets/cookieclicker/textures/font/gui/recipe_book.png
         resourcepack/assets/cookieclicker/font/gui.json
Layout is the vanilla generic_54 grid for 4 rows (docs/research/textured-gui.md): slot frames at
(7+18c, 17+18r), player inventory frames from y 103, hotbar frames at y 161, "Inventory" label at y 92.
Run with `mise run assets`. Deterministic. Spec: design/recipe-book-gui.md
"""
import json
import random
from pathlib import Path

from PIL import Image

PNG = Path("resourcepack/assets/cookieclicker/textures/font/gui/recipe_book.png")
FONT = Path("resourcepack/assets/cookieclicker/font/gui.json")
W, H = 176, 186
ROWS = 4

# parchment ramp (light -> dark, hue shifts towards red/brown), leather cover, vanilla greys
PARCH = ["#F8EDC8", "#EFDDAB", "#E2C98E", "#CDAE74", "#B38F5C"]
LEATHER = ["#9A6240", "#7C4B32", "#5E3A2A", "#43271F"]
GOLD = ["#FFE48A", "#E8B544", "#B8812C"]
RIBBON = ["#E0675A", "#BC3F3F", "#8A2A32"]
GREY = ["#FFFFFF", "#C6C6C6", "#8B8B8B", "#373737", "#555555"]
DOUGH = ["#F7DC9A", "#EBBE6C", "#D69A4C", "#B8743A", "#8E5032"]
CHIP = "#54301F"

# codepoints (font cookieclicker:gui); mirrored in design/recipe-book-gui.md
SPACES = {  # char -> advance
    "": -1, "": -2, "": -4, "": -8, "": -16, "": -32,
    "": -64, "": -128, "": 1, "": 2, "": 4, "": 8,
    "": 16, "": 32, "": 64, "": 128,
    "": -169,   # glyph advance (176 + 1) minus the title x (8): back to the vanilla title position
    "": -177,   # back to the container's left edge
}
BG_CHAR = ""


def rgb(h):
    return tuple(int(h[i:i + 2], 16) for i in (1, 3, 5)) + (255,)


def build() -> Image.Image:
    rng = random.Random(2026)
    img = Image.new("RGBA", (W, H))
    px = img.load()

    def fill(x0, y0, x1, y1, c):
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1):
                px[x, y] = rgb(c)

    # ---- lower, vanilla-looking part: grey panel
    fill(0, 0, W - 1, H - 1, GREY[1])
    fill(0, 102, W - 1, 102, GREY[1])
    # vanilla-style outer bevel on the lower part: white left, dark right/bottom
    fill(0, 102, 0, H - 2, GREY[0])
    fill(W - 1, 102, W - 1, H - 1, GREY[3])
    fill(1, H - 1, W - 1, H - 1, GREY[3])
    fill(W - 2, 103, W - 2, H - 2, GREY[2])
    fill(1, H - 2, W - 2, H - 2, GREY[2])
    px[0, H - 1] = rgb(GREY[1])
    px[W - 1, 102] = rgb(GREY[1])

    # ---- the book: leather cover y 0..101, parchment inside
    fill(0, 0, W - 1, 101, LEATHER[1])
    fill(0, 0, W - 1, 0, LEATHER[0])               # lit top edge
    fill(0, 0, 0, 101, LEATHER[0])                 # lit left edge
    fill(W - 1, 1, W - 1, 101, LEATHER[3])
    fill(1, 101, W - 1, 101, LEATHER[3])
    fill(1, 1, W - 2, 1, LEATHER[1])
    # gold line round the pages
    fill(3, 3, W - 4, 3, GOLD[1])
    fill(3, 98, W - 4, 98, GOLD[2])
    fill(3, 3, 3, 98, GOLD[1])
    fill(W - 4, 3, W - 4, 98, GOLD[2])
    # leather corners get a dark notch so the cover reads as rounded
    for x, y in ((1, 1), (W - 2, 1)):
        px[x, y] = rgb(LEATHER[2])
    # pages: x 4..171, y 4..97; clustered paper tone from smoothed noise
    nz = [[rng.random() for _ in range(W)] for _ in range(H)]
    for y in range(4, 98):
        for x in range(4, W - 4):
            v = sum(nz[(y + dy) % H][(x + dx) % W] for dx in (-1, 0, 1) for dy in (-1, 0)) / 6
            base = PARCH[1] if v < 0.58 else PARCH[0]
            if v < 0.32:
                base = PARCH[2]
            px[x, y] = rgb(base)
    # page edges (stacked sheets) on the outer sides, and the spine shadow in the middle
    for i, c in enumerate((PARCH[3], PARCH[0], PARCH[3])):
        fill(4 + i, 4, 4 + i, 97, c)
        fill(W - 5 - i, 4, W - 5 - i, 97, c)
    # spine: columns 84..91 darkening to the centre
    for x, c in zip(range(84, 92), (PARCH[1], PARCH[2], PARCH[2], PARCH[3], PARCH[3], PARCH[2], PARCH[2], PARCH[1])):
        for y in range(4, 98):
            px[x, y] = rgb(c)
    fill(87, 4, 88, 97, PARCH[4])
    # title strip: baseline rule under the title text
    fill(8, 14, 78, 14, PARCH[3])
    # bookmark ribbon hanging into the title strip (right page)
    fill(138, 4, 142, 15, RIBBON[1])
    fill(138, 4, 138, 15, RIBBON[0])
    fill(142, 4, 142, 15, RIBBON[2])
    px[138, 15] = rgb(PARCH[1]); px[142, 15] = rgb(PARCH[1]); px[140, 15] = rgb(PARCH[1])  # swallow-tail notch
    px[139, 14] = rgb(RIBBON[1]); px[141, 14] = rgb(RIBBON[1])
    # small cookie stamp (11x11) top right of the title strip
    cx, cy, r = 160, 9, 5.2
    for y in range(3, 16):
        for x in range(154, 167):
            d = ((x + .5 - cx) ** 2 + (y + .5 - cy) ** 2) ** .5
            if d <= r:
                lit = (x + .5 - cx) + (y + .5 - cy)
                px[x, y] = rgb(DOUGH[0] if lit < -3.2 else DOUGH[1] if lit < 1.5 else DOUGH[2])
            elif d <= r + 1.0:
                lit = (x + .5 - cx) + (y + .5 - cy)
                px[x, y] = rgb(DOUGH[4] if lit > 0 else DOUGH[3])
    for x, y in ((158, 7), (162, 8), (160, 11), (157, 11), (163, 12)):
        px[x, y] = rgb(CHIP)
    # light strip behind the "Inventory" label (y 92..99): clean paper, no noise
    fill(8, 90, W - 9, 97, PARCH[0])
    fill(8, 90, W - 9, 90, PARCH[1])

    # ---- slot frames: parchment style for the 4 x 9 recipe slots (recessed)
    def slot_parch(x0, y0):
        fill(x0, y0, x0 + 17, y0 + 17, PARCH[3])
        fill(x0 + 1, y0 + 1, x0 + 16, y0 + 16, PARCH[2])
        fill(x0, y0, x0 + 16, y0, LEATHER[1])           # dark top + left
        fill(x0, y0, x0, y0 + 16, LEATHER[1])
        fill(x0 + 1, y0 + 17, x0 + 17, y0 + 17, PARCH[0])   # light bottom + right
        fill(x0 + 17, y0 + 1, x0 + 17, y0 + 17, PARCH[0])

    def slot_vanilla(x0, y0):
        fill(x0, y0, x0 + 17, y0 + 17, GREY[2])
        fill(x0, y0, x0 + 16, y0, GREY[3])
        fill(x0, y0, x0, y0 + 16, GREY[3])
        fill(x0 + 1, y0 + 17, x0 + 17, y0 + 17, GREY[0])
        fill(x0 + 17, y0 + 1, x0 + 17, y0 + 17, GREY[0])
        px[x0 + 17, y0] = rgb(GREY[1])
        px[x0, y0 + 17] = rgb(GREY[1])

    for r in range(ROWS):
        for c in range(9):
            slot_parch(7 + 18 * c, 17 + 18 * r)
    for r in range(3):
        for c in range(9):
            slot_vanilla(7 + 18 * c, H - 83 + 18 * r)
    for c in range(9):
        slot_vanilla(7 + 18 * c, H - 25)
    return img


def main() -> None:
    PNG.parent.mkdir(parents=True, exist_ok=True)
    FONT.parent.mkdir(parents=True, exist_ok=True)
    img = build()
    assert img.size == (W, H) and img.getchannel("A").getextrema()[0] == 255
    img.save(PNG)
    providers = [{"type": "space", "advances": SPACES},
                 {"type": "bitmap", "file": "cookieclicker:font/gui/recipe_book.png", "ascent": 13, "height": H,
                  "chars": [BG_CHAR]}]
    FONT.write_text(json.dumps({"providers": providers}, indent=2) + "\n")
    print(f"wrote {PNG} ({W}x{H}) and {FONT}")


if __name__ == "__main__":
    main()
