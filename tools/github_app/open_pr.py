"""Open a pull request as the ``cookie-bakery[bot]`` GitHub App.

    mise run pr-open --head feature/<name> --base main --title "<title>" --body-file <file> [--draft]
    python tools/github_app/open_pr.py ... [--repo CookieEmpireMC/CookieClicker] [--config <env file>]

Gets an installation token internally (never printed or stored), POSTs
``/repos/{repo}/pulls`` and prints ``url``, ``number`` and ``author`` on stdout.
Fails clearly when a PR for the head branch already exists (HTTP 422) or the App lacks
permissions. Settings as for ``token.py``; ``--body-file -`` reads the body from stdin.
"""

import os
import sys


def _bootstrap():
    """Keep this folder off ``sys.path`` (its ``token.py`` would shadow the stdlib module)
    and import the package via ``tools/``."""
    here = os.path.dirname(os.path.abspath(__file__))
    sys.path[:] = [p for p in sys.path if os.path.abspath(p or os.curdir) != here]
    sys.path.insert(0, os.path.dirname(here))


if __name__ == "__main__":
    _bootstrap()

import argparse  # noqa: E402

from github_app import app  # noqa: E402
from github_app.cli import fail  # noqa: E402

PROG = "pr-open"


def parse_args(argv=None):
    parser = argparse.ArgumentParser(
        prog=PROG, description="Open a pull request as the cookie-bakery[bot] GitHub App.")
    parser.add_argument("--head", required=True, help="branch to merge from, e.g. feature/<name>")
    parser.add_argument("--base", default="main", help="branch to merge into (default: main)")
    parser.add_argument("--title", required=True, help="pull request title")
    parser.add_argument("--body-file", required=True,
                        help="file with the pull request body (Markdown); '-' reads stdin")
    parser.add_argument("--draft", action="store_true", help="open as a draft pull request")
    parser.add_argument("--repo", default=app.DEFAULT_REPO,
                        help=f"owner/name (default: {app.DEFAULT_REPO})")
    parser.add_argument("--config", help=f"settings file (default: ${app.CONFIG_ENV_VAR} or "
                                         f"{app.DEFAULT_CONFIG_PATH})")
    return parser.parse_args(argv)


def read_body(path, stdin=None):
    if path == "-":
        return (sys.stdin if stdin is None else stdin).read()
    try:
        with open(path, encoding="utf-8") as fh:
            return fh.read()
    except FileNotFoundError:
        raise app.ConfigError(f"body file not found: {path}") from None
    except (OSError, UnicodeDecodeError) as exc:
        raise app.ConfigError(f"cannot read body file {path}: {exc}") from None


def main(argv=None, environ=None, stdout=None, stderr=None, stdin=None):
    environ = os.environ if environ is None else environ
    stdout = sys.stdout if stdout is None else stdout
    stderr = sys.stderr if stderr is None else stderr
    args = parse_args(argv)
    try:
        app.validate_repo(args.repo)
        body = read_body(args.body_file, stdin)
        app.pull_request_payload(args.head, args.base, args.title, body, args.draft)
        base_url = app.api_base_url(environ)
        token = app.get_installation_token(args.config, environ)
        pr = app.create_pull_request(token, args.repo, args.head, args.base, args.title, body,
                                     draft=args.draft, base_url=base_url)
    except app.GitHubAppError as exc:
        return fail(PROG, exc, stderr)
    stdout.write(f"url: {pr['url']}\nnumber: {pr['number']}\nauthor: {pr['author']}\n")
    stdout.flush()
    return 0


if __name__ == "__main__":
    sys.exit(main())
