---
name: test only the important parts
description: user rule (2026-10-23): keep the suite to what matters; keep/drop rules for tests
metadata:
  type: feedback
---

Test only the important parts (user, 2026-10-23): "There's no need for 600 tests and covering 100%."

**Keep:** economy formulas + golden values; save/load + schema upgrades (`JdbcPlayerStore`); shipped-file load tests and ID/asset/sound/effect contracts (`ShippedContentConsistencyTest`); `ArchitectureRulesTest`; permissions/commands (`PluginDescriptorTest`); thread rules; panel geometry pins; `TidyPerfTest`; ownership-check tool tests; one regression per real bug; safety paths (game mode restore, undo/backup, keep-out, operation gate).

**Drop:** getters/records/constructors, duplicates, tests of private helpers or source-text guards that pin private names, long case tables (keep 1-2 cases), config tests that only pin internal knobs.

**Why:** lean suite; token cost and refactor churn. **How to apply:** default to few, value-based cases; when asked to prune aim at about half. Outcome: 686 -> 392 Java tests (vfx included), tools 146 -> 73.
