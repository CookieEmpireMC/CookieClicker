# Review: ownership-enforcement / plugin-coder (branch review)

Reviewer: Gandalf. Branch `ownership-enforcement/plugin-coder` vs `feature/ownership-enforcement`. Python tooling feature (no Paper API involved; target version check not applicable).

## Tests
- `tools/ownership/test_ownership.py`: 62 tests (stdlib unittest), all pass in about 1 s via `mise run tools-test`.
  - Globs (`**`, `*`, `!`, last match wins, case), map validation, map/agent-file consistency, no unintended double ownership across all tracked files.
  - Allow/deny per agent (coder, tester, model, sound, vfx, balance, researcher), human-controlled denied for all, unknown agents denied, deny reasons name the owner.
  - Orchestrator allow / ask / deny.
  - Path handling: main checkout vs `.claude/worktrees/<name>/` vs relative (with cwd), `..` normalisation, outside-repo, sibling-prefix dir, symlink.
  - Hook as subprocess with temp `CLAUDE_PROJECT_DIR`: allow prints nothing, deny JSON, read tools (Explore/Plan) no decision, NotebookEdit, looser map + patched matcher inside a fake worktree ignored, exit 2 for no/bad project dir, bad JSON, missing/broken map, missing lib; settings entry shape; speed.
  - Merge check: logic with precomputed files (tester commit on coder branch passes, wrong author fails, unknown author, case-insensitive email, human-controlled with/without `--human-approved`, merge skipped) and a throwaway git repo with a real merge conflict (conflict file listed, no FAIL), `--base` default, exit codes, rules read from `CLAUDE_PROJECT_DIR` first.
- `mise run test`, `check`, `validate-assets`, `sound-check`, `balance-check`: all pass (no Java, assets or balance config in this worktree yet, so they are trivially green).
- `mise run ownership-check ownership-enforcement/plugin-coder --base feature/ownership-enforcement --human-approved`: OK. Human-controlled list is exactly the 4 bootstrap files (`.claude/ownership.json`, `.claude/hooks/ownership_lib.py`, `.claude/hooks/ownership.py`, `.claude/settings.json`); the coder's tests-free commits pass. Gandalf's commits pass against Gandalf's rules.

## Acceptance criteria
All met: map format and identities, glob rules, orchestrator allow/ask/deny, human-controlled list, per-commit author check, merge skip + conflict listing, failure output with commit/author/file/owner, self-declared authorship documented, hook matcher/onFailure/exec form/worktree conversion/map from `CLAUDE_PROJECT_DIR`/unknown agents denied/no output on allow, Bash gap documented, tests. Workflow/skill/CLAUDE.md/CI items belong to Rick and are not on this branch. The live smoke test is still to do after merge.

Map vs CLAUDE.md "Asset ownership": matches, including the splits (design `*.md` vs `sound-*`, `vfx-*`, `sounds.md`, `effects.md`, `CREDITS.md`; `resourcepack` sounds/lang/particles carve-outs; vfx Java and tests; tester may also write vfx tests). Every tracked file has an owner or is human-controlled.

## Findings (none blocking)
1. (Wesly, info) Commit `b94d5ef` gives `.gitattributes` to plugin-coder, which is not in the brief's decisions. Harmless, but the user should confirm it under "Needs human review" in `.claude/ownership.json`.
2. (Rick) `check.py` falls back to the checked-out worktree's own map when neither `CLAUDE_PROJECT_DIR` nor the main checkout has one. That is the case for this very feature until it is merged, so the self-check here is not tamper-proof. Run the real check from the main checkout after the guardrail files are on the feature branch, or accept it for the bootstrap.
3. (Wesly, minor) `EDIT_TOOLS` in the hook includes `MultiEdit`, but the settings matcher is `Edit|Write|NotebookEdit` per the brief, so it is dead code. Harmless.
4. (Wesly, minor) A symlink inside the repo that points outside is resolved with `realpath` and counts as outside the repo (allowed). Acceptable under the "guards against mistakes" model; the merge check still covers it.
5. (info) `tools-test` maps unittest's exit code 5 (no tests) to success; now that tests exist this only matters for a future empty tree.

## Verdict
VERDICT: READY

---

## Integration review

Reviewer: Gandalf. Feature branch `feature/ownership-enforcement` at `0c2fd85` (clean tree, `main..HEAD` = 12 commits, 1 merge). Python/process feature; no Paper API involved (target version check not applicable).

### Checks (clean tree, exact numbers)
| Task | Result |
|---|---|
| `mise run check` | BUILD SUCCESS (no Java tests, "No tests to run") |
| `mise run validate-assets` | Checked 0 file(s), 0 problem(s) |
| `mise run sound-check` | Checked 0 sound(s), 0 failed |
| `mise run balance-check` | No balance config yet; nothing to check (pass) |
| `mise run tools-test` | Ran 62 tests, OK (1.07 s) |
| `git status --porcelain --untracked-files=all` | empty before and after the checks (apart from this review file) |
| `mise run ownership-check HEAD --base main --human-approved` | OK: 11 commits checked, 1 merge skipped (no conflict-resolution files). Human-controlled files changed (for "Needs human review"): `.claude/skills/feature/SKILL.md`, `docs/workflow.md`, `.claude/hooks/ownership_lib.py`, `.claude/hooks/ownership.py`, `.claude/ownership.json`, `.claude/settings.json` |

### Acceptance criteria (all met)
- Ownership map: `.claude/ownership.json` has 7 agents (identity + globs), orchestrator (`CLAUDE.md`, `docs/briefs/**`, `.github/**`) and the 6 human-controlled globs exactly as in the brief. It matches CLAUDE.md "Asset ownership" including Wesly's tooling/reference docs and `.gitattributes`, and the tester's tool tests.
- Hook: settings entry has the right matcher, exec form, `onFailure: block`; `MultiEdit` is gone from the hook (grep: no occurrences in `.claude/` or `tools/`), so finding 3 of the branch review is resolved. Worktree/main-checkout paths, map only from `$CLAUDE_PROJECT_DIR`, unknown/read-only agents, Bash gap and symlink behaviour are covered by tests and documented.
- Merge check: per-commit author check, merge skip + conflict listing, `--human-approved`, owner named in failures, self-declared authorship documented (workflow.md, toolchain.md, CLAUDE.md). The self-check from the main checkout now uses the main checkout's map (output says "rules from .../CookieClicker"), so branch-review finding 2 is resolved.
- Tests: 62 tests run by `mise run tools-test`; CI runs the same task (`.github/workflows/ci.yml`, step "Test the Python tools").
- Workflow: SKILL.md, workflow.md and CLAUDE.md agree: coder in parallel once `Effects.play(id)` is on `main`, ownership-check before each merge, `git branch -d` + `git worktree prune` after, leftovers listed in the summary, Rick never `cd`s into a worktree (`git -C` / `mise -C`).
- Docs, `ownership.json`, hook and `check.py` agree with each other and with CLAUDE.md.
- Live smoke test: Rick's results (7 agents, allowed writes OK, forbidden writes denied naming the correct owner, Rick's own write to `tools/` denied) accepted as reported; I did not re-run it. No smoke files or commits remain (tree clean).

### Findings (none blocking)
1. (user, confirm) `.gitattributes` is owned by plugin-coder (Wesly) although the brief's decisions did not list it; it is documented in CLAUDE.md and goes under "Needs human review".
2. (info) Symlink-to-outside is allowed by the hook (documented, merge check still covers it); `tools-test` treats "no tests" (exit 5) as success. Both accepted.
3. (info, Rick) Smoke-test summary names the sound-designer's owner as "Pixel Pat"; the map lists `pat@bakery.invalid` under model-designer and `bruno@bakery.invalid` under sound-designer. Check that the PR text names the right person for the `design/scripts` denial (that path should be denied to the sound-designer with the model-designer as owner, which is consistent with Pat owning model-designer).

Fixes needed: Wesly none, Gandalf none, Rick none (finding 3 is a wording check in the PR).

VERDICT: READY
