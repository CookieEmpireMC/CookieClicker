"""The factory plot layout (the `layout:` section of houses/factory.yml) and its checks. Not a generator: imported by
house_factory.py, decor_style.py and the preview scripts. Spec: design/factory.md ("Plot layout").

Plot cells are relative to the plot's min corner: x west -> east, z north -> south, y 0 = first air layer above the
ground. The fence runs on the outer ring of plot-size. Design cell d lands on HOUSE_AT + d.
"""
from __future__ import annotations

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent / "lib"))
sys.path.insert(0, str(Path(__file__).resolve().parent))
import blocks as B  # noqa: E402
import belt_style  # noqa: E402
from voxel_render import project  # noqa: E402

SX, SY, SZ = 19, 17, 14                              # design box above the ground
SIZE = (SX, SY, SZ)
PLOT_X, PLOT_Z = 31, 30
HOUSE_AT = (6, 0, 10)
MARGIN = 5                                           # free blocks between the design box and the fence
BELT_LEN = 5
POINTS = {
    "smoke": [[4, 16, 5]],                           # the single oven stack (set by house_factory.py)
    "steam": [[10, 13, 6]],                          # flush ridge louvre (sound anchor)
    "belt_out": [12, 0, 1],                          # first belt cell outside the loading hatch; belt runs north (-Z)
    "door": [8, 1, 2],                               # lower half of the front door
}


def to_plot(p):
    return (p[0] + HOUSE_AT[0], p[1] + HOUSE_AT[1], p[2] + HOUSE_AT[2])


def _derive():
    global BELT_FROM, BELT_TO, PEDESTAL, SHOP, CLICKS, BELT
    BELT_FROM = to_plot(POINTS["belt_out"])
    BELT_TO = (BELT_FROM[0], BELT_FROM[1], BELT_FROM[2] - (BELT_LEN - 1))
    PEDESTAL = (BELT_TO[0], 0, BELT_TO[2] - 1)
    SHOP = (PEDESTAL[0] + 2, 0, PEDESTAL[2])
    CLICKS = (PEDESTAL[0] - 2, 0, PEDESTAL[2])
    BELT = [(BELT_FROM[0], z) for z in range(BELT_FROM[2], BELT_TO[2] - 1, -1)]


_derive()
VIEWPOINT = (15, 0, 2)
CAMERA = (23, 12, -13)
CAMERA_YAW, CAMERA_PITCH = 20, 24
CAMERA_FOV, CAMERA_ASPECT, CAMERA_MARGIN = 70, 4 / 3, 0.95
EYE_HEIGHT = 1.62
ENTRANCE = (15, 0)
LAYOUT = {
    "plot-size": [PLOT_X, PLOT_Z],
    "house-at": list(HOUSE_AT),
    "pedestal": {"at": list(PEDESTAL), "yaw": 180},
    "shop": {"at": list(SHOP), "yaw": 180},
    "clicks": {"at": list(CLICKS), "yaw": 180},
    "viewpoint": {"at": list(VIEWPOINT), "yaw": 0, "pitch": -14},
    "entrance": list(ENTRANCE),
    "belt": {"from": list(BELT_FROM), "to": list(BELT_TO)},
    "camera": {"at": list(CAMERA), "yaw": CAMERA_YAW, "pitch": CAMERA_PITCH},
}


def layout_yaml() -> list[str]:
    v = lambda p: "[" + ", ".join(str(i) for i in p) + "]"  # noqa: E731
    L = LAYOUT
    return [
        "layout:",
        f"  plot-size: {v(L['plot-size'])}",
        f"  house-at: {v(L['house-at'])}",
        f"  pedestal: {{at: {v(L['pedestal']['at'])}, yaw: {L['pedestal']['yaw']}}}",
        f"  shop: {{at: {v(L['shop']['at'])}, yaw: {L['shop']['yaw']}}}",
        f"  clicks: {{at: {v(L['clicks']['at'])}, yaw: {L['clicks']['yaw']}}}",
        f"  viewpoint: {{at: {v(L['viewpoint']['at'])}, yaw: {L['viewpoint']['yaw']}, "
        f"pitch: {L['viewpoint']['pitch']}}}",
        f"  entrance: {v(L['entrance'])}",
        f"  belt: {{from: {v(L['belt']['from'])}, to: {v(L['belt']['to'])}}}",
        f"  camera: {{at: {v(L['camera']['at'])}, yaw: {L['camera']['yaw']}, pitch: {L['camera']['pitch']}}}",
    ]


def camera_eye():
    return (CAMERA[0] + 0.5, CAMERA[1] + EYE_HEIGHT, CAMERA[2] + 0.5)


def framing_points():
    pts = [(x, y, z) for x in (0, PLOT_X) for z in (0, PLOT_Z) for y in (0, 2.5)]
    pts += [(ENTRANCE[0] + dx, 5, 0) for dx in (-2, 3)]
    pts += [(x, y, z) for x in (HOUSE_AT[0], HOUSE_AT[0] + SX) for z in (HOUSE_AT[2], HOUSE_AT[2] + SZ)
            for y in (0, SY)]
    pts += [(PEDESTAL[0] + .5, 2.5, PEDESTAL[2] + .5)]
    return pts


def validate_layout(g: dict) -> None:
    """g: {(x, y, z): block data} design cells (y >= 0 used). Same checks as before the rework."""
    solid3 = {to_plot(p) for p, d in g.items() if p[1] >= 0 and B.block_id(d) != "air"}

    class Occupied:
        def __init__(self, levels):
            self.levels = levels

        def __contains__(self, p):
            return any((p[0], y, p[1]) in solid3 for y in self.levels)

    occupied = Occupied((0, 1))
    bx0, bz0, bx1, bz1 = HOUSE_AT[0], HOUSE_AT[2], HOUSE_AT[0] + SX - 1, HOUSE_AT[2] + SZ - 1

    inner = lambda x, z: 1 <= x <= PLOT_X - 2 and 1 <= z <= PLOT_Z - 2  # noqa: E731
    in_box = lambda x, z: bx0 <= x <= bx1 and bz0 <= z <= bz1  # noqa: E731
    assert HOUSE_AT[1] == 0
    assert bx0 - MARGIN >= 1 and bx1 + MARGIN <= PLOT_X - 2 and bz1 + MARGIN <= PLOT_Z - 2, "margin to fence"
    assert len(BELT) >= 4 and BELT[0] == (BELT_FROM[0], BELT_FROM[2])
    for x, z in BELT:
        assert inner(x, z) and (x, z) not in Occupied((0,)), f"belt cell {(x, z)} is not free"
    assert (PEDESTAL[0], PEDESTAL[2] + 1) == (BELT_TO[0], BELT_TO[2]), "belt must end next to the pedestal"
    for name, (x, y, z) in (("pedestal", PEDESTAL), ("shop", SHOP), ("clicks", CLICKS), ("viewpoint", VIEWPOINT)):
        assert y == 0 and inner(x, z) and (x, z) not in occupied and (x, z) not in BELT, f"{name} not free"
        assert not in_box(x, z), f"{name} must be outside the house footprint"
    assert PEDESTAL[2] - 1 >= MARGIN, "room in front of the pedestal"
    ex, ez = ENTRANCE
    assert ez == 0 and 2 <= ex <= PLOT_X - 3, "entrance: front ring, gate fully between the corners"
    for gx in (ex - 1, ex, ex + 1):
        assert (gx, 1) not in occupied
    bx, by, bz = POINTS["belt_out"]
    assert B.block_id(g.get((bx, by, bz + 1), "minecraft:air")) == "air", "hatch behind belt_out is open"
    for x, z in BELT:
        y = belt_style.STYLE["surface-height"]
        if (x, y, z) in solid3:
            d = (x - HOUSE_AT[0], y, z - HOUSE_AT[2])
            b, pr = B.block_id(g[d]), B.props_of(g[d])
            assert b.endswith("_trapdoor") and pr["half"] == "top" and pr["open"] == "false", f"belt clearance {d}"
    cx, cy, cz = CAMERA
    assert cy >= 1 and not in_box(cx, cz), "camera: raised and outside the design box"
    assert (cx, cy, cz) not in solid3 and (cx, cy + 1, cz) not in solid3
    sx, sy, depth = project(framing_points(), camera_eye(), CAMERA_YAW, CAMERA_PITCH, CAMERA_FOV, CAMERA_ASPECT)
    assert (depth > 1).all() and abs(sx).max() <= CAMERA_MARGIN and abs(sy).max() <= CAMERA_MARGIN, \
        f"camera framing: x {sx.min():.2f}..{sx.max():.2f}, y {sy.min():.2f}..{sy.max():.2f}"
