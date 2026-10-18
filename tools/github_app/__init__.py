"""GitHub App helpers: open feature PRs as the ``cookie-bakery[bot]`` App.

Standard library plus the ``openssl`` CLI only. The reusable, testable parts live in
``github_app.app``; ``token.py`` and ``open_pr.py`` are thin command-line entry points
(also exposed as the ``pr-token`` and ``pr-open`` mise tasks).
"""
