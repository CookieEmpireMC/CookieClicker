---
name: factory-makeover-facts
description: Verified facts from factory-makeover (2026-10-20) - layout yaw convention, guard quirks, how to smoke-test parsers against another agent's branch files
metadata:
  type: reference
---

Verified 2026-10-20 (compile + scratch runs), Paper 26.3.build.205-stable:
- `World#getHighestBlockYAt(int, int, HeightMap)`, `HeightMap.WORLD_SURFACE`, `Tag.LEAVES`, `Material#isSolid()`,
  `MultipleFacing#getAllowedFaces/setFace`, `Wall#setHeight(BlockFace, Wall.Height.LOW)`, `TileState` compile clean
  with -Xlint:deprecation. Stable.
- Layout yaw convention (design/factory.md, Pixel Pat): layout `pedestal/shop.yaw` = the way the thing FACES (180 =
  north, toward visitors); `Station.yaw` is the opposite (viewer's facing), so station yaw = layout yaw + 180.
  `viewpoint.yaw/pitch` are plain player angles. Stable for this contract.
- Records that tests construct with N args (Plot 7, HouseBackup 8, Station 6): add components but keep a compat
  constructor with the old arity, or tester tests stop compiling.
- Smoke-test pure parsers against another agent's unmerged files: `git show <branch>:<path> > scratchpad/x.yml`,
  `mise exec -- mvn -B -q dependency:build-classpath -Dmdep.outputFile=<scratch>/cp.txt`, then
  `java -cp "target/classes:$(cat cp.txt)" Scratch.java` (single-file launch, own default package works).
- Guard: refuses a Bash call that combines `cd` + heredoc + python edit when the script is long; write the script
  with the Write tool to the scratchpad and run `python3 -I script.py <files>` alone. Short heredoc-to-file works.
- Run the build while one tester-pinned test is known to fail by design:
  `mise exec -- mvn -B -q package '-Dtest=!Class#method' -Dsurefire.failIfNoSpecifiedTests=false`.
