# github-app-pr-author

**Status:** in progress  <!-- planned | in progress | review | done -->
**Created:** 2026-10-18 · **Depends on:** ownership-enforcement (merged, PR #2)

## Goal
Every feature PR is authored by the `cookie-bakery[bot]` GitHub App instead of the user's account, so the user can approve it under `main`'s ruleset (1 approving review + the `check` CI job; authors can't approve their own PR). Turn the one-off script used for PR #2 into a tested tool and make it the standard last step of `/feature`.

## Player story
Not player-facing. As the user, when a feature is done I get a PR opened by the bot, I review and approve it, and I merge it, with no workaround needed.

## Acceptance criteria
- [ ] `tools/github_app/token.py` creates a GitHub App installation token: signs an RS256 JWT with `openssl` (iat −60 s, exp +9 min), exchanges it at `POST /app/installations/<id>/access_tokens`, and prints **only** the token on stdout. Standard library + `openssl` only.
- [ ] Settings come from `~/.config/cookieclicker/github-app.env` (`APP_ID`, `INSTALLATION_ID`, `KEY_PATH`), overridable by environment variables. The key file must exist and be mode `600` (refuse otherwise). Nothing secret is stored in the repo; the token is never written to disk or logs.
- [ ] Clear errors (no token printed) for: missing config, missing/unsafe key, wrong IDs, network/API errors; non-zero exit.
- [ ] `tools/github_app/open_pr.py --head <branch> --base main --title … --body-file …` opens the PR via the REST API as the App and prints `url`, `number` and author login; `--draft` supported; fails clearly if a PR for that head already exists.
- [ ] mise tasks `pr-token` (quiet, for `GH_TOKEN=$(mise run -q pr-token)`) and `pr-open`.
- [ ] Tests (stdlib `unittest`, run by `tools-test`): JWT structure and claims, signature verifies with the test key's public half (via `openssl`), config/env precedence, key-permission refusal, error paths with a fake local HTTP server (no real GitHub calls, no real key).
- [ ] `/feature` skill, `docs/workflow.md`, `CLAUDE.md`, PR template: PRs are opened with `mise run pr-open …` as the bot; if the bot can't be used, Rick stops and tells the user (never falls back to the user's account).
- [ ] `mise run pr-open` is run **by Rick**, allowed by a permission rule the user adds themselves (decision 1). If the rule is missing or the call is denied, Rick stops and gives the user the exact `! mise run pr-open …` command instead.

**Added scope (user request, 2026-10-18): agent memory**
- [ ] Every agent file has `memory: project`; `.claude/ownership.json` gives each agent only its own `.claude/agent-memory/<agent_type>/**`.
- [ ] `docs/workflow.md` "Agent memory" section: what to store, what not, keep `MEMORY.md` under 200 lines, memory travels through branches.
- [x] Verify where project memory lands for an agent in a worktree: **in the main checkout** (`$CLAUDE_PROJECT_DIR/.claude/agent-memory/<agent_type>/`), not the worktree (Gandalf, 2026-10-18). Rick commits it on the feature branch credited to the agent.

## Out of scope
- Approving or merging PRs as the bot (never).
- Pushing as the bot (pushes keep using the user's SSH key).
- Creating or changing the GitHub App itself.

## Assets needed
none

## Balance impact
none

## Research needed
none. Facts for the coder (checked 2026-10-18):
- App `cookie-bakery`, App ID `5371158`, installation `172778981` on `Self-Learned/Cookie-Clicker`; permissions: pull requests write, contents read, metadata read. Key at `~/.config/cookieclicker/github-app.pem` (mode 600).
- The working flow (used for PR #2) is in the user's scratchpad script `open-pr-as-bot.sh`: base64url JWT `{"alg":"RS256","typ":"JWT"}` / `{"iat","exp","iss"}`, `openssl dgst -sha256 -sign <key> -binary`, then the access-token exchange; PR via `POST /repos/{owner}/{repo}/pulls`.
- `gh pr create` with an App token may fail on gh 2.45 (it queries the viewer); use the REST API directly.

## Open questions
1. ~~Who runs `pr-open`?~~ **Decided (user): b.** Rick runs it under a permission rule the user adds for `Bash(mise run pr-open:*)`; the user still reviews, approves and merges every PR.
2. ~~Old remote branch~~ **Decided (user):** "Automatically delete head branches" is enabled for future merges.

## Contract (fixed names; change only via the orchestrator)
| Kind | ID | Owner | Notes |
|---|---|---|---|
| script | `tools/github_app/token.py` | plugin-coder | prints only the token |
| script | `tools/github_app/open_pr.py` | plugin-coder | `--head --base --title --body-file [--draft]` |
| mise task | `pr-token`, `pr-open` | plugin-coder | `mise.toml` → Needs human review |
| config (outside repo) | `~/.config/cookieclicker/github-app.env` | user | `APP_ID`, `INSTALLATION_ID`, `KEY_PATH` |
| tests | `tools/github_app/test_github_app.py` | plugin-tester | |
| docs | `.claude/skills/feature/SKILL.md`, `docs/workflow.md` | orchestrator (user asked) | human-controlled → Needs human review |
| docs | `CLAUDE.md`, `.github/pull_request_template.md` | orchestrator | |

## Tasks
| Agent | Task | Needs (gate) | Branch |
|---|---|---|---|
| plugin-coder | `token.py`, `open_pr.py`, mise tasks, `docs/toolchain.md` rows | brief approved | `github-app-pr-author/plugin-coder` |
| plugin-tester | review + `test_github_app.py` + integration review | coder DONE | (reviewed branch) |
| orchestrator | skill, workflow.md, CLAUDE.md, PR template, merge, PR | coder READY | `feature/github-app-pr-author` |

## Status
| Agent | Status | Branch | Verdict | Notes |
|---|---|---|---|---|
| plugin-coder | in progress | `github-app-pr-author/plugin-coder` | | launched |
| plugin-tester | planned | | | |
| orchestrator | in progress | `feature/github-app-pr-author` | | brief approved; pr-open allow rule added by the user |

## Merge order
1. Orchestrator's docs commits
2. `github-app-pr-author/plugin-coder` (incl. Gandalf's tests), after `ownership-check`
3. PR opened as `cookie-bakery[bot]` by Rick with `mise run pr-open` (decision 1)

## Needs human review
- [ ] `mise.toml` (`pr-token`, `pr-open`)
- [ ] `.claude/skills/feature/SKILL.md`, `docs/workflow.md` (human-controlled)
- [ ] Security: token never printed/stored except stdout of `pr-token`; key permission check
