# Workflow: who runs when

Reference for all agents and the orchestrator. Agents can't see or wait for each other while running, so all coordination goes through **files on `main`** and **reports to the orchestrator**. Each agent checks its start conditions, does its job, and reports in a fixed format. The orchestrator launches the next agents.

---

## Roles

| Role | Who | Works in |
|---|---|---|
| **Orchestrator** | the main Claude session talking to the user | `main` checkout |
| resource-pack-researcher | agent | `main` checkout, `docs/research/` only (no worktree; the orchestrator commits its notes) |
| model-designer, sound-designer, vfx-designer, balance-designer, plugin-coder | agents | their own worktree, branch `<feature>/<agent>` |
| plugin-tester | agent | the worktree of the branch it reviews (only after that branch's owner has stopped), or `main` for an integration review |

The orchestrator is the only one who writes feature briefs, merges branches into `main` (only after the user approves), and decides between conflicting requests. Nothing is pushed. Everyone commits under their own git identity; see "The team" in `CLAUDE.md` and the Commits section of each agent file.

---

## The feature brief: the contract everyone builds against

Before launching agents for a feature, the orchestrator writes `docs/briefs/<feature>.md` and commits it to `main`:

```markdown
# <feature>
Goal: …
Depends on: <other features whose code/API must already be on main>, or none
Needs research: yes (docs/research/<topic>.md) / no

## Contract (fixed names; change only via the orchestrator)
| Kind | ID | Owner | Notes |
|---|---|---|---|
| item model | cookieclicker:big_cookie | model-designer | ItemDisplay, ~1 block |
| sound event | cookie_click | sound-designer | key cookieclicker:ui.cookie_click, fallback minecraft:block.wood.hit |
| effect | cookie_click | vfx-designer | placeholders: amount |
| message key | cookie.click-hud | plugin-coder | in messages.yml |

## Tasks
| Agent | Task | Branch |
|---|---|---|

## Status
| Agent | Status | Branch | Verdict |
|---|---|---|---|
```

- **Contract first.** Everyone uses exactly these IDs. That's what lets designers and the coder work in parallel: the coder references `cookie_click` before the sound exists, and the sound-designer delivers exactly that name.
- Agents **never edit the brief**. If an ID is wrong or missing, propose the change under `CONTRACT CHANGES` in your report; the orchestrator updates the brief.
- Only the orchestrator updates the **Status** table, based on reports.

---

## Phases of a feature

```
1. Brief          orchestrator writes docs/briefs/<f>.md (user approves)
2. Research       researcher → docs/research/<topic>.md        gate: note on main (if needed)
3. Build          in parallel, each in its own worktree:       gate: all DONE
                    model-designer · sound-designer · vfx-designer
                    balance-designer · plugin-coder
4. Review         plugin-tester per branch → READY / NOT READY (+ fix loop with the owner)
5. Merge          orchestrator merges READY branches one at a time, in order, after the user approves
6. Integration    plugin-tester reviews main for the whole feature
```

### Start conditions (gates)

| Agent | Can start when | Does NOT wait for |
|---|---|---|
| researcher | the brief exists (or the orchestrator asks directly) | anyone |
| model-designer | brief contract lists its assets; research note on `main` if the brief says "Needs research: yes" | coder, other designers |
| sound-designer | same as model-designer, for sound events | coder, other designers |
| vfx-designer | same, for effect IDs; and any item model its effects use is in the contract | coder, other designers |
| balance-designer | the brief lists a balance impact (new buildings/upgrades/prices/rewards) | coder, other designers |
| plugin-coder | brief exists; research note on `main` if needed; **every Java API from another owner it calls is already on `main`** (e.g. the `Effects` service in the `vfx` package) and every feature in "Depends on" is merged | designers' assets and final balance numbers (code against contract IDs and `docs/balance/model.md`; vanilla fallbacks cover missing assets) |
| plugin-tester (branch) | that branch's owner reported `DONE` and has stopped | other branches |
| plugin-tester (integration) | all branches of the feature are merged into `main` | — |

**Java code dependencies are the one hard wait.** Assets can arrive later because of IDs and fallbacks, but code can't compile against an API that isn't on `main`. So, for example, the vfx engine's `Effects` service must be merged before any coder task calls it.

### If a gate isn't met
Stop and report `STATUS: BLOCKED` with exactly what you're waiting for. Don't invent IDs, stub another owner's code, or edit files you don't own to get unblocked.

### Merge order (orchestrator)
The orchestrator proposes the merge order with the tester's verdicts and merges only after the user approves, as Such Merge Doge (`git -c user.name="Such Merge Doge" -c user.email="doge@bakery.invalid" merge --no-ff <branch>`). Within a feature, merge in this order so `main` always validates:
1. research notes (docs only, committed as they land)
2. model-designer, then sound-designer (assets + `sounds.yml`)
3. vfx-designer (engine + `effects.yml`)
4. balance-designer (`buildings.yml`, `upgrades.yml`, model, golden values)
5. plugin-coder
6. tester's test-only changes, if they're on a separate branch

After every merge, run `mise run check`, `mise run validate-assets`, `mise run sound-check` and `mise run balance-check` on `main`. If something fails, fix it before the next merge. Later agents start from the updated `main`.

### Fix loop
`NOT READY` goes back to the branch owner, in the same worktree and branch, with the tester's findings. When the owner reports `DONE` again, the tester re-reviews only what changed. Max 3 rounds; after that the orchestrator asks the user.

---

## Rules for every agent

- **Read first:** `docs/workflow.md` (this file) and the feature brief you were given (`docs/briefs/<feature>.md`).
- **Own files only.** See "Asset ownership" in `CLAUDE.md`. Need something from another owner? Put it under `REQUESTS` in your report.
- **Git:** follow the Commits section of your agent file: your own identity per commit, only your own files, only on your branch, small commits, checks first. Never commit to `main`, merge, rebase, amend, force or push. The researcher has no shell; the orchestrator commits its notes credited to it.
- **Don't wait in a loop.** If you're blocked, report and stop. The orchestrator re-launches you when the gate is met.

### Report format (end every run with this)

```
STATUS: DONE | BLOCKED | NEEDS-INPUT
FEATURE: <feature>
BRANCH: <branch>, or "main (docs only)"
PRODUCED: files, plus the IDs/names others will use
CONTRACT CHANGES: proposed changes to the brief's contract, or none
REQUESTS: <owner>: <what you need from them>, or none
WAITING FOR: (only if BLOCKED) the exact gate that isn't met
NEXT: who can start now because of this work
```

The tester also adds `VERDICT: READY | NOT READY` and, at integration, a `MERGE ORDER` line.
