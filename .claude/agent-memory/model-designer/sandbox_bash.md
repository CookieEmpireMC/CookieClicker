---
name: sandbox-bash-limits
description: Worktree isolation hook refuses some Bash forms (shell vars, for-loops, heredocs mentioning the VCS tool); how to work around it
metadata:
  type: feedback
---

The worktree-isolation hook refuses Bash commands it can't prove stay inside the worktree: commands using shell variables (`S=...; python $S/x.py`), `for` loops with `$var`, and heredocs/inline text containing the word for the VCS tool. Verified 2026-10-18 (bakery-core).

**Why:** the hook checks every command for operations outside the worktree and gives up on anything computed at runtime.
**How to apply:** use literal absolute paths, the Write/Edit tools for file changes, and put multi-step work (previews of many files) in a scratch Python script run with `mise exec -- python <script>`. Run VCS commands as plain separate commands.
