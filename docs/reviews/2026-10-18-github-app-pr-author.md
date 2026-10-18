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
