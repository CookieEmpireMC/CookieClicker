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

Related: [[bakery-core-balance]]
