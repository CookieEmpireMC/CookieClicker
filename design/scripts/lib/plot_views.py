"""Textured views of the WHOLE plot (house + decor + fence + belt), in plot coordinates. Optional: needs the client jar.

The house comes from src/main/resources/houses/<design>.yml as generated; the fence from fence_style.ring();
pedestal and panels are drawn as simple stand-ins (they are display entities in the game).
"""
from __future__ import annotations

from pathlib import Path

import yaml

import block_models as bm
import blocks as B
import design_views as dv
import tex_render as tr

ROOT = Path(__file__).resolve().parents[3]
PEDESTAL_STAND_INS = {(18, 0, 6): "minecraft:chiseled_stone_bricks",
                      (18, 1, 6): "minecraft:smooth_stone_slab[type=bottom,waterlogged=false]"}


def load_design(name: str) -> tuple[dict, dict]:
    f = yaml.safe_load((ROOT / f"src/main/resources/houses/{name}.yml").read_text(encoding="utf-8"))
    pal = f["palette"]
    gd = f.get("ground-depth", 0)
    g = {}
    for y, layer in enumerate(f["layers"]):
        for z, row in enumerate(layer):
            for x, c in enumerate(row):
                if c != ".":
                    g[(x, y - gd, z)] = pal[c]
    return g, f


def plot_world(house_g: dict, decor_g: dict, fence_cells: dict, belt_cells=(), house_at=(6, 0, 10)) -> dict:
    w = dv.ground_world(-10, -10, 40, 40)
    hx, hy, hz = house_at
    for p, d in house_g.items():
        q = (p[0] + hx, p[1] + hy, p[2] + hz)
        if B.block_id(d) == "air":
            if q[1] >= 0:
                w.pop(q, None)
            continue
        w[q] = d
    w.update(decor_g)
    w.update(fence_cells)
    for (x, z) in belt_cells:
        w[(x, 0, z)] = "minecraft:spruce_log[axis=x]"
    w.update(PEDESTAL_STAND_INS)
    return w


SHOTS = {
    "eye": ((15.5, 2.62, 1.5), (15.5, 3.2, 12)),
    "ne": ((38, 17, -9), (15, 1, 14)),
    "nw": ((-9, 17, -9), (15, 1, 14)),
    "se": ((40, 19, 38), (15, 1, 14)),
    "sw": ((-9, 19, 38), (15, 1, 14)),
}


def plot_previews(decor_g: dict, out: Path, design: str = "factory") -> None:
    if not bm.available():
        print("  (client jar not found: textured previews skipped)")
        return
    import factory_layout as FL
    import fence_style
    hg, _ = load_design(design)
    fence = fence_style.ring(fence_style.STYLE, FL.PLOT_X, FL.PLOT_Z, FL.ENTRANCE[0])
    world = plot_world(hg, decor_g, fence, belt_cells=FL.BELT)
    imgs = []
    for name, (eye, target) in SHOTS.items():
        im = dv.render_world(world, eye, target, (1280, 720), 70, fog=0.0025)
        im.save(out / f"decor_{name}.png")
        imgs.append(im)
    tr.contact_sheet(imgs[1:5], cols=2).save(out / "decor_sheet.png")
    # the build cinematic's view from layout.camera (16:9, FOV 70) with the 4:3 crop marked (design/conveyor-belt.md)
    from PIL import ImageDraw
    sc = tr.Scene()
    sc.add_blocks(world)
    im = tr.render(sc, tr.persp(FL.camera_eye(), FL.CAMERA_YAW, FL.CAMERA_PITCH, FL.CAMERA_FOV, (1280, 720)),
                   ss=2, ground=-1.0, fog=0.0025)
    d = ImageDraw.Draw(im)
    x0 = (1280 - int(720 * 4 / 3)) // 2
    d.rectangle([x0, 0, x0 + int(720 * 4 / 3) - 1, 719], outline=(255, 255, 255), width=2)
    im.save(out / "house_factory_camera.png")
