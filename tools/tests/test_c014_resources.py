"""Reject unsafe promotion and wrong-course evidence in the C014 handoff."""
import copy
import json
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from build_c014_resources import validate_bundle, validate_observation_draft


class HandoffTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.root = Path(__file__).resolve().parents[2]
        cls.bundle = json.loads((cls.root / "docs/calibration/C014/three-lessons.json").read_text())

    def setUp(self):
        self.data = copy.deepcopy(self.bundle)

    def test_source_console_position_cannot_be_promoted_to_eye(self):
        smoke = self.data["lessons"][0]
        smoke["measuredRuntime"]["eye"] = [-11.4866290968, 4.4352478732, 16.7655689072]
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

    def test_old_smoke_command_cannot_follow_replaced_lineup(self):
        self.data["lessons"][0]["unconfirmedConsolePosition"]["displayXYZ"] = [-11.4866, 4.4352, 16.7656]
        with self.assertRaisesRegex(ValueError, "superseded command"):
            validate_bundle(self.data, self.root)

    def test_hang_door_cannot_silently_switch_to_full_seal(self):
        self.data["lessons"][0]["sourceDeclaredTeaching"]["selectedVariant"] = "full-seal-forward-adjustment"
        with self.assertRaisesRegex(ValueError, "variant/source mismatch"):
            validate_bundle(self.data, self.root)

    def test_he_cannot_use_the_old_plain_throw_flags(self):
        flags = self.data["lessons"][2]["sourceDeclaredTeaching"]["throwFlags"]
        flags["jump"] = False
        flags["crouchAtRelease"] = False
        with self.assertRaisesRegex(ValueError, "throw flags mismatch"):
            validate_bundle(self.data, self.root)

    def test_old_webpage_cannot_remain_the_active_smoke_source(self):
        self.data["lessons"][0]["sourceDeclaredTeaching"]["url"] = "https://getreplay.gg/en/utility/dust2/smoke/d2-mid-door-smoke"
        with self.assertRaisesRegex(ValueError, "variant/source mismatch"):
            validate_bundle(self.data, self.root)

    def draft(self):
        return json.loads((self.root / self.bundle["lessons"][0]["appDraft"]["path"]).read_text())

    def test_formal_camera_slots_cannot_be_omitted(self):
        draft = self.draft()
        del draft["cameras"]["landing"]
        with self.assertRaisesRegex(ValueError, "five camera slots"):
            validate_observation_draft(draft)

    def test_overview_cannot_be_promoted_to_measured_follow_camera(self):
        draft = self.draft()
        draft["cameras"]["follow"] = {"position": [0, 0, 0], "yaw": 0, "pitch": 30, "distance": 7}
        with self.assertRaisesRegex(ValueError, "overview defaults"):
            validate_observation_draft(draft)

    def test_video_progress_cannot_replace_release_clock(self):
        draft = self.draft()
        draft["timeOrigin"] = "video-progress-seconds"
        with self.assertRaisesRegex(ValueError, "coordinate/time"):
            validate_observation_draft(draft)

    def test_missing_measurement_cannot_be_filled_from_dev_course(self):
        draft = self.draft()
        draft["path"] = [{"seconds": 0, "position": [0, 0, 0]}]
        with self.assertRaisesRegex(ValueError, "runtime field"):
            validate_observation_draft(draft)

    def test_receipt_for_different_app_revision_is_rejected(self):
        self.data["appObservationReceipt"]["appHead"] = "0" * 40
        with self.assertRaisesRegex(ValueError, "receipt version"):
            validate_bundle(self.data, self.root)


if __name__ == "__main__":
    unittest.main()
