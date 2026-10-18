# Project guide for Claude

This is a **Paper Minecraft plugin**, built with **Maven** (Paper API only, no NMS).
The target version is whatever `paper-api` version `pom.xml` declares: either **1.21.x** or **26.x**.

## Docs (in `docs/`)

| File | Covers | Read when |
|---|---|---|
| `workflow.md` | who runs when: feature briefs, start gates, merge order, fix loop, report format | always, before launching or acting as any agent |
| `briefs/<feature>.md` | feature brief from `briefs/TEMPLATE.md`: goal, player story, acceptance criteria, contract (fixed IDs), tasks, status (planned / in progress / review / done); start one with `/feature <name> <description>` | before working on that feature |
| `paper-121x-reference.md` | Java 21, Adventure 4, item model changes across 1.21.x, rules, supporting both versions in one jar | `pom.xml` has `1.21.x-R0.1-SNAPSHOT` |
| `paper-26x-reference.md` | Java 25, new version strings, unobfuscated server, Adventure 5, world keys, API changes 26.1–26.3, rules | `pom.xml` has `26.x.build.…` |
| `toolchain.md` | mise: pinned Java/Maven/Python per target, build/test/asset tasks, worktree trust | before any build or test |
| `minimessage.md` | All player-facing text: MiniMessage, placeholders, security, fonts/glyphs, Adventure 5 removals | any feature that shows text |
| `integrations.md` | ItemsAdder & ModelEngine: compatibility, Maven, hooks, load events, resource packs | the project uses either plugin |
| `architecture.md` | package structure & conventions (maintained by plugin-tester) | always, if it exists |
| `research/*.md` | resource pack research notes (written by resource-pack-researcher) | before building a researched feature |
| `reviews/*.md` | test & review reports (written by plugin-tester) | after a review |
| `balance/model.md` | the economy formula contract (costs, CPS, clicks, unlocks) | before any economy code |
| `balance/targets.md` | the user's pacing targets (editable YAML block) | before tuning; when `balance-check` fails |
| `balance/report-*.md`, `balance/golden-values.json` | simulator reports; exact expected values for the Java economy | after tuning; when testing economy code |

## Design specs (in `design/`)

| Path | Covers | Read when |
|---|---|---|
| `design/<asset>.md` | per-asset spec & hand-off: IDs, model/animation names, glyphs, what's placeholder (model-designer) | before code uses a model, texture, animation or glyph |
| `design/sounds.md` | every sound event → key → files → feel → licence, plus what needs a human listen (sound-designer) | before code plays a sound |
| `design/effects.md` | every effect ID → look → when to use → cost → placeholders (vfx-designer) | before code plays an effect |
| `design/CREDITS.md` | source, author and licence of every external (CC0) asset | when adding or auditing third-party assets |
| `design/*style-guide.md`, `design/*lessons.md` | each designer's style rules and learning log | the designers read these before every job |
| `design/review/index.html` | local review page for the user (`mise run review`, git-ignored); a spec line `Status: final` marks an asset as final, otherwise it shows as placeholder | when the user reviews assets |
| `design/scripts/`, `design/sound-scripts/`, `design/vfx-scripts/` | asset generators (`mise run assets` / `sounds` / `vfx-previews`) | when regenerating assets |

## Plugin config owned by designers

| File | Covers | Owner |
|---|---|---|
| `src/main/resources/sounds.yml` | game event → sound key, vanilla fallback, volume, pitch range, source | sound-designer |
| `src/main/resources/effects.yml` | effect presets (layers) and performance `limits` | vfx-designer |
| `src/main/resources/buildings.yml`, `upgrades.yml` | all balance values (format in `docs/balance/model.md`) | balance-designer |

## Agents (in `dot-claude`)

| Agent | Role | Writes to |
|---|---|---|
| `resource-pack-researcher` | researches resource pack techniques for the target version | `docs/research/` only |
| `model-designer` | creates assets: models, item model definitions, .bbmodel, ItemsAdder YAML, placeholder textures, fonts/glyphs, pack.mcmeta | `resourcepack/` (see ownership), `itemsadder/`, `modelengine/`, `design/` (its files) |
| `sound-designer` | creates original .ogg sounds (synthesized or processed CC0), sounds.json, subtitles, sounds.yml | `resourcepack/assets/<ns>/{sounds,sounds.json,lang}`, `src/main/resources/sounds.yml`, `design/sound-*`, `design/sounds.md`, `design/CREDITS.md` |
| `vfx-designer` | builds the effects engine (particles, display-entity effects) and its presets | `src/main/java/…/vfx/` + its tests, `src/main/resources/effects.yml`, particle files in the pack, `design/vfx-*`, `design/effects.md` |
| `balance-designer` | economy numbers, formula contract, pacing simulator and reports | `buildings.yml`, `upgrades.yml`, `tools/balance/`, `docs/balance/` |
| `plugin-coder` | implements features | `src/main/` except designer-owned files, `pom.xml`, `plugin.yml`, other config |
| `plugin-tester` | writes/runs tests, reviews architecture, gives a merge verdict | `src/test/`, `docs/reviews/`, `docs/architecture.md` |

### The team (git identities)

Everyone commits under their own name, per commit (`git -c user.name=… -c user.email=… commit`), never by changing git config. Each agent file's "Commits" section has its exact command and rules; `mise run team` shows commits per member.

| Agent | Name | Email | Motto |
|---|---|---|---|
| main session (orchestrator, merges into feature branches) | Rickroll Rick | rick@bakery.invalid | never gonna push to main, never gonna merge your PR |
| resource-pack-researcher | Citation Cindy | cindy@bakery.invalid | [citation needed] is a lifestyle |
| plugin-coder | Works4Me Wesly | wesly@bakery.invalid | it compiled on my machine |
| plugin-tester | Gandalf | gandalf@bakery.invalid | no branch passes without a verdict |
| model-designer | Pixel Pat | pat@bakery.invalid | no mistakes, just happy little texels |
| sound-designer | Bassboost Bruno | bruno@bakery.invalid | every click deserves a drop |
| vfx-designer | Flare Larry | larry@bakery.invalid | if it doesn't sparkle, it isn't done |
| balance-designer | Stonks Stan | stan@bakery.invalid | number go up (on schedule) |

The orchestrator commits as Rickroll Rick (`git -c user.name="Rickroll Rick" -c user.email="rick@bakery.invalid" …`), including research notes and integration reviews on behalf of agents without a shell or branch (credited with `--author`). Rick merges agent branches into the feature branch and opens the pull request; **only the user merges into `main`**.

### Asset ownership
- **model-designer:** models, item model definitions, textures, fonts/glyphs, `pack.mcmeta`, `itemsadder/`, `modelengine/`
- **sound-designer:** `.ogg` files, `sounds.json`, subtitle lang files, `sounds.yml`
- **vfx-designer:** the `vfx` package and its tests, `effects.yml`, particle definitions and textures
- **balance-designer:** `buildings.yml`, `upgrades.yml` (later golden cookie/prestige values), `tools/balance/`, `docs/balance/`
- **plugin-coder:** everything else in `src/main/` (incl. wiring the `Effects` service and the single sound service that reads `sounds.yml`), `pom.xml`, `plugin.yml`, other config; tooling (`tools/` except `tools/balance/` and tool tests, `mise.toml`, `.gitignore`, `.gitattributes`, `.mailmap`, `design/requirements.txt`) and the reference docs (`docs/paper-*.md`, `docs/toolchain.md`, `docs/minimessage.md`, `docs/integrations.md`). `mise.toml` and `.gitattributes` changes always go under "Needs human review" in the PR.
- **plugin-tester:** `src/test/`, tool tests (`tools/**/test_*.py`), `docs/reviews/`, `docs/architecture.md`
- **orchestrator (Rick):** `CLAUDE.md`, `docs/briefs/`, `.github/`, merge commits
- **Human-controlled (no agent):** `.claude/agents/`, `.claude/skills/`, `docs/workflow.md`, `.claude/ownership.*`, `.claude/hooks/`, `.claude/settings.json`. Rick edits them only when the user explicitly asks, and every change goes under "Needs human review".
- Nobody edits another owner's files; ask the owner instead.
- **Agent memory:** every agent has `memory: project`, a private cache in `.claude/agent-memory/<agent_type>/` (its `MEMORY.md` loads at startup) for verified facts it would otherwise rediscover each run. Only that agent may write it; what to store and what not is in `docs/workflow.md` → "Agent memory".
- **Enforced** by `.claude/ownership.json` in two layers: a PreToolUse hook that denies `Edit`/`Write`/`NotebookEdit` outside an agent's globs (early warning; Bash-written files aren't covered), and `mise run ownership-check <branch>`, which checks every commit against its author's globs before Rick merges (the real gate; authorship is self-declared, so it catches mistakes, not tampering). Details in `docs/workflow.md`.

Every agent that writes code or assets runs in its own worktree (`isolation: worktree` in its frontmatter), branched from the feature branch, on branch `<name>/<agent>`. Agent branches are merged into the feature branch one at a time; the feature reaches `main` only through a pull request the user merges.

## Feature workflow (branches + pull requests)

Run a feature with **`/feature <name> <description>`** (`.claude/skills/feature/SKILL.md`); details and start gates are in `docs/workflow.md`.

1. **Branch:** Rickroll Rick (the main session) runs `git switch main && git pull && git switch -c feature/<name>` and stays on it, in the main checkout. He **never `cd`s into an agent worktree** (that moves the whole session); he uses `git -C <path>` and `mise -C <path> run …` / `--cd` instead.
2. **Brief:** drafts `docs/briefs/<name>.md` from the template, waits for the user's approval, commits it on the feature branch.
3. **Agents:** researcher first if needed; then model-designer, sound-designer, vfx-designer, balance-designer and coder in parallel (the coder uses new effect IDs and sound events from the brief while the designers add them; it waits only for a Java API that isn't on `main` yet, like the first `Effects` service), each in its own worktree **branched from the feature branch** (`.claude/settings.json`: `"worktree": { "baseRef": "head" }`), on branch `<name>/<agent>`, committing with its own identity. Agents never push, merge or touch `main`.
4. **Review + merge:** Gandalf reviews each agent branch; Rick runs `mise run ownership-check <name>/<agent>` (a violation goes back to the agent), merges `READY` branches into the feature branch with `git merge --no-ff` (assets and config before code), runs `check`, `validate-assets`, `sound-check` and `balance-check` after each merge, sends red checks back to the owner, resolves conflicts only where the owner is clear, then removes the merged worktree and branch (`git branch -d <name>/<agent>`, `git worktree prune`). Leftover `<name>/*` branches or worktrees are listed in the final summary.
5. **Pull request:** after Gandalf's integration review, Rick checks `gh auth status`, pushes `feature/<name>` (only `feature/*`, never force), and opens a PR **as `cookie-bakery[bot]`** with `mise run pr-open` (so the user can approve it; `main` requires 1 approval and the `check` job), filled from `.github/pull_request_template.md`: ready when everything is green and `READY`, otherwise draft with the open issues. He waits for CI with `gh pr checks --watch`, fixes failures through the owners, **opens the PR in the user's browser automatically** (`gh pr view --web`) once the feature is fully implemented, and gives the user the URL and a 3-line summary.
6. **Merge:** the user reviews and merges the PR. Rick never merges into `main`.

## Ground rules

- Detect the target version from `pom.xml` first, and follow only that version's reference.
- Java, Maven and Python are pinned in `mise.toml` (Java 21 for 1.21.x, Java 25 for 26.x). Build with `mise run build`, test with `mise run test`, full check with `mise run check`, check assets with `mise run validate-assets` and `mise run sound-check`, check pacing with `mise run balance-check`, zip the pack with `mise run pack`. Never run plain `mvn`; use `mise exec -- mvn …`. CI (`.github/workflows/ci.yml`) runs `check`, `validate-assets`, `sound-check` and `balance-check` on every push and PR, so run them locally before handing work back.
- Never shade Paper, Adventure/MiniMessage, ItemsAdder or ModelEngine; they use `provided` scope.
- Never hardcode sounds or particles in code: sounds go through `sounds.yml` events, effects through `Effects.play(id)`.
- Never set balance values in code: costs, CPS, multipliers and unlocks come only from `buildings.yml` / `upgrades.yml`, with the maths from `docs/balance/model.md`.
- If a feature needs something the target version or an integration doesn't support, stop and report it instead of working around it.
