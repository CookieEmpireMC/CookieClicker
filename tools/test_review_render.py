"""Tests for tools/review/review_render.py: the review server stays loopback-only (client endpoint, server.properties)
and the RCON password is random and owner-only. No server is started.
"""

import os
import stat
import sys
import tempfile
import unittest
from unittest import mock

REPO = os.path.realpath(os.path.join(os.path.dirname(__file__), ".."))
sys.path.insert(0, os.path.join(REPO, "tools", "review"))
import review_render as rr  # noqa: E402


class LoopbackTest(unittest.TestCase):
    def test_a_client_endpoint_off_the_loopback_is_refused(self):
        with mock.patch.dict(os.environ, {"BAKERY_RCON_HOST": "192.168.1.5"}):
            with self.assertRaises(SystemExit):
                rr.rcon_endpoint()

    def test_server_properties_bound_to_another_address_are_refused(self):
        with self.assertRaises(SystemExit):
            rr.check_loopback({"server-ip": "0.0.0.0"}, "server.properties")
        self.assertTrue(rr.check_loopback({"server-ip": "127.0.0.1"}, "server.properties"))
        self.assertFalse(rr.check_loopback({}, "server.properties"), "unset: ensure_server writes 127.0.0.1 first")

    def test_the_stored_password_is_random_and_kept_owner_only(self):
        with tempfile.TemporaryDirectory() as home:
            first = rr.stored_password(home)
            self.assertEqual(first, rr.stored_password(home), "reused, not regenerated")
            self.assertNotEqual("review", first, "not the old fixed password")
            self.assertGreaterEqual(len(first), 20)
            if os.name == "posix":
                mode = stat.S_IMODE(os.stat(os.path.join(home, rr.PASSWORD_FILE)).st_mode)
                self.assertEqual(0o600, mode)

    def test_write_properties_sets_the_keys_and_keeps_the_rest(self):
        with tempfile.TemporaryDirectory() as home:
            path = os.path.join(home, "server.properties")
            with open(path, "w", encoding="utf-8") as f:
                f.write("level-name=world\nserver-ip=10.0.0.1\n")
            lines, values = rr.read_properties(path)
            with self.assertRaises(SystemExit):
                rr.check_loopback(values, path)
            rr.write_properties(path, lines, {"server-ip": "127.0.0.1", "enable-rcon": "true"})
            _, after = rr.read_properties(path)
            self.assertEqual("127.0.0.1", after["server-ip"])
            self.assertEqual("true", after["enable-rcon"])
            self.assertEqual("world", after["level-name"])


if __name__ == "__main__":
    unittest.main()
