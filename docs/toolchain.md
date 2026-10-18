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
