#!/usr/bin/env python3
"""Conservative material inventory near unconfirmed source console positions.

Manifest bounding spheres only: a candidate list for local refinement, not an
aim ray, a feet location, an exact triangle match, or a visual acceptance test.
"""
import argparse
import hashlib
import json
import math
from pathlib import Path


def candidate_parts(parts, center, radius):
    if len(center) != 3 or not all(math.isfinite(x) for x in center) or not math.isfinite(radius) or radius <= 0:
        raise ValueError("finite center and positive radius required")
    return [p for p in parts if math.dist(center, p["center"]) <= radius + p["radius"]]


def inventory(root):
    folder = root / "docs/calibration/C013"
    assets = root / "app/src/main/assets"
    manifest_path = assets / "maps/dust2/mobile/manifest.json"
    manifest_bytes = manifest_path.read_bytes()
    manifest = json.loads(manifest_bytes)
    textures = {t["asset"]: t for t in manifest["textures"]}
    records = json.loads((folder / "calibration-records.json").read_text())["records"]
    rows = []
    for record in records:
        center = record["consolePositionDisplay"]
        if center is None:
            rows.append({"id": record["id"], "status": "PENDING_SOURCE_POSITION", "center": None,
                         "candidateParts": [], "materials": []})
            continue
        parts = candidate_parts(manifest["parts"], center, 3.0)
        materials = []
        for material_id in sorted({p["material"] for p in parts}):
            material = manifest["materials"][material_id]
            slots = []
            for slot in ("base", "layer", "blend"):
                asset = material.get(slot)
                if not asset:
                    continue
                t = textures[asset]
                p = (assets / asset).resolve()
                if not p.is_relative_to(assets.resolve()) or hashlib.sha256(p.read_bytes()).hexdigest() != t["sha256"]:
                    raise ValueError("texture checksum/path mismatch")
                source = t["sourcePNG"]
                slots.append({"slot": slot, "asset": asset, "sha256": t["sha256"],
                              "source": t["source"], "sourcePNG": source,
                              "mobileSize": [t["width"], t["height"]],
                              "sourceToMobileScale": [source["width"] / t["width"], source["height"] / t["height"]]})
            materials.append({"materialId": material_id, "source": material["source"],
                              "uvScale": material["uvScale"], "uvOffset": material["uvOffset"],
                              "uvRotation": material["uvRotation"], "textures": slots})
        rows.append({"id": record["id"], "status": "CANDIDATES_ONLY_NOT_VISUALLY_VERIFIED",
                     "center": center, "centerSemantics": record["consolePositionSemantics"],
                     "searchRadiusDisplayUnits": 3.0,
                     "candidateParts": [{"asset": p["asset"], "sha256": p["sha256"], "material": p["material"]} for p in parts],
                     "materials": materials})
    result = {"schema": "c013-local-texture-inventory-v1",
              "mapVersion": hashlib.sha256(manifest_bytes).hexdigest(),
              "coordinateSpace": "dust2_display_y_up_v1",
              "selection": "conservative manifest sphere overlap; not precise proximity or line of sight",
              "assetChanges": [], "formalAcceptancePassed": False, "records": rows}
    (folder / "local-texture-inventory.json").write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n")
    print("Local texture candidates:", ", ".join(f"{r['id']} {len(r['candidateParts'])} parts/{len(r['materials'])} materials" for r in rows))


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[1])
    inventory(parser.parse_args().root.resolve())
