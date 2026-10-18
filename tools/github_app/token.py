"""Print a GitHub App installation token for ``cookie-bakery`` (and nothing else) on stdout.

    GH_TOKEN=$(mise run -q pr-token) gh api ...
    python tools/github_app/token.py [--config <env file>]

Settings: ``~/.config/cookieclicker/github-app.env`` (or ``--config`` / ``$GITHUB_APP_CONFIG``)
with ``APP_ID``, ``INSTALLATION_ID`` and ``KEY_PATH``; environment variables of the same names
override the file. ``$GITHUB_API_URL`` overrides the API base URL (tests only).
Errors go to stderr with a non-zero exit; they never contain the token or key material.
"""

import os
import sys


def _bootstrap():
    """Run as a script, this folder would shadow the stdlib ``token`` module (imported by
    ``tokenize``/``linecache``). Drop it from ``sys.path`` and import via ``tools/``."""
    here = os.path.dirname(os.path.abspath(__file__))
    sys.path[:] = [p for p in sys.path if os.path.abspath(p or os.curdir) != here]
    sys.path.insert(0, os.path.dirname(here))


if __name__ == "__main__":
    _bootstrap()

import argparse  # noqa: E402

from github_app import app  # noqa: E402
from github_app.cli import fail  # noqa: E402

PROG = "pr-token"


def parse_args(argv=None):
    parser = argparse.ArgumentParser(
        prog=PROG, description="Print a cookie-bakery GitHub App installation token.")
    parser.add_argument("--config", help=f"settings file (default: ${app.CONFIG_ENV_VAR} or "
                                         f"{app.DEFAULT_CONFIG_PATH})")
    return parser.parse_args(argv)


def main(argv=None, environ=None, stdout=None, stderr=None):
    stdout = sys.stdout if stdout is None else stdout
    stderr = sys.stderr if stderr is None else stderr
    args = parse_args(argv)
    try:
        token = app.get_installation_token(args.config, environ)
    except app.GitHubAppError as exc:
        return fail(PROG, exc, stderr)
    stdout.write(token + "\n")
    stdout.flush()
    return 0


if __name__ == "__main__":
    sys.exit(main())
