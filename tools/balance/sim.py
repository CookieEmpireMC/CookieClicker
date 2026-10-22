"""Second-by-second pacing simulation of one player profile with the optimal-ish buy strategy."""
from dataclasses import dataclass, field

from model import (Economy, State, building_cost, building_unlocked, expected_click, milestones_of, total_cps,
                   upgrade_cost, upgrade_unlocked)

# A player saving towards a building milestone: the buyer also scores "buy the k copies that reach the next
# milestone" as one purchase when k is at most this. Without it, a one-step greedy buyer never sees that the
# 8th and 9th copy are worth it for the doubling at 10.
MILESTONE_LOOKAHEAD = 5


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
    timeline: list[tuple[int, float, float, float]] = field(default_factory=list)  # t, cps, expected click, pouch
    dead_zones: list[tuple[int, int]] = field(default_factory=list)                # (start, length)
    baked: list[float] = field(default_factory=list)       # baked[t - 1] = cookies baked after second t
    final: State | None = None


def clicks_now(profile: Profile, state: State) -> float:
    if profile.bootstrap_clicks_per_second is not None and not any(state.owned.values()):
        return profile.bootstrap_clicks_per_second
    return profile.clicks_per_second


def income(eco: Economy, state: State, clicks: float) -> float:
    """Cookies per second: production plus clicks at the expected (crit-averaged) click value."""
    return total_cps(eco, state) + clicks * expected_click(eco, state)


def candidates(eco: Economy, state: State) -> list[tuple[str, str, float]]:
    """(kind, id, cost) for everything unlocked and not yet maxed out."""
    items = [("building", bid, building_cost(eco, state, bid)) for bid in eco.buildings
             if building_unlocked(eco, state, bid)]
    items += [("upgrade", uid, upgrade_cost(eco, state, uid)) for uid in eco.upgrades
              if upgrade_unlocked(eco, state, uid)]
    return items


def apply(state: State, kind: str, item_id: str) -> State:
    after = state.copy()
    if kind == "building":
        after.owned[item_id] = after.owned.get(item_id, 0) + 1
    else:
        after.upgrades[item_id] = after.level(item_id) + 1
    return after


def best(eco: Economy, state: State, clicks: float):
    """Lowest cost per extra cookie/s as (score, total cost, kind, id, count); items that add nothing are never
    chosen. count > 1 is a push to the building's next milestone: the buyer commits to that building and buys its
    copies one at a time; the choice is re-scored after each copy."""
    now = income(eco, state, clicks)
    scored = []
    for kind, item_id, cost in candidates(eco, state):
        after = apply(state, kind, item_id)
        gain = income(eco, after, clicks) - now
        if gain > 1e-12:
            scored.append((cost / gain, cost, kind, item_id, 1))
        if kind == "building":
            owned = state.owned.get(item_id, 0)
            need = next((c - owned for c in milestones_of(eco, item_id) if c > owned), 0)
            if 2 <= need <= MILESTONE_LOOKAHEAD:
                total, step = cost, after
                for _ in range(need - 1):
                    total += building_cost(eco, step, item_id)
                    step = apply(step, kind, item_id)
                gain = income(eco, step, clicks) - now
                if gain > 1e-12:
                    scored.append((total / gain, total, kind, item_id, need))
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
        result.baked.append(state.baked)

        offers = candidates(eco, state)
        if len(offers) != unlocked_count:
            unlocked_count, dirty = len(offers), True
        while True:
            if dirty:
                choice, dirty = best(eco, state, clicks), False
            if choice is None:
                break
            _, _, kind, item_id, _ = choice
            # a milestone push is bought one copy at a time, as each becomes affordable
            cost = building_cost(eco, state, item_id) if kind == "building" else upgrade_cost(eco, state, item_id)
            if state.pouch < cost:
                break
            state.pouch -= cost
            state = apply(state, kind, item_id)
            owned = state.owned.get(item_id, 1) if kind == "building" else state.level(item_id)
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
            result.timeline.append((t, total_cps(eco, state), expected_click(eco, state), state.pouch))
    if dead_start is not None:
        result.dead_zones.append((dead_start, horizon_seconds + 1 - dead_start))
    result.final = state
    return result
