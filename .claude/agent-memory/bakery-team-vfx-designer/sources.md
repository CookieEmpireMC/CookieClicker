---
name: vfx-sources
description: Where VFX facts were verified (URL/file | what it confirmed | date), plus fast lookup routes and dead ends
metadata:
  type: reference
---

- `~/.m2/repository/io/papermc/paper/paper-api/26.3.build.205-stable/paper-api-26.3.build.205-stable-sources.jar`
  | Particle enum + data types, Player/World spawnParticle overloads, BlockDisplay, Display, Block light, scheduler
  | 2026-10-18/19. Route: `unzip -o -q <jar> 'org/bukkit/entity/Display.java'` into the scratchpad, then grep.
  Faster than WebFetch on jd.papermc.io; no web lookups were needed this run.
- `docs/research/fake-blocks.md` Q7 | BlockDisplay drop + bounce recipe, first transformation >= 1 tick after spawn,
  corner anchoring, brightness from above the roof | 2026-10-19 (Cindy's note, not re-verified by me).
- Dead end: compound Bash commands (cd + heredoc + build in one call) are refused in the isolated worktree; write
  helper scripts to the scratchpad with Write and run them with plain single commands.

- Re-confirmed 2026-10-20: even `python3 - <<EOF` with `cd ... &&` is refused; Write a .py to the scratchpad (chdir
  inside it) and run `python3 <path>`; use `mise -C <worktree> run ...` and `git -C <worktree> ...`.

Related: [[reference-paper263-vfx-api]]
