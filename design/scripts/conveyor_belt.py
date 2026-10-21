"""The conveyor belt surface: item model `cookieclicker:conveyor_belt` with an animated rubber belt.

Outputs (final locations):
  resourcepack/assets/cookieclicker/items/conveyor_belt.json                 (item model definition)
  resourcepack/assets/cookieclicker/models/item/conveyor_belt.json           (3 cuboids: rubber + 2 copper rails)
  resourcepack/assets/cookieclicker/textures/item/conveyor_belt.png          (16x128: 8 animation frames)
  resourcepack/assets/cookieclicker/textures/item/conveyor_belt.png.mcmeta   (frametime 2, no interpolation)
  resourcepack/assets/cookieclicker/textures/item/conveyor_belt_frame.png    (static: copper rails, rubber edge)
  design/scripts/previews/conveyor_belt_*.png                                (x8 frames, model renders)
Run with `mise run assets`. Deterministic. Spec: design/conveyor-belt.md

Motion: on an `up` face the texture's top row (v = 0) lies on the north (-Z) edge. Every frame rolls the
slat pattern one texel towards v = 0, so the slats travel north, towards the pedestal, at 1 texel per
2 ticks = 10 texels/s = 0.625 blocks/s. The pattern repeats every 8 texels, so 8 frames make a seamless loop.
The model's bottom sits at its origin (model y 8), so an item display with transform NONE stands right
on the frame when spawned at ground + surface-height.
"""
from __future__ import annotations

import json
import sys
from pathlib import Path

import numpy as np
from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(Path(__file__).resolve().parent / "lib"))
import model_render  # noqa: E402
from preview import upscale  # noqa: E402

NS = ROOT / "resourcepack/assets/cookieclicker"
ITEM_DEF = NS / "items/conveyor_belt.json"
MODEL = NS / "models/item/conveyor_belt.json"
TEX = NS / "textures/item/conveyor_belt.png"
FRAME_TEX = NS / "textures/item/conveyor_belt_frame.png"
PREVIEWS = ROOT / "design/scripts/previews"

PERIOD = 8           # texels between slats; also the number of frames
FRAMETIME = 2        # ticks per frame

H = lambda s: tuple(int(s[i:i + 2], 16) for i in (1, 3, 5)) + (255,)  # noqa: E731
# rubber: purple-grey ramp, highlights warm up, shadows go purple
SHADOW, BASE, WEAR, SLAT, SLAT_HI, EDGE = (H("#1B1720"), H("#2C2832"), H("#35303B"), H("#4A4450"),
                                           H("#6B645F"), H("#221E27"))
# waxed copper (same family as the factory awning and lintel)
CU_HI, CU_LIGHT, CU_BASE, CU_SHADOW, CU_DARK = (H("#F0B58A"), H("#D2805C"), H("#B5603F"), H("#8A4232"),
                                                H("#5A2A2C"))
CLEAR = (0, 0, 0, 0)


def belt_period() -> np.ndarray:
    """One 16 x 8 repeat of the belt: a raised slat (lit north edge, contact shadow south) and worn rubber."""
    t = np.zeros((PERIOD, 16, 4), np.uint8)
    t[:] = BASE
    t[0, :] = SLAT_HI
    t[1, :] = SLAT
    t[2, :] = SHADOW
    for x, y in ((3, 4), (4, 4), (5, 4), (9, 6), (10, 6)):                     # scuffs along the slats
        t[y, x] = WEAR
    t[:, 0] = CLEAR                                     # columns 0 and 15 lie under the rails (unused)
    t[:, 15] = CLEAR
    t[:, 1] = np.where(np.arange(PERIOD)[:, None] < 2, SLAT, EDGE)        # worn belt edge
    t[:, 14] = np.where(np.arange(PERIOD)[:, None] < 2, SLAT, EDGE)
    t[0, 1] = t[0, 14] = SLAT                           # the slat highlight stops short of the edges
    return t


def belt_strip() -> Image.Image:
    tile = np.concatenate([belt_period()] * (16 // PERIOD), axis=0)       # one 16 x 16 frame
    frames = [np.roll(tile, -k, axis=0) for k in range(PERIOD)]         # roll towards v = 0 = north
    return Image.fromarray(np.concatenate(frames, axis=0), "RGBA")


def frame_texture() -> Image.Image:
    """Static 16 x 16: row 0 rail top, row 1 rail side with rivets, rows 2-3 rail end/bottom, rows 4-5 rubber edge."""
    t = np.zeros((16, 16, 4), np.uint8)
    t[0, :] = CU_LIGHT
    t[0, 2:5] = CU_HI                                   # lit glints along the top of the rail
    t[0, 9:11] = CU_HI
    t[1, :] = CU_BASE
    for x in range(1, 16, 4):
        t[1, x] = CU_DARK                               # rivet
        t[0, x] = CU_LIGHT
    t[2, :] = CU_BASE
    t[3, :] = CU_SHADOW
    t[4, :] = SLAT
    t[5, :] = SHADOW
    t[6:, :] = CU_DARK                                  # filler (not mapped)
    return Image.fromarray(t, "RGBA")


def model() -> dict:
    frame = "cookieclicker:item/conveyor_belt_frame"

    def rail(x1, x2):
        return {
            "from": [x1, 8, 0], "to": [x2, 10, 16],
            "faces": {
                "up": {"uv": [0, 0, 16, 1], "rotation": 90, "texture": "#frame"},
                "east": {"uv": [0, 0, 16, 2], "texture": "#frame"},
                "west": {"uv": [0, 0, 16, 2], "texture": "#frame"},
                "north": {"uv": [0, 2, 1, 4], "texture": "#frame"},
                "south": {"uv": [0, 2, 1, 4], "texture": "#frame"},
                "down": {"uv": [0, 3, 16, 4], "rotation": 90, "texture": "#frame"},
            },
        }

    return {
        "textures": {"belt": "cookieclicker:item/conveyor_belt", "frame": frame, "particle": frame},
        "elements": [
            {"from": [1, 8, 0], "to": [15, 9, 16],
             "faces": {
                 "up": {"uv": [1, 0, 15, 16], "texture": "#belt"},
                 "north": {"uv": [1, 4, 15, 5], "texture": "#frame"},
                 "south": {"uv": [1, 4, 15, 5], "texture": "#frame"},
                 "down": {"uv": [1, 5, 15, 6], "texture": "#frame"},
             }},
            rail(0, 1),
            rail(15, 16),
        ],
        "display": {
            "gui": {"rotation": [30, 225, 0], "translation": [0, -2, 0], "scale": [0.625, 0.625, 0.625]},
            "ground": {"rotation": [0, 0, 0], "translation": [0, 1, 0], "scale": [0.25, 0.25, 0.25]},
            "fixed": {"rotation": [-90, 0, 0], "translation": [0, 0, -4], "scale": [0.5, 0.5, 0.5]},
            "head": {"rotation": [0, 0, 0], "translation": [0, 6, 0], "scale": [1, 1, 1]},
            "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 1, 0], "scale": [0.375] * 3},
            "thirdperson_lefthand": {"rotation": [75, 45, 0], "translation": [0, 1, 0], "scale": [0.375] * 3},
            "firstperson_righthand": {"rotation": [0, 45, 0], "translation": [0, 0, 0], "scale": [0.4] * 3},
            "firstperson_lefthand": {"rotation": [0, 225, 0], "translation": [0, 0, 0], "scale": [0.4] * 3},
        },
    }


def write_json(path: Path, data: dict) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8")


def main() -> int:
    strip = belt_strip()
    assert strip.size == (16, 16 * PERIOD)
    TEX.parent.mkdir(parents=True, exist_ok=True)
    strip.save(TEX)
    write_json(TEX.with_suffix(".png.mcmeta"), {"animation": {"frametime": FRAMETIME, "interpolate": False}})
    frame_texture().save(FRAME_TEX)
    write_json(MODEL, model())
    write_json(ITEM_DEF, {"model": {"type": "minecraft:model", "model": "cookieclicker:item/conveyor_belt"}})

    PREVIEWS.mkdir(parents=True, exist_ok=True)
    # all 8 frames side by side at x8, then the frame texture
    sheet = Image.new("RGBA", (16 * 8 * PERIOD + 8 * (PERIOD - 1) + 16 * 8 + 24, 16 * 8), (58, 64, 76, 255))
    for k in range(PERIOD):
        sheet.alpha_composite(upscale(strip.crop((0, 16 * k, 16, 16 * k + 16)), 8), (k * (128 + 8), 0))
    sheet.alpha_composite(upscale(frame_texture(), 8), (PERIOD * 136 + 16, 0))
    sheet.save(PREVIEWS / "conveyor_belt_frames.png")
    views = [model_render.render(MODEL, yaw=y, pitch=p, px_per_unit=16, assets=ROOT / "resourcepack/assets")
             for y, p in ((30, 35), (150, 35), (0, 10), (90, 10))]
    w = sum(v.width for v in views) + 12 * (len(views) + 1)
    out = Image.new("RGBA", (w, max(v.height for v in views) + 24), (58, 64, 76, 255))
    x = 12
    for v in views:
        out.alpha_composite(v, (x, 12))
        x += v.width + 12
    out.save(PREVIEWS / "conveyor_belt_model.png")
    print(f"{TEX.relative_to(ROOT)} ({PERIOD} frames), {MODEL.relative_to(ROOT)}, {ITEM_DEF.relative_to(ROOT)}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
