# cookie-memory: research (claude-code-guide, 2026-10-20)

Sources: code.claude.com/docs/en/{plugins/create-marketplace, plugins/marketplace-reference, plugins/manifest-reference, plugins/host-marketplace, plugins/install, plugins/loading, settings, settings-reference, sub-agents, skills, hooks, worktrees}.md

## Answers
1. **Layout:** `.claude-plugin/marketplace.json` (name `cookie-memory`, owner, `plugins: [{name: "bakery-team", source: "./plugins/bakery-team"}]`) + `plugins/bakery-team/.claude-plugin/plugin.json` + `agents/`, `skills/`, `hooks/hooks.json`. Project settings: `extraKnownMarketplaces.cookie-memory.source = {source: "github", repo: "CookieEmpireMC/CookieMemory"}` (+ optional `autoUpdate: true`), `enabledPlugins["bakery-team@cookie-memory"] = true`. Applies after folder trust; no documented install prompt. Private repo: the user's own git credentials (SSH key, or `gh auth setup-git`); `CLAUDE_CODE_PLUGIN_PREFER_HTTPS=1` for users without SSH.
2. **Plugin agent frontmatter:** `hooks`, `mcpServers`, `permissionMode` are **ignored** (none of our agents use them). `isolation`, `memory`, `tools`, `model`, `skills`: not listed as ignored (to test). Plugin agents are namespaced `bakery-team:<agent>`; a project agent with the same name overrides the plugin one (so the repo copies must be deleted).
3. **Hooks:** `hooks/hooks.json` with a top-level `hooks` key; `${CLAUDE_PLUGIN_ROOT}` for the script (changes per version: no state there), `${CLAUDE_PROJECT_DIR}` = main checkout (also in worktrees), so the map stays `$CLAUDE_PROJECT_DIR/.claude/ownership.json`. The script must load `ownership_lib.py` from its own directory.
4. **Skills:** `skills:` in agent frontmatter preloads the **full SKILL.md body** (not supporting files). No hard size cap. Don't set `disable-model-invocation` on a preloaded skill.
5. **Updates:** omit `version` everywhere → every commit is a new version; auto-update is off by default for third-party marketplaces (`autoUpdate: true` turns it on). `worktree.baseRef` stays a per-repo project setting.

## Risks to test before removing anything (UNCONFIRMED in the docs)
- (a) `agent_type` a plugin agent reports to PreToolUse hooks: `plugin-coder` or `bakery-team:plugin-coder`. If prefixed, the ownership lookup must strip `bakery-team:`.
- (b) the Agent tool's `subagent_type` syntax for plugin agents.
- (c) `memory: project` path for plugin agents (expected: the repo's `.claude/agent-memory/<name>/`; with the prefix it could become `bakery-team:plugin-coder/`).
- (d) `isolation: worktree` + `baseRef: head`. (e) `skills:` resolution (bare vs prefixed name). (f) plugin hooks firing for subagent tool calls.
- Side effect: `/feature` becomes `/bakery-team:feature`; docs and agents that name it must change.

## Decision (Rick)
- Two preloaded skills, full bodies (≈ 9k tokens per agent, accepted for robustness): `bakery-context` (team, workflow, ownership, rules) and `game-design` (the GDD).
- The ownership hook normalises `bakery-team:<agent>` → `<agent>`.
- Verify (a)–(f) with a headless run in a scratch clone of CookieCore before any removal from CookieClicker.
