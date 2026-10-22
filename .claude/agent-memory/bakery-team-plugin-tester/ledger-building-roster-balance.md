---
name: ledger-building-roster-balance
description: building-roster balance tooling (tools/balance model/sim/main, milestones + per-building override, baked/min-seconds targets) reviewed at b7613e3; tool-test recipes
metadata:
  type: project
---

Reviewed 2026-10-22: branch `building-roster/balance-designer`, Stan's commits up to 929cb87, my tests at b7613e3 (READY-candidate, see report).

- `tools-test` = 146 tests, 1 env skip (was 134). `balance-check` 19/19 at 929cb87.
- Pacing at the committed HEAD: first-wheat-farm casual 2m14s. Earlier runs showed 1m42s while Stan's WIP was live in the worktree; not a hash-seed effect (PYTHONHASHSEED 0..8 gives identical md5 of check output at 929cb87).
- Shell `python` resolves to the MAIN checkout's `.venv`; `mise run` in a worktree uses the worktree's `.venv`. Both Python 3.12.15 here.
- Milestone factor: `2 ** milestones_reached(bid, owned)`; per-building `milestones:` replaces the top-level list; `[]` = none; null is treated as none (not fallback) – doc says "absent falls back".
- Buyer (`sim.best`): push = need copies when 2 <= need <= MILESTONE_LOOKAHEAD (5); simulate buys one copy at a time with per-copy cost.
- Target semantics: never-reached passes only min-only targets. Two rebirth targets are min-only, so a too-slow 1B curve passes vacuously; suggest max-seconds at horizon (14400).
- Tests added: MilestoneTest, MilestonePushTest (uses `sim.best` with duck-typed balance.model objects; works), BakedAndMinSecondsTest (raises `bm.ConfigError`, not `model.ConfigError`: evaluate() uses the top-level `model` module).
- CLI mutation recipe: scale every `base-cost: N` with a regex and format `:.1f` (`:g` gives `2e+09`, which the parser rejects as a config error, not a FAIL).
- Open LOW: main.py line 188 is 129 chars.
