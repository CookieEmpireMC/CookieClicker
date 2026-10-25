"""Textured preview sets for the house/decor generators (real 26.3 block textures via tex_render).

Without the client jar (block_models.available() is False) every function returns quietly: previews are optional,
the YAML never depends on them. Output PNGs land in design/scripts/previews/.
"""
from __future__ import annotations

import math
from pathlib import Path

import numpy as np

import block_models as bm
import blocks as B
import tex_render as tr

GRASS = "minecraft:grass_block[snowy=false]"
DIRT = "minecraft:dirt"


def look_at(eye, target):
    d = np.asarray(target, float) - np.asarray(eye, float)
    yaw = math.degrees(math.atan2(-d[0], d[2]))
    pitch = math.degrees(math.atan2(-d[1], math.hypot(d[0], d[2])))
    return yaw, pitch


def ground_world(x0: int, z0: int, x1: int, z1: int) -> dict:
    w = {}
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            w[(x, -1, z)] = GRASS
            w[(x, -2, z)] = DIRT
    return w


def render_world(world: dict, eye, target, size=(1280, 720), fov=70, ss=2, ground_y=-1.0, fog=0.004):
    sc = tr.Scene()
    sc.add_blocks({p: d for p, d in world.items()})
    yaw, pitch = look_at(eye, target)
    return tr.render(sc, tr.persp(eye, yaw, pitch, fov, size), ss=ss, ground=ground_y, fog=fog)


def render_iso(world: dict, yaw: float, pitch: float, width: int = 1400, ss=2):
    sc = tr.Scene()
    sc.add_blocks(world)
    return tr.render(sc, tr.fit_ortho(sc, yaw, pitch, width, margin=0.5), ss=ss)


def view_set(world: dict, shots: dict, out: Path, prefix: str, size=(1280, 720), sheet=True) -> list:
    """shots: name -> (eye, target) perspective, or name -> ('iso', yaw, pitch)."""
    if not bm.available():
        print("  (client jar not found: textured previews skipped; set BAKERY_CLIENT_JAR)")
        return []
    imgs = []
    for name, spec in shots.items():
        if spec[0] == "iso":
            im = render_iso(world, spec[1], spec[2])
        else:
            im = render_world(world, spec[0], spec[1], size)
        im.save(out / f"{prefix}_{name}.png")
        imgs.append(im)
    return imgs


def design_world(g: dict, pad: int = 10, box=None) -> dict:
    """Design grid (resolved block data, y may be -1) on a grass lawn that continues `pad` blocks around."""
    xs = [p[0] for p in g]
    zs = [p[2] for p in g]
    x0, x1 = (box[0], box[2]) if box else (min(xs), max(xs))
    z0, z1 = (box[1], box[3]) if box else (min(zs), max(zs))
    w = ground_world(x0 - pad, z0 - pad, x1 + pad, z1 + pad)
    for p, d in g.items():
        if B.block_id(d) == "air":
            w.pop(p, None) if p[1] >= 0 else None
            continue
        w[p] = d
    return w


def house_shots(cx: float, cz: float, w: int, d: int, h: float, front_z: float | None = None) -> dict:
    """Standard 4 views for a building whose body spans x cx±w/2, z cz±d/2 (front = north)."""
    fz = cz - d / 2 if front_z is None else front_z
    mid = (cx, h * 0.4, cz)
    r = max(w, d) * 0.62 + 4
    return {
        "front": ((cx, 3.0, fz - r * 0.75), (cx, h * 0.45, cz)),
        "ne": ((cx + r * 0.85, h * 0.85, fz - r * 0.55), mid),
        "nw": ((cx - r * 0.85, h * 0.85, fz - r * 0.55), mid),
        "back": ((cx + r * 0.9, h * 0.7, cz + d / 2 + r * 0.55), mid),
        "above": ("iso", 215, 52),
    }


def factory_previews(g: dict, out: Path) -> None:
    if not bm.available():
        print("  (client jar not found: textured previews skipped)")
        return
    world = design_world(g, pad=12, box=(0, 0, 18, 13))
    shots = house_shots(9.5, 6, 19, 13, 14)
    imgs = view_set(world, shots, out, "house_factory", size=(1100, 680))
    tr.contact_sheet(imgs[:4], cols=2).save(out / "house_factory_sheet.png")


def default_previews(g: dict, out: Path) -> None:
    if not bm.available():
        print("  (client jar not found: textured previews skipped)")
        return
    world = design_world(g, pad=10, box=(0, 0, 16, 14))
    mid = (8.5, 3.5, 9)
    shots = {"front": ((8.5, 3.0, -7.0), mid), "ne": ((23, 9, -4), mid), "nw": ((-7, 9, -4), mid),
             "back": ((24, 8, 24), mid), "above": ("iso", 215, 52)}
    imgs = view_set(world, shots, out, "house_default", size=(1100, 680))
    tr.contact_sheet(imgs[:4], cols=2).save(out / "house_default_sheet.png")
