#!/usr/bin/env python3
"""Audited offline triangle queries. Requires numpy; not an Android physics engine.

All six layers remain in storage. Each caller MUST choose its query layers.
Do not treat playerclip/grenadeclip/sky tags as a certified smoke/fire policy.
"""
import argparse
import hashlib
import json
import math
from pathlib import Path

import numpy as np

SCALE = 0.0254  # Existing display convention, not certified physical metres.
EXPECTED_FILES = {"vertices.f32", "triangles.u32", "triangle_layers.u8",
                  "triangle_surfaces.u8", "triangle_shapes.u32"}


def source_to_display(points):
    values = np.asarray(points, dtype=np.float64)
    if values.shape[-1:] != (3,) or not np.isfinite(values).all():
        raise ValueError("finite XYZ required")
    return values[..., [0, 2, 1]] * np.array([SCALE, SCALE, -SCALE])


def display_to_source(points):
    values = np.asarray(points, dtype=np.float64)
    if values.shape[-1:] != (3,) or not np.isfinite(values).all():
        raise ValueError("finite XYZ required")
    return values[..., [0, 2, 1]] * np.array([1 / SCALE, -1 / SCALE, 1 / SCALE])


def validated_raw(directory):
    directory = Path(directory)
    manifest_bytes = (directory / "manifest.json").read_bytes()
    manifest = json.loads(manifest_bytes)
    if (manifest.get("format"), manifest.get("version"), manifest.get("map"), manifest.get("byteOrder")) != ("cs2-collision-raw", 1, "de_dust2", "little-endian"):
        raise ValueError("unexpected raw collision format/version/map/byte order")
    if set(manifest["files"]) != EXPECTED_FILES:
        raise ValueError("raw collision must include all five streams")
    payloads = {}
    for name, spec in manifest["files"].items():
        p = directory / name
        if p.resolve().parent != directory.resolve():
            raise ValueError("raw collision path escapes directory")
        payload = p.read_bytes()
        if len(payload) != spec["bytes"] or hashlib.sha256(payload).hexdigest() != spec["sha256"]:
            raise ValueError("raw collision checksum mismatch: " + name)
        payloads[name] = payload
    vertices = np.frombuffer(payloads["vertices.f32"], dtype="<f4").reshape(-1, 3)
    triangles = np.frombuffer(payloads["triangles.u32"], dtype="<u4").reshape(-1, 3)
    layers = np.frombuffer(payloads["triangle_layers.u8"], dtype="u1")
    surfaces = np.frombuffer(payloads["triangle_surfaces.u8"], dtype="u1")
    shapes = np.frombuffer(payloads["triangle_shapes.u32"], dtype="<u4")
    if len(vertices) != manifest["vertexCount"] or len(triangles) != manifest["triangleCount"]:
        raise ValueError("raw collision counts mismatch")
    if not len(triangles) or not np.isfinite(vertices).all() or triangles.max() >= len(vertices):
        raise ValueError("empty collision, non-finite coordinate or invalid vertex index")
    if any(len(x) != len(triangles) for x in (layers, surfaces, shapes)):
        raise ValueError("triangle metadata counts mismatch")
    attrs = {x["index"] for x in manifest["collisionAttributes"]}
    if not set(np.unique(layers)).issubset(attrs) or surfaces.max() >= len(manifest["surfacePropertyHashes"]):
        raise ValueError("invalid collision layer or surface index")
    if len(manifest["shapes"]) != manifest["shapeCount"]:
        raise ValueError("shape count mismatch")
    triangle_cursor = vertex_cursor = 0
    mixed_surfaces = []
    for index, shape in enumerate(manifest["shapes"]):
        if shape["index"] != index or shape["triangleOffset"] != triangle_cursor or shape["vertexOffset"] != vertex_cursor:
            raise ValueError("shape ranges do not give contiguous complete coverage")
        end = triangle_cursor + shape["triangleCount"]
        vend = vertex_cursor + shape["vertexCount"]
        ti = triangles[triangle_cursor:end]
        if end > len(triangles) or vend > len(vertices) or not len(ti):
            raise ValueError("shape range outside streams")
        if ti.min() < vertex_cursor or ti.max() >= vend:
            raise ValueError("triangle crosses its shape vertex range")
        if not np.all(shapes[triangle_cursor:end] == index) or not np.all(layers[triangle_cursor:end] == shape["collisionAttributeIndex"]):
            raise ValueError("per-triangle shape/layer identity mismatch")
        if not np.all(surfaces[triangle_cursor:end] == shape["surfacePropertyIndex"]):
            mixed_surfaces.append(index)  # Mesh overrides are retained, never flattened.
        v = vertices[vertex_cursor:vend]
        if not np.allclose(v.min(0), shape["boundsMin"], atol=0.001, rtol=0) or not np.allclose(v.max(0), shape["boundsMax"], atol=0.001, rtol=0):
            raise ValueError("shape bounds do not match vertices")
        triangle_cursor, vertex_cursor = end, vend
    if (triangle_cursor, vertex_cursor) != (len(triangles), len(vertices)):
        raise ValueError("incomplete shape coverage")
    return manifest, vertices, triangles, layers, surfaces, shapes, {
        "rawManifestSha256": hashlib.sha256(manifest_bytes).hexdigest(),
        "mixedSurfaceShapes": mixed_surfaces}


class CollisionQuery:
    """Vectorized segment reference, O(N) broad phase; no collision-policy default."""

    def __init__(self, vertices, triangles, layers, surfaces, shapes, manifest):
        self.vertices = np.asarray(vertices, dtype=np.float64)
        self.triangles = np.asarray(triangles)
        self.layers, self.surfaces, self.shapes = layers, surfaces, shapes
        self.manifest = manifest
        self.tv = self.vertices[self.triangles]
        self.low, self.high = self.tv.min(1), self.tv.max(1)
        self.e1, self.e2 = self.tv[:, 1] - self.tv[:, 0], self.tv[:, 2] - self.tv[:, 0]

    @classmethod
    def from_raw(cls, directory):
        m, v, t, l, s, sh, audit = validated_raw(directory)
        return cls(source_to_display(v), t, l, s, sh, m)

    def trace_segment(self, start, end, *, layers):
        start, end = np.asarray(start, dtype=float), np.asarray(end, dtype=float)
        if start.shape != (3,) or end.shape != (3,) or not np.isfinite([start, end]).all():
            raise ValueError("finite start/end XYZ required")
        chosen = set(layers)
        known = {x["index"] for x in self.manifest["collisionAttributes"]}
        if not chosen or not chosen.issubset(known):
            raise ValueError("choose explicit, known collision attribute indices")
        direction = end - start
        length = np.linalg.norm(direction)
        if length <= 1e-12:
            raise ValueError("zero-length segment")
        epsilon = 1e-8
        ids = np.flatnonzero(np.isin(self.layers, list(chosen)) &
                            np.all(self.high >= np.minimum(start, end) - epsilon, axis=1) &
                            np.all(self.low <= np.maximum(start, end) + epsilon, axis=1))
        if not len(ids):
            return None
        e1, e2 = self.e1[ids], self.e2[ids]
        p = np.cross(direction, e2)
        det = np.einsum("ij,ij->i", e1, p)
        scale = np.linalg.norm(e1, axis=1) * np.linalg.norm(e2, axis=1) * length
        valid = np.abs(det) > 1e-12 * np.maximum(scale, 1e-30)
        inv = np.divide(1.0, det, out=np.zeros_like(det), where=valid)
        tvec = start - self.tv[ids, 0]
        u = np.einsum("ij,ij->i", tvec, p) * inv
        q = np.cross(tvec, e1)
        v = q @ direction * inv
        fraction = np.einsum("ij,ij->i", e2, q) * inv
        valid &= (u >= -epsilon) & (v >= -epsilon) & (u + v <= 1 + epsilon) & (fraction >= -epsilon) & (fraction <= 1 + epsilon)
        if not valid.any():
            return None
        nearest = int(np.argmin(np.where(valid, fraction, np.inf)))
        index = int(ids[nearest]); f = float(np.clip(fraction[nearest], 0, 1))
        normal = np.cross(e1[nearest], e2[nearest]); normal /= np.linalg.norm(normal)
        if np.dot(normal, direction) > 0:
            normal *= -1
        surface = int(self.surfaces[index])
        layer = int(self.layers[index])
        return {"triangle": index, "shape": int(self.shapes[index]), "layer": layer,
                "surfaceIndex": surface, "surfaceHash": self.manifest["surfacePropertyHashes"][surface],
                "fraction": f, "distanceDisplayUnits": f * float(length),
                "positionDisplay": (start + f * direction).tolist(),
                "normalAgainstSegment": normal.tolist()}

    def ground_below(self, point, *, max_drop, layers, min_normal_y=0.7):
        if not math.isfinite(max_drop) or max_drop <= 0 or not 0 <= min_normal_y <= 1:
            raise ValueError("positive finite drop and normal threshold [0,1] required")
        end = np.asarray(point, dtype=float) - [0, max_drop, 0]
        hit = self.trace_segment(point, end, layers=layers)
        return hit if hit and hit["normalAgainstSegment"][1] >= min_normal_y else None


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("raw", type=Path)
    parser.add_argument("--start", nargs=3, type=float, required=True)
    parser.add_argument("--end", nargs=3, type=float, required=True)
    parser.add_argument("--layers", nargs="+", type=int, required=True)
    args = parser.parse_args()
    result = CollisionQuery.from_raw(args.raw).trace_segment(args.start, args.end, layers=args.layers)
    print(json.dumps(result, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
