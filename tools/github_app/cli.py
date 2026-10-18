"""Shared command-line helpers for ``token.py`` and ``open_pr.py``."""


def fail(prog, exc, stderr):
    """Print ``<prog>: error: <message>`` to stderr and return exit code 1.

    Only messages of the package's own exceptions are printed; they are built without the
    token or key material."""
    stderr.write(f"{prog}: error: {exc}\n")
    stderr.flush()
    return 1
