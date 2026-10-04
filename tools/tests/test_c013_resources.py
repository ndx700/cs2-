"""Meaningful geometry/source regressions; all geometry here is explicitly synthetic."""
import hashlib
import json
import sys
import tempfile
import unittest
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from collect_c013_references import Page, extract_commands
from dust2_collision_query import CollisionQuery, display_to_source, source_to_display, validated_raw
from adapt_c013_pending import make_pending
from inventory_c013_local_textures import candidate_parts


def query_fixture():
    vertices, triangles, layers = [], [], []

    def quad(points, layer=0):
        start = len(vertices)
        vertices.extend(points)
        triangles.extend([[start, start + 1, start + 2], [start, start + 2, start + 3]])
        layers.extend([layer, layer])

    # Wall with a real opening [-0.5,0.5] at x=0, lower floor and an upper floor.
    quad([[0, 0, -2], [0, 3, -2], [0, 3, -0.5], [0, 0, -0.5]])
    quad([[0, 0, 0.5], [0, 3, 0.5], [0, 3, 2], [0, 0, 2]])
    quad([[-2, 0, -2], [2, 0, -2], [2, 0, 2], [-2, 0, 2]])
    quad([[0.5, 2, -2], [2, 2, -2], [2, 2, 2], [0.5, 2, 2]])
    # Separate invisible grenade and player clips; layer policy must be explicit.
    quad([[1.5, 0, -0.4], [1.5, 3, -0.4], [1.5, 3, 0.4], [1.5, 0, 0.4]], 4)
    quad([[1, 0, -0.4], [1, 3, -0.4], [1, 3, 0.4], [1, 0, 0.4]], 3)
    tri = np.array(triangles)
    return CollisionQuery(np.array(vertices), tri, np.array(layers), np.zeros(len(tri), dtype=int),
                          np.arange(len(tri)) // 2,
                          {"collisionAttributes": [{"index": n} for n in range(6)], "surfacePropertyHashes": [123]})


class QueryTests(unittest.TestCase):
    def setUp(self):
        self.query = query_fixture()

    def test_source_display_roundtrip_and_up_axis(self):
        xyz = np.array([[-452.229492, -660.061768, 174.616058], [0, 0, 64]])
        np.testing.assert_allclose(display_to_source(source_to_display(xyz)), xyz, atol=1e-10)
        np.testing.assert_allclose(source_to_display([0, 0, 64]), [0, 1.6256, 0])

    def test_wall_blocks_and_doorway_connects(self):
        wall = self.query.trace_segment([-1, 1, 1], [1, 1, 1], layers=[0])
        self.assertAlmostEqual(wall["fraction"], 0.5)
        self.assertIsNone(self.query.trace_segment([-1, 1, 0], [1, 1, 0], layers=[0]))

    def test_nearest_floor_at_different_heights(self):
        lower = self.query.ground_below([-1, 1, 1], max_drop=5, layers=[0])
        upper = self.query.ground_below([1, 3, 1], max_drop=5, layers=[0])
        below_upper = self.query.ground_below([1, 1, 1], max_drop=5, layers=[0])
        self.assertEqual(lower["positionDisplay"][1], 0)
        self.assertEqual(upper["positionDisplay"][1], 2)
        self.assertEqual(below_upper["positionDisplay"][1], 0)

    def test_clip_layers_are_not_a_display_filter(self):
        self.assertIsNone(self.query.trace_segment([0.6, 1, 0], [2, 1, 0], layers=[0]))
        grenade = self.query.trace_segment([0.6, 1, 0], [2, 1, 0], layers=[0, 4])
        player = self.query.trace_segment([0.6, 1, 0], [2, 1, 0], layers=[0, 3])
        self.assertEqual(grenade["layer"], 4)
        self.assertEqual(player["layer"], 3)
        self.assertEqual(grenade["surfaceHash"], 123)

    def test_reverse_segment_same_surface(self):
        a = self.query.trace_segment([-1, 1, 1], [1, 1, 1], layers=[0])
        b = self.query.trace_segment([1, 1, 1], [-1, 1, 1], layers=[0])
        self.assertEqual(a["positionDisplay"], b["positionDisplay"])
        np.testing.assert_allclose(np.array(a["normalAgainstSegment"]), -np.array(b["normalAgainstSegment"]))

    def test_per_triangle_surface_override_preserved(self):
        self.query.manifest["surfacePropertyHashes"].append(456)
        self.query.surfaces[0:2] = 1
        hit = self.query.trace_segment([-1, 1, -1], [1, 1, -1], layers=[0])
        self.assertEqual(hit["surfaceIndex"], 1)
        self.assertEqual(hit["surfaceHash"], 456)

    def test_invalid_queries_rejected(self):
        for start, end, layers in [([0, 0, 0], [0, 0, 0], [0]), ([float("nan"), 1, 0], [1, 1, 0], [0]), ([0, 1, 0], [1, 1, 0], []), ([0, 1, 0], [1, 1, 0], [99])]:
            with self.assertRaises(ValueError):
                self.query.trace_segment(start, end, layers=layers)


class SourceTests(unittest.TestCase):
    def test_parse_command_without_promoting_to_calibration(self):
        commands = extract_commands('text: \\"setpos -452.2 -660 174.6;setang -9.9 88.6 0\\"')
        self.assertEqual(len(commands), 1)
        self.assertEqual(commands[0]["status"], "SOURCE_DECLARED_NOT_GAME_VALIDATED")

    def test_media_section_and_unrelated_images(self):
        p = Page()
        p.feed('<img src="https://mc.yandex.ru/a"><section aria-labelledby="section-guide"><img src="https://storage.getreplay.gg/a.jpg" alt="aim"></section>')
        self.assertEqual(len(p.media), 1)
        self.assertEqual(p.media[0]["section"], "section-guide")

    def test_corrupt_raw_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            p = Path(directory)
            names = ["vertices.f32", "triangles.u32", "triangle_layers.u8", "triangle_surfaces.u8", "triangle_shapes.u32"]
            files = {}
            for name in names:
                body = b'original'; (p / name).write_bytes(body)
                files[name] = {"bytes": len(body), "sha256": hashlib.sha256(body).hexdigest()}
            (p / "manifest.json").write_text(json.dumps({"format": "cs2-collision-raw", "version": 1, "map": "de_dust2", "byteOrder": "little-endian", "files": files}))
            (p / "vertices.f32").write_bytes(b'corrupt!')
            with self.assertRaisesRegex(ValueError, "checksum mismatch"):
                validated_raw(p)


class AdapterTests(unittest.TestCase):
    def setUp(self):
        root = Path(__file__).resolve().parents[2]
        self.record = json.loads((root / "docs/calibration/C013/calibration-records.json").read_text())["records"][0]
        self.version = self.record["versions"]["mobileManifestSha256"]

    def test_command_position_does_not_become_feet_or_eye(self):
        draft = make_pending(self.record, self.version)
        self.assertIsNotNone(draft["resourceObservations"]["displayConsolePosition"])
        for key in ("foot", "eye", "aim", "cameras", "path", "smoke", "fire", "he"):
            self.assertIsNone(draft[key])
        self.assertFalse(draft["importable"])
        self.assertEqual(draft["relatedIds"], ["D2-014"])

    def test_wrong_map_and_promoted_record_rejected(self):
        with self.assertRaisesRegex(ValueError, "map version mismatch"):
            make_pending(self.record, "0" * 64)
        self.record["formalImportAllowed"] = True
        with self.assertRaisesRegex(ValueError, "only accepts pending"):
            make_pending(self.record, self.version)

    def test_sphere_candidates_include_touching_and_large_remote_chunk(self):
        parts = [{"center": [2, 0, 0], "radius": 1},
                 {"center": [20, 0, 0], "radius": 20},
                 {"center": [20, 0, 0], "radius": 0.5}]
        self.assertEqual(candidate_parts(parts, [0, 0, 0], 1), parts[:2])
        with self.assertRaises(ValueError):
            candidate_parts(parts, [float("nan"), 0, 0], 1)


if __name__ == "__main__":
    unittest.main()
