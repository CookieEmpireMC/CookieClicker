"""Panel icons for the 9 buildings added by building-roster (32x32, flat item models).

Outputs per id under resourcepack/assets/cookieclicker/:
  items/building/<id>.json, models/item/building/<id>.json, textures/item/building/<id>.png
ids: cow_barn sugar_cane_field industrial_oven extra_belt chicken_farm cocoa_plantation shipping_centre alchemy_lab portal_bakery
(wheat_farm and villager_baker keep their own scripts: building_wheat_farm.py / building_villager_baker.py.)
Deterministic. Spec: design/building-icons.md
"""
import json
import sys
from pathlib import Path

import numpy as np
from scipy import ndimage

sys.path.insert(0, str(Path(__file__).resolve().parent / "lib"))
from pixel import Canvas, clean_orphans, ellipse, grid, hex_rgba, polygon, rect, shade  # noqa: E402

NS = "cookieclicker"
ASSETS = Path("resourcepack/assets") / NS
S = 32

BRICK = ["#E0987A", "#C0694F", "#9C4A3C", "#743434"]
RED = ["#EE8A68", "#CE5A44", "#A43E34", "#7C2C2E"]
WHITE = ["#FFFBEF", "#ECE3D2", "#CDBDA8", "#A8948A"]
WOOD = ["#E8B77A", "#C48A52", "#9A5E3C", "#6E3F2E"]
DARKWOOD = ["#8E6248", "#6E4638", "#54332E", "#3F2626"]
GREEN = ["#B8E070", "#84BC48", "#5A9438", "#3C6A30"]
CANE = ["#C8EE8C", "#98CC5C", "#6CA844", "#487A38"]
SOIL = ["#8C6040", "#6A452E", "#4A2E22", "#2F1C17"]
GOLD = ["#FFF2A8", "#F4CF55", "#D79B2B", "#A96A24"]
HONEY = ["#FFE28A", "#F6BC3E", "#D28A2A", "#9A5A26"]
IRON = ["#B2B2BC", "#8A8A98", "#646476", "#464658"]
FIRE = ["#FFF2A8", "#FFD060", "#F4A038", "#D8662C"]
OBS = ["#62508A", "#443466", "#2E2248", "#1E1432"]
PURPLE = ["#D4AAF4", "#A878DC", "#8050BC", "#5C3894"]
POTION = ["#EAFFA0", "#B8EC62", "#82CC3E", "#52A430"]
GLASS = ["#E8FAF6", "#BCE6E2", "#8CC8C8", "#5C9CAA"]
COCOA = ["#C28458", "#9A5E3C", "#744230", "#4E2C28"]
DOUGH = ["#F7DC9A", "#EBBE6C", "#D69A4C", "#B8743A"]
OUTLINE = "#2E1A22"


def block(c, x0, y0, x1, y1, ramp, hl=True):
    """Flat part: body, lit top row + left column, shadow bottom row + right column."""
    c.paint(rect(S, x0, y0, x1, y1), ramp[1])
    if hl:
        c.paint(rect(S, x0, y0, x1, y0), ramp[0])
        c.paint(rect(S, x0, y0, x0, y1), ramp[0])
        c.paint(rect(S, x0, y1, x1, y1), ramp[2])
        c.paint(rect(S, x1, y0, x1, y1), ramp[2])


def lump(c, mask, ramp, round_px=2.5, thr=(0.8, 0.5, 0.1)):
    c.paint_ramp(shade(mask, list(thr), round_px=round_px), ramp)


def outline(c):
    a = c.alpha()
    ring = ndimage.binary_dilation(a, structure=np.ones((3, 3), bool)) & ~a
    ring &= ~np.zeros_like(a)
    c.paint(ring, OUTLINE)


def finish(c):
    outline(c)
    return clean_orphans(c.image(), {hex_rgba(x) for x in (*FIRE, *GOLD, OUTLINE, *WHITE)})


def thick_line(x0, y0, x1, y1, w=1.0):
    ys, xs = grid(S)
    dx, dy = x1 - x0, y1 - y0
    L2 = dx * dx + dy * dy + 1e-9
    t = np.clip(((xs - x0) * dx + (ys - y0) * dy) / L2, 0, 1)
    return np.hypot(xs - (x0 + t * dx), ys - (y0 + t * dy)) <= w / 2 + 0.05


def px(c, pts, col):
    for x, y in pts:
        c.px[y, x] = hex_rgba(col)


# ----------------------------------------------------------------------------------- icons
def cow_barn():
    c = Canvas(S)
    block(c, 4, 15, 27, 28, RED)                                   # walls
    for x in range(8, 27, 4):                                       # board lines
        c.paint(rect(S, x, 16, x, 27), RED[2])
    c.paint(polygon(S, [(2, 15.5), (16, 3), (30, 15.5)]), DARKWOOD[1])      # roof
    lump(c, polygon(S, [(3, 15), (16, 4.5), (29, 15)]), DARKWOOD, 1.5, (0.9, 0.55, 0.1))
    c.paint(rect(S, 2, 15, 29, 15), DARKWOOD[3])
    block(c, 11, 18, 20, 28, WHITE)                                 # wide door with X braces
    c.paint(thick_line(11.5, 18.5, 19.5, 27.5, 1.0), RED[2])
    c.paint(thick_line(19.5, 18.5, 11.5, 27.5, 1.0), RED[2])
    block(c, 14, 8, 17, 11, WHITE)                                  # hayloft window
    c.paint(rect(S, 15, 8, 15, 11), DARKWOOD[2])
    c.paint(rect(S, 14, 9, 17, 9), DARKWOOD[2])
    # cow spots on the right wall: a patch
    c.paint(ellipse(S, 24, 22, 2.0, 2.0), WHITE[1])
    c.paint(ellipse(S, 23.6, 21.6, 1.0, 1.0), "#3A3040")
    return finish(c)


def sugar_cane_field():
    c = Canvas(S)
    soil = ellipse(S, 16, 27.5, 13, 3.4)
    lump(c, soil, SOIL, 2.0, (0.78, 0.5, 0.1))
    for x, top in ((9, 8), (15, 3), (21, 7)):                       # three stalks with nodes
        for y in range(top, 27):
            c.px[y, x] = hex_rgba(CANE[1])
            c.px[y, x + 1] = hex_rgba(CANE[2])
            c.px[y, x - 1 if False else x] = hex_rgba(CANE[1])
            if (y - top) % 5 == 4:
                c.px[y, x] = hex_rgba(CANE[3])
                c.px[y, x + 1] = hex_rgba(CANE[3])
        c.px[top, x] = hex_rgba(CANE[0])
        c.px[top + 1, x] = hex_rgba(CANE[0])
        for dy_, dx_ in ((top + 3, -3), (top + 9, 3)):               # leaves
            for k in range(1, 4):
                yy = dy_ - (k if dx_ < 0 else k)
                xx = x + (dx_ // 3) * k
                if 0 <= xx < S and 0 <= yy < S:
                    c.px[yy, xx] = hex_rgba(CANE[1] if k < 3 else CANE[0])
    for x, y in ((5, 25), (12, 26), (19, 26), (26, 25)):            # sugar crystals
        block(c, x, y, x + 2, y + 2, WHITE)
    return finish(c)


def industrial_oven():
    c = Canvas(S)
    block(c, 7, 8, 11, 20, BRICK)                                   # chimney
    block(c, 6, 6, 12, 8, IRON)
    for y in (11, 14, 17):
        c.paint(rect(S, 7, y, 11, y), "#B8A890")
    block(c, 3, 17, 28, 29, BRICK)                                  # body
    for y in (20, 23, 26):
        c.paint(rect(S, 4, y, 27, y), "#B8A890")
        off = 0 if y == 23 else 3
        for x in range(4 + off, 27, 6):
            c.paint(rect(S, x, y - 2, x, y - 1), "#B8A890")
    block(c, 2, 15, 29, 17, IRON)                                   # top plate
    block(c, 9, 21, 22, 28, IRON)                                   # door frame
    c.paint(rect(S, 11, 23, 20, 27), FIRE[3])
    c.paint(rect(S, 12, 24, 19, 26), FIRE[2])
    c.paint(rect(S, 13, 25, 18, 26), FIRE[1])
    c.paint(rect(S, 14, 26, 17, 26), FIRE[0])
    c.paint(ellipse(S, 10, 3.5, 2.6, 2.0), WHITE[1])                # smoke
    c.paint(ellipse(S, 13.5, 1.8, 2.0, 1.5), WHITE[2])
    return finish(c)


def extra_belt():
    c = Canvas(S)
    block(c, 2, 17, 29, 23, IRON)                                   # frame
    block(c, 3, 15, 28, 17, ["#6E6680", "#58506A", "#463E56", "#342E44"])   # belt surface
    for x in range(5, 28, 4):
        c.paint(rect(S, x, 16, x, 16), "#8E86A0")
    for cx in (9, 18, 24):                                          # cookies
        m = ellipse(S, cx, 12, 3.6, 2.6)
        lump(c, m, DOUGH, 1.8, (0.75, 0.4, 0.0))
        c.px[11, cx - 1] = hex_rgba("#54301F")
        c.px[12, cx + 1] = hex_rgba("#54301F")
    for cx in (5, 26):                                              # rollers / ends
        lump(c, ellipse(S, cx, 20, 3.6, 3.6), WOOD, 2.5)
    for x in (6, 24):                                               # legs
        block(c, x - 1, 24, x + 1, 29, IRON)
    return finish(c)


def chicken_farm():
    c = Canvas(S)
    block(c, 5, 15, 26, 28, WOOD)                                   # coop walls
    for x in range(9, 26, 4):                                       # board lines
        c.paint(rect(S, x, 16, x, 27), WOOD[2])
    c.paint(polygon(S, [(2, 15.5), (15.5, 4), (29, 15.5)]), RED[2])  # gable roof
    lump(c, polygon(S, [(3, 15), (15.5, 5.5), (28, 15)]), RED, 1.5, (0.9, 0.55, 0.1))
    c.paint(rect(S, 2, 15, 29, 15), RED[3])
    c.paint(polygon(S, [(12, 15), (15.5, 11), (19, 15)]), DARKWOOD[2])    # loft hatch
    block(c, 9, 19, 14, 28, DARKWOOD)                               # pop-hole door
    c.paint(rect(S, 10, 20, 13, 27), DARKWOOD[3])
    c.paint(polygon(S, [(9, 28), (14, 28), (12, 30), (9, 30)]), WOOD[2])  # ramp
    c.paint(rect(S, 17, 21, 24, 24), WHITE[1])                      # nest window with an egg
    c.paint(rect(S, 17, 21, 24, 21), DARKWOOD[2])
    c.paint(ellipse(S, 20.5, 23, 1.6, 1.4), "#F2D8A8")
    c.paint(ellipse(S, 24.5, 27, 3.4, 2.0), GOLD[1])                # hay
    c.paint(ellipse(S, 24.2, 26.6, 2.2, 1.0), GOLD[0])
    ch = ellipse(S, 27.5, 26, 3.0, 2.6) | ellipse(S, 29, 22.8, 1.6, 1.8)   # little chicken at the right edge
    c.paint(ch, WHITE[0])
    c.paint(rect(S, 28, 20, 29, 20), RED[0])
    c.paint(rect(S, 30, 23, 30, 23), GOLD[2])
    return finish(c)


def cocoa_plantation():
    c = Canvas(S)
    c.paint(thick_line(2, 5, 29, 4, 2.0), DARKWOOD[1])              # branch
    c.paint(thick_line(2, 4, 29, 3, 1.0), DARKWOOD[0])
    for cx, cy in ((10, 16), (21, 15)):                             # pods
        m = ellipse(S, cx, cy, 5.2, 8.6)
        lump(c, m, COCOA, 3.0, (0.8, 0.5, 0.15))
        for dx_ in (-2, 0, 2):                                      # ridges
            c.paint(thick_line(cx + dx_, cy - 7, cx + dx_ * 0.7, cy + 7, 0.8) & m, COCOA[2])
        c.paint(thick_line(cx, 6, cx, cy - 7, 1.4), DARKWOOD[2])    # stalk
    for lx, ly, flip in ((5, 8, 1), (27, 8, -1), (16, 8, 1)):       # leaves
        m = ellipse(S, lx, ly + 1.5, 3.4, 1.8)
        lump(c, m, GREEN, 1.6, (0.8, 0.45, 0.05))
    return finish(c)


def shipping_centre():
    c = Canvas(S)
    block(c, 3, 19, 15, 29, WOOD)                                   # big crate
    c.paint(thick_line(4, 20, 14, 28, 1.0), WOOD[3])
    c.paint(rect(S, 3, 19, 15, 19), WOOD[3])
    c.paint(rect(S, 3, 28, 15, 28), WOOD[3])
    block(c, 16, 22, 28, 29, WOOD)                                  # second crate
    c.paint(thick_line(17, 23, 27, 28, 1.0), WOOD[3])
    block(c, 6, 11, 13, 18, ["#F0CE92", "#DDAE6C", "#BC8A4C", "#94653A"])   # parcel on top
    c.paint(rect(S, 9, 11, 9, 18), RED[1])
    c.paint(rect(S, 6, 14, 13, 14), RED[1])
    c.paint(polygon(S, [(2, 11), (30, 11), (27, 5), (5, 5)]), RED[1])       # striped awning
    for i, x in enumerate(range(3, 29, 4)):
        if i % 2:
            c.paint(polygon(S, [(x - 0.5, 11), (x + 3.5, 11), (x + 3.0, 5), (x - 0.5 + 0.9, 5)]), WHITE[1])
    c.paint(rect(S, 2, 11, 29, 11), RED[3])
    c.paint(rect(S, 20, 11, 20, 18), DARKWOOD[2])                   # pole
    return finish(c)


def alchemy_lab():
    c = Canvas(S)
    flask = ellipse(S, 14, 21, 8.4, 8.0)
    neck = rect(S, 11, 5, 16, 14)
    lump(c, flask | neck, GLASS, 3.0, (0.82, 0.55, 0.2))
    liquid = ellipse(S, 14, 21, 7.2, 7.0) & (np.mgrid[0:S, 0:S][0] > 17)
    lump(c, liquid, POTION, 3.0, (0.8, 0.45, 0.1))
    c.paint(rect(S, 9, 3, 18, 5), WOOD[2])                          # cork
    c.paint(rect(S, 9, 3, 18, 3), WOOD[0])
    for bx, by, r in ((12, 15, 1.3), (17, 12, 1.0), (10, 10, 0.9)):
        c.paint(ellipse(S, bx, by, r + 0.4, r + 0.4), POTION[0])
    c.paint(ellipse(S, 12, 14.5, 0.8, 0.8), GLASS[0])
    block(c, 23, 17, 28, 28, PURPLE)                                # small vial
    c.paint(rect(S, 24, 14, 27, 17), GLASS[1])
    c.paint(rect(S, 24, 12, 27, 13), WOOD[2])
    c.paint(rect(S, 24, 19, 24, 24), PURPLE[0])
    return finish(c)


def portal_bakery():
    c = Canvas(S)
    block(c, 4, 9, 27, 29, OBS)                                     # frame
    inner = rect(S, 9, 13, 22, 28)
    ys, xs = grid(S)
    ang = np.arctan2(ys - 21, xs - 15.5)
    r = np.hypot((xs - 15.5) / 7, (ys - 21) / 8)
    v = np.sin(ang * 2 + r * 6.0) * 0.5 + 0.5
    idx = np.clip((v * 3 + r * 0.9).astype(int), 0, 3)
    for i in range(4):
        c.paint(inner & (idx == i), ["#E6D0FF", "#B890F0", "#7A58D8", "#4C3AA8"][i])
    for x, y in ((6, 15), (24, 15), (6, 22), (24, 22)):             # runes
        c.paint(rect(S, x, y, x + 1, y + 2), GOLD[1])
    c.paint(rect(S, 3, 27, 28, 29), OBS[2])
    c.paint(rect(S, 3, 27, 28, 27), OBS[0])
    cook = ellipse(S, 16, 6.2, 5.2, 3.8)                            # cookie keystone
    lump(c, cook, DOUGH, 2.0, (0.75, 0.4, 0.0))
    for x, y in ((14, 5), (18, 6), (16, 8)):
        c.px[y, x] = hex_rgba("#54301F")
    return finish(c)


ICONS = {"cow_barn": cow_barn, "sugar_cane_field": sugar_cane_field, "industrial_oven": industrial_oven,
         "extra_belt": extra_belt, "chicken_farm": chicken_farm, "cocoa_plantation": cocoa_plantation,
         "shipping_centre": shipping_centre, "alchemy_lab": alchemy_lab, "portal_bakery": portal_bakery}


def main():
    (ASSETS / "textures/item/building").mkdir(parents=True, exist_ok=True)
    (ASSETS / "models/item/building").mkdir(parents=True, exist_ok=True)
    (ASSETS / "items/building").mkdir(parents=True, exist_ok=True)
    for bid, fn in ICONS.items():
        img = fn()
        img.save(ASSETS / f"textures/item/building/{bid}.png")
        (ASSETS / f"models/item/building/{bid}.json").write_text(json.dumps(
            {"parent": "minecraft:item/generated", "textures": {"layer0": f"{NS}:item/building/{bid}"}}, indent=2) + "\n")
        (ASSETS / f"items/building/{bid}.json").write_text(json.dumps(
            {"model": {"type": "minecraft:model", "model": f"{NS}:item/building/{bid}"}}, indent=2) + "\n")
        print(f"wrote {bid}")


if __name__ == "__main__":
    main()
