"""Offline D2M1 ray picking for C015 evidence, independent of game physics."""
import gzip
import hashlib
import json
import math
import struct
from pathlib import Path

import numpy as np


def camera_basis(eye, target):
    eye, target = np.asarray(eye, dtype=float), np.asarray(target, dtype=float)
    if eye.shape != (3,) or target.shape != (3,) or not np.isfinite([eye, target]).all():
        raise ValueError('finite XYZ camera required')
    forward = target - eye
    if np.linalg.norm(forward) <= 1e-10 or np.linalg.norm(np.cross(forward, [0, 1, 0])) <= 1e-10:
        raise ValueError('nonzero, nonvertical camera direction required')
    forward /= np.linalg.norm(forward)
    right = np.cross(forward, [0, 1, 0])
    right /= np.linalg.norm(right)
    return right, np.cross(right, forward), forward


def pixel_ray(view, pixel, width, height):
    if width <= 0 or height <= 0 or not np.isfinite(pixel).all() or len(pixel) != 2:
        raise ValueError('valid viewport and pixel required')
    if not 0 < view['verticalFovDegrees'] < 180 or not math.isfinite(view.get('horizontalStretch', 1)) or view.get('horizontalStretch', 1) <= 0:
        raise ValueError('valid FOV and positive horizontal scale required')
    eye = np.asarray(view['eye'], dtype=float)
    right, up, forward = camera_basis(eye, view['target'])
    tangent = math.tan(math.radians(view['verticalFovDegrees']) / 2)
    direction = (forward + right * (2 * pixel[0] / width - 1) * width / height * tangent / view.get("horizontalStretch", 1)
                 + up * (1 - 2 * pixel[1] / height) * tangent)
    return eye, direction / np.linalg.norm(direction)


class SceneGeometry:
    def __init__(self, assets):
        self.assets = Path(assets)
        payload = (self.assets / 'maps/dust2/mobile/manifest.json').read_bytes()
        self.map_version = hashlib.sha256(payload).hexdigest()
        self.manifest = json.loads(payload)
        self.cache = {}

    def part(self, spec):
        if spec['asset'] not in self.cache:
            payload = (self.assets / spec['asset']).read_bytes()
            if hashlib.sha256(payload).hexdigest() != spec['sha256']:
                raise ValueError('geometry hash mismatch')
            data = gzip.decompress(payload) if payload[:2] == b'\x1f\x8b' else payload
            magic, nv, ni, stride = struct.unpack_from('>4sIII', data)
            if magic != b'D2M1' or stride != 36 or len(data) != 16 + 36 * nv + 2 * ni or nv != spec['vertexCount'] or ni != spec['indexCount'] or not ni or ni % 3:
                raise ValueError('invalid D2M1')
            positions = np.ndarray((nv, 3), dtype='<f4', buffer=data, offset=16, strides=(36, 4)).copy()
            uv = np.ndarray((nv, 2), dtype='<f4', buffer=data, offset=36, strides=(36, 4)).copy()
            indices = np.frombuffer(data, dtype='<u2', offset=16 + nv * 36).reshape(-1, 3).copy()
            if not np.isfinite(positions).all() or not np.isfinite(uv).all() or indices.max() >= nv:
                raise ValueError('invalid geometry values/index')
            self.cache[spec['asset']] = positions, uv, indices
        return self.cache[spec['asset']]

    def pick(self, eye, direction, max_distance=200):
        eye, direction = np.asarray(eye, dtype=float), np.asarray(direction, dtype=float)
        if eye.shape != (3,) or direction.shape != (3,) or not np.isfinite([eye, direction]).all() or np.linalg.norm(direction) <= 1e-10 or not math.isfinite(max_distance) or max_distance <= 0:
            raise ValueError('finite eye, nonzero direction and positive range required')
        direction /= np.linalg.norm(direction)
        best = None
        for spec in self.manifest['parts']:
            material = self.manifest['materials'][spec['material']]
            if not material.get('previewEnabled', True) or material['alphaMode'] == 'blend':
                continue
            delta = np.asarray(spec['center']) - eye
            along = np.dot(delta, direction)
            radius = spec['radius']
            if along + radius < 0 or along - radius > max_distance:
                continue
            if np.dot(delta, delta) - along * along > radius * radius + 1e-8:
                continue
            positions, uv, indices = self.part(spec)
            triangles = positions[indices].astype(float)
            e1, e2 = triangles[:, 1] - triangles[:, 0], triangles[:, 2] - triangles[:, 0]
            p = np.cross(direction, e2)
            det = np.einsum('ij,ij->i', e1, p)
            valid = np.abs(det) > 1e-10
            inv = np.divide(1., det, out=np.zeros_like(det), where=valid)
            t = eye - triangles[:, 0]
            a = np.einsum('ij,ij->i', t, p) * inv
            q = np.cross(t, e1)
            b = q @ direction * inv
            distance = np.einsum('ij,ij->i', e2, q) * inv
            valid &= (a >= -1e-8) & (b >= -1e-8) & (a + b <= 1 + 1e-8) & (distance > 1e-5) & (distance <= max_distance)
            if not valid.any():
                continue
            index = int(np.argmin(np.where(valid, distance, np.inf)))
            d = float(distance[index])
            if best is not None and d >= best['distance']:
                continue
            weights = np.array([1-a[index]-b[index], a[index], b[index]])
            best = {'position': (eye + d * direction).tolist(), 'distance': d,
                    'part': spec['asset'], 'partSha256': spec['sha256'],
                    'materialId': spec['material'], 'materialSource': material['source'],
                    'triangleInPart': index, 'vertices': triangles[index].tolist(),
                    'vertexIndices': indices[index].tolist(),
                    'uv': (weights @ uv[indices[index]]).tolist(),
                    'limitations': 'Opaque/mask geometry intersection; alpha texels and original shader semantics not evaluated'}
        return best
