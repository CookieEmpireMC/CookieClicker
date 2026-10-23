---
name: balance-tooling-facts
description: Verified gotchas for the balance tooling: tester-owned tests, worktree-guard-safe commands, sweep approach, report overwrite, golden doubles
metadata:
  type: project
---

- `tools/balance/test_*.py` belongs to plugin-tester (Gandalf), even though `tools/balance/` is ours; the ownership hook blocks it. Ask for simulator tests under REQUESTS; ad-hoc checks go in the scratchpad. (stable, ownership.json)
- Worktree isolation guard (verified 2026-10-18..23): it refuses as "names git" any Bash command that combines the worktree path (contains "GitHub") or `git` with `cd`, `&&` chains, `for` loops, `$(…)`, `>>` or heredocs into `.claude/`. Run git commands as single plain commands, use relative paths from the worktree cwd, write files with Write/Edit.
- Lever sweeps: write a Python script in the scratchpad (Write tool) that copies the configs, edits one value (pass overrides as argv) and runs `main.py check --config-dir <dir>` (writes nothing). One plain command per sweep.
- `main.py report` overwrites `report-<date>.md`: add hand-written notes *after* the final run, then commit without rerunning.
- Tool tests: `python -m unittest discover -s tools -p 'test_*.py'` (no pytest in the venv; `-s tools/balance` fails on the `balance` package import). (stable, 2026-10-22)
- Golden values must be representable doubles (`math.ceil` gives Python ints; `int - 1` above 2^53 isn't a double); `main.py` `_below`/`_above` handle it with `math.nextafter`. After Max/maths edits verify with `mise run check-quiet` (Java GoldenValuesTest), not only tools-test. (2026-10-22)

Related: [[balance-open-items]]
