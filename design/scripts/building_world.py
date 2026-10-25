"""World models of the 7 bakery buildings x 3 stages (feature building-roster).

Outputs (under resourcepack/assets/cookieclicker/):
  items/building_world/<id>.json                select on custom_model_data strings[0] = "1"/"2"/"3"
  models/item/building_world/<id>_<stage>.json  18 cuboid models
  textures/item/building_world/<id>.png         one atlas per building (2 texels per model unit), shared by its 3 stages

ItemDisplay NONE draws model (8, 8, 8) at the display position: the lowest element y is 8 (asserted) and
x/z are centred on 8. Model front = north face (-Z). Everything is in "local" units: x/z centred on 0,
y 0 = floor, 16 units = 1 block. Heights are capped at 24 units (model y 32 is the vanilla limit).
Other scripts import STAGES / footprint() (building_slots.py, decor_style.py), so keep this importable.
Deterministic. Spec: design/buildings-world.md
"""
from __future__ import annotations

import json
import random
from pathlib import Path

import numpy as np
from PIL import Image

NS = "cookieclicker"
ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / "resourcepack/assets" / NS
PX = 2          # texels per model unit
YOFF = 8        # model y 8 = display position
IDS = ["villager_baker", "industrial_oven", "extra_belt", "shipping_centre", "alchemy_lab", "portal_bakery", "chicken_farm"]

# ---- ramps light -> dark, hue shifted (lights lean yellow, shadows lean red/purple)
PAL = {
    "brick": ["#E0987A", "#C0694F", "#9C4A3C", "#743434", "#4E2434"],
    "stone": ["#D8CFBC", "#B6AC9C", "#928A82", "#6E6874", "#4C4658"],
    "wood": ["#D8A06A", "#B97A4C", "#94583A", "#6E3F2E", "#4A2B26"],
    "lightwood": ["#F0CE92", "#DDAE6C", "#BC8A4C", "#94653A", "#684330"],
    "darkwood": ["#8E6248", "#6E4638", "#54332E", "#3F2626", "#2C1A1E"],
    "copper": ["#F2B684", "#D98A56", "#B5623E", "#8A4430", "#5A2E2C"],
    "gold": ["#FFF2A8", "#F4CF55", "#D79B2B", "#A96A24", "#6E3F24"],
    "iron": ["#B2B2BC", "#8A8A98", "#646476", "#464658", "#2E2C3C"],
    "white": ["#FFFBEF", "#ECE3D2", "#CDBDA8", "#A8948A", "#7C6A68"],
    "cream": ["#F6E8C0", "#E6D2A0", "#C8AE80", "#A08660", "#7A6048"],
    "skin": ["#F4CDA0", "#DCA67A", "#B97A58", "#8C5440", "#5E3A2C"],
    "cloth": ["#8E7A9A", "#6E5E80", "#544866", "#40344E", "#2C2438"],
    "red": ["#EE8A68", "#CE5A44", "#A43E34", "#7C2C2E", "#521E26"],
    "blue": ["#86AEDC", "#5E86C2", "#4E6EAA", "#34487C", "#262E58"],
    "purple": ["#D4AAF4", "#A878DC", "#8050BC", "#5C3894", "#3E2468"],
    "obs": ["#62508A", "#443466", "#2E2248", "#1E1432", "#120C20"],
    "glass": ["#DCF6F0", "#AEDEDA", "#80C0C0", "#5C9CAA", "#3E7488"],
    "potion": ["#EAFFA0", "#B8EC62", "#82CC3E", "#52A430", "#30782A"],
    "amber": ["#FFE6A0", "#F4BE5A", "#D8903A", "#A8602E", "#743C2A"],
    "fire": ["#FFF2A8", "#FFD060", "#F4A038", "#D8662C", "#A03A28"],
    "dough": ["#F7DC9A", "#EBBE6C", "#D69A4C", "#B8743A", "#8E5032"],
    "rubber": ["#6E6680", "#58506A", "#463E56", "#342E44", "#242030"],
    "kraft": ["#E6C48C", "#D2A66C", "#B58852", "#8E6640", "#684630"],
    "portal": ["#E6D0FF", "#B890F0", "#7A58D8", "#4C3AA8", "#2C2070"],
    "portal2": ["#FFF0C0", "#FFC8A0", "#F08CB8", "#B058C8", "#6A38A0"],
}
CHIP = ["#86523C", "#54301F", "#311A1E"]


def rgba(h):
    return np.array([int(h[1:3], 16), int(h[3:5], 16), int(h[5:7], 16), 255], np.uint8)


def R(pal):
    return [rgba(c) for c in PAL[pal]]


# ---------------------------------------------------------------- painters (return h x w x 4)
def bevel(img, p, strong=True):
    """Lit top row / left column, shadow bottom row / right column."""
    h, w = img.shape[:2]
    if h > 2:
        img[0, :] = p[1]
        img[h - 1, :] = p[3]
    if w > 2:
        img[:, 0] = p[1] if h > 2 else p[1]
        img[:, w - 1] = p[3]
    return img


def flat(p, w, h, rng, speck=0.07):
    img = np.zeros((h, w, 4), np.uint8)
    img[:] = p[2]
    for y in range(h):
        for x in range(w):
            r = rng.random()
            if r < speck:
                img[y, x] = p[1]
            elif r > 1 - speck:
                img[y, x] = p[3]
    return bevel(img, p)


def bricks(p, w, h, rng, mortar=None):
    m = mortar or R("stone")[1]
    img = np.zeros((h, w, 4), np.uint8)
    img[:] = m
    for r0 in range(0, h, 4):
        off = (r0 // 4 % 2) * 4
        for x0 in range(-off, w, 8):
            col = p[rng.choice([1, 2, 2, 3])]
            for y in range(r0, min(r0 + 3, h)):
                for x in range(max(0, x0), min(w, x0 + 7)):
                    img[y, x] = col if y > r0 else p[max(0, [i for i in range(5) if (p[i] == col).all()][0] - 1)]
    return img


def planks(p, w, h, rng):
    img = np.zeros((h, w, 4), np.uint8)
    for y in range(h):
        k = y % 4
        img[y, :] = p[1] if k == 0 else (p[3] if k == 3 else p[2])
    for r0 in range(0, h, 4):
        for _ in range(max(1, w // 14)):
            x = rng.randrange(w)
            img[r0:min(h, r0 + 4), x] = p[3]
        for _ in range(w // 8):
            img[min(h - 1, r0 + 1 + rng.randrange(2)), rng.randrange(w)] = p[3]
    return img


def crate(p, w, h, rng):
    img = planks(p, w, h, rng)
    b = 2 if min(w, h) >= 8 else 1
    img[:b, :] = p[3]
    img[-b:, :] = p[4]
    img[:, :b] = p[3]
    img[:, -b:] = p[4]
    img[b:b + 1, b:w - b] = p[1]
    img[b:h - b, b:b + 1] = p[1]
    for i in range(b, min(w, h) - b):          # diagonal brace
        img[i, i] = p[4]
        if i + 1 < w - b:
            img[i, i + 1] = p[3]
    return img


def plate(p, w, h, rng):
    img = flat(p, w, h, rng, 0.03)
    if w >= 6 and h >= 6:
        for x, y in ((2, 2), (w - 3, 2), (2, h - 3), (w - 3, h - 3)):
            img[y, x] = p[0]
            if x + 1 < w:
                img[y, x + 1] = p[4]
    return img


def stripes(p, w, h, rng):
    img = np.zeros((h, w, 4), np.uint8)
    red, wh = R("red"), R("white")
    for x in range(w):
        c = red if (x // 4) % 2 == 0 else wh
        for y in range(h):
            img[y, x] = c[1] if y == 0 else (c[3] if y == h - 1 else c[2])
    return img


def slats(p, w, h, rng):
    img = np.zeros((h, w, 4), np.uint8)
    for x in range(w):
        k = x % 4
        img[:, x] = p[1] if k == 0 else (p[3] if k == 3 else p[2])
    img[0, :] = p[4]
    img[h - 1, :] = p[4]
    return img


def glow(p, w, h, rng):
    img = np.zeros((h, w, 4), np.uint8)
    for y in range(h):
        t = y / max(1, h - 1)
        base = 1 + int(t * 2.4)
        for x in range(w):
            c = base + (1 if rng.random() < 0.18 else 0) - (1 if rng.random() < 0.12 else 0)
            img[y, x] = p[max(0, min(4, c))]
    if min(w, h) > 4:
        img[0, :] = p[3]
        img[:, 0] = p[3]
        img[:, w - 1] = p[3]
    return img


def glass(p, w, h, rng):
    img = np.zeros((h, w, 4), np.uint8)
    img[:] = p[2]
    img[:, 0] = p[0]
    img[0, :] = p[1]
    img[:, w - 1] = p[3]
    img[h - 1, :] = p[4]
    if h > 5 and w > 3:
        img[1:h // 2, 1] = p[0]
    return img


def swirl(p, w, h, rng):
    img = np.zeros((h, w, 4), np.uint8)
    cx, cy = (w - 1) / 2, (h - 1) / 2
    for y in range(h):
        for x in range(w):
            dx, dy = (x - cx) / (w / 2), (y - cy) / (h / 2)
            r = (dx * dx + dy * dy) ** 0.5
            a = np.arctan2(dy, dx)
            v = np.sin(a * 2 + r * 7.0) * 0.5 + 0.5
            c = int(np.clip(v * 3.2 + r * 1.0, 0, 4))
            img[y, x] = p[c]
    return img


def vein(p, w, h, rng):
    img = flat(p, w, h, rng, 0.12)
    pur = R("purple")
    for _ in range(max(2, w * h // 90)):
        x, y = rng.randrange(w), rng.randrange(h)
        for _ in range(rng.randrange(2, 5)):
            if 0 < x < w - 1 and 0 < y < h - 1:
                img[y, x] = pur[3]
            x += rng.choice([-1, 0, 1])
            y += rng.choice([0, 1])
    return img


def cookie(p, w, h, rng):
    img = flat(p, w, h, rng, 0.05)
    for _ in range(max(1, w * h // 14)):
        x, y = rng.randrange(1, max(2, w - 2)), rng.randrange(1, max(2, h - 1))
        img[y, x] = rgba(CHIP[1])
        if x + 1 < w - 1:
            img[y, x + 1] = rgba(CHIP[1])
    return img


def face(p, w, h, rng):
    """Baker head front (10 x 10 px for 5 x 5 units): eyes, brows, rosy cheeks, moustache."""
    img = flat(p, w, h, rng, 0.0)
    img[:] = p[2]
    img[0, :] = p[1]
    img[:, 0] = p[1]
    img[:, w - 1] = p[3]
    img[h - 1, :] = p[3]
    wh, ir, br, mo = rgba("#FFF6EA"), rgba("#4E8A3C"), rgba("#5E3A2C"), rgba("#8A5A40")
    k = max(1, w // 10)
    ey = int(h * 0.4)
    for ex in (int(w * 0.22), int(w * 0.62)):
        img[ey, ex:ex + 2 * k] = wh
        img[ey, ex + (k if ex < w // 2 else 0)] = ir
        img[ey - 1, ex:ex + 2 * k] = br
    img[int(h * 0.62):int(h * 0.62) + 1, int(w * 0.2):int(w * 0.4)] = rgba("#E08C74")
    img[int(h * 0.62):int(h * 0.62) + 1, int(w * 0.6):int(w * 0.8)] = rgba("#E08C74")
    img[int(h * 0.74), int(w * 0.28):int(w * 0.72)] = mo
    img[int(h * 0.74) + 1, int(w * 0.36):int(w * 0.64)] = br
    return img


PAINT = {"flat": flat, "bricks": bricks, "planks": planks, "crate": crate, "plate": plate, "stripes": stripes,
         "slats": slats, "glow": glow, "glass": glass, "swirl": swirl, "vein": vein, "cookie": cookie, "face": face}


def paint(mat, w, h, seed):
    kind, _, pal = mat.partition(":")
    return PAINT[kind](R(pal), w, h, random.Random(seed))


# ---------------------------------------------------------------- geometry
FACES = ("north", "south", "east", "west", "up")


class Box:
    def __init__(self, x0, y0, z0, x1, y1, z1, mat, over=None, rot=None, faces=FACES):
        self.a, self.b, self.mat, self.over, self.rot, self.faces = (x0, y0, z0), (x1, y1, z1), mat, over or {}, rot, faces


def fsize(bx, f):
    dx, dy, dz = (bx.b[i] - bx.a[i] for i in range(3))
    w, h = {"north": (dx, dy), "south": (dx, dy), "east": (dz, dy), "west": (dz, dy), "up": (dx, dz), "down": (dx, dz)}[f]
    return max(1, int(round(w * PX))), max(1, int(round(h * PX)))


def B(*a, **k):
    return Box(*a, **k)


def mirror_x(b):
    return Box(-b.b[0], b.a[1], b.a[2], -b.a[0], b.b[1], b.b[2], b.mat, b.over, b.rot, b.faces)


# ---------------------------------------------------------------- the six buildings
def apprentice(s):
    o = []
    o += [B(-2.5, 0, -1.5, -0.3, 5, 1.5, "flat:cloth"), B(0.3, 0, -1.5, 2.5, 5, 1.5, "flat:cloth")]
    jacket = "flat:white"
    o.append(B(-3, 5, -2, 3, 11, 2, jacket))
    o.append(B(-2.2, 5, -2.4, 2.2, 10, -2, "flat:blue" if s == 3 else "flat:cream"))
    o += [B(-4.6, 6, -1.2, -3, 11, 1.2, jacket), B(3, 6, -1.2, 4.6, 11, 1.2, jacket)]
    o += [B(-4.4, 5, -1, -3.2, 6, 1, "flat:skin"), B(3.2, 5, -1, 4.4, 6, 1, "flat:skin")]
    o.append(B(-2.5, 11, -2.5, 2.5, 16, 2.5, "flat:skin", over={"north": "face:skin"}))
    o.append(B(-0.6, 12, -3.4, 0.6, 14, -2.5, "flat:skin"))
    if s >= 2:
        o.append(B(-2.8, 10.5, -2.3, 2.8, 11.2, 2.3, "flat:red"))
        o += [B(-0.4, 8, -2.7, 0.4, 8.8, -2.4, "flat:gold"), B(-0.4, 6, -2.7, 0.4, 6.8, -2.4, "flat:gold")]
        o.append(B(3.8, 5.2, -4, 4.6, 6.4, 1.5, "flat:lightwood"))     # rolling pin
    band = "flat:gold" if s == 3 else "flat:white"
    o.append(B(-3, 16, -3, 3, 17.5, 3, band))
    if s == 1:
        o.append(B(-3.5, 17.5, -3.5, 3.5, 20.5, 3.5, "flat:white"))
    elif s == 2:
        o.append(B(-3.5, 17.5, -3.5, 3.5, 21, 3.5, "flat:white"))
        o.append(B(-2.5, 21, -2.5, 2.5, 22.5, 2.5, "flat:white"))
    else:
        o.append(B(-4, 17.5, -4, 4, 22, 4, "flat:white"))
        o.append(B(-2.5, 22, -2.5, 2.5, 24, 2.5, "flat:white"))
        o.append(B(-0.8, 18, -4.3, 0.8, 19.6, -4, "flat:gold"))        # star badge on the hat
        o.append(B(-3.5, 9.4, -6.5, 3.5, 10.2, -3, "flat:lightwood"))   # tray
        o += [B(-2.7, 10.2, -5.8, -0.5, 11.2, -3.6, "cookie:dough"), B(0.5, 10.2, -5.8, 2.7, 11.2, -3.6, "cookie:dough")]
        o += [B(-4.4, 9.4, -4.2, -3.2, 10.4, -3, "flat:skin"), B(3.2, 9.4, -4.2, 4.4, 10.4, -3, "flat:skin")]
    return o


def oven(s):
    o = [B(-10, 0, -8, 10, 2, 8, "flat:stone"), B(-9, 2, -7, 9, 11, 7, "bricks:brick"),
         B(-10, 11, -8, 10, 12.5, 8, "plate:copper")]
    o.append(B(-5, 2, -7.6, 5, 9, -7, "plate:iron"))
    o.append(B(-5.5, 9, -7.8, 5.5, 10.2, -7, "flat:stone"))
    o.append(B(-3.5, 3, -8.1, 3.5, 7.5, -7.6, "flat:iron", over={"north": "glow:fire"}))
    if s == 1:
        o.append(B(4.5, 12.5, 2, 7.5, 15, 5, "plate:iron"))
    else:
        o.append(B(3.5, 12.5, 1, 8, 20 if s == 3 else 22, 5.5, "bricks:brick"))
        if s == 2:
            o.append(B(3, 22, 0.5, 8.5, 23, 6, "plate:iron"))
        for y in (0, 3):                                                      # log pile behind
            o += [B(-6, y, 8.4, 2, y + 3, 10.4, "planks:darkwood"), B(-6, y, 10.4, 2, y + 3, 12.4, "planks:darkwood")] if y == 0 \
                else [B(-5, y, 9, 1, y + 3, 11.8, "planks:darkwood")]
    if s == 3:
        o.append(B(3, 20, 0.5, 8.5, 24, 6, "plate:copper"))
        o.append(B(-8, 12.5, 1, -4.5, 18, 4.5, "bricks:brick"))
        o.append(B(-8.5, 18, 0.5, -4, 19.5, 5, "plate:copper"))
        o.append(B(-9.3, 6.5, -7.3, 9.3, 7.8, 7.3, "plate:gold"))
        o += [B(9, 3.5, -3, 9.4, 8.5, 3, "glow:fire"), B(-9.4, 3.5, -3, -9, 8.5, 3, "glow:fire")]
        o.append(B(-1, 12.5, -1, 1, 14.5, 1, "glow:fire"))
    return o


def belt(s):
    o = []
    for sx in (-1, 1):
        for sz in (-1, 1):
            o.append(B(min(sx * 11.5, sx * 9.5), 0, min(sz * 3.5, sz * 1.5), max(sx * 11.5, sx * 9.5), 3, max(sz * 3.5, sz * 1.5), "plate:iron"))
    o.append(B(-12, 3, -4, 12, 5, 4, "plate:iron"))
    o.append(B(-11, 5, -3, 11, 5.8, 3, "flat:rubber", over={"up": "slats:rubber"}))
    o += [B(-12.5, 3.5, -3.8, -10.5, 6.2, 3.8, "planks:wood"), B(10.5, 3.5, -3.8, 12.5, 6.2, 3.8, "planks:wood")]
    xs = [-6, 0, 6] if s == 1 else [-8, -4, 0, 4, 8]
    for cx in xs:
        o.append(B(cx - 1.5, 5.8, -1.5, cx + 1.5, 6.9, 1.5, "cookie:dough"))
    if s >= 2:
        o += [B(-11, 5.8, -4, 11, 7.5, -3.2, "plate:copper"), B(-11, 5.8, 3.2, 11, 7.5, 4, "plate:copper")]
    if s == 3:
        for gx in (-5, 3):
            o += [B(gx, 0, -5.2, gx + 2, 13, -4.2, "plate:copper"), B(gx, 0, 4.2, gx + 2, 13, 5.2, "plate:copper"),
                  B(gx - 0.5, 13, -5.4, gx + 2.5, 14.5, 5.4, "plate:gold"),
                  B(gx + 0.2, 11.6, -1, gx + 1.8, 13, 1, "glow:fire")]
    return o


def shipping(s):
    o = [B(-12, 0, -8, 12, 1.5, 8, "planks:wood")]
    o.append(B(-11, 1.5, -7, -3, 9.5, 1, "crate:wood"))
    o.append(B(-2, 1.5, -7, 6, 8.5, -0.5, "crate:wood"))
    o.append(B(-10, 9.5, -6, -4, 15, 0, "crate:lightwood"))
    o.append(B(-11, 1.5, 2.5, -5, 6, 7, "crate:wood"))
    if s == 3:
        o.append(B(7, 1.5, 2, 11, 6, 6, "crate:gold"))
    else:
        o.append(B(7, 1.5, 2, 11, 6, 6, "flat:kraft"))
        o.append(B(8.7, 1.5, 1.8, 9.3, 6.2, 6.2, "flat:red"))
    if s >= 2:
        for sx in (-1, 1):
            for sz in (-1, 1):
                x0 = sx * 12 if sx < 0 else 10.5
                z0 = sz * 8 if sz < 0 else 6.5
                o.append(B(x0, 1.5, z0, x0 + 1.5, 19, z0 + 1.5, "planks:darkwood"))
        o.append(B(-12.5, 19, -8.5, 12.5, 20.5, 8.5, "stripes:red"))
    if s == 3:
        o.append(B(-12.5, 20.5, -4, 12.5, 22.5, 4, "stripes:red"))
        o.append(B(-12.8, 18.4, -8.8, 12.8, 19.2, 8.8, "plate:gold"))
        o.append(B(-0.3, 16.5, -0.3, 0.3, 19, 0.3, "flat:iron"))
        o.append(B(-1.5, 13.5, -1.5, 1.5, 16.5, 1.5, "glow:fire"))
    return o


def alchemy(s):
    o = [B(-12, 5, -5, 0, 6.5, 5, "planks:wood")]
    for x0 in (-12, -1.5):
        for z0 in (-5, 3.5):
            o.append(B(x0, 0, z0, x0 + 1.5, 5, z0 + 1.5, "planks:darkwood"))
    o += [B(-10, 6.5, -2, -7.5, 10, 0.5, "glass:potion"), B(-9.4, 10, -1.4, -8.1, 11, -0.1, "flat:wood")]
    o += [B(-6, 6.5, 1, -3.5, 9, 3.5, "glass:purple"), B(-5.3, 9, 1.7, -4.2, 10, 2.8, "flat:wood")]
    o += [B(-3, 6.5, -3.5, -1, 11.5, -1.5, "glass:amber"), B(-2.7, 11.5, -3.2, -1.3, 12.5, -1.8, "flat:wood")]
    o.append(B(3, 0, -4.5, 11, 5.5, 4.5, "plate:iron"))
    liquid = "glow:purple" if s == 3 else "glow:potion"
    o.append(B(3.5, 5.5, -4, 10.5, 6.4, 4, "flat:iron", over={"up": liquid}))
    o += [B(2.5, 5.5, -5, 11.5, 7, -4, "plate:iron"), B(2.5, 5.5, 4, 11.5, 7, 5, "plate:iron"),
          B(2.5, 5.5, -4, 3.5, 7, 4, "plate:iron"), B(10.5, 5.5, -4, 11.5, 7, 4, "plate:iron")]
    if s >= 2:
        o += [B(-12, 0, 5.6, -11, 17, 7.4, "planks:darkwood"), B(-1, 0, 5.6, 0, 17, 7.4, "planks:darkwood"),
              B(-11, 6.5, 6.8, -1, 17, 7.4, "planks:darkwood")]
        for y in (11, 15):
            o.append(B(-11, y, 5.5, -1, y + 0.8, 6.8, "planks:wood"))
        for i, (x, y, pal) in enumerate([(-10, 11.8, "glass:potion"), (-7, 11.8, "glass:purple"), (-4, 11.8, "glass:amber"),
                                          (-9, 15.8, "glass:amber"), (-5.5, 15.8, "glass:potion")]):
            o.append(B(x, y, 5.9, x + 1.8, y + 2.6, 6.8, pal))
    if s == 3:
        o.append(B(5.5, 12, -1.5, 8.5, 20, 1.5, "glass:purple", rot=("y", 45, (7, 0))))
        o.append(B(6.2, 20, -0.8, 7.8, 22, 0.8, "glass:purple", rot=("y", 45, (7, 0))))
        o += [B(-8, 17, 5.9, -6.5, 18.5, 6.8, "glow:fire"), B(-4, 17, 5.9, -2.5, 18.5, 6.8, "glow:fire")]
    return o


def portal(s):
    o = [B(-11, 0, -5, 11, 1.5, 5, "vein:obs")]
    top = 21 if s >= 2 else 21
    o += [B(-10, 1.5, -4, -6, 18, 4, "vein:obs"), B(6, 1.5, -4, 10, 18, 4, "vein:obs"),
          B(-10, 18, -4, 10, top, 4, "vein:obs")]
    sw = "swirl:portal2" if s == 3 else "swirl:portal"
    o.append(B(-6, 1.5, -0.6, 6, 18, 0.6, "flat:purple", over={"north": sw, "south": sw}))
    for x in (-9, 7):
        o.append(B(x, 9, -4.4, x + 2, 12, -4, "plate:gold"))
    if s >= 2:
        for x in (-9, 7):
            o.append(B(x, 9, 4, x + 2, 12, 4.4, "plate:gold"))
        o.append(B(-3, top, -3, 3, 23.5, 3, "cookie:dough"))
        o += [B(-9, 14, -4.4, -7, 16, -4, "glow:fire"), B(7, 14, -4.4, 9, 16, -4, "glow:fire")]
    if s == 3:
        for x in (-9, 7):
            o.append(B(x, top, -2, x + 2, 24, 2, "plate:gold"))
        o.append(B(-11.3, 0.8, -5.3, 11.3, 1.9, 5.3, "plate:gold"))
    return o


def chicken(x, z):
    """A small white hen at (x, z), beak towards -z."""
    return [B(x - 1.3, 0, z - 0.6, x - 0.7, 2.2, z + 0.1, "flat:gold"), B(x + 0.7, 0, z - 0.6, x + 1.3, 2.2, z + 0.1, "flat:gold"),
            B(x - 2, 2, z - 2.5, x + 2, 6, z + 2.5, "flat:white"),
            B(x - 2.4, 3, z - 1.5, x - 2, 5.5, z + 1.5, "flat:cream"), B(x + 2, 3, z - 1.5, x + 2.4, 5.5, z + 1.5, "flat:cream"),
            B(x - 0.8, 5, z + 2.5, x + 0.8, 8, z + 3.4, "flat:white"),
            B(x - 1, 6, z - 3.7, x + 1, 9, z - 1.7, "flat:white"),
            B(x - 0.4, 9, z - 3.2, x + 0.4, 10.2, z - 2, "flat:red"), B(x - 0.4, 6, z - 3.9, x + 0.4, 7, z - 3.6, "flat:red"),
            B(x - 0.5, 7.2, z - 4.6, x + 0.5, 8.2, z - 3.7, "flat:amber")]


def hay(x0, z0, x1, z1, h):
    return [B(x0, 0, z0, x1, h, z1, "slats:kraft")]


def coop(x0, x1, z0, z1, floor, wall, top, s):
    """Walls (box with a door and a pop-hole), a gable made of steps and two 45 degree roof panels."""
    o = [B(x0, floor, z0, x1, wall, z1, "planks:" + ("red" if s == 3 else "wood"))]
    cx = (x0 + x1) / 2
    o.append(B(cx - 1.8, floor, z0 - 0.3, cx + 1.8, floor + 5.5, z0 + 0.1, "flat:darkwood"))             # pop-hole door
    o.append(B(cx - 2.4, floor + 5.5, z0 - 0.4, cx + 2.4, floor + 6.4, z0 + 0.2, "planks:cream" if s == 3 else "planks:lightwood"))
    ramp = B(cx - 1.5, floor - 0.5, z0 - 6.5, cx + 1.5, floor + 0.3, z0 - 0.2, "slats:wood")
    ramp.rot = ("x", -22.5, (0, (z0 - 6.5 + z0 - 0.2) / 2))
    o.append(ramp)
    half = (x1 - x0) / 2 + 0.6
    steps = int(half // 2.4)
    for i in range(steps):                                                                              # gable infill
        w = half - 0.6 - i * 2.4
        o.append(B(cx - w, wall + i * 2.4, z0 + 0.3, cx + w, wall + (i + 1) * 2.4, z1 - 0.3, "planks:lightwood"))
    run = half * 1.4142 + 0.4                                                                           # panel length along its slope
    rise = half
    ptop = wall + rise / 2 + 0.3
    for sign in (-1, 1):
        px = cx + sign * (half / 2)
        panel = B(px - run / 2, ptop - 0.5, z0 - 1.2, px + run / 2, ptop + 0.5, z1 + 1.2, "stripes:red" if s == 3 else "planks:darkwood")
        panel.rot = ("z", -45 * sign, (px, 0))
        o.append(panel)
    o.append(B(cx - 0.6, wall + rise - 0.3, z0 - 1.3, cx + 0.6, wall + rise + 0.9, z1 + 1.3, "planks:darkwood"))   # ridge cap
    return o


def chicken_farm(s):
    if s == 1:
        zc0, zc1 = -4.5, 4.5
        o = [B(x, 0, z, x + 1.3, 3, z + 1.3, "planks:darkwood") for x in (-6, 4.7) for z in (zc0, zc1 - 1.3)]
        o += coop(-6, 6, zc0, zc1, 3, 10.5, 0, s)
        o += [B(6, 4, -2.5, 9.5, 8, 2.5, "crate:lightwood"), B(6.2, 8, -2.2, 9.3, 8.7, 2.2, "flat:kraft"),
              B(9.5, 5, -2.5, 10.1, 8, 2.5, "planks:darkwood")]                                           # nest box
        o += hay(-10, -2, -7, 1.5, 3.5)
        o += chicken(-8, -8.5) + chicken(6, -9)
        return o
    zc0, zc1 = 0, 9
    o = [B(x, 0, z, x + 1.3, 3, z + 1.3, "planks:darkwood") for x in (-7, 5.7) for z in (zc0 + 0.2, zc1 - 1.5)]
    o += coop(-7, 7, zc0, zc1, 3, 10.5 + (1 if s == 3 else 0), 0, s)
    o += [B(7, 4, 2.5, 10.5, 8.5, 7, "crate:lightwood"), B(7.2, 8.5, 2.8, 10.3, 9.2, 6.7, "flat:kraft"),
          B(10.5, 5, 2.5, 11.1, 8.5, 7, "planks:darkwood")]
    for x in (-11, 11):                                                                                 # fenced run in front
        for z in (-11, -6, -1):
            o.append(B(x - 0.7, 0, z - 0.7, x + 0.7, 5.5, z + 0.7, "planks:darkwood"))
    for z0_ in (-11, -6):
        for x in (-11, 11):
            o.append(B(x - 0.4, 2, z0_, x + 0.4, 3.2, z0_ + 5, "planks:wood"))
            o.append(B(x - 0.4, 4, z0_, x + 0.4, 5.2, z0_ + 5, "planks:wood"))
    for x0_ in (-11, -3.5, 3.5):
        for y in (2, 4):
            o.append(B(x0_, y, -11.4, x0_ + 7.5, y + 1.2, -10.6, "planks:wood"))
    o += [B(-6, 0, -5, -1, 2.2, -3.4, "planks:darkwood"), B(-5.6, 2.2, -4.6, -1.4, 2.8, -3.8, "flat:kraft")]     # feeder
    o += hay(5.5, -4.5, 9.5, -1.5, 3.5)
    o += chicken(-7, -8) + chicken(2, -9)
    if s == 3:
        o += chicken(8, -8.5)
        o += [B(-0.4, 14, 3.9, 0.4, 14.2, 4.7, "flat:gold")] if False else []
        cx = 0
        o += [B(cx - 0.3, 19, 4, cx + 0.3, 23, 4.6, "plate:gold"), B(cx - 2.4, 21, 4.1, cx + 2.4, 21.6, 4.5, "plate:gold"),
              B(cx - 1.4, 22.2, 4.0, cx + 1.4, 23.6, 4.6, "plate:gold")]                                    # weather vane
        o += [B(-3.2, 3, -1.2, -1, 4.6, 0.8, "plate:gold"), B(-2.6, 4.6, -0.8, -1.6, 5.8, 0.4, "plate:gold")]   # golden eggs by the feeder
    return o


BUILD = {"villager_baker": apprentice, "industrial_oven": oven, "extra_belt": belt, "shipping_centre": shipping,
         "alchemy_lab": alchemy, "portal_bakery": portal, "chicken_farm": chicken_farm}
STAGES = {b: {s: BUILD[b](s) for s in (1, 2, 3)} for b in IDS}


def bounds(boxes):
    lo = [min(b.a[i] for b in boxes) for i in range(3)]
    hi = [max(b.b[i] for b in boxes) for i in range(3)]
    return lo, hi


def footprint(building, stage=None):
    """Half extent in blocks of the centred square that holds the building (any yaw), max over stages."""
    boxes = [b for s in ((stage,) if stage else (1, 2, 3)) for b in STAGES[building][s]]
    lo, hi = bounds(boxes)
    return max(abs(lo[0]), abs(lo[2]), hi[0], hi[2]) / 16


# ---------------------------------------------------------------- output
class Atlas:
    def __init__(self, size, seed):
        self.size, self.seed = size, seed
        self.img = np.zeros((size, size, 4), np.uint8)
        self.x = self.y = self.rowh = 0
        self.cache = {}

    def get(self, mat, w, h):
        key = (mat, w, h)
        if key not in self.cache:
            if self.x + w > self.size:
                self.x, self.y, self.rowh = 0, self.y + self.rowh, 0
            if self.y + h > self.size:
                raise OverflowError
            self.img[self.y:self.y + h, self.x:self.x + w] = paint(mat, w, h, self.seed + len(self.cache))
            self.cache[key] = (self.x, self.y)
            self.x += w
            self.rowh = max(self.rowh, h)
        x, y = self.cache[key]
        k = 16 / self.size
        return [round(v, 4) for v in (x * k, y * k, (x + w) * k, (y + h) * k)]


def model_json(bid, boxes, atlas, height):
    elements = []
    for bx in boxes:
        faces = {}
        for f in bx.faces:
            mat = bx.over.get(f, bx.mat)
            w, h = fsize(bx, f)
            faces[f] = {"uv": atlas.get(mat, w, h), "texture": "#0"}
        e = {"from": [bx.a[0] + 8, bx.a[1] + YOFF, bx.a[2] + 8], "to": [bx.b[0] + 8, bx.b[1] + YOFF, bx.b[2] + 8], "faces": faces}
        if bx.rot:
            axis, ang, (ox, oz) = bx.rot
            assert ang in (-45, -22.5, 0, 22.5, 45)
            e["rotation"] = {"angle": ang, "axis": axis, "origin": [ox + 8, (bx.a[1] + bx.b[1]) / 2 + YOFF, oz + 8]}
        elements.append(e)
    assert min(e["from"][1] for e in elements) == 8, "lowest element y must be 8"
    assert max(e["to"][1] for e in elements) <= 32, "taller than model y 32"
    for e in elements:
        assert all(-16 <= v <= 32 for v in e["from"] + e["to"]), e
    t = -round(height / 2 * 0.9, 1)
    return {"textures": {"0": f"{NS}:item/building_world/{bid}", "particle": f"{NS}:item/building_world/{bid}"},
            "elements": elements,
            "display": {"gui": {"rotation": [30, 225, 0], "scale": [0.5, 0.5, 0.5], "translation": [0, t * 0.5, 0]},
                        "fixed": {"rotation": [0, 0, 0], "scale": [0.5, 0.5, 0.5], "translation": [0, t * 0.5, 0]}}}


def main():
    mdir, tdir, idir = (ASSETS / "models/item/building_world", ASSETS / "textures/item/building_world",
                        ASSETS / "items/building_world")
    for d in (mdir, tdir, idir):
        d.mkdir(parents=True, exist_ok=True)
    for bi, bid in enumerate(IDS):
        for size in (128, 256):
            try:
                atlas = Atlas(size, 500 + bi * 1000)
                models = {}
                for s in (1, 2, 3):
                    lo, hi = bounds(STAGES[bid][s])
                    models[s] = model_json(bid, STAGES[bid][s], atlas, hi[1])
                break
            except OverflowError:
                continue
        for s, m in models.items():
            (mdir / f"{bid}_{s}.json").write_text(json.dumps(m, indent=2) + "\n")
        Image.fromarray(atlas.img).save(tdir / f"{bid}.png")
        item = {"model": {"type": "minecraft:select", "property": "minecraft:custom_model_data", "index": 0,
                          "cases": [{"when": str(s), "model": {"type": "minecraft:model", "model": f"{NS}:item/building_world/{bid}_{s}"}}
                                    for s in (1, 2, 3)],
                          "fallback": {"type": "minecraft:model", "model": f"{NS}:item/building_world/{bid}_1"}}}
        (idir / f"{bid}.json").write_text(json.dumps(item, indent=2) + "\n")
        for s in (1, 2, 3):
            lo, hi = bounds(STAGES[bid][s])
            print(f"{bid} stage {s}: x {lo[0]:.1f}..{hi[0]:.1f} z {lo[2]:.1f}..{hi[2]:.1f} h {hi[1]:.1f} "
                  f"({hi[0]-lo[0]:.0f}x{hi[2]-lo[2]:.0f} units) {len(STAGES[bid][s])} elements atlas {atlas.size}")


if __name__ == "__main__":
    main()
