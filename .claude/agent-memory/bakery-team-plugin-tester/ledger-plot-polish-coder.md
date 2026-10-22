---
name: ledger-plot-polish-coder
description: plot-polish plugin-coder (plot part) branch review at c636c4e..1c53b6b..740f760 (+tests f57fe49): decor parser/keep-out, LoopPacer, belt cookie pose; open LOWs and test recipes
metadata:
  type: project
---
Verified 2026-10-22, branch plot-polish/plugin-coder-plot (worktree .claude/worktrees/agent-abd03090ebf8af4f8), coder head dae2d0d, Gandalf tests d9fba1d + docs f57fe49. check-quiet 637 run, 2 skipped (shipped-decor tests, file not on branch). Shipped decor.yml from feature/plot-polish (360 blocks) copied in temporarily: 3/3 + 3/3 green, file removed again.

- Passed: Decor.parse (bad/dup/range/not-a-list), DecorKeepOut zones on factory.yml (fence ring, factory box with MARGIN 1 = x5..25 z9..24, front zone x12..23 z1..8 after ring clip), shipped decor avoids every keep-out cell.
- Passed: LoopPacer (refused inside period, restart keeps beat via delay(), forget per player), ChimneySmoke timers cancelled on leave (smoke.stop in onLeavePlot) and on quit; ConveyorBelts.onQuit calls forgetLoops (listener always registered in start()).
- Passed: backup/undo: decor goes through change() into `cells`, undo = restoreCells, so it is covered. Reads fall back to plugin.getResource("houses/decor.yml"). Tile entities skipped. setBlockData(…, false).
- Belt: cookieTransform bottom = RUBBER (flat 90 and upright 0), face normal vertical at 90 (test in BeltPathTest). Surface +PI turn is NOT provable in code: in-game check only.
- LOW for coder: (1) remove deprecated Chimney.crackleEvery() shim, no test uses it now; (2) decor uses one height y0 for all columns while setup accepts ground variance up to 2 (house does the same; fence is per column): decor can float/bury on uneven ground, fix like FenceLayout per-column ground; (3) DecorKeepOut Javadoc says walk is "one column wide", code is the bounding box of gate and door (conservative); (4) acceptance wording "the parser rejects" is implemented in PlotSetup via keepOut.allowed (logged, skipped): fine, say so in the PR; (5) vanilla fallbacks of the 3 loops (beehive.work, campfire.crackle, furnace.fire_crackle) are not the file lengths: seamless timing only with our pack; (6) an admin who set belt.surface-yaw 180 as the old workaround now gets it reversed.
- Recipe: Transformation corner check = `new Matrix4f().translation(t.getTranslation()).rotate(t.getLeftRotation()).scale(t.getScale())`, then transformPosition on the 8 corners; transformDirection keeps the scale: normalise.
- Recipe: test a shipped file from the feature branch: `git show feature/x:path > worktree/path`, `mise exec -- mvn -q -Dtest=A,B -Dsurefire.failIfNoSpecifiedTests=false test`, check target/surefire-reports XML for skipped="0", then `rm` the copy.
