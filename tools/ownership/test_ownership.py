"""Tests for the ownership map, the matcher, the PreToolUse hook and the merge-time check.

Standard library only. The matcher is loaded from ``.claude/hooks/ownership_lib.py`` of
this checkout; the hook runs as a subprocess against a temporary ``CLAUDE_PROJECT_DIR``
that holds copies of the map, hook and matcher.
"""

import importlib.util
import json
import os
import shutil
import subprocess
import sys
import tempfile
import unittest

REPO = os.path.realpath(os.path.join(os.path.dirname(__file__), "..", ".."))
LIB_PATH = os.path.join(REPO, ".claude", "hooks", "ownership_lib.py")
HOOK_PATH = os.path.join(REPO, ".claude", "hooks", "ownership.py")
MAP_PATH = os.path.join(REPO, ".claude", "ownership.json")
CHECK_PATH = os.path.join(REPO, "tools", "ownership", "check.py")


def read_json(path):
    with open(path, encoding="utf-8") as fh:
        return json.load(fh)


def write_text(path, text):
    with open(path, "w", encoding="utf-8") as fh:
        fh.write(text)


def load_module(name, path):
    spec = importlib.util.spec_from_file_location(name, path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


lib = load_module("ownership_lib_under_test", LIB_PATH)
check = load_module("ownership_check_under_test", CHECK_PATH)
DATA = lib.load_map(MAP_PATH)

CODER, TESTER, MODEL = "plugin-coder", "plugin-tester", "model-designer"
SOUND, VFX, BALANCE, RESEARCH = "sound-designer", "vfx-designer", "balance-designer", "resource-pack-researcher"


def verdict(agent, path):
    return lib.decide(DATA, agent, path)[0]


class GlobTests(unittest.TestCase):
    def test_double_star_matches_any_depth_including_none(self):
        self.assertTrue(lib.glob_match("src/**/vfx/**", "src/vfx/A.java"))
        self.assertTrue(lib.glob_match("src/**/vfx/**", "src/main/java/a/b/vfx/x/A.java"))
        self.assertTrue(lib.glob_match("docs/briefs/**", "docs/briefs/a.md"))
        self.assertFalse(lib.glob_match("docs/briefs/**", "docs/brief/a.md"))

    def test_single_star_stays_in_one_segment(self):
        self.assertTrue(lib.glob_match("design/*.md", "design/a.md"))
        self.assertFalse(lib.glob_match("design/*.md", "design/x/a.md"))
        self.assertTrue(lib.glob_match("resourcepack/assets/*/sounds/**", "resourcepack/assets/ns/sounds/a/b.ogg"))
        self.assertFalse(lib.glob_match("resourcepack/assets/*/sounds/**", "resourcepack/assets/a/b/sounds/x.ogg"))

    def test_exclusion_and_last_match_wins(self):
        globs = ["a/**", "!a/b/**", "a/b/keep.txt"]
        self.assertTrue(lib.matches(globs, "a/x.txt"))
        self.assertFalse(lib.matches(globs, "a/b/x.txt"))
        self.assertTrue(lib.matches(globs, "a/b/keep.txt"))
        self.assertFalse(lib.matches(globs, "c.txt"))

    def test_case_sensitive(self):
        self.assertFalse(lib.glob_match("CLAUDE.md", "claude.md"))

    def test_validate_rejects_malformed_maps(self):
        for bad in ([], {}, {"orchestrator": {}, "agents": {}, "human_controlled": []},
                    {"orchestrator": {"name": "a", "email": "b", "paths": ["x"]},
                     "agents": {"x": {"name": "a", "email": "b", "paths": "x"}}, "human_controlled": []}):
            with self.assertRaises(ValueError):
                lib.validate_map(bad)


class MapShapeTests(unittest.TestCase):
    def test_every_agent_file_has_an_entry(self):
        agent_dir = os.path.join(REPO, ".claude", "agents")
        names = {f[:-3] for f in os.listdir(agent_dir) if f.endswith(".md")}
        self.assertEqual(names, set(lib.agents(DATA)))

    def test_emails_are_unique_and_bakery_domain(self):
        emails = [DATA["orchestrator"]["email"]] + [a["email"] for a in lib.agents(DATA).values()]
        self.assertEqual(len(emails), len(set(emails)))
        self.assertTrue(all(e.endswith("@bakery.invalid") for e in emails))

    def test_no_file_in_repo_is_writable_by_two_agents_unintentionally(self):
        # Only the vfx tests may be shared (vfx-designer owns them, the tester may write tests).
        tracked = subprocess.run(["git", "ls-files"], cwd=REPO, capture_output=True, text=True).stdout.split()
        for path in tracked:
            if lib.is_human_controlled(DATA, path):
                continue
            owners = [o for o in lib.owners(DATA, path) if o != lib.ORCHESTRATOR]
            if len(owners) > 1:
                self.assertEqual(sorted(owners), [TESTER, VFX], path)


class AgentDecisionTests(unittest.TestCase):
    def assertAllowed(self, agent, *paths):
        for p in paths:
            self.assertEqual(verdict(agent, p), lib.ALLOW, f"{agent} should edit {p}")

    def assertDenied(self, agent, *paths):
        for p in paths:
            self.assertEqual(verdict(agent, p), lib.DENY, f"{agent} must not edit {p}")

    def test_plugin_coder(self):
        self.assertAllowed(CODER, "src/main/java/dev/x/Main.java", "src/main/resources/plugin.yml",
                           "src/main/resources/messages.yml", "pom.xml", "mise.toml", ".gitignore",
                           ".gitattributes", ".mailmap", "tools/pack.py", "tools/ownership/check.py",
                           "design/requirements.txt", "docs/paper-26x-reference.md", "docs/toolchain.md",
                           "docs/minimessage.md", "docs/integrations.md")
        self.assertDenied(CODER, "src/main/java/dev/x/vfx/Effects.java", "src/main/java/vfx/A.java",
                          "src/main/resources/sounds.yml", "src/main/resources/effects.yml",
                          "src/main/resources/buildings.yml", "src/main/resources/upgrades.yml",
                          "tools/balance/model.py", "tools/ownership/test_ownership.py",
                          "src/test/java/dev/x/ATest.java", "resourcepack/pack.mcmeta", "design/sounds.md",
                          "docs/workflow.md", "CLAUDE.md", "docs/briefs/x.md", ".github/workflows/ci.yml")

    def test_plugin_tester(self):
        self.assertAllowed(TESTER, "src/test/java/dev/x/ATest.java", "src/test/java/dev/x/vfx/ShapeTest.java",
                           "tools/ownership/test_ownership.py", "tools/balance/test_model.py",
                           "docs/reviews/2026-10-18-x.md", "docs/architecture.md")
        self.assertDenied(TESTER, "src/main/java/dev/x/Main.java", "tools/ownership/check.py",
                          "tools/pack.py", "pom.xml", "docs/briefs/x.md")

    def test_model_designer_and_design_docs_split(self):
        self.assertAllowed(MODEL, "resourcepack/pack.mcmeta", "resourcepack/assets/cookie/models/item/a.json",
                           "resourcepack/assets/cookie/textures/item/a.png", "resourcepack/assets/cookie/font/default.json",
                           "itemsadder/contents/x.yml", "modelengine/blueprints/a.bbmodel",
                           "design/scripts/test_cookie.py", "design/test_cookie.md", "design/style-guide.md",
                           "design/lessons.md")
        self.assertDenied(MODEL, "resourcepack/assets/cookie/sounds/a.ogg", "resourcepack/assets/cookie/sounds.json",
                          "resourcepack/assets/cookie/lang/en_us.json",
                          "resourcepack/assets/minecraft/particles/p.json",
                          "resourcepack/assets/minecraft/textures/particle/p.png",
                          "design/sound-style-guide.md", "design/sound-lessons.md", "design/sound-scripts/a.py",
                          "design/vfx-style-guide.md", "design/vfx-lessons.md", "design/vfx-scripts/a.py",
                          "design/sounds.md", "design/effects.md", "design/CREDITS.md",
                          "design/requirements.txt", "src/main/java/dev/x/Main.java")

    def test_sound_designer(self):
        self.assertAllowed(SOUND, "resourcepack/assets/cookie/sounds/click/a.ogg", "resourcepack/assets/cookie/sounds.json",
                           "resourcepack/assets/cookie/lang/en_us.json", "src/main/resources/sounds.yml",
                           "design/sound-scripts/a.py", "design/sound-tools/render.py", "design/sound-sources/x.wav",
                           "design/sound-style-guide.md", "design/sound-lessons.md", "design/sounds.md",
                           "design/CREDITS.md")
        self.assertDenied(SOUND, "resourcepack/pack.mcmeta", "resourcepack/assets/cookie/textures/a.png",
                          "src/main/resources/effects.yml", "design/effects.md", "design/test_cookie.md",
                          "src/main/java/dev/x/Main.java")

    def test_vfx_designer(self):
        self.assertAllowed(VFX, "src/main/java/dev/x/vfx/Effects.java", "src/main/java/dev/x/vfx/shape/Ring.java",
                           "src/test/java/dev/x/vfx/ShapeTest.java", "src/main/resources/effects.yml",
                           "resourcepack/assets/minecraft/particles/p.json",
                           "resourcepack/assets/minecraft/textures/particle/p.png", "design/vfx-scripts/a.py",
                           "design/vfx-style-guide.md", "design/effects.md")
        self.assertDenied(VFX, "src/main/java/dev/x/Main.java", "src/main/resources/sounds.yml",
                          "resourcepack/pack.mcmeta", "design/sounds.md", "design/test_cookie.md")

    def test_balance_designer(self):
        self.assertAllowed(BALANCE, "src/main/resources/buildings.yml", "src/main/resources/upgrades.yml",
                           "tools/balance/simulate.py", "docs/balance/model.md", "docs/balance/golden-values.json")
        self.assertDenied(BALANCE, "tools/balance/test_model.py", "src/main/java/dev/x/Main.java",
                          "src/test/java/dev/x/ATest.java", "src/main/resources/messages.yml", "docs/briefs/x.md")

    def test_researcher(self):
        self.assertAllowed(RESEARCH, "docs/research/topic.md")
        self.assertDenied(RESEARCH, "docs/briefs/x.md", "src/main/java/A.java", "docs/reviews/x.md")

    def test_human_controlled_denied_for_every_agent(self):
        paths = [".claude/agents/plugin-coder.md", ".claude/skills/feature/SKILL.md", "docs/workflow.md",
                 ".claude/ownership.json", ".claude/ownership.local", ".claude/hooks/ownership.py",
                 ".claude/hooks/ownership_lib.py", ".claude/settings.json"]
        for agent in lib.agents(DATA):
            self.assertDenied(agent, *paths)

    def test_unknown_agent_denied_inside_repo_but_not_outside(self):
        for agent in ("general-purpose", "statusline-setup", "made-up"):
            self.assertDenied(agent, "src/main/java/A.java", "README.md", "docs/research/x.md")
            self.assertEqual(lib.decide(DATA, agent, None)[0], lib.ALLOW)

    def test_deny_reason_names_the_owner(self):
        decision, reason = lib.decide(DATA, CODER, "src/main/resources/sounds.yml")
        self.assertEqual(decision, lib.DENY)
        self.assertIn("sound-designer", reason)
        _, reason = lib.decide(DATA, SOUND, "src/main/java/dev/x/vfx/Effects.java")
        self.assertIn("vfx-designer", reason)
        _, reason = lib.decide(DATA, CODER, ".claude/settings.json")
        self.assertIn("human-controlled", reason)
        _, reason = lib.decide(DATA, CODER, "some/unowned/file.txt")
        self.assertIn("unowned", reason)

    def test_outside_repo_allowed_for_everyone(self):
        for agent in list(lib.agents(DATA)) + [None, ""]:
            self.assertEqual(lib.decide(DATA, agent, None), (lib.ALLOW, ""))


class OrchestratorTests(unittest.TestCase):
    def test_allow_ask_deny(self):
        for p in ("CLAUDE.md", "docs/briefs/x.md", ".github/workflows/ci.yml", ".github/pull_request_template.md"):
            self.assertEqual(verdict(None, p), lib.ALLOW, p)
        for p in (".claude/agents/plugin-coder.md", ".claude/skills/feature/SKILL.md", "docs/workflow.md",
                  ".claude/ownership.json", ".claude/hooks/ownership.py", ".claude/settings.json"):
            self.assertEqual(verdict(None, p), lib.ASK, p)
        for p in ("src/main/java/A.java", "src/test/java/ATest.java", "pom.xml", "mise.toml",
                  "resourcepack/pack.mcmeta", "docs/reviews/x.md", "tools/pack.py", "docs/research/x.md"):
            self.assertEqual(verdict(None, p), lib.DENY, p)

    def test_empty_agent_type_is_orchestrator(self):
        self.assertEqual(verdict("", "CLAUDE.md"), lib.ALLOW)


class PathTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.mkdtemp()
        self.proj = os.path.realpath(self.tmp)
        self.wt = os.path.join(self.proj, ".claude", "worktrees", "feat-a")
        os.makedirs(self.wt)

    def tearDown(self):
        shutil.rmtree(self.tmp, ignore_errors=True)

    def rel(self, path, cwd=None):
        return lib.to_repo_relative(path, self.proj, cwd)

    def test_main_checkout_and_worktree_agree(self):
        self.assertEqual(self.rel(f"{self.proj}/src/main/A.java"), "src/main/A.java")
        self.assertEqual(self.rel(f"{self.wt}/src/main/A.java"), "src/main/A.java")
        self.assertEqual(self.rel(f"{self.wt}/.claude/ownership.json"), ".claude/ownership.json")

    def test_relative_paths_resolve_against_cwd(self):
        self.assertEqual(self.rel("src/main/A.java", cwd=self.wt), "src/main/A.java")
        self.assertEqual(self.rel("src/main/A.java", cwd=self.proj), "src/main/A.java")
        self.assertEqual(self.rel("src/main/A.java"), "src/main/A.java")
        self.assertEqual(self.rel("../../pom.xml", cwd=f"{self.wt}/src/main"), "pom.xml")

    def test_dotdot_cannot_escape_or_disguise(self):
        self.assertEqual(self.rel(f"{self.proj}/docs/briefs/../../src/main/A.java"), "src/main/A.java")
        self.assertEqual(self.rel(f"{self.wt}/docs/briefs/../../.claude/settings.json"), ".claude/settings.json")
        self.assertIsNone(self.rel(f"{self.proj}/../outside.txt"))
        self.assertIsNone(self.rel(f"{self.wt}/../../../../outside.txt"))

    def test_outside_and_degenerate(self):
        self.assertIsNone(self.rel("/etc/passwd"))
        self.assertIsNone(self.rel(os.path.expanduser("~/.claude/x.md")))
        self.assertIsNone(self.rel(""))
        self.assertIsNone(self.rel(self.proj))

    def test_sibling_directory_with_same_prefix_is_outside(self):
        self.assertIsNone(self.rel(self.proj + "-other/src/A.java"))

    def test_symlink_into_repo_resolves(self):
        os.makedirs(os.path.join(self.proj, "src"))
        link = os.path.join(self.tmp + "-link")
        os.symlink(self.proj, link)
        try:
            self.assertEqual(self.rel(f"{link}/src/A.java"), "src/A.java")
        finally:
            os.unlink(link)


class HookTests(unittest.TestCase):
    """Runs the real hook script as a subprocess against a temp project dir."""

    @classmethod
    def setUpClass(cls):
        cls.tmp = tempfile.mkdtemp()
        cls.proj = os.path.realpath(cls.tmp)
        cls.install(cls.proj, MAP_PATH)
        cls.wt = os.path.join(cls.proj, ".claude", "worktrees", "feat-a")
        os.makedirs(cls.wt)

    @classmethod
    def tearDownClass(cls):
        shutil.rmtree(cls.tmp, ignore_errors=True)

    @staticmethod
    def install(root, map_source):
        os.makedirs(os.path.join(root, ".claude", "hooks"), exist_ok=True)
        shutil.copy(map_source, os.path.join(root, ".claude", "ownership.json"))
        shutil.copy(HOOK_PATH, os.path.join(root, ".claude", "hooks", "ownership.py"))
        shutil.copy(LIB_PATH, os.path.join(root, ".claude", "hooks", "ownership_lib.py"))

    def run_hook(self, event, project_dir="__default__", raw=None):
        env = {k: v for k, v in os.environ.items() if k != "CLAUDE_PROJECT_DIR"}
        pd = self.proj if project_dir == "__default__" else project_dir
        if pd is not None:
            env["CLAUDE_PROJECT_DIR"] = pd
        stdin = raw if raw is not None else json.dumps(event)
        script = os.path.join(self.proj, ".claude", "hooks", "ownership.py")
        proc = subprocess.run([sys.executable, script], input=stdin, capture_output=True, text=True, env=env, timeout=30)
        return proc

    def edit(self, agent, path, tool="Edit", cwd=None, key="file_path"):
        event = {"tool_name": tool, "tool_input": {key: path}, "cwd": cwd or self.proj}
        if agent:
            event["agent_type"] = agent
        return self.run_hook(event)

    def decision(self, proc):
        self.assertEqual(proc.returncode, 0, proc.stderr)
        if not proc.stdout.strip():
            return None, ""
        out = json.loads(proc.stdout)["hookSpecificOutput"]
        self.assertEqual(out["hookEventName"], "PreToolUse")
        return out["permissionDecision"], out["permissionDecisionReason"]

    def test_allowed_edit_prints_nothing(self):
        proc = self.edit(CODER, f"{self.proj}/src/main/java/A.java")
        self.assertEqual((proc.returncode, proc.stdout.strip()), (0, ""))
        proc = self.edit(CODER, f"{self.wt}/src/main/java/A.java", tool="Write", cwd=self.wt)
        self.assertEqual((proc.returncode, proc.stdout.strip()), (0, ""))

    def test_denied_edit_names_owner(self):
        d, reason = self.decision(self.edit(CODER, f"{self.wt}/src/main/resources/sounds.yml", cwd=self.wt))
        self.assertEqual(d, "deny")
        self.assertIn("sound-designer", reason)

    def test_main_checkout_worktree_and_relative_paths_agree(self):
        paths = [(f"{self.proj}/src/main/resources/effects.yml", self.proj),
                 (f"{self.wt}/src/main/resources/effects.yml", self.wt),
                 ("src/main/resources/effects.yml", self.wt),
                 ("src/main/resources/effects.yml", self.proj)]
        for path, cwd in paths:
            d, reason = self.decision(self.edit(CODER, path, cwd=cwd))
            self.assertEqual(d, "deny", path)
            self.assertIn("vfx-designer", reason)
            self.assertEqual(self.decision(self.edit(VFX, path, cwd=cwd))[0], None, path)

    def test_dotdot_cannot_smuggle_an_edit(self):
        d, _ = self.decision(self.edit(CODER, f"{self.wt}/src/main/../../.claude/settings.json", cwd=self.wt))
        self.assertEqual(d, "deny")
        d, _ = self.decision(self.edit(CODER, "../../.claude/ownership.json", cwd=f"{self.wt}/src/main"))
        self.assertEqual(d, "deny")

    def test_outside_repo_allowed(self):
        for agent in (CODER, "general-purpose", SOUND):
            self.assertIsNone(self.decision(self.edit(agent, "/tmp/claude-scratch/x.txt"))[0])
            self.assertIsNone(self.decision(self.edit(agent, os.path.expanduser("~/.claude/projects/x.md")))[0])

    def test_notebook_path_and_multiedit(self):
        d, _ = self.decision(self.edit(CODER, f"{self.proj}/src/test/x.ipynb", tool="NotebookEdit", key="notebook_path"))
        self.assertEqual(d, "deny")
        self.assertIsNone(self.decision(self.edit(TESTER, f"{self.proj}/src/test/x.ipynb", tool="NotebookEdit", key="notebook_path"))[0])

    def test_unknown_agent_denied(self):
        d, reason = self.decision(self.edit("general-purpose", f"{self.proj}/README.md"))
        self.assertEqual(d, "deny")
        self.assertIn("general-purpose", reason)

    def test_read_only_agents_get_no_decision(self):
        for agent in ("Explore", "Plan"):
            for tool, tin in (("Read", {"file_path": f"{self.proj}/src/main/A.java"}),
                              ("Grep", {"pattern": "x", "path": self.proj}),
                              ("Glob", {"pattern": "**/*.java"}),
                              ("Bash", {"command": "ls"})):
                proc = self.run_hook({"tool_name": tool, "tool_input": tin, "agent_type": agent, "cwd": self.proj})
                self.assertEqual((proc.returncode, proc.stdout.strip()), (0, ""), f"{agent}/{tool}")

    def test_edit_without_path_gives_no_decision(self):
        proc = self.run_hook({"tool_name": "Edit", "tool_input": {}, "agent_type": CODER})
        self.assertEqual((proc.returncode, proc.stdout.strip()), (0, ""))

    def test_orchestrator_allow_ask_deny(self):
        self.assertIsNone(self.decision(self.edit(None, f"{self.proj}/CLAUDE.md"))[0])
        self.assertIsNone(self.decision(self.edit(None, f"{self.proj}/docs/briefs/x.md"))[0])
        self.assertIsNone(self.decision(self.edit(None, f"{self.proj}/.github/workflows/ci.yml"))[0])
        self.assertEqual(self.decision(self.edit(None, f"{self.proj}/docs/workflow.md"))[0], "ask")
        self.assertEqual(self.decision(self.edit(None, f"{self.proj}/.claude/skills/feature/SKILL.md"))[0], "ask")
        self.assertEqual(self.decision(self.edit(None, f"{self.proj}/.claude/hooks/ownership.py"))[0], "ask")
        self.assertEqual(self.decision(self.edit(None, f"{self.proj}/src/main/java/A.java"))[0], "deny")

    def test_map_comes_from_project_dir_not_worktree(self):
        loose = read_json(MAP_PATH)
        loose["agents"][CODER]["paths"] = ["**"]
        loose["human_controlled"] = []
        loose_map = os.path.join(self.tmp, "loose.json")
        write_text(loose_map, json.dumps(loose))
        # a looser map, matcher and hook inside the fake worktree must all be ignored
        self.install(self.wt, loose_map)
        with open(os.path.join(self.wt, ".claude", "hooks", "ownership_lib.py"), "a") as fh:
            fh.write("\n\ndef decide(*a, **k):\n    return ALLOW, ''\n")
        for path in (f"{self.wt}/.claude/ownership.json", f"{self.wt}/docs/workflow.md",
                     f"{self.wt}/src/main/resources/sounds.yml"):
            d, _ = self.decision(self.edit(CODER, path, cwd=self.wt))
            self.assertEqual(d, "deny", path)

    def test_failure_no_project_dir(self):
        proc = self.run_hook({"tool_name": "Edit", "tool_input": {"file_path": "/x"}, "agent_type": CODER}, project_dir=None)
        self.assertEqual(proc.returncode, 2)
        self.assertIn("CLAUDE_PROJECT_DIR", proc.stderr)

    def test_failure_bad_project_dir(self):
        proc = self.run_hook({"tool_name": "Edit", "tool_input": {"file_path": "/x"}}, project_dir="/nonexistent/dir")
        self.assertEqual(proc.returncode, 2)

    def test_failure_bad_json_input(self):
        for raw in ("not json", "", "[1, 2]", "null"):
            proc = self.run_hook(None, raw=raw)
            self.assertEqual(proc.returncode, 2, repr(raw))
            self.assertTrue(proc.stderr.strip())

    def test_failure_missing_or_broken_map_and_lib(self):
        for breaker in ("map-missing", "map-broken", "map-incomplete", "lib-missing"):
            root = tempfile.mkdtemp()
            try:
                root = os.path.realpath(root)
                self.install(root, MAP_PATH)
                if breaker == "map-missing":
                    os.unlink(os.path.join(root, ".claude", "ownership.json"))
                elif breaker == "map-broken":
                    write_text(os.path.join(root, ".claude", "ownership.json"), "{nope")
                elif breaker == "map-incomplete":
                    write_text(os.path.join(root, ".claude", "ownership.json"), '{"agents": {}}')
                else:
                    os.unlink(os.path.join(root, ".claude", "hooks", "ownership_lib.py"))
                env = dict(os.environ, CLAUDE_PROJECT_DIR=root)
                event = {"tool_name": "Edit", "tool_input": {"file_path": f"{root}/src/A.java"}, "agent_type": CODER}
                proc = subprocess.run([sys.executable, HOOK_PATH], input=json.dumps(event),
                                      capture_output=True, text=True, env=env, timeout=30)
                self.assertEqual(proc.returncode, 2, breaker + proc.stdout)
            finally:
                shutil.rmtree(root, ignore_errors=True)

    def test_fast_enough(self):
        import time
        start = time.time()
        for _ in range(5):
            self.edit(CODER, f"{self.proj}/src/main/java/A.java")
        per_call = (time.time() - start) / 5
        self.assertLess(per_call, 0.5, f"{per_call:.3f}s per call (budget is 200 ms; allowing slack for CI)")


class SettingsTests(unittest.TestCase):
    def test_hook_entry(self):
        settings = read_json(os.path.join(REPO, ".claude", "settings.json"))
        self.assertEqual(settings["worktree"]["baseRef"], "head")
        entries = settings["hooks"]["PreToolUse"]
        self.assertEqual(len(entries), 1)
        self.assertEqual(entries[0]["matcher"], "Edit|Write|NotebookEdit")
        handler = entries[0]["hooks"][0]
        self.assertEqual(handler["command"], "${CLAUDE_PROJECT_DIR}/.claude/hooks/ownership.py")
        self.assertEqual(handler["onFailure"], "block")
        self.assertTrue(os.access(HOOK_PATH, os.X_OK), "hook must be executable")
        with open(HOOK_PATH) as fh:
            self.assertEqual(fh.readline().strip(), "#!/usr/bin/env python3")


# --------------------------------------------------------------------------
# Merge-time check
# --------------------------------------------------------------------------

def commit(email, files, parents=("p",), sha="a" * 40, name="X", subject="s"):
    return {"sha": sha, "parents": list(parents), "name": name, "email": email, "subject": subject, "files": files}


class CheckLogicTests(unittest.TestCase):
    def run_check(self, commits, approved=False):
        return check.check(lib, DATA, commits, approved)

    def test_tester_commit_on_coder_branch_passes(self):
        res = self.run_check([commit("wesly@bakery.invalid", ["src/main/java/A.java"]),
                              commit("gandalf@bakery.invalid", ["tools/ownership/test_ownership.py", "docs/reviews/x.md"])])
        self.assertEqual(res["violations"], [])
        self.assertEqual(res["checked"], 2)

    def test_same_file_by_wrong_author_fails(self):
        res = self.run_check([commit("wesly@bakery.invalid", ["tools/ownership/test_ownership.py"])])
        self.assertEqual(len(res["violations"]), 1)
        c, path, who, owner = res["violations"][0]
        self.assertEqual((path, who), ("tools/ownership/test_ownership.py", CODER))
        self.assertIn("plugin-tester", owner)

    def test_violation_among_good_files_is_listed_once(self):
        res = self.run_check([commit("wesly@bakery.invalid", ["src/main/java/A.java", "src/main/resources/sounds.yml"])])
        self.assertEqual([v[1] for v in res["violations"]], ["src/main/resources/sounds.yml"])
        self.assertIn("sound-designer", res["violations"][0][3])

    def test_unknown_author_fails_for_every_file(self):
        res = self.run_check([commit("stranger@example.com", ["README.md", "src/main/java/A.java"])])
        self.assertEqual(len(res["violations"]), 2)
        self.assertTrue(all(v[2] == "unknown author" for v in res["violations"]))

    def test_email_is_case_insensitive(self):
        res = self.run_check([commit("Wesly@Bakery.Invalid", ["pom.xml"])])
        self.assertEqual(res["violations"], [])

    def test_human_controlled(self):
        files = [".claude/hooks/ownership.py"]
        res = self.run_check([commit("wesly@bakery.invalid", files)])
        self.assertEqual(len(res["violations"]), 1)
        self.assertEqual(res["human_review"], [])
        res = self.run_check([commit("wesly@bakery.invalid", files)], approved=True)
        self.assertEqual(res["violations"], [])
        self.assertEqual([p for _, p in res["human_review"]], files)
        res = self.run_check([commit("rick@bakery.invalid", ["docs/workflow.md"])])
        self.assertEqual(res["violations"], [])
        self.assertEqual(len(res["human_review"]), 1)

    def test_human_approved_does_not_excuse_ordinary_violations(self):
        res = self.run_check([commit("wesly@bakery.invalid", ["src/main/resources/sounds.yml"])], approved=True)
        self.assertEqual(len(res["violations"]), 1)
        res = self.run_check([commit("stranger@example.com", [".claude/settings.json"])], approved=True)
        self.assertEqual(len(res["violations"]), 1)

    def test_orchestrator_violation(self):
        res = self.run_check([commit("rick@bakery.invalid", ["src/main/java/A.java"])])
        self.assertEqual(len(res["violations"]), 1)

    def test_merge_commits_skipped_and_files_listed(self):
        merge = commit("rick@bakery.invalid", ["src/main/java/Conflict.java"], parents=("p1", "p2"))
        res = self.run_check([merge])
        self.assertEqual(res["violations"], [])
        self.assertEqual(res["checked"], 0)
        self.assertEqual(res["merges"], [(merge, ["src/main/java/Conflict.java"])])

    def test_report_output_and_exit_codes(self):
        import contextlib
        import io
        res = self.run_check([commit("wesly@bakery.invalid", ["src/main/resources/sounds.yml"], sha="b" * 40, subject="oops"),
                              commit("rick@bakery.invalid", ["x.java"], parents=("p1", "p2"), sha="c" * 40)])
        buf = io.StringIO()
        with contextlib.redirect_stdout(buf):
            code = check.report(res, "feature/x", "x/plugin-coder", "/root")
        out = buf.getvalue()
        self.assertEqual(code, 1)
        for needle in ("bbbbbbbbb", "wesly@bakery.invalid", "src/main/resources/sounds.yml",
                       "sound-designer", "x.java", "FAIL"):
            self.assertIn(needle, out)
        clean = self.run_check([commit("wesly@bakery.invalid", ["pom.xml"])])
        with contextlib.redirect_stdout(io.StringIO()):
            self.assertEqual(check.report(clean, "a", "b", "/root"), 0)


def git(cwd, *args, env=None):
    full = dict(os.environ, GIT_CONFIG_GLOBAL=os.devnull, GIT_CONFIG_SYSTEM=os.devnull)
    full.update(env or {})
    return subprocess.run(["git", *args], cwd=cwd, capture_output=True, text=True, check=True, env=full).stdout


class CheckGitIntegrationTests(unittest.TestCase):
    """Runs check.py against a throwaway repo with real commits and a real merge conflict."""

    @classmethod
    def setUpClass(cls):
        cls.tmp = os.path.realpath(tempfile.mkdtemp())
        r = cls.repo = cls.tmp
        git(r, "init", "-q", "-b", "feature/f")
        cls.install_rules(r)
        cls.commit_as(r, "rick@bakery.invalid", {"CLAUDE.md": "base\n", "src/main/java/A.java": "a\n",
                                                   "src/main/java/B.java": "b\n"}, "base")
        git(r, "switch", "-q", "-c", "f/plugin-coder")
        cls.commit_as(r, "wesly@bakery.invalid", {"src/main/java/A.java": "coder\n"}, "coder work")
        cls.commit_as(r, "gandalf@bakery.invalid", {"tools/ownership/test_x.py": "t\n", "docs/reviews/r.md": "r\n"}, "tests")
        git(r, "switch", "-q", "feature/f")
        # feature branch also edits A.java: merging the coder branch conflicts, Rick resolves it
        cls.commit_as(r, "rick@bakery.invalid", {"src/main/java/A.java": "feature\n"}, "feature edit", folder_ok=True)
        git(r, "switch", "-q", "-c", "f/bad", "feature/f~1")
        cls.commit_as(r, "wesly@bakery.invalid", {"src/main/resources/sounds.yml": "x: 1\n"}, "coder touches sounds")
        git(r, "switch", "-q", "-c", "f/stranger", "feature/f~1")
        cls.commit_as(r, "stranger@example.com", {"README.md": "hi\n"}, "unknown")
        git(r, "switch", "-q", "-c", "f/human", "feature/f~1")
        cls.commit_as(r, "wesly@bakery.invalid", {".claude/settings.json": "{}\n"}, "guardrail")
        git(r, "switch", "-q", "feature/f")

    @classmethod
    def tearDownClass(cls):
        shutil.rmtree(cls.tmp, ignore_errors=True)

    @staticmethod
    def install_rules(root):
        os.makedirs(os.path.join(root, ".claude", "hooks"), exist_ok=True)
        shutil.copy(MAP_PATH, os.path.join(root, ".claude", "ownership.json"))
        shutil.copy(LIB_PATH, os.path.join(root, ".claude", "hooks", "ownership_lib.py"))

    @staticmethod
    def commit_as(root, email, files, subject, folder_ok=False):
        for rel, text in files.items():
            full = os.path.join(root, rel)
            os.makedirs(os.path.dirname(full), exist_ok=True)
            with open(full, "w") as fh:
                fh.write(text)
            git(root, "add", rel)
        # the bootstrap commit also records the rules, which are not part of the checked branches
        if subject == "base":
            git(root, "add", ".claude")
        git(root, "-c", f"user.email={email}", "-c", "user.name=T", "commit", "-q", "-m", subject)

    def run_check(self, *args, project_dir=None):
        env = dict(os.environ, GIT_CONFIG_GLOBAL=os.devnull, GIT_CONFIG_SYSTEM=os.devnull)
        env.pop("CLAUDE_PROJECT_DIR", None)
        if project_dir:
            env["CLAUDE_PROJECT_DIR"] = project_dir
        return subprocess.run([sys.executable, CHECK_PATH, *args], cwd=self.repo,
                              capture_output=True, text=True, env=env, timeout=60)

    def test_clean_branch_with_tester_commit_passes(self):
        # coder branch was cut before the feature edit, so it is "behind" but must still pass
        proc = self.run_check("f/plugin-coder", "--base", "feature/f")
        self.assertEqual(proc.returncode, 0, proc.stdout + proc.stderr)
        self.assertIn("2 commit(s) checked", proc.stdout)

    def test_violation_fails_with_details(self):
        proc = self.run_check("f/bad", "--base", "feature/f")
        self.assertEqual(proc.returncode, 1, proc.stdout)
        for needle in ("sounds.yml", "wesly@bakery.invalid", "sound-designer", "coder touches sounds"):
            self.assertIn(needle, proc.stdout)

    def test_unknown_author_fails(self):
        proc = self.run_check("f/stranger", "--base", "feature/f")
        self.assertEqual(proc.returncode, 1)
        self.assertIn("unknown author", proc.stdout)

    def test_human_controlled_needs_flag(self):
        proc = self.run_check("f/human", "--base", "feature/f")
        self.assertEqual(proc.returncode, 1)
        proc = self.run_check("f/human", "--base", "feature/f", "--human-approved")
        self.assertEqual(proc.returncode, 0, proc.stdout)
        self.assertIn("Needs human review", proc.stdout)
        self.assertIn(".claude/settings.json", proc.stdout)

    def test_base_defaults_to_current_branch(self):
        proc = self.run_check("f/bad")
        self.assertEqual(proc.returncode, 1)
        self.assertIn("feature/f..f/bad", proc.stdout)

    def test_usage_errors_exit_2(self):
        self.assertEqual(self.run_check("no/such-branch", "--base", "feature/f").returncode, 2)
        self.assertEqual(self.run_check("f/bad", "--base", "no/such-base").returncode, 2)

    def test_rules_come_from_project_dir_first(self):
        other = tempfile.mkdtemp()
        try:
            self.install_rules(other)
            data = read_json(os.path.join(other, ".claude", "ownership.json"))
            data["agents"][CODER]["paths"].append("src/main/resources/sounds.yml")
            write_text(os.path.join(other, ".claude", "ownership.json"), json.dumps(data))
            proc = self.run_check("f/bad", "--base", "feature/f", project_dir=other)
            self.assertEqual(proc.returncode, 0, proc.stdout)
            self.assertIn(other, proc.stdout)
        finally:
            shutil.rmtree(other, ignore_errors=True)

    def test_merge_commit_skipped_and_conflict_file_listed(self):
        r = self.repo
        git(r, "switch", "-q", "-c", "f/merged", "feature/f")
        try:
            merge = subprocess.run(["git", "merge", "--no-ff", "-q", "f/plugin-coder", "-m", "merge coder"],
                                   cwd=r, capture_output=True, text=True,
                                   env=dict(os.environ, GIT_CONFIG_GLOBAL=os.devnull, GIT_AUTHOR_NAME="Rick",
                                            GIT_AUTHOR_EMAIL="rick@bakery.invalid", GIT_COMMITTER_NAME="Rick",
                                            GIT_COMMITTER_EMAIL="rick@bakery.invalid"))
            self.assertNotEqual(merge.returncode, 0, "expected a conflict on A.java")
            with open(os.path.join(r, "src/main/java/A.java"), "w") as fh:
                fh.write("resolved by rick\n")
            git(r, "add", "src/main/java/A.java")
            git(r, "-c", "user.email=rick@bakery.invalid", "-c", "user.name=Rick", "commit", "-q", "--no-edit")
            # base = the commit before the merge's first parent history is irrelevant: check the merge itself
            first_parent = git(r, "rev-parse", "HEAD^1").strip()
            proc = self.run_check("f/merged", "--base", first_parent)
            self.assertEqual(proc.returncode, 0, proc.stdout + proc.stderr)
            self.assertIn("1 merge commit(s) skipped", proc.stdout)
            self.assertIn("src/main/java/A.java", proc.stdout)
            self.assertNotIn("FAIL", proc.stdout)
        finally:
            git(r, "switch", "-q", "feature/f")
            git(r, "branch", "-q", "-D", "f/merged")


if __name__ == "__main__":
    unittest.main()
