---
name: balance-tooling-facts
description: Verified facts about the balance tooling and ownership boundaries (test files, isolation-safe commands, sweep script approach)
metadata:
  type: project
---

- `tools/balance/test_*.py` belongs to plugin-tester (Gandalf), even though `tools/balance/` is ours. The ownership hook blocks it. Ask for simulator tests under REQUESTS.
  **Why:** ownership.json carves test files out to the tester. **How to apply:** never write tests yourself. Ad-hoc checks go in the scratchpad.
- The worktree isolation guard rejects compound bash (shell functions, `&&` chains with git + heredocs). Run git commits as plain separate commands. For lever sweeps, write a Python script in the scratchpad that copies the configs, edits one value and runs `main.py check --config-dir <dir>` (writes nothing).
- `main.py report` overwrites `report-<date>.md`. Add hand-written notes *after* the final run, then commit without rerunning.
- As of 2026-10-18, `longest-dead-zone` accepts `window-minutes`, and the report has an early (first 15 min) gaps plot.
- (2026-10-21) The guard also refuses `for` loops over a script and heredocs written into `.claude/` paths; run each sweep as its own plain command and write memory with the Write/Edit tools.
- (2026-10-21) `test_balance.py::test_empty_upgrades_map_is_valid` asserts the shipped upgrades.yml is empty; it breaks once real upgrades ship (ask the tester to update it).
- (2026-10-22) Bash commands containing the worktree path (it has "GitHub" in it) together with `cd`, `$(…)` or `>>` appends are refused as "names git"; use relative paths from the worktree cwd, write scratch scripts with Write, and pass options as argv (scratchpad explore.py took `b:buildings.x.base-cost=N` overrides + `dump:a,b,profile`).
- (2026-10-22) Tool tests run with `python -m unittest discover -s tools -p 'test_*.py'` (no pytest in the venv; `-s tools/balance` fails on the `balance` package import).
- (2026-10-22) Since building-roster: `baked` metric, `min-seconds` on targets, milestone-lookahead buyer (MILESTONE_LOOKAHEAD = 5, copies bought one by one), golden `building-cps`.
- (2026-10-22) Golden pouches must be representable doubles: costs from `math.ceil` are Python ints, and `int - 1` above 2^53 isn't a double (Java reads it rounded). `main.py` `_below`/`_above` convert to float and use `math.nextafter` when ±1/±0.5 rounds back. Max/maths edits: verify with `mise run check-quiet` (Java GoldenValuesTest), not only tools-test.

Related: [[bakery-core-balance]]
