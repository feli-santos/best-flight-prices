import tempfile
import unittest
from pathlib import Path

import sys
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import check_docs  # noqa: E402


class CheckDocsTest(unittest.TestCase):
    def test_detects_missing_and_broken(self):
        with tempfile.TemporaryDirectory() as d:
            root = Path(d)
            (root / "README.md").write_text("[x](nope.md) \n")
            errs = check_docs.check(root)
            self.assertTrue(any("broken link nope.md" in e for e in errs))
            self.assertTrue(any("trailing whitespace" in e for e in errs))
            self.assertTrue(any("missing required file: AGENTS.md" in e for e in errs))

    def test_repo_is_clean(self):
        self.assertEqual(check_docs.check(), [])


if __name__ == "__main__":
    unittest.main()
