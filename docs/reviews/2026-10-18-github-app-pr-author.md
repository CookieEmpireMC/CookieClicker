# Review: github-app-pr-author / plugin-coder (2026-10-18)

Reviewer: Gandalf. Target: not a plugin feature (Python tooling; `pom.xml` unaffected).

## Verdict: READY

## Acceptance criteria
- token.py: RS256 JWT via openssl, iat -60 / exp +540, exchange at `/app/installations/<id>/access_tokens`, stdout only the token (script run as a subprocess verified: stdout == token, empty stderr, nothing written to disk). OK.
- Config: file, `--config` > `$GITHUB_APP_CONFIG` > default, env overrides; key must exist, be a regular file, owned by the user, no group/other bits (644/640/604/660/666 refused, 600/400 accepted). OK.
- Errors: missing config, bad IDs, missing/unsafe/not-a-key, 401/404/5xx, network, non-JSON; all exit 1, none contain token/JWT/PEM. OK.
- open_pr.py: url/number/author output, `--draft`, `--repo`, `--body-file -`, 401/403/404/422 and "already exists" (own exception). OK.
- mise tasks `pr-token` (quiet) and `pr-open` pass args through (checked with a failing call). OK.
- Secrets: token only returned to caller; `redact()` scrubs JWT and token from remote messages; http accepted only for loopback hosts (`127.0.0.1.evil.com`, `example.com`, `api.github.com` over http, ftp all refused before any request).

## Tests
`tools/github_app/test_github_app.py`: 50 new test methods; full `tools-test` 112 run, OK, 1 expected failure (the bug below). `mise run test`, `check`, `validate-assets`, `sound-check`, `balance-check` pass (no Java/assets/balance content yet). `ownership-check`: OK (3 commits).

## Findings
1. LOW (plugin-coder) `tools/github_app/app.py` `REPO_PATTERN`: `[A-Za-z0-9_.-]+` accepts `..`, so `--repo ../..` builds `/repos/../../pulls` (sent after the token is minted). Only the user/Rick supplies `--repo`, and the token goes to the configured host anyway, so not blocking. Fix: reject segments that are `.` or `..` (or consist only of dots). Test `test_dot_segments_in_repo_rejected` is `expectedFailure`; remove the decorator when fixed.
2. INFO: `check_key_file` is stat-then-openssl (small TOCTOU); acceptable for a single-user dev machine.
3. INFO: the PR-step 401 is now covered with the fake server (message "GitHub rejected the installation token"); real-API behavior still untested.
4. INFO: mode 700 key is accepted (no group/other bits); intended.

## Architecture
Clean split: `app.py` (logic), `cli.py`, two thin entry points; the `sys.path` bootstrap under `__main__` handles the `token.py` stdlib-shadowing. No `docs/architecture.md` change needed (not plugin code).

## Memory check
Memory file written to `/home/casper-rs/Documents/GitHub/CookieClicker/.claude/agent-memory/plugin-tester/testing-github-app-tools.md`: this is in the main checkout, NOT inside the worktree, so it can't be committed on this branch.

## Re-review (fix round 1, commit 1a3174e)
`validate_repo` now uses `fullmatch` and rejects `.`/`..` segments; runs before any token is minted. Finding 1 resolved. Tests: `expectedFailure` removed; added `../evil`, `../..`, `a/..`, `./b`, `a/b\n` rejected (no request made) and `a/.b`, `..a/b..` still accepted. `tools-test` fully green, `ownership-check` OK. VERDICT: READY

## Integration review (feature/github-app-pr-author @ 2f9bb87)

Reviewer: Gandalf. Target: not a plugin feature (tooling/docs; `pom.xml` unaffected).

### Verdict: READY

### Acceptance criteria (all met)
- Tools: `token.py`, `open_pr.py` (+ `app.py`, `cli.py`), mise tasks `pr-token` (quiet = true) and `pr-open`, `docs/toolchain.md` rows and "GitHub App PRs" section agree with the code and the brief contract. Tests in `tools/github_app/test_github_app.py` (stdlib, fake loopback server, throwaway key) all pass; coder and re-review findings closed (dot segments in `--repo`).
- Rick's docs: `.claude/skills/feature/SKILL.md` step 4, `docs/workflow.md` (PR step), `CLAUDE.md` (flow step 5) and `.github/pull_request_template.md` all say: open the PR with `mise run pr-open` as cookie-bakery[bot]; if it fails or is denied, stop and give the user the exact `! mise run pr-open ...` command; never `gh pr create` with the user's account. No remaining `gh pr create` instruction (only the "don't fall back" sentences).
- Permission rule: `.claude/settings.json` has `permissions.allow: ["Bash(mise run pr-open:*)"]`, added by the user's own commit 9545bb7; hooks/worktree settings unchanged. Rule covers only `pr-open`, not `pr-token`.
- Agent memory: `memory: project` present in all 7 agent files (grep -L finds none missing). `ownership.json` gives each agent exactly `.claude/agent-memory/<agent_type>/**`, keys match the 7 agent file names, no agent gets another's folder. workflow.md "Agent memory" section covers store / don't store / MEMORY.md under 200 lines (also says 25 KB) / "lands in the main checkout; Rick commits it credited to the agent", and the skill (step 4) and CLAUDE.md agree.
- Docs, ownership.json, settings and tools agree; no secrets in the repo (config and key live outside it; I did not read ~/.config/cookieclicker and did not run pr-token/pr-open).

### Checks on the clean tree
- `mise run check`: BUILD SUCCESS (no Java tests yet, "No tests to run").
- `validate-assets`: Checked 0 file(s), 0 problem(s).
- `sound-check`: Checked 0 sound(s), 0 failed.
- `balance-check`: no balance config yet, nothing to check, rc 0.
- `tools-test`: Ran 113 tests, OK (0 failures, 0 expected failures).
- `ownership-check HEAD --base main --human-approved`: 15 commits checked, 1 merge skipped (no conflict-resolution files). Human-controlled files reported (all Rick, for "Needs human review"): `.claude/skills/feature/SKILL.md`, `docs/workflow.md`, `.claude/ownership.json`, and the seven `.claude/agents/*.md` (balance-designer, model-designer, plugin-coder, plugin-tester, resource-pack-researcher, sound-designer, vfx-designer). It exits FAIL for exactly one violation: commit 9545bb7 "Allow Rick to run mise run pr-open", author Casper Schotman (unknown author), file `.claude/settings.json` (human-controlled). This is the user's own commit; expected. List `.claude/settings.json` under "Needs human review" too, and note it in the PR so the red ownership-check is explained. Not a defect.
- `git status --porcelain --untracked-files=all`: empty (before this section was appended).

### Findings per owner
- plugin-coder: none open.
- plugin-tester (me): none. Memory files in `.claude/agent-memory/plugin-tester/` are committed already; no new memory this round.
- orchestrator: none blocking. Needs-human-review list for the PR: `mise.toml`, `.claude/settings.json` (allow rule, user's commit), SKILL.md, workflow.md, ownership.json, the 7 agent files. Brief Status is still "in progress"; set to review/done per the skill after the PR is opened. Real-API behavior of `pr-open` is untested here by design; the first real run is the proof.
