"""Tests for the GitHub App tools. Stdlib unittest, throwaway RSA key made with openssl in a
temp dir, fake GitHub API on 127.0.0.1. Never calls the real API, never reads ~/.config."""

import io
import json
import os
import shutil
import subprocess
import sys
import tempfile
import threading
import unittest
from http.server import BaseHTTPRequestHandler, HTTPServer

from github_app import app, open_pr, token as token_cli

FAKE_TOKEN = "ghs_FAKEINSTALLATIONTOKEN1234567890"
APP_ID = "4242"
INSTALLATION_ID = "777"
HAVE_OPENSSL = shutil.which("openssl") is not None


class FakeGitHub:
    """Loopback HTTP server. ``responses`` maps (method, path) -> (status, body dict)."""

    def __init__(self):
        self.requests = []
        self.responses = {}
        outer = self

        class Handler(BaseHTTPRequestHandler):
            def log_message(self, *a):
                pass

            def do_POST(self):
                length = int(self.headers.get("Content-Length") or 0)
                raw = self.rfile.read(length)
                outer.requests.append({"path": self.path, "headers": dict(self.headers),
                                       "body": json.loads(raw) if raw else None})
                status, body = outer.responses.get(
                    ("POST", self.path), (404, {"message": "Not Found"}))
                data = json.dumps(body).encode()
                self.send_response(status)
                self.send_header("Content-Type", "application/json")
                self.send_header("Content-Length", str(len(data)))
                self.end_headers()
                self.wfile.write(data)

        self.server = HTTPServer(("127.0.0.1", 0), Handler)
        self.url = f"http://127.0.0.1:{self.server.server_port}"
        self.thread = threading.Thread(target=self.server.serve_forever, daemon=True)
        self.thread.start()

    def close(self):
        self.server.shutdown()
        self.server.server_close()
        self.thread.join(5)

    @property
    def token_path(self):
        return f"/app/installations/{INSTALLATION_ID}/access_tokens"

    def ok_token(self):
        self.responses[("POST", self.token_path)] = (201, {"token": FAKE_TOKEN})

    def pulls(self, status, body, repo="Self-Learned/Cookie-Clicker"):
        self.responses[("POST", f"/repos/{repo}/pulls")] = (status, body)


@unittest.skipUnless(HAVE_OPENSSL, "openssl not installed")
class Base(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.tmp = tempfile.mkdtemp(prefix="ghapp-test-")
        cls.key = os.path.join(cls.tmp, "key.pem")
        subprocess.run(["openssl", "genrsa", "-out", cls.key, "2048"], check=True,
                       capture_output=True)
        os.chmod(cls.key, 0o600)
        cls.pub = os.path.join(cls.tmp, "pub.pem")
        subprocess.run(["openssl", "rsa", "-in", cls.key, "-pubout", "-out", cls.pub],
                       check=True, capture_output=True)

    @classmethod
    def tearDownClass(cls):
        shutil.rmtree(cls.tmp, ignore_errors=True)

    def setUp(self):
        self.dir = tempfile.mkdtemp(dir=self.tmp)
        self.fake = FakeGitHub()
        self.addCleanup(self.fake.close)
        self.fake.ok_token()
        # an existing empty file keeps the tests away from ~/.config/cookieclicker
        empty = self.write_config("", "empty.env")
        self.env = {"APP_ID": APP_ID, "INSTALLATION_ID": INSTALLATION_ID,
                    "KEY_PATH": self.key, "GITHUB_API_URL": self.fake.url,
                    "GITHUB_APP_CONFIG": empty}

    def write_config(self, text, name="app.env"):
        path = os.path.join(self.dir, name)
        with open(path, "w") as fh:
            fh.write(text)
        return path

    def run_token(self, argv=(), env=None):
        out, err = io.StringIO(), io.StringIO()
        code = token_cli.main(list(argv), self.env if env is None else env, out, err)
        return code, out.getvalue(), err.getvalue()

    def pr_args(self, *extra):
        body = os.path.join(self.dir, "body.md")
        with open(body, "w") as fh:
            fh.write("Body text")
        return ["--head", "feature/x", "--base", "main", "--title", "T", "--body-file", body,
                *extra]

    def run_pr(self, *extra, env=None):
        out, err = io.StringIO(), io.StringIO()
        code = open_pr.main(self.pr_args(*extra), self.env if env is None else env, out, err)
        return code, out.getvalue(), err.getvalue()

    def assertNoSecrets(self, *texts):
        for text in texts:
            self.assertNotIn(FAKE_TOKEN, text)
            self.assertNotIn("BEGIN", text)
            self.assertNotIn("eyJ", text)  # any JWT starts with base64 of '{"'


class JwtTests(Base):
    def test_structure_claims_and_signature(self):
        jwt = app.build_jwt(APP_ID, self.key, now=1_000_000)
        head, claims, sig = jwt.split(".")
        self.assertEqual(json.loads(app.b64url_decode(head)), {"alg": "RS256", "typ": "JWT"})
        self.assertEqual(json.loads(app.b64url_decode(claims)),
                         {"iat": 1_000_000 - 60, "exp": 1_000_000 + 540, "iss": APP_ID})
        for part in jwt.split("."):
            self.assertNotIn("=", part)
            self.assertNotIn("+", part)
            self.assertNotIn("/", part)
        sigfile = os.path.join(self.dir, "sig")
        with open(sigfile, "wb") as fh:
            fh.write(app.b64url_decode(sig))
        result = subprocess.run(
            ["openssl", "dgst", "-sha256", "-verify", self.pub, "-signature", sigfile],
            input=f"{head}.{claims}".encode(), capture_output=True)
        self.assertEqual(result.returncode, 0, result.stderr)

    def test_tampered_payload_fails_verification(self):
        head, claims, sig = app.build_jwt(APP_ID, self.key, now=5).split(".")
        sigfile = os.path.join(self.dir, "sig")
        with open(sigfile, "wb") as fh:
            fh.write(app.b64url_decode(sig))
        result = subprocess.run(
            ["openssl", "dgst", "-sha256", "-verify", self.pub, "-signature", sigfile],
            input=f"{head}.{claims}x".encode(), capture_output=True)
        self.assertNotEqual(result.returncode, 0)

    def test_default_time_is_now(self):
        import time
        before = int(time.time())
        claims = json.loads(app.b64url_decode(app.build_jwt(APP_ID, self.key).split(".")[1]))
        self.assertGreaterEqual(claims["iat"], before - 60)
        self.assertLessEqual(claims["exp"] - claims["iat"], 600)

    def test_missing_openssl_is_clean_error(self):
        with self.assertRaises(app.KeyFileError):
            app.build_jwt(APP_ID, self.key, openssl="definitely-not-openssl")


class ConfigTests(Base):
    def test_file_values(self):
        path = self.write_config(
            f"# comment\nAPP_ID=1\nexport INSTALLATION_ID='2'\nKEY_PATH=\"{self.key}\"\n")
        cfg = app.load_config(path, {})
        self.assertEqual((cfg.app_id, cfg.installation_id, cfg.key_path), ("1", "2", self.key))

    def test_env_overrides_file(self):
        path = self.write_config(f"APP_ID=1\nINSTALLATION_ID=2\nKEY_PATH={self.key}\n")
        cfg = app.load_config(path, {"APP_ID": "9", "INSTALLATION_ID": "8"})
        self.assertEqual((cfg.app_id, cfg.installation_id, cfg.key_path), ("9", "8", self.key))
        self.assertEqual(cfg.sources["APP_ID"], "environment")

    def test_precedence_arg_over_env_var_over_default(self):
        a = self.write_config(f"APP_ID=1\nINSTALLATION_ID=1\nKEY_PATH={self.key}\n", "a.env")
        b = self.write_config(f"APP_ID=2\nINSTALLATION_ID=2\nKEY_PATH={self.key}\n", "b.env")
        self.assertEqual(app.load_config(a, {"GITHUB_APP_CONFIG": b}).app_id, "1")
        self.assertEqual(app.load_config(None, {"GITHUB_APP_CONFIG": b}).app_id, "2")
        home = os.path.join(self.dir, "home")
        default = os.path.join(home, ".config", "cookieclicker", "github-app.env")
        os.makedirs(os.path.dirname(default))
        with open(default, "w") as fh:
            fh.write(f"APP_ID=3\nINSTALLATION_ID=3\nKEY_PATH={self.key}\n")
        old = os.environ.get("HOME")
        os.environ["HOME"] = home
        try:
            self.assertEqual(app.load_config(None, {}).app_id, "3")
        finally:
            if old is None:
                del os.environ["HOME"]
            else:
                os.environ["HOME"] = old

    def test_relative_key_path_resolves_against_file(self):
        path = self.write_config("APP_ID=1\nINSTALLATION_ID=2\nKEY_PATH=rel.pem\n")
        self.assertEqual(app.load_config(path, {}).key_path, os.path.join(self.dir, "rel.pem"))

    def test_missing_explicit_file(self):
        with self.assertRaises(app.ConfigError):
            app.load_config(os.path.join(self.dir, "nope.env"), {})

    def test_missing_keys_named(self):
        path = self.write_config("APP_ID=1\n")
        with self.assertRaises(app.ConfigError) as cm:
            app.load_config(path, {})
        self.assertIn("INSTALLATION_ID", str(cm.exception))
        self.assertIn("KEY_PATH", str(cm.exception))

    def test_non_numeric_ids(self):
        path = self.write_config(f"APP_ID=abc\nINSTALLATION_ID=2\nKEY_PATH={self.key}\n")
        with self.assertRaises(app.ConfigError):
            app.load_config(path, {})

    def test_malformed_line_not_echoed(self):
        path = self.write_config("this-is-secret-looking\n")
        with self.assertRaises(app.ConfigError) as cm:
            app.load_config(path, {})
        self.assertNotIn("secret-looking", str(cm.exception))

    def test_cli_missing_config_reports_and_fails(self):
        env = {"GITHUB_APP_CONFIG": os.path.join(self.dir, "none.env")}
        code, out, err = self.run_token(env=env)
        self.assertEqual(code, 1)
        self.assertEqual(out, "")
        self.assertIn("error", err)


class KeyTests(Base):
    def make_key(self, mode, content=None):
        path = os.path.join(self.dir, f"k{mode:o}.pem")
        if content is None:
            shutil.copy(self.key, path)
        else:
            with open(path, "w") as fh:
                fh.write(content)
        os.chmod(path, mode)
        return path

    def test_unsafe_modes_refused(self):
        for mode in (0o644, 0o640, 0o604, 0o660, 0o666):
            with self.subTest(mode=oct(mode)):
                with self.assertRaises(app.KeyFileError) as cm:
                    app.check_key_file(self.make_key(mode))
                self.assertIn("chmod 600", str(cm.exception))

    def test_safe_modes_accepted(self):
        for mode in (0o600, 0o400):
            with self.subTest(mode=oct(mode)):
                app.check_key_file(self.make_key(mode))

    def test_missing_key(self):
        with self.assertRaises(app.KeyFileError):
            app.check_key_file(os.path.join(self.dir, "none.pem"))

    def test_directory_refused(self):
        with self.assertRaises(app.KeyFileError):
            app.check_key_file(self.dir)

    def test_not_a_key(self):
        path = self.make_key(0o600, "hello, not a key\n")
        with self.assertRaises(app.KeyFileError):
            app.build_jwt(APP_ID, path)

    def test_cli_unsafe_key_makes_no_request(self):
        self.env["KEY_PATH"] = self.make_key(0o644)
        code, out, err = self.run_token()
        self.assertEqual(code, 1)
        self.assertEqual(out, "")
        self.assertEqual(self.fake.requests, [])
        self.assertNoSecrets(err)

    def test_cli_not_a_key(self):
        self.env["KEY_PATH"] = self.make_key(0o600, "garbage")
        code, out, err = self.run_token()
        self.assertEqual((code, out), (1, ""))
        self.assertEqual(self.fake.requests, [])
        self.assertNotIn("garbage", err)


class TokenTests(Base):
    def test_success_prints_exactly_token(self):
        code, out, err = self.run_token()
        self.assertEqual(code, 0)
        self.assertEqual(out, FAKE_TOKEN + "\n")
        self.assertEqual(err, "")
        req = self.fake.requests[0]
        self.assertEqual(req["path"], self.fake.token_path)
        auth = req["headers"]["Authorization"]
        self.assertTrue(auth.startswith("Bearer eyJ"))
        claims = json.loads(app.b64url_decode(auth.split()[1].split(".")[1]))
        self.assertEqual(claims["iss"], APP_ID)

    def test_config_flag(self):
        path = self.write_config(f"APP_ID={APP_ID}\nINSTALLATION_ID={INSTALLATION_ID}\n"
                                 f"KEY_PATH={self.key}\n")
        env = {"GITHUB_API_URL": self.fake.url}
        code, out, _ = self.run_token(["--config", path], env)
        self.assertEqual((code, out), (0, FAKE_TOKEN + "\n"))

    def error_case(self, status, body, expect):
        self.fake.responses[("POST", self.fake.token_path)] = (status, body)
        code, out, err = self.run_token()
        self.assertEqual(code, 1)
        self.assertEqual(out, "")
        self.assertIn(expect, err)
        self.assertNoSecrets(err)

    def test_401(self):
        self.error_case(401, {"message": "bad jwt"}, "401")

    def test_404(self):
        self.error_case(404, {"message": "Not Found"}, "INSTALLATION_ID")

    def test_500_non_json(self):
        self.error_case(500, {"message": "boom"}, "500")

    def test_jwt_echoed_in_error_is_redacted(self):
        jwt = app.build_jwt(APP_ID, self.key, now=1)
        with self.assertRaises(app.ApiError) as cm:
            self.fake.responses[("POST", "/x")] = (400, {"message": f"got {jwt}"})
            app.api_request("POST", self.fake.url + "/x", jwt)
        self.assertNotIn(jwt, str(cm.exception))
        self.assertNotIn(jwt, cm.exception.remote_message)

    def test_missing_token_in_response(self):
        self.fake.responses[("POST", self.fake.token_path)] = (201, {"nope": 1})
        code, out, err = self.run_token()
        self.assertEqual((code, out), (1, ""))

    def test_network_error(self):
        self.fake.close()
        code, out, err = self.run_token()
        self.assertEqual((code, out), (1, ""))
        self.assertIn("cannot reach", err)
        self.assertNoSecrets(err)
        self.fake = FakeGitHub()  # keep cleanup happy
        self.addCleanup(self.fake.close)

    def test_non_loopback_http_refused(self):
        for url in ("http://example.com", "http://127.0.0.1.evil.com", "ftp://127.0.0.1",
                    "http://api.github.com"):
            with self.subTest(url=url):
                self.env["GITHUB_API_URL"] = url
                code, out, err = self.run_token()
                self.assertEqual((code, out), (1, ""))
                self.assertIn("GITHUB_API_URL", err)
                self.assertEqual(self.fake.requests, [])

    def test_https_and_loopback_http_accepted(self):
        self.assertEqual(app.api_base_url({"GITHUB_API_URL": "https://ghe.example/api/"}),
                         "https://ghe.example/api")
        self.assertEqual(app.api_base_url({}), "https://api.github.com")
        self.assertEqual(app.api_base_url({"GITHUB_API_URL": "http://localhost:1"}),
                         "http://localhost:1")

    def test_script_stdout_is_only_token(self):
        """Run token.py as a real script (also covers the sys.path bootstrap)."""
        script = os.path.join(os.path.dirname(os.path.abspath(app.__file__)), "token.py")
        env = {**os.environ, **self.env}
        result = subprocess.run([sys.executable, script], env=env, capture_output=True,
                                text=True, timeout=60)
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(result.stdout, FAKE_TOKEN + "\n")
        self.assertEqual(result.stderr, "")
        self.assertEqual(os.listdir(self.dir), ["empty.env"])  # nothing written


class PullRequestTests(Base):
    def test_success_and_payload(self):
        self.fake.pulls(201, {"html_url": "https://github.com/o/r/pull/9", "number": 9,
                              "user": {"login": "cookie-bakery[bot]"}})
        code, out, err = self.run_pr()
        self.assertEqual(code, 0, err)
        self.assertEqual(out, "url: https://github.com/o/r/pull/9\nnumber: 9\n"
                              "author: cookie-bakery[bot]\n")
        self.assertEqual(err, "")
        self.assertNoSecrets(out)
        pr_req = self.fake.requests[-1]
        self.assertEqual(pr_req["headers"]["Authorization"], f"Bearer {FAKE_TOKEN}")
        self.assertEqual(pr_req["body"], {"title": "T", "head": "feature/x", "base": "main",
                                          "body": "Body text", "draft": False})

    def test_draft(self):
        self.fake.pulls(201, {"html_url": "u", "number": 1, "user": {"login": "b"}})
        self.assertEqual(self.run_pr("--draft")[0], 0)
        self.assertTrue(self.fake.requests[-1]["body"]["draft"])

    def test_other_repo(self):
        self.fake.pulls(201, {"html_url": "u", "number": 1, "user": {"login": "b"}},
                        repo="a/b")
        self.assertEqual(self.run_pr("--repo", "a/b")[0], 0)

    def test_bad_repo_rejected_before_network(self):
        for repo in ("not-a-repo", "a b/c", "a/b/c", "a/b?x=1"):
            with self.subTest(repo=repo):
                code, out, err = self.run_pr("--repo", repo)
                self.assertEqual(code, 1)
                self.assertEqual(self.fake.requests, [])

    @unittest.expectedFailure
    def test_dot_segments_in_repo_rejected(self):
        # KNOWN BUG (see review): REPO_PATTERN accepts '..' as owner or name
        code, out, err = self.run_pr("--repo", "../evil")
        self.assertEqual(code, 1)
        self.assertEqual(self.fake.requests, [])

    def test_empty_title_rejected_before_network(self):
        out, err = io.StringIO(), io.StringIO()
        args = self.pr_args()
        args[args.index("--title") + 1] = "  "
        code = open_pr.main(args, self.env, out, err)
        self.assertEqual(code, 1)
        self.assertEqual(self.fake.requests, [])

    def test_missing_body_file(self):
        out, err = io.StringIO(), io.StringIO()
        args = self.pr_args()
        args[args.index("--body-file") + 1] = os.path.join(self.dir, "none.md")
        self.assertEqual(open_pr.main(args, self.env, out, err), 1)
        self.assertIn("body file not found", err.getvalue())

    def test_body_from_stdin(self):
        self.fake.pulls(201, {"html_url": "u", "number": 1, "user": {"login": "b"}})
        out, err = io.StringIO(), io.StringIO()
        args = self.pr_args()
        args[args.index("--body-file") + 1] = "-"
        code = open_pr.main(args, self.env, out, err, stdin=io.StringIO("from stdin"))
        self.assertEqual(code, 0)
        self.assertEqual(self.fake.requests[-1]["body"]["body"], "from stdin")

    def fail_case(self, status, body, expect):
        self.fake.pulls(status, body)
        code, out, err = self.run_pr()
        self.assertEqual(code, 1)
        self.assertEqual(out, "")
        self.assertIn(expect, err)
        self.assertNoSecrets(err)
        return err

    def test_401(self):
        self.fail_case(401, {"message": "Bad credentials"}, "401")

    def test_403(self):
        self.fail_case(403, {"message": "Resource not accessible by integration"}, "403")

    def test_404(self):
        self.fail_case(404, {"message": "Not Found"}, "not found")

    def test_422_exists(self):
        err = self.fail_case(422, {"message": "Validation Failed", "errors": [
            {"message": "A pull request already exists for o:feature/x."}]}, "already exists")
        self.assertIn("feature/x", err)

    def test_exists_is_distinct_exception(self):
        self.fake.pulls(422, {"errors": [{"message": "A pull request already exists"}]})
        with self.assertRaises(app.PullRequestExistsError):
            app.create_pull_request(FAKE_TOKEN, "Self-Learned/Cookie-Clicker", "h", "main",
                                    "t", "b", base_url=self.fake.url)

    def test_422_other(self):
        self.fail_case(422, {"message": "Validation Failed",
                             "errors": [{"message": "No commits between main and x"}]}, "422")

    def test_remote_message_with_token_redacted(self):
        self.fake.pulls(403, {"message": f"nope {FAKE_TOKEN}"})
        code, out, err = self.run_pr()
        self.assertEqual(code, 1)
        self.assertNoSecrets(err, out)

    def test_token_step_failure_stops_before_pr(self):
        self.fake.responses[("POST", self.fake.token_path)] = (401, {"message": "bad"})
        code, out, err = self.run_pr()
        self.assertEqual((code, out), (1, ""))
        self.assertEqual(len(self.fake.requests), 1)

    def test_non_loopback_http_refused(self):
        self.env["GITHUB_API_URL"] = "http://example.com"
        code, out, err = self.run_pr()
        self.assertEqual(code, 1)
        self.assertEqual(self.fake.requests, [])

    def test_network_error(self):
        env = dict(self.env)
        self.fake.close()
        out = io.StringIO()
        err = io.StringIO()
        code = open_pr.main(self.pr_args(), env, out, err)
        self.assertEqual(code, 1)
        self.assertNoSecrets(err.getvalue())
        self.fake = FakeGitHub()
        self.addCleanup(self.fake.close)


class RedactTests(unittest.TestCase):
    def test_redact(self):
        self.assertEqual(app.redact("a SECRET b SECRET", ["SECRET", "", None]), "a *** b ***")


if __name__ == "__main__":
    unittest.main()
