"""Block data builders and the resolvers every design generator needs (26.3 vanilla states, all properties spelled out).

    st("dark_oak", "east", "top")    -> stairs (shape is computed later by resolve(); write every stair as straight)
    sl("spruce", "top") / lg("dark_oak", "x") / td("spruce", "north", "top", open=True) / dr("oak", "north", "lower")
    CONNECT tokens "@glass_pane", "@iron_bars", "@spruce_fence", "@stone_brick_wall" ...: resolve() fills the
    connection flags from the neighbours, exactly as the validator checks them.

resolve(g) mutates a grid {(x, y, z): data} in place:
  1. connectables (panes, bars, fences, walls) get north/east/south/west (+ up for walls),
  2. every stair gets the shape the game would give it on placement (inner/outer corners).
to_palette(g) -> (chars grid, palette dict): deterministic single-char palette, `_` = air.
"""
from __future__ import annotations

HORIZ = ("north", "east", "south", "west")
DIRS = {"north": (0, 0, -1), "south": (0, 0, 1), "west": (-1, 0, 0), "east": (1, 0, 0), "up": (0, 1, 0),
        "down": (0, -1, 0)}
OPP = {"north": "south", "south": "north", "west": "east", "east": "west", "up": "down", "down": "up"}
CCW = {"north": "west", "west": "south", "south": "east", "east": "north"}     # Direction.getCounterClockWise
CW = {v: k for k, v in CCW.items()}
AIR = "minecraft:air"


def plain(block: str) -> str:
    return f"minecraft:{block}"


def st(mat: str, facing: str, half: str = "bottom", wet: bool = False) -> str:
    return (f"minecraft:{mat}_stairs[facing={facing},half={half},shape=straight,"
            f"waterlogged={'true' if wet else 'false'}]")


def sl(mat: str, kind: str = "bottom", wet: bool = False) -> str:
    return f"minecraft:{mat}_slab[type={kind},waterlogged={'true' if wet else 'false'}]"


def lg(mat: str, axis: str = "y") -> str:
    return f"minecraft:{mat}_log[axis={axis}]"


def stripped(mat: str, axis: str = "y") -> str:
    return f"minecraft:stripped_{mat}_log[axis={axis}]"


def td(mat: str, facing: str, half: str = "bottom", open: bool = False, wet: bool = False) -> str:
    return (f"minecraft:{mat}_trapdoor[facing={facing},half={half},open={'true' if open else 'false'},"
            f"powered=false,waterlogged={'true' if wet else 'false'}]")


def dr(mat: str, facing: str, half: str, hinge: str = "left") -> str:
    return f"minecraft:{mat}_door[facing={facing},half={half},hinge={hinge},open=false,powered=false]"


def lantern(hanging: bool = False, kind: str = "lantern") -> str:
    return f"minecraft:{kind}[hanging={'true' if hanging else 'false'},waterlogged=false]"


def wall_sign(mat: str, facing: str) -> str:
    return f"minecraft:{mat}_wall_sign[facing={facing},waterlogged=false]"


def barrel(facing: str = "up") -> str:
    return f"minecraft:barrel[facing={facing},open=false]"


def hay(axis: str = "y") -> str:
    return f"minecraft:hay_block[axis={axis}]"


def leaves(kind: str = "oak", dist: int = 1) -> str:
    return f"minecraft:{kind}_leaves[distance={dist},persistent=true,waterlogged=false]"


def campfire(lit: bool = True, facing: str = "north") -> str:
    return f"minecraft:campfire[facing={facing},lit={'true' if lit else 'false'},signal_fire=false,waterlogged=false]"


def chain(axis: str = "y") -> str:
    return f"minecraft:iron_chain[axis={axis},waterlogged=false]"


def cauldron_water(level: int = 3) -> str:
    return f"minecraft:water_cauldron[level={level}]"


def farmland(m: int = 7) -> str:
    return f"minecraft:farmland[moisture={m}]"


def crop(kind: str, age: int) -> str:
    return f"minecraft:{kind}[age={age}]"


def grass() -> str:
    return "minecraft:grass_block[snowy=false]"


CONNECT = "@"                         # prefix of connectable tokens


def is_connect(data: str) -> bool:
    return data.startswith(CONNECT)


def _family(block: str) -> str | None:
    if block.endswith("glass_pane") or block.endswith("_bars"):
        return "pane"
    if block.endswith("_fence") and "nether" not in block:
        return "wood_fence"
    if block.endswith("_wall") and not block.endswith("_wall_sign") and "hanging" not in block:
        return "wall"
    return None


def block_id(data: str) -> str:
    b = data.removeprefix(CONNECT).removeprefix("minecraft:")
    return b.partition("[")[0]


def props_of(data: str) -> dict:
    _, _, rest = data.partition("[")
    return dict(p.split("=") for p in rest.rstrip("]").split(",")) if rest else {}


# blocks whose side face is a full sturdy face: panes/bars/fences/walls connect to them
def sturdy(block: str) -> bool:
    if block in ("air",):
        return False
    if block.endswith(("_stairs", "_slab", "_trapdoor", "_door", "_sign", "lantern", "_carpet", "_fence_gate",
                       "campfire", "_hanging_sign", "_pot", "lightning_rod", "_bars", "_wall", "_fence",
                       "glass_pane", "_leaves", "composter", "cauldron", "flower_pot", "hopper", "bell",
                       "iron_chain", "chain", "_button", "_pressure_plate", "torch", "moss_carpet", "lectern",
                       "cocoa", "vine", "wheat", "farmland", "dirt_path", "grass", "fern", "_tulip", "poppy",
                       "dandelion", "cornflower", "azure_bluet", "oxeye_daisy", "allium", "lily_of_the_valley",
                       "rose_bush", "lilac", "peony", "bush", "sugar_cane", "water", "snow", "candle",
                       "scaffolding", "lily_pad", "orchid", "bluet", "tulip")):
        return False
    if block.startswith(("potted_", "flowering_azalea", "azalea")) and not block.endswith("_leaves"):
        return False
    if block in ("barrel_open",):
        return True
    return True


def resolve(g: dict) -> None:
    """Connection flags for @-tokens and the placement shape of every stair (in place)."""
    def bid(p):
        v = g.get(p)
        return "air" if v is None else block_id(v)

    out = {}
    for p, v in g.items():
        if not is_connect(v):
            continue
        ident = block_id(v)
        fam = _family(ident)
        flags = {}
        for d in HORIZ:
            dx, _, dz = DIRS[d]
            nb = bid((p[0] + dx, p[1], p[2] + dz))
            conn = False
            if sturdy(nb) or _family(nb) == fam:
                conn = True
            elif fam == "wood_fence" and nb.endswith("_fence_gate"):
                conn = True
            elif fam == "wall" and nb.endswith("_fence_gate"):
                conn = False
            if fam == "pane" and nb.endswith("_wall") or fam == "wood_fence" and nb.endswith(("_wall", "_bars")) \
                    or fam == "pane" and nb.endswith("_fence"):
                conn = False
            flags[d] = conn
        if fam == "wall":
            n_conn = sum(flags.values())
            straight = n_conn == 2 and (flags["north"] and flags["south"] or flags["east"] and flags["west"])
            above = bid((p[0], p[1] + 1, p[2]))
            up = not straight or sturdy(above) or _family(above) == "wall"
            props = {d: ("low" if flags[d] else "none") for d in HORIZ}
            props.update({"up": "true" if up else "false", "waterlogged": "false"})
        else:
            props = {d: ("true" if flags[d] else "false") for d in HORIZ}
            props["waterlogged"] = "false"
        out[p] = f"minecraft:{ident}[" + ",".join(f"{k}={props[k]}" for k in sorted(props)) + "]"
    g.update(out)
    resolve_stairs(g)


def stair_shape(g: dict, p: tuple, data: str) -> str:
    """The shape Minecraft assigns to a stair placed at p (StairBlock.getStairsShape)."""
    pr = props_of(data)
    facing, half = pr["facing"], pr["half"]

    def stair_at(q):
        v = g.get(q)
        if v is not None and block_id(v).endswith("_stairs"):
            return props_of(v)
        return None

    def add(q, d):
        dx, dy, dz = DIRS[d]
        return (q[0] + dx, q[1] + dy, q[2] + dz)

    def can_take(d):
        nb = stair_at(add(p, d))
        return not (nb and nb["facing"] == facing and nb["half"] == half)

    nb = stair_at(add(p, facing))
    if nb and nb["half"] == half:
        nf = nb["facing"]
        if nf in HORIZ and nf not in (facing, OPP[facing]) and can_take(OPP[nf]):
            return "outer_left" if nf == CCW[facing] else "outer_right"
    nb = stair_at(add(p, OPP[facing]))
    if nb and nb["half"] == half:
        nf = nb["facing"]
        if nf in HORIZ and nf not in (facing, OPP[facing]) and can_take(nf):
            return "inner_left" if nf == CCW[facing] else "inner_right"
    return "straight"


def resolve_stairs(g: dict) -> None:
    updates = {}
    for p, v in g.items():
        if block_id(v).endswith("_stairs"):
            shape = stair_shape(g, p, v)
            updates[p] = v.replace("shape=straight", f"shape={shape}") if "shape=straight" in v else \
                _set_shape(v, shape)
    g.update(updates)


def _set_shape(v: str, shape: str) -> str:
    pr = props_of(v)
    pr["shape"] = shape
    return v.partition("[")[0] + "[" + ",".join(f"{k}={pr[k]}" for k in sorted(pr)) + "]"


# --- palette -------------------------------------------------------------------------------------------
POOL = ("abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-|+=^~!#$%&*:;<>?@/(),{}[]'`"
        "ÀÁÂÃÄÅÆÇÈÉÊËÌÍÎÏÐÑÒÓÔÕÖØÙÚÛÜÝÞßàáâãäåæçèéêëìíîïðñòóôõöøùúûüýþÿ")


def to_palette(g: dict, preferred: dict | None = None) -> tuple[dict, dict]:
    """grid of block data -> grid of chars + palette (deterministic: scan order y, z, x; `_` = air)."""
    preferred = preferred or {}
    palette: dict[str, str] = {"_": AIR}
    rev: dict[str, str] = {AIR: "_"}
    used = set(preferred.values()) | {"_"}
    pool = iter(c for c in POOL if c not in used)
    for p in sorted(g, key=lambda p: (p[1], p[2], p[0])):
        data = g[p]
        if data in rev:
            continue
        c = preferred.get(data) or next(pool)
        assert c not in palette, (c, data)
        palette[c] = data
        rev[data] = c
    return {p: rev[d] for p, d in g.items()}, palette


def water(level: int = 0) -> str:
    return f"minecraft:water[level={level}]"


def ladder(facing: str) -> str:
    return f"minecraft:ladder[facing={facing},waterlogged=false]"


def lever(face: str, facing: str) -> str:
    return f"minecraft:lever[face={face},facing={facing},powered=false]"


def grindstone(face: str = "floor", facing: str = "north") -> str:
    return f"minecraft:grindstone[face={face},facing={facing}]"


def hopper(facing: str = "down") -> str:
    return f"minecraft:hopper[enabled=true,facing={facing}]"


def vine(*sides: str) -> str:
    return "minecraft:vine[" + ",".join(f"{d}={'true' if d in sides else 'false'}" for d in ("east", "north", "south", "up", "west")) + "]"
