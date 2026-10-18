"""File-ownership matcher shared by the PreToolUse hook and the merge-time check.

Standard library only. This module is human-controlled: the hook and
``tools/ownership/check.py`` load it from the main checkout
(``$CLAUDE_PROJECT_DIR``), never from an agent's worktree.

Ownership map (``.claude/ownership.json``)
------------------------------------------
::

    {
      "orchestrator": {"name": "...", "email": "...", "paths": ["<glob>", ...]},
      "agents": {
        "<agent_type>": {"name": "...", "email": "...", "paths": ["<glob>", ...]}
      },
      "human_controlled": ["<glob>", ...]
    }

Keys starting with ``_`` (e.g. ``_comment``) are ignored everywhere.

Globs (matched against repo-relative POSIX paths, case-sensitive):

* ``**`` as a whole segment matches any number of segments, including none
  (``src/**/vfx/**`` matches ``src/vfx/A.java``).
* ``*`` matches within one segment and never crosses ``/``
  (``design/*.md`` matches ``design/a.md`` but not ``design/x/a.md``).
  ``?`` and ``[...]`` work as in :mod:`fnmatch`, also within one segment.
* A leading ``!`` excludes a path that an earlier glob in the same list
  allowed. Within a list the last matching glob wins, like ``.gitignore``.

Decisions (:func:`decide`):

* Paths outside the repository are always allowed (scratchpad, ``~/.claude``).
* Human-controlled paths: ``ask`` for the orchestrator (the user confirms),
  ``deny`` for every agent.
* Otherwise ``allow`` when the editor's globs match, else ``deny`` with a
  reason that names the owner(s). Unknown agent types are denied.
"""

from __future__ import annotations

import fnmatch
import json
import os
import posixpath
from functools import lru_cache

ALLOW = "allow"
ASK = "ask"
DENY = "deny"

ORCHESTRATOR = "orchestrator"
WORKTREES_DIR = (".claude", "worktrees")


# --------------------------------------------------------------------------
# Loading
# --------------------------------------------------------------------------

def load_map(path: str) -> dict:
    """Read and validate the ownership map; raises ValueError when malformed."""
    with open(path, encoding="utf-8") as fh:
        data = json.load(fh)
    validate_map(data)
    return data


def validate_map(data: dict) -> None:
    if not isinstance(data, dict):
        raise ValueError("ownership map must be a JSON object")
    for key in ("orchestrator", "agents", "human_controlled"):
        if key not in data:
            raise ValueError(f"ownership map is missing '{key}'")
    identities = [(ORCHESTRATOR, data["orchestrator"])]
    if not isinstance(data["agents"], dict):
        raise ValueError("'agents' must be an object")
    identities += [(k, v) for k, v in data["agents"].items() if not k.startswith("_")]
    for name, ident in identities:
        if not isinstance(ident, dict):
            raise ValueError(f"'{name}' must be an object")
        for field in ("name", "email", "paths"):
            if field not in ident:
                raise ValueError(f"'{name}' is missing '{field}'")
        _check_globs(name, ident["paths"])
    _check_globs("human_controlled", data["human_controlled"])


def _check_globs(where: str, globs) -> None:
    if not isinstance(globs, list) or not all(isinstance(g, str) and g.strip("!") for g in globs):
        raise ValueError(f"'{where}' paths must be a list of non-empty glob strings")


def agents(data: dict) -> dict:
    """Agent entries without ``_``-prefixed keys."""
    return {k: v for k, v in data["agents"].items() if not k.startswith("_")}


# --------------------------------------------------------------------------
# Paths
# --------------------------------------------------------------------------

def to_repo_relative(path: str, project_dir: str, cwd: str | None = None) -> str | None:
    """Map ``path`` to a repo-relative POSIX path, or ``None`` if it's outside.

    Relative paths resolve against ``cwd`` (default: ``project_dir``).
    Paths inside ``<project>/.claude/worktrees/<name>/`` map to the same
    repo-relative path as in the main checkout.
    """
    if not path:
        return None
    base = cwd or project_dir
    absolute = path if os.path.isabs(path) else os.path.join(base, path)
    absolute = os.path.realpath(absolute)
    root = os.path.realpath(project_dir)
    try:
        rel = os.path.relpath(absolute, root)
    except ValueError:  # different drive (Windows)
        return None
    rel = rel.replace(os.sep, "/")
    if rel == "." or rel == ".." or rel.startswith("../"):
        return None
    parts = rel.split("/")
    if tuple(parts[:2]) == WORKTREES_DIR and len(parts) >= 4:
        parts = parts[3:]  # drop .claude/worktrees/<name>
    return posixpath.normpath("/".join(parts))


# --------------------------------------------------------------------------
# Globs
# --------------------------------------------------------------------------

@lru_cache(maxsize=4096)
def glob_match(pattern: str, path: str) -> bool:
    """True if repo-relative ``path`` matches ``pattern`` (no ``!`` prefix)."""
    return _match(tuple(pattern.strip("/").split("/")), tuple(path.split("/")))


def _match(pat: tuple, parts: tuple) -> bool:
    if not pat:
        return not parts
    head = pat[0]
    if head == "**":
        rest = pat[1:]
        return any(_match(rest, parts[i:]) for i in range(len(parts) + 1))
    if not parts:
        return False
    return fnmatch.fnmatchcase(parts[0], head) and _match(pat[1:], parts[1:])


def matches(globs: list, path: str) -> bool:
    """Apply a glob list in order; the last matching glob (``!`` or not) wins."""
    result = False
    for glob in globs:
        negated = glob.startswith("!")
        if glob_match(glob[1:] if negated else glob, path):
            result = not negated
    return result


# --------------------------------------------------------------------------
# Ownership questions
# --------------------------------------------------------------------------

def is_human_controlled(data: dict, path: str) -> bool:
    return matches(data["human_controlled"], path)


def owners(data: dict, path: str) -> list:
    """Names (agent types, plus ``orchestrator``) whose globs allow ``path``."""
    found = [name for name, ident in agents(data).items() if matches(ident["paths"], path)]
    if matches(data["orchestrator"]["paths"], path):
        found.append(ORCHESTRATOR)
    return found


def identity_for_email(data: dict, email: str):
    """``(name, entry)`` for a git author email (case-insensitive), or ``None``."""
    wanted = (email or "").strip().lower()
    if data["orchestrator"]["email"].lower() == wanted:
        return ORCHESTRATOR, data["orchestrator"]
    for name, ident in agents(data).items():
        if ident["email"].lower() == wanted:
            return name, ident
    return None


def describe_owners(data: dict, path: str) -> str:
    names = owners(data, path)
    if is_human_controlled(data, path):
        return "human-controlled (the user; the orchestrator only when the user asks)"
    if not names:
        return "nobody (unowned; ask the orchestrator)"
    return ", ".join(_label(data, n) for n in names)


def _label(data: dict, name: str) -> str:
    ident = data["orchestrator"] if name == ORCHESTRATOR else agents(data).get(name)
    return f"{name} ({ident['name']})" if ident else name


def decide(data: dict, agent_type: str | None, path: str | None) -> tuple:
    """Decision for ``agent_type`` editing repo-relative ``path``.

    ``agent_type`` is ``None``/empty for the main session (the orchestrator);
    ``path`` is ``None`` for paths outside the repository.
    Returns ``(ALLOW | ASK | DENY, reason)``; the reason is empty for ALLOW.
    """
    if path is None:
        return ALLOW, ""
    is_orchestrator = not agent_type
    who = ORCHESTRATOR if is_orchestrator else agent_type

    if is_human_controlled(data, path):
        if is_orchestrator:
            return ASK, (f"ownership: '{path}' is human-controlled. Edit it only because the user "
                         f"explicitly asked, and list it under 'Needs human review' in the PR.")
        return DENY, (f"ownership: '{path}' is human-controlled; no agent may edit it. "
                      f"Report what should change to the orchestrator instead.")

    if is_orchestrator:
        allowed = matches(data["orchestrator"]["paths"], path)
    else:
        ident = agents(data).get(agent_type)
        if ident is None:
            return DENY, (f"ownership: unknown agent type '{agent_type}' may not edit files in the "
                          f"repository ('{path}' is owned by {describe_owners(data, path)}).")
        allowed = matches(ident["paths"], path)

    if allowed:
        return ALLOW, ""
    hint = ("Delegate the change to the owning agent." if is_orchestrator
            else "Ask the owner under REQUESTS in your report.")
    return DENY, (f"ownership: {who} may not edit '{path}'; it is owned by "
                  f"{describe_owners(data, path)}. {hint}")
