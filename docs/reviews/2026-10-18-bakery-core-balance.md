# Review: bakery-core / balance-designer (2026-10-18)

Reviewer: Gandalf. Branch `bakery-core/balance-designer` (Stonks Stan), 5 commits. No Java in the branch.

## Target version
Paper 26.x (unaffected; the branch has only Python tools, YAML and docs).

## Results
| Check | Result |
|---|---|
| `mise run tools-test` | pass, 133 tests (20 new in `tools/balance/test_balance.py`) |
| `mise run balance-check` | pass, 6/6 targets |
| `mise run check` | BUILD SUCCESS |
| `ownership-check bakery-core/balance-designer` | OK, 5 commits inside Stan's files |

## Review against the brief
- Targets in `docs/balance/targets.md` match the user's three: first Villager Baker <= 20 s (casual 12 s, active 5 s), first Wheat Farm <= 3 min (1m58s / 49 s), no dead zone > 60 s in the first 15 min (25 s / 18 s). Targets were not loosened.
- Formulas in `tools/balance/model.py` match `model.md`: expected click = power x (1 + p(m-1)); a crit multiplies the whole click including the cps-fraction part; chance in [0,1]; multiplier >= 1; missing = no crits.
- `buildings.yml` (50/0.5, 500/4, growth 1.15) and `upgrades.yml` (base-power 1, crit 0.05 x 10) match the brief's contract keys `click.base-power`, `click.crit-chance`, `click.crit-multiplier`.
- `golden-values.json` has `click` (base-power, crit-chance, crit-multiplier, expected-factor 1.45, base-state) and per-state `click-power`, `normal-click`, `crit-click`, `expected-click`; costs for 0-100 owned and buy-10 from 0/10/50. Regenerates identically from the configs (tested).
- Plots look sane: CPS curves are monotone and converge; early-game sawtooth stays below the 60 s line for casual/active; idle shows its 500 s wait for the first Wheat Farm (not a target).

## Tests written (`tools/balance/test_balance.py`)
crit_factor 1.45; crit multiplies the cps-fraction part; missing crit fields = no crits; chance 1.5 and -0.1 and multiplier 0.5 raise ConfigError; boundary values (1, 1) accepted; empty `upgrades: {}` valid; `longest_dead_zone` [(10,30),(850,200),(1000,500)] = 500 unwindowed, 51 with a 15 min window, empty and out-of-window cases; CLI exit 0 on real configs, 1 on a config that violates a target (base-cost 5000), 1 on invalid config, `check` writes nothing; golden values regenerate identically, click keys present, snapshot expected-click = power x 1.45, costs match the formula.

## Findings
1. (minor, for whoever owns `mise.toml`) `tools-test` has no `depends = ["assets-deps"]`. The balance tests import PyYAML and matplotlib-free `main.py`; on a fresh checkout `mise run tools-test` fails with `No module named 'yaml'` until `assets-deps` has run. CI is fine only because earlier steps install the deps. Suggest adding the dependency.
2. (note, not a bug) Only 2 buildings: the economy flattens at about 208 CPS and "Nth building" for #50+ is "never" within 4 h. Expected for the bakery-core slice; revisit when more buildings land.
3. (note) `main.py` imports `model` and `sim` by bare name via a `sys.path` insert, so `balance.main` and `balance.model` are separate module objects (distinct `ConfigError` classes). The tests keep the two apart; harmless for the CLI.

## Architecture
No Java here; nothing to update in `docs/architecture.md`.

VERDICT: READY
