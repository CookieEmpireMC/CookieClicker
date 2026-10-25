"""Balance simulator CLI.

  python tools/balance/main.py report [--config-dir DIR]   # mise run balance
  python tools/balance/main.py check  [--config-dir DIR]   # mise run balance-check

`report` simulates every profile from docs/balance/targets.md against the real configs
(src/main/resources/buildings.yml + upgrades.yml), writes docs/balance/report-<date>.md with
plots in docs/balance/img/, and regenerates docs/balance/golden-values.json for the tester.
`check` runs the same simulation, writes nothing, and exits 1 if any target fails.
"""
import argparse
import bisect
import datetime
import json
import math
import re
import sys
from pathlib import Path

import yaml

sys.path.insert(0, str(Path(__file__).parent))
from model import (ConfigError, Economy, State, building_cps, building_max, bulk_cost, click_power,  # noqa: E402
                   click_value, crit_factor, expected_click, ingredient_rate, load, milestone_factor, milestones_of,
                   milestones_reached, recipe_affordable, recipe_percent, total_cps,
                   upgrade_bulk_cost, upgrade_max)
from sim import Profile, Result, simulate  # noqa: E402

TARGETS = Path("docs/balance/targets.md")
BALANCE_DIR = Path("docs/balance")
DEFAULT_CONFIG_DIR = Path("src/main/resources")
MILESTONES = [1, 10, 25, 50, 100]
BAKED_MARKS = [10_000, 750_000, 25_000_000, 1_000_000_000]   # roughly the expansion-tier unlocks + Rebirth
MILESTONE_OWNED = (0, 1, 9, 10, 24, 25, 49, 50, 99, 100, 149, 150, 199, 200, 250)
from model import MAX_BULK  # noqa: E402


def load_targets(path: Path) -> dict:
    match = re.search(r"```yaml\n(.*?)```", path.read_text(encoding="utf-8"), re.S)
    if not match:
        raise ConfigError(f"{path} has no ```yaml block")
    return yaml.safe_load(match.group(1))


def profiles_from(targets: dict) -> list[Profile]:
    return [Profile(name, float(p.get("clicks-per-second", 0)), p.get("bootstrap-clicks-per-second"))
            for name, p in (targets.get("profiles") or {}).items()]


def first_time(result: Result, kind: str, item_id: str | None = None, count: int = 1) -> int | None:
    for p in result.purchases:
        if p.kind == kind and (item_id is None or p.id == item_id) and p.owned_after >= count:
            return p.time
    return None


def metric(eco: Economy, result: Result, target: dict) -> float | None:
    """Seconds for the target's metric; None means never reached within the horizon."""
    kind = target["metric"]
    if kind == "first-purchase":
        return first_time(result, "building")
    if kind == "all-buildings":
        times = [first_time(result, "building", bid) for bid in eco.buildings]
        return None if None in times else max(times)
    if kind == "longest-dead-zone":
        return longest_dead_zone(result, target.get("window-minutes"))
    if kind == "time-to":
        return first_time(result, "building", target["building"], int(target["count"]))
    if kind == "upgrade":
        return first_time(result, "upgrade", target["upgrade"])
    if kind == "baked":
        return baked_time(result, float(target["cookies"]))
    if kind == "recipes":
        return recipes_time(eco, result, target)
    if kind == "recipe-ingredient-wait":
        return recipe_wait(result)
    raise ConfigError(f"unknown metric '{kind}' in target {target.get('id')}")


def recipes_time(eco: Economy, result: Result, target: dict) -> int | None:
    """Seconds until `count` recipes are bought (default: all); with `since: <upgrade>`, counted from that buy."""
    count = int(target.get("count", len(eco.recipes)))
    times = [p.time for p in result.purchases if p.kind == "recipe"]
    if count < 1 or len(times) < count:
        return None
    if "since" not in target:
        return times[count - 1]
    start = first_time(result, "upgrade", target["since"])
    return None if start is None else times[count - 1] - start


def recipe_wait(result: Result) -> int:
    """Longest wait of any recipe on its ingredients alone (see sim.Result.recipe_waits); an unfinished wait runs
    to the end of the horizon."""
    end = len(result.baked) + 1
    return max(((ready or end) - start for start, ready in result.recipe_waits.values()), default=0)


def baked_time(result: Result, cookies: float) -> int | None:
    """First second at which `cookies` have been baked in total."""
    i = bisect.bisect_left(result.baked, cookies)
    return i + 1 if i < len(result.baked) else None


def longest_dead_zone(result: Result, window_minutes: float | None = None) -> int:
    """Longest dead zone; with a window, only the part of each zone inside the first N minutes counts."""
    if window_minutes is None:
        return max((length for _, length in result.dead_zones), default=0)
    end = int(float(window_minutes) * 60) + 1          # seconds 1..window are inside
    return max((min(start + length, end) - start for start, length in result.dead_zones if start < end), default=0)


def evaluate(eco: Economy, targets: dict, results: dict[str, Result]) -> list[dict]:
    rows = []
    for target in targets.get("targets") or []:
        for name in target.get("profiles") or list(results):
            if name not in results:
                raise ConfigError(f"target {target['id']} uses unknown profile '{name}'")
            value = metric(eco, results[name], target)
            limit = float(target["max-seconds"]) if "max-seconds" in target else None
            floor = float(target["min-seconds"]) if "min-seconds" in target else None
            if limit is None and floor is None:
                raise ConfigError(f"target {target['id']} needs max-seconds and/or min-seconds")
            # never reached passes a min-only target ("not too fast"); it fails anything with a max
            ok = (value is None and limit is None) or (value is not None and (limit is None or value <= limit)
                                                       and (floor is None or value >= floor))
            rows.append({"id": target["id"], "description": target.get("description", ""), "profile": name,
                         "value": value, "min": floor, "max": limit, "ok": ok})
    return rows


def fmt_time(seconds: float | None) -> str:
    if seconds is None:
        return "never"
    seconds = int(seconds)
    h, rem = divmod(seconds, 3600)
    m, s = divmod(rem, 60)
    return f"{h}h{m:02d}m{s:02d}s" if h else (f"{m}m{s:02d}s" if m else f"{s}s")


def fmt_limit(row: dict) -> str:
    """'≤ 3m00s', '≥ 1h30m00s' or '1h30m00s – 4h00m00s'."""
    if row["min"] is None:
        return f"≤ {fmt_time(row['max'])}"
    if row["max"] is None:
        return f"≥ {fmt_time(row['min'])}"
    return f"{fmt_time(row['min'])} – {fmt_time(row['max'])}"


def golden_values(eco: Economy, results: dict[str, Result]) -> dict:
    """Deterministic expected values the Java economy must reproduce exactly."""
    costs = {bid: {"single": [bulk_cost(eco, bid, n, 1) for n in range(0, 101)],
                   "buy-10-from": {str(n): bulk_cost(eco, bid, n, 10) for n in (0, 10, 50)},
                   "bulk": [{"owned": n, "count": k, "cost": bulk_cost(eco, bid, n, k)}
                            for n in (0, 1, 10, 50, 100) for k in (1, 5, 10, 100)]}
             for bid in eco.buildings}
    max_cases = []
    for bid in eco.buildings:
        cost = lambda n, k, b=bid: bulk_cost(eco, b, n, k)  # noqa: E731
        maxf = lambda n, p, b=bid: building_max(eco, b, n, p)  # noqa: E731
        max_cases += _max_cases("building", bid, cost, maxf, (0, 7, 50), None)
    upgrades = {}
    for uid, u in eco.upgrades.items():
        entry = {"cost": u.cost, "repeatable": u.repeatable, "cost-growth": u.cost_growth, "max-level": u.max_level,
                 "type": u.type, "value": u.value}
        if u.repeatable:
            top = min(u.level_cap, 100)
            entry["single"] = [upgrade_bulk_cost(eco, uid, lv, 1) for lv in range(top)]
            entry["bulk"] = [{"level": lv, "count": k, "cost": upgrade_bulk_cost(eco, uid, lv, k)}
                             for lv in (0, 1, 10) for k in (1, 5, 10) if lv < u.level_cap]
            cost = lambda n, k, i=uid: upgrade_bulk_cost(eco, i, n, k)  # noqa: E731
            maxf = lambda n, p, i=uid: upgrade_max(eco, i, n, p)  # noqa: E731
            levels = sorted({0, 3, max(u.level_cap - 3, 0)} if u.max_level is not None else {0, 3, 20})
            max_cases += _max_cases("upgrade", uid, cost, maxf, levels, u.max_level)
            if u.max_level is not None:
                max_cases.append({"kind": "upgrade", "id": uid, "owned": u.max_level, "pouch": 1e30,
                                  "max": upgrade_max(eco, uid, u.max_level, 1e30), "case": "at max-level"})
        else:
            max_cases += [{"kind": "upgrade", "id": uid, "owned": lv, "pouch": p, "max": upgrade_max(eco, uid, lv, p),
                           "case": c} for lv, p, c in ((0, u.cost, "exact"), (0, _below(u.cost), "one short"),
                                                       (0, u.cost * 1000, "rich, one-time"),
                                                       (1, u.cost * 1000, "already bought"))]
        upgrades[uid] = entry
    recipes = {rid: {"cost": r.cost, "ingredients": dict(r.ingredients), "type": "cps-percent", "value": r.value,
                     "unlock-baked": r.unlock_baked} for rid, r in eco.recipes.items()}
    sample = State(owned={}, upgrades={})
    click = {"base-power": eco.base_click, "crit-chance": eco.crit_chance, "crit-multiplier": eco.crit_multiplier,
             "expected-factor": crit_factor(eco), "base-state": {
                 "click-power": click_power(eco, sample), "normal-click": click_value(eco, sample, False),
                 "crit-click": click_value(eco, sample, True), "expected-click": expected_click(eco, sample)}}
    states = []
    for name, result in results.items():
        # replay the purchases to get the state at a few checkpoints
        for t in (60, 600, 1800, 3600, 7200, 14400):
            snap = State(owned={}, upgrades={})
            for p in result.purchases:
                if p.time > t:
                    break
                if p.kind == "building":
                    snap.owned[p.id] = p.owned_after
                elif p.kind == "recipe":
                    snap.recipes.add(p.id)
                else:
                    snap.upgrades[p.id] = p.owned_after
            states.append({"profile": name, "time": t, "owned": dict(sorted(snap.owned.items())),
                           "upgrades": dict(sorted(snap.upgrades.items())),
                           "recipes": [rid for rid in eco.recipes if rid in snap.recipes],
                           "recipe-percent": recipe_percent(eco, snap),
                           "ingredient-rates": {ing: ingredient_rate(eco, snap, ing) for ing in eco.ingredients},
                           "total-cps": total_cps(eco, snap),
                           "click-power": click_power(eco, snap),
                           "normal-click": click_value(eco, snap, False), "crit-click": click_value(eco, snap, True),
                           "expected-click": expected_click(eco, snap)})
    production = {bid: [{"owned": n, "milestones-reached": milestones_reached(eco, bid, n),
                         "factor": milestone_factor(eco, bid, n),
                         "cps": building_cps(eco, State(owned={bid: n}, upgrades={}), bid)}
                        for n in MILESTONE_OWNED]
                  for bid in eco.buildings}
    book = next((uid for uid, u in eco.upgrades.items() if u.type == "recipes-unlock"), None)
    ingredient_rates = {}
    for bid, b in eco.buildings.items():
        if b.produces:
            rows = [{"owned": n, "recipe-book": False,
                     "rate": ingredient_rate(eco, State(owned={bid: n}, upgrades={}), b.produces[0])}
                    for n in (1, 10)]
            rows += [{"owned": n, "recipe-book": True,
                      "rate": ingredient_rate(eco, State(owned={bid: n}, upgrades={book: 1}), b.produces[0])}
                     for n in MILESTONE_OWNED]
            ingredient_rates[bid] = {"ingredient": b.produces[0], "per-second": b.produces[1], "rates": rows}
    return {"cost-growth": eco.cost_growth, "max-bulk": MAX_BULK, "milestones": list(eco.milestones),
            "building-milestones": {bid: list(milestones_of(eco, bid)) for bid in eco.buildings}, "click": click, "costs": costs,
            "building-cps": production, "upgrades": upgrades, "max-affordable": max_cases,
            "ingredients": list(eco.ingredients), "ingredient-rates": ingredient_rates, "recipes": recipes,
            "recipe-purchase": _recipe_cases(eco, book), "recipe-cps": _recipe_cps_cases(eco, book),
            "states": states}


def _recipe_cases(eco: Economy, book: str | None) -> list[dict]:
    """Affordability (all or nothing) at the boundaries of each recipe's cookie and ingredient costs."""
    cases = []
    for rid, r in eco.recipes.items():
        full = {ing: amount for ing, amount in r.ingredients}

        def add(label, pouch, stock, baked=r.unlock_baked, has_book=True, bought=False):
            st = State(pouch=float(pouch), baked=float(baked), upgrades={book: 1} if has_book else {},
                       ingredients=dict(stock), recipes={rid} if bought else set())
            cases.append({"recipe": rid, "case": label, "pouch": st.pouch, "baked": st.baked, "recipe-book": has_book,
                          "bought": bought, "stock": st.ingredients, "affordable": recipe_affordable(eco, st, rid)})
        add("exact", r.cost, full)
        add("one cookie short", _below(r.cost), full)
        for ing, amount in r.ingredients:
            add(f"one {ing} short", r.cost, {**full, ing: _below(amount)})
        add("no stock", r.cost * 10, {})
        add("no recipe book", r.cost, full, has_book=False)
        if r.unlock_baked > 0:
            add("below unlock", r.cost, full, baked=_below(r.unlock_baked))
        add("already bought", r.cost, full, bought=True)
    return cases


def _recipe_cps_cases(eco: Economy, book: str | None) -> list[dict]:
    """total_cps with buildings owned and 0, 1, some and all recipes bought."""
    owned = {bid: 10 for bid in eco.buildings}
    ids = list(eco.recipes)
    out = []
    for chosen in ([], ids[:1], ids[::2], ids):
        st = State(owned=dict(owned), upgrades={book: 1} if book else {}, recipes=set(chosen))
        out.append({"owned": owned, "recipes": chosen, "recipe-percent": recipe_percent(eco, st),
                    "total-cps": total_cps(eco, st)})
    return out


def _below(x: float) -> float:
    """One cookie short of x; above ~9e15 (ulp >= 2) x - 1 rounds back to x, so use the next double below."""
    x = float(x)                      # costs are Python ints; Java reads every pouch as a double
    return x - 1 if x - 1 < x else math.nextafter(x, 0)


def _above(x: float) -> float:
    """Half a cookie over x, or the next double above when x + 0.5 isn't representable."""
    x = float(x)
    return x + 0.5 if x + 0.5 > x else math.nextafter(x, math.inf)


def _max_cases(kind: str, item_id: str, cost, maxf, owned_list, max_level: int | None) -> list[dict]:
    """Max-affordable cases around the boundaries: exactly N, one cookie short of N, nothing, cap."""
    cases = []
    for n in owned_list:
        def add(pouch, label):
            cases.append({"kind": kind, "id": item_id, "owned": n, "pouch": pouch, "max": maxf(n, pouch),
                          "case": label})
        add(0, "empty pouch")
        add(_below(cost(n, 1)), "one short of 1")
        for k in (1, 2, 5, 10, 37):
            if max_level is not None and n + k > max_level:
                continue
            exact = cost(n, k)
            add(exact, f"exactly {k}")
            add(_below(exact), f"one short of {k}")
            add(_above(exact), f"just over {k}")
        if max_level is not None:
            add(cost(n, max_level - n) * 10, "capped by max-level")
        else:
            add(1e30, "huge pouch")
    return cases


def plot(results: dict[str, Result], img_dir: Path, stamp: str) -> list[str]:
    import matplotlib
    matplotlib.use("Agg")
    import matplotlib.pyplot as plt

    img_dir.mkdir(parents=True, exist_ok=True)
    names = []
    fig, ax = plt.subplots(figsize=(9, 4.5))
    for name, r in results.items():
        ax.plot([t / 60 for t, *_ in r.timeline], [max(c, 1e-3) for _, c, _, _ in r.timeline], label=name)
    ax.set(yscale="log", xlabel="minutes", ylabel="cookies / s (buildings)", title="CPS over time")
    ax.grid(alpha=0.3), ax.legend()
    fig.tight_layout(), fig.savefig(img_dir / f"{stamp}-cps.png", dpi=110), plt.close(fig)
    names.append(f"{stamp}-cps.png")

    fig, ax = plt.subplots(figsize=(9, 4.5))
    for name, r in results.items():
        times = [p.time for p in r.purchases]
        gaps = [b - a for a, b in zip(times, times[1:])]
        ax.plot([t / 60 for t in times[1:]], gaps, ".", ms=3, label=name)
    ax.set(xlabel="minutes", ylabel="seconds since previous purchase", title="Wait between purchases")
    ax.axhline(180, color="grey", ls="--", lw=0.8)
    ax.grid(alpha=0.3), ax.legend()
    fig.tight_layout(), fig.savefig(img_dir / f"{stamp}-gaps.png", dpi=110), plt.close(fig)
    names.append(f"{stamp}-gaps.png")

    # early game: the first 15 minutes, where the onboarding targets live
    fig, ax = plt.subplots(figsize=(9, 4.5))
    for name, r in results.items():
        times = [p.time for p in r.purchases if p.time <= 900]
        gaps = [b - a for a, b in zip(times, times[1:])]
        ax.plot([t / 60 for t in times[1:]], gaps, "o-", ms=3, lw=0.8, label=name)
    ax.set(xlabel="minutes", ylabel="seconds since previous purchase", xlim=(0, 15),
           title="Wait between purchases, first 15 minutes")
    ax.axhline(60, color="grey", ls="--", lw=0.8)
    ax.grid(alpha=0.3), ax.legend()
    fig.tight_layout(), fig.savefig(img_dir / f"{stamp}-early.png", dpi=110), plt.close(fig)
    names.append(f"{stamp}-early.png")
    return names


def write_report(eco: Economy, targets: dict, results: dict[str, Result], rows: list[dict], stamp: str) -> Path:
    images = plot(results, BALANCE_DIR / "img", stamp)
    passed = sum(r["ok"] for r in rows)
    out = [f"# Balance report {stamp}", "",
           f"Configs: `buildings.yml`, `upgrades.yml` · horizon {targets.get('horizon-minutes')} min · "
           f"targets: {passed}/{len(rows)} pass", "",
           f"Click: base power {eco.base_click:g}, crit {eco.crit_chance:.0%} × {eco.crit_multiplier:g} "
           f"→ expected {crit_factor(eco):g} × click power", "",
           "Milestones: ×2 per building at " + (", ".join(str(c) for c in eco.milestones) or "none") + " owned"
           + "".join(f"; {b}: " + (", ".join(map(str, eco.buildings[b].milestones)) or "none")
                     for b in eco.buildings if eco.buildings[b].milestones is not None), "", "## Targets", "",
           "| Target | Profile | Result | Limit | |", "|---|---|---|---|---|"]
    out += [f"| {r['id']} | {r['profile']} | {fmt_time(r['value'])} | {fmt_limit(r)} | "
            f"{'✅' if r['ok'] else '❌'} |" for r in rows]
    out += ["", "## Time to Nth building", ""]
    header = "| Building | " + " | ".join(f"{p} #{n}" for p in results for n in MILESTONES) + " |"
    out += [header, "|---|" + "---|" * (len(results) * len(MILESTONES))]
    for bid in eco.buildings:
        cells = [fmt_time(_nth(r, bid, n)) for r in results.values() for n in MILESTONES]
        out.append(f"| {bid} | " + " | ".join(cells) + " |")
    out += ["", "## Cookies baked", "", "| Baked | " + " | ".join(results) + " |", "|---|" + "---|" * len(results)]
    for cookies in BAKED_MARKS:
        out.append(f"| {cookies:,} | " + " | ".join(fmt_time(baked_time(r, cookies)) for r in results.values()) + " |")
    out += ["", "## Upgrades", "", "| Upgrade | " + " | ".join(results) + " |", "|---|" + "---|" * len(results)]
    for uid in eco.upgrades:
        cells = []
        for r in results.values():
            cell = fmt_time(_upgrade(r, uid))
            if eco.upgrades[uid].repeatable:
                cell += f" (level {r.final.level(uid)} at end)"
            cells.append(cell)
        out.append(f"| {uid} | " + " | ".join(cells) + " |")
    if eco.recipes:
        out += ["", "## Recipes", "",
                "Time bought, and in brackets the longest wait on ingredients alone (shown, producers running, "
                "stock short).", "", "| Recipe | Needs | +CPS | " + " | ".join(results) + " |",
                "|---|---|---|" + "---|" * len(results)]
        end = {name: len(r.baked) + 1 for name, r in results.items()}
        for rid, rec in eco.recipes.items():
            cells = []
            for name, r in results.items():
                start, ready = r.recipe_waits.get(rid, (None, None))
                wait = "" if start is None else f" ({fmt_time((ready or end[name]) - start)})"
                cells.append(fmt_time(first_time(r, "recipe", rid)) + wait)
            needs = ", ".join(f"{a:,g} {i}" for i, a in rec.ingredients) + f" + {rec.cost:,.0f} cookies"
            out.append(f"| {rid} | {needs} | {rec.value:.0%} | " + " | ".join(cells) + " |")
        out += ["", "| Ingredient stock at the end | " + " | ".join(results) + " |", "|---|" + "---|" * len(results)]
        for ing in eco.ingredients:
            out.append(f"| {ing} | " + " | ".join(f"{r.final.ingredients.get(ing, 0):,.0f} "
                                                  f"(+{ingredient_rate(eco, r.final, ing):,.1f}/s)"
                                                  for r in results.values()) + " |")
    out += ["", "## Per profile", "", "| Profile | Purchases | Longest dead zone (first 15 min) | Longest dead zone | "
            "Longest gap between purchases | Final CPS |", "|---|---|---|---|---|---|"]
    for name, r in results.items():
        times = [p.time for p in r.purchases]
        gap = max((b - a for a, b in zip(times, times[1:])), default=0)
        dead = max((length for _, length in r.dead_zones), default=0)
        out.append(f"| {name} | {len(r.purchases)} | {fmt_time(longest_dead_zone(r, 15))} | {fmt_time(dead)} | "
                   f"{fmt_time(gap)} | "
                   f"{total_cps(eco, r.final):,.1f} |")
    out += ["", "## First 15 minutes (purchases)", "", "| Profile | Purchases (time: id #owned) |", "|---|---|"]
    for name, r in results.items():
        early = [f"{fmt_time(p.time)}: {p.id} #{p.owned_after}" for p in r.purchases if p.time <= 900]
        out.append(f"| {name} | " + ", ".join(early) + " |")
    out += ["", "## Plots", ""] + [f"![{n}](img/{n})" for n in images] + [""]
    path = BALANCE_DIR / f"report-{stamp}.md"
    path.write_text("\n".join(out), encoding="utf-8")
    return path


def _nth(result: Result, bid: str, n: int) -> int | None:
    return first_time(result, "building", bid, n)


def _upgrade(result: Result, uid: str) -> int | None:
    return first_time(result, "upgrade", uid)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("mode", choices=["report", "check"])
    parser.add_argument("--config-dir", type=Path, default=DEFAULT_CONFIG_DIR)
    args = parser.parse_args()
    try:
        eco = load(args.config_dir)
        if eco is None:
            print(f"No balance config in {args.config_dir} yet (buildings.yml/upgrades.yml); nothing to check.")
            return 0
        targets = load_targets(TARGETS)
        horizon = int(float(targets.get("horizon-minutes", 240)) * 60)
        results = {p.name: simulate(eco, p, horizon) for p in profiles_from(targets)}
        rows = evaluate(eco, targets, results)
    except (ConfigError, KeyError, TypeError, ValueError, yaml.YAMLError) as e:
        print(f"Balance config error: {e}")
        return 1

    for r in rows:
        print(f"{'PASS' if r['ok'] else 'FAIL'}  {r['id']:<24} {r['profile']:<8} "
              f"{fmt_time(r['value']):>10}  ({fmt_limit(r)})")
    if args.mode == "report":
        stamp = datetime.date.today().isoformat()
        report = write_report(eco, targets, results, rows, stamp)
        golden = BALANCE_DIR / "golden-values.json"
        golden.write_text(json.dumps(golden_values(eco, results), indent=2) + "\n", encoding="utf-8")
        print(f"Wrote {report} and {golden}")
    failed = sum(not r["ok"] for r in rows)
    print(f"{len(rows) - failed}/{len(rows)} targets pass.")
    return 1 if failed and args.mode == "check" else 0


if __name__ == "__main__":
    sys.exit(main())
