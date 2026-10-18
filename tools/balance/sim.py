"""Second-by-second pacing simulation of one player profile with the optimal-ish buy strategy."""
from dataclasses import dataclass, field

from model import (Economy, State, building_cost, building_unlocked, click_power, total_cps,
                   upgrade_unlocked)


@dataclass
class Profile:
    name: str
    clicks_per_second: float
    bootstrap_clicks_per_second: float | None = None   # used until the first building is owned


@dataclass
class Purchase:
    time: int
    kind: str      # "building" | "upgrade"
    id: str
    owned_after: int
    cost: float


@dataclass
class Result:
    profile: Profile
    purchases: list[Purchase] = field(default_factory=list)
    timeline: list[tuple[int, float, float, float]] = field(default_factory=list)  # t, cps, click, pouch
    dead_zones: list[tuple[int, int]] = field(default_factory=list)                # (start, length)
    final: State | None = None


def clicks_now(profile: Profile, state: State) -> float:
    if profile.bootstrap_clicks_per_second is not None and not any(state.owned.values()):
        return profile.bootstrap_clicks_per_second
    return profile.clicks_per_second


def income(eco: Economy, state: State, clicks: float) -> float:
    return total_cps(eco, state) + clicks * click_power(eco, state)


def candidates(eco: Economy, state: State) -> list[tuple[str, str, float]]:
    """(kind, id, cost) for everything unlocked and not yet maxed out."""
    items = [("building", bid, building_cost(eco, state, bid)) for bid in eco.buildings
             if building_unlocked(eco, state, bid)]
    items += [("upgrade", uid, eco.upgrades[uid].cost) for uid in eco.upgrades if upgrade_unlocked(eco, state, uid)]
    return items


def apply(state: State, kind: str, item_id: str) -> State:
    after = state.copy()
    if kind == "building":
        after.owned[item_id] = after.owned.get(item_id, 0) + 1
    else:
        after.upgrades.add(item_id)
    return after


def best(eco: Economy, state: State, clicks: float):
    """Lowest cost per extra cookie/s; items that add nothing are never chosen."""
    now = income(eco, state, clicks)
    scored = []
    for kind, item_id, cost in candidates(eco, state):
        gain = income(eco, apply(state, kind, item_id), clicks) - now
        if gain > 1e-12:
            scored.append((cost / gain, cost, kind, item_id))
    return min(scored) if scored else None


def simulate(eco: Economy, profile: Profile, horizon_seconds: int, sample_every: int = 10) -> Result:
    state, result = State(), Result(profile)
    choice, dirty, dead_start = None, True, None
    unlocked_count = -1
    for t in range(1, horizon_seconds + 1):
        clicks = clicks_now(profile, state)
        gained = income(eco, state, clicks)
        state.pouch += gained
        state.baked += gained

        offers = candidates(eco, state)
        if len(offers) != unlocked_count:
            unlocked_count, dirty = len(offers), True
        while True:
            if dirty:
                choice, dirty = best(eco, state, clicks), False
            if choice is None or state.pouch < choice[1]:
                break
            _, cost, kind, item_id = choice
            state.pouch -= cost
            state = apply(state, kind, item_id)
            owned = state.owned.get(item_id, 1) if kind == "building" else 1
            result.purchases.append(Purchase(t, kind, item_id, owned, cost))
            dirty, clicks = True, clicks_now(profile, state)

        if result.purchases:
            affordable = any(cost <= state.pouch for _, _, cost in candidates(eco, state))
            if not affordable and dead_start is None:
                dead_start = t
            elif affordable and dead_start is not None:
                result.dead_zones.append((dead_start, t - dead_start))
                dead_start = None
        if t % sample_every == 0 or t == 1:
            result.timeline.append((t, total_cps(eco, state), click_power(eco, state), state.pouch))
    if dead_start is not None:
        result.dead_zones.append((dead_start, horizon_seconds + 1 - dead_start))
    result.final = state
    return result
