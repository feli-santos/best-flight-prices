import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import build_catalog as b  # noqa: E402
import validate_catalog as v  # noqa: E402


class HelpersTest(unittest.TestCase):
    def test_haversine(self):
        self.assertAlmostEqual(b.haversine_km(0, 0, 0, 0), 0.0)
        d = b.haversine_km(-19.6244, -43.9719, -23.4356, -46.4731)
        self.assertTrue(400 < d < 600)

    def test_classify_cyclone_boundaries(self):
        self.assertEqual(b.classify_cyclone(0.0), "LOW")
        self.assertEqual(b.classify_cyclone(0.049), "LOW")
        self.assertEqual(b.classify_cyclone(0.05), "MODERATE")
        self.assertEqual(b.classify_cyclone(0.199), "MODERATE")
        self.assertEqual(b.classify_cyclone(0.20), "HIGH")

    def test_parse_months(self):
        self.assertEqual(b.parse_months("3, 4,3"), [3, 4])
        self.assertEqual(b.parse_months(""), [])

    def test_nearest_timezone(self):
        places = [(35.7, 139.7, "Asia/Tokyo"), (34.7, 135.5, "Asia/Tokyo"), (51.5, -0.1, "Europe/London")]
        self.assertEqual(b.nearest_timezone(35.55, 139.78, places), "Asia/Tokyo")
        self.assertIsNone(b.nearest_timezone(0.0, 0.0, places))


class CuratedTest(unittest.TestCase):
    def test_curated_files_parse_and_are_consistent(self):
        countries = b.parse_pipe_file(b.CURATED / "countries.txt")
        cities = b.parse_pipe_file(b.CURATED / "cities.txt")
        codes = {r[0] for r in countries}
        self.assertEqual(len(codes), len(countries), "duplicate country codes")
        self.assertEqual(sum(1 for r in countries if int(r[2]) > 0), 50)
        ids = [r[0] for r in cities]
        self.assertEqual(len(ids), len(set(ids)))
        for r in cities:
            self.assertEqual(len(r), 8, r)
            self.assertIn(r[1], codes, r[0])
        counts = {}
        for r in cities:
            counts[r[1]] = counts.get(r[1], 0) + 1
        for cc in codes:
            self.assertTrue(1 <= counts.get(cc, 0) <= 3, cc)
        exps = b.parse_experiences(b.CURATED / "experiences.txt")
        for e in exps:
            for d in e["destinations"]:
                self.assertIn(d, ids, e["id"])


class ValidateTest(unittest.TestCase):
    def test_flags_missing_files_content(self):
        with tempfile.TemporaryDirectory() as d:
            root = Path(d)
            (root / "cat").mkdir()
            (root / "srv").mkdir()
            for n in ("airports", "countries", "destinations"):
                (root / "cat" / f"{n}.json").write_text("[]")
            for n in ("climate", "cyclones", "seismic"):
                (root / "srv" / f"{n}.json").write_text("{}")
            (root / "srv" / "experiences.json").write_text("[]")
            errs = v.validate(root / "cat", root / "srv")
            self.assertTrue(any("top-50" in e for e in errs))

    @unittest.skipUnless((v.CAT / "destinations.json").exists(), "catalog not generated")
    def test_generated_catalog_is_valid(self):
        self.assertEqual(v.validate(), [])


if __name__ == "__main__":
    unittest.main()
