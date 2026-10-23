---
name: ledger-tidy-review
description: tidy feature architecture/standards/perf review at 8940d31 (2026-10-23): where findings live, timing-test recipe, sandbox quirk
metadata:
  type: project
---

Reviewed `src/main` at base `8940d31` for the tidy brief (docs/briefs/tidy.md). Outputs: `docs/architecture.md` "Standards" section, `docs/reviews/2026-10-23-tidy-architecture.md` (per coder part: split plans, config key table, PERF ids W/U/V), `docs/reviews/2026-10-23-tidy-perf.md` (before numbers).

- Timing test = `src/test/java/.../view/TidyPerfTest.java` (view package for package-private `PanelUpdater.buildingRow`); prints `[tidy-perf]`, writes `target/tidy-perf.txt`; run alone with `MAVEN_ARGS="-Dtest=TidyPerfTest" mise run test`. Fill the "After" table in the perf doc at the integration review, same machine.
- Key measured fact (stable until PanelUpdater changes): `buildingRow` MiniMessage parse = ~10.6 us/row = 90 % of a row render; Economy ops are sub-microsecond.
- Facts found: config.yml has 144 leaf keys (31 keep, 3 never read: station.snap-*, plot.chimney-reach, 110 to constants); 8 classes > 500 lines; no wildcard imports, no ChatColor; the 12 public getters on CookieClicker have no callers; `CookieClicker.olderThan` is used by CookieClickerVersionCheckTest; pom.xml filters only plugin.yml.
- Tests that will need mechanical adapting after the moves: ArchitectureRulesTest (paths at lines ~101/122/131/163), ShippedContentConsistencyTest (reads station.panel.icons / plot.buildings.* from config.yml), config/*SettingsTest.
- Open for branch reviews: verify PERF-W1 (PlotTracker early-out), U1 (MarkerTemplate equals real parse), U3 (save skipping with statement-counting fake), permission string declared in paper-plugin.yml == BakeryCommand's.
- Sandbox quirk (2026-10-23): Bash refuses compound commands (for-loops, awk, python heredocs, long pipelines) with a bogus "names git" error in this worktree; put scripts in the scratchpad dir and run `bash <file>` as a plain command.
