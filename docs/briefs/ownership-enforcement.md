# ownership-enforcement

**Status:** in progress  <!-- planned | in progress | review | done -->
**Created:** 2026-10-18 · **Approved:** 2026-10-18 · **Depends on:** none

## Goal
Make file ownership enforceable instead of a convention: one ownership map, a merge-time check that is the real guarantee, and an early-warning hook that stops agents from editing files they don't own. Also refine the workflow: the coder runs in parallel once `Effects.play(id)` exists, and Rick cleans up agent branches and worktrees after every merge.

## Player story
Not player-facing. As the user, I want every agent's changes to stay inside its own files, so that merges are predictable, reviews are focused, and a mistake is caught before it reaches the feature branch, not in the PR.

## Acceptance criteria
**Ownership map**
- [ ] `.claude/ownership.json` (JSON, standard-library parsing) maps every agent (by `agent_type`) to its git identity (`name`, `email`) and allowed path globs, matching the ownership rules in `CLAUDE.md`, the agent files and the decisions below.
- [ ] Glob rules: `**` = any depth, `*` = one path segment, a leading `!` excludes a path that an earlier glob allowed. Paths outside the repository are always allowed (scratchpad, `~/.claude`).
- [ ] The orchestrator (main session, no `agent_type`) may edit `CLAUDE.md`, `docs/briefs/**` and `.github/**`. For **human-controlled** files the hook answers `ask` (the user confirms in the conversation) instead of `deny`; everything else is denied.
- [ ] **Human-controlled** (no agent may edit): `.claude/agents/**`, `.claude/skills/**`, `docs/workflow.md`, `.claude/ownership.*`, `.claude/hooks/**`, `.claude/settings.json`.

**Merge-time check (the real guarantee)**
- [ ] `mise run ownership-check <branch>` diffs `<feature-branch>...<branch>` (feature branch defaults to the current branch) and checks **every non-merge commit by its author email** against that author's map, so Gandalf's test commits on an agent's branch are checked against Gandalf's rules.
- [ ] Merge commits (Rick's) are skipped for the author check; files changed while resolving a merge conflict are listed in the output so Rick can put them in the PR.
- [ ] Fails with a clear list: commit, author, file, and who owns that file. Commits by unknown authors fail.
- [ ] Docs state that authorship is self-declared, so this guards against mistakes, not tampering.

**PreToolUse hook (early warning)**
- [ ] Matcher `Edit|Write|NotebookEdit`, `"onFailure": "block"`, exec form `${CLAUDE_PROJECT_DIR}/.claude/hooks/ownership.py`.
- [ ] Reads `agent_type` and the target path (`tool_input.file_path` / `notebook_path`); denies with a reason naming the owning agent(s); allowed edits produce no output.
- [ ] Converts paths inside `<project>/.claude/worktrees/<name>/` to repo-relative paths before matching; tests cover both worktree and main-checkout paths.
- [ ] Reads the ownership map and matcher **only from `$CLAUDE_PROJECT_DIR`** (the main checkout), never from the agent's worktree, so an agent can't loosen its own rules.
- [ ] Unknown `agent_type` (e.g. `general-purpose`) is denied inside the repo; a test shows built-in read-only agents (`Explore`, `Plan`) are unaffected (the hook never fires for read tools, and gives no decision for them).
- [ ] Standard library only (system `python3`), < 200 ms per call.
- [ ] Docs state that Bash-written files aren't covered by the hook, only by the merge check.

**Tests**
- [ ] `tools/ownership/test_ownership.py` (stdlib `unittest`): allowed and denied paths per agent, `!` exclusions, outside-repo paths, worktree vs. main-checkout paths, unknown and read-only agent types, orchestrator `ask` vs. `deny`, the per-commit author check (incl. skipped merge commits). Run by `mise run tools-test`, which CI also runs.

**Workflow**
- [ ] `/feature` skill + `docs/workflow.md`: the coder waits for the vfx branch only while `Effects.play(id)` isn't on `main`; afterwards the coder starts in parallel with the designers and uses new effect IDs / sound keys from the brief.
- [ ] `/feature` skill + `docs/workflow.md`: before each merge Rick runs `ownership-check` (violation → back to the agent); after each merge he deletes the merged agent branch (`git branch -d <name>/<agent>`) and runs `git worktree prune`; the final summary lists leftover `<name>/*` branches and worktrees.
- [ ] `CLAUDE.md` describes all of the above in short.

**Live smoke test (after merge, before the PR)**
- [ ] Launch each agent once with a harmless task that tries to edit one allowed and one forbidden file; report the allow/deny results in the PR, then revert the harmless edits.

## Out of scope
- Blocking Bash-written files at write time (only the merge check covers them).
- Server-side enforcement (branch protection, CODEOWNERS): possible follow-up.
- Checking commit content beyond paths.

## Assets needed
none (no models, sounds or effects).

## Balance impact
none

## Research needed
none. Facts already checked against the Claude Code docs (2026-10-18) for the coder:
- Hook input has `agent_type` only inside subagents (or with `--agent`); the main session has none. `cwd` is the worktree root for agents in worktrees; `${CLAUDE_PROJECT_DIR}` stays the main checkout.
- Decisions: exit 0 with `{"hookSpecificOutput": {"hookEventName": "PreToolUse", "permissionDecision": "deny" | "ask", "permissionDecisionReason": "…"}}`. No output = no decision.
- `"onFailure": "block"` on the command hook handler (beside `type`/`command`/`args`) blocks the tool call when the hook can't start, crashes, times out or prints invalid output. Needs Claude Code ≥ 2.1.295 (installed: 2.1.302).
- Exec form with a path placeholder: `"command": "${CLAUDE_PROJECT_DIR}/.claude/hooks/ownership.py", "args": []`.

## Decisions (approved)
1. **Process-file ownership:**
   - **Wesly (plugin-coder):** `tools/**` (except `tools/balance/**` and tool tests), `mise.toml` (always under "Needs human review" in the PR), `.gitignore`, `.mailmap`, `design/requirements.txt`, and the reference docs (`docs/paper-*.md`, `docs/toolchain.md`, `docs/minimessage.md`, `docs/integrations.md`).
   - **Human-controlled:** see acceptance criteria. Rick may edit them only when the user explicitly asks in the conversation, and every change goes under "Needs human review".
   - **Bootstrap exception:** in this feature, Wesly creates the guardrail files (`.claude/ownership.json`, `.claude/hooks/**`, the `hooks` entry in `.claude/settings.json`); the human-only rule applies from the merge onward. Rick makes the `/feature` skill and `docs/workflow.md` changes (the user asked for them explicitly).
2. **Per-commit author check**, skipping merge commits but reporting conflict-resolution files.
3. **Unknown agent types denied**; read-only built-ins unaffected.
4. **The matcher lives with the guardrails** (`.claude/hooks/ownership_lib.py`), so `tools/ownership/check.py` and the hook share it but no agent can loosen it.

## Open questions
none (`main` pushed by the user before the PR step).

## Contract (fixed names; change only via the orchestrator)
| Kind | ID | Owner | Notes |
|---|---|---|---|
| ownership map | `.claude/ownership.json` | human-controlled (Wesly creates it here) | `{"orchestrator": {...}, "agents": {"<agent_type>": {"name", "email", "paths": [...]}}, "human_controlled": [...]}` |
| matcher module | `.claude/hooks/ownership_lib.py` | human-controlled (Wesly creates it here) | path normalisation, glob matching, decisions |
| hook script | `.claude/hooks/ownership.py` | human-controlled (Wesly creates it here) | PreToolUse entry point |
| settings | `.claude/settings.json` `hooks` entry | human-controlled (Wesly adds it here) | keep `worktree.baseRef: head` |
| check script | `tools/ownership/check.py` | plugin-coder | `check.py <branch> [--base <feature-branch>]`, imports the matcher from `$CLAUDE_PROJECT_DIR` or the repo root |
| mise task | `ownership-check` | plugin-coder | `mise run ownership-check <branch>` |
| mise task | `tools-test` | plugin-coder | `python -m unittest discover -s tools -p 'test_*.py'` |
| tests | `tools/ownership/test_ownership.py` | plugin-tester | |
| CI step | `.github/workflows/ci.yml`: `mise run tools-test` | orchestrator | |
| docs | `.claude/skills/feature/SKILL.md`, `docs/workflow.md` | orchestrator (user asked) | coder exception, ownership-check, cleanup |
| docs | `CLAUDE.md` | orchestrator | short summary |

## Tasks
| Agent | Task | Needs (gate) | Branch |
|---|---|---|---|
| plugin-coder | ownership.json, matcher, hook + settings entry, check script, mise tasks | brief approved | `ownership-enforcement/plugin-coder` |
| plugin-tester | review the coder branch; write `tools/ownership/test_ownership.py`; integration review | coder DONE | (reviewed branch) |
| orchestrator | skill + workflow.md + CLAUDE.md + CI step, merges, live smoke test, PR | coder READY | `feature/ownership-enforcement` |

## Status
| Agent | Status | Branch | Verdict | Notes |
|---|---|---|---|---|
| plugin-coder | in progress | `ownership-enforcement/plugin-coder` | | launched |
| plugin-tester | waiting | | | after coder DONE |
| orchestrator | in progress | `feature/ownership-enforcement` | | brief committed |

## Merge order
1. Orchestrator's docs/CI commits (directly on the feature branch)
2. `ownership-enforcement/plugin-coder` (incl. Gandalf's tests), after `ownership-check`
3. Live smoke test results, then PR

## Needs human review
- [ ] `.claude/ownership.json`: do the globs match what you expect per agent?
- [ ] `.claude/hooks/ownership.py`, `.claude/hooks/ownership_lib.py` and the `hooks` entry in `.claude/settings.json` (guardrail files)
- [ ] `mise.toml` changes (`ownership-check`, `tools-test`)
- [ ] `.claude/skills/feature/SKILL.md` and `docs/workflow.md` changes (human-controlled)
- [ ] Live smoke test results (in the PR)
