---
name: test only the important parts
description: user rule (2026-10-23): keep the suite to what matters; keep/drop rules for test pruning
metadata:
  type: feedback
---

test only the important parts (user, 2026-10-23). "There's no need for 600 tests and covering 100%."

**Keep:** economy formulas + golden values; save/load + schema upgrades (JdbcPlayerStore); shipped-file load tests and ID/asset/sound/effect contracts (ShippedContentConsistencyTest); ArchitectureRulesTest; permissions/commands; thread rules; panel geometry pins; TidyPerfTest timing; ownership-check tool tests; one regression per real bug; safety paths (game mode restore, undo/backup, keep-out, operation gate).

**Drop:** getters/records/constructors, duplicates, tests of private helpers or source-text guards that pin private method names (they break on refactors), long case tables (keep 1-2 cases), config tests that only pin internal knobs (the config part moves them to code).

**Why:** user wants a lean suite; token cost and refactor churn. **How to apply:** when writing or reviewing tests, default to fewer, value-based cases; aim roughly half of an existing suite when asked to prune. Result on 2026-10-23: 582 -> 301 Java tests outside vfx (405 with vfx), tool tests 146 -> 73.
