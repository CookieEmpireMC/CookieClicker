---
name: ledger-building-roster-world
description: building-roster plugin-coder (world part) branch review at c11df8d (+ Gandalf tests f97cfe5): VisibleBuildings per-owner visuals, BuildingSlots parser, BuildingLayout maths, footprint keep-out LOW; re-review range
metadata:
  type: project
---
Verified 2026-10-22 on branch building-roster/plugin-coder-world (coder head c11df8d, Gandalf tests f97cfe5). check-quiet 666 green, 1 skip (shipped building-slots test; passes when the feature-branch file is copied in: 4/4). Verdict READY, no HIGH/MEDIUM.

- Passed: per-owner visibility (setVisibleByDefault(false) set inside the spawn consumer, then owner.showEntity only); Effects.play with a viewer is personal (vfx Effects javadoc); sound is owner-only (SoundService.play(player,…)). Origin round trip: PlotSetup house box = design.toWorld(houseAt) so BuildingLayout.origin inverts it; only PlotSetup writes the house box and its setup callback calls buildings.plotChanged. Removal on leave, quit, onUnloaded, plotChanged, pack swap, reload, shutdown; chunk unload removes the owner's view. No duplicates on re-entry (fresh view per enter, loud=false). No effects on join/reload/pack/plotChanged. Everything main thread; runTaskLater checks isOnline.
- LOW: BuildingSlots.parse reserves a cell in `used` before the over-cap check, so an 11th cell blocks the same cell for another type with a false "used twice" warning (no test; fix: check the cap before used.add).
- LOW: keep-out covers only the slot cell; design/buildings-world.md footprints are 3x3 for most types (oven x26..28, belt/shipping/alchemy 3 wide). Shipped decor avoids them (generator asserts), so no current overlap.
- LOW: spawn returns null on an unloaded chunk → `owned` is recorded anyway, the rest waits for the next change (no retry). Rare (plot chunks load with the player).
- PERF-LOW: ×Max buy crossing 25/100 for all six types = up to 120 personal effect calls over 240 ticks (2-tick stagger); vfx caps (8 display entities per viewer, 200 particles per effect) absorb most. Fix: cap effects per purchase (≈12), rest silent.
- PERF-LOW: hidden displays per owner up to 19 with shipped slots (8+4+2+2+2+1); N players in one plot = N×19 tracked entities at one location. Estimate ≤0.1–0.3 ms/tick for 1000 hidden displays (unmeasured). Profile with spark in a 30-player plot.
- Open for integration: model/sound/effect IDs (building_world/<id>, building_appear/milestone sounds+effects) must exist; in-game alignment of ItemDisplay NONE bottom at model y 8.
- Re-review: `git diff f97cfe5..HEAD -- src/main/java/dev/casperrs/cookieClicker/building src/main/java/dev/casperrs/cookieClicker/house src/main/java/dev/casperrs/cookieClicker/config/BuildingVisualSettings.java`.
- Recipe: a shipped file from the feature branch: `git show feature/x:path > worktree/path`, `mise exec -- mvn -q -Dtest=A -Dsurefire.failIfNoSpecifiedTests=false test`, then rm the copy (worktree must stay clean).
