"""Place files for the countryside (houses/<place>.yml): house format + `points:` with dict values (design/countryside.md).

    points = {"viewpoint": {"at": [x,y,z], "yaw": 0, "pitch": 12}, "yard": {"from": [x,z], "to": [x,z]},
              "hologram": [x,y,z], "panel": {"at": [x,y,z], "yaw": 180}, "ripe": [x,y,z],
              "smoke": [[x,y,z]]  (0-1 entries, optional), "wheel": {"at": [x,y,z], "axis": "z"}  (mill)}
check_points() asserts the contract; write_place_yaml() writes the file (same layout as house_lib.write_yaml).
"""
from __future__ import annotations

import math

import blocks as B
import house_lib as H

REQUIRED = ("viewpoint", "yard", "hologram", "panel", "ripe")
MAX_CLICK_DISTANCE = 5.0
BAD_IN_HITBOX = ("water", "leaves", "vine")
FLOWERS = ("poppy", "dandelion", "cornflower", "azure_bluet", "oxeye_daisy", "allium", "tulip", "lily_of_the_valley",
           "orchid", "rose_bush", "lilac", "peony", "sunflower", "potted_")


def _at(v):
    return v["at"] if isinstance(v, dict) else v


def centre(p) -> tuple:
    return (p[0] + 0.5, p[1] + 0.5, p[2] + 0.5)


def check_points(points: dict, g: dict, size: tuple, ground: int, *, wheel_required: bool = False) -> None:
    """g = {(x, y, z): block data} with design y. Raises AssertionError listing the first problem."""
    sx, sy, sz = size
    top = sy - ground
    for k in REQUIRED:
        assert k in points, f"point {k} is required"
    if wheel_required:
        assert "wheel" in points, "point wheel is required for the mill"
    inside = lambda p: 0 <= p[0] < sx and 0 <= p[1] < top and 0 <= p[2] < sz  # noqa: E731
    vp = points["viewpoint"]
    assert set(vp) == {"at", "yaw", "pitch"} and inside(vp["at"]), vp
    yd = points["yard"]
    assert set(yd) == {"from", "to"} and yd["from"][0] <= yd["to"][0] and yd["from"][1] <= yd["to"][1], yd
    for c in (yd["from"], yd["to"]):
        assert 0 <= c[0] < sx and 0 <= c[1] < sz, f"yard corner {c} outside the box"
    pn = points["panel"]
    assert set(pn) == {"at", "yaw"} and inside(pn["at"]), pn
    for k in ("hologram", "ripe"):
        assert len(points[k]) == 3 and inside(points[k]), (k, points[k])
    sm = points.get("smoke", [])
    assert len(sm) <= 1 and all(inside(p) for p in sm), "smoke: 0-1 entries inside the box"
    if "wheel" in points:
        w = points["wheel"]
        assert set(w) == {"at", "axis"} and w["axis"] in ("x", "z") and inside(w["at"]), w

    def bid(p):
        v = g.get(tuple(p))
        return "air" if v is None else B.block_id(v)

    v0 = centre(vp["at"])
    for k in ("hologram", "ripe"):
        p = points[k]
        d = math.dist(v0, centre(p))
        assert d <= MAX_CLICK_DISTANCE, f"{k} is {d:.1f} blocks from the viewpoint (max {MAX_CLICK_DISTANCE})"
    d = math.dist(v0, centre(pn["at"]))
    assert d <= MAX_CLICK_DISTANCE, f"panel is {d:.1f} blocks from the viewpoint (max {MAX_CLICK_DISTANCE})"
    for k, p in (("hologram", points["hologram"]), ("panel", pn["at"]), ("ripe", points["ripe"])):
        for dy in (0, 1):
            b = bid((p[0], p[1] + dy, p[2]))
            assert not any(w in b for w in BAD_IN_HITBOX) and not any(w in b for w in FLOWERS), \
                f"{k} hitbox contains {b} at {(p[0], p[1] + dy, p[2])}"
    for dy in (0, 1):                                  # the viewpoint is a standing place: feet and head free
        assert bid((vp["at"][0], vp["at"][1] + dy, vp["at"][2])) == "air", "viewpoint is not free"
    below = bid((vp["at"][0], vp["at"][1] - 1, vp["at"][2]))
    assert below not in ("air", "water"), f"viewpoint has {below} below it"


def _vec(v) -> str:
    return "[" + ", ".join(str(int(i)) for i in v) + "]"


def points_yaml(points: dict) -> list[str]:
    out = ["points:"]
    vp, yd, pn = points["viewpoint"], points["yard"], points["panel"]
    out.append(f"  viewpoint: {{at: {_vec(vp['at'])}, yaw: {vp['yaw']}, pitch: {vp['pitch']}}}")
    out.append(f"  yard: {{from: {_vec(yd['from'])}, to: {_vec(yd['to'])}}}")
    out.append(f"  hologram: {_vec(points['hologram'])}")
    out.append(f"  panel: {{at: {_vec(pn['at'])}, yaw: {pn['yaw']}}}")
    out.append(f"  ripe: {_vec(points['ripe'])}")
    if points.get("smoke"):
        out.append("  smoke: [" + ", ".join(_vec(p) for p in points["smoke"]) + "]")
    if "wheel" in points:
        w = points["wheel"]
        out.append(f"  wheel: {{at: {_vec(w['at'])}, axis: {w['axis']}}}")
    if "recipe_book" in points:
        out.append(f"  recipe_book: {_vec(points['recipe_book'])}")
    if "notice_board" in points:
        nb = points["notice_board"]
        out.append(f"  notice_board: {{at: {_vec(nb['at'])}, yaw: {nb['yaw']}}}")
    return out


def write_place_yaml(header: list[str], layers, palette: dict, size: tuple, points: dict, signs: list,
                     ground: int) -> str:
    out = list(header) + [f"size: [{size[0]}, {size[1]}, {size[2]}]", "origin: [0, 0, 0]"]
    if ground:
        out.append(f"ground-depth: {ground}")
    out.append("palette:")
    out += [f"  {H._q(c)}: {H._q(d)}" for c, d in palette.items()]
    out.append("layers:")
    for y, layer in enumerate(layers):
        out.append(f"  # y={y - ground}")
        out.append("  - [" + ", ".join(H._q(r) for r in layer) + "]")
    out += points_yaml(points)
    out.append("signs:" if signs else "signs: []")
    for s in signs:
        out.append(f"  - at: {H._vec(s['at'])}")
        out.append("    lines: [" + ", ".join(H._q(l) for l in s["lines"]) + "]")
    return "\n".join(out) + "\n"
