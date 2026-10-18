<!-- Opened by cookie-bakery[bot] via `mise run pr-open`; the user reviews, approves and merges. -->
## Summary
<!-- What the feature does, in player terms. -->

## Brief
[`docs/briefs/<name>.md`](../blob/feature/<name>/docs/briefs/<name>.md)

Acceptance criteria:
- [ ] …

## Team contributions
<!-- Per agent: what they did, then their commits from `git log main..HEAD --format="%an: %s"`. -->
| Member | What they did | Commits |
|---|---|---|
| Rickroll Rick (orchestrator) | brief, merges, integration | … |

## Checks
| Check | Result |
|---|---|
| `mise run check` (build + tests) | |
| `mise run validate-assets` | |
| `mise run sound-check` | |
| `mise run balance-check` | |
| Tests (count) | |

## Tester verdict
[`docs/reviews/<date>-<name>.md`](../blob/feature/<name>/docs/reviews/<date>-<name>.md): **READY / NOT READY**
<!-- 2–3 line summary. -->

## Needs human review
<!-- Run `mise run review` and open design/review/index.html for all of the below. -->
- **Textures / models:** paths
- **Sounds:** paths to the .ogg files
- **Effects:** preview paths
- **Balance changes:** before → after numbers (from docs/balance/report-<date>.md)
- **Config keys added:** file: key

## How to test in-game
1. Build with `mise run build` and copy `target/CookieClicker-*.jar` to the test server's `plugins/`.
2. Zip the pack with `mise run pack`; host it and set the URL + SHA-1 in config.
3. …commands and steps…

## Out of scope / follow-ups
- …

<!-- Draft PR? List the open issues here, per owner. -->

🤖 Generated with [Claude Code](https://claude.com/claude-code)
