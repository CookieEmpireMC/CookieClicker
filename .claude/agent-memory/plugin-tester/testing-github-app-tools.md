---
name: testing-github-app-tools
description: How to test tools/github_app (pr-token, pr-open) offline with a fake loopback server and throwaway openssl key
metadata:
  type: reference
---

Tests live in `tools/github_app/test_github_app.py`, run by `mise run tools-test` (unittest discover -s tools).
- Generate a throwaway key with `openssl genrsa`, chmod 600, in a temp dir; verify JWT signatures with `openssl dgst -sha256 -verify pub.pem`.
- Fake GitHub = `http.server` on 127.0.0.1 in a thread; point `$GITHUB_API_URL` at it (http is only allowed on loopback).
- Pass an `environ` dict to `token.main`/`open_pr.main` instead of touching os.environ. Always set `GITHUB_APP_CONFIG` to an EXISTING empty file: a nonexistent explicit config is an error, and an unset one falls back to the real ~/.config/cookieclicker file.
- Verified 2026-10-18: `REPO_PATTERN` accepts `..` segments (`--repo ../evil`), covered by an expectedFailure test.
