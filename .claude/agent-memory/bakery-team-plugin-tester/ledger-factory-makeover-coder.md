---
name: ledger-factory-makeover-coder
description: factory-makeover plugin-coder branch review at 55dbac1 (PlotSetup, FenceLayout, layout parser); what passed, open LOWs
metadata:
  type: project
---
Verified 2026-10-20 at 55dbac1 (+ Gandalf test commit on factory-makeover/plugin-coder): PlotSetup, FenceLayout/FenceStyle/HouseLayout/SetupBackup, PlotCodec keys, parser list points. READY. check + validate-assets green, 485 tests.
- Re-review: `git diff 55dbac1..HEAD -- src/main/java/dev/casperrs/cookieClicker/house src/main/java/dev/casperrs/cookieClicker/plot`.
- Open LOWs: stale backup after unexpected exception post-restore; polygon region footprint; tree trunk gives "not flat"; ChimneySmoke Session.active() with one-shot tasks.
- Tests use fixture copies `src/test/resources/fixtures/factory-makeover-*.yml`; `HouseDesignPointsTest.theShippedFactoryAndCottageParseWhenPresent` is skipped on the branch, must run (not skip) at integration.
- FenceLayout facts: posts every n counted from minX/minZ, a post adjacent to a corner/pillar becomes a rail; gate left = east when entrance is on the north side.
