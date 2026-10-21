"""Click upgrade icons (hologram panel rows and upgrade shelf): 10 flat 32x32 item textures.

Output: resourcepack/assets/cookieclicker/{textures,models}/item/upgrade/<id>.png|json and
        resourcepack/assets/cookieclicker/items/upgrade/<id>.json
Run with `mise run assets`. Deterministic. Spec: design/upgrade-icons.md
"""
import json
import sys
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parent / "lib"))
from pixel import Canvas, clean_orphans, ellipse, grid, hex_rgba, lit_side, shade  # noqa: E402

ASSETS = Path("resourcepack/assets/cookieclicker")
S = 32

# ramps: light, base, shadow, outline
WOOD = ["#D9AE6E", "#B58444", "#8A5E2E", "#4F321B"]
STONE = ["#B9B9B2", "#8E8E8A", "#66666A", "#37373C"]
IRON = ["#F2F2EE", "#CFCFCC", "#9C9CA2", "#4A4A54"]
GOLD = ["#FFEFA0", "#F7C93C", "#C98A1E", "#6E4312"]
DIAMOND = ["#D8FFFA", "#6FE6DC", "#2FA6B4", "#14556A"]
NETHERITE = ["#7A6A6C", "#4E4244", "#322A2D", "#17121A"]
EMBER = ["#FFC25A", "#E8702A"]
COOKIE = ["#E0A25A", "#B9783A", "#8A5326", "#4A2B17"]
GREEN = ["#B8F07A", "#5EBB4A", "#2F7A3A", "#173F26"]
SKIN = ["#F7D9B0", "#E3AE7C", "#BC7F58", "#6B4030"]
CUFF = {1: ["#F2B0A0", "#D8584A", "#9E3030", "#531A22"],
        2: ["#F6D590", "#E8A83A", "#B06E1E", "#5E3612"],
        3: ["#E7B8FF", "#B060E0", "#7A3AA6", "#3C1B5A"]}


def line_mask(x0, y0, x1, y1, width):
    ys, xs = grid(S)
    dx, dy = x1 - x0, y1 - y0
    t = np.clip(((xs - x0) * dx + (ys - y0) * dy) / (dx * dx + dy * dy), 0, 1)
    return np.hypot(xs - (x0 + t * dx), ys - (y0 + t * dy)) <= width / 2 + 0.05


def dilate(m):
    out = m.copy()
    out[1:] |= m[:-1]
    out[:-1] |= m[1:]
    out[:, 1:] |= m[:, :-1]
    out[:, :-1] |= m[:, 1:]
    return out


def part(c, mask, ramp, thresholds=(0.80, 0.55), round_px=2.0, outline=True):
    """Outline ring (lit side one step lighter) then a height-shaded fill."""
    if outline:
        ring = dilate(mask) & ~mask & ~c.alpha()
        cx, cy = np.mean(np.where(mask)[1]) + .5, np.mean(np.where(mask)[0]) + .5
        c.paint(ring & lit_side(S, cx, cy), ramp[2])
        c.paint(ring & ~lit_side(S, cx, cy), ramp[3])
    c.paint_ramp(shade(mask, list(thresholds), round_px=round_px), ramp[:3])


def finish(c, keep=()):
    return clean_orphans(c.image(), {hex_rgba(k) for k in keep})


# --- tools -------------------------------------------------------------------------------------
def spatula():
    c = Canvas(S)
    part(c, line_mask(9, 23, 24, 8, 3.2), WOOD, round_px=1.6)               # handle
    ys, xs = grid(S)
    blade = (np.abs((xs - 22.5) + (ys - 9.5)) <= 5.2) & (np.abs((xs - 22.5) - (ys - 9.5)) <= 6.6)
    part(c, blade, WOOD, thresholds=(0.82, 0.6), round_px=2.4)
    for (x0, y0, x1, y1) in ((20, 12, 25, 7), (21, 13, 26, 8)):              # two slots in the blade
        c.paint(line_mask(x0, y0, x1, y1, 1.0) & blade, WOOD[3])
    c.paint(line_mask(6, 26, 9, 23, 2.6), WOOD[2])                           # handle end
    return finish(c, WOOD)


def rolling_pin(ramp, grip):
    c = Canvas(S)
    for (x0, y0, x1, y1) in ((5, 27, 10, 22), (22, 10, 27, 5)):              # handles
        part(c, line_mask(x0, y0, x1, y1, 3.4), grip, round_px=1.5)
    part(c, line_mask(10, 22, 22, 10, 8.4), ramp, thresholds=(0.80, 0.5), round_px=3.0)
    for (x0, y0, x1, y1) in ((14, 18, 16, 20), (18, 13, 20, 15)):            # surface cracks / seams
        c.paint(line_mask(x0, y0, x1, y1, 1.0) & c.alpha(), ramp[2])
    return finish(c, ramp + grip)


# --- hands -------------------------------------------------------------------------------------
def hand(ramp, cuff, up, detail=None):
    """Mitten-style hand. `up` = heights (rows of finger above the palm) for 4 fingers, left to right."""
    c = Canvas(S)
    ys, xs = grid(S)
    # cuff
    cf = (ys >= 25) & (ys <= 30) & (xs >= 8) & (xs <= 24)
    part(c, cf, cuff, thresholds=(0.8, 0.5), round_px=1.6)
    # palm + thumb
    palm = (ys >= 14) & (ys <= 25) & (xs >= 8) & (xs <= 24)
    palm |= ellipse(S, 16, 19, 9.5, 8)
    thumb = line_mask(8, 21, 4, 15, 4.0)
    part(c, thumb, ramp, round_px=1.8)
    part(c, palm & (ys <= 26), ramp, thresholds=(0.86, 0.58), round_px=3.5)
    # fingers
    for i, h in enumerate(up):
        x = 10.5 + i * 3.7
        top = 14 - h
        f = (np.abs(xs - x) <= 1.5) & (ys >= top) & (ys <= 16) & ~(np.abs(xs - x) > 0.9 + 0.6 * (ys - top) / 1.5)
        f = ((np.abs(xs - x) <= 1.6) & (ys >= top + 1) & (ys <= 17)) | ellipse(S, x, top + 1.6, 1.6, 1.8)
        part(c, f, ramp, thresholds=(0.84, 0.55), round_px=1.4)
    if detail:
        detail(c)
    return c


def golden_glove():
    c = hand(GOLD, ["#F2F2F0", "#D4D4D8", "#9A9AA6", "#4A4A58"], [8, 10, 9, 7])
    return finish(c, GOLD)


def diamond_fingers():
    def gems(c):
        for x, y in ((12, 20), (18, 21), (15, 17)):
            m = np.zeros((S, S), bool)
            m[y - 1:y + 1, x - 1:x + 1] = True
            c.paint(m, DIAMOND[0])
            c.px[y, x] = hex_rgba(DIAMOND[2])
    c = hand(DIAMOND, ["#F2F2F0", "#D4D4D8", "#9A9AA6", "#4A4A58"], [8, 10, 9, 7], gems)
    return finish(c, DIAMOND)


def netherite_hands():
    def cracks(c):
        for (x0, y0, x1, y1) in ((13, 19, 16, 22), (19, 17, 20, 21)):
            c.paint(line_mask(x0, y0, x1, y1, 1.0) & c.alpha(), EMBER[1])
        c.px[20, 15] = hex_rgba(EMBER[0])
        c.px[19, 20] = hex_rgba(EMBER[0])
    c = hand(NETHERITE, GOLD, [8, 10, 9, 7], cracks)
    return finish(c, NETHERITE + EMBER)


def handy(n):
    # n fingers raised (the rest curled short): I = 1%, II = 3%, III = 5%
    up = [9 if i >= 4 - n else 2 for i in range(4)]   # the n right-most fingers stand, equal height
    c = hand(SKIN, CUFF[n], up)
    return finish(c, SKIN + CUFF[n])


# --- baker's strength: a cookie with a green level-up arrow -------------------------------------
def bakers_strength():
    c = Canvas(S)
    ck = ellipse(S, 16, 21, 12.5, 9.5)
    part(c, ck, COOKIE, thresholds=(0.82, 0.58), round_px=4.0)
    for x, y in ((8, 20), (19, 26), (24, 20), (13, 27)):   # chocolate chips
        c.paint(ellipse(S, x + .5, y + .5, 1.3, 1.2), COOKIE[3])
    arrow = np.zeros((S, S), bool)
    from pixel import polygon
    arrow |= polygon(S, [(16, 1), (25, 10), (19.5, 10), (19.5, 18), (12.5, 18), (12.5, 10), (7, 10)])
    part(c, arrow, GREEN, thresholds=(0.84, 0.6), round_px=2.2)
    return finish(c, COOKIE + GREEN)


ICONS = {
    "bakers_strength": bakers_strength,
    "wooden_spatula": spatula,
    "stone_rolling_pin": lambda: rolling_pin(STONE, WOOD),
    "iron_rolling_pin": lambda: rolling_pin(IRON, WOOD),
    "golden_glove": golden_glove,
    "diamond_fingers": diamond_fingers,
    "netherite_hands": netherite_hands,
    "handy_fingers_1": lambda: handy(1),
    "handy_fingers_2": lambda: handy(2),
    "handy_fingers_3": lambda: handy(3),
}


def main() -> None:
    for d in ("textures/item/upgrade", "models/item/upgrade", "items/upgrade"):
        (ASSETS / d).mkdir(parents=True, exist_ok=True)
    for name, fn in ICONS.items():
        img = fn()
        img.save(ASSETS / f"textures/item/upgrade/{name}.png")
        (ASSETS / f"models/item/upgrade/{name}.json").write_text(json.dumps(
            {"parent": "minecraft:item/generated", "textures": {"layer0": f"cookieclicker:item/upgrade/{name}"}},
            indent=2) + "\n")
        (ASSETS / f"items/upgrade/{name}.json").write_text(json.dumps(
            {"model": {"type": "minecraft:model", "model": f"cookieclicker:item/upgrade/{name}"}}, indent=2) + "\n")
        n = len({p for p in img.get_flattened_data() if p[3] > 0})
        print(f"wrote {name} ({n} colours)")


if __name__ == "__main__":
    main()
