---
name: ledger-tidy-config
description: tidy plugin-coder (config) branch review 2026-10-23: READY, tests added, paper-plugin class-loading facts verified from Paper source
metadata:
  type: project
---

Branch `tidy/plugin-tester-config` (coder branch merged + tests), 415 tests green. Report: `docs/reviews/2026-10-23-tidy-config.md`.

- New tests: `PluginDescriptorTest` (loader coords == pom props, paper-plugin.yml fields, permission == `BakeryCommand.PERMISSION`), `PluginSettingsOldConfigTest` (fixture `src/test/resources/config-1.2.1.yml` from git; `PluginSettings` records are value-equal old vs new), `MarkerTemplateTest`, `WarnOnceTest`.
- Verified in Paper source (2026-10-23, stable-ish): `PaperPluginClassLoader` group for a non-open Paper plugin = loaders that are *transitive* dependencies per `SimpleMetaDependencyTree` (hard + soft edges, `Graphs.reachableNodes`), so `com.sk89q.worldedit` is reachable through WorldGuard's `depend`. Not run in a real server.
- Gotchas: `VirtualComponentImpl` (gradient) is not `equals` across parses: compare plain text. `getResource("plugin.yml")` in tests finds WorldGuard's jar: check `Files.exists(src/main/resources/...)` instead.
- Sandbox: `T=path; cmd $T/...` and heredoc-heavy compound commands are refused in this worktree; use the Write tool and literal paths.
- Open LOWs: MarkerTemplate/WarnOnce not yet used by callers; stale "plugin.yml libraries" text in `DriverConnectionFactory`; messages.yml comment points at removed `station.panel.colors`.
