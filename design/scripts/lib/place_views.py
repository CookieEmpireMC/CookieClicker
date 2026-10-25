"""Textured preview sets for the countryside places (client jar optional)."""
from __future__ import annotations

from pathlib import Path

import block_models as bm
import blocks as B
import design_views as dv
import tex_render as tr


def place_world(g: dict, pad: int = 8, box=None) -> dict:
    """Design grid on a lawn that continues `pad` cells around; the design's ground layers replace the lawn."""
    xs = [p[0] for p in g]
    zs = [p[2] for p in g]
    x0, x1 = (box[0], box[2]) if box else (min(xs), max(xs))
    z0, z1 = (box[1], box[3]) if box else (min(zs), max(zs))
    w = dv.ground_world(x0 - pad, z0 - pad, x1 + pad, z1 + pad)
    for p, d in g.items():
        if B.block_id(d) == "air":
            w.pop(p, None)
            continue
        w[p] = d
    return w


def mill_previews(g: dict, out: Path) -> list:
    if not bm.available():
        print("  (client jar not found: textured previews skipped)")
        return []
    world = place_world(g, pad=6, box=(0, 0, 45, 35))
    shots = {
        "eye": ((16.5, 1.62, 5.5), (26, 4, 15)),
        "front": ((22, 6, -9), (24, 5, 16)),
        "ne": ((58, 18, -6), (24, 4, 16)),
        "nw": ((-12, 18, -6), (22, 4, 16)),
        "back": ((50, 14, 50), (24, 4, 16)),
        "slot": ((28.5, 3.0, 6.0), (28.5, 1.5, 16)),
        "water": ((36, 5, 3), (28, 1, 15)),
        "yard": ((8, 5, 0), (20, 2, 8)),
        "above": ("iso", 215, 52),
    }
    imgs = []
    for name, spec in shots.items():
        im = dv.render_iso(world, spec[1], spec[2]) if spec[0] == "iso" else \
            dv.render_world(world, spec[0], spec[1], (1280, 720), ground_y=-6.0)
        im.save(out / f"place_mill_{name}.png")
        imgs.append(im)
    if len(imgs) >= 4:
        tr.contact_sheet(imgs[:4], cols=2).save(out / "place_mill_sheet.png")
    return imgs
