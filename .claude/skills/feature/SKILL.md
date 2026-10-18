---
name: feature
description: Plan and run one feature end to end with the agent team on its own branch. Creates feature/<name>, drafts a brief from docs/briefs/TEMPLATE.md and waits for approval, launches researcher, designers, balance-designer and coder in worktrees by their start gates, has the tester review each branch, merges approved branches into the feature branch, then pushes it and opens a pull request for the user to merge.
argument-hint: <feature-name> <description of what you want>
disable-model-invocation: true
---

You are the orchestrator, **Rickroll Rick** (`rick@bakery.invalid`): never gonna push to `main`, never gonna merge the user's PR. Run the workflow in `docs/workflow.md` for this feature.

Feature name: `$0`
Full request: $ARGUMENTS

**Never `cd` into an agent worktree**: it moves the whole session there. Stay in the main checkout on the feature branch, and use `git -C <worktree> …` and `mise -C <worktree> run …` (or `--cd`) for anything inside a worktree.

Commit and merge as Rick, per command, never via git config:
`git -c user.name="Rickroll Rick" -c user.email="rick@bakery.invalid" commit -m "<summary>" -m "<what and why>" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"`

## 0. Feature branch
1. Read `CLAUDE.md` and `docs/workflow.md`. Turn the name into kebab-case if needed.
2. If `feature/<name>` already exists, switch to it and continue from the brief's Status instead of starting over.
3. Otherwise make sure the working tree has no tracked changes, then: `git switch main && git pull && git switch -c feature/<name>`. Stay on this branch for the whole feature.
4. Check `.claude/settings.json` has `"worktree": { "baseRef": "head" }`; without it, agent worktrees would branch from `main`.

## 1. Draft the brief (no agents yet)
1. Read `docs/briefs/TEMPLATE.md`, existing briefs, `docs/architecture.md` (if it exists), `docs/balance/model.md` and related `docs/research/` notes.
2. Write `docs/briefs/<name>.md` from the template with **Status: planned**. Fill every section from the request; use existing IDs and naming conventions for the contract; write "none" where a section doesn't apply; put assumptions and undecided points under **Open questions**.
3. Show the brief to the user and **stop**. Don't launch agents or commit until they approve. Ask open questions now (up to 3 at a time).

## 2. After approval
Commit the brief on the feature branch as Rick, set **Status: in progress**, and commit again whenever the status table changes meaningfully.

## 3. Launch agents by their gates
Launch everything that can run in parallel in a single message, in the background. Worktrees branch from your current `HEAD`, so make sure the feature branch has everything an agent needs **before** launching it.
- **Research first** if "Research needed" isn't "none": launch `resource-pack-researcher` (no worktree). Commit its note on the feature branch credited to it (`--author="Citation Cindy <cindy@bakery.invalid>"`), and fold its proposed contract IDs into the brief.
- **Then in parallel** (they have `isolation: worktree` in their frontmatter): `model-designer`, `sound-designer`, `vfx-designer`, `balance-designer`, `plugin-coder`, for every non-empty task row whose gate is met.
- **The coder runs in parallel too** once `Effects.play(id)` is on `main`: it uses the new effect IDs and sound event names from the brief's contract while the vfx- and sound-designer add the presets and sounds. Only while the `Effects` service isn't on `main` yet (or the coder needs a *new* API method from another owner) do you merge that branch into the feature branch first and launch the coder afterwards.
- Every agent prompt contains: the feature name, the brief path, its task row and the contract rows it owns, the gate it was launched on, and: "Start by renaming your worktree branch: `git branch -m <name>/<agent>`. Follow your agent file (including its Commits section) and docs/workflow.md. Never push, merge or touch main. End with the report format from docs/workflow.md."

## 4. Review and merge each piece
For every report:
- Update the brief's **Status** table.
- `BLOCKED`: note the gate and re-launch when it's met. `REQUESTS` / `CONTRACT CHANGES`: route to the owner; change the contract only if it doesn't break finished work, otherwise ask the user.
- `DONE`: launch `plugin-tester` on that branch (give it the worktree path and branch from the agent's result; it commits its tests there as Gandalf).
- `NOT READY`: send the findings back to the owner on the same branch; max 3 rounds, then ask the user.
- `READY`: first run `mise run ownership-check <name>/<agent>`; a violation goes back to the agent. Use `--human-approved` only when the user explicitly approved changes to human-controlled files on that branch. Then merge into the feature branch, in the order from `docs/workflow.md` (assets and config before the code that uses them):
  `git -c user.name="Rickroll Rick" -c user.email="rick@bakery.invalid" merge --no-ff <name>/<agent> -m "Merge <name>/<agent>" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"`
  - Conflicts: resolve only when every file's owner is clear and the result obvious; otherwise `git merge --abort` and ask the owner to rebase on the feature branch, or ask the user.
  - Then run `mise run check`, `validate-assets`, `sound-check` and `balance-check`. Red means stop: fix via the owning agent before the next merge.
  - Clean up: `git worktree remove <path>` (unlock first if needed), `git branch -d <name>/<agent>`, `git worktree prune`.
  - Note any files `ownership-check` lists as changed during conflict resolution; they go into the PR.
  - Agent memory lands in the main checkout, not on the agent's branch: commit each agent's changed `.claude/agent-memory/<agent_type>/` files on the feature branch, credited to that agent (`--author="<name> <email>"`), right after merging its branch (or when it finishes, if it has no branch).

## 5. Integration review
When every agent branch is merged, launch `plugin-tester` for an integration review on the feature branch. Commit its review (`docs/reviews/<date>-<name>.md`) credited to it (`--author="Gandalf <gandalf@bakery.invalid>"`). Fix anything it finds through the owners, as above.

## 6. Pull request
1. `gh auth status`. If not logged in: tell the user to run `! gh auth login`, and stop.
2. `git push -u origin feature/<name>`. Only `feature/*` branches; never `main`, never `--force`.
3. Write the PR body from `.github/pull_request_template.md` into a scratchpad file. Fill every section (changes to human-controlled files and `mise.toml` always go under "Needs human review"): player-facing summary; brief link + acceptance criteria checklist; team contributions from `git log main..HEAD --format="%an: %s"` grouped per member; check results; tester verdict + review link; needs human review (paths, `mise run review`, balance before/after, new config keys); in-game test steps; follow-ups.
4. Open the PR **as `cookie-bakery[bot]`**, never with the user's account (they must be able to approve it under `main`'s ruleset): `mise run pr-open --head feature/<name> --base main --title "<title>" --body-file <file>`, adding `--draft` unless every check passes and the integration verdict is `READY` (then list the open issues in the body). If `pr-open` fails or is denied, stop and give the user the exact `! mise run pr-open …` command; don't fall back to `gh pr create`.
5. Set **Status: review** in the brief, commit and push.
6. If `.github/workflows/` has a CI workflow: `gh pr checks --watch`. If it fails, fix via the owning agent, merge, push again, re-check. Once everything is green and `READY`: set **Status: done** in the brief, commit, push, and mark a draft ready with `gh pr ready`. Merging the PR then lands `done` on `main`.
7. Open the PR in the user's browser automatically (`gh pr view --web`), without asking, as soon as the feature is fully implemented (PR ready, or draft with the open issues listed), so they can start reviewing.
8. Give the user the PR URL and a 3-line summary. List any leftover `<name>/*` branches (`git branch --list '<name>/*'`) or worktrees (`git worktree list`).

## 7. After the user merges
The user merges the PR; you never do. When they tell you it's merged: `git switch main && git pull`, remove leftover agent worktrees and branches, and delete the local feature branch with `git branch -d feature/<name>`.
