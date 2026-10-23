---
name: sandbox-bash-limits
description: Worktree isolation hook refuses some Bash forms (shell vars, for-loops, heredocs); how to work around it
metadata:
  type: feedback
---

The worktree-isolation hook refuses Bash it can't prove stays inside the worktree (verified 2026-10-18, 2026-10-20, 2026-10-21): shell variables, `for` loops with `$var`, heredocs containing the VCS tool's name, `python3 - <<'EOF'` multi-line replacements, and `cat > x.py <<'EOF' ... && python x.py` in one command. Plain `cat >> file <<'EOF'` appends work.

**How to apply:** literal absolute paths; Write/Edit for file changes; multi-step work in a scratch Python script run with `mise exec -- python <script>`; VCS commands as plain separate commands.
