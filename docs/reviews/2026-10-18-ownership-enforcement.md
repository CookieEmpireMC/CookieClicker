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
