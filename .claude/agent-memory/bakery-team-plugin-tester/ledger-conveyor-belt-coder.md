---
name: ledger-conveyor-belt-coder
description: conveyor-belt plugin-coder review verified at 8da4215 (+tests 0335cea); open LOWs, integration to-dos, test recipes
metadata:
  type: project
---
Verified 2026-10-21, branch conveyor-belt/plugin-coder (worktree .claude/worktrees/agent-aeb62ec2471d07818), paper 26.3. VERDICT READY.
- Passed: camera game-mode safety (PDC first, removed after restore; all abort paths), threading, entity cleanup, per-player visibility, config defaults (sound-every 100), no playSound/spawnParticle outside services. Re-review: `git diff 8da4215..HEAD -- src/main/java/dev/casperrs/cookieClicker/{belt,house,config}`.
- Open LOW: complete() camera.exit unguarded; onLost ignores abort()==false; missing return world; per-tick List.copyOf in ConveyorBelts.tick; surfaces for empty plots.
- Integration to-do: BeltStyleTest.theShippedBeltFileParsesWhenPresent un-skips once belt.yml merged; EffectsConfigParserTest/HouseEffectsConfigTest need Larry's `min-interval-ticks` engine (feature branch) - verified red only on the coder branch with feature resources copied in.
- Recipe: to test shipped assets from another branch, `git show feature/x:path > worktree/path`, run tests, `git checkout -- src/main/resources` AND rm untracked copies (belt.yml is new, checkout does not remove it).
- Tests: no MockBukkit in pom; camera/entity logic covered only by source-level guards (HouseCinematicsCameraSourceTest, brace-matched method bodies).
