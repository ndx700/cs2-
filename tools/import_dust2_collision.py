#!/usr/bin/env python3
"""Adapt a verified cs2-collision-raw v1 export to the Android C2M2 viewer.

Usage: python tools/import_dust2_collision.py EXPORT_DIRECTORY
This is a display adapter, not a grenade physics implementation.
Requires numpy. The Source 2 parsing workflow owns the raw export.
"""
import argparse
import hashlib
import json
import struct
from pathlib import Path

import numpy as np

ROOT = Path(__file__).resolve().parents[1]
DEST = ROOT / 'app/src/main/assets/maps/dust2'
SCALE = 0.0254  # display scale only; keep Source coordinates in the hand-off
VISIBLE_LAYERS = (0, 1, 2)


def convert(source):
    manifest_bytes = (source / 'manifest.json').read_bytes()
    manifest = json.loads(manifest_bytes)
    assert manifest['format'] == 'cs2-collision-raw' and manifest['version'] == 1
    assert manifest['map'] == 'de_dust2' and manifest['byteOrder'] == 'little-endian'
    payloads = {}
    for name, spec in manifest['files'].items():
        assert Path(name).name == name
        payload = (source / name).read_bytes()
        assert len(payload) == spec['bytes'], name
        assert hashlib.sha256(payload).hexdigest() == spec['sha256'], name
        payloads[name] = payload
    vertices = np.frombuffer(payloads['vertices.f32'], dtype='<f4').reshape(-1, 3)
    triangles = np.frombuffer(payloads['triangles.u32'], dtype='<u4').reshape(-1, 3)
    layers = np.frombuffer(payloads['triangle_layers.u8'], dtype='u1')
    assert len(vertices) == manifest['vertexCount'] and len(triangles) == manifest['triangleCount']
    assert len(layers) == len(triangles) and triangles.max() < len(vertices)
    assert np.isfinite(vertices).all()
    rotated = vertices[:, [0, 2, 1]].copy() * SCALE
    rotated[:, 2] *= -1
    selected = np.isin(layers, VISIBLE_LAYERS)
    tv = rotated[triangles[selected]]
    display_layers = layers[selected]
    normals = np.cross(tv[:, 1] - tv[:, 0], tv[:, 2] - tv[:, 0])
    lengths = np.linalg.norm(normals, axis=1)
    good = lengths > 1e-9
    degenerate = int((~good).sum())
    tv, display_layers, normals, lengths = tv[good], display_layers[good], normals[good], lengths[good]
    normals /= lengths[:, None]
    # Spatial cells allow the existing bounding-sphere frustum culling to work.
    cells = np.floor(tv.mean(axis=1) / 8).astype(np.int32)
    keys = np.column_stack([display_layers, cells])
    _, groups = np.unique(keys, axis=0, return_inverse=True)
    order = np.argsort(groups, kind='stable')
    stops = np.flatnonzero(np.diff(groups[order])) + 1
    records = []
    colors = {0: (0.70, 0.63, 0.49), 1: (0.56, 0.59, 0.61), 2: (0.76, 0.70, 0.57)}
    for group in np.split(order, stops):
        for start in range(0, len(group), 20000):
            indices = group[start:start + 20000]
            layer = int(display_layers[indices[0]])
            count = len(indices) * 3
            data = np.zeros((count, 8), dtype='<f4')
            data[:, :3] = tv[indices].reshape(-1, 3)
            data[:, 3:6] = np.repeat(normals[indices], 3, axis=0)
            # Empty texture filename: the native loader uses a plain layer color.
            records.append(struct.pack('>HIIfff', 0, count, count, *colors[layer]) +
                           data.tobytes() + np.arange(count, dtype='<u2').tobytes())
    assert 1 <= len(records) <= 4096
    DEST.joinpath('collision').mkdir(parents=True, exist_ok=True)
    mesh = b'C2M2' + struct.pack('>I', len(records)) + b''.join(records)
    DEST.joinpath('collision/dust2.c2m').write_bytes(mesh)
    low, high = tv.reshape(-1, 3).min(axis=0), tv.reshape(-1, 3).max(axis=0)
    center = ((low + high) / 2).tolist()
    extent = float(max(high - low) * 0.95)
    scene = {'schemaVersion': 1, 'schematic': False, 'extent': extent,
             'meshAsset': 'maps/dust2/collision/dust2.c2m', 'cameraTarget': center,
             'displayLabel': '沙二 · 碰撞轮廓预览', 'boxes': [], 'labels': [],
             'minimumDistance': 6, 'targets': [],
             'credits': '原地图 Valve · Powered by Source 2 Viewer (s2v.app)',
             'overviewText': '已接入沙二真实资源导出的碰撞轮廓。可旋转和缩放查看布局；完整建筑外观与贴图待接入。',
             'emptyLineupsText': '沙二的站位、瞄点和教学视频正在整理，接入后可从地图上点选。'}
    DEST.joinpath('scene.json').write_text(json.dumps(scene, ensure_ascii=False, indent=2) + '\n')
    DEST.joinpath('lineups.json').write_text(json.dumps({'schemaVersion': 1, 'lineups': []}, indent=2) + '\n')
    summary = {'sourceFormat': manifest['format'], 'sourceVersion': manifest['version'],
               'rawManifestSha256': hashlib.sha256(manifest_bytes).hexdigest(), 'rawFiles': manifest['files'],
               'sourceTriangles': len(triangles), 'displayTriangles': len(tv),
               'excludedClipAndSkyTriangles': int((~selected).sum()), 'removedDegenerateTriangles': degenerate,
               'parts': len(records), 'meshBytes': len(mesh), 'meshSha256': hashlib.sha256(mesh).hexdigest(),
               'sourceToDisplay': '[x*0.0254, z*0.0254, -y*0.0254]; no recentering',
               'boundsMin': low.tolist(), 'boundsMax': high.tolist(),
               'visibleCollisionAttributeIndices': list(VISIBLE_LAYERS),
               'limitations': ['Collision geometry only; not the textured render model',
                               'Display filtering is not a grenade collision policy',
                               'No game-tested Dust II lineups or grenade simulation']}
    DEST.joinpath('collision/import-report.json').write_text(json.dumps(summary, ensure_ascii=False, indent=2) + '\n')
    print(json.dumps(summary, ensure_ascii=False, indent=2))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('source', type=Path)
    convert(parser.parse_args().source)
