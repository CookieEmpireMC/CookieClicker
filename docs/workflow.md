# Workflow: who runs when

Reference for all agents and the orchestrator. Agents can't see or wait for each other while running, so all coordination goes through **files on the feature branch** and **reports to the orchestrator**. Each agent checks its start conditions, does its job, and reports in a fixed format. The orchestrator launches the next agents.

---

## Branches at a glance

```
main ──────────────────────────────────────────────────────────── (user merges the PR)
  └─ feature/<name>            Rickroll Rick: brief, research notes, merges, integration review
       ├─ <name>/model-designer      agent worktree, branched from feature/<name>
       ├─ <name>/sound-designer      …
       ├─ <name>/vfx-designer
       ├─ <name>/balance-designer
       └─ <name>/plugin-coder
```

- **One branch per feature:** `feature/<name>`, created from an up-to-date `main`.
- **Agent branches** are named `<name>/<agent>` (not `feature/<name>/<agent>`: git can't have both `feature/<name>` and refs under it). Agents create them by renaming their worktree's branch (`git branch -m <name>/<agent>`).
- `.claude/settings.json` sets `"worktree": { "baseRef": "head" }`, so agent worktrees branch from the orchestrator's current `HEAD` (the feature branch) instead of `main`. The coder and designers have `isolation: worktree` in their frontmatter, so they always get one.
- Agents **never push, never touch `main`, never merge.** The orchestrator merges each agent branch into the feature branch after the tester approves it. The user reviews and merges the pull request into `main`.

## Roles

| Role | Who | Works in |
|---|---|---|
| **Orchestrator**: Rickroll Rick | the main Claude session talking to the user | the main checkout, on `feature/<name>` |
| resource-pack-researcher | agent | the main checkout, `docs/research/` only (no worktree, no shell; the orchestrator commits its notes) |
| model-designer, sound-designer, vfx-designer, balance-designer, plugin-coder | agents | their own worktree, branch `<name>/<agent>` |
| plugin-tester | agent | the worktree of the branch it reviews (only after that branch's owner has stopped), or the feature branch for an integration review |

Everyone commits under their own git identity; see "The team" in `CLAUDE.md` and the Commits section of each agent file.

---

## The feature brief: the contract everyone builds against

Every feature starts with a brief: `docs/briefs/<name>.md`, copied from `docs/briefs/TEMPLATE.md` (goal, player story, acceptance criteria, out of scope, assets needed, balance impact, research needed, open questions, contract, tasks, status, merge order, human review). The easiest way is the **`/feature <name> <description>`** skill, which runs this whole workflow.

The brief's **Status** line tracks the feature: `planned` (drafted, waiting for approval) → `in progress` (agents running) → `review` (pull request open, draft or waiting on CI) → `done` (green, `READY`, PR marked ready; set in the PR's last commit so merging lands it on `main`). The **Status** table inside tracks each agent.

- **Contract first.** Everyone uses exactly the IDs in the brief's Contract section. That's what lets designers and the coder work in parallel: the coder references `cookie_click` before the sound exists, and the sound-designer delivers exactly that name.
- Agents **never edit the brief**. If an ID is wrong or missing, propose the change under `CONTRACT CHANGES` in your report; the orchestrator updates the brief.
- Only the orchestrator updates the **Status** table, based on reports.

---

## Phases of a feature

```
0. Branch         git switch main && git pull && git switch -c feature/<name>
1. Brief          /feature drafts docs/briefs/<name>.md; user approves; committed on the feature branch   (planned)
2. Research       researcher → docs/research/<topic>.md, committed on the feature branch                   (in progress)
3. Build          in parallel, each in its own worktree from the feature branch:
                    model-designer · sound-designer · vfx-designer · balance-designer · plugin-coder
4. Review         plugin-tester per agent branch → READY / NOT READY (+ fix loop with the owner)
5. Merge          READY agent branch → feature branch (merge --no-ff), one at a time, checks after each
6. Integration    plugin-tester reviews the whole feature on the feature branch
7. Pull request   push feature/<name>, open the PR (ready or draft), wait for CI                         (review)
8. Done           all green + READY → status done, PR marked ready; the user merges it; Rick cleans up   (done)
```

### Start conditions (gates)

| Agent | Can start when | Does NOT wait for |
|---|---|---|
| researcher | the brief exists (or the orchestrator asks directly) | anyone |
| model-designer | brief contract lists its assets; research note on the feature branch if the brief says "Needs research: yes" | coder, other designers |
| sound-designer | same as model-designer, for sound events | coder, other designers |
| vfx-designer | same, for effect IDs; and any item model its effects use is in the contract | coder, other designers |
| balance-designer | the brief lists a balance impact (new buildings/upgrades/prices/rewards) | coder, other designers |
| plugin-coder | brief exists; research note on the feature branch if needed; **every Java API from another owner it calls is already merged into the feature branch** (e.g. the `Effects` service in the `vfx` package) and every feature in "Depends on" is merged into `main` | designers' assets and final balance numbers (code against contract IDs and `docs/balance/model.md`; vanilla fallbacks cover missing assets) |
| plugin-tester (branch) | that branch's owner reported `DONE` and has stopped | other branches |
| plugin-tester (integration) | all agent branches of the feature are merged into the feature branch | — |

**Java code dependencies are the one hard wait.** Assets can arrive later because of IDs and fallbacks, but code can't compile against an API that isn't on the branch. So the vfx engine's `Effects` service must be merged into the feature branch before the coder's worktree is created. Because worktrees branch from the orchestrator's `HEAD`, launch the coder **after** that merge.

### If a gate isn't met
Stop and report `STATUS: BLOCKED` with exactly what you're waiting for. Don't invent IDs, stub another owner's code, or edit files you don't own to get unblocked.

### Merging agent branches (orchestrator)
Merge each `READY` branch into the feature branch as Rickroll Rick, one at a time:
`git -c user.name="Rickroll Rick" -c user.email="rick@bakery.invalid" merge --no-ff <name>/<agent>`

Order, so assets and config land before the code that uses them:
1. research notes (committed directly on the feature branch as they land)
2. model-designer, then sound-designer (assets + `sounds.yml`)
3. vfx-designer (engine + `effects.yml`)
4. balance-designer (`buildings.yml`, `upgrades.yml`, model, golden values)
5. plugin-coder

After every merge, run `mise run check`, `mise run validate-assets`, `mise run sound-check` and `mise run balance-check`. A red check stops the line: send it to the owning agent, fix on its branch, re-review, merge again.

**Conflicts:** resolve only when every conflicting file has a clear owner and the right result is obvious (e.g. two appended sections). Otherwise abort the merge and ask the owning agent to rebase its change on the current feature branch, or ask the user.

**Cleanup:** after an agent branch is merged, remove its worktree and branch (`git worktree remove <path>`, then `git branch -d <name>/<agent>`; `git worktree unlock` first if it's still locked).

### Fix loop
`NOT READY` goes back to the branch owner, in the same worktree and branch, with the tester's findings. When the owner reports `DONE` again, the tester re-reviews only what changed. Max 3 rounds; after that the orchestrator asks the user.

### Pull request
1. `gh auth status`: if not logged in, tell the user to run `gh auth login` and stop.
2. Push only the feature branch: `git push -u origin feature/<name>`. Never push `main`, never force-push.
3. Fill in `.github/pull_request_template.md` (summary, brief + acceptance criteria, team contributions from `git log main..HEAD --format="%an: %s"`, checks, tester verdict, needs human review, how to test in-game, follow-ups) into a body file, then:
   `gh pr create --base main --head feature/<name> --title "<title>" --body-file <file>`
   - **Ready** PR only when every check passes and the integration verdict is `READY`.
   - Otherwise add `--draft` and list the open issues in the body.
4. If `.github/workflows/` has a CI workflow: `gh pr checks --watch`. If CI fails, fix it through the owning agent, merge into the feature branch, push again (the PR updates), and re-check.
5. Give the user the PR URL and a 3-line summary. **The user reviews and merges the PR.** The orchestrator never merges it.

---

## Rules for every agent

- **Read first:** `docs/workflow.md` (this file) and the feature brief you were given (`docs/briefs/<name>.md`).
- **Own files only.** See "Asset ownership" in `CLAUDE.md`. Need something from another owner? Put it under `REQUESTS` in your report.
- **Git:** follow the Commits section of your agent file: your own identity per commit, only your own files, only on your own branch, small commits, checks first. Never push, merge, rebase, amend or force, and never touch `main` or the feature branch directly.
- **Don't wait in a loop.** If you're blocked, report and stop. The orchestrator re-launches you when the gate is met.

### Report format (end every run with this)

```
STATUS: DONE | BLOCKED | NEEDS-INPUT
FEATURE: <name>
BRANCH: <name>/<agent>, or "feature/<name> (docs only)"
PRODUCED: files, plus the IDs/names others will use
CONTRACT CHANGES: proposed changes to the brief's contract, or none
REQUESTS: <owner>: <what you need from them>, or none
WAITING FOR: (only if BLOCKED) the exact gate that isn't met
NEXT: who can start now because of this work
```

The tester also adds `VERDICT: READY | NOT READY` and, at integration, a `MERGE ORDER` line.
