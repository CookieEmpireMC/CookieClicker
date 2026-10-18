"""Panel icon for the Villager Baker building: a friendly long-nosed baker in a puffy chef hat.

Output: resourcepack/assets/cookieclicker/textures/item/building/villager_baker.png (32x32)
Run with `mise run assets`. Deterministic. Spec: design/building-icons.md
"""
import sys
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parent / "lib"))
from pixel import Canvas, clean_orphans, edge_of, ellipse, hex_rgba, lit_side, rect, shade  # noqa: E402

OUT = Path("resourcepack/assets/cookieclicker/textures/item/building/villager_baker.png")
S = 32

HAT = ["#FFFBEF", "#ECE3D2", "#CDBDA8", "#94807A"]         # light -> outline (warm grey)
SKIN = ["#F4CDA0", "#DCA67A", "#B97A58", "#7C4A3B"]        # light -> outline
HAIR = ["#8A5A40", "#5E3A2C", "#3E2622"]                   # brows + moustache
EYE_WHITE, IRIS, IRIS_DARK = "#FFF6EA", "#4E8A3C", "#2F5A2C"
CHEEK = "#E08C74"


def build():
    c = Canvas(S)
    # --- head (tall, villager-ish) and nose
    head = rect(S, 9, 13, 22, 29)
    head[29, 9] = head[29, 22] = False  # round the chin corners
    c.paint(head, SKIN[1])
    c.paint(rect(S, 21, 15, 22, 29) & head, SKIN[2])
    # shadow under the hat band
    c.paint(rect(S, 9, 14, 22, 14) & head, SKIN[2])

    # brow (one bushy brow, lit top edge)
    brow = rect(S, 11, 16, 20, 17)
    c.paint(brow, HAIR[1])
    c.paint(rect(S, 11, 16, 15, 16), HAIR[0])
    # eyes: white + iris, looking slightly to the viewer's left
    c.paint(rect(S, 11, 18, 13, 19), EYE_WHITE)
    c.paint(rect(S, 12, 18, 13, 19), IRIS)
    c.paint(rect(S, 13, 19, 13, 19), IRIS_DARK)
    c.paint(rect(S, 18, 18, 20, 19), EYE_WHITE)
    c.paint(rect(S, 18, 18, 19, 19), IRIS)
    c.paint(rect(S, 19, 19, 19, 19), IRIS_DARK)
    # rosy cheeks
    c.paint(rect(S, 10, 22, 11, 22), CHEEK)
    c.paint(rect(S, 20, 22, 21, 22), CHEEK)

    # big nose: lit left column, base middle, shadow on the right and underneath
    c.paint(rect(S, 14, 18, 17, 25), SKIN[1])
    c.paint(rect(S, 14, 18, 14, 24), SKIN[0])
    c.paint(rect(S, 17, 19, 17, 25), SKIN[2])
    c.paint(rect(S, 18, 20, 18, 24), SKIN[2])   # cast shadow on the cheek
    c.paint(rect(S, 15, 25, 16, 25), SKIN[2])   # underside of the tip

    # handlebar moustache under the nose, curled up at the ends
    for x0, x1, tip in ((10, 14, 9), (17, 21, 22)):
        c.paint(rect(S, x0, 26, x1, 27), HAIR[1])
        c.paint(rect(S, x0, 26, x1, 26), HAIR[0])
        c.paint(rect(S, tip, 25, tip, 26), HAIR[1])
    c.paint(rect(S, 15, 26, 16, 26), HAIR[1])
    # small smile under the moustache
    c.paint(rect(S, 15, 28, 16, 28), SKIN[2])

    # --- chef hat: band + three puffs
    band = rect(S, 9, 10, 22, 13)
    c.paint(band, HAT[1])
    c.paint(rect(S, 9, 11, 9, 13), HAT[0])    # lit left end
    c.paint(rect(S, 21, 11, 22, 13), HAT[2])  # shaded right end
    c.paint(rect(S, 9, 10, 22, 10), HAT[2])   # shadow under the puffs
    lobes = [(10.5, 7.5, 5.0, 4.6), (16.0, 5.6, 6.0, 5.0), (21.5, 7.5, 5.0, 4.6)]
    puff = np.zeros((S, S), bool)
    extra = np.zeros((S, S))
    ys, xs = np.mgrid[0:S, 0:S] + 0.5
    for cx, cy, rx, ry in lobes:
        m = ellipse(S, cx, cy, rx, ry)
        puff |= m
        extra = np.maximum(extra, 4.0 * np.sqrt(np.clip(1 - ((xs - cx) / rx) ** 2 - ((ys - cy) / ry) ** 2, 0, 1)))
    puff &= ys < 11
    c.paint_ramp(shade(puff, [0.72, 0.42], round_px=2.0, extra_height=extra), HAT)

    # --- selective outline on the silhouette
    sil = c.alpha()
    edge = edge_of(sil)
    lit = lit_side(S, 16, 16)
    hat_part = puff | band
    for part, ramp in ((hat_part, HAT), (~hat_part, SKIN)):
        c.paint(edge & part & lit, ramp[2])
        c.paint(edge & part & ~lit, ramp[3])
    # crease between the hat lobes
    c.paint(rect(S, 13, 8, 13, 10) | rect(S, 18, 8, 18, 10), HAT[2])

    img = c.image()
    keep = {hex_rgba(x) for x in (EYE_WHITE, IRIS, IRIS_DARK, CHEEK, *HAIR)}
    return clean_orphans(img, keep)


def main() -> None:
    OUT.parent.mkdir(parents=True, exist_ok=True)
    img = build()
    img.save(OUT)
    colours = {p for p in img.get_flattened_data() if p[3] > 0}
    print(f"wrote {OUT} ({img.width}x{img.height}, {len(colours)} colours)")


if __name__ == "__main__":
    main()
