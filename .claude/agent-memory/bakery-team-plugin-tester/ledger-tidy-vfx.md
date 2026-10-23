---
name: ledger-tidy-vfx
description: tidy vfx split branch review (2026-10-23, tidy/plugin-tester-vfx at 4a6d3b1): READY; parser equivalence probe recipe; gradient = VirtualComponent identity-equals; open LOWs
metadata:
  type: project
---

Reviewed `tidy/vfx-designer` (949980f, 9e2d433) merged into feature/tidy (b09a659) as `tidy/plugin-tester-vfx`. Verdict READY; report `docs/reviews/2026-10-23-tidy-vfx.md`. Tests 380 green (375 + 5 added). Re-review with `git diff 4a6d3b1..HEAD -- src/main/java/dev/casperrs/cookieClicker/vfx src/main/resources/effects.yml`.

- Verified: the parser split is output-identical to feature/tidy (EffectsConfig.toString, 16 inputs + defaults). Recipe: copy the old file from a `git archive feature/tidy -o <scratch>.tar` extract, `sed` the class name to `OldEffectsConfigParser` into the same test package, assert `toString()` equal (strip `@hash`: ShapePlan prints an identity hash). Delete afterwards.
- Verified: V1 fast path for `cookie_click` (`<#f5c542>+<amount>`); V2 string only on warn; V3 LoopJob checks only on play ticks; V4 DisplayJob per-entity next-keyframe index (needs tick-ordered tracks; Motions builds them in order).
- Adventure fact (stable for pom's Adventure 5): a `<gradient>` produces `VirtualComponentImpl`, which does not override equals (identity). MarkerTemplate's probe rejects it, so such templates always parse. Compare rendered trees via `toString()` minus `@hash` in tests.
- Public Effects API: all feature/tidy signatures kept; new overload `loop(id, loc, viewer, int startDelay, Runnable)`.
- Open LOWs (follow-ups): DropJob callback tests trimmed (2 of 3 not restored), Effects 17 fields and BlockDrops/Loops back-reference to Effects, `List<int[]>` in DisplayJob, DisplayTrack tick-order not enforced, cookie_crit parses on each crit (gradient).
- Loop termination after a viewer quits/changes world now waits for the next play tick (no packets sent in the gap).
- Sandbox: `mise run test` output is ~250 KB; grep `Tests run:` / `FAIL` from a redirected file or the tail. Compound shell commands containing `git` are refused in this worktree, so run git as plain single commands.
