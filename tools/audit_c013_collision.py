#!/usr/bin/env python3
"""Reproduce full-world collision evidence and a query-ready supplemental archive.

The archive is an offline calibration input, not an APK resource replacement.
Requires the existing R002 raw export and numpy. No display filtering is applied.
"""
import argparse
import hashlib
import json
import zipfile
from collections import Counter
from pathlib import Path

import numpy as np

from dust2_collision_query import source_to_display, validated_raw


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def audit(raw, report_path, archive=None):
    raw = Path(raw)
    manifest, v, t, layers, surfaces, shapes, checks = validated_raw(raw)
    tv = source_to_display(v)[t]
    cross = np.cross(tv[:, 1] - tv[:, 0], tv[:, 2] - tv[:, 0])
    area2 = np.linalg.norm(cross, axis=1)
    layer_counts = Counter(int(x) for x in layers)
    report = {"schema": "c013-world-collision-audit-v1", "resourceVersion": "R002",
              "scope": "world_physics_only", "map": manifest["map"],
              "checks": checks, "rawFiles": manifest["files"],
              "vertexCount": len(v), "triangleCount": len(t),
              "shapeCount": len(manifest["shapes"]),
              "shapeKinds": dict(Counter(s["kind"] for s in manifest["shapes"])),
              "collisionAttributes": [{**item, "triangles": layer_counts[item["index"]]}
                                      for item in manifest["collisionAttributes"]],
              "surfacePropertyHashes": manifest["surfacePropertyHashes"],
              "surfaceNamesResolved": manifest["surfaceNamesResolved"],
              "surfaceTriangleCounts": dict(Counter(int(x) for x in surfaces)),
              "boundsSource": [v.min(0).tolist(), v.max(0).tolist()],
              "boundsDisplay": [tv.reshape(-1, 3).min(0).tolist(), tv.reshape(-1, 3).max(0).tolist()],
              "sourceToDisplay": "[0.0254*x, 0.0254*z, -0.0254*y]; no recentering; apply once",
              "exactZeroAreaTriangles": int((area2 == 0).sum()),
              "displayAdapterExcludedTriangles": int(np.isin(layers, [3, 4, 5]).sum()),
              "queryLayers": "Caller-specified; smoke/fire policies not certified",
              "entityCoverage": {"included": False, "reason": "R002 is world_physics only; entity physics and runtime state need separate review"},
              "limitations": ["No swept sphere, hull or dynamic entity queries", "No official smoke/fire layer policy or coefficients", "No surface-name/friction/restitution resolution", "Offline O(N) reference; Android acceleration and performance unmeasured"]}
    if archive:
        archive = Path(archive)
        archive.parent.mkdir(parents=True, exist_ok=True)
        with zipfile.ZipFile(archive, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=6) as z:
            for name in ["manifest.json", *sorted(manifest["files"])]:
                info = zipfile.ZipInfo(name, (2026, 10, 4, 0, 0, 0))
                info.compress_type = zipfile.ZIP_DEFLATED
                info.external_attr = 0o100644 << 16
                z.writestr(info, (raw / name).read_bytes())
        report["supplementalArchive"] = {"name": archive.name, "bytes": archive.stat().st_size, "sha256": sha(archive),
                                          "role": "offline_query_input_not_imported_into_APK"}
    report_path = Path(report_path)
    report_path.parent.mkdir(parents=True, exist_ok=True)
    report_path.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n")
    print(f"PASS: {len(v)} vertices, {len(t)} triangles, {len(manifest['shapes'])} shapes, six layers retained")
    return report


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("raw", type=Path)
    p.add_argument("--report", type=Path, required=True)
    p.add_argument("--archive", type=Path)
    args = p.parse_args()
    audit(args.raw, args.report, args.archive)


if __name__ == "__main__":
    main()
