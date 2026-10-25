"""The economy model from docs/balance/model.md: config loading and the formulas.

Keep this file and docs/balance/model.md in sync; the Java economy implements the same maths
and is checked against golden-values.json generated from here.
"""
import math
import re
from dataclasses import dataclass, field
from pathlib import Path

import yaml

EFFECT_TYPES = {"click-multiplier", "click-add", "click-cps-fraction", "building-multiplier", "global-multiplier",
                "recipes-unlock"}
RECIPE_EFFECT_TYPES = {"cps-percent"}
MULTIPLIER_TYPES = {"click-multiplier", "building-multiplier", "global-multiplier"}
MAX_BULK = 10_000           # hard upper bound for any bulk / Max count (model.md)
UNLIMITED_LEVEL = 2_147_483_647   # level cap of a repeatable upgrade without max-level (Java int max)
ID_PATTERN = re.compile(r"[a-z][a-z0-9_]*")


class ConfigError(Exception):
    pass


@dataclass(frozen=True)
class Building:
    id: str
    base_cost: float
    base_cps: float
    unlock_baked: float
    milestones: tuple[int, ...] | None = None   # per-building override of the top-level list; None = use it
    produces: tuple[str, float] | None = None   # (ingredient id, per second per copy); None = no ingredient


@dataclass(frozen=True)
class Recipe:
    id: str
    cost: float                          # cookies
    ingredients: tuple[tuple[str, float], ...]   # (ingredient id, amount), in config order
    value: float                         # cps-percent as a fraction: 0.02 = +2 % CPS
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
    milestones: tuple[int, ...] = ()        # owned counts; each one reached doubles that building's CPS
    recipes: dict[str, Recipe] = field(default_factory=dict)

    @property
    def ingredients(self) -> tuple[str, ...]:
        """Every ingredient some building produces, in buildings.yml order."""
        seen = []
        for b in self.buildings.values():
            if b.produces and b.produces[0] not in seen:
                seen.append(b.produces[0])
        return tuple(seen)


@dataclass
class State:
    pouch: float = 0.0
    baked: float = 0.0
    owned: dict[str, int] = field(default_factory=dict)
    upgrades: dict[str, int] = field(default_factory=dict)   # upgrade id -> level (one-time = 1)
    ingredients: dict[str, float] = field(default_factory=dict)   # ingredient id -> stock
    recipes: set[str] = field(default_factory=set)               # bought recipe ids

    def __post_init__(self):
        if isinstance(self.upgrades, (set, frozenset, list, tuple)):   # old form: a set of bought ids
            self.upgrades = {uid: 1 for uid in self.upgrades}

    def level(self, uid: str) -> int:
        return self.upgrades.get(uid, 0)

    def copy(self) -> "State":
        return State(self.pouch, self.baked, dict(self.owned), dict(self.upgrades), dict(self.ingredients),
                     set(self.recipes))


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
                                  _number(unlock.get("baked", 0), f"{where}.unlock.baked"),
                                  _milestones(spec["milestones"], f"{where}.milestones")
                                  if "milestones" in spec else None,
                                  _produces(spec["produces"], f"{where}.produces") if "produces" in spec else None)
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
        if effect["type"] == "recipes-unlock" and growth is not None:
            raise ConfigError(f"{where}: a recipes-unlock upgrade is one-time (no cost-growth)")
        value = effect.get("value", 0) if effect["type"] == "recipes-unlock" else effect.get("value")
        upgrades[uid] = Upgrade(uid, cost, effect["type"],
                                _number(value, f"{where}.effect.value"), target,
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
    milestones = _milestones(b_raw.get("milestones"), "buildings.yml: milestones")
    produced = {b.produces[0] for b in buildings.values() if b.produces}
    recipes = {}
    for rid, spec in (u_raw.get("recipes") or {}).items():
        where = f"upgrades.yml: recipes.{rid}"
        if rid in upgrades:
            raise ConfigError(f"{where}: id is also an upgrade id")
        spec = spec or {}
        effect, unlock = spec.get("effect") or {}, spec.get("unlock") or {}
        if effect.get("type") not in RECIPE_EFFECT_TYPES:
            raise ConfigError(f"{where}.effect.type must be one of {sorted(RECIPE_EFFECT_TYPES)}")
        value = _number(effect.get("value"), f"{where}.effect.value")
        cost = _number(spec.get("cost"), f"{where}.cost")
        if cost <= 0 or value <= 0:
            raise ConfigError(f"{where}: cost and effect.value must be > 0")
        raw_ing = spec.get("ingredients") or {}
        if not isinstance(raw_ing, dict) or not raw_ing:
            raise ConfigError(f"{where}.ingredients must be a non-empty map ingredient -> amount")
        ingredients = []
        for ing, amount in raw_ing.items():
            if ing not in produced:
                raise ConfigError(f"{where}.ingredients: '{ing}' is not produced by any building")
            amount = _number(amount, f"{where}.ingredients.{ing}")
            if amount <= 0:
                raise ConfigError(f"{where}.ingredients.{ing} must be > 0")
            ingredients.append((ing, amount))
        recipes[rid] = Recipe(rid, cost, tuple(ingredients), value,
                              _number(unlock.get("baked", 0), f"{where}.unlock.baked"))
    if recipes and not any(u.type == "recipes-unlock" for u in upgrades.values()):
        raise ConfigError("upgrades.yml: recipes need a recipes-unlock upgrade, or they can never show")
    return Economy(growth, base_click, buildings, upgrades, crit_chance, crit_multiplier, milestones, recipes)


def _produces(raw, where: str) -> tuple[str, float]:
    """`produces: { ingredient: <id>, per-second: <rate > 0> }`."""
    if not isinstance(raw, dict) or not isinstance(raw.get("ingredient"), str) \
            or not ID_PATTERN.fullmatch(raw["ingredient"]):
        raise ConfigError(f"{where} needs ingredient: <id> (lowercase letters, digits, _), got {raw!r}")
    rate = _number(raw.get("per-second"), f"{where}.per-second")
    if rate <= 0:
        raise ConfigError(f"{where}.per-second must be > 0")
    return raw["ingredient"], rate


def _milestones(raw, where: str) -> tuple[int, ...]:
    """A milestones list: strictly increasing integers >= 1; missing/null/empty = none."""
    raw = [] if raw is None else raw
    if not isinstance(raw, list) or any(not isinstance(c, int) or isinstance(c, bool) or c < 1 for c in raw):
        raise ConfigError(f"{where} must be a list of integers >= 1, got {raw!r}")
    if any(b <= a for a, b in zip(raw, raw[1:])):
        raise ConfigError(f"{where} must be strictly increasing")
    return tuple(raw)


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


MILESTONE_FACTOR = 2.0      # each milestone reached doubles the building's production (model.md, GDD §4.3)


def milestones_of(eco: Economy, bid: str) -> tuple[int, ...]:
    """The building's own `milestones:` if set, else the top-level list."""
    own = eco.buildings[bid].milestones
    return eco.milestones if own is None else own


def milestones_reached(eco: Economy, bid: str, owned: int) -> int:
    """How many of the building's milestone counts are <= owned."""
    return sum(1 for c in milestones_of(eco, bid) if owned >= c)


def milestone_factor(eco: Economy, bid: str, owned: int) -> float:
    """2 ^ milestones_reached (model.md "Milestones")."""
    return MILESTONE_FACTOR ** milestones_reached(eco, bid, owned)


def building_cps(eco: Economy, state: State, bid: str) -> float:
    """owned × base_cps × milestone factor × Π building-multiplier effects, in that order."""
    n = state.owned.get(bid, 0)
    mult = math.prod(_effect(u, lv) for u, lv in _bought(eco, state, "building-multiplier") if u.building == bid)
    return n * eco.buildings[bid].base_cps * milestone_factor(eco, bid, n) * mult


def total_cps(eco: Economy, state: State) -> float:
    """(Σ building_cps) × Π global multipliers × (1 + recipe percent), in that order."""
    total = 0.0
    for bid in eco.buildings:              # buildings.yml order (model.md), so sums match the Java economy bit for bit
        if state.owned.get(bid, 0):
            total += building_cps(eco, state, bid)
    total *= math.prod(_effect(u, lv) for u, lv in _bought(eco, state, "global-multiplier"))
    return total * (1.0 + recipe_percent(eco, state))


def recipe_percent(eco: Economy, state: State) -> float:
    """Σ value of bought recipes, from 0.0 in upgrades.yml `recipes:` order."""
    pct = 0.0
    for rid, r in eco.recipes.items():
        if rid in state.recipes:
            pct += r.value
    return pct


def recipes_unlocked(eco: Economy, state: State) -> bool:
    """True once any recipes-unlock upgrade (the Recipe Book) is bought."""
    return any(lv > 0 for _, lv in _bought(eco, state, "recipes-unlock"))


def ingredient_rate(eco: Economy, state: State, ingredient: str) -> float:
    """Per second: Σ owned × per_second × milestone factor over its producers (buildings.yml order); 0 before the
    Recipe Book. Building/global multipliers and recipes don't apply."""
    if not recipes_unlocked(eco, state):
        return 0.0
    rate = 0.0
    for bid, b in eco.buildings.items():
        n = state.owned.get(bid, 0)
        if b.produces and b.produces[0] == ingredient and n:
            rate += n * b.produces[1] * milestone_factor(eco, bid, n)
    return rate


def recipe_visible(eco: Economy, state: State, rid: str) -> bool:
    """Shown in the book: Recipe Book owned, baked >= unlock, not bought yet."""
    return (rid not in state.recipes and recipes_unlocked(eco, state)
            and state.baked >= eco.recipes[rid].unlock_baked)


def recipe_ingredients_ready(eco: Economy, state: State, rid: str) -> bool:
    return all(state.ingredients.get(ing, 0.0) >= amount for ing, amount in eco.recipes[rid].ingredients)


def recipe_affordable(eco: Economy, state: State, rid: str) -> bool:
    """Visible, pouch >= cost and every ingredient stock >= its amount (all or nothing)."""
    return (recipe_visible(eco, state, rid) and state.pouch >= eco.recipes[rid].cost
            and recipe_ingredients_ready(eco, state, rid))


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
