"""The economy model from docs/balance/model.md: config loading and the formulas.

Keep this file and docs/balance/model.md in sync; the Java economy implements the same maths
and is checked against golden-values.json generated from here.
"""
import math
from dataclasses import dataclass, field
from pathlib import Path

import yaml

EFFECT_TYPES = {"click-multiplier", "click-add", "click-cps-fraction", "building-multiplier", "global-multiplier"}


class ConfigError(Exception):
    pass


@dataclass(frozen=True)
class Building:
    id: str
    base_cost: float
    base_cps: float
    unlock_baked: float


@dataclass(frozen=True)
class Upgrade:
    id: str
    cost: float
    type: str
    value: float
    building: str | None        # for building-multiplier
    unlock_building: str | None
    unlock_owned: int
    unlock_baked: float


@dataclass(frozen=True)
class Economy:
    cost_growth: float
    base_click: float
    buildings: dict[str, Building]
    upgrades: dict[str, Upgrade]


@dataclass
class State:
    pouch: float = 0.0
    baked: float = 0.0
    owned: dict[str, int] = field(default_factory=dict)
    upgrades: set[str] = field(default_factory=set)

    def copy(self) -> "State":
        return State(self.pouch, self.baked, dict(self.owned), set(self.upgrades))


def _number(raw, where: str) -> float:
    if not isinstance(raw, (int, float)) or isinstance(raw, bool) or not math.isfinite(raw) or raw < 0:
        raise ConfigError(f"{where} must be a non-negative number, got {raw!r}")
    return float(raw)


def load(config_dir: Path) -> Economy | None:
    """Load buildings.yml + upgrades.yml; None if neither exists yet."""
    b_path, u_path = config_dir / "buildings.yml", config_dir / "upgrades.yml"
    if not b_path.is_file() and not u_path.is_file():
        return None
    if not b_path.is_file() or not u_path.is_file():
        raise ConfigError(f"need both {b_path} and {u_path}")
    b_raw = yaml.safe_load(b_path.read_text(encoding="utf-8")) or {}
    u_raw = yaml.safe_load(u_path.read_text(encoding="utf-8")) or {}

    buildings = {}
    for bid, spec in (b_raw.get("buildings") or {}).items():
        where = f"buildings.yml: {bid}"
        unlock = (spec or {}).get("unlock") or {}
        buildings[bid] = Building(bid, _number(spec.get("base-cost"), f"{where}.base-cost"),
                                  _number(spec.get("base-cps"), f"{where}.base-cps"),
                                  _number(unlock.get("baked", 0), f"{where}.unlock.baked"))
    if not buildings:
        raise ConfigError("buildings.yml has no buildings")

    upgrades = {}
    for uid, spec in (u_raw.get("upgrades") or {}).items():
        where = f"upgrades.yml: {uid}"
        effect, unlock = (spec or {}).get("effect") or {}, (spec or {}).get("unlock") or {}
        if effect.get("type") not in EFFECT_TYPES:
            raise ConfigError(f"{where}.effect.type must be one of {sorted(EFFECT_TYPES)}")
        target = effect.get("building")
        if effect["type"] == "building-multiplier" and target not in buildings:
            raise ConfigError(f"{where}.effect.building '{target}' is not a building")
        unlock_building = unlock.get("building")
        if unlock_building is not None and unlock_building not in buildings:
            raise ConfigError(f"{where}.unlock.building '{unlock_building}' is not a building")
        upgrades[uid] = Upgrade(uid, _number(spec.get("cost"), f"{where}.cost"), effect["type"],
                                _number(effect.get("value"), f"{where}.effect.value"), target,
                                unlock_building, int(unlock.get("owned", 0)),
                                _number(unlock.get("baked", 0), f"{where}.unlock.baked"))

    growth = _number(b_raw.get("cost-growth", 1.15), "buildings.yml: cost-growth")
    if growth < 1:
        raise ConfigError("buildings.yml: cost-growth must be >= 1")
    base_click = _number(((u_raw.get("click") or {}).get("base-power", 1)), "upgrades.yml: click.base-power")
    return Economy(growth, base_click, buildings, upgrades)


def building_cost(eco: Economy, state: State, bid: str) -> float:
    b = eco.buildings[bid]
    return math.ceil(b.base_cost * eco.cost_growth ** state.owned.get(bid, 0))


def bulk_cost(eco: Economy, bid: str, owned: int, count: int) -> float:
    b = eco.buildings[bid]
    return sum(math.ceil(b.base_cost * eco.cost_growth ** (owned + i)) for i in range(count))


def total_cps(eco: Economy, state: State) -> float:
    total = 0.0
    for bid, n in state.owned.items():
        mult = math.prod(u.value for u in _bought(eco, state, "building-multiplier") if u.building == bid)
        total += n * eco.buildings[bid].base_cps * mult
    return total * math.prod(u.value for u in _bought(eco, state, "global-multiplier"))


def click_power(eco: Economy, state: State) -> float:
    base = eco.base_click + sum(u.value for u in _bought(eco, state, "click-add"))
    power = base * math.prod(u.value for u in _bought(eco, state, "click-multiplier"))
    fraction = sum(u.value for u in _bought(eco, state, "click-cps-fraction"))
    return power + (fraction * total_cps(eco, state) if fraction else 0.0)


def building_unlocked(eco: Economy, state: State, bid: str) -> bool:
    return state.baked >= eco.buildings[bid].unlock_baked


def upgrade_unlocked(eco: Economy, state: State, uid: str) -> bool:
    u = eco.upgrades[uid]
    if uid in state.upgrades or state.baked < u.unlock_baked:
        return False
    return u.unlock_building is None or state.owned.get(u.unlock_building, 0) >= u.unlock_owned


def _bought(eco: Economy, state: State, kind: str):
    return (eco.upgrades[uid] for uid in state.upgrades if eco.upgrades[uid].type == kind)
