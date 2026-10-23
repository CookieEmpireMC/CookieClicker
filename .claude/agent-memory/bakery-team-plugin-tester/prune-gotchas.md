---
name: prune-gotchas
description: pitfalls found while pruning tests (2026-10-23): sandbox git check, Java/Python method removal, counts after prune
metadata:
  type: reference
---

**Sandbox (verified 2026-10-23):** Bash calls that contain `git` together with a shell `for`/`while` loop, a `$VAR`
expansion, a heredoc, or an output redirect (`> file`) are refused as "too complex to verify". Use plain single commands
with literal paths; pipes (`| grep | tail`) are fine. Prefer Write for scripts, then `python3 -I <script>`.

**Method removal (Java):** a naive "remove unused private helper" pass also removes `@Override` methods inside anonymous
classes in `setUp` (they look unused) and silently changes behaviour. Only remove 4-space-indented `private`/`static`
helpers, and skip anything preceded by `@Override`. Verify with `git diff -U0 | grep "^-.*Override"`.

**Method removal (Python):** a body containing a multi-line triple-quoted string (YAML at column 0) breaks an
indent-based end finder. Track `"""` parity. Same method name in two classes of one file: address it as `Class::method`.

**Counts after the 2026-10-23 prune:** Java 405 executed (vfx 104, non-vfx 301; top-level 22, plot 45, house 62,
economy 43, view 43, storage 15). Tools 73 (balance 18, github_app 20, ownership 29, house_design 6). Baseline was 686 / 146.
Use `grep -h "Tests run" target/surefire-reports/*.txt` for per-class counts.

**Fixtures:** `src/test/resources/fixtures/factory-makeover-fence.yml` is still used (FenceStyleTest); the
`factory-makeover-factory.yml` fixture was removed with its only test.
