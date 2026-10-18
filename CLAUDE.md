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
| main session (orchestrator, merges) | Such Merge Doge | doge@bakery.invalid | such merge, very bake, wow |
| resource-pack-researcher | Citation Needed Cindy | cindy@bakery.invalid | [citation needed] is a lifestyle |
| plugin-coder | Works-On-My-Machine Wes | wes@bakery.invalid | it compiled on my machine |
| plugin-tester | You-Shall-Not-Pass Gandalf | gandalf@bakery.invalid | no branch passes without a verdict |
| model-designer | Happy Little Pixels Pat | pat@bakery.invalid | no mistakes, just happy little texels |
| sound-designer | Bass Boosted Bruno | bruno@bakery.invalid | every click deserves a drop |
| vfx-designer | Lens Flare Larry | larry@bakery.invalid | if it doesn't sparkle, it isn't done |
| balance-designer | Stonks Stan | stan@bakery.invalid | number go up (on schedule) |

The orchestrator commits as Such Merge Doge (`git -c user.name="Such Merge Doge" -c user.email="doge@bakery.invalid" …`), including research notes and integration reviews on behalf of agents without a shell or branch (credited with `--author`). Merges to `main` happen only after the user approves them.

Flow (details and start gates in `docs/workflow.md`): orchestrator writes the feature brief → researcher (in the background) → model-designer + sound-designer + vfx-designer + balance-designer + coder in parallel, each in its own worktree → tester per branch → orchestrator merges in order → tester integration review.

**The main session is the orchestrator** (run a feature with `/feature`, see `.claude/skills/feature/SKILL.md`): it writes `docs/briefs/<feature>.md` and gets the user's approval before launching agents, launches each agent only when its gate in `docs/workflow.md` is met, commits research notes, merges `READY` branches one at a time after the user approves (assets → vfx → coder), runs `mise run check`, `validate-assets` and `sound-check` on `main` after each merge, updates the brief's status table, and routes every `REQUESTS` / `CONTRACT CHANGES` item from agent reports to the right owner.

### Asset ownership
- **model-designer:** models, item model definitions, textures, fonts/glyphs, `pack.mcmeta`, `itemsadder/`, `modelengine/`
- **sound-designer:** `.ogg` files, `sounds.json`, subtitle lang files, `sounds.yml`
- **vfx-designer:** the `vfx` package and its tests, `effects.yml`, particle definitions and textures
- **balance-designer:** `buildings.yml`, `upgrades.yml` (later golden cookie/prestige values), `tools/balance/`, `docs/balance/`
- **plugin-coder:** everything else in `src/main/` (incl. wiring the `Effects` service and the single sound service that reads `sounds.yml`), `pom.xml`, `plugin.yml`, other config
- Nobody edits another owner's files; ask the owner instead.

Every agent that writes code or assets runs in its own worktree (`isolation: "worktree"` when launched by the orchestrator, or `claude -w <feature>` by hand), on branch `<feature>/<agent>`. Branches are merged into `main` one at a time.

## Ground rules

- Detect the target version from `pom.xml` first, and follow only that version's reference.
- Java, Maven and Python are pinned in `mise.toml` (Java 21 for 1.21.x, Java 25 for 26.x). Build with `mise run build`, test with `mise run test`, full check with `mise run check`, check assets with `mise run validate-assets` and `mise run sound-check`, check pacing with `mise run balance-check`, zip the pack with `mise run pack`. Never run plain `mvn`; use `mise exec -- mvn …`.
- Never shade Paper, Adventure/MiniMessage, ItemsAdder or ModelEngine; they use `provided` scope.
- Never hardcode sounds or particles in code: sounds go through `sounds.yml` events, effects through `Effects.play(id)`.
- Never set balance values in code: costs, CPS, multipliers and unlocks come only from `buildings.yml` / `upgrades.yml`, with the maths from `docs/balance/model.md`.
- If a feature needs something the target version or an integration doesn't support, stop and report it instead of working around it.
