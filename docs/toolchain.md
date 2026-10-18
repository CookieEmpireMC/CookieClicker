# Toolchain: Java & Maven versions with mise

Reference for all agents. Every project pins its **Java and Maven versions in a `mise.toml`** at the repo root, so the right JDK is used automatically: Java 21 for 1.21.x projects, Java 25 for 26.x projects.
mise docs: https://mise.jdx.dev

Last checked: 2026-10-18

---

## Rules for agents

1. **Always build and test through mise**, never plain `mvn`:
   - `mise run build` / `mise run test` / `mise run check` (tasks defined in `mise.toml`), or
   - `mise exec -- mvn <args>` for anything else.

   Agent shells are non-interactive, so mise's normal shell activation doesn't fire there. A plain `mvn` could pick up the wrong JDK (or none), while `mise run` / `mise exec` always load the versions and `JAVA_HOME` from `mise.toml`.
2. **Before the first build, run `mise install`.** It's a no-op if everything is already installed.
3. **The Java version in `mise.toml` must match the Paper target** (see the table below). If it doesn't, stop and report it; don't silently change the JDK.
4. **Don't edit `mise.toml`** unless the task is a toolchain change. Version bumps are a project decision.
5. Check what's active with `mise current` or `mise exec -- java -version` — not `which java` (that shows the shim, not the real JDK).

| Target (`paper-api` in pom.xml) | `java` in mise.toml | `maven.compiler.release` |
|---|---|---|
| `1.21.x-R0.1-SNAPSHOT` | `temurin-21` | 21 |
| `26.x.build.…` | `temurin-25` | 25 |

### Python for asset scripts

`mise.toml` also pins `python = "3.12"` for the designers' asset scripts. mise creates and activates a project-local venv (`.venv/`, git-ignored) via `[env] _.python.venv`, so `python`/`pip` inside `mise run` / `mise exec` always use it — no sudo, no system packages. Packages (Pillow, PyYAML, numpy, scipy, soundfile, pyloudnorm, matplotlib) are pinned in `design/requirements.txt` and installed by the `assets-deps` task, which only re-runs when that file changes. `soundfile`'s wheels bundle libsndfile with Ogg Vorbis, so no ffmpeg is needed. Each worktree gets its own `.venv` on first use.

| Task | Does |
|---|---|
| `mise run assets` | runs every `design/scripts/*.py` (models/textures/glyphs) |
| `mise run sounds` | runs every `design/sound-scripts/*.py` (regenerates all `.ogg` files) |
| `mise run sound-previews` | renders a waveform + spectrogram PNG per `.ogg` to `design/sound-previews/` (`design/sound-tools/render_previews.py`) |
| `mise run sound-check` | measures every `.ogg` (duration, channels, rate, peak, LUFS/RMS); fails on stereo outside `stereo/`, peaks above -1 dBFS, leading silence, an abrupt (unfaded) ending or a wrong format; loudness off-target is a warning (`tools/sound_check.py`) |
| `mise run vfx-previews` | runs the `*PreviewExport` JUnit tests (real shape code → `target/vfx-points/*.csv`), then `design/vfx-scripts/*.py` plots them to `design/vfx-previews/` |
| `mise run validate-assets` | checks every JSON/YAML/`.mcmeta`/`.bbmodel` parses; item definitions, models, textures and `sounds.json` entries resolve; `sounds.yml` keys exist, have a `minecraft:` fallback, volume ≤ 1.0 and a valid `source`; subtitle keys exist in `lang/en_us.json`; `effects.yml` presets have layers (`tools/validate_assets.py`) |
| `mise run balance` | simulates idle/casual/active play against `buildings.yml` + `upgrades.yml`; writes `docs/balance/report-<date>.md`, plots and `golden-values.json` (`tools/balance/`) |
| `mise run balance-check` | same simulation, writes nothing, fails if a target in `docs/balance/targets.md` is violated; passes when no balance config exists yet |
| `mise run review` | renders sound previews, then builds `design/review/index.html` (git-ignored, local only): every texture pixelated, every `.ogg` with a player and measurements, every effect preview, grouped per feature brief, with spec notes and a placeholder/final label (`tools/review.py`). An asset counts as final when its spec says `Status: final` |
| `mise run team` | commits per team member (`git shortlog -sn --all`) |
| `mise run pack` | zips `resourcepack/` reproducibly to `target/cookieclicker-pack.zip` and prints its SHA-1 (`tools/pack.py`) |
| `mise run ownership-check <branch> [--base <feature-branch>] [--human-approved]` | checks every non-merge commit in `<base>..<branch>` (base defaults to the current branch) by its **author email** against `.claude/ownership.json`; lists files that merge commits changed relative to every parent (conflict resolutions); fails with commit, author, file and owner per violation; unknown authors fail (`tools/ownership/check.py`, see below) |
| `mise run tools-test` | runs the Python tool tests: `python -m unittest discover -s tools -p 'test_*.py'` (test folders need an `__init__.py`); passes when there are no tests yet |
| `mise run -q pr-token` | prints a `cookie-bakery` GitHub App installation token and nothing else on stdout, for `GH_TOKEN=$(mise run -q pr-token) gh …`; errors go to stderr with a non-zero exit (`tools/github_app/token.py`, see below) |
| `mise run pr-open --head <branch> [--base main] --title <title> --body-file <file> [--draft] [--repo owner/name]` | opens the PR as `cookie-bakery[bot]` via the REST API and prints `url`, `number` and `author`; fails clearly if a PR for that head already exists or the App lacks permissions (`tools/github_app/open_pr.py`, see below) |

### Ownership enforcement

Who may edit which file is defined once, in `.claude/ownership.json` (human-controlled; format and glob rules in the docstring of `.claude/hooks/ownership_lib.py`). Two layers use it:

- **PreToolUse hook** (`.claude/hooks/ownership.py`, early warning): denies `Edit`/`Write`/`NotebookEdit` calls on files the agent doesn't own, with a reason that names the owner. Paths in `.claude/worktrees/<name>/` count as the same repo-relative path; paths outside the repository are always allowed. It loads the map and matcher only from `$CLAUDE_PROJECT_DIR` (the main checkout), so an agent's worktree copy can't loosen its rules. **Files written through Bash are not covered by the hook**, only by the merge check.
- **`mise run ownership-check`** (the real guarantee, run by the orchestrator before every merge): judges each commit by its author. Human-controlled files are a violation for agents; for the orchestrator, or with `--human-approved` when the user explicitly approved the change, they're listed for "Needs human review" instead. The map and matcher come from `$CLAUDE_PROJECT_DIR`, else the main checkout, else the current checkout (the output says which).
- **Authorship is self-declared** (`git -c user.email=…`), so both layers guard against mistakes, not against tampering.

### GitHub App PRs

Feature PRs are opened by the `cookie-bakery[bot]` GitHub App, so the user can approve them under `main`'s ruleset (authors can't approve their own PR). `tools/github_app/` uses the standard library and the `openssl` CLI only: it signs an RS256 App JWT (iat −60 s, exp +9 min, iss = App ID) with `openssl dgst -sha256 -sign`, exchanges it at `POST /app/installations/<id>/access_tokens`, and `open_pr.py` then calls `POST /repos/{owner}/{repo}/pulls`. It doesn't use `gh pr create`, which can fail with an App token.

- **Config file** (outside the repo, written by the user): `~/.config/cookieclicker/github-app.env`, one `KEY=VALUE` per line (`#` comments, `export` prefix and quotes allowed):
  ```
  APP_ID=5371158
  INSTALLATION_ID=172778981
  KEY_PATH=~/.config/cookieclicker/github-app.pem
  ```
  Environment variables with the same names override the file. `--config <file>` or `$GITHUB_APP_CONFIG` point at another file, and `$GITHUB_API_URL` replaces `https://api.github.com` (tests only; plain `http` only for localhost).
- **Key permissions:** the private key must be a regular file owned by you with no group/other access (`chmod 600`); anything else is refused before the key is used. Python never reads the key; only `openssl` does.
- **The token is never stored:** `pr-open` fetches it in memory and never prints it. `pr-token` prints it only on stdout, for a command substitution. Neither writes it to disk or logs, and error messages leave out the token, the JWT and key material.
- **Who runs it:** Rick runs `mise run pr-open …` at the end of `/feature`, allowed by a `Bash(mise run pr-open:*)` permission rule that the user adds themselves. If the rule is missing or the call is denied, Rick stops and gives the user the exact `! mise run pr-open …` command. He never falls back to the user's account. `pr-open` runs from the repo root, so a relative `--body-file` is read from there.


---

## Templates

Copy the one that matches the project to `mise.toml` in the repo root and commit it.

### 1.21.x project

```toml
[tools]
java = "temurin-21"
maven = "3.9"

[tasks.build]
description = "Compile, test and package the plugin jar"
run = "mvn -B package"

[tasks.test]
description = "Run unit tests"
run = "mvn -B test"

[tasks.check]
description = "Full clean build, as the tester runs it"
run = "mvn -B clean verify"
```

### 26.x project

```toml
[tools]
java = "temurin-25"
maven = "3.9"

[tasks.build]
description = "Compile, test and package the plugin jar"
run = "mvn -B package"

[tasks.test]
description = "Run unit tests"
run = "mvn -B test"

[tasks.check]
description = "Full clean build, as the tester runs it"
run = "mvn -B clean verify"
```

Notes:
- Always name the vendor (`temurin-…`), so every machine and worktree resolves the same JDK build.
- `"3.9"` means "latest 3.9.x". If `maven` doesn't resolve on your machine, run `mise registry maven` to see the available backends.
- `-B` (batch mode) keeps Maven output clean for agents.

### Optional: test server task

To start a local Paper server with the same JDK (paper.jar downloaded yourself into `run/`):

```toml
[tasks.server]
description = "Copy the plugin into the test server and start it"
depends = ["build"]
run = """
cp target/*.jar run/plugins/
cd run && java -Xms2G -Xmx2G -jar paper.jar --nogui
"""
```

Add `run/` to `.gitignore`.

---

## Supporting both 1.21.x and 26.x from one jar

Only when the project explicitly targets both (see section 5 of `paper-121x-reference.md`): compile with Java 21, then verify on 25.

```toml
[tools]
java = "temurin-21"
maven = "3.9"

[tasks.check-25]
description = "Build and test again on Java 25"
run = "mise exec java@temurin-25 -- mvn -B clean verify"
```

---

## One-time machine setup (human, not agents)

```bash
curl -fsSL https://mise.run | sh

# Interactive terminals: full activation (switches versions on cd)
echo 'eval "$(~/.local/bin/mise activate bash)"' >> ~/.bashrc

# Login shells / IDEs / other programs: shims on PATH
# On Pop!_OS/Ubuntu, add this to ~/.profile — don't create a new ~/.bash_profile, it would stop ~/.profile from loading
echo 'eval "$(~/.local/bin/mise activate bash --shims)"' >> ~/.profile
```

Log out and back in once so the `~/.profile` change applies.

### Trust & worktrees

mise only loads a `mise.toml` it trusts. Worktrees (`../dot-claude`) are new paths, so trust your whole projects folder once in `~/.config/mise/config.toml`:

```toml
[settings]
trusted_config_paths = ["~/projects"]
```

Adjust the path to wherever your plugin repos live. Installed JDKs are shared across all projects and worktrees, so nothing downloads twice.

### IDE (IntelliJ)

Point the project SDK at the mise JDK: `mise where java` prints the path, for example `~/.local/share/mise/installs/java/temurin-25...`. Restart the IDE after switching versions.
