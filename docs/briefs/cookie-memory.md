# cookie-memory

**Status:** in progress  <!-- planned | in progress | review | done -->
**Created:** 2026-10-20 · **Approved:** 2026-10-20 · **Depends on:** factory-makeover (merged, PR #6); repos moved to `CookieEmpireMC` (done)

## Goal
Move the team's shared brain (agents, skills, hooks, game design, workflow and the other cross-repo rules) into **`CookieEmpireMC/CookieMemory`**, packaged as a Claude Code plugin with its own marketplace. `CookieClicker` and `CookieCore` (and later the resource pack loader, GDD §9.1) then load the same team and context, for the user and their friend alike, without copies drifting apart.

## Player story
none (team tooling). For the user: "I open CookieClicker or CookieCore, Claude asks once to install the Cookie Empire team, and from then on `/feature`, Rick, Wesly, Pat, Gandalf and the rest work the same in both, and know the full game design."

## Acceptance criteria
**CookieMemory (plugin + marketplace)**
- [ ] `CookieEmpireMC/CookieMemory` contains a marketplace (`.claude-plugin/marketplace.json`) with one plugin `bakery-team` (`.claude-plugin/plugin.json`, versioned).
- [ ] The plugin ships the 7 agents, the `/feature` skill, and the ownership hook (code + hook registration via `${CLAUDE_PLUGIN_ROOT}`), behaving exactly as today.
- [ ] A `bakery-context` skill that every agent preloads holds: `game-design.md` (full GDD, §1–10), `workflow.md`, the team table (names, emails, mottos), `minimessage.md`, the Paper version references, the ownership model, and the "be decisive, no repeated loops" rule.
- [ ] All 7 agents can be launched in every repo that enables `bakery-team`; each writes its memory to **that repo's** `.claude/agent-memory/<agent_type>/` (Decision 3). If plugin agents can't do that, the research proposes the fix (e.g. a thin per-repo agent file).
- [ ] Each repo keeps its own facts: `CLAUDE.md` (what this repo is, its commands, its docs), `.claude/ownership.json`, `docs/briefs/`, `docs/reviews/`, `docs/architecture.md`, `.claude/agent-memory/`, code and assets.
- [ ] The CookieMemory README explains: install, update (bump the version, `/plugin update`), and how to change an agent or the GDD (a PR on CookieMemory, merged by the user).
- [ ] `main` on CookieMemory is protected like CookieClicker (1 approval + check job; the check validates the plugin and marketplace JSON and the agent frontmatter).

**CookieClicker**
- [ ] `.claude/settings.json` lists the marketplace (`extraKnownMarketplaces`) and enables `bakery-team`.
- [ ] `.claude/agents/`, `.claude/skills/feature/`, the hook code and `docs/workflow.md` are removed **only after** a verified run that loads them from the plugin (a dry-run `/feature` brief + one agent launch in a worktree + the ownership hook denying an out-of-scope write).
- [ ] `docs/game-design.md` moves to CookieMemory; CLAUDE.md points to the `bakery-context` skill instead.
- [ ] `tools/github_app` uses `CookieEmpireMC/CookieClicker` and the new app installation ID; `mise run pr-open` works in every repo (`--repo`).

**CookieCore**
- [ ] Starter repo: `CLAUDE.md` (empty plugin, purpose to be decided, uses CookieMemory), `.claude/settings.json` enabling `bakery-team`, `.claude/ownership.json`, `mise.toml` (same pins as CookieClicker), `.gitignore`, a CI workflow that passes on an empty project, `docs/briefs/TEMPLATE.md`, and `main` protection. No Java code yet.

**Quality**
- [ ] Opening either repo fresh (new clone, no `~/.claude` cache) shows the install prompt and then the same agents and skills.
- [ ] All checks green in CookieClicker after the removals; `ownership-check` still works (it reads the rules from the plugin or the repo, whichever the research recommends).

## Out of scope
- What CookieCore actually does (its first real feature comes later).
- The resource pack loader plugin (GDD §9.1): later, as a fourth repo that also enables `bakery-team`.
- Moving `resourcepack/` out of CookieClicker.
- Balance or gameplay changes (the GDD differences are listed in `game-design.md` → "Differences from the current build").

## Assets needed
none

## Balance impact
none

## Research needed
For `claude-code-guide` (blocking, before anything moves), with links to the docs:
1. Plugin and marketplace layout on the current Claude Code; `extraKnownMarketplaces` + `enabledPlugins` in project settings, and the install prompt for teammates; private GitHub marketplaces (auth for the user and their friend).
2. Which agent frontmatter fields work for **plugin** agents: `isolation: worktree`, `memory: project` (and where that memory then lives), `tools`, `model`, `skills`, `hooks`, `permissionMode`. If any is ignored, what's the workaround (e.g. keep a thin agent file in the repo)?
3. Whether plugin hooks can read the repo's `.claude/ownership.json` (the project dir env var) and run Python.
4. How a skill can preload into every agent (frontmatter `skills:`), and its size limits for the GDD.
5. Updating: does a version bump reach both repos automatically, or does everyone run `/plugin update`?

## Open questions
1. **cookie-bakery app:** install it on `CookieEmpireMC` (all repositories) and give Rick the installation ID; until then PRs can't be opened as the bot.
2. **Your friend:** add them to `CookieEmpireMC` (write access is enough) so they can install the private plugin. What role should they have?

## Decisions (user, 2026-10-20)
1. Organisation `CookieEmpireMC` with `CookieClicker` (this repo, moved and renamed), `CookieCore` (empty for now, uses the main repo), and `CookieMemory` (the main repo with the agents, gameplay memory and central rules).
2. The full GDD (§1–10, translated to English) is part of CookieMemory.
3. **Memory per repo, agents everywhere** (user): every repo keeps its own development memory (its `CLAUDE.md`, `.claude/agent-memory/<agent>/`, briefs, reviews, architecture doc) for what is specific to that repo; CookieMemory holds only what is shared. All 7 agents are available in every repo, even when a repo rarely needs one (e.g. sound-designer in CookieCore).

## Contract (fixed names; change only via the orchestrator)
| Kind | ID | Owner | Notes |
|---|---|---|---|
| marketplace | `cookie-memory` (repo `CookieEmpireMC/CookieMemory`) | orchestrator (user-approved) | |
| plugin | `bakery-team` | orchestrator (user-approved) | agents, skills, hooks; semver |
| skill | `bakery-context` | orchestrator | GDD, workflow, team, rules; preloaded by every agent |
| skill | `feature` | orchestrator (human-controlled) | unchanged behaviour |
| settings | `.claude/settings.json`: `extraKnownMarketplaces.cookie-memory`, `enabledPlugins["bakery-team@cookie-memory"]` | orchestrator (human-controlled) | in every repo |

## Tasks
| Agent | Task | Needs (gate) | Branch |
|---|---|---|---|
| claude-code-guide | research questions 1–5 | brief approved | — (note committed in CookieMemory) |
| orchestrator (Rick) | build CookieMemory (agents, skills, hooks, context skill, README, CI), CookieCore starter, settings in both repos; human-controlled, user approves | research | `main` of the empty repos via PR; `feature/cookie-memory` here |
| plugin-coder | `tools/github_app` repo/installation update, ownership-check reading rules per the research, mise tasks if paths change | research, app installation ID | `cookie-memory/plugin-coder` |
| plugin-tester | review the coder branch, then the end-to-end check in both repos | owner DONE | (reviewed branch) |

## Status
| Agent | Status | Branch | Verdict | Notes |
|---|---|---|---|---|
| claude-code-guide | running | — | — | research Q1–5 |
| orchestrator | waiting | | | on research |
| plugin-coder | waiting | | | on research + app installation ID |

## Merge order
1. CookieMemory PR (plugin) → the user merges.
2. CookieClicker `feature/cookie-memory`: settings + plugin-coder branch; removals last, after the verified run.
3. CookieCore starter PR.

## Needs human review
- [ ] Everything in CookieMemory (agents, skills, hooks: all human-controlled today).
- [ ] `.claude/settings.json` and `.claude/ownership.json` in both repos.
- [ ] The removals from CookieClicker's `.claude/`.
