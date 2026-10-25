"""Recipe Book icons: the shelf upgrade icon, 7 recipe icons and the locked look (32x32 flat item models).

Outputs under resourcepack/assets/cookieclicker/:
  items/upgrade/recipe_book.json, models/item/upgrade/recipe_book.json, textures/item/upgrade/recipe_book.png
  items/recipe/<id>.json, models/item/recipe/<id>.json, textures/item/recipe/<id>.png
  ids: butter brown_sugar chocolate_chips oatmeal hazelnut macadamia caramel locked
Run with `mise run assets`. Deterministic. Spec: design/recipe-book-gui.md
"""
import json
import sys
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parent))
sys.path.insert(0, str(Path(__file__).resolve().parent / "lib"))
from building_icons import (DARKWOOD, DOUGH, GLASS, GOLD, GREEN, RED, WHITE, WOOD, block, lump,  # noqa: E402
                            outline, thick_line)
from scipy import ndimage  # noqa: E402
from pixel import Canvas, clean_orphans, ellipse, grid, hex_rgba, polygon, rect  # noqa: E402

NS = "cookieclicker"
ASSETS = Path("resourcepack/assets") / NS
S = 32

BUTTER = ["#FFF2A0", "#F8D860", "#E0A838", "#B07A2C"]
BROWN_SUGAR = ["#C89060", "#9A6440", "#744630", "#4E2E26"]
SACK = ["#E6C48C", "#C89A5C", "#946A3E", "#684630"]
CHIP = ["#86523C", "#54301F", "#311A1E"]
OAT = ["#FFF0C0", "#EBD090", "#C8A468", "#946E44"]
BOWL = ["#9CC4E8", "#6E98D0", "#4E70AC", "#34487C"]
HAZEL = ["#D49A60", "#A8683C", "#7C4A2C", "#523224"]
HUSK = ["#C8E08A", "#8CB85A", "#5E9040", "#3C6A30"]
MACA = ["#F8E8C0", "#E4C890", "#C09C64", "#8E6C44"]
CARAMEL = ["#FFD070", "#E89A30", "#BC6A24", "#7C4224"]
LEATHER = ["#B87A50", "#8E5638", "#683C2C", "#43271F"]
PAGE = ["#FFF6D8", "#F2DEA8", "#D6B878", "#A8844C"]
OUTLINE = "#2E1A22"
LOCK = ["#E8E8EC", "#B4B4C0", "#80808E", "#50505E"]


def finish(c, keep=()):
    outline(c)
    return clean_orphans(c.image(), {hex_rgba(k) for k in keep})


def speck(c, pts, col):
    for x, y in pts:
        c.px[y, x] = hex_rgba(col)


# ------------------------------------------------------------------------------ shelf icon
def recipe_book():
    c = Canvas(S)
    block(c, 7, 4, 25, 27, LEATHER)                                    # cover
    c.paint(rect(S, 7, 4, 9, 27), LEATHER[2])                          # spine
    c.paint(rect(S, 7, 4, 7, 27), LEATHER[1])
    c.paint(rect(S, 10, 4, 10, 27), LEATHER[3])
    c.paint(rect(S, 25, 8, 26, 26), PAGE[1])                           # page block on the right
    c.paint(rect(S, 25, 8, 26, 8), PAGE[0])
    c.paint(rect(S, 25, 10, 26, 10), PAGE[2])
    c.paint(rect(S, 25, 13, 26, 13), PAGE[2])
    c.paint(rect(S, 25, 16, 26, 16), PAGE[2])
    c.paint(rect(S, 25, 19, 26, 19), PAGE[2])
    c.paint(rect(S, 25, 22, 26, 22), PAGE[2])
    c.paint(rect(S, 25, 26, 26, 26), PAGE[3])
    for x0, y0, x1, y1 in ((11, 5, 11, 26), (24, 5, 24, 26)):          # gold edging
        pass
    c.paint(rect(S, 12, 7, 23, 7), GOLD[2])                            # gold frame
    c.paint(rect(S, 12, 24, 23, 24), GOLD[2])
    c.paint(rect(S, 12, 7, 12, 24), GOLD[2])
    c.paint(rect(S, 23, 7, 23, 24), GOLD[2])
    c.paint(rect(S, 12, 7, 23, 7), GOLD[1])
    c.paint(rect(S, 12, 7, 12, 24), GOLD[1])
    lump(c, ellipse(S, 17.5, 15.5, 4.8, 4.8), DOUGH, 2.0, (0.75, 0.4, 0.0))      # cookie emblem
    speck(c, ((16, 13), (19, 15), (16, 17), (19, 18)), CHIP[1])
    c.paint(rect(S, 21, 27, 22, 30), RED[1])                           # ribbon bookmark
    c.paint(rect(S, 21, 27, 21, 30), RED[0])
    c.paint(rect(S, 22, 27, 22, 30), RED[2])
    c.px[30, 21] = (0, 0, 0, 0)
    return finish(c, (*GOLD, *RED))


# ------------------------------------------------------------------------------ recipes
def butter():
    c = Canvas(S)
    c.paint(polygon(S, [(5, 14), (11, 8), (28, 8), (22, 14)]), BUTTER[0])          # top
    c.paint(rect(S, 5, 14, 22, 26), BUTTER[1])                                     # front
    c.paint(polygon(S, [(22, 14), (28, 8), (28, 20), (22, 26)]), BUTTER[2])        # side
    c.paint(rect(S, 5, 14, 22, 14), BUTTER[0])
    c.paint(rect(S, 5, 25, 22, 26), BUTTER[2])
    c.paint(rect(S, 5, 14, 5, 26), BUTTER[0])
    c.paint(polygon(S, [(7, 18), (16, 18), (16, 22), (7, 22)]), WHITE[0])           # paper label
    c.paint(rect(S, 7, 22, 16, 22), WHITE[2])
    c.paint(rect(S, 8, 19, 15, 19), BOWL[1])
    c.paint(rect(S, 8, 20, 12, 20), BOWL[1])
    c.paint(thick_line(18, 12, 25, 10, 1.2), BUTTER[2])                             # knife cut
    c.paint(polygon(S, [(14, 9), (20, 5), (26, 5), (20, 9)]), BUTTER[0])            # curl on top
    c.paint(rect(S, 20, 9, 25, 9) & False, BUTTER[2])
    return finish(c, (*BUTTER, *WHITE))


def brown_sugar():
    c = Canvas(S)
    lump(c, polygon(S, [(5, 27), (27, 27), (25, 14), (7, 14)]), SACK, 2.6)          # sack body
    c.paint(rect(S, 5, 27, 27, 27), SACK[3])
    lump(c, ellipse(S, 16, 13.5, 11, 4.2), BROWN_SUGAR, 3.0, (0.8, 0.5, 0.1))        # sugar heap
    rng = np.random.default_rng(7)
    ys, xs = grid(S)
    heap = ((xs - 16) / 11) ** 2 + ((ys - 13.5) / 4.2) ** 2 <= 1
    for _ in range(14):
        x, y = int(rng.integers(7, 25)), int(rng.integers(10, 17))
        if heap[y, x]:
            c.px[y, x] = hex_rgba(BROWN_SUGAR[0])
    c.paint(rect(S, 6, 17, 26, 17), SACK[3])                                         # rolled rim
    c.paint(rect(S, 6, 16, 26, 16), SACK[0])
    c.paint(rect(S, 11, 21, 21, 24), SACK[0])                                        # label
    c.paint(rect(S, 11, 24, 21, 24), SACK[2])
    c.paint(ellipse(S, 16, 22.5, 2.2, 1.4), BROWN_SUGAR[1])
    return finish(c, (*SACK, *BROWN_SUGAR))


def chocolate_chips():
    c = Canvas(S)
    chip = ["#B07A58", "#7C4A32", "#52301F"]
    for cx, cy in ((11, 12), (21, 12), (7, 21), (16, 20), (25, 21)):     # kisses, back row first
        drop = polygon(S, [(cx - 4.5, cy + 4), (cx + 4.5, cy + 4), (cx + 2, cy - 1), (cx + 0.6, cy - 5), (cx - 0.6, cy - 5),
                           (cx - 2, cy - 1)]) | ellipse(S, cx, cy + 2.5, 4.5, 2.2)
        ring = ndimage.binary_dilation(drop, structure=np.ones((3, 3), bool)) & ~drop
        c.paint(ring, OUTLINE)
        lump(c, drop, chip, 2.2, (0.78, 0.5))
        c.paint(rect(S, cx - 2, cy - 1, cx - 2, cy + 1), chip[0])
    return finish(c, chip)


def oatmeal():
    c = Canvas(S)
    lump(c, ellipse(S, 16, 15, 11.5, 4.0), OAT, 2.4, (0.8, 0.5, 0.1))               # oat heap
    for x, y in ((9, 14), (13, 12), (18, 13), (22, 15), (15, 16), (11, 17), (20, 17)):
        c.paint(rect(S, x, y, x + 1, y), OAT[3])
    bowl = polygon(S, [(4, 15), (28, 15), (24, 27), (8, 27)]) | ellipse(S, 16, 27, 8, 1.6)
    lump(c, bowl, BOWL, 3.2, (0.82, 0.55, 0.15))
    c.paint(rect(S, 4, 15, 28, 15), BOWL[0])
    c.paint(rect(S, 8, 21, 24, 21), BOWL[3])
    # spoon
    c.paint(thick_line(23, 12, 29, 4, 1.6), WOOD[1])
    c.paint(ellipse(S, 22, 13, 2.4, 1.8), WOOD[0])
    c.paint(thick_line(10, 6, 10, 12, 1.0), GREEN[2])                                # oat stalk
    for y in (4, 6, 8):
        c.paint(ellipse(S, 11, y, 1.2, 1.4), OAT[1])
        c.paint(ellipse(S, 9, y + 1, 1.2, 1.4), OAT[2])
    return finish(c, (*OAT, *BOWL, *WOOD))


def hazelnut():
    c = Canvas(S)
    for cx, cy, r in ((11, 20, 7.0), (21, 14, 6.5)):
        shell = ellipse(S, cx, cy, r - 0.6, r)
        lump(c, shell, HAZEL, 2.8, (0.8, 0.5, 0.15))
        for k in (-3, 0, 3):                                              # grain streaks
            c.paint(thick_line(cx + k * 0.8, cy - r + 2, cx + k * 0.5, cy + r - 3, 0.8) & shell, HAZEL[2])
        c.paint(ellipse(S, cx, cy + r - 1.2, 3.4, 1.8) & shell, "#E8C898")   # pale base scar
        c.paint(ellipse(S, cx, cy + r - 1.5, 2.0, 1.0) & shell, "#C89868")
        c.paint(polygon(S, [(cx - 1, cy - r + 1), (cx, cy - r - 1.5), (cx + 1, cy - r + 1)]), HAZEL[2])   # tip
        c.paint(rect(S, cx - 3, int(cy - 3), cx - 3, int(cy - 2)) & shell, HAZEL[0])
    return finish(c, (*HAZEL, "#E8C898", "#C89868"))


def macadamia():
    c = Canvas(S)
    a = ellipse(S, 10, 21, 6.4, 6.4)
    lump(c, a, MACA, 2.6, (0.82, 0.55, 0.15))
    c.paint(thick_line(10, 15, 10, 27, 0.8) & a, MACA[3])                           # seam
    b = ellipse(S, 22, 20, 6.4, 6.4)
    lump(c, b, MACA, 2.6, (0.82, 0.55, 0.15))
    c.paint(thick_line(22, 14, 21, 26, 0.8) & b, MACA[3])
    top = ellipse(S, 16, 10, 6.0, 6.0)                                                # cracked, kernel inside
    lump(c, top, MACA, 2.6, (0.82, 0.55, 0.15))
    c.paint(ellipse(S, 16, 10.5, 3.4, 3.8), WHITE[0])
    c.paint(ellipse(S, 16.5, 11.2, 2.2, 2.4), WHITE[1])
    c.paint(polygon(S, [(13, 6), (15, 9), (17, 5), (19, 9), (21, 6), (19, 11), (13, 11)]) & ~ellipse(S, 16, 10.5, 3.4, 3.8),
            MACA[3])
    return finish(c, (*MACA, *WHITE))


def caramel():
    c = Canvas(S)
    jar = polygon(S, [(7, 27), (25, 27), (26, 12), (6, 12)])
    lump(c, jar, GLASS, 3.0, (0.84, 0.6, 0.2))
    fill = polygon(S, [(8, 26), (24, 26), (25, 15), (7, 15)])
    lump(c, fill, CARAMEL, 3.4, (0.8, 0.5, 0.1))
    c.paint(rect(S, 5, 8, 27, 11), WOOD[1])                                          # lid
    c.paint(rect(S, 5, 8, 27, 8), WOOD[0])
    c.paint(rect(S, 5, 11, 27, 11), WOOD[3])
    c.paint(rect(S, 5, 8, 5, 11), WOOD[0])
    c.paint(rect(S, 14, 15, 18, 22), CARAMEL[0])                                     # drip running down the glass
    c.paint(ellipse(S, 16, 22.5, 2.4, 2.4), CARAMEL[0])
    c.paint(rect(S, 9, 17, 9, 24), "#FFFFFF")                                        # glass glint
    c.paint(rect(S, 9, 17, 9, 18), "#FFFFFF")
    c.paint(ellipse(S, 16, 4.5, 2.6, 2.6), CARAMEL[1])                               # a drop on the lid
    c.paint(ellipse(S, 15.5, 4, 1.0, 1.0), CARAMEL[0])
    return finish(c, (*CARAMEL, *GLASS, "#FFFFFF"))


def locked():
    c = Canvas(S)
    block(c, 6, 4, 25, 27, PAGE)                                                     # blank page
    for y in (8, 11, 14):
        c.paint(rect(S, 9, y, 22, y), PAGE[2])
    shackle = ellipse(S, 16, 17, 5.6, 6.4) & ~ellipse(S, 16, 17.5, 3.2, 4.0)
    c.paint(shackle & (grid(S)[0] < 19), LOCK[2])
    c.paint(shackle & (grid(S)[0] < 19) & (grid(S)[1] < 14), LOCK[1])
    block(c, 10, 18, 22, 28, LOCK)
    c.paint(rect(S, 10, 18, 22, 18), LOCK[0])
    c.paint(ellipse(S, 16, 22.5, 1.9, 1.9), LOCK[3])                                 # keyhole
    c.paint(rect(S, 15, 23, 16, 26), LOCK[3])
    c.paint(rect(S, 11, 19, 11, 27), LOCK[0])
    return finish(c, (*LOCK, *PAGE))


RECIPES = {"butter": butter, "brown_sugar": brown_sugar, "chocolate_chips": chocolate_chips, "oatmeal": oatmeal,
           "hazelnut": hazelnut, "macadamia": macadamia, "caramel": caramel, "locked": locked}


def write(group, name, img):
    for sub in ("textures/item", "models/item", "items"):
        (ASSETS / sub / group).mkdir(parents=True, exist_ok=True)
    img.save(ASSETS / f"textures/item/{group}/{name}.png")
    (ASSETS / f"models/item/{group}/{name}.json").write_text(json.dumps(
        {"parent": "minecraft:item/generated", "textures": {"layer0": f"{NS}:item/{group}/{name}"}}, indent=2) + "\n")
    (ASSETS / f"items/{group}/{name}.json").write_text(json.dumps(
        {"model": {"type": "minecraft:model", "model": f"{NS}:item/{group}/{name}"}}, indent=2) + "\n")
    print(f"wrote {group}/{name}")


def main():
    write("upgrade", "recipe_book", recipe_book())
    for name, fn in RECIPES.items():
        write("recipe", name, fn())


if __name__ == "__main__":
    main()
