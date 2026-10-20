# Project guide for Claude

This is a **Paper Minecraft plugin**, built with **Maven** (Paper API only, no NMS).
The target version is whatever `paper-api` version `pom.xml` declares: either **1.21.x** or **26.x**.

## Shared team and knowledge (plugin `bakery-team`)
The team comes from the **`bakery-team`** plugin of [CookieEmpireMC/CookieMemory](https://github.com/CookieEmpireMC/CookieMemory), enabled in `.claude/settings.json`. It is shared with CookieCore and every later Cookie Empire repo:
- agents run as `bakery-team:<agent>` (e.g. `subagent_type: "bakery-team:plugin-coder"`)
- `/bakery-team:feature <name> <description>` runs a feature end to end
- every agent preloads two skills: **`bakery-context`** (the team and git identities, ownership model, the full workflow: briefs, gates, merge order, fix loop, report format, agent memory, ground rules) and **`game-design`** (the user's full game design document, §1–10, plus its "Differences from the current build")
- the ownership hook ships with the plugin and reads **this repo's** `.claude/ownership.json`

Shared knowledge changes only through a PR on CookieMemory that the user merges. Everything below is specific to CookieClicker and stays here: this file, `.claude/ownership.json`, `docs/`, `design/`, and the agents' memory for this repo in `.claude/agent-memory/bakery-team-<agent>/`.

## Docs (in `docs/`)

| File | Covers | Read when |
|---|---|---|
| `briefs/<feature>.md` | feature brief from `briefs/TEMPLATE.md`: goal, player story, acceptance criteria, contract (fixed IDs), tasks, status (planned / in progress / review / done); start one with `/bakery-team:feature <name> <description>` | before working on that feature |
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

## Agents (from the plugin) and what they write here

| Agent | Role | Writes to |
|---|---|---|
| `resource-pack-researcher` | researches resource pack techniques for the target version | `docs/research/` only |
| `model-designer` | creates assets: models, item model definitions, .bbmodel, ItemsAdder YAML, placeholder textures, fonts/glyphs, pack.mcmeta, house design files | `resourcepack/` (see ownership), `itemsadder/`, `modelengine/`, `src/main/resources/houses/`, `design/` (its files) |
| `sound-designer` | creates original .ogg sounds (synthesized or processed CC0), sounds.json, subtitles, sounds.yml | `resourcepack/assets/<ns>/{sounds,sounds.json,lang}`, `src/main/resources/sounds.yml`, `design/sound-*`, `design/sounds.md`, `design/CREDITS.md` |
| `vfx-designer` | builds the effects engine (particles, display-entity effects) and its presets | `src/main/java/…/vfx/` + its tests, `src/main/resources/effects.yml`, particle files in the pack, `design/vfx-*`, `design/effects.md` |
| `balance-designer` | economy numbers, formula contract, pacing simulator and reports | `buildings.yml`, `upgrades.yml`, `tools/balance/`, `docs/balance/` |
| `plugin-coder` | implements features | `src/main/` except designer-owned files, `pom.xml`, `plugin.yml`, other config |
| `plugin-tester` | writes/runs tests, reviews architecture, gives a merge verdict | `src/test/`, `docs/reviews/`, `docs/architecture.md` |

### The team (git identities)
Names, emails, mottos and the commit rules are in the `bakery-context` skill. The orchestrator (main session) is **Rickroll Rick** (`rick@bakery.invalid`); he merges agent branches into the feature branch and opens the pull request; **only the user merges into `main`**. `mise run team` shows commits per member.

### Asset ownership
- **model-designer:** models, item model definitions, textures, fonts/glyphs, `pack.mcmeta`, `itemsadder/`, `modelengine/`, house design files (`src/main/resources/houses/`)
- **sound-designer:** `.ogg` files, `sounds.json`, subtitle lang files, `sounds.yml`
- **vfx-designer:** the `vfx` package and its tests, `effects.yml`, particle definitions and textures
- **balance-designer:** `buildings.yml`, `upgrades.yml` (later golden cookie/prestige values), `tools/balance/`, `docs/balance/`
- **plugin-coder:** everything else in `src/main/` (incl. wiring the `Effects` service and the single sound service that reads `sounds.yml`), `pom.xml`, `plugin.yml`, other config; tooling (`tools/` except `tools/balance/` and tool tests, `mise.toml`, `.gitignore`, `.gitattributes`, `.mailmap`, `design/requirements.txt`) and the reference docs (`docs/paper-*.md`, `docs/toolchain.md`, `docs/minimessage.md`, `docs/integrations.md`). `mise.toml` and `.gitattributes` changes always go under "Needs human review" in the PR.
- **plugin-tester:** `src/test/`, tool tests (`tools/**/test_*.py`), `docs/reviews/`, `docs/architecture.md`
- **orchestrator (Rick):** `CLAUDE.md`, `docs/briefs/`, `.github/`, merge commits
- **Human-controlled (no agent):** `.claude/ownership.*`, `.claude/settings.json` (and the shared team in CookieMemory). Rick edits them only when the user explicitly asks, and every change goes under "Needs human review".
- Nobody edits another owner's files; ask the owner instead.
- **Agent memory:** every agent has `memory: project`, a private cache in `.claude/agent-memory/bakery-team-<agent>/` (its `MEMORY.md` loads at startup) for verified facts about **this repo** it would otherwise rediscover each run. Only that agent may write it; what to store and what not is in the `bakery-context` skill → "Agent memory".
- **Enforced** by `.claude/ownership.json` in two layers: the plugin's PreToolUse hook that denies `Edit`/`Write`/`NotebookEdit` outside an agent's globs (early warning; Bash-written files aren't covered), and `mise run ownership-check <branch>`, which checks every commit against its author's globs before Rick merges (the real gate; authorship is self-declared, so it catches mistakes, not tampering). The check loads its matcher from `tools/ownership/ownership_lib.py`, a copy of the plugin's (keep them identical). Details in the `bakery-context` skill.

Every agent that writes code or assets runs in its own worktree (`isolation: worktree` in its frontmatter), branched from the feature branch, on branch `<name>/<agent>`. Agent branches are merged into the feature branch one at a time; the feature reaches `main` only through a pull request the user merges.

## Feature workflow (branches + pull requests)
Run a feature with **`/bakery-team:feature <name> <description>`**; the steps, gates and merge order are in the `bakery-context` skill. Repo-specific parts: agent worktrees branch from the feature branch (`.claude/settings.json`: `"worktree": { "baseRef": "head" }`); after each merge Rick runs `check`, `validate-assets`, `sound-check` and `balance-check` (and `tools-test` when tools change); PRs are opened as `cookie-bakery[bot]` with `mise run pr-open` (default repo `CookieEmpireMC/CookieClicker`; `--repo` for the others).

## Ground rules

- Detect the target version from `pom.xml` first, and follow only that version's reference.
- Java, Maven and Python are pinned in `mise.toml` (Java 21 for 1.21.x, Java 25 for 26.x). Build with `mise run build`, test with `mise run test`, full check with `mise run check`, check assets with `mise run validate-assets` and `mise run sound-check`, check pacing with `mise run balance-check`, zip the pack with `mise run pack`. Never run plain `mvn`; use `mise exec -- mvn …`. CI (`.github/workflows/ci.yml`) runs `check`, `validate-assets`, `sound-check` and `balance-check` on every push and PR, so run them locally before handing work back.
- Never shade Paper, Adventure/MiniMessage, ItemsAdder or ModelEngine; they use `provided` scope.
- Never hardcode sounds or particles in code: sounds go through `sounds.yml` events, effects through `Effects.play(id)`.
- Never set balance values in code: costs, CPS, multipliers and unlocks come only from `buildings.yml` / `upgrades.yml`, with the maths from `docs/balance/model.md`.
- If a feature needs something the target version or an integration doesn't support, stop and report it instead of working around it.
