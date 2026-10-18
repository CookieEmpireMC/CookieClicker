#!/usr/bin/env python3
"""PreToolUse hook: stop agents from editing files they don't own.

Wired in ``.claude/settings.json`` for ``Edit|Write|NotebookEdit``. Reads the
hook input JSON on stdin, takes ``agent_type`` (absent in the main session,
which is the orchestrator) and the target path (``tool_input.file_path`` or
``tool_input.notebook_path``), and asks ``ownership_lib.decide``.

* allowed (or not an edit tool, or outside the repository): no output, exit 0
* ask / deny: a ``hookSpecificOutput`` decision JSON on stdout, exit 0
* broken setup (no ``$CLAUDE_PROJECT_DIR``, unreadable map, bad input):
  message on stderr, exit 2, which blocks the call

The map and matcher are loaded only from ``$CLAUDE_PROJECT_DIR`` (the main
checkout), never from the agent's worktree, so an agent can't loosen its own
rules. Files written through Bash are not covered here; only the merge-time
check (``mise run ownership-check``) catches those. Standard library only.
"""

import importlib.util
import json
import os
import sys

EDIT_TOOLS = {"Edit", "Write", "NotebookEdit", "MultiEdit"}


def fail(message: str) -> None:
    print(f"ownership hook: {message}", file=sys.stderr)
    sys.exit(2)


def load_lib(project_dir: str):
    lib_path = os.path.join(project_dir, ".claude", "hooks", "ownership_lib.py")
    spec = importlib.util.spec_from_file_location("ownership_lib", lib_path)
    if spec is None or not os.path.isfile(lib_path):
        fail(f"matcher not found at {lib_path}")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def main() -> None:
    try:
        event = json.load(sys.stdin)
    except (ValueError, OSError) as exc:
        fail(f"could not parse hook input: {exc}")
    if not isinstance(event, dict):
        fail("hook input is not a JSON object")

    if event.get("tool_name") not in EDIT_TOOLS:
        return  # read tools etc.: no decision
    tool_input = event.get("tool_input") or {}
    target = tool_input.get("file_path") or tool_input.get("notebook_path")
    if not target:
        return  # nothing to check; the tool itself rejects the call

    project_dir = os.environ.get("CLAUDE_PROJECT_DIR")
    if not project_dir or not os.path.isdir(project_dir):
        fail("CLAUDE_PROJECT_DIR is not set to the project directory")

    lib = load_lib(project_dir)
    try:
        data = lib.load_map(os.path.join(project_dir, ".claude", "ownership.json"))
    except (OSError, ValueError) as exc:
        fail(f"could not load .claude/ownership.json: {exc}")

    rel = lib.to_repo_relative(target, project_dir, event.get("cwd"))
    decision, reason = lib.decide(data, event.get("agent_type"), rel)
    if decision == lib.ALLOW:
        return
    json.dump({
        "hookSpecificOutput": {
            "hookEventName": "PreToolUse",
            "permissionDecision": decision,
            "permissionDecisionReason": reason,
        }
    }, sys.stdout)
    sys.stdout.write("\n")


if __name__ == "__main__":
    main()
