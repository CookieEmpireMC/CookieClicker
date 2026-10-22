"""The economy model from docs/balance/model.md: config loading and the formulas.

Keep this file and docs/balance/model.md in sync; the Java economy implements the same maths
and is checked against golden-values.json generated from here.
"""
import math
from dataclasses import dataclass, field
from pathlib import Path

import yaml

EFFECT_TYPES = {"click-multiplier", "click-add", "click-cps-fraction", "building-multiplier", "global-multiplier"}
MULTIPLIER_TYPES = {"click-multiplier", "building-multiplier", "global-multiplier"}
MAX_BULK = 10_000           # hard upper bound for any bulk / Max count (model.md)
UNLIMITED_LEVEL = 2_147_483_647   # level cap of a repeatable upgrade without max-level (Java int max)


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
    cost_growth: float | None = None    # set = repeatable; cost of the next level grows by this
    max_level: int | None = None        # repeatable only; None = unlimited

    @property
    def repeatable(self) -> bool:
        return self.cost_growth is not None

    @property
    def level_cap(self) -> int:
        """Highest level this upgrade can reach (one-time = 1)."""
        if not self.repeatable:
            return 1
        return self.max_level if self.max_level is not None else UNLIMITED_LEVEL


@dataclass(frozen=True)
class Economy:
    cost_growth: float
    base_click: float
    buildings: dict[str, Building]
    upgrades: dict[str, Upgrade]
    crit_chance: float = 0.0        # probability a click is a crit, 0..1
    crit_multiplier: float = 1.0    # a crit multiplies the whole click value by this (>= 1)
    milestones: tuple[int, ...] = ()        # owned counts; each one reached multiplies that building's CPS
    milestone_multiplier: float = 2.0       # the factor per milestone reached (>= 1)


@dataclass
class State:
    pouch: float = 0.0
    baked: float = 0.0
    owned: dict[str, int] = field(default_factory=dict)
    upgrades: dict[str, int] = field(default_factory=dict)   # upgrade id -> level (one-time = 1)

    def __post_init__(self):
        if isinstance(self.upgrades, (set, frozenset, list, tuple)):   # old form: a set of bought ids
            self.upgrades = {uid: 1 for uid in self.upgrades}

    def level(self, uid: str) -> int:
        return self.upgrades.get(uid, 0)

    def copy(self) -> "State":
        return State(self.pouch, self.baked, dict(self.owned), dict(self.upgrades))


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
        if _number(spec.get("base-cost"), f"{where}.base-cost") <= 0:
            raise ConfigError(f"{where}.base-cost must be > 0")
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
        growth, max_level = spec.get("cost-growth"), spec.get("max-level")
        if growth is not None:
            growth = _number(growth, f"{where}.cost-growth")
            if growth < 1:
                raise ConfigError(f"{where}.cost-growth must be >= 1")
        if max_level is not None:
            if growth is None:
                raise ConfigError(f"{where}.max-level needs cost-growth (only repeatable upgrades have levels)")
            if not isinstance(max_level, int) or isinstance(max_level, bool) or max_level < 1:
                raise ConfigError(f"{where}.max-level must be an integer >= 1, got {max_level!r}")
        cost = _number(spec.get("cost"), f"{where}.cost")
        if cost <= 0:
            raise ConfigError(f"{where}.cost must be > 0")
        upgrades[uid] = Upgrade(uid, cost, effect["type"],
                                _number(effect.get("value"), f"{where}.effect.value"), target,
                                unlock_building, int(unlock.get("owned", 0)),
                                _number(unlock.get("baked", 0), f"{where}.unlock.baked"), growth, max_level)

    growth = _number(b_raw.get("cost-growth", 1.15), "buildings.yml: cost-growth")
    if growth < 1:
        raise ConfigError("buildings.yml: cost-growth must be >= 1")
    click = u_raw.get("click") or {}
    base_click = _number(click.get("base-power", 1), "upgrades.yml: click.base-power")
    crit_chance = _number(click.get("crit-chance", 0), "upgrades.yml: click.crit-chance")
    if crit_chance > 1:
        raise ConfigError("upgrades.yml: click.crit-chance must be between 0 and 1")
    crit_multiplier = _number(click.get("crit-multiplier", 1), "upgrades.yml: click.crit-multiplier")
    if crit_multiplier < 1:
        raise ConfigError("upgrades.yml: click.crit-multiplier must be >= 1")
    milestones = b_raw.get("milestones") or []
    if not isinstance(milestones, list) or any(not isinstance(c, int) or isinstance(c, bool) or c < 1
                                               for c in milestones):
        raise ConfigError(f"buildings.yml: milestones must be a list of integers >= 1, got {milestones!r}")
    if any(b <= a for a, b in zip(milestones, milestones[1:])):
        raise ConfigError("buildings.yml: milestones must be strictly increasing")
    milestone_multiplier = _number(b_raw.get("milestone-multiplier", 2), "buildings.yml: milestone-multiplier")
    if milestone_multiplier < 1:
        raise ConfigError("buildings.yml: milestone-multiplier must be >= 1")
    return Economy(growth, base_click, buildings, upgrades, crit_chance, crit_multiplier,
                   tuple(milestones), milestone_multiplier)


def series_cost(base: float, growth: float, owned: int, count: int) -> float:
    """Cost of the next `count` items when `owned` are owned (model.md "Bulk cost"): rounded up once."""
    if count <= 0:
        return 0
    first = base * growth ** owned
    ratio = float(count) if growth == 1 else (growth ** count - 1) / (growth - 1)
    return math.ceil(first * ratio)


def series_max(base: float, growth: float, owned: int, cap: int, pouch: float) -> int:
    """Largest k in [0, cap] with series_cost(k) <= pouch (model.md "Max affordable")."""
    cap = min(cap, MAX_BULK)
    if cap <= 0 or not pouch >= series_cost(base, growth, owned, 1):
        return 0
    first = base * growth ** owned
    if growth == 1:
        estimate = pouch / first
    else:
        estimate = math.log(1 + pouch * (growth - 1) / first) / math.log(growth)
    k = int(min(max(math.floor(estimate), 1), cap)) if math.isfinite(estimate) else cap
    while k > 1 and series_cost(base, growth, owned, k) > pouch:
        k -= 1
    while k < cap and series_cost(base, growth, owned, k + 1) <= pouch:
        k += 1
    return k


def building_cost(eco: Economy, state: State, bid: str) -> float:
    return bulk_cost(eco, bid, state.owned.get(bid, 0), 1)


def bulk_cost(eco: Economy, bid: str, owned: int, count: int) -> float:
    return series_cost(eco.buildings[bid].base_cost, eco.cost_growth, owned, count)


def building_max(eco: Economy, bid: str, owned: int, pouch: float) -> int:
    return series_max(eco.buildings[bid].base_cost, eco.cost_growth, owned, MAX_BULK, pouch)


def upgrade_bulk_cost(eco: Economy, uid: str, level: int, count: int) -> float:
    """Cost of the next `count` levels from `level`; one-time upgrades cost `cost` (count 1 only)."""
    u = eco.upgrades[uid]
    count = min(count, u.level_cap - level)
    if count <= 0:
        return math.inf
    if not u.repeatable:
        return u.cost
    return series_cost(u.cost, u.cost_growth, level, count)


def upgrade_cost(eco: Economy, state: State, uid: str) -> float:
    return upgrade_bulk_cost(eco, uid, state.level(uid), 1)


def upgrade_max(eco: Economy, uid: str, level: int, pouch: float) -> int:
    u = eco.upgrades[uid]
    if not u.repeatable:
        return 1 if level < 1 and pouch >= u.cost else 0
    return series_max(u.cost, u.cost_growth, level, u.level_cap - level, pouch)


def milestones_reached(eco: Economy, owned: int) -> int:
    """How many milestone counts are <= owned."""
    return sum(1 for c in eco.milestones if owned >= c)


def milestone_factor(eco: Economy, owned: int) -> float:
    """milestone_multiplier ^ milestones_reached (model.md "Milestones")."""
    return eco.milestone_multiplier ** milestones_reached(eco, owned)


def building_cps(eco: Economy, state: State, bid: str) -> float:
    """owned × base_cps × milestone factor × Π building-multiplier effects, in that order."""
    n = state.owned.get(bid, 0)
    mult = math.prod(_effect(u, lv) for u, lv in _bought(eco, state, "building-multiplier") if u.building == bid)
    return n * eco.buildings[bid].base_cps * milestone_factor(eco, n) * mult


def total_cps(eco: Economy, state: State) -> float:
    total = 0.0
    for bid in eco.buildings:              # buildings.yml order (model.md), so sums match the Java economy bit for bit
        if state.owned.get(bid, 0):
            total += building_cps(eco, state, bid)
    return total * math.prod(_effect(u, lv) for u, lv in _bought(eco, state, "global-multiplier"))


def click_power(eco: Economy, state: State) -> float:
    base = eco.base_click + sum(_effect(u, lv) for u, lv in _bought(eco, state, "click-add"))
    power = base * math.prod(_effect(u, lv) for u, lv in _bought(eco, state, "click-multiplier"))
    fraction = sum(_effect(u, lv) for u, lv in _bought(eco, state, "click-cps-fraction"))
    return power + (fraction * total_cps(eco, state) if fraction else 0.0)


def click_value(eco: Economy, state: State, crit: bool) -> float:
    """Cookies from one concrete click: a crit multiplies the whole click power."""
    return click_power(eco, state) * (eco.crit_multiplier if crit else 1.0)


def crit_factor(eco: Economy) -> float:
    """Expected click value / click power: 1 + p × (m − 1)."""
    return 1.0 + eco.crit_chance * (eco.crit_multiplier - 1.0)


def expected_click(eco: Economy, state: State) -> float:
    """Average cookies per click including crits (what the simulator uses; deterministic)."""
    return click_power(eco, state) * crit_factor(eco)


def building_unlocked(eco: Economy, state: State, bid: str) -> bool:
    return state.baked >= eco.buildings[bid].unlock_baked


def upgrade_unlocked(eco: Economy, state: State, uid: str) -> bool:
    u = eco.upgrades[uid]
    if state.level(uid) >= u.level_cap or state.baked < u.unlock_baked:
        return False
    return u.unlock_building is None or state.owned.get(u.unlock_building, 0) >= u.unlock_owned


def _effect(u: Upgrade, level: int) -> float:
    """An upgrade's effect at `level`: additive types × level, multiplier types ^ level."""
    return u.value ** level if u.type in MULTIPLIER_TYPES else u.value * level


def _bought(eco: Economy, state: State, kind: str):
    return ((eco.upgrades[uid], lv) for uid, lv in state.upgrades.items()
            if lv > 0 and uid in eco.upgrades and eco.upgrades[uid].type == kind)
