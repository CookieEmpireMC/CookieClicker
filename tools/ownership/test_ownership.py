"""Tests for the ownership map, the matcher and the merge-time check.

Standard library only. The matcher is loaded from ``tools/ownership/ownership_lib.py``.
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
LIB_PATH = os.path.join(REPO, "tools", "ownership", "ownership_lib.py")
HOOK_LIB_PATH = os.path.join(REPO, ".claude", "hooks", "ownership_lib.py")
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

    def test_exclusion_and_last_match_wins(self):
        globs = ["a/**", "!a/b/**", "a/b/keep.txt"]
        self.assertTrue(lib.matches(globs, "a/x.txt"))
        self.assertFalse(lib.matches(globs, "a/b/x.txt"))
        self.assertTrue(lib.matches(globs, "a/b/keep.txt"))
        self.assertFalse(lib.matches(globs, "c.txt"))

    def test_validate_rejects_malformed_maps(self):
        for bad in ([], {}, {"orchestrator": {}, "agents": {}, "human_controlled": []},
                    {"orchestrator": {"name": "a", "email": "b", "paths": ["x"]},
                     "agents": {"x": {"name": "a", "email": "b", "paths": "x"}}, "human_controlled": []}):
            with self.assertRaises(ValueError):
                lib.validate_map(bad)


class MapShapeTests(unittest.TestCase):
    @staticmethod
    def find_agent_dir():
        """Agent files live in the bakery-team plugin, not in this repo."""
        import glob
        candidates = []
        env = os.environ.get("BAKERY_TEAM_AGENTS")
        if env:
            candidates.append(env)
        home = os.path.expanduser("~/.claude/plugins")
        candidates += sorted(glob.glob(os.path.join(home, "**", "bakery-team", "agents"), recursive=True))
        candidates.append(os.path.join(REPO, "..", "CookieMemory", "plugins", "bakery-team", "agents"))
        for c in candidates:
            if os.path.isdir(c) and any(f.endswith(".md") for f in os.listdir(c)):
                return c
        return None

    def test_every_agent_file_has_an_entry(self):
        agent_dir = self.find_agent_dir()
        if agent_dir is None:
            self.skipTest("bakery-team agent files not found (set BAKERY_TEAM_AGENTS, install the plugin, "
                          "or check out ../CookieMemory)")
        names = {f[:-3] for f in os.listdir(agent_dir) if f.endswith(".md")}
        self.assertEqual(names, set(lib.agents(DATA)))

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

    def test_dotdot_cannot_escape_or_disguise(self):
        self.assertEqual(self.rel(f"{self.proj}/docs/briefs/../../src/main/A.java"), "src/main/A.java")
        self.assertEqual(self.rel(f"{self.wt}/docs/briefs/../../.claude/settings.json"), ".claude/settings.json")
        self.assertIsNone(self.rel(f"{self.proj}/../outside.txt"))
        self.assertIsNone(self.rel(f"{self.wt}/../../../../outside.txt"))

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


class LibSyncTests(unittest.TestCase):
    @unittest.skipUnless(os.path.exists(HOOK_LIB_PATH), "no .claude/hooks/ownership_lib.py in this checkout")
    def test_repo_copy_matches_hook_lib(self):
        with open(LIB_PATH, encoding="utf-8") as fh:
            repo_copy = fh.read().split(chr(10), 1)[1]
        with open(HOOK_LIB_PATH, encoding="utf-8") as fh:
            self.assertEqual(repo_copy, fh.read())


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

    def test_unknown_author_fails_for_every_file(self):
        res = self.run_check([commit("stranger@example.com", ["README.md", "src/main/java/A.java"])])
        self.assertEqual(len(res["violations"]), 2)
        self.assertTrue(all(v[2] == "unknown author" for v in res["violations"]))

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
        os.makedirs(os.path.join(root, ".claude"), exist_ok=True)
        os.makedirs(os.path.join(root, "tools", "ownership"), exist_ok=True)
        shutil.copy(MAP_PATH, os.path.join(root, ".claude", "ownership.json"))
        shutil.copy(LIB_PATH, os.path.join(root, "tools", "ownership", "ownership_lib.py"))

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
            git(root, "add", ".claude", "tools")
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

    def test_human_controlled_needs_flag(self):
        proc = self.run_check("f/human", "--base", "feature/f")
        self.assertEqual(proc.returncode, 1)
        proc = self.run_check("f/human", "--base", "feature/f", "--human-approved")
        self.assertEqual(proc.returncode, 0, proc.stdout)
        self.assertIn("Needs human review", proc.stdout)
        self.assertIn(".claude/settings.json", proc.stdout)

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
