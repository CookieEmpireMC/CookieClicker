---
name: feature
description: Plan and run one feature end to end with the agent team. Drafts a brief from docs/briefs/TEMPLATE.md, waits for the user's approval, then launches researcher, designers, balance-designer, coder and tester by their start gates in docs/workflow.md, and ends with a summary, merge order and human review list.
argument-hint: <feature-name> <description of what you want>
disable-model-invocation: true
---

You are the orchestrator (git identity: **Such Merge Doge**, `doge@bakery.invalid`) running the feature workflow from `docs/workflow.md`.

Feature name: `$0`
Full request: $ARGUMENTS

## 1. Draft the brief (no agents yet)
1. Read `CLAUDE.md`, `docs/workflow.md`, `docs/briefs/TEMPLATE.md`, existing briefs in `docs/briefs/`, `docs/architecture.md` (if it exists), `docs/balance/model.md` and any `docs/research/` notes related to the request.
2. Turn the feature name into kebab-case if needed. If `docs/briefs/<name>.md` already exists, continue that feature from its Status instead of starting over.
3. Write `docs/briefs/<name>.md` from the template with **Status: planned**. Fill every section from the request; use existing IDs and naming conventions for the contract; write "none" where a section doesn't apply. Put anything you had to assume or can't decide under **Open questions**.
4. Show the brief to the user and **stop**. Ask them to approve it or change it. Don't launch agents or commit anything until they approve. If open questions remain, ask them now (up to 3 at a time).

## 2. After approval
1. Commit the brief on `main`:
   `git -c user.name="Such Merge Doge" -c user.email="doge@bakery.invalid" commit -m "Add <name> feature brief" -m "<one line>" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"`
2. Set **Status: in progress** in the brief (and commit it the same way whenever the status table changes meaningfully).

## 3. Launch agents by their gates
Follow the start conditions in `docs/workflow.md`. Launch everything that can run in parallel in a single message, in the background.
- **Research first** if "Research needed" isn't "none": launch `resource-pack-researcher` (no worktree) with the brief path and the questions. When its note lands, commit it credited to Citation Needed Cindy (`--author="Citation Needed Cindy <cindy@bakery.invalid>"`, committer Such Merge Doge) and fold its proposed contract IDs into the brief (commit).
- **Then, in parallel**, each with `isolation: "worktree"`, for every agent whose task row isn't empty and whose gate is met: `model-designer`, `sound-designer`, `vfx-designer`, `balance-designer`, `plugin-coder`. If the coder needs another owner's Java API that isn't on `main` yet (e.g. the vfx `Effects` service), hold the coder until that branch is merged.
- Every agent prompt contains: the feature name, the brief path, its task row and the contract rows it owns, the gate it was launched on, and these instructions: "Start by renaming your worktree branch: `git branch -m <feature>/<agent>`. Follow your agent file (including its Commits section) and docs/workflow.md. End with the report format from docs/workflow.md."

## 4. Handle reports
For every report:
- Update the brief's **Status** table (agent, status, branch, notes).
- `BLOCKED`: note what it's waiting for and re-launch the agent when that gate is met.
- `REQUESTS` / `CONTRACT CHANGES`: route each to its owner. Change the contract only if it doesn't break someone else's finished work; otherwise ask the user.
- `DONE`: launch `plugin-tester` for that branch, telling it the worktree path and branch from the agent's result (it works there, commits its tests to that branch as You-Shall-Not-Pass Gandalf). Set the agent's row to "review".
- Tester `NOT READY`: send the findings back to the branch owner (continue the same agent, or re-launch in the same worktree). At most 3 rounds, then ask the user.

When every branch is `READY`, set **Status: review** in the brief.

## 5. Finish
Give the user a summary:
- what each agent built (files, IDs), with test, `validate-assets`, `sound-check` and `balance-check` results
- the proposed **merge order** (from `docs/workflow.md` and the tester's advice) with each branch's verdict
- everything under **Needs human review** (with absolute paths to listen to or look at, e.g. `mise run review` output)
- open requests or follow-ups

Then **ask for approval to merge**. Never merge without it.

## 6. After merge approval
Merge one branch at a time, in order, as Such Merge Doge:
`git -c user.name="Such Merge Doge" -c user.email="doge@bakery.invalid" merge --no-ff <branch> -m "Merge <branch>" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"`
After each merge run `mise run check`, `mise run validate-assets`, `mise run sound-check` and `mise run balance-check`; stop and report if one fails. Then launch `plugin-tester` for an integration review on `main`. When it's `READY`, set **Status: done** in the brief, commit, and report the final summary. Never push.
