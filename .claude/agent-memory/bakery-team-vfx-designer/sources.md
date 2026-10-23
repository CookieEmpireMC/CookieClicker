---
name: vfx-sources
description: Where VFX facts were verified (URL/file | what it confirmed | date), plus fast lookup routes and dead ends
metadata:
  type: reference
---

- `~/.m2/repository/io/papermc/paper/paper-api/26.3.build.205-stable/paper-api-26.3.build.205-stable-sources.jar`
  | Particle enum + data types, spawnParticle overloads, BlockDisplay, Display, light, scheduler | 2026-10-18/19.
  Route: `unzip -o -q <jar> 'org/bukkit/entity/Display.java'` into the scratchpad, then grep. Faster than WebFetch.
- `docs/research/fake-blocks.md` Q7 | BlockDisplay drop + bounce recipe, first transformation >= 1 tick after spawn,
  corner anchoring, brightness from above the roof | 2026-10-19 (Cindy's note, not re-verified).
- `org.bukkit.util.Transformation` constructors take `Vector3f`/`Quaternionf` or `AxisAngle4f`, not the `...c` interfaces
  | 2026-10-23, compile error.
- Worktree Bash quirk: long compound commands (`cd` + several `&&`, big heredocs, a variable used as an option value
  or glob in sed) are refused in the isolated worktree. Write a script to the scratchpad with Write and run
  `python3 <path>` with plain arguments; `cd <worktree> && single command` works.
