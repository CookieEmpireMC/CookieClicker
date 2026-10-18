"""Balance simulator CLI.

  python tools/balance/main.py report [--config-dir DIR]   # mise run balance
  python tools/balance/main.py check  [--config-dir DIR]   # mise run balance-check

`report` simulates every profile from docs/balance/targets.md against the real configs
(src/main/resources/buildings.yml + upgrades.yml), writes docs/balance/report-<date>.md with
plots in docs/balance/img/, and regenerates docs/balance/golden-values.json for the tester.
`check` runs the same simulation, writes nothing, and exits 1 if any target fails.
"""
import argparse
import datetime
import json
import re
import sys
from pathlib import Path

import yaml

sys.path.insert(0, str(Path(__file__).parent))
from model import (ConfigError, Economy, State, bulk_cost, click_power, click_value, crit_factor,  # noqa: E402
                   expected_click, load, total_cps)
from sim import Profile, Result, simulate  # noqa: E402

TARGETS = Path("docs/balance/targets.md")
BALANCE_DIR = Path("docs/balance")
DEFAULT_CONFIG_DIR = Path("src/main/resources")
MILESTONES = [1, 10, 25, 50, 100]


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
    raise ConfigError(f"unknown metric '{kind}' in target {target.get('id')}")


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
            limit = float(target["max-seconds"])
            rows.append({"id": target["id"], "description": target.get("description", ""), "profile": name,
                         "value": value, "max": limit, "ok": value is not None and value <= limit})
    return rows


def fmt_time(seconds: float | None) -> str:
    if seconds is None:
        return "never"
    seconds = int(seconds)
    h, rem = divmod(seconds, 3600)
    m, s = divmod(rem, 60)
    return f"{h}h{m:02d}m{s:02d}s" if h else (f"{m}m{s:02d}s" if m else f"{s}s")


def golden_values(eco: Economy, results: dict[str, Result]) -> dict:
    """Deterministic expected values the Java economy must reproduce exactly."""
    costs = {bid: {"single": [bulk_cost(eco, bid, n, 1) for n in range(0, 101)],
                   "buy-10-from": {str(n): bulk_cost(eco, bid, n, 10) for n in (0, 10, 50)}}
             for bid in eco.buildings}
    sample = State(owned={}, upgrades=set())
    click = {"base-power": eco.base_click, "crit-chance": eco.crit_chance, "crit-multiplier": eco.crit_multiplier,
             "expected-factor": crit_factor(eco), "base-state": {
                 "click-power": click_power(eco, sample), "normal-click": click_value(eco, sample, False),
                 "crit-click": click_value(eco, sample, True), "expected-click": expected_click(eco, sample)}}
    states = []
    for name, result in results.items():
        # replay the purchases to get the state at a few checkpoints
        for t in (60, 600, 1800, 3600, 7200):
            snap = State(owned={}, upgrades=set())
            for p in result.purchases:
                if p.time > t:
                    break
                if p.kind == "building":
                    snap.owned[p.id] = p.owned_after
                else:
                    snap.upgrades.add(p.id)
            states.append({"profile": name, "time": t, "owned": dict(sorted(snap.owned.items())),
                           "upgrades": sorted(snap.upgrades), "total-cps": total_cps(eco, snap),
                           "click-power": click_power(eco, snap),
                           "normal-click": click_value(eco, snap, False), "crit-click": click_value(eco, snap, True),
                           "expected-click": expected_click(eco, snap)})
    return {"cost-growth": eco.cost_growth, "click": click, "costs": costs, "states": states}


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
           f"→ expected {crit_factor(eco):g} × click power", "", "## Targets", "",
           "| Target | Profile | Result | Limit | |", "|---|---|---|---|---|"]
    out += [f"| {r['id']} | {r['profile']} | {fmt_time(r['value'])} | {fmt_time(r['max'])} | "
            f"{'✅' if r['ok'] else '❌'} |" for r in rows]
    out += ["", "## Time to Nth building", ""]
    header = "| Building | " + " | ".join(f"{p} #{n}" for p in results for n in MILESTONES) + " |"
    out += [header, "|---|" + "---|" * (len(results) * len(MILESTONES))]
    for bid in eco.buildings:
        cells = [fmt_time(_nth(r, bid, n)) for r in results.values() for n in MILESTONES]
        out.append(f"| {bid} | " + " | ".join(cells) + " |")
    out += ["", "## Upgrades", "", "| Upgrade | " + " | ".join(results) + " |", "|---|" + "---|" * len(results)]
    for uid in eco.upgrades:
        out.append(f"| {uid} | " + " | ".join(fmt_time(_upgrade(r, uid)) for r in results.values()) + " |")
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
              f"{fmt_time(r['value']):>10}  (max {fmt_time(r['max'])})")
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
