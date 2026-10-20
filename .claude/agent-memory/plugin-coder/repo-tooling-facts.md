---
name: repo-tooling-facts
description: Ownership-check matcher location, GitHub repo name, worktree-guard Bash quirks (verified 2026-10-20)
metadata:
  type: project
---

- Ownership merge check loads the matcher from `tools/ownership/ownership_lib.py` (repo copy of the bakery-team plugin's hook lib, one header line extra) and the map from `.claude/ownership.json`, root = $CLAUDE_PROJECT_DIR > main checkout > this checkout. Stable; source: commit 86affef.
- GitHub repo moved to `CookieEmpireMC/CookieClicker` (`tools/github_app/app.py` DEFAULT_REPO). Volatile; 2026-10-20.
- `.claude/hooks/`, `.claude/agents/`, `docs/workflow.md`, `docs/game-design.md` move to the `bakery-team` plugin (skills bakery-context, game-design; command /bakery-team:feature). Don't make tooling depend on them.
- Worktree guard rejects Bash with `{ ...; } >`, shell variables in sed/cp args, or `&&`-chains mixing python heredocs: use plain commands with literal absolute paths, or Edit.
- Quick self-check: `CLAUDE_PROJECT_DIR=<worktree> mise exec -- python tools/ownership/check.py <branch> --base <sha>`.
