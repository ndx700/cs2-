import hashlib
import json
import math
import sys
import tempfile
import unittest
from pathlib import Path

import numpy as np

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT/'tools'))
from c015_scene_geometry import SceneGeometry, camera_basis, pixel_ray
from fit_c015_camera import app_angles, camera_from_parameters, fit_standing, project


class SpatialGeometryTests(unittest.TestCase):
    def test_projection_roundtrip_preserves_unknown_video_stretch(self):
        view = camera_from_parameters([-9, 4, 18, -.05, .2, math.radians(68), 1.28])
        for pixel in ([640, 360], [320, 190], [1000, 600]):
            eye, direction = pixel_ray(view, pixel, 1280, 720)
            self.assertTrue(np.allclose(project(view, [eye+direction*8], [1280, 720])[0], pixel, atol=1e-8))

    def test_app_camera_signs_point_to_world_aim(self):
        view = camera_from_parameters([-9, 4, 18, -.05, .2, math.radians(68), 1.28])
        yaw, pitch = np.radians(app_angles(view))
        actual = [-math.sin(yaw)*math.cos(pitch), -math.sin(pitch), -math.cos(yaw)*math.cos(pitch)]
        self.assertTrue(np.allclose(actual, camera_basis(view['eye'], view['target'])[2], atol=1e-10))

    def test_nonplanar_correspondences_recover_synthetic_pose(self):
        values = np.array([-9, 4, 18, -.05, .2, math.radians(68), 1.28])
        points = np.random.default_rng(5).uniform([-13, 4, 8], [-4, 8, 12], (20, 3))
        pixels = project(camera_from_parameters(values), points, [1280, 720])
        fitted, errors = fit_standing(points, pixels, [1280, 720])
        self.assertTrue(np.allclose(fitted, values, atol=1e-5))
        self.assertLess(np.abs(errors).max(), 1e-5)

    def test_invalid_camera_and_zero_ray_rejected(self):
        with self.assertRaises(ValueError):
            camera_basis([0, 0, 0], [0, 1, 0])
        scene = SceneGeometry(ROOT/'app/src/main/assets')
        with self.assertRaises(ValueError):
            scene.pick([0, 0, 0], [0, 0, 0])

    def test_corrupt_geometry_cannot_be_used_for_fitting(self):
        original = SceneGeometry(ROOT/'app/src/main/assets')
        spec = original.manifest['parts'][817]
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            manifest = root/'maps/dust2/mobile/manifest.json'
            manifest.parent.mkdir(parents=True)
            manifest.write_text(json.dumps(original.manifest))
            blob = root/spec['asset']
            blob.parent.mkdir(parents=True)
            blob.write_bytes(b'corrupt')
            with self.assertRaisesRegex(ValueError, 'hash mismatch'):
                SceneGeometry(root).part(spec)

    def test_image_estimate_does_not_replace_formal_runtime(self):
        candidate = json.loads((ROOT/'docs/calibration/C015/D2-001-spatial-candidate.json').read_text())
        draft = json.loads((ROOT/candidate['runtimeDraft']).read_text())
        self.assertIsNotNone(candidate['candidateValues']['eye'])
        self.assertFalse(candidate['importable'])
        self.assertIsNone(draft['eye'])
        self.assertIsNone(draft['path'])
        self.assertFalse(draft['importable'])
        self.assertTrue(all(v is None for v in candidate['verifiedRuntimeValues'].values()))


if __name__ == '__main__':
    unittest.main()
