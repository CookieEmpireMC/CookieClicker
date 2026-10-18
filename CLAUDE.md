# Project guide for Claude

This is a **Paper Minecraft plugin**, built with **Maven** (Paper API only, no NMS).
The target version is whatever `paper-api` version `pom.xml` declares: either **1.21.x** or **26.x**.

## Docs (in `docs/`)

| File | Covers | Read when |
|---|---|---|
| `paper-121x-reference.md` | Java 21, Adventure 4, item model changes across 1.21.x, rules, supporting both versions in one jar | `pom.xml` has `1.21.x-R0.1-SNAPSHOT` |
| `paper-26x-reference.md` | Java 25, new version strings, unobfuscated server, Adventure 5, world keys, API changes 26.1–26.3, rules | `pom.xml` has `26.x.build.…` |
| `toolchain.md` | mise: pinned Java/Maven per target, build/test tasks, worktree trust | before any build or test |
| `minimessage.md` | All player-facing text: MiniMessage, placeholders, security, fonts/glyphs, Adventure 5 removals | any feature that shows text |
| `integrations.md` | ItemsAdder & ModelEngine: compatibility, Maven, hooks, load events, resource packs | the project uses either plugin |
| `architecture.md` | package structure & conventions (maintained by plugin-tester) | always, if it exists |
| `research/*.md` | resource pack research notes (written by resource-pack-researcher) | before building a researched feature |
| `reviews/*.md` | test & review reports (written by plugin-tester) | after a review |

## Agents (in `dot-claude`)

| Agent | Role | Writes to |
|---|---|---|
| `resource-pack-researcher` | researches resource pack techniques for the target version | `docs/research/` only |
| `plugin-coder` | implements features | `src/main/`, `pom.xml`, config |
| `plugin-tester` | writes/runs tests, reviews architecture, gives a merge verdict | `src/test/`, `docs/reviews/`, `docs/architecture.md` |

Typical flow: researcher (in the background) → coder → tester → back to the coder if the tester reports issues.
Run parallel features in separate worktrees (`claude -w <feature>`) and merge them one at a time.

## Ground rules

- Detect the target version from `pom.xml` first, and follow only that version's reference.
- Java and Maven are pinned in `mise.toml` (Java 21 for 1.21.x, Java 25 for 26.x). Build with `mise run build`, test with `mise run test`, full check with `mise run check`. Never run plain `mvn`; use `mise exec -- mvn …`.
- Never shade Paper, Adventure/MiniMessage, ItemsAdder or ModelEngine; they use `provided` scope.
- If a feature needs something the target version or an integration doesn't support, stop and report it instead of working around it.
