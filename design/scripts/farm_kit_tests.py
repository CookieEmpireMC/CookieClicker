"""Runs the farm kit and L-F lint tests (design/scripts/lib/tests) as part of `mise run assets`; fails the run when one breaks."""
import sys
import unittest
from pathlib import Path

START = Path(__file__).resolve().parent / "lib" / "tests"

if __name__ == "__main__":
    suite = unittest.defaultTestLoader.discover(str(START), top_level_dir=str(START))
    result = unittest.TextTestRunner(verbosity=1).run(suite)
    sys.exit(0 if result.wasSuccessful() else 1)
