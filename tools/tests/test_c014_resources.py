"""Reject unsafe promotion and wrong-course evidence in the C014 handoff."""
import copy
import json
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from build_c014_resources import validate_bundle


class HandoffTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.root = Path(__file__).resolve().parents[2]
        cls.bundle = json.loads((cls.root / "docs/calibration/C014/three-lessons.json").read_text())

    def setUp(self):
        self.data = copy.deepcopy(self.bundle)

    def test_source_console_position_cannot_be_promoted_to_eye(self):
        smoke = self.data["lessons"][0]
        smoke["measuredRuntime"]["eye"] = smoke["unconfirmedConsolePosition"]["displayXYZ"]
        with self.assertRaisesRegex(ValueError, "runtime coordinates"):
            validate_bundle(self.data, self.root)

    def test_encoded_clip_duration_is_not_release_relative_lifetime(self):
        smoke = self.data["lessons"][0]
        smoke["effectStages"][-1]["releaseRelativeSeconds"] = smoke["clipEvidence"]["encodedDurationSeconds"]
        with self.assertRaisesRegex(ValueError, "release-relative timing"):
            validate_bundle(self.data, self.root)

    def test_he_cannot_reference_car_fire_as_smoke(self):
        self.data["lessons"][2]["smokeDependency"]["lessonId"] = "D2-010"
        with self.assertRaisesRegex(ValueError, "already-formed smoke"):
            validate_bundle(self.data, self.root)

    def test_he_cannot_start_the_smoke_at_release(self):
        self.data["lessons"][2]["smokeDependency"]["smokeAgeAtHEReleaseSeconds"] = 0
        with self.assertRaisesRegex(ValueError, "clock offset"):
            validate_bundle(self.data, self.root)

    def test_valid_image_hash_cannot_hide_wrong_course(self):
        self.data["lessons"][1]["screenshots"] = self.data["lessons"][0]["screenshots"]
        with self.assertRaisesRegex(ValueError, "different course"):
            validate_bundle(self.data, self.root)

    def test_wrong_map_bundle_is_rejected(self):
        self.data["mapVersion"] = "0" * 64
        with self.assertRaisesRegex(ValueError, "map version"):
            validate_bundle(self.data, self.root)


if __name__ == "__main__":
    unittest.main()
